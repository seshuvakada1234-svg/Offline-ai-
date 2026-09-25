import assert from 'node:assert/strict';
import { test } from 'node:test';
import type { Message } from '../types';

Object.defineProperty(globalThis, 'localStorage', {
  configurable: true,
  value: { getItem: () => null, setItem: () => {} },
});
const { AssistantResponsePipeline } = await import('./responsePipeline');
const { ActionHandler } = await import('./actionHandler');
const { Qwen3LocalEngine } = await import('./qwen3Engine');
const messages = (content: string): Message[] => [{ id: 'user', role: 'user', content, timestamp: 1 }];

test('ordinary requests always produce chat text, never actions', async () => {
  const prompts = [
    'Hello', 'Hi', 'What is Android?', 'What is Python?', 'Explain machine learning',
    'How do I create a website?', 'How do I create a wedding invitation website?',
    'How to create a website for wedding invitation website', 'Write HTML for a wedding invitation website',
    'Write Python code', 'Explain this code', 'Give me ideas for my project', 'What is 7+9?',
    'Explain how to open YouTube', 'Do not open Chrome', 'Open a website', 'Run this Python code',
    'Open the door', 'Open Chrome tutorials', 'Open example.com',
    '6+9', 'YouTube', 'Settings', 'Open constructor', 'Open WhatsApp and send a message',
    'How do I open WhatsApp?', 'Write code to open Chrome', 'Open YouTube\nand open Settings',
    'Search YouTube for', `Search YouTube for ${'x'.repeat(301)}`, '{"action":"OPEN_YOUTUBE"}',
  ];
  for (const prompt of prompts) {
    const phases: string[] = [];
    const answer = 'To create a wedding invitation website, you can use HTML, CSS and JavaScript.';
    const pipeline = new AssistantResponsePipeline(async (_, __, onText) => {
      onText(answer);
      return { text: answer };
    }, async () => { throw new Error('Unexpected command execution'); });
    const response = await pipeline.respond(messages(prompt), 'qwen3-1.7b', { onPhase: phase => phases.push(phase) });
    assert.equal(response.text, answer, prompt);
    assert.equal(response.action, undefined);
    assert.deepEqual(phases, ['GENERATING', 'IDLE']);
  }
});

test('model Markdown, code, malformed JSON and valid action examples remain ordinary responses', async () => {
  for (const text of [
    'Opening a website means loading its HTML in a browser.',
    '```html\n<h1>Wedding invitation</h1>\n```',
    '```json\n{"action":\n```',
    '{"title":"Wedding"}',
    '{"action":"UNKNOWN_ACTION"}',
    '{"action":"OPEN_YOUTUBE"}',
    'You can search YouTube for tutorials about website development.',
  ]) {
    const pipeline = new AssistantResponsePipeline(async () => ({ text }), async () => {
      throw new Error('Model output is not execution authority');
    });
    assert.equal((await pipeline.respond(messages('Explain this code'), 'qwen3-1.7b')).text, text);
  }
  assert.equal(ActionHandler.parseActionFromLLM('You can search YouTube for tutorials.').hasAction, false);
});

test('only explicit supported commands execute once and return an outcome', async () => {
  const prompts = new Map([
    ['Open YouTube', 'OPEN_YOUTUBE'], ['Open Chrome', 'OPEN_CHROME'], ['Open Settings', 'OPEN_SETTINGS'],
    ['Open WhatsApp', 'OPEN_APP'], ['Search YouTube for Python tutorials', 'SEARCH_YOUTUBE'],
    ['Open Custom Reader app', 'OPEN_APP'],
  ]);
  for (const [prompt, type] of prompts) {
    let executions = 0;
    const phases: string[] = [];
    const pipeline = new AssistantResponsePipeline(async () => { throw new Error('Unexpected inference'); }, async action => {
      executions++;
      assert.equal(action.type, type);
      return { success: true, message: 'Action completed' };
    });
    const response = await pipeline.respond(messages(prompt), 'qwen3-1.7b', { onPhase: phase => phases.push(phase) });
    assert.equal(executions, 1);
    assert.equal(response.text, 'Action completed');
    assert.deepEqual(phases, ['EXECUTING_ACTION', 'IDLE']);
  }
});

