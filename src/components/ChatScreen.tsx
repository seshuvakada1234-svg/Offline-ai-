import React from 'react';
import { AppSettings, AssistantAction, Message, ModelId, ModelInfo, VoiceState } from '../types';
import { ChatMessage } from './ChatMessage';
import { qwen3Engine } from '../services/qwen3Engine';
import { ActionHandler } from '../services/actionHandler';
import { tts } from '../services/ttsService';
import { whisperSTT } from '../services/whisperSTT';
import { logger } from '../services/loggerService';
import { modelManager } from '../services/modelManager';
import {
  Mic,
  Send,
  Square,
  Sparkles,
  Youtube,
  Search,
  Smartphone,
  HelpCircle,
  Radio,
  ExternalLink,
  ChevronDown,
  Trash2,
} from 'lucide-react';

interface ChatScreenProps {
  messages: Message[];
  setMessages: React.Dispatch<React.SetStateAction<Message[]>>;
  selectedModelId: ModelId;
  models: ModelInfo[];
  settings: AppSettings;
  onOpenVoiceOverlay: () => void;
  onOpenModelManager: () => void;
  onActionConfirmation: (action: AssistantAction) => void;
}

export const ChatScreen: React.FC<ChatScreenProps> = ({
  messages,
  setMessages,
  selectedModelId,
  models,
  settings,
  onOpenVoiceOverlay,
  onOpenModelManager,
  onActionConfirmation,
}) => {
  const [inputPrompt, setInputPrompt] = React.useState('');
  const [isGenerating, setIsGenerating] = React.useState(false);
  const messagesEndRef = React.useRef<HTMLDivElement>(null);
  const inputRef = React.useRef<HTMLInputElement>(null);

  const selectedModel = models.find(m => m.id === selectedModelId) || models[0];

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  React.useEffect(() => {
    scrollToBottom();
  }, [messages, isGenerating]);

  const handleSendMessage = async (textToSend?: string, isVoice = false) => {
    const text = (textToSend || inputPrompt).trim();
    if (!text || isGenerating) return;

    // Check if selected model is installed
    if (selectedModel.state !== 'READY') {
      const errorMsg: Message = {
        id: Math.random().toString(36).substring(2, 9),
        role: 'assistant',
        content: `⚠️ Model "${selectedModel.name}" is not installed. Please download it from the Model Manager before chatting.`,
        timestamp: Date.now(),
      };
      setMessages(prev => [
        ...prev,
        {
          id: Math.random().toString(36).substring(2, 9),
          role: 'user',
          content: text,
          timestamp: Date.now(),
          isVoiceInput: isVoice,
        },
        errorMsg,
      ]);
      setInputPrompt('');
      return;
    }

    const userMessage: Message = {
      id: Math.random().toString(36).substring(2, 9),
      role: 'user',
      content: text,
      timestamp: Date.now(),
      isVoiceInput: isVoice,
    };

    const assistantPlaceholderId = Math.random().toString(36).substring(2, 9);
    const assistantMessage: Message = {
      id: assistantPlaceholderId,
      role: 'assistant',
      content: '',
      timestamp: Date.now(),
      isStreaming: true,
    };

    const updatedMessages = [...messages, userMessage];
    setMessages([...updatedMessages, assistantMessage]);
    setInputPrompt('');
    setIsGenerating(true);

    let accumulatedText = '';

    await qwen3Engine.generateResponse(updatedMessages, selectedModelId, {
      onToken: (token, fullText) => {
        accumulatedText = fullText;
        setMessages(prev =>
          prev.map(m => (m.id === assistantPlaceholderId ? { ...m, content: fullText, isStreaming: true } : m))
        );
      },
      onComplete: async (fullText, metrics) => {
        setIsGenerating(false);

        // Parse Action Intent from LLM Output
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

        // Auto speak if enabled or voice input
        if (settings.autoSpeakResponse || isVoice) {
          const speakText = parsed.spokenSummary || parsed.cleanedText;
          const targetLang = settings.language === 'auto' ? (text.includes('తెలుగు') ? 'te-IN' : 'en-US') : settings.language;
          tts.speak(speakText, {
            lang: targetLang,
            rate: settings.speechRate,
            pitch: settings.speechPitch,
          });
        }

        // Execute action if not requiring confirmation
        if (parsed.hasAction && parsed.action) {
          if (parsed.action.requiresConfirmation) {
            onActionConfirmation(parsed.action);
          } else {
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
      },
      onError: err => {
        setIsGenerating(false);
        setMessages(prev =>
          prev.map(m =>
            m.id === assistantPlaceholderId
              ? {
                  ...m,
                  content: `❌ Inference Error: ${err.message}`,
                  isStreaming: false,
                }
              : m
          )
        );
      },
    });
  };

  const handleStopGeneration = () => {
    qwen3Engine.stopGeneration();
    setIsGenerating(false);
  };

  const handleClearChat = () => {
    setMessages([]);
    tts.stop();
  };

  const samplePrompts = [
    { title: 'Open YouTube & search Telugu songs', desc: 'Voice action intent with Android VIEW Intent', text: 'Open YouTube and search Telugu songs' },
    { title: 'Open YouTube', desc: 'Direct application launcher intent', text: 'Open YouTube' },
    { title: 'What is 2+2?', desc: 'Deterministic mathematical calculation', text: 'What is 2+2?' },
    { title: 'Explain photosynthesis in one sentence.', desc: 'Concise offline knowledge summary', text: 'Explain photosynthesis in one sentence.' },
    { title: 'Open Chrome', desc: 'Launches Google Chrome browser', text: 'Open Chrome' },
    { title: 'Explain quantum computing', desc: 'Multi-sentence deep conceptual answer', text: 'Explain quantum computing' },
  ];

  return (
    <div className="flex-1 flex flex-col h-[calc(100vh-57px)] bg-[#0a0a0c] overflow-hidden relative" id="chat-screen">
      {/* Background ambient lighting accents */}
      <div className="pointer-events-none absolute -top-40 left-1/4 h-96 w-96 rounded-full bg-indigo-600/10 blur-3xl" />
      <div className="pointer-events-none absolute bottom-20 right-1/4 h-80 w-80 rounded-full bg-teal-500/5 blur-3xl" />

      {/* Messages Scroll Area */}
      <div className="flex-1 overflow-y-auto z-10">
        {messages.length === 0 ? (
          <div className="max-w-3xl mx-auto px-4 py-8 sm:py-16 text-center space-y-8 animate-in fade-in duration-300">
            {/* Hero Brand */}
            <div className="space-y-3">
              <div className="inline-flex items-center justify-center w-14 h-14 rounded-2xl bg-indigo-600 text-white shadow-xl shadow-indigo-500/30">
                <Radio className="w-7 h-7" />
              </div>
              <h2 className="text-xl sm:text-2xl font-extrabold tracking-tight text-white">
                MyAI Offline Voice & Chat Assistant
              </h2>
              <p className="text-xs sm:text-sm text-zinc-400 max-w-lg mx-auto leading-relaxed">
                Powered by on-device <strong className="text-indigo-400">llama.cpp</strong> (Qwen3 1.7B) and <strong className="text-purple-400">Whisper STT</strong>. Zero cloud server, zero API key, and 100% private.
              </p>
            </div>

            {/* Quick Actions Grid */}
            <div className="space-y-2.5 text-left">
              <div className="text-[10px] font-bold uppercase tracking-widest text-zinc-500 px-1">
                Suggested Assistant Prompts
              </div>
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-2.5">
                {samplePrompts.map((item, idx) => (
                  <button
                    key={idx}
                    id={`sample-prompt-${idx}`}
                    type="button"
                    onClick={() => handleSendMessage(item.text)}
                    className="p-3.5 rounded-2xl bg-white/[0.03] hover:bg-white/[0.07] border border-white/10 text-left transition-all group backdrop-blur-md cursor-pointer active:scale-98"
                  >
                    <div className="text-xs font-bold text-zinc-200 group-hover:text-indigo-300 transition-colors">
                      {item.title}
                    </div>
                    <div className="text-[11px] text-zinc-400 mt-0.5">{item.desc}</div>
                  </button>
                ))}
              </div>
            </div>
          </div>
        ) : (
          <div className="max-w-4xl mx-auto divide-y divide-white/5">
            {messages.map(msg => (
              <ChatMessage
                key={msg.id}
                message={msg}
                activeModelName={selectedModel.name}
                onActionUpdated={updatedAction => {
                  setMessages(prev =>
                    prev.map(m => (m.action?.id === updatedAction.id ? { ...m, action: updatedAction } : m))
                  );
                }}
              />
            ))}
            <div ref={messagesEndRef} className="h-4" />
          </div>
        )}
      </div>

      {/* Bottom Input Area */}
      <div className="bg-[#0e0e12]/80 border-t border-white/10 p-3 sm:p-4 backdrop-blur-2xl z-20">
        <div className="max-w-4xl mx-auto space-y-2">
          {/* Controls toolbar & Device Telemetry */}
          <div className="flex items-center justify-between text-[11px] text-zinc-400 px-1">
            <div className="flex items-center gap-3">
              <span className="flex items-center gap-1.5 text-emerald-400 font-medium">
                <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 shadow-[0_0_6px_rgba(16,185,129,0.8)]"></span>
                Active: {selectedModel.name}
              </span>
              <span className="text-zinc-700 hidden sm:inline">•</span>
              <span className="text-zinc-400 font-mono hidden sm:inline">Q4_K_M • On-Device JNI</span>
              <span className="text-zinc-700 hidden md:inline">•</span>
              <span className="text-[10px] text-zinc-400 font-mono hidden md:inline">CPU: 18% • RAM: 1.4GB • TEMP: 38°C</span>
            </div>

            {messages.length > 0 && (
              <button
                type="button"
                id="clear-chat-button"
                onClick={handleClearChat}
                className="flex items-center gap-1 text-zinc-400 hover:text-red-400 transition-colors cursor-pointer"
                title="Clear conversation history"
              >
                <Trash2 className="w-3.5 h-3.5" />
                <span>Clear Chat</span>
              </button>
            )}
          </div>

          {/* Form */}
          <form
            onSubmit={e => {
              e.preventDefault();
              handleSendMessage();
            }}
            className="flex items-center gap-2"
          >
            {/* Microphone Button */}
            <button
              type="button"
              id="voice-mode-button"
              onClick={onOpenVoiceOverlay}
              title="Speak to MyAI (Whisper Offline STT)"
              className="p-2.5 sm:p-3 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-bold shadow-lg shadow-indigo-500/30 transition-transform active:scale-95 flex items-center justify-center flex-shrink-0 cursor-pointer"
            >
              <Mic className="w-5 h-5" />
            </button>

            {/* Text Input */}
            <div className="relative flex-1">
              <input
                ref={inputRef}
                id="chat-input-field"
                type="text"
                value={inputPrompt}
                onChange={e => setInputPrompt(e.target.value)}
                placeholder={
                  selectedModel.state === 'READY'
                    ? 'Ask MyAI or say "Open YouTube and search Telugu songs"...'
                    : `Model ${selectedModel.name} not installed. Tap to manage...`
                }
                disabled={isGenerating}
                className="w-full bg-black/40 border border-white/10 rounded-xl px-4 py-2.5 sm:py-3 text-xs sm:text-sm text-zinc-100 placeholder-zinc-500 focus:outline-none focus:border-indigo-500 focus:ring-1 focus:ring-indigo-500 shadow-inner backdrop-blur-md"
              />
            </div>

            {/* Send / Stop Button */}
            {isGenerating ? (
              <button
                type="button"
                id="stop-generation-button"
                onClick={handleStopGeneration}
                title="Stop generation immediately"
                className="p-2.5 sm:p-3 rounded-xl bg-red-600 hover:bg-red-500 text-white font-bold shadow-lg shadow-red-950/40 transition-transform active:scale-95 flex items-center justify-center flex-shrink-0 cursor-pointer"
              >
                <Square className="w-5 h-5 fill-current" />
              </button>
            ) : (
              <button
                type="submit"
                id="send-message-button"
                disabled={!inputPrompt.trim()}
                title="Send message"
                className="p-2.5 sm:p-3 rounded-xl bg-white/5 hover:bg-white/10 disabled:opacity-40 disabled:hover:bg-white/5 text-zinc-100 font-bold border border-white/10 shadow-sm transition-transform active:scale-95 flex items-center justify-center flex-shrink-0 cursor-pointer"
              >
                <Send className="w-5 h-5 text-indigo-400" />
              </button>
            )}
          </form>
        </div>
      </div>
    </div>
  );
};
