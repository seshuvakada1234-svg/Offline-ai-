import React from 'react';
import { Message } from '../types';
import { ActionCard } from './ActionCard';
import { tts } from '../services/ttsService';
import {
  Volume2,
  Square,
  Copy,
  Check,
  Mic,
  Cpu,
  Zap,
  Sparkles,
  Bot,
  User,
  Loader2,
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
  const isThinking = !isUser && message.isStreaming && !message.content;

  if (isUser) {
    // USER MESSAGE - ALIGNED TO RIGHT
    return (
      <div id={`message-${message.id}`} className="py-2 px-3 sm:px-6 flex justify-end">
        <div className="max-w-[82%] sm:max-w-[75%] flex flex-col items-end">
          <div className="bg-indigo-600 text-white rounded-2xl rounded-br-sm px-4 py-2.5 text-xs sm:text-sm leading-relaxed shadow-md shadow-indigo-600/10">
            {message.isVoiceInput && (
              <div className="flex items-center gap-1 text-[10px] text-indigo-200 mb-1 font-medium">
                <Mic className="w-2.5 h-2.5" />
                <span>Voice Transcript</span>
              </div>
            )}
            <div className="whitespace-pre-wrap break-words">{message.content}</div>
          </div>
          <span className="text-[10px] text-zinc-500 font-mono mt-1 px-1">
            {new Date(message.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
          </span>
        </div>
      </div>
    );
  }

  // AI MESSAGE - ALIGNED TO LEFT
  return (
    <div id={`message-${message.id}`} className="py-2.5 px-3 sm:px-6 flex justify-start gap-3">
      {/* Small AI Avatar */}
      <div className="flex-shrink-0 mt-0.5">
        <div className="h-7 w-7 rounded-full bg-indigo-600/20 border border-indigo-500/30 flex items-center justify-center text-[10px] font-bold text-indigo-400">
          <Sparkles className="w-3.5 h-3.5" />
        </div>
      </div>

      <div className="max-w-[88%] sm:max-w-[82%] space-y-1.5">
        {/* Model Tag */}
        <div className="flex items-center gap-2">
          <span className="text-xs font-bold text-zinc-200">MyAI</span>
          <span className="text-[10px] bg-zinc-800 text-zinc-400 border border-white/5 px-1.5 py-0.5 rounded font-mono">
            {activeModelName}
          </span>
          <span className="text-[10px] text-zinc-500 font-mono">
            {new Date(message.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
          </span>
        </div>

        {/* Bubble / Streaming Content */}
        {isThinking ? (
          <div className="inline-flex items-center gap-2 px-3 py-2 rounded-xl bg-zinc-900 border border-white/10 text-xs text-zinc-400 italic">
            <Loader2 className="w-3.5 h-3.5 text-indigo-400 animate-spin" />
            <span>Thinking...</span>
          </div>
        ) : (
          <div className="text-xs sm:text-sm text-zinc-200 leading-relaxed font-sans">
            <div className="whitespace-pre-wrap break-words">
              {message.content}
              {message.isStreaming && (
                <span className="inline-block w-1.5 h-4 ml-1 bg-indigo-400 animate-pulse align-middle" />
              )}
            </div>

            {/* Action Card if present */}
            {message.action && (
              <div className="mt-3">
                <ActionCard action={message.action} onActionUpdated={onActionUpdated} />
              </div>
            )}

            {/* AI Bottom Action Bar */}
            {!message.isStreaming && (
              <div className="mt-3 pt-2 flex flex-wrap items-center gap-2 border-t border-white/5">
                <button
                  onClick={handleCopy}
                  className={`inline-flex items-center gap-1 text-[11px] px-2 py-1 rounded-md border transition-colors ${
                    copied
                      ? 'bg-teal-500/10 text-teal-300 border-teal-500/30'
                      : 'bg-zinc-900/60 text-zinc-400 border-white/10 hover:text-zinc-200 hover:bg-zinc-800'
                  }`}
                  title="Copy response"
                >
                  {copied ? <Check className="w-3 h-3 text-teal-400" /> : <Copy className="w-3 h-3" />}
                  <span>{copied ? 'Copied' : 'Copy'}</span>
                </button>

                <button
                  onClick={handleToggleTTS}
                  className={`inline-flex items-center gap-1 text-[11px] px-2 py-1 rounded-md border transition-colors ${
                    isSpeaking
                      ? 'bg-amber-500/15 text-amber-300 border-amber-500/30'
                      : 'bg-zinc-900/60 text-zinc-400 border-white/10 hover:text-zinc-200 hover:bg-zinc-800'
                  }`}
                  title={isSpeaking ? 'Stop speaking' : 'Speak response'}
                >
                  {isSpeaking ? <Square className="w-3 h-3 text-amber-400" /> : <Volume2 className="w-3 h-3" />}
                  <span>{isSpeaking ? 'Stop' : 'Speak'}</span>
                </button>

                {message.metrics && (
                  <div className="flex items-center gap-1.5 text-[10px] text-zinc-500 font-mono ml-auto">
                    <span className="text-teal-400 font-semibold">{message.metrics.tokensPerSec} t/s</span>
                    <span>•</span>
                    <span>{message.metrics.timeToFirstTokenMs}ms TTFT</span>
                    <span>•</span>
                    <span>{message.metrics.totalTokens} tokens</span>
                  </div>
                )}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};