test('malformed or unsupported action structures never parse as executable actions', () => {
  for (const text of [
    '{"action":"OPEN_APP"}', '{"action":"SEARCH_YOUTUBE","query":null}',
    '{"action":"OPEN_APP","appName":123}', '{"action":"MAKE_CALL","phoneNumber":"1234"}',
    '{"action":"OPEN_URL","url":"javascript:void(0)"}', '{"action":"UNKNOWN"}',
    "{action: 'OPEN_YOUTUBE'}", '{"action":"OPEN_YOUTUBE",}',
    '{"action":"OPEN_YOUTUBE"} trailing', '{"action":"OPEN_YOUTUBE"}{"action":"OPEN_SETTINGS"}',
    '{"action":"OPEN_YOUTUBE","action":"OPEN_SETTINGS"}',
    '{"action":"OPEN_YOUTUBE","\\u0061ction":"OPEN_SETTINGS"}',
    '{/* comment */"action":"OPEN_YOUTUBE"}',
    '{"action":"OPEN_APP","appName":"WhatsApp","query":"ignored"}',
    '{"action":"OPEN_SETTINGS","query":"ignored"}',
    '{"action":"SEARCH_YOUTUBE","query":"Python","appName":"Chrome"}',
    '{"action":"SEARCH_YOUTUBE","query":"Python\\nOpen Chrome"}',
    '{"action":"SEARCH_YOUTUBE","query":"invalid\\x20escape"}',
    '[{"action":"OPEN_YOUTUBE"}]',
    'Here is an example:\n```json\n{"action":"OPEN_SETTINGS"}\n```',
    '```python\n{"action":"OPEN_YOUTUBE"}\n```',
    '```json\n{"action":"OPEN_YOUTUBE"}\n```\n```json\n{"action":"OPEN_SETTINGS"}\n```',
  ]) {
    const result = ActionHandler.parseActionFromLLM(text);
    assert.equal(result.hasAction, false, text);
    assert.equal(result.cleanedText, text);
  }
});

test('standalone supported JSON parses with required string parameters and escapes intact', () => {
  for (const type of ['OPEN_YOUTUBE', 'OPEN_CHROME', 'OPEN_SETTINGS']) {
    const result = ActionHandler.parseActionFromLLM(JSON.stringify({ action: type }));
    assert.equal(result.action?.type, type);
    assert.equal(result.cleanedText, '');
  }
  const query = 'Python "dict" {examples} \\ unicode: π';
  const search = ActionHandler.parseActionFromLLM(`\`\`\`json\n${JSON.stringify({ action: 'SEARCH_YOUTUBE', query })}\n\`\`\``);
  assert.equal(search.action?.query, query);
  const app = ActionHandler.parseActionFromLLM('{"action":"OPEN_APP","appName":"WhatsApp"}');
  assert.equal(app.action?.appName, 'WhatsApp');
});

test('validation rejects extraneous parameters and control characters before dispatch', async () => {
  for (const action of [
    { type: 'OPEN_SETTINGS', appName: 'Chrome' },
    { type: 'OPEN_YOUTUBE', url: 'https://example.com' },
    { type: 'OPEN_APP', appName: 'WhatsApp', query: 'ignored' },
    { type: 'SEARCH_YOUTUBE', query: 'Python\u0085tutorials' },
    { type: 'SEARCH_YOUTUBE', query: 'a'.repeat(301) },
  ] as const) {
    const outcome = await ActionHandler.executeAction({ id: 'invalid', requiresConfirmation: false, ...action });
    assert.equal(outcome.success, false);
  }
});

test('browser dispatch exceptions return failed action results instead of success', async () => {
  const savedWindow = Object.getOwnPropertyDescriptor(globalThis, 'window');
  Object.defineProperty(globalThis, 'window', {
    configurable: true, value: { open: () => { throw new Error('Launch failed'); } },
  });
  try {
    for (const prompt of ['Open YouTube', 'Open Chrome', 'Open WhatsApp', 'Search YouTube for Python tutorials']) {
      const action = ActionHandler.detectUserRequest(prompt)!;
      const outcome = await ActionHandler.executeAction(action);
      assert.equal(outcome.success, false, prompt);
      assert.ok(outcome.message.includes('Launch failed'));
    }
  } finally {
    if (savedWindow) Object.defineProperty(globalThis, 'window', savedWindow);
    else Reflect.deleteProperty(globalThis, 'window');
  }
});

test('generation and action failures, timeouts and cancellation always end in IDLE', async () => {
  for (const prompt of ['Explain Python', 'Open YouTube']) {
    for (const mode of ['error', 'timeout', 'cancel']) {
      const phases: string[] = [];
      const work = async () => {
        if (mode === 'error') throw new Error('Backend failed');
        return new Promise<never>(() => {});
      };
      const pipeline = new AssistantResponsePipeline(async (_, __, onText) => {
        onText('Partial answer');
        return work();
      }, work, 10, 10);
      const pending = pipeline.respond(messages(prompt), 'qwen3-1.7b', { onPhase: phase => phases.push(phase) });
      if (mode === 'cancel') pipeline.stop();
      const response = await pending;
      assert.equal(phases.at(-1), 'IDLE');
      assert.ok(response.text.length > 0);
      if (prompt === 'Explain Python') assert.ok(response.text.startsWith('Partial answer'));
      if (mode === 'timeout') assert.ok(response.text.includes('timed out'));
      if (mode === 'cancel') assert.equal(response.cancelled, true);
    }
  }
});

