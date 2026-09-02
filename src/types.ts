export type ModelId =
  | 'qwen3-1.7b'
  | 'qwen3-4b'
  | 'phi4-mini'
  | 'gemma3-4b'
  | 'gemma3-270m'
  | 'whisper-base';

export type ModelState =
  | 'NOT_INSTALLED'
  | 'CHECKING_STORAGE'
  | 'DOWNLOADING'
  | 'PAUSED'
  | 'VERIFYING'
  | 'READY'
  | 'LOADING'
  | 'ERROR';

export interface ModelInfo {
  id: ModelId;
  name: string;
  tag: string;
  description: string;
  sizeFormatted: string;
  sizeBytes: number;
  isDefault?: boolean;
  sha256Expected: string;
  filename: string;
  sourceUrl: string;
  contextSize: number;
  quant: string;
  backend: 'llama.cpp' | 'whisper.cpp';
  architecture: string;
  downloadSpeed?: string;
  progress?: number;
  downloadedBytes?: number;
  state: ModelState;
  errorMessage?: string;
  isLoaded?: boolean;
}

export type AssistantActionType =
  | 'OPEN_APP'
  | 'OPEN_URL'
  | 'SEARCH_YOUTUBE'
  | 'OPEN_SETTINGS'
  | 'MAKE_CALL'
  | 'SEND_SMS';

export interface AssistantAction {
  id: string;
  type: AssistantActionType;
  appName?: string;
  url?: string;
  query?: string;
  phoneNumber?: string;
  messageText?: string;
  requiresConfirmation: boolean;
  confirmed?: boolean;
  executed?: boolean;
  resultMessage?: string;
  intentAction?: string;
  intentDataUri?: string;
}

export interface InferenceMetrics {
  modelLoadTimeMs: number;
  timeToFirstTokenMs: number;
  tokensPerSec: number;
  totalTokens: number;
  totalGenTimeMs: number;
  timestamp: number;
}

export type EngineLogTag =
  | 'MODEL_LOAD_START'
  | 'MODEL_LOAD_END'
  | 'PROMPT_START'
  | 'PROMPT_END'
  | 'INFERENCE_START'
  | 'FIRST_TOKEN'
  | 'INFERENCE_END'
  | 'INFERENCE_HALT'
  | 'ACTION_PARSED'
  | 'ACTION_EXECUTED'
  | 'VOICE_START'
  | 'VOICE_TRANSCRIPT'
  | 'TTS_START'
  | 'TTS_END';

export interface EngineLogEntry {
  id: string;
  tag: EngineLogTag;
  timestamp: number;
  timeFormatted: string;
  detail: string;
  meta?: Record<string, any>;
}

export interface Message {
  id: string;
  role: 'user' | 'assistant' | 'system';
  content: string;
  timestamp: number;
  action?: AssistantAction;
  metrics?: InferenceMetrics;
  isStreaming?: boolean;
  isVoiceInput?: boolean;
}

export type VoiceState =
  | 'IDLE'
  | 'LISTENING'
  | 'TRANSCRIBING'
  | 'THINKING'
  | 'STREAMING'
  | 'SPEAKING'
  | 'ACTION_EXECUTING'
  | 'ERROR';

export interface AppSettings {
  language: 'en-US' | 'te-IN' | 'auto';
  speechRate: number;
  speechPitch: number;
  autoSpeakResponse: boolean;
  inferenceThreads: number;
  showPerformanceStats: boolean;
}
