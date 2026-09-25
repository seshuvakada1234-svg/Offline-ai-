import React from 'react';
import { AppSettings, Message, ModelId, ModelInfo, VoiceState } from './types';
import { modelManager } from './services/modelManager';
import { assistantPipeline } from './services/responsePipeline';
import { tts } from './services/ttsService';
import { whisperSTT } from './services/whisperSTT';
import { logger } from './services/loggerService';
import { Header } from './components/Header';
import { ChatScreen } from './components/ChatScreen';
import { ModelManagerModal } from './components/ModelManagerModal';
import { EngineLogsModal } from './components/EngineLogsModal';
import { TestSuiteModal } from './components/TestSuiteModal';
import { SettingsModal } from './components/SettingsModal';
import { VoiceOverlay } from './components/VoiceOverlay';

const CHAT_STORAGE_KEY = 'myai_offline_chat_history_v2';
const SETTINGS_STORAGE_KEY = 'myai_offline_settings_v2';

const DEFAULT_SETTINGS: AppSettings = {
  language: 'auto',
  speechRate: 1.0,
  speechPitch: 1.0,
  autoSpeakResponse: true,
  inferenceThreads: 6,
  showPerformanceStats: true,
};

export default function App() {
  const [models, setModels] = React.useState<ModelInfo[]>(modelManager.getModels());
  const [selectedModelId, setSelectedModelId] = React.useState<ModelId>('qwen3-1.7b');
  const [messages, setMessages] = React.useState<Message[]>(() => {
    try {
      const saved = localStorage.getItem(CHAT_STORAGE_KEY);
      const restored: Message[] = saved ? JSON.parse(saved) : [];
      return Array.isArray(restored) ? restored.map(message => message.isStreaming ? {
        ...message, isStreaming: false, content: message.content || 'Response interrupted. Please try again.',
      } : message) : [];
    } catch (e) {
      return [];
    }
  });

  const [settings, setSettings] = React.useState<AppSettings>(() => {
    try {
      const saved = localStorage.getItem(SETTINGS_STORAGE_KEY);
      return saved ? JSON.parse(saved) : DEFAULT_SETTINGS;
    } catch (e) {
      return DEFAULT_SETTINGS;
    }
  });

  // Modals state
  const [isModelManagerOpen, setIsModelManagerOpen] = React.useState(false);
  const [isLogsOpen, setIsLogsOpen] = React.useState(false);
  const [isTestsOpen, setIsTestsOpen] = React.useState(false);
  const [isSettingsOpen, setIsSettingsOpen] = React.useState(false);
  const [isVoiceOverlayOpen, setIsVoiceOverlayOpen] = React.useState(false);

  // Voice Interaction state
  const [voiceState, setVoiceState] = React.useState<VoiceState>('IDLE');
  const [voiceTranscript, setVoiceTranscript] = React.useState('');
  const [voiceAssistantResponse, setVoiceAssistantResponse] = React.useState('');
  const [audioLevel, setAudioLevel] = React.useState(0);
  const activeVoiceRequest = React.useRef<string | null>(null);

  React.useEffect(() => () => {
    assistantPipeline.stop();
    whisperSTT.cancel();
    tts.stop();
  }, []);

  // Subscribe to Model Manager updates
  React.useEffect(() => {
    const unsub = modelManager.subscribe(updatedModels => {
      setModels(updatedModels);
    });
    return unsub;
  }, []);

  // Save messages to localStorage
  React.useEffect(() => {
    try {
      localStorage.setItem(CHAT_STORAGE_KEY, JSON.stringify(messages));
    } catch (e) {
      console.warn('Could not save chat history to localStorage', e);
    }
  }, [messages]);

  // Save settings to localStorage
  const handleSaveSettings = (newSettings: AppSettings) => {
    setSettings(newSettings);
    try {
      localStorage.setItem(SETTINGS_STORAGE_KEY, JSON.stringify(newSettings));
    } catch (e) {}
  };

  // Voice input uses the same conversation/action pipeline as keyboard input.
  const processVoiceInput = async (spokenText: string) => {
    if (!spokenText.trim()) {
      setVoiceState('IDLE');
      return;
    }

    setVoiceState('THINKING');
    setVoiceAssistantResponse('');

    const userMessage: Message = {
      id: Math.random().toString(36).substring(2, 9),
      role: 'user',
      content: spokenText,
      timestamp: Date.now(),
      isVoiceInput: true,
    };

    const previousVoiceId = activeVoiceRequest.current;
    const assistantPlaceholderId = Math.random().toString(36).substring(2, 9);
    activeVoiceRequest.current = assistantPlaceholderId;
    const updatedMessages = [...messages.map(m => m.id === previousVoiceId ? { ...m, isStreaming: false } : m), userMessage];

    setMessages([
      ...updatedMessages,
      {
        id: assistantPlaceholderId,
        role: 'assistant',
        content: '',
        timestamp: Date.now(),
        isStreaming: true,
      },
    ]);

    try {
      const response = await assistantPipeline.respond(updatedMessages, selectedModelId, {
        onText: full => {
          if (activeVoiceRequest.current !== assistantPlaceholderId) return;
          setVoiceAssistantResponse(full);
          setMessages(prev => prev.map(m => m.id === assistantPlaceholderId ? { ...m, content: full } : m));
        },
        onPhase: phase => {
          if (activeVoiceRequest.current !== assistantPlaceholderId) return;
          setVoiceState(phase === 'EXECUTING_ACTION' ? 'ACTION_EXECUTING' : phase === 'GENERATING' ? 'THINKING' : 'IDLE');
        },
      });
      if (activeVoiceRequest.current !== assistantPlaceholderId) return;
      setMessages(prev => prev.map(m => m.id === assistantPlaceholderId ? {
        ...m, content: response.text, action: response.action, metrics: response.metrics, isStreaming: false,
      } : m));
      setVoiceAssistantResponse(response.text);
      if (!response.cancelled) {
        const lang = settings.language === 'auto' ? (/[\u0c00-\u0c7f]/.test(response.text) ? 'te-IN' : 'en-US') : settings.language;
        tts.speak(response.text, { lang, rate: settings.speechRate, pitch: settings.speechPitch });
      }
    } catch (error) {
      if (activeVoiceRequest.current === assistantPlaceholderId) {
        setMessages(prev => prev.map(m => m.id === assistantPlaceholderId ? {
          ...m, content: m.content || `Unable to complete the request: ${error instanceof Error ? error.message : 'unknown error'}`, isStreaming: false,
        } : m));
      }
    } finally {
      if (activeVoiceRequest.current === assistantPlaceholderId) {
        activeVoiceRequest.current = null;
        setVoiceState('IDLE');
      }
    }
  };

  const handleStartVoice = () => {
    handleCancelVoice();
    setVoiceState('LISTENING');
    setVoiceTranscript('');
    setVoiceAssistantResponse('');

    const targetLang = settings.language === 'auto' ? 'en-US' : settings.language;

    whisperSTT.startListening({
      language: targetLang,
      onAudioLevel: (level) => {
        setAudioLevel(level);
      },
      onTranscript: (transcript, isFinal) => {
        setVoiceTranscript(transcript);
        if (isFinal && transcript.trim()) {
          setVoiceState('TRANSCRIBING');
          processVoiceInput(transcript);
        }
      },
      onError: (err) => {
        logger.log('VOICE_TRANSCRIPT', `Voice capture error: ${err}`);
        setVoiceAssistantResponse(err);
        setVoiceState('IDLE');
      },
      onStateChange: (newState) => {
        if (newState === 'LISTENING') setVoiceState('LISTENING');
        if (newState === 'TRANSCRIBING') setVoiceState('TRANSCRIBING');
        if (newState === 'IDLE') setVoiceState(previous => previous === 'LISTENING' || previous === 'TRANSCRIBING' ? 'IDLE' : previous);
      },
    });
  };

  const handleStopVoice = () => {
    whisperSTT.stopListening();
  };

  const handleCancelVoice = () => {
    const id = activeVoiceRequest.current;
    activeVoiceRequest.current = null;
    whisperSTT.cancel();
    assistantPipeline.stop();
    tts.stop();
    setMessages(prev => prev.map(m => m.id === id ? { ...m, isStreaming: false, content: m.content || 'Response stopped.' } : m));
    setVoiceState('IDLE');
  };

  const handleSelectModel = (id: ModelId) => {
    if (id === selectedModelId) return;
    handleCancelVoice();
    setSelectedModelId(id);
  };

  const storageStats = modelManager.getStorageStats();

  return (
    <div className="flex flex-col h-screen w-screen bg-zinc-950 text-zinc-100 antialiased overflow-hidden font-sans select-none" id="myai-app-root">
      {/* Top Header */}
      <Header
        models={models}
        selectedModelId={selectedModelId}
        onSelectModel={handleSelectModel}
        onOpenModelManager={() => setIsModelManagerOpen(true)}
        onOpenLogs={() => setIsLogsOpen(true)}
        onOpenTests={() => setIsTestsOpen(true)}
        onOpenSettings={() => setIsSettingsOpen(true)}
        storageStats={storageStats}
        isOnline={false}
      />

      {/* Main Chat View */}
      <ChatScreen
        messages={messages}
        setMessages={setMessages}
        selectedModelId={selectedModelId}
        models={models}
        settings={settings}
        onOpenVoiceOverlay={() => {
          setIsVoiceOverlayOpen(true);
          handleStartVoice();
        }}
        onOpenModelManager={() => setIsModelManagerOpen(true)}
      />

      {/* Voice Assistant Interactive Overlay */}
      <VoiceOverlay
        isOpen={isVoiceOverlayOpen}
        onClose={() => {
          handleCancelVoice();
          setIsVoiceOverlayOpen(false);
        }}
        voiceState={voiceState}
        transcript={voiceTranscript}
        assistantResponse={voiceAssistantResponse}
        audioLevel={audioLevel}
        onStartListening={handleStartVoice}
        onStopListening={handleStopVoice}
        onSelectPrompt={prompt => {
          whisperSTT.cancel();
          setVoiceTranscript(prompt);
          processVoiceInput(prompt);
        }}
        selectedLanguage={settings.language}
        onChangeLanguage={lang => {
          const updated = { ...settings, language: lang };
          handleSaveSettings(updated);
        }}
      />

      {/* Model Manager Modal */}
      <ModelManagerModal
        isOpen={isModelManagerOpen}
        onClose={() => setIsModelManagerOpen(false)}
        models={models}
        selectedModelId={selectedModelId}
        onSelectModel={handleSelectModel}
      />

      {/* llama.cpp & Intent Logcat Modal */}
      <EngineLogsModal
        isOpen={isLogsOpen}
        onClose={() => setIsLogsOpen(false)}
      />

      {/* Automated Test Suite Modal */}
      <TestSuiteModal
        isOpen={isTestsOpen}
        onClose={() => setIsTestsOpen(false)}
      />

      {/* Settings Modal */}
      <SettingsModal
        isOpen={isSettingsOpen}
        onClose={() => setIsSettingsOpen(false)}
        settings={settings}
        onSaveSettings={handleSaveSettings}
      />

    </div>
  );
}
