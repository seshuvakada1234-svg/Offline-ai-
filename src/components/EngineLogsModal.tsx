import React from 'react';
import { EngineLogEntry, EngineLogTag } from '../types';
import { logger } from '../services/loggerService';
import {
  X,
  Terminal,
  Trash2,
  Copy,
  Check,
  Filter,
  Search,
  Cpu,
  Clock,
  Sparkles,
} from 'lucide-react';

interface EngineLogsModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const EngineLogsModal: React.FC<EngineLogsModalProps> = ({ isOpen, onClose }) => {
  const [logs, setLogs] = React.useState<EngineLogEntry[]>([]);
  const [selectedTag, setSelectedTag] = React.useState<string>('ALL');
  const [searchQuery, setSearchQuery] = React.useState<string>('');
  const [copied, setCopied] = React.useState(false);

  React.useEffect(() => {
    const unsub = logger.subscribe(newLogs => {
      setLogs(newLogs);
    });
    return unsub;
  }, []);

  if (!isOpen) return null;

  const filteredLogs = logs.filter(entry => {
    const matchesTag = selectedTag === 'ALL' || entry.tag === selectedTag;
    const matchesSearch =
      !searchQuery ||
      entry.detail.toLowerCase().includes(searchQuery.toLowerCase()) ||
      entry.tag.toLowerCase().includes(searchQuery.toLowerCase());
    return matchesTag && matchesSearch;
  });

