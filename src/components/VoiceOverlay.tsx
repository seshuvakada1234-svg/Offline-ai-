import React from 'react';
import { VoiceState } from '../types';
import { whisperSTT } from '../services/whisperSTT';
import {
  Mic,
  MicOff,
  X,
  Volume2,
  Sparkles,
  Radio,
  ExternalLink,
  ChevronRight,
  Languages,
} from 'lucide-react';

interface VoiceOverlayProps {
  isOpen: boolean;
  onClose: () => void;
  voiceState: VoiceState;
  transcript: string;
  assistantResponse: string;
  audioLevel: number;
  onStartListening: () => void;
  onStopListening: () => void;
  onSelectPrompt: (prompt: string) => void;
  selectedLanguage: 'en-US' | 'te-IN' | 'auto';
  onChangeLanguage: (lang: 'en-US' | 'te-IN' | 'auto') => void;
}

export const VoiceOverlay: React.FC<VoiceOverlayProps> = ({
  isOpen,
  onClose,
  voiceState,
  transcript,
  assistantResponse,
  audioLevel,
  onStartListening,
  onStopListening,
  onSelectPrompt,
  selectedLanguage,
  onChangeLanguage,
}) => {
  if (!isOpen) return null;

  const isListening = voiceState === 'LISTENING';
  const isTranscribing = voiceState === 'TRANSCRIBING';
  const isThinking = voiceState === 'THINKING';
  const isSpeaking = voiceState === 'SPEAKING';
  const isActionExecuting = voiceState === 'ACTION_EXECUTING';

  const quickPrompts = [
    { label: 'Open YouTube & search Telugu songs', text: 'Open YouTube and search Telugu songs', tag: 'YouTube Action' },
    { label: 'Open YouTube', text: 'Open YouTube', tag: 'App Intent' },
    { label: 'Search YouTube for Telugu melodies', text: 'Search YouTube for Telugu melodies', tag: 'Voice Search' },
    { label: 'Open Chrome', text: 'Open Chrome', tag: 'App Intent' },
    { label: 'Open Settings', text: 'Open Settings', tag: 'Android Settings' },
    { label: 'What is 2+2?', text: 'What is 2+2?', tag: 'Reasoning' },
    { label: 'Explain photosynthesis in one sentence', text: 'Explain photosynthesis in one sentence.', tag: 'Summary' },
    { label: 'Explain quantum computing', text: 'Explain quantum computing', tag: 'Knowledge' },
    { label: 'యూట్యూబ్‌లో తెలుగు పాటలు (Telugu)', text: 'యూట్యూబ్‌లో తెలుగు పాటలు', tag: 'Telugu Voice' },
  ];

  return (
    <div className="fixed inset-0 z-50 flex flex-col bg-[#0a0a0c]/90 backdrop-blur-2xl animate-in fade-in duration-200" id="voice-assistant-overlay">
      {/* Ambient background glows */}
      <div className="pointer-events-none absolute top-1/3 left-1/2 -translate-x-1/2 -translate-y-1/2 w-[480px] h-[480px] bg-indigo-600/15 rounded-full blur-[100px]" />

      {/* Top bar */}
      <div className="p-4 sm:p-6 flex items-center justify-between border-b border-white/10 z-10">
        <div className="flex items-center gap-3">
          <div className="w-8 h-8 rounded-xl bg-indigo-600 shadow-lg shadow-indigo-500/30 flex items-center justify-center text-white font-bold">
            <Radio className="w-4 h-4" />
          </div>
          <div>
            <h2 className="text-sm sm:text-base font-bold text-white flex items-center gap-2">
              MyAI Voice Assistant
              <span className="text-[10px] bg-indigo-500/15 text-indigo-300 border border-indigo-500/30 px-2 py-0.5 rounded-full font-mono">
                Whisper STT • Offline
              </span>
            </h2>
          </div>
        </div>

        <div className="flex items-center gap-3">
          {/* Language selector */}
          <div className="flex items-center bg-white/5 border border-white/10 rounded-xl p-1 text-xs backdrop-blur-md">
            <Languages className="w-3.5 h-3.5 text-zinc-400 ml-1.5 mr-1" />
            <select
              value={selectedLanguage}
              onChange={e => onChangeLanguage(e.target.value as any)}
              className="bg-transparent text-zinc-200 text-xs font-medium focus:outline-none cursor-pointer pr-2 py-0.5"
            >
              <option value="auto" className="bg-[#0e0e12] text-white">Auto (En / Te)</option>
              <option value="en-US" className="bg-[#0e0e12] text-white">English</option>
              <option value="te-IN" className="bg-[#0e0e12] text-white">Telugu (తెలుగు)</option>
            </select>
          </div>

          <button
            type="button"
            id="close-voice-overlay"
            onClick={onClose}
            className="p-2 rounded-xl text-zinc-400 hover:text-white hover:bg-white/10 border border-transparent hover:border-white/10 transition-colors cursor-pointer"
          >
            <X className="w-6 h-6" />
          </button>
        </div>
      </div>

      {/* Main Interactive Stage */}
      <div className="flex-1 flex flex-col items-center justify-center px-4 sm:px-8 py-6 text-center max-w-2xl mx-auto w-full z-10">
        {/* Visualizer Orb */}
        <div className="relative mb-8">
          {/* Animated Glow Rings based on Audio Level */}
          <div
            style={{
              transform: `scale(${1 + audioLevel * 0.9})`,
              opacity: 0.25 + audioLevel * 0.65,
            }}
            className={`absolute inset-0 rounded-full transition-transform duration-75 ${
              isListening ? 'bg-indigo-500 blur-3xl' : isSpeaking ? 'bg-teal-400 blur-3xl' : 'bg-white/10 blur-2xl'
            }`}
          />

          <button
            type="button"
            id="voice-orb-button"
            onClick={() => {
              if (isListening) onStopListening();
              else onStartListening();
            }}
            className={`relative w-28 h-28 sm:w-36 sm:h-36 rounded-full flex items-center justify-center transition-all duration-300 shadow-2xl cursor-pointer ${
              isListening
                ? 'bg-indigo-600 text-white scale-105 shadow-indigo-500/50 ring-4 ring-indigo-400/40'
                : isSpeaking
                ? 'bg-gradient-to-tr from-teal-500 to-indigo-600 text-white shadow-teal-500/40'
                : isTranscribing || isThinking
                ? 'bg-gradient-to-tr from-amber-600 to-indigo-600 text-white animate-pulse'
                : 'bg-white/5 hover:bg-white/10 text-zinc-300 border border-white/15 backdrop-blur-xl'
            }`}
          >
            {isListening ? (
              <Mic className="w-12 h-12 animate-pulse text-white" />
            ) : isSpeaking ? (
              <Volume2 className="w-12 h-12 animate-bounce text-white" />
            ) : isTranscribing || isThinking ? (
              <Sparkles className="w-12 h-12 animate-spin text-white" />
            ) : (
              <MicOff className="w-12 h-12 text-zinc-400" />
            )}
          </button>
        </div>

        {/* Status Indicator */}
        <div className="space-y-2.5 mb-6">
          <div className="text-lg sm:text-xl font-bold tracking-tight text-white flex items-center justify-center gap-2">
            {isListening && <span className="text-indigo-400">Listening...</span>}
            {isTranscribing && <span className="text-amber-300">Transcribing speech with Whisper...</span>}
            {isThinking && <span className="text-teal-300">Processing Intent with Qwen3...</span>}
            {isActionExecuting && <span className="text-indigo-300">Executing Android Intent...</span>}
            {isSpeaking && <span className="text-emerald-300">Speaking Answer (Android TTS)...</span>}
            {voiceState === 'IDLE' && <span className="text-zinc-300">Tap microphone to speak</span>}
          </div>

          {/* Transcript / Spoken prompt */}
          {transcript && (
            <div className="p-4 bg-white/[0.04] backdrop-blur-xl rounded-2xl border border-white/10 max-w-lg text-sm sm:text-base text-zinc-100 italic shadow-xl animate-in fade-in">
              "{transcript}"
            </div>
          )}

          {/* Assistant Response in voice mode */}
          {assistantResponse && (
            <div className="p-4 bg-indigo-500/10 backdrop-blur-xl rounded-2xl border border-indigo-500/20 max-w-lg text-sm text-zinc-200 mt-2 shadow-xl animate-in fade-in leading-relaxed">
              {assistantResponse}
            </div>
          )}
        </div>
      </div>

      {/* Bottom Quick Test Prompts */}
      <div className="p-4 sm:p-6 bg-white/[0.02] border-t border-white/10 backdrop-blur-xl z-10">
        <div className="max-w-4xl mx-auto space-y-2.5">
          <div className="flex items-center justify-between text-xs text-zinc-400 px-1">
            <span className="font-bold uppercase tracking-wider text-[10px] text-zinc-500">Quick Voice & Action Test Prompts</span>
            <span className="text-[11px] text-zinc-400">Tap to simulate spoken command</span>
          </div>

          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2.5">
            {quickPrompts.map((item, idx) => (
              <button
                key={idx}
                type="button"
                id={`voice-quick-prompt-${idx}`}
                onClick={() => onSelectPrompt(item.text)}
                className="p-3 rounded-2xl bg-white/[0.03] hover:bg-white/[0.08] text-left border border-white/10 transition-all hover:border-indigo-500/40 flex items-center justify-between group backdrop-blur-md cursor-pointer active:scale-98"
              >
                <div className="min-w-0 pr-2">
                  <div className="text-xs font-semibold text-zinc-200 truncate group-hover:text-indigo-300">
                    {item.label}
                  </div>
                  <div className="text-[10px] text-zinc-500 font-mono">{item.tag}</div>
                </div>
                <ChevronRight className="w-4 h-4 text-zinc-500 group-hover:text-indigo-400 flex-shrink-0 transition-transform group-hover:translate-x-0.5" />
              </button>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
};
