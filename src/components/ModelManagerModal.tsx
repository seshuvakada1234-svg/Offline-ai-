import React from 'react';
import { ModelId, ModelInfo } from '../types';
import { modelManager } from '../services/modelManager';
import {
  X,
  HardDrive,
  Download,
  Pause,
  Play,
  Trash2,
  CheckCircle2,
  AlertCircle,
  ShieldCheck,
  RefreshCw,
  Cpu,
  Zap,
  RotateCcw,
} from 'lucide-react';

interface ModelManagerModalProps {
  isOpen: boolean;
  onClose: () => void;
  models: ModelInfo[];
  selectedModelId: ModelId;
  onSelectModel: (id: ModelId) => void;
}

export const ModelManagerModal: React.FC<ModelManagerModalProps> = ({
  isOpen,
  onClose,
  models,
  selectedModelId,
  onSelectModel,
}) => {
  const [testCorruptChecksum, setTestCorruptChecksum] = React.useState(false);
  const storageStats = modelManager.getStorageStats();

  if (!isOpen) return null;

  const usedPercent = Math.min(100, (storageStats.usedBytes / storageStats.totalBytes) * 100);
  const modelsPercent = Math.min(100, (storageStats.modelsSizeBytes / storageStats.totalBytes) * 100);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-6 bg-black/75 backdrop-blur-md animate-in fade-in duration-150" id="model-manager-modal">
      <div className="bg-[#0e0e12]/95 backdrop-blur-2xl border border-white/15 rounded-3xl w-full max-w-4xl max-h-[90vh] flex flex-col shadow-2xl shadow-black/90 overflow-hidden">
        {/* Header */}
        <div className="px-5 py-4 border-b border-white/10 flex items-center justify-between bg-white/[0.02]">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-2xl bg-indigo-500/10 border border-indigo-500/20 text-indigo-400">
              <HardDrive className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base sm:text-lg font-bold text-white">Model Manager & Offline Storage</h2>
              <p className="text-xs text-zinc-400">
                GGUF weights are downloaded directly to local storage and executed via on-device llama.cpp & Whisper.
              </p>
            </div>
          </div>
          <button
            type="button"
            id="close-model-manager-modal"
            onClick={onClose}
            className="p-2 rounded-xl text-zinc-400 hover:text-white hover:bg-white/10 border border-transparent hover:border-white/10 transition-colors cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Storage Breakdown */}
        <div className="px-5 py-4 bg-white/[0.02] border-b border-white/10">
          <div className="flex items-center justify-between text-xs mb-2">
            <span className="font-semibold text-zinc-300">Device Storage Allocation (64 GB eMMC / UFS)</span>
            <span className="text-zinc-400 font-mono">
              <strong className="text-emerald-400">{storageStats.freeFormatted}</strong> free / {storageStats.totalFormatted} total
            </span>
          </div>

          {/* Bar */}
          <div className="w-full h-3 rounded-full bg-white/10 overflow-hidden flex shadow-inner">
            <div
              style={{ width: `${usedPercent - modelsPercent}%` }}
              className="h-full bg-zinc-600"
              title="Android OS & System Apps (22.4 GB)"
            />
            <div
              style={{ width: `${modelsPercent}%` }}
              className="h-full bg-gradient-to-r from-indigo-500 to-teal-400 transition-all duration-300 shadow-md"
              title={`MyAI Offline Models (${storageStats.modelsFormatted})`}
            />
          </div>

          <div className="flex flex-wrap items-center justify-between gap-2 mt-2.5 text-[11px] text-zinc-400">
            <div className="flex items-center gap-4">
              <span className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 rounded-sm bg-zinc-600"></span>
                System (22.4 GB)
              </span>
              <span className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 rounded-sm bg-indigo-500"></span>
                Offline Models ({storageStats.modelsFormatted})
              </span>
              <span className="flex items-center gap-1.5">
                <span className="w-2.5 h-2.5 rounded-sm bg-white/10 border border-white/10"></span>
                Free Space ({storageStats.freeFormatted})
              </span>
            </div>

            <label className="flex items-center gap-1.5 text-zinc-400 hover:text-zinc-200 cursor-pointer select-none text-[10px]">
              <input
                type="checkbox"
                checked={testCorruptChecksum}
                onChange={e => setTestCorruptChecksum(e.target.checked)}
                className="rounded border-white/20 bg-white/5 text-indigo-500 focus:ring-0"
              />
              Simulate Checksum Error test
            </label>
          </div>
        </div>

        {/* Model List */}
        <div className="flex-1 overflow-y-auto p-4 sm:p-5 space-y-3 divide-y divide-white/5">
          {models.map(model => {
            const isSelected = model.id === selectedModelId;
            const isReady = model.state === 'READY';
            const isDownloading = model.state === 'DOWNLOADING';
            const isChecking = model.state === 'CHECKING_STORAGE';
            const isVerifying = model.state === 'VERIFYING';
            const isPaused = model.state === 'PAUSED';
            const isError = model.state === 'ERROR';

            return (
              <div
                key={model.id}
                id={`model-card-${model.id}`}
                className={`pt-3.5 first:pt-0 rounded-2xl p-4 transition-all backdrop-blur-md ${
                  isSelected
                    ? 'bg-indigo-500/10 border border-indigo-500/30'
                    : 'bg-white/[0.02] hover:bg-white/[0.05] border border-white/10'
                }`}
              >
                <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                  <div className="space-y-1">
                    <div className="flex items-center gap-2 flex-wrap">
                      <h3 className="text-sm sm:text-base font-bold text-white flex items-center gap-1.5">
                        {model.name}
                        {model.isDefault && (
                          <span className="text-[10px] bg-indigo-500/20 text-indigo-300 border border-indigo-500/40 px-2 py-0.5 rounded-full font-medium">
                            Default Assistant
                          </span>
                        )}
                        {model.backend === 'whisper.cpp' && (
                          <span className="text-[10px] bg-purple-500/20 text-purple-300 border border-purple-500/40 px-2 py-0.5 rounded-full font-medium">
                            Whisper STT
                          </span>
                        )}
                      </h3>
                      <span className="text-xs text-zinc-400 font-medium">• {model.tag}</span>
                    </div>

                    <p className="text-xs text-zinc-400 leading-relaxed max-w-2xl">{model.description}</p>

                    <div className="flex flex-wrap items-center gap-2 text-[11px] font-mono text-zinc-400 pt-1">
                      <span className="bg-white/5 border border-white/10 px-2 py-0.5 rounded-md text-zinc-300">{model.sizeFormatted}</span>
                      <span className="bg-white/5 border border-white/10 px-2 py-0.5 rounded-md text-zinc-300">{model.quant}</span>
                      <span className="bg-white/5 border border-white/10 px-2 py-0.5 rounded-md text-zinc-300">Ctx: {model.contextSize}</span>
                      <span className="truncate max-w-xs text-zinc-500" title={model.filename}>
                        {model.filename}
                      </span>
                    </div>
                  </div>

                  {/* Actions Column */}
                  <div className="flex items-center gap-2 self-end sm:self-center flex-shrink-0">
                    {isReady ? (
                      <div className="flex items-center gap-2">
                        <button
                          type="button"
                          id={`select-model-${model.id}`}
                          onClick={() => {
                            onSelectModel(model.id);
                            modelManager.loadModel(model.id);
                          }}
                          disabled={isSelected}
                          className={`px-3.5 py-1.5 rounded-xl text-xs font-semibold flex items-center gap-1.5 transition-all cursor-pointer ${
                            isSelected
                              ? 'bg-indigo-500/20 text-indigo-300 border border-indigo-500/40 cursor-default'
                              : 'bg-white/5 hover:bg-white/10 text-zinc-200 border border-white/10'
                          }`}
                        >
                          <CheckCircle2 className="w-3.5 h-3.5 text-emerald-400" />
                          {isSelected ? 'Active Model' : 'Select'}
                        </button>

                        <button
                          type="button"
                          id={`delete-model-${model.id}`}
                          onClick={() => modelManager.deleteModel(model.id)}
                          title="Delete downloaded GGUF file from storage"
                          className="p-2 rounded-xl text-zinc-400 hover:text-red-400 hover:bg-red-500/10 border border-white/10 hover:border-red-500/20 transition-colors cursor-pointer"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    ) : isDownloading ? (
                      <div className="flex items-center gap-2">
                        <button
                          type="button"
                          id={`pause-model-${model.id}`}
                          onClick={() => modelManager.pauseDownload(model.id)}
                          className="px-3.5 py-1.5 rounded-xl bg-white/5 hover:bg-white/10 text-zinc-200 border border-white/10 text-xs font-medium flex items-center gap-1.5 cursor-pointer"
                        >
                          <Pause className="w-3.5 h-3.5 text-amber-400" />
                          Pause
                        </button>
                      </div>
                    ) : isPaused ? (
                      <div className="flex items-center gap-2">
                        <button
                          type="button"
                          id={`resume-model-${model.id}`}
                          onClick={() => modelManager.resumeDownload(model.id)}
                          className="px-3.5 py-1.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold flex items-center gap-1.5 shadow-lg shadow-indigo-500/30 cursor-pointer"
                        >
                          <Play className="w-3.5 h-3.5" />
                          Resume
                        </button>
                        <button
                          type="button"
                          onClick={() => modelManager.deleteModel(model.id)}
                          className="p-2 rounded-xl text-zinc-400 hover:text-red-400 hover:bg-white/10 cursor-pointer"
                        >
                          <Trash2 className="w-4 h-4" />
                        </button>
                      </div>
                    ) : isChecking || isVerifying ? (
                      <div className="flex items-center gap-2 text-xs text-amber-300 font-medium bg-amber-500/10 border border-amber-500/20 px-3 py-1.5 rounded-xl">
                        <RefreshCw className="w-3.5 h-3.5 animate-spin text-amber-400" />
                        {isChecking ? 'Checking Disk...' : 'Verifying SHA-256...'}
                      </div>
                    ) : (
                      <button
                        type="button"
                        id={`download-model-${model.id}`}
                        onClick={() => modelManager.startDownload(model.id, testCorruptChecksum)}
                        className="px-4 py-1.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-semibold flex items-center gap-1.5 shadow-lg shadow-indigo-500/30 transition-transform active:scale-95 cursor-pointer"
                      >
                        <Download className="w-3.5 h-3.5" />
                        Download
                      </button>
                    )}
                  </div>
                </div>

                {/* Progress / State Info Bar */}
                {(isDownloading || isPaused || isVerifying || isChecking || isError) && (
                  <div className="mt-3 pt-3 border-t border-white/10 space-y-2">
                    <div className="flex items-center justify-between text-xs">
                      <span className="text-zinc-400 font-medium flex items-center gap-2">
                        {isDownloading && (
                          <>
                            <span className="w-2 h-2 rounded-full bg-indigo-400 animate-ping"></span>
                            Streaming weights to disk ({model.downloadSpeed})
                          </>
                        )}
                        {isPaused && <span className="text-amber-400">Download Paused</span>}
                        {isVerifying && (
                          <span className="text-teal-400 flex items-center gap-1.5">
                            <ShieldCheck className="w-3.5 h-3.5" />
                            Calculating SHA-256 hash checksum...
                          </span>
                        )}
                        {isError && (
                          <span className="text-red-400 flex items-center gap-1.5">
                            <AlertCircle className="w-3.5 h-3.5" />
                            {model.errorMessage || 'Download error'}
                          </span>
                        )}
                      </span>

                      {!isError && (
                        <span className="font-mono text-zinc-300 text-xs">
                          {model.progress || 0}% (
                          {((model.downloadedBytes || 0) / (1024 * 1024)).toFixed(0)} /{' '}
                          {(model.sizeBytes / (1024 * 1024)).toFixed(0)} MB)
                        </span>
                      )}
                    </div>

                    <div className="w-full h-2 rounded-full bg-white/10 overflow-hidden">
                      <div
                        style={{ width: `${model.progress || 0}%` }}
                        className={`h-full transition-all duration-150 ${
                          isError ? 'bg-red-500' : isVerifying ? 'bg-teal-400' : isPaused ? 'bg-amber-400' : 'bg-indigo-500'
                        }`}
                      />
                    </div>
                  </div>
                )}
              </div>
            );
          })}
        </div>

        {/* Footer */}
        <div className="px-5 py-3.5 border-t border-white/10 bg-white/[0.02] flex items-center justify-between text-xs text-zinc-400">
          <div className="flex items-center gap-2">
            <ShieldCheck className="w-4 h-4 text-emerald-400" />
            <span>Zero Cloud Dependency • 100% On-Device Offline llama.cpp</span>
          </div>

          <button
            type="button"
            onClick={() => modelManager.resetAllToDefault()}
            className="flex items-center gap-1 text-zinc-400 hover:text-zinc-200 transition-colors text-[11px] cursor-pointer"
          >
            <RotateCcw className="w-3 h-3" />
            Reset State
          </button>
        </div>
      </div>
    </div>
  );
};
