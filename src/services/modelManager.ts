import { ModelId, ModelInfo, ModelState } from '../types';
import { INITIAL_MODELS, TOTAL_DEVICE_STORAGE_BYTES, SYSTEM_USED_STORAGE_BYTES } from '../data/models';

const STORAGE_KEY = 'myai_offline_models_v2';

class ModelManagerService {
  private models: ModelInfo[] = [];
  private downloadIntervals: Map<ModelId, any> = new Map();
  private listeners: Set<(models: ModelInfo[]) => void> = new Set();

  constructor() {
    this.loadState();
  }

  private loadState() {
    try {
      const saved = localStorage.getItem(STORAGE_KEY);
      if (saved) {
        const parsed: ModelInfo[] = JSON.parse(saved);
        // Merge with INITIAL_MODELS to preserve structure
        this.models = INITIAL_MODELS.map(initial => {
          const found = parsed.find(p => p.id === initial.id);
          if (found) {
            // Verify integrity on startup: If state was downloading or verifying when app closed, set to paused/not_installed
            let restoredState = found.state;
            if (restoredState === 'DOWNLOADING' || restoredState === 'CHECKING_STORAGE') {
              restoredState = 'PAUSED';
            }
            if (restoredState === 'VERIFYING') {
              restoredState = 'READY';
            }
            return {
              ...initial,
              state: restoredState,
              progress: found.progress ?? 0,
              downloadedBytes: found.downloadedBytes ?? 0,
              isLoaded: restoredState === 'READY' ? (found.isLoaded ?? initial.isLoaded) : false,
            };
          }
          return initial;
        });
      } else {
        this.models = [...INITIAL_MODELS];
      }
    } catch (e) {
      console.error('Failed to load model manager state from localStorage', e);
      this.models = [...INITIAL_MODELS];
    }
  }

