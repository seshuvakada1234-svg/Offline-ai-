import type { AssistantAction, InferenceMetrics, Message, ModelId } from '../types';
import { ActionHandler } from './actionHandler';
import { qwen3Engine } from './qwen3Engine';

export type ResponsePhase = 'IDLE' | 'GENERATING' | 'EXECUTING_ACTION';
export interface AssistantResponse {
  text: string;
  action?: AssistantAction;
  metrics?: InferenceMetrics;
  cancelled?: boolean;
}
type Generate = (messages: Message[], model: ModelId, onText: (text: string) => void, signal: AbortSignal) => Promise<AssistantResponse>;
type Execute = (action: AssistantAction, signal: AbortSignal) => Promise<{ success: boolean; message: string }>;

const generate: Generate = (messages, model, onText, signal) => new Promise((resolve, reject) => {
  qwen3Engine.generateResponse(messages, model, {
    onToken: (_, fullText) => onText(fullText),
    onComplete: (text, metrics) => resolve({ text, metrics }),
    onError: reject,
  }, signal).catch(reject);
});

export class AssistantResponsePipeline {
  private active: AbortController | null = null;

  constructor(
    private generateText: Generate = generate,
    private execute: Execute = (action, signal) => ActionHandler.executeAction(action, signal),
    private generationTimeoutMs = 120_000,
    private actionTimeoutMs = 8_000,
  ) {}

  stop() {
    this.active?.abort(new DOMException('Response stopped.', 'AbortError'));
  }

  async respond(messages: Message[], model: ModelId, callbacks: {
    onText?: (text: string) => void;
    onPhase?: (phase: ResponsePhase) => void;
  } = {}): Promise<AssistantResponse> {
    this.stop();
    const controller = new AbortController();
    this.active = controller;
    let partial = '';
    let timer: ReturnType<typeof setTimeout> | undefined;
    try {
      const lastMessage = messages.at(-1);
      const userText = lastMessage?.role === 'user' ? lastMessage.content : '';
      const detected = ActionHandler.detectUserRequest(userText);
      const action = detected && !ActionHandler.validateAction(detected) ? detected : undefined;
      callbacks.onPhase?.(action ? 'EXECUTING_ACTION' : 'GENERATING');
      timer = setTimeout(() => controller.abort(new DOMException('The request timed out. Please try again.', 'TimeoutError')),
        action ? this.actionTimeoutMs : this.generationTimeoutMs);

      if (action) {
        const outcome = await this.abortable(() => this.execute(action, controller.signal), controller.signal);
        return { text: outcome.message, action: { ...action, executed: outcome.success, resultMessage: outcome.message } };
      }

      // Normal model output remains chat text, including Markdown, code and malformed JSON.
      const response = await this.abortable(() => this.generateText(messages, model, text => {
        if (this.active === controller && !controller.signal.aborted) {
          partial = text;
          callbacks.onText?.(text);
        }
      }, controller.signal), controller.signal);
      return { text: response.text.trim() || 'The model returned an empty response. Please try again.', metrics: response.metrics };
    } catch (error) {
      const reason = controller.signal.aborted ? controller.signal.reason : error;
      const message = reason instanceof Error ? reason.message : 'Unable to complete the request.';
      return {
        text: [partial.trim(), message].filter(Boolean).join('\n\n'),
        cancelled: controller.signal.aborted && reason?.name === 'AbortError',
      };
    } finally {
      clearTimeout(timer);
      if (this.active === controller) {
        this.active = null;
        callbacks.onPhase?.('IDLE');
      }
    }
  }

  private abortable<T>(start: () => Promise<T>, signal: AbortSignal): Promise<T> {
    signal.throwIfAborted();
    return new Promise((resolve, reject) => {
      const abort = () => { cleanup(); reject(signal.reason); };
      const cleanup = () => signal.removeEventListener('abort', abort);
      signal.addEventListener('abort', abort, { once: true });
      try {
        start().then(value => { cleanup(); resolve(value); }, error => { cleanup(); reject(error); });
      } catch (error) {
        cleanup();
        reject(error);
      }
    });
  }
}

export const assistantPipeline = new AssistantResponsePipeline();