  const handleCopyLogs = () => {
    const formatted = logs
      .map(l => `[${l.timeFormatted}][${l.tag}] ${l.detail}`)
      .join('\n');
    navigator.clipboard.writeText(formatted);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const getTagColor = (tag: EngineLogTag) => {
    switch (tag) {
      case 'MODEL_LOAD_START':
      case 'MODEL_LOAD_END':
        return 'bg-blue-950 text-blue-400 border-blue-800';
      case 'PROMPT_START':
      case 'PROMPT_END':
        return 'bg-indigo-950 text-indigo-400 border-indigo-800';
      case 'INFERENCE_START':
      case 'FIRST_TOKEN':
      case 'INFERENCE_END':
        return 'bg-emerald-950 text-emerald-400 border-emerald-800';
      case 'ACTION_PARSED':
      case 'ACTION_EXECUTED':
        return 'bg-amber-950 text-amber-400 border-amber-800';
      case 'VOICE_START':
      case 'VOICE_TRANSCRIPT':
        return 'bg-purple-950 text-purple-400 border-purple-800';
      case 'TTS_START':
      case 'TTS_END':
        return 'bg-teal-950 text-teal-400 border-teal-800';
      default:
        return 'bg-zinc-800 text-zinc-400 border-zinc-700';
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-6 bg-black/75 backdrop-blur-md animate-in fade-in duration-150" id="engine-logs-modal">
      <div className="bg-[#0e0e12]/95 backdrop-blur-2xl border border-white/15 rounded-3xl w-full max-w-4xl max-h-[90vh] flex flex-col shadow-2xl shadow-black/90 overflow-hidden font-mono">
        {/* Header */}
        <div className="px-5 py-4 border-b border-white/10 flex items-center justify-between bg-white/[0.02] font-sans">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-2xl bg-indigo-500/10 border border-indigo-500/20 text-indigo-400">
              <Terminal className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-sm sm:text-base font-bold text-white">llama.cpp Engine & Logcat Monitor</h2>
              <p className="text-xs text-zinc-400 font-sans">
                Real-time JNI pipeline events, TTFT benchmarks, token streaming, and Intent actions.
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-2 rounded-xl text-zinc-400 hover:text-white hover:bg-white/10 border border-transparent hover:border-white/10 transition-colors cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Toolbar */}
        <div className="p-3.5 bg-white/[0.02] border-b border-white/10 flex flex-wrap items-center justify-between gap-2.5 font-sans">
          <div className="flex items-center gap-2 flex-1 min-w-[200px]">
            <Search className="w-4 h-4 text-zinc-500 ml-1" />
            <input
              type="text"
              placeholder="Filter log messages..."
              value={searchQuery}
              onChange={e => setSearchQuery(e.target.value)}
              className="bg-black/40 border border-white/10 text-zinc-200 text-xs rounded-xl px-3 py-2 w-full focus:outline-none focus:border-indigo-500 font-mono shadow-inner"
            />
          </div>

          <div className="flex items-center gap-2">
            <select
              value={selectedTag}
              onChange={e => setSelectedTag(e.target.value)}
              className="bg-[#0e0e12] border border-white/10 text-zinc-200 text-xs rounded-xl px-3 py-2 focus:outline-none cursor-pointer font-mono"
            >
              <option value="ALL">ALL TAGS</option>
              <option value="MODEL_LOAD_START">MODEL_LOAD_START</option>
              <option value="MODEL_LOAD_END">MODEL_LOAD_END</option>
              <option value="PROMPT_START">PROMPT_START</option>
              <option value="PROMPT_END">PROMPT_END</option>
              <option value="INFERENCE_START">INFERENCE_START</option>
              <option value="FIRST_TOKEN">FIRST_TOKEN</option>
              <option value="INFERENCE_END">INFERENCE_END</option>
              <option value="ACTION_PARSED">ACTION_PARSED</option>
              <option value="ACTION_EXECUTED">ACTION_EXECUTED</option>
              <option value="VOICE_START">VOICE_START</option>
              <option value="VOICE_TRANSCRIPT">VOICE_TRANSCRIPT</option>
              <option value="TTS_START">TTS_START</option>
              <option value="TTS_END">TTS_END</option>
            </select>

            <button
              type="button"
              onClick={handleCopyLogs}
              className="px-3 py-2 rounded-xl bg-white/5 hover:bg-white/10 text-zinc-200 text-xs flex items-center gap-1.5 border border-white/10 font-medium cursor-pointer transition-all active:scale-95"
            >
              {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
              <span>{copied ? 'Copied' : 'Copy'}</span>
            </button>

            <button
              type="button"
              onClick={() => logger.clear()}
              title="Clear all logs"
              className="p-2 rounded-xl text-zinc-400 hover:text-red-400 hover:bg-red-500/10 border border-white/10 transition-colors cursor-pointer"
            >
              <Trash2 className="w-4 h-4" />
            </button>
          </div>
        </div>

        {/* Log Entries */}
        <div className="flex-1 overflow-y-auto p-4 space-y-1.5 text-xs">
          {filteredLogs.length === 0 ? (
            <div className="text-center py-12 text-zinc-500 font-sans">
              No logs recorded yet. Send a message or speak into the microphone to view pipeline logs.
            </div>
          ) : (
            filteredLogs.map(entry => (
              <div
                key={entry.id}
                className="p-2.5 rounded-xl bg-white/[0.02] hover:bg-white/[0.05] border border-white/5 flex items-start gap-2.5 transition-colors backdrop-blur-sm"
              >
                <span className="text-[10px] text-zinc-500 whitespace-nowrap pt-0.5 font-mono">{entry.timeFormatted}</span>
                <span
                  className={`text-[9px] px-2 py-0.5 rounded-md border font-semibold whitespace-nowrap font-mono ${getTagColor(
                    entry.tag
                  )}`}
                >
                  {entry.tag}
                </span>
                <span className="text-zinc-200 flex-1 break-all leading-relaxed font-mono">{entry.detail}</span>
              </div>
            ))
          )}
        </div>

        {/* Footer */}
        <div className="px-5 py-3 border-t border-white/10 bg-white/[0.02] flex items-center justify-between text-[11px] text-zinc-400 font-sans">
          <span>{filteredLogs.length} events logged</span>
          <span className="text-emerald-400 font-mono flex items-center gap-1.5">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-400 animate-pulse"></span>
            llama.cpp JNI bindings active
          </span>
        </div>
      </div>
    </div>
  );
};