test('unsuccessful actions and empty model output terminate and allow another conversation', async () => {
  const phases: string[] = [];
  const pipeline = new AssistantResponsePipeline(async () => ({ text: '' }), async () => ({ success: false, message: 'App not installed.' }));
  const failed = await pipeline.respond(messages('Open WhatsApp'), 'qwen3-1.7b', { onPhase: phase => phases.push(phase) });
  assert.equal(failed.text, 'App not installed.');
  assert.equal(failed.action?.executed, false);
  assert.equal(phases.at(-1), 'IDLE');
  const next = await pipeline.respond(messages('Hi'), 'qwen3-1.7b', { onPhase: phase => phases.push(phase) });
  assert.ok(next.text.includes('empty response'));
  assert.equal(next.action, undefined);
  assert.equal(phases.at(-1), 'IDLE');
});

test('stopping before dispatch prevents the executor from being called', async () => {
  let executions = 0;
  const pipeline = new AssistantResponsePipeline(async () => ({ text: 'answer' }), async () => {
    executions++;
    return { success: true, message: 'Opened' };
  });
  const response = await pipeline.respond(messages('Open YouTube'), 'qwen3-1.7b', {
    onPhase: phase => { if (phase === 'EXECUTING_ACTION') pipeline.stop(); },
  });
  assert.equal(executions, 0);
  assert.equal(response.cancelled, true);
});

test('cancelled or completed requests cannot emit late text or reset a newer request', async () => {
  const emit: Array<(text: string) => void> = [];
  const complete: Array<(response: { text: string }) => void> = [];
  const phases: string[] = [];
  const streamed: string[] = [];
  const pipeline = new AssistantResponsePipeline(async (_, __, onText) => {
    emit.push(onText);
    return new Promise(resolve => complete.push(resolve));
  });
  const callbacks = { onPhase: (phase: string) => phases.push(phase), onText: (text: string) => streamed.push(text) };
  const first = pipeline.respond(messages('Explain Python'), 'qwen3-1.7b', callbacks);
  emit[0]('Partial answer');
  const second = pipeline.respond(messages('Explain Android'), 'qwen3-1.7b', callbacks);
  assert.equal((await first).cancelled, true);
  assert.equal(phases.at(-1), 'GENERATING');
  emit[0]('Late old answer');
  complete[0]({ text: 'Late old answer' });
  emit[1]('Android answer');
  complete[1]({ text: 'Android answer' });
  assert.equal((await second).text, 'Android answer');
  emit[1]('Late completed answer');
  assert.deepEqual(streamed, ['Partial answer', 'Android answer']);
  assert.deepEqual(phases, ['GENERATING', 'GENERATING', 'IDLE']);
});

test('late text after timeout is ignored and callback errors preserve the partial response', async () => {
  let emit!: (text: string) => void;
  const streamed: string[] = [];
  const pipeline = new AssistantResponsePipeline(async (_, __, onText) => {
    emit = onText;
    onText('Partial answer');
    return new Promise(() => {});
  }, undefined, 10);
  const response = await pipeline.respond(messages('Explain Python'), 'qwen3-1.7b', { onText: text => streamed.push(text) });
  emit('Too late');
  assert.ok(response.text.includes('timed out'));
  assert.deepEqual(streamed, ['Partial answer']);

  const phases: string[] = [];
  const failingCallback = new AssistantResponsePipeline(async (_, __, onText) => {
    onText('Keep this answer');
    return { text: 'Keep this answer' };
  });
  const failed = await failingCallback.respond(messages('Hi'), 'qwen3-1.7b', {
    onPhase: phase => phases.push(phase), onText: () => { throw new Error('Rendering callback failed'); },
  });
  assert.ok(failed.text.startsWith('Keep this answer'));
  assert.equal(phases.at(-1), 'IDLE');
});

test('web engine always releases isGenerating after token callback failure', async () => {
  const { modelManager } = await import('./modelManager');
  const model = modelManager.getModel('qwen3-1.7b')!;
  const originalState = model.state;
  model.state = 'READY';
  const engine = new Qwen3LocalEngine();
  let error: Error | undefined;
  try {
    await engine.generateResponse(messages('Hi'), model.id, {
      onToken: () => { throw new Error('Token callback failed'); },
      onComplete: () => assert.fail('Generation should have failed'),
      onError: failure => { error = failure; },
    });
    assert.equal(error?.message, 'Token callback failed');
    assert.equal(engine.getIsGenerating(), false);
  } finally {
    engine.stop();
    model.state = originalState;
  }
});

