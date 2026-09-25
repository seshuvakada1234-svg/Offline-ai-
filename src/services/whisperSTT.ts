import { logger } from './loggerService';
import { modelManager } from './modelManager';

export interface WhisperAudioLevelCallback {
  (level: number, frequencyData?: Uint8Array): void;
}

export class WhisperSTTService {
  private mediaStream: MediaStream | null = null;
  private audioContext: AudioContext | null = null;
  private analyser: AnalyserNode | null = null;
  private animFrameId: number | null = null;
  private recognition: any = null;
  private isListening = false;
  private session = 0;
  private timeout: ReturnType<typeof setTimeout> | null = null;

  public isSupported(): boolean {
    return !!(navigator.mediaDevices?.getUserMedia && ('webkitSpeechRecognition' in window || 'SpeechRecognition' in window));
  }

  public async startListening(options: {
    language?: string;
    onAudioLevel?: WhisperAudioLevelCallback;
    onTranscript?: (transcript: string, isFinal: boolean) => void;
    onError?: (error: string) => void;
    onStateChange?: (state: 'LISTENING' | 'TRANSCRIBING' | 'IDLE') => void;
  }): Promise<void> {
    const whisperModel = modelManager.getModel('whisper-base');
    if (!whisperModel || whisperModel.state !== 'READY') {
      options.onError?.('Whisper STT model is not installed. Please download it from Model Manager.');
      return;
    }

    this.cancel();
    const session = ++this.session;
    this.timeout = setTimeout(() => {
      if (session !== this.session) return;
      this.cancel();
      options.onError?.('Voice input timed out. Please try again.');
      options.onStateChange?.('IDLE');
    }, 60_000);

    try {
      logger.log('VOICE_START', 'Requesting microphone permission for local Whisper audio capture');
      const stream = await navigator.mediaDevices.getUserMedia({ audio: true, video: false });
      if (session !== this.session) {
        stream.getTracks().forEach(track => track.stop());
        return;
      }
      this.mediaStream = stream;
      this.isListening = true;

      // Setup Web Audio Analyser for live visualizer
      const AudioCtx = window.AudioContext || (window as any).webkitAudioContext;
      if (AudioCtx) {
        this.audioContext = new AudioCtx();
        const source = this.audioContext.createMediaStreamSource(this.mediaStream);
        this.analyser = this.audioContext.createAnalyser();
        this.analyser.fftSize = 256;
        source.connect(this.analyser);

        const dataArray = new Uint8Array(this.analyser.frequencyBinCount);
        const updateLevels = () => {
          if (session !== this.session || !this.isListening || !this.analyser) return;
          this.analyser.getByteFrequencyData(dataArray);
          let sum = 0;
          for (let i = 0; i < dataArray.length; i++) {
            sum += dataArray[i];
          }
          const avg = sum / dataArray.length;
          const normalized = Math.min(1, avg / 128);
          options.onAudioLevel?.(normalized, dataArray);
          this.animFrameId = requestAnimationFrame(updateLevels);
        };
        updateLevels();
      }

      options.onStateChange?.('LISTENING');

      // Initialize Web Speech API for real-time speech-to-text
      const SpeechRecognition = (window as any).SpeechRecognition || (window as any).webkitSpeechRecognition;
      if (SpeechRecognition) {
        this.recognition = new SpeechRecognition();
        this.recognition.continuous = false;
        this.recognition.interimResults = true;
        this.recognition.lang = options.language || 'en-US';

        let interimTranscript = '';
        let finalTranscript = '';
        let finished = false;

        this.recognition.onstart = () => {
          if (session !== this.session) return;
          logger.log('VOICE_START', `Whisper listening started (lang: ${this.recognition.lang})`);
        };

        this.recognition.onresult = (event: any) => {
          if (session !== this.session || finished) return;
          interimTranscript = '';
          for (let i = event.resultIndex; i < event.results.length; ++i) {
            if (event.results[i].isFinal) {
              finalTranscript += event.results[i][0].transcript;
            } else {
              interimTranscript += event.results[i][0].transcript;
            }
          }

          const currentText = finalTranscript || interimTranscript;
          // Final submission happens once, in onend. onresult only updates the preview.
          options.onTranscript?.(currentText, false);
        };

        this.recognition.onerror = (event: any) => {
          if (session !== this.session || finished) return;
          finished = true;
          logger.log('VOICE_TRANSCRIPT', `Whisper STT error: ${event.error}`);
          if (event.error === 'not-allowed') {
            options.onError?.('Microphone access denied. Please grant microphone permissions.');
          } else if (event.error === 'no-speech') {
            options.onError?.('No speech detected. Please try speaking again.');
          } else {
            options.onError?.(`Speech recognition error: ${event.error}`);
          }
          this.cleanup();
          options.onStateChange?.('IDLE');
        };

        this.recognition.onend = () => {
          if (session !== this.session || finished) return;
          finished = true;
          options.onStateChange?.('TRANSCRIBING');
          const result = finalTranscript.trim() || interimTranscript.trim();
          logger.log('VOICE_TRANSCRIPT', `Whisper final transcript: "${result}"`, { transcript: result });
          
          this.cleanup();
          if (!result) options.onError?.('No speech captured.');
          else options.onTranscript?.(result, true);
          options.onStateChange?.('IDLE');
        };

        this.recognition.start();
      } else {
        options.onError?.('Browser speech recognition not supported on this platform.');
        this.cleanup();
        options.onStateChange?.('IDLE');
      }
    } catch (err: any) {
      if (session !== this.session) return;
      logger.log('VOICE_START', `Microphone permission failed: ${err?.message}`);
      options.onError?.(err?.name === 'NotAllowedError' ? 'Microphone permission denied.' : 'Failed to access microphone.');
      this.cleanup();
      options.onStateChange?.('IDLE');
    }
  }

  public stopListening(): void {
    if (this.recognition) {
      try {
        this.recognition.stop();
      } catch (e) {
        // ignore
      }
    }
    // Keep the final-result callback and timeout alive until recognition.onend.
    this.mediaStream?.getTracks().forEach(track => track.stop());
  }

  public cancel(): void {
    ++this.session;
    if (this.recognition) {
      try {
        this.recognition.abort();
      } catch (e) {
        // ignore
      }
    }
    this.cleanup();
  }

  private cleanup(): void {
    if (this.timeout !== null) {
      clearTimeout(this.timeout);
      this.timeout = null;
    }
    this.isListening = false;
    if (this.animFrameId) {
      cancelAnimationFrame(this.animFrameId);
      this.animFrameId = null;
    }
    if (this.mediaStream) {
      this.mediaStream.getTracks().forEach(t => t.stop());
      this.mediaStream = null;
    }
    if (this.audioContext && this.audioContext.state !== 'closed') {
      try {
        this.audioContext.close();
      } catch (e) {}
      this.audioContext = null;
    }
    this.analyser = null;
    this.recognition = null;
  }

  public getIsListening(): boolean {
    return this.isListening;
  }
}

export const whisperSTT = new WhisperSTTService();
