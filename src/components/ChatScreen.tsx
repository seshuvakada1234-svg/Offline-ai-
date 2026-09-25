import React from 'react';
import { AppSettings, Message, ModelId, ModelInfo } from '../types';
import { ChatMessage } from './ChatMessage';
import { assistantPipeline, type ResponsePhase } from '../services/responsePipeline';
import { tts } from '../services/ttsService';
import {
  Mic,
  ArrowUp,
  Square,
  Plus,
  ArrowDown,
  Bot,
} from 'lucide-react';

interface ChatScreenProps {
  messages: Message[];
  setMessages: React.Dispatch<React.SetStateAction<Message[]>>;
  selectedModelId: ModelId;
  models: ModelInfo[];
  settings: AppSettings;
  onOpenVoiceOverlay: () => void;
  onOpenModelManager: () => void;
}

export const ChatScreen: React.FC<ChatScreenProps> = ({
  messages,
  setMessages,
  selectedModelId,
  models,
  settings,
  onOpenVoiceOverlay,
  onOpenModelManager,
}) => {
  const [inputPrompt, setInputPrompt] = React.useState('');
  const [isGenerating, setIsGenerating] = React.useState(false);
  const [phase, setPhase] = React.useState<ResponsePhase>('IDLE');
  const [showScrollBottom, setShowScrollBottom] = React.useState(false);
  const scrollContainerRef = React.useRef<HTMLDivElement>(null);
  const messagesEndRef = React.useRef<HTMLDivElement>(null);
  const inputRef = React.useRef<HTMLInputElement>(null);
  const activeRequest = React.useRef<string | null>(null);

  const selectedModel = models.find(m => m.id === selectedModelId) || models[0];

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  React.useEffect(() => {
    scrollToBottom();
  }, [messages, isGenerating]);

  const handleScroll = () => {
    if (scrollContainerRef.current) {
      const { scrollTop, scrollHeight, clientHeight } = scrollContainerRef.current;
      setShowScrollBottom(scrollHeight - scrollTop - clientHeight > 150);
    }
  };

  const handleStopGeneration = () => {
    assistantPipeline.stop();
    const id = activeRequest.current;
    activeRequest.current = null;
    setMessages(prev => prev.map(message => message.id === id
      ? { ...message, isStreaming: false, content: message.content || 'Response stopped.' }
      : message));
    setIsGenerating(false);
    setPhase('IDLE');
  };

  const handleSendMessage = async (textToSend?: string, isVoice = false) => {
    const text = (textToSend || inputPrompt).trim();
    if (!text || activeRequest.current) return;

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
    activeRequest.current = assistantPlaceholderId;
    setMessages([...updatedMessages, assistantMessage]);
    setInputPrompt('');
    setIsGenerating(true);

    try {
      const response = await assistantPipeline.respond(updatedMessages, selectedModelId, {
        onPhase: next => {
          if (activeRequest.current === assistantPlaceholderId) setPhase(next);
        },
        onText: fullText => {
          if (activeRequest.current !== assistantPlaceholderId) return;
          setMessages(prev => prev.map(m => m.id === assistantPlaceholderId ? { ...m, content: fullText } : m));
        },
      });
      if (activeRequest.current !== assistantPlaceholderId) return;
      setMessages(prev => prev.map(m => m.id === assistantPlaceholderId ? {
        ...m, content: response.text, action: response.action, metrics: response.metrics, isStreaming: false,
      } : m));
      if (!response.cancelled && (settings.autoSpeakResponse || isVoice)) {
        tts.speak(response.text, { lang: settings.language === 'auto' ? 'en-US' : settings.language, rate: settings.speechRate, pitch: settings.speechPitch });
      }
    } catch (error) {
      if (activeRequest.current === assistantPlaceholderId) {
        setMessages(prev => prev.map(m => m.id === assistantPlaceholderId ? {
          ...m, content: m.content || `Unable to complete the request: ${error instanceof Error ? error.message : 'unknown error'}`, isStreaming: false,
        } : m));
      }
    } finally {
      if (activeRequest.current === assistantPlaceholderId) {
        activeRequest.current = null;
        setIsGenerating(false);
        setPhase('IDLE');
      }
    }
  };

  return (
    <div className="flex-1 flex flex-col min-h-0 bg-[#0A0A0C] relative">
      {/* Messages Scroll Area */}
      <div
        ref={scrollContainerRef}
        onScroll={handleScroll}
        className="flex-1 overflow-y-auto px-2 sm:px-4 py-4 space-y-2"
      >
        {messages.length === 0 && !isGenerating ? (
          <div className="h-full flex flex-col items-center justify-center p-6 text-center max-w-md mx-auto my-auto">
            <div className="w-14 h-14 rounded-2xl bg-indigo-500/15 border border-indigo-500/30 flex items-center justify-center mb-4 text-indigo-400">
              <Bot className="w-7 h-7" />
            </div>
            <h2 className="text-xl font-bold text-zinc-100 mb-1">Welcome to MyAI</h2>
            <p className="text-xs sm:text-sm text-zinc-400 mb-6">
              Private on-device intelligence powered by <span className="text-indigo-300 font-semibold">{selectedModel.name}</span>
            </p>

            <div className="w-full space-y-2 text-left">
              {[
                'What is an Operating System?',
                'Open YouTube and search Telugu songs',
                'Open Android Settings',
                'Explain quantum computing in simple terms',
              ].map((prompt, idx) => (
                <button
                  key={idx}
                  onClick={() => handleSendMessage(prompt)}
                  className="w-full text-left text-xs text-zinc-300 bg-zinc-900/70 border border-white/10 hover:border-indigo-500/40 hover:bg-zinc-850 p-3 rounded-xl transition-all"
                >
                  {prompt}
                </button>
              ))}
            </div>
          </div>
        ) : (
          messages.map(message => (
            <ChatMessage
              key={message.id}
              message={message}
              activeModelName={selectedModel.name}
              loadingText={phase === 'EXECUTING_ACTION' ? 'Executing device action...' : 'Generating response...'}
            />
          ))
        )}
        <div ref={messagesEndRef} />
      </div>

      {/* Floating Scroll to Bottom Button */}
      {showScrollBottom && (
        <button
          onClick={scrollToBottom}
          className="absolute bottom-20 right-6 p-2 rounded-full bg-zinc-800 border border-white/15 text-zinc-300 hover:text-white shadow-lg transition-all"
          title="Scroll to latest"
        >
          <ArrowDown className="w-4 h-4" />
        </button>
      )}

      {/* Modern Bottom Composer */}
      <div className="p-3 sm:p-4 bg-[#121216] border-t border-white/5">
        <div className="max-w-4xl mx-auto flex items-center gap-2 bg-[#18181F] border border-white/10 rounded-full px-3 py-1.5 focus-within:border-indigo-500/50 transition-all">
          <button
            onClick={onOpenModelManager}
            className="p-1.5 text-zinc-400 hover:text-zinc-200 rounded-full hover:bg-white/5 transition-colors"
            title="Open Model Manager"
          >
            <Plus className="w-5 h-5" />
          </button>

          <input
            ref={inputRef}
            type="text"
            value={inputPrompt}
            onChange={e => setInputPrompt(e.target.value)}
            onKeyDown={e => {
              if (e.key === 'Enter' && !e.shiftKey) {
                e.preventDefault();
                handleSendMessage();
              }
            }}
            placeholder={isGenerating ? (phase === 'EXECUTING_ACTION' ? 'Executing device action...' : 'Generating response...') : 'Ask anything...'}
            disabled={isGenerating}
            className="flex-1 bg-transparent text-sm text-zinc-100 placeholder-zinc-500 px-2 py-1.5 focus:outline-none disabled:opacity-60"
          />

          <button
            onClick={onOpenVoiceOverlay}
            disabled={isGenerating}
            className="p-1.5 text-zinc-400 hover:text-teal-400 rounded-full hover:bg-white/5 transition-colors disabled:opacity-40"
            title="Voice Input (Whisper STT)"
          >
            <Mic className="w-5 h-5" />
          </button>

          {isGenerating ? (
            <button
              onClick={handleStopGeneration}
              className="p-2 bg-rose-600 hover:bg-rose-500 text-white rounded-full transition-all shadow-md shadow-rose-600/25"
              title="Stop generation"
            >
              <Square className="w-4 h-4" />
            </button>
          ) : (
            <button
              onClick={() => handleSendMessage()}
              disabled={!inputPrompt.trim()}
              className={`p-2 rounded-full transition-all ${
                inputPrompt.trim()
                  ? 'bg-indigo-600 text-white shadow-md shadow-indigo-600/25 hover:bg-indigo-500'
                  : 'bg-zinc-800 text-zinc-600 cursor-not-allowed'
              }`}
              title="Send message"
            >
              <ArrowUp className="w-4 h-4" />
            </button>
          )}
        </div>
      </div>
    </div>
  );
};
