import React from 'react';
import { ModelId, ModelInfo } from '../types';
import { ModelSelector } from './ModelSelector';
import {
  HardDrive,
  Terminal,
  Settings,
  ShieldCheck,
  CheckCircle,
  FlaskConical,
  Radio,
  Sparkles,
} from 'lucide-react';

interface HeaderProps {
  models: ModelInfo[];
  selectedModelId: ModelId;
  onSelectModel: (id: ModelId) => void;
  onOpenModelManager: () => void;
  onOpenLogs: () => void;
  onOpenTests: () => void;
  onOpenSettings: () => void;
  storageStats: { freeFormatted: string; usedFormatted: string };
  isOnline: boolean;
}

export const Header: React.FC<HeaderProps> = ({
  models,
  selectedModelId,
  onSelectModel,
  onOpenModelManager,
  onOpenLogs,
  onOpenTests,
  onOpenSettings,
  storageStats,
}) => {
  return (
    <header className="sticky top-0 z-30 bg-white/[0.03] backdrop-blur-xl border-b border-white/10 px-3 sm:px-6 py-2.5 flex items-center justify-between shadow-lg shadow-black/40" id="app-header">
      <div className="flex items-center gap-2 sm:gap-4">
        <div className="flex items-center gap-2.5">
          <div className="w-8 h-8 rounded-lg bg-indigo-600 shadow-lg shadow-indigo-500/30 flex items-center justify-center text-white">
            <Radio className="w-4 h-4 text-white" />
          </div>
          <div>
            <div className="flex items-center gap-2">
              <h1 className="text-sm sm:text-base font-bold tracking-tight text-white flex items-center gap-1.5">
                MyAI <span className="text-[10px] font-bold text-indigo-400 bg-indigo-500/10 border border-indigo-500/20 px-1.5 py-0.5 rounded tracking-wider uppercase">OFFLINE</span>
              </h1>
            </div>
          </div>
        </div>

        {/* Local Session Active Pill */}
        <div className="hidden md:flex items-center gap-2 rounded-full bg-zinc-900/80 px-3 py-1 border border-white/10 shadow-sm">
          <div className="h-2 w-2 animate-pulse rounded-full bg-indigo-400 shadow-[0_0_8px_rgba(99,102,241,0.6)]"></div>
          <span className="text-xs font-medium text-zinc-300">Local Session Active</span>
        </div>

        <div className="h-4 w-px bg-white/10 mx-0.5 hidden sm:block"></div>

        {/* Model Selector Dropdown */}
        <ModelSelector
          models={models}
          selectedModelId={selectedModelId}
          onSelectModel={onSelectModel}
          onOpenModelManager={onOpenModelManager}
        />
      </div>

      <div className="flex items-center gap-1.5 sm:gap-2">
        {/* Test Suite Button */}
        <button
          id="open-tests-button"
          type="button"
          onClick={onOpenTests}
          title="Run Verification Tests (Model Manager, LLM, STT, Actions, TTS)"
          className="p-2 rounded-xl bg-white/5 hover:bg-white/10 text-zinc-300 hover:text-white transition-all text-xs font-medium flex items-center gap-1.5 border border-white/10 shadow-sm active:scale-95 cursor-pointer"
        >
          <FlaskConical className="w-4 h-4 text-indigo-400" />
          <span className="hidden lg:inline">Test Suite</span>
        </button>

        {/* Engine Logcat Button */}
        <button
          id="open-logs-button"
          type="button"
          onClick={onOpenLogs}
          title="View llama.cpp Engine Logs & Intent Logcat"
          className="p-2 rounded-xl bg-white/5 hover:bg-white/10 text-zinc-300 hover:text-white transition-all text-xs font-medium flex items-center gap-1.5 border border-white/10 shadow-sm active:scale-95 cursor-pointer"
        >
          <Terminal className="w-4 h-4 text-emerald-400" />
          <span className="hidden md:inline">Engine Logs</span>
        </button>

        {/* Model Manager / Storage Button */}
        <button
          id="open-model-manager-button"
          type="button"
          onClick={onOpenModelManager}
          title="Manage Offline GGUF Models & Storage"
          className="p-2 rounded-xl bg-white/5 hover:bg-white/10 text-zinc-300 hover:text-white transition-all text-xs font-medium flex items-center gap-1.5 border border-white/10 shadow-sm active:scale-95 cursor-pointer"
        >
          <HardDrive className="w-4 h-4 text-amber-400" />
          <span className="hidden sm:inline">Storage</span>
          <span className="text-[10px] text-zinc-400 font-mono hidden xl:inline">({storageStats.freeFormatted} free)</span>
        </button>

        {/* Settings */}
        <button
          id="open-settings-button"
          type="button"
          onClick={onOpenSettings}
          title="Settings & Voice Config"
          className="p-2 rounded-xl bg-white/5 hover:bg-white/10 text-zinc-300 hover:text-white transition-all border border-white/10 shadow-sm active:scale-95 cursor-pointer"
        >
          <Settings className="w-4 h-4" />
        </button>
      </div>
    </header>
  );
};
