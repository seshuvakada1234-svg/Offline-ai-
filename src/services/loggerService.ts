import { EngineLogEntry, EngineLogTag } from '../types';

class LoggerService {
  private logs: EngineLogEntry[] = [];
  private listeners: Set<(logs: EngineLogEntry[]) => void> = new Set();

  public log(tag: EngineLogTag, detail: string, meta?: Record<string, any>) {
    const entry: EngineLogEntry = {
      id: Math.random().toString(36).substring(2, 9),
      tag,
      timestamp: Date.now(),
      timeFormatted: new Date().toLocaleTimeString() + '.' + String(Date.now() % 1000).padStart(3, '0'),
      detail,
      meta,
    };

    this.logs.unshift(entry);
    if (this.logs.length > 300) {
      this.logs.pop();
    }

    console.log(`[MyAI Logcat][${tag}] ${detail}`, meta || '');
    this.notify();
  }

  public getLogs(): EngineLogEntry[] {
    return [...this.logs];
  }

  public subscribe(listener: (logs: EngineLogEntry[]) => void): () => void {
    this.listeners.add(listener);
    listener([...this.logs]);
    return () => {
      this.listeners.delete(listener);
    };
  }

  public clear() {
    this.logs = [];
    this.notify();
  }

  private notify() {
    const copy = [...this.logs];
    this.listeners.forEach(l => l(copy));
  }
}

export const logger = new LoggerService();
