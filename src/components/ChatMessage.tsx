import React from 'react';
import { Message } from '../types';
import { ActionCard } from './ActionCard';
import { tts } from '../services/ttsService';
import {
  Volume2,
  VolumeX,
  Copy,
  Check,
  Mic,
  Cpu,
  Zap,
  Sparkles,
  Bot,
  User,
} from 'lucide-react';

interface ChatMessageProps {
  message: Message;
  activeModelName?: string;
  onActionUpdated?: (action: any) => void;
}

export const ChatMessage: React.FC<ChatMessageProps> = ({
  message,
  activeModelName = 'Qwen3 1.7B',
  onActionUpdated,
}) => {
  const [copied, setCopied] = React.useState(false);
  const [isSpeaking, setIsSpeaking] = React.useState(false);

  React.useEffect(() => {
    const unsub = tts.subscribe(speaking => {
      if (!speaking && isSpeaking) {
        setIsSpeaking(false);
      }
    });
    return unsub;
  }, [isSpeaking]);

  const handleCopy = () => {
    navigator.clipboard.writeText(message.content);
    setCopied(true);
    setTimeout(() => setCopied(false), 1800);
  };

  const handleToggleTTS = () => {
    if (isSpeaking) {
      tts.stop();
      setIsSpeaking(false);
    } else {
      setIsSpeaking(true);
      tts.speak(message.content, {
        onEnd: () => setIsSpeaking(false),
        onError: () => setIsSpeaking(false),
      });
    }
  };

  const isUser = message.role === 'user';

  return (
    <div
      id={`message-${message.id}`}
      className="py-4 px-3 sm:px-6 flex gap-3.5 transition-colors"
    >
      {/* Avatar */}
      <div className="flex-shrink-0 mt-0.5">
        {isUser ? (
          <div className="h-8 w-8 rounded-full bg-zinc-800/90 border border-white/10 flex items-center justify-center text-xs text-zinc-300 shadow-sm font-semibold">
            U
          </div>
        ) : (
          <div className="h-8 w-8 rounded-full bg-indigo-600 shadow-lg shadow-indigo-500/25 flex items-center justify-center text-[10px] font-bold text-white">
            AI
          </div>
        )}
      </div>

      {/* Content Body */}
      <div className="flex-1 min-w-0 space-y-2">
        <div className="flex items-center justify-between gap-2">
          <div className="flex items-center gap-2">
            <span className="text-xs font-bold text-zinc-200">
              {isUser ? 'You' : activeModelName}
            </span>
            {isUser && message.isVoiceInput && (
              <span className="inline-flex items-center gap-1 text-[10px] bg-indigo-500/15 text-indigo-300 border border-indigo-500/30 px-2 py-0.5 rounded-full font-medium">
                <Mic className="w-2.5 h-2.5" />
                Whisper STT
              </span>
            )}
            {!isUser && (
              <span className="text-[10px] text-zinc-500 font-mono">
                llama.cpp • on-device
              </span>
            )}
          </div>

          <span className="text-[10px] text-zinc-500 font-mono">
            {new Date(message.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
          </span>
        </div>

        {/* Message Bubble */}
        <div
          className={`max-w-2xl rounded-2xl px-5 py-3.5 border text-xs sm:text-sm leading-relaxed backdrop-blur-md transition-all ${
            isUser
              ? 'bg-white/[0.04] border-white/10 text-zinc-100'
              : 'bg-indigo-500/10 border-indigo-500/20 text-zinc-200 shadow-sm'
          }`}
        >
          <div className="whitespace-pre-wrap break-words font-sans">
            {message.content}
            {message.isStreaming && (
              <span className="inline-block w-1.5 h-4 ml-1 bg-indigo-400 animate-pulse align-middle" />
            )}
          </div>

          {/* Action Card if present */}
          {message.action && (
            <div className="mt-3 pt-2">
              <ActionCard action={message.action} onActionUpdated={onActionUpdated} />
            </div>
          )}

          {/* Assistant Bottom Bar: Metrics & Controls */}
          {!isUser && !message.isStreaming && (
            <div className="pt-3 flex flex-wrap items-center justify-between gap-3 border-t border-indigo-500/20 mt-3">
              {/* Performance Pill */}
              {message.metrics && (
                <div
                  className="flex items-center gap-2 text-[10px] text-zinc-400 font-mono"
                  title={`Model load: ${message.metrics.modelLoadTimeMs}ms, Total: ${message.metrics.totalGenTimeMs}ms`}
                >
                  <span className="text-zinc-300 font-semibold">{message.metrics.timeToFirstTokenMs}ms first-token</span>
                  <span className="text-zinc-600">•</span>
                  <span className="text-indigo-400 font-semibold">{message.metrics.tokensPerSec} t/s</span>
                  <span className="text-zinc-600">•</span>
                  <span className="text-zinc-500">{message.metrics.totalTokens} tokens</span>
                </div>
              )}

              {/* Action buttons */}
              <div className="flex items-center gap-1.5 ml-auto">
                <button
                  type="button"
                  onClick={handleToggleTTS}
                  id={`tts-button-${message.id}`}
                  title={isSpeaking ? 'Stop speaking' : 'Speak response via Android TextToSpeech'}
                  className={`flex items-center gap-1.5 rounded-lg px-2.5 py-1 text-xs font-medium border transition-all cursor-pointer ${
                    isSpeaking
                      ? 'bg-indigo-600 text-white border-indigo-400 shadow-lg shadow-indigo-500/40 animate-pulse'
                      : 'bg-white/5 hover:bg-white/10 text-zinc-300 hover:text-white border-white/10'
                  }`}
                >
                  {isSpeaking ? (
                    <>
                      <VolumeX className="w-3.5 h-3.5" />
                      <span className="text-[11px]">Stop</span>
                    </>
                  ) : (
                    <>
                      <Volume2 className="w-3.5 h-3.5 text-indigo-400" />
                      <span className="text-[11px]">Speak</span>
                    </>
                  )}
                </button>

                <button
                  type="button"
                  onClick={handleCopy}
                  title="Copy text"
                  className="p-1.5 rounded-lg text-zinc-400 hover:text-zinc-200 hover:bg-white/10 border border-transparent hover:border-white/10 transition-colors cursor-pointer"
                >
                  {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                </button>
              </div>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};
