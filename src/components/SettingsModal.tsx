import React from 'react';
import { AppSettings } from '../types';
import {
  X,
  Settings,
  Languages,
  Volume2,
  Cpu,
  Zap,
  ShieldCheck,
  RotateCcw,
} from 'lucide-react';

interface SettingsModalProps {
  isOpen: boolean;
  onClose: () => void;
  settings: AppSettings;
  onSaveSettings: (settings: AppSettings) => void;
}

export const SettingsModal: React.FC<SettingsModalProps> = ({
  isOpen,
  onClose,
  settings,
  onSaveSettings,
}) => {
  const [localSettings, setLocalSettings] = React.useState<AppSettings>(settings);

  React.useEffect(() => {
    setLocalSettings(settings);
  }, [settings]);

  if (!isOpen) return null;

  const handleSave = () => {
    onSaveSettings(localSettings);
    onClose();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-6 bg-black/75 backdrop-blur-md animate-in fade-in duration-150" id="settings-modal">
      <div className="bg-[#0e0e12]/95 backdrop-blur-2xl border border-white/15 rounded-3xl w-full max-w-lg shadow-2xl shadow-black/90 overflow-hidden flex flex-col">
        {/* Header */}
        <div className="px-5 py-4 border-b border-white/10 flex items-center justify-between bg-white/[0.02]">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-2xl bg-indigo-500/10 border border-indigo-500/20 text-indigo-400">
              <Settings className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base font-bold text-white">Assistant & Engine Settings</h2>
              <p className="text-xs text-zinc-400">Configure offline voice, TTS synthesis, and llama.cpp parameters.</p>
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

        {/* Content */}
        <div className="p-5 space-y-3.5 text-xs overflow-y-auto max-h-[70vh]">
          {/* Language selection */}
          <div className="p-4 rounded-2xl bg-white/[0.02] border border-white/10 space-y-2 backdrop-blur-md">
            <div className="flex items-center justify-between">
              <label className="font-semibold text-zinc-200 flex items-center gap-1.5">
                <Languages className="w-4 h-4 text-indigo-400" />
                Primary Language / Voice Recognition
              </label>
            </div>
            <select
              value={localSettings.language}
              onChange={e => setLocalSettings({ ...localSettings, language: e.target.value as any })}
              className="w-full bg-[#0e0e12] border border-white/10 text-zinc-200 rounded-xl p-2.5 text-xs focus:outline-none focus:border-indigo-500 cursor-pointer"
            >
              <option value="auto">Auto (English & Telugu Mixed)</option>
              <option value="en-US">English (US / Global)</option>
              <option value="te-IN">Telugu (తెలుగు)</option>
            </select>
            <p className="text-[11px] text-zinc-400">
              Whisper STT supports English, Telugu, and mixed code-switching speech.
            </p>
          </div>

          {/* Auto speak */}
          <div className="p-4 rounded-2xl bg-white/[0.02] border border-white/10 space-y-3 backdrop-blur-md">
            <div className="flex items-center justify-between">
              <div>
                <div className="font-semibold text-zinc-200 flex items-center gap-1.5">
                  <Volume2 className="w-4 h-4 text-teal-400" />
                  Auto-Speak Responses (Android TTS)
                </div>
                <div className="text-[11px] text-zinc-400 mt-0.5">
                  Automatically announce responses and action confirmations.
                </div>
              </div>
              <input
                type="checkbox"
                checked={localSettings.autoSpeakResponse}
                onChange={e => setLocalSettings({ ...localSettings, autoSpeakResponse: e.target.checked })}
                className="rounded border-white/20 bg-white/5 text-indigo-500 focus:ring-0 w-4 h-4 cursor-pointer"
              />
            </div>

            {/* Speech rate */}
            <div className="space-y-1.5 pt-2 border-t border-white/10">
              <div className="flex justify-between text-[11px] text-zinc-400">
                <span>TTS Speech Rate</span>
                <span className="font-mono text-zinc-200">{localSettings.speechRate}x</span>
              </div>
              <input
                type="range"
                min="0.75"
                max="1.5"
                step="0.05"
                value={localSettings.speechRate}
                onChange={e => setLocalSettings({ ...localSettings, speechRate: parseFloat(e.target.value) })}
                className="w-full accent-indigo-500 cursor-pointer"
              />
            </div>
          </div>

          {/* llama.cpp threads */}
          <div className="p-4 rounded-2xl bg-white/[0.02] border border-white/10 space-y-2 backdrop-blur-md">
            <div className="flex items-center justify-between">
              <div className="font-semibold text-zinc-200 flex items-center gap-1.5">
                <Cpu className="w-4 h-4 text-amber-400" />
                llama.cpp CPU Threads
              </div>
              <span className="font-mono text-amber-400 font-bold">{localSettings.inferenceThreads} Cores</span>
            </div>
            <input
              type="range"
              min="2"
              max="8"
              step="1"
              value={localSettings.inferenceThreads}
              onChange={e => setLocalSettings({ ...localSettings, inferenceThreads: parseInt(e.target.value) })}
              className="w-full accent-amber-500 cursor-pointer"
            />
            <p className="text-[11px] text-zinc-400">
              Allocates CPU big/LITTLE cores for maximum tokens/sec without throttling device temperature.
            </p>
          </div>

          {/* Performance stats toggle */}
          <div className="p-4 rounded-2xl bg-white/[0.02] border border-white/10 flex items-center justify-between backdrop-blur-md">
            <div>
              <div className="font-semibold text-zinc-200 flex items-center gap-1.5">
                <Zap className="w-4 h-4 text-indigo-400" />
                Show Token Performance Stats
              </div>
              <div className="text-[11px] text-zinc-400">
                Display TTFT, tokens/sec, and token counts on assistant responses.
              </div>
            </div>
            <input
              type="checkbox"
              checked={localSettings.showPerformanceStats}
              onChange={e => setLocalSettings({ ...localSettings, showPerformanceStats: e.target.checked })}
              className="rounded border-white/20 bg-white/5 text-indigo-500 focus:ring-0 w-4 h-4 cursor-pointer"
            />
          </div>
        </div>

        {/* Footer */}
        <div className="px-5 py-3.5 border-t border-white/10 bg-white/[0.02] flex items-center justify-between">
          <div className="flex items-center gap-1.5 text-[11px] text-zinc-400">
            <ShieldCheck className="w-3.5 h-3.5 text-emerald-400" />
            <span>Saved locally to device storage</span>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              onClick={onClose}
              className="px-3.5 py-1.5 rounded-xl bg-white/5 hover:bg-white/10 text-zinc-300 font-medium text-xs border border-white/10 transition-colors cursor-pointer"
            >
              Cancel
            </button>
            <button
              type="button"
              onClick={handleSave}
              className="px-4 py-1.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-semibold text-xs shadow-lg shadow-indigo-500/30 transition-transform active:scale-95 cursor-pointer"
            >
              Save Settings
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
