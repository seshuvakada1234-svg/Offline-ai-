import React from 'react';
import { ModelId, ModelInfo } from '../types';
import { ChevronDown, HardDrive, Sparkles, CheckCircle2, Download, Cpu } from 'lucide-react';

interface ModelSelectorProps {
  models: ModelInfo[];
  selectedModelId: ModelId;
  onSelectModel: (id: ModelId) => void;
  onOpenModelManager: () => void;
}

export const ModelSelector: React.FC<ModelSelectorProps> = ({
  models,
  selectedModelId,
  onSelectModel,
  onOpenModelManager,
}) => {
  const [isOpen, setIsOpen] = React.useState(false);
  const selectedModel = models.find(m => m.id === selectedModelId) || models[0];

  const llamaModels = models.filter(m => m.backend === 'llama.cpp');

  return (
    <div className="relative inline-block text-left" id="model-selector-dropdown">
      <button
        type="button"
        id="model-selector-button"
        onClick={() => setIsOpen(!isOpen)}
        className="flex items-center gap-2 px-3 py-1.5 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-zinc-100 text-xs sm:text-sm font-medium transition-all shadow-sm cursor-pointer"
      >
        <span className="flex items-center gap-1.5">
          <span className="w-2 h-2 rounded-full bg-emerald-500 shadow-[0_0_8px_rgba(16,185,129,0.6)] animate-pulse"></span>
          <span className="font-semibold text-zinc-100">{selectedModel?.name}</span>
          <span className="hidden sm:inline text-zinc-400 text-xs font-mono">({selectedModel?.quant})</span>
        </span>
        <ChevronDown className={`w-3.5 h-3.5 text-zinc-400 transition-transform ${isOpen ? 'rotate-180' : ''}`} />
      </button>

      {isOpen && (
        <>
          <div className="fixed inset-0 z-40" onClick={() => setIsOpen(false)} />
          <div className="absolute right-0 sm:left-0 mt-2 w-72 sm:w-80 rounded-2xl bg-[#0e0e12]/95 backdrop-blur-2xl border border-white/15 shadow-2xl shadow-black/80 z-50 p-2 overflow-hidden animate-in fade-in zoom-in-95 duration-100">
            <div className="px-2 py-1.5 border-b border-white/10 mb-1 flex items-center justify-between">
              <span className="text-[10px] font-bold uppercase tracking-widest text-zinc-500">Local LLM Models</span>
              <span className="text-[10px] text-indigo-400 bg-indigo-500/10 border border-indigo-500/20 px-1.5 py-0.5 rounded font-mono">llama.cpp</span>
            </div>

            <div className="space-y-1.5">
              {llamaModels.map(model => {
                const isSelected = model.id === selectedModelId;
                const isReady = model.state === 'READY';

                return (
                  <button
                    key={model.id}
                    id={`model-option-${model.id}`}
                    type="button"
                    onClick={() => {
                      if (isReady) {
                        onSelectModel(model.id);
                        setIsOpen(false);
                      } else {
                        onOpenModelManager();
                        setIsOpen(false);
                      }
                    }}
                    className={`w-full text-left px-3 py-2.5 rounded-xl flex items-center justify-between transition-all border ${
                      isSelected
                        ? 'bg-indigo-500/15 border-indigo-500/30 text-zinc-100'
                        : 'bg-white/[0.02] hover:bg-white/[0.07] border-white/5 text-zinc-200'
                    }`}
                  >
                    <div className="min-w-0 flex-1 pr-2">
                      <div className="flex items-center gap-1.5">
                        <span className="font-semibold text-xs sm:text-sm truncate">{model.name}</span>
                        {model.isDefault && (
                          <span className="text-[9px] bg-indigo-500/20 text-indigo-300 border border-indigo-500/30 px-1 rounded font-medium">
                            Default
                          </span>
                        )}
                      </div>
                      <div className="text-[11px] text-zinc-400 truncate">{model.tag}</div>
                      <div className="text-[10px] text-zinc-500 font-mono mt-0.5">
                        {model.sizeFormatted} • {model.quant}
                      </div>
                    </div>

                    <div className="flex-shrink-0">
                      {isReady ? (
                        <span className="inline-flex items-center gap-1 text-[10px] text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded-full border border-emerald-500/20 font-mono font-medium">
                          <CheckCircle2 className="w-3 h-3" />
                          Ready
                        </span>
                      ) : (
                        <span className="inline-flex items-center gap-1 text-[10px] text-indigo-400 bg-indigo-500/10 px-2 py-0.5 rounded-full border border-indigo-500/20 font-mono font-medium">
                          <Download className="w-3 h-3" />
                          Get
                        </span>
                      )}
                    </div>
                  </button>
                );
              })}
            </div>

            <div className="mt-2 pt-2 border-t border-white/10">
              <button
                id="open-model-manager-from-dropdown"
                type="button"
                onClick={() => {
                  setIsOpen(false);
                  onOpenModelManager();
                }}
                className="w-full py-2 px-3 rounded-xl bg-white/5 hover:bg-white/10 border border-white/10 text-zinc-200 text-xs flex items-center justify-center gap-1.5 font-medium transition-all cursor-pointer active:scale-98"
              >
                <HardDrive className="w-3.5 h-3.5 text-zinc-400" />
                Manage Storage & Downloads
              </button>
            </div>
          </div>
        </>
      )}
    </div>
  );
};