test('web token streaming preserves underscores in code and action JSON', () => {
  const engine = new Qwen3LocalEngine();
  const text = '{"action":"SEARCH_YOUTUBE","query":"Python tutorials"}\nconst wedding_date = "2026";';
  assert.equal(engine['splitIntoTokens'](text).join(''), text);
  for (const prompt of ['Explain how to open Chrome', 'What are device settings?', 'Explain YouTube search']) {
    assert.equal(ActionHandler.parseActionFromLLM(engine['synthesizeOfflineResponse'](prompt, [])).hasAction, false);
  }
});

test('late completion from a cancelled request cannot clear or update its replacement', async () => {
  let calls = 0;
  let lateText!: (text: string) => void;
  let finishOld!: (response: { text: string }) => void;
  const oldUpdates: string[] = [];
  const newPhases: string[] = [];
  const pipeline = new AssistantResponsePipeline(async (_, __, onText) => {
    if (++calls === 1) {
      lateText = onText;
      return new Promise(resolve => { finishOld = resolve; });
    }
    return new Promise(() => {});
  });
  const oldRequest = pipeline.respond(messages('Explain Python'), 'qwen3-1.7b', { onText: text => oldUpdates.push(text) });
  const replacement = pipeline.respond(messages('Explain Android'), 'qwen3-1.7b', { onPhase: phase => newPhases.push(phase) });
  assert.equal((await oldRequest).cancelled, true);
  lateText('Late response from the old request');
  finishOld({ text: 'Late completion' });
  assert.deepEqual(oldUpdates, []);
  assert.deepEqual(newPhases, ['GENERATING']);
  pipeline.stop();
  assert.equal((await replacement).cancelled, true);
  assert.deepEqual(newPhases, ['GENERATING', 'IDLE']);
});

test('voice submits once, starts its visualizer and ignores a late microphone grant after cancel', async () => {
  const { modelManager } = await import('./modelManager');
  const { WhisperSTTService } = await import('./whisperSTT');
  const model = modelManager.getModel('whisper-base')!;
  const originalState = model.state;
  model.state = 'READY';
  const saved = new Map(['navigator', 'window', 'requestAnimationFrame', 'cancelAnimationFrame']
    .map(key => [key, Object.getOwnPropertyDescriptor(globalThis, key)]));
  let recognition: any;
  let frames = 0;
  let levels = 0;
  let tracksStopped = 0;
  const stream = { getTracks: () => [{ stop: () => { tracksStopped++; } }] };
  let getPermission = () => Promise.resolve(stream);
  class Recognition {
    constructor() { recognition = this; }
    start() {}
    stop() {}
    abort() {}
  }
  class AudioContext {
    state = 'running';
    createMediaStreamSource() { return { connect() {} }; }
    createAnalyser() { return { frequencyBinCount: 8, getByteFrequencyData(data: Uint8Array) { data.fill(64); } }; }
    close() { this.state = 'closed'; return Promise.resolve(); }
  }
  Object.defineProperty(globalThis, 'navigator', { configurable: true, value: { mediaDevices: { getUserMedia: () => getPermission() } } });
  Object.defineProperty(globalThis, 'window', { configurable: true, value: { SpeechRecognition: Recognition, AudioContext } });
  Object.defineProperty(globalThis, 'requestAnimationFrame', { configurable: true, value: () => { frames++; return 1; } });
  Object.defineProperty(globalThis, 'cancelAnimationFrame', { configurable: true, value: () => {} });
  const service = new WhisperSTTService();
  try {
    const finalTranscripts: string[] = [];
    await service.startListening({
      onAudioLevel: () => levels++,
      onTranscript: (text, final) => { if (final) finalTranscripts.push(text); },
    });
    const result = Object.assign([{ transcript: 'Open YouTube' }], { isFinal: true });
    recognition.onresult({ resultIndex: 0, results: [result] });
    recognition.onend();
    recognition.onend();
    assert.deepEqual(finalTranscripts, ['Open YouTube']);
    assert.ok(frames > 0 && levels > 0);

    let allow!: (value: typeof stream) => void;
    getPermission = () => new Promise(resolve => { allow = resolve; });
    const pending = service.startListening({});
    service.cancel();
    const before = tracksStopped;
    allow(stream);
    await pending;
    assert.equal(service.getIsListening(), false);
    assert.equal(tracksStopped, before + 1);
  } finally {
    service.cancel();
    model.state = originalState;
    for (const [key, descriptor] of saved) {
      if (descriptor) Object.defineProperty(globalThis, key, descriptor);
      else Reflect.deleteProperty(globalThis, key);
    }
  }
});