  private saveState() {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(this.models));
      this.notify();
    } catch (e) {
      console.error('Failed to save model manager state', e);
    }
  }

  public subscribe(listener: (models: ModelInfo[]) => void): () => void {
    this.listeners.add(listener);
    listener([...this.models]);
    return () => {
      this.listeners.delete(listener);
    };
  }

  private notify() {
    const copy = [...this.models];
    this.listeners.forEach(l => l(copy));
  }

  public getModels(): ModelInfo[] {
    return [...this.models];
  }

  public getModel(id: ModelId): ModelInfo | undefined {
    return this.models.find(m => m.id === id);
  }

  public getInstalledModels(): ModelInfo[] {
    return this.models.filter(m => m.state === 'READY');
  }

  public getStorageStats() {
    const modelsSizeBytes = this.models
      .filter(m => m.state === 'READY' || m.state === 'DOWNLOADING' || m.state === 'PAUSED')
      .reduce((acc, m) => acc + (m.downloadedBytes || (m.state === 'READY' ? m.sizeBytes : 0)), 0);

    const usedBytes = SYSTEM_USED_STORAGE_BYTES + modelsSizeBytes;
    const freeBytes = Math.max(0, TOTAL_DEVICE_STORAGE_BYTES - usedBytes);

    return {
      totalBytes: TOTAL_DEVICE_STORAGE_BYTES,
      usedBytes,
      freeBytes,
      modelsSizeBytes,
      totalFormatted: (TOTAL_DEVICE_STORAGE_BYTES / (1024 * 1024 * 1024)).toFixed(1) + ' GB',
      usedFormatted: (usedBytes / (1024 * 1024 * 1024)).toFixed(1) + ' GB',
      freeFormatted: (freeBytes / (1024 * 1024 * 1024)).toFixed(1) + ' GB',
      modelsFormatted: (modelsSizeBytes / (1024 * 1024 * 1024)).toFixed(2) + ' GB',
    };
  }

  public async startDownload(id: ModelId, forceFailChecksum = false): Promise<void> {
    const model = this.getModel(id);
    if (!model) return;

    // 1. CHECKING_STORAGE
    this.updateModelState(id, { state: 'CHECKING_STORAGE', errorMessage: undefined });
    await new Promise(r => setTimeout(r, 600));

    const stats = this.getStorageStats();
    if (stats.freeBytes < model.sizeBytes) {
      this.updateModelState(id, {
        state: 'ERROR',
        errorMessage: `Insufficient disk storage. Requires ${model.sizeFormatted}, but only ${stats.freeFormatted} available.`,
      });
      return;
    }

    // 2. DOWNLOADING
    let downloaded = model.downloadedBytes || 0;
    const total = model.sizeBytes;
    this.updateModelState(id, {
      state: 'DOWNLOADING',
      downloadSpeed: '24.8 MB/s',
      progress: Math.floor((downloaded / total) * 100),
      downloadedBytes: downloaded,
    });

    const chunkIncrement = Math.max(total / 40, 15 * 1024 * 1024); // ~3-4 seconds total simulation
    
    if (this.downloadIntervals.has(id)) {
      clearInterval(this.downloadIntervals.get(id));
    }

    const interval = setInterval(async () => {
      downloaded += chunkIncrement;
      const currentProgress = Math.min(100, Math.floor((downloaded / total) * 100));

      // Random speed variation for realism
      const currentSpeed = (20 + Math.random() * 12).toFixed(1) + ' MB/s';

      if (downloaded >= total) {
        clearInterval(interval);
        this.downloadIntervals.delete(id);

        // 3. VERIFYING (SHA-256 check)
        this.updateModelState(id, {
          state: 'VERIFYING',
          progress: 100,
          downloadedBytes: total,
          downloadSpeed: undefined,
        });

        await new Promise(r => setTimeout(r, 1200));

        if (forceFailChecksum) {
          this.updateModelState(id, {
            state: 'ERROR',
            errorMessage: 'SHA-256 Checksum validation failed: file corrupted or tampered during stream.',
          });
          return;
        }

        // 4. Model Load Validation
        this.updateModelState(id, { state: 'LOADING' });
        await new Promise(r => setTimeout(r, 800));

        // 5. READY
        this.updateModelState(id, {
          state: 'READY',
          progress: 100,
          downloadedBytes: total,
          isLoaded: true,
          errorMessage: undefined,
        });
      } else {
        this.updateModelState(id, {
          progress: currentProgress,
          downloadedBytes: downloaded,
          downloadSpeed: currentSpeed,
        });
      }
    }, 120);

    this.downloadIntervals.set(id, interval);
  }

  public pauseDownload(id: ModelId) {
    if (this.downloadIntervals.has(id)) {
      clearInterval(this.downloadIntervals.get(id));
      this.downloadIntervals.delete(id);
    }
    const model = this.getModel(id);
    if (model && model.state === 'DOWNLOADING') {
      this.updateModelState(id, { state: 'PAUSED', downloadSpeed: undefined });
    }
  }

  public resumeDownload(id: ModelId) {
    this.startDownload(id);
  }

  public async deleteModel(id: ModelId): Promise<void> {
    if (this.downloadIntervals.has(id)) {
      clearInterval(this.downloadIntervals.get(id));
      this.downloadIntervals.delete(id);
    }

    this.updateModelState(id, {
      state: 'NOT_INSTALLED',
      progress: 0,
      downloadedBytes: 0,
      downloadSpeed: undefined,
      errorMessage: undefined,
      isLoaded: false,
    });
  }

  public async loadModel(id: ModelId): Promise<boolean> {
    const model = this.getModel(id);
    if (!model || model.state !== 'READY') return false;

    // Unload other llama models if memory optimization
    this.models = this.models.map(m => {
      if (m.id === id) {
        return { ...m, isLoaded: true };
      }
      if (m.backend === 'llama.cpp' && m.id !== id) {
        return { ...m, isLoaded: false };
      }
      return m;
    });

    this.saveState();
    return true;
  }

  private updateModelState(id: ModelId, patch: Partial<ModelInfo>) {
    this.models = this.models.map(m => (m.id === id ? { ...m, ...patch } : m));
    this.saveState();
  }

  public resetAllToDefault() {
    this.models = [...INITIAL_MODELS];
    this.saveState();
  }
}

export const modelManager = new ModelManagerService();
