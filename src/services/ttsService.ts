import { logger } from './loggerService';

class TTSService {
  private synth: SpeechSynthesis | null = null;
  private currentUtterance: SpeechSynthesisUtterance | null = null;
  private isSpeaking = false;
  private listeners: Set<(speaking: boolean) => void> = new Set();
  private availableVoices: SpeechSynthesisVoice[] = [];

  constructor() {
    if (typeof window !== 'undefined' && 'speechSynthesis' in window) {
      this.synth = window.speechSynthesis;
      this.loadVoices();
      if (this.synth.onvoiceschanged !== undefined) {
        this.synth.onvoiceschanged = () => this.loadVoices();
      }
    }
  }

  private loadVoices() {
    if (!this.synth) return;
    this.availableVoices = this.synth.getVoices();
  }

  public getVoices(): SpeechSynthesisVoice[] {
    if (!this.availableVoices.length && this.synth) {
      this.availableVoices = this.synth.getVoices();
    }
    return this.availableVoices;
  }

  public subscribe(listener: (speaking: boolean) => void): () => void {
    this.listeners.add(listener);
    listener(this.isSpeaking);
    return () => {
      this.listeners.delete(listener);
    };
  }

  private setSpeaking(val: boolean) {
    this.isSpeaking = val;
    this.listeners.forEach(l => l(val));
  }

  public speak(
    text: string,
    options?: {
      lang?: string;
      rate?: number;
      pitch?: number;
      onStart?: () => void;
      onEnd?: () => void;
      onError?: (err: string) => void;
    }
  ): boolean {
    if (!this.synth) {
      options?.onError?.('Speech synthesis is not supported on this browser/platform.');
      return false;
    }

    // Stop any ongoing speech
    this.stop();

    // Clean text from markdown codeblocks or JSON tags
    const cleanText = text
      .replace(/```(?:json)?[\s\S]*?```/g, '')
      .replace(/[\*\_#`]/g, '')
      .trim();

    if (!cleanText) return false;

    const utterance = new SpeechSynthesisUtterance(cleanText);
    const targetLang = options?.lang || 'en-US';
    utterance.lang = targetLang;
    utterance.rate = options?.rate ?? 1.0;
    utterance.pitch = options?.pitch ?? 1.0;

    // Pick best matching voice
    const voices = this.getVoices();
    let voice = voices.find(v => v.lang === targetLang || v.lang.startsWith(targetLang.split('-')[0]));

    // Check Telugu voice availability
    if (targetLang.startsWith('te')) {
      const teluguVoice = voices.find(v => v.lang.toLowerCase().includes('te') || v.name.toLowerCase().includes('telugu'));
      if (teluguVoice) {
        voice = teluguVoice;
      } else {
        logger.log('TTS_START', 'Telugu voice not installed on device engine; falling back to default synthesized voice without crashing');
      }
    }

    if (voice) {
      utterance.voice = voice;
    }

    utterance.onstart = () => {
      this.setSpeaking(true);
      logger.log('TTS_START', `Speaking: "${cleanText.substring(0, 50)}${cleanText.length > 50 ? '...' : ''}" (${utterance.lang})`);
      options?.onStart?.();
    };

    utterance.onend = () => {
      this.setSpeaking(false);
      this.currentUtterance = null;
      logger.log('TTS_END', 'Speech synthesis complete.');
      options?.onEnd?.();
    };

    utterance.onerror = (e) => {
      this.setSpeaking(false);
      this.currentUtterance = null;
      logger.log('TTS_END', `TTS Error: ${e.error}`);
      options?.onError?.(e.error);
    };

    this.currentUtterance = utterance;
    this.synth.speak(utterance);
    return true;
  }

  public stop() {
    if (this.synth) {
      this.synth.cancel();
    }
    this.setSpeaking(false);
    this.currentUtterance = null;
  }

  public getIsSpeaking(): boolean {
    return this.isSpeaking;
  }
}

export const tts = new TTSService();
