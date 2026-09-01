import React from 'react';
import { AppSettings, AssistantAction, Message, ModelId, ModelInfo, VoiceState } from './types';
import { modelManager } from './services/modelManager';
import { qwen3Engine } from './services/qwen3Engine';
import { ActionHandler } from './services/actionHandler';
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
import { ActionConfirmationDialog } from './components/ActionConfirmationDialog';

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
      return saved ? JSON.parse(saved) : [];
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
  const [pendingAction, setPendingAction] = React.useState<AssistantAction | null>(null);

  // Voice Interaction state
  const [voiceState, setVoiceState] = React.useState<VoiceState>('IDLE');
  const [voiceTranscript, setVoiceTranscript] = React.useState('');
  const [voiceAssistantResponse, setVoiceAssistantResponse] = React.useState('');
  const [audioLevel, setAudioLevel] = React.useState(0);

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

  // Voice Pipeline: Handle Voice Command
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

    const assistantPlaceholderId = Math.random().toString(36).substring(2, 9);
    const updatedMessages = [...messages, userMessage];

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

    let accumulatedText = '';

    await qwen3Engine.generateResponse(updatedMessages, selectedModelId, {
      onToken: (token, full) => {
        accumulatedText = full;
        setVoiceAssistantResponse(full);
        setMessages(prev =>
          prev.map(m => (m.id === assistantPlaceholderId ? { ...m, content: full, isStreaming: true } : m))
        );
      },
      onComplete: async (fullText, metrics) => {
        const parsed = ActionHandler.parseActionFromLLM(fullText);

        setMessages(prev =>
          prev.map(m =>
            m.id === assistantPlaceholderId
              ? {
                  ...m,
                  content: parsed.cleanedText || fullText,
                  action: parsed.action,
                  metrics,
                  isStreaming: false,
                }
              : m
          )
        );

        setVoiceAssistantResponse(parsed.spokenSummary || parsed.cleanedText);

        // Execute action if not requiring confirmation
        if (parsed.hasAction && parsed.action) {
          if (parsed.action.requiresConfirmation) {
            setVoiceState('IDLE');
            setPendingAction(parsed.action);
          } else {
            setVoiceState('ACTION_EXECUTING');
            const execResult = await ActionHandler.executeAction(parsed.action);
            setMessages(prev =>
              prev.map(m =>
                m.id === assistantPlaceholderId && m.action
                  ? {
                      ...m,
                      action: {
                        ...m.action,
                        executed: execResult.success,
                        resultMessage: execResult.message,
                      },
                    }
                  : m
              )
            );
          }
        }

        // Announce with TTS
        setVoiceState('SPEAKING');
        const targetLang =
          settings.language === 'auto'
            ? spokenText.includes('తెలుగు') || fullText.includes('తెలుగు')
              ? 'te-IN'
              : 'en-US'
            : settings.language;

        tts.speak(parsed.spokenSummary || parsed.cleanedText, {
          lang: targetLang,
          rate: settings.speechRate,
          pitch: settings.speechPitch,
          onEnd: () => {
            setVoiceState('IDLE');
          },
          onError: () => {
            setVoiceState('IDLE');
          },
        });
      },
      onError: err => {
        setVoiceState('ERROR');
        setVoiceAssistantResponse(`Inference error: ${err.message}`);
        setTimeout(() => setVoiceState('IDLE'), 3000);
      },
    });
  };

  const handleStartVoice = () => {
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
        setVoiceState('IDLE');
      },
      onStateChange: (newState) => {
        if (newState === 'LISTENING') setVoiceState('LISTENING');
        if (newState === 'TRANSCRIBING') setVoiceState('TRANSCRIBING');
        if (newState === 'IDLE' && voiceState === 'LISTENING') setVoiceState('IDLE');
      },
    });
  };

  const handleStopVoice = () => {
    whisperSTT.stopListening();
    setVoiceState('IDLE');
  };

  const handleActionConfirmed = async (action: AssistantAction) => {
    setPendingAction(null);
    const res = await ActionHandler.executeAction({ ...action, confirmed: true });
    setMessages(prev =>
      prev.map(m =>
        m.action?.id === action.id
          ? {
              ...m,
              action: {
                ...m.action,
                confirmed: true,
                executed: res.success,
                resultMessage: res.message,
              },
            }
          : m
      )
    );
  };

  const storageStats = modelManager.getStorageStats();

  return (
    <div className="flex flex-col h-screen w-screen bg-zinc-950 text-zinc-100 antialiased overflow-hidden font-sans select-none" id="myai-app-root">
      {/* Top Header */}
      <Header
        models={models}
        selectedModelId={selectedModelId}
        onSelectModel={id => setSelectedModelId(id)}
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
        onActionConfirmation={action => setPendingAction(action)}
      />

      {/* Voice Assistant Interactive Overlay */}
      <VoiceOverlay
        isOpen={isVoiceOverlayOpen}
        onClose={() => {
          handleStopVoice();
          setIsVoiceOverlayOpen(false);
        }}
        voiceState={voiceState}
        transcript={voiceTranscript}
        assistantResponse={voiceAssistantResponse}
        audioLevel={audioLevel}
        onStartListening={handleStartVoice}
        onStopListening={handleStopVoice}
        onSelectPrompt={prompt => {
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
        onSelectModel={id => setSelectedModelId(id)}
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

      {/* Action Confirmation Modal */}
      <ActionConfirmationDialog
        action={pendingAction}
        isOpen={!!pendingAction}
        onConfirm={handleActionConfirmed}
        onCancel={() => setPendingAction(null)}
      />
    </div>
  );
}
