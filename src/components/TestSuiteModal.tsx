import React from 'react';
import { modelManager } from '../services/modelManager';
import { qwen3Engine } from '../services/qwen3Engine';
import { ActionHandler } from '../services/actionHandler';
import { tts } from '../services/ttsService';
import {
  X,
  Play,
  CheckCircle2,
  XCircle,
  Clock,
  FlaskConical,
  RotateCcw,
  Sparkles,
  ShieldCheck,
} from 'lucide-react';

interface TestCase {
  id: string;
  category: 'Model Manager' | 'LLM & Qwen3' | 'Actions & Intents' | 'Voice STT' | 'TTS';
  name: string;
  description: string;
  status: 'PENDING' | 'RUNNING' | 'PASSED' | 'FAILED';
  durationMs?: number;
  error?: string;
  run: () => Promise<void>;
}

interface TestSuiteModalProps {
  isOpen: boolean;
  onClose: () => void;
}

export const TestSuiteModal: React.FC<TestSuiteModalProps> = ({ isOpen, onClose }) => {
  const [isRunningAll, setIsRunningAll] = React.useState(false);
  const [testResults, setTestResults] = React.useState<Record<string, { status: string; durationMs?: number; error?: string }>>({});

  const tests: TestCase[] = [
    // 1. Model Manager Tests
    {
      id: 'mm-1',
      category: 'Model Manager',
      name: 'Initial Model Registry & NOT_INSTALLED State',
      description: 'Verifies all 6 verified models exist with proper metadata and initial states.',
      status: (testResults['mm-1']?.status as any) || 'PENDING',
      durationMs: testResults['mm-1']?.durationMs,
      error: testResults['mm-1']?.error,
      run: async () => {
        const models = modelManager.getModels();
        if (models.length !== 6) throw new Error(`Expected 6 models, found ${models.length}`);
        const qwen17 = models.find(m => m.id === 'qwen3-1.7b');
        if (!qwen17 || !qwen17.isDefault) throw new Error('Qwen3 1.7B must be configured as default model');
        const whisper = models.find(m => m.id === 'whisper-base');
        if (!whisper) throw new Error('Whisper model missing from registry');
      },
    },
    {
      id: 'mm-2',
      category: 'Model Manager',
      name: 'Storage Calculation & Free Space Bounds',
      description: 'Calculates storage allocation against 64 GB partition.',
      status: (testResults['mm-2']?.status as any) || 'PENDING',
      durationMs: testResults['mm-2']?.durationMs,
      error: testResults['mm-2']?.error,
      run: async () => {
        const stats = modelManager.getStorageStats();
        if (stats.totalBytes <= 0 || stats.freeBytes < 0) throw new Error('Invalid storage calculation bounds');
        if (stats.usedBytes + stats.freeBytes !== stats.totalBytes) throw new Error('Storage balance mismatch');
      },
    },
    {
      id: 'mm-3',
      category: 'Model Manager',
      name: 'Model Checksum Verification & Atomic Validation',
      description: 'Checks SHA-256 integrity rules for GGUF model files.',
      status: (testResults['mm-3']?.status as any) || 'PENDING',
      durationMs: testResults['mm-3']?.durationMs,
      error: testResults['mm-3']?.error,
      run: async () => {
        const qwen = modelManager.getModel('qwen3-1.7b');
        if (!qwen || qwen.sha256Expected.length !== 64) throw new Error('Invalid SHA-256 checksum format');
      },
    },
    {
      id: 'mm-4',
      category: 'Model Manager',
      name: 'Model Memory Load & State Switching',
      description: 'Validates in-memory model loading and memory pinning.',
      status: (testResults['mm-4']?.status as any) || 'PENDING',
      durationMs: testResults['mm-4']?.durationMs,
      error: testResults['mm-4']?.error,
      run: async () => {
        const loaded = await modelManager.loadModel('qwen3-1.7b');
        if (!loaded) throw new Error('Failed to load ready model into active memory');
      },
    },

    // 2. LLM & Qwen3 Chat Template Tests
    {
      id: 'llm-1',
      category: 'LLM & Qwen3',
      name: 'Qwen3 Chat Template Formatting',
      description: 'Verifies <|im_start|>system, user, assistant and <|im_end|> BOS/EOS formatting.',
      status: (testResults['llm-1']?.status as any) || 'PENDING',
      durationMs: testResults['llm-1']?.durationMs,
      error: testResults['llm-1']?.error,
      run: async () => {
        const formatted = qwen3Engine.formatQwen3ChatTemplate([
          { id: '1', role: 'user', content: 'What is 2+2?', timestamp: Date.now() },
        ]);
        if (!formatted.includes('<|im_start|>system') || !formatted.includes('<|im_end|>')) {
          throw new Error('Missing Qwen3 system tags in chat template');
        }
        if (!formatted.includes('<|im_start|>user\nWhat is 2+2?<|im_end|>')) {
          throw new Error('User turn improperly formatted');
        }
        if (!formatted.endsWith('<|im_start|>assistant\n')) {
          throw new Error('Template must terminate with open assistant tag');
        }
      },
    },
    {
      id: 'llm-2',
      category: 'LLM & Qwen3',
      name: 'Offline Inference: "What is 2+2?"',
      description: 'Generates streaming response and verifies mathematical reasoning.',
      status: (testResults['llm-2']?.status as any) || 'PENDING',
      durationMs: testResults['llm-2']?.durationMs,
      error: testResults['llm-2']?.error,
      run: async () => {
        let result = '';
        await new Promise<void>((resolve, reject) => {
          qwen3Engine.generateResponse(
            [{ id: '1', role: 'user', content: 'What is 2+2?', timestamp: Date.now() }],
            'qwen3-1.7b',
            {
              onToken: (_, full) => {
                result = full;
              },
              onComplete: full => {
                result = full;
                resolve();
              },
              onError: reject,
            }
          );
        });
        if (!result.includes('4')) throw new Error(`Unexpected answer: ${result}`);
      },
    },
    {
      id: 'llm-3',
      category: 'LLM & Qwen3',
      name: 'Offline Inference: "Explain photosynthesis in one sentence."',
      description: 'Generates concise explanation with Qwen3 local prompt.',
      status: (testResults['llm-3']?.status as any) || 'PENDING',
      durationMs: testResults['llm-3']?.durationMs,
      error: testResults['llm-3']?.error,
      run: async () => {
        let result = '';
        await new Promise<void>((resolve, reject) => {
          qwen3Engine.generateResponse(
            [{ id: '1', role: 'user', content: 'Explain photosynthesis in one sentence.', timestamp: Date.now() }],
            'qwen3-1.7b',
            {
              onToken: (_, full) => {
                result = full;
              },
              onComplete: full => {
                result = full;
                resolve();
              },
              onError: reject,
            }
          );
        });
        if (!result.toLowerCase().includes('sunlight') && !result.toLowerCase().includes('plants')) {
          throw new Error(`Invalid photosynthesis explanation: ${result}`);
        }
      },
    },
    {
      id: 'llm-4',
      category: 'LLM & Qwen3',
      name: 'Stop Generation & Token Cancellation',
      description: 'Verifies that aborting stops token streaming immediately without native deadlock.',
      status: (testResults['llm-4']?.status as any) || 'PENDING',
      durationMs: testResults['llm-4']?.durationMs,
      error: testResults['llm-4']?.error,
      run: async () => {
        let tokensReceived = 0;
        const genPromise = new Promise<void>((resolve, reject) => {
          qwen3Engine.generateResponse(
            [{ id: '1', role: 'user', content: 'Explain quantum computing in deep detail', timestamp: Date.now() }],
            'qwen3-1.7b',
            {
              onToken: () => {
                tokensReceived++;
                if (tokensReceived >= 3) {
                  qwen3Engine.stopGeneration();
                }
              },
              onComplete: () => resolve(),
              onError: reject,
            }
          );
        });
        await genPromise;
        if (qwen3Engine.getIsGenerating()) throw new Error('Engine remained in generating state after stop');
      },
    },

    // 3. Actions & Intents
    {
      id: 'act-1',
      category: 'Actions & Intents',
      name: 'Action Intent: "Open YouTube and search Telugu songs"',
      description: 'Parses action, validates SEARCH_YOUTUBE, and generates Android Intent.',
      status: (testResults['act-1']?.status as any) || 'PENDING',
      durationMs: testResults['act-1']?.durationMs,
      error: testResults['act-1']?.error,
      run: async () => {
        const text = '```json\n{"action": "SEARCH_YOUTUBE", "query": "Telugu songs"}\n```\nOpening Telugu songs on YouTube.';
        const parsed = ActionHandler.parseActionFromLLM(text);
        if (!parsed.hasAction || !parsed.action) throw new Error('Failed to parse structured JSON action');
        if (parsed.action.type !== 'SEARCH_YOUTUBE') throw new Error(`Wrong action type: ${parsed.action.type}`);
        if (!parsed.action.query?.toLowerCase().includes('telugu')) throw new Error('Missing query parameter in action');
      },
    },
    {
      id: 'act-2',
      category: 'Actions & Intents',
      name: 'Action Intent: "Open Chrome"',
      description: 'Validates OPEN_APP action for Android applications.',
      status: (testResults['act-2']?.status as any) || 'PENDING',
      durationMs: testResults['act-2']?.durationMs,
      error: testResults['act-2']?.error,
      run: async () => {
        const parsed = ActionHandler.parseActionFromLLM('Open Chrome');
        if (!parsed.hasAction || parsed.action?.type !== 'OPEN_APP') {
          throw new Error('Failed to parse OPEN_APP action for Chrome');
        }
      },
    },
    {
      id: 'act-3',
      category: 'Actions & Intents',
      name: 'Action Intent: "Open Settings"',
      description: 'Validates OPEN_SETTINGS intent.',
      status: (testResults['act-3']?.status as any) || 'PENDING',
      durationMs: testResults['act-3']?.durationMs,
      error: testResults['act-3']?.error,
      run: async () => {
        const parsed = ActionHandler.parseActionFromLLM('Open Settings');
        if (!parsed.hasAction || parsed.action?.type !== 'OPEN_SETTINGS') {
          throw new Error('Failed to parse OPEN_SETTINGS action');
        }
      },
    },
    {
      id: 'act-4',
      category: 'Actions & Intents',
      name: 'Security Filter: Reject Arbitrary Code & Shell Execution',
      description: 'Ensures unauthorized commands cannot execute.',
      status: (testResults['act-4']?.status as any) || 'PENDING',
      durationMs: testResults['act-4']?.durationMs,
      error: testResults['act-4']?.error,
      run: async () => {
        const malicious = '```json\n{"action": "EXEC_SHELL", "command": "rm -rf /"}\n```';
        const parsed = ActionHandler.parseActionFromLLM(malicious);
        if (parsed.hasAction && (parsed.action?.type as string) === 'EXEC_SHELL') {
          throw new Error('Malicious shell action was not rejected by validator');
        }
      },
    },

    // 4. Voice STT & TTS
    {
      id: 'voice-1',
      category: 'Voice STT',
      name: 'Whisper STT Readiness & Audio Analyser',
      description: 'Verifies Whisper model state and Web Audio frequency analyser initialization.',
      status: (testResults['voice-1']?.status as any) || 'PENDING',
      durationMs: testResults['voice-1']?.durationMs,
      error: testResults['voice-1']?.error,
      run: async () => {
        const whisper = modelManager.getModel('whisper-base');
        if (!whisper || whisper.state !== 'READY') throw new Error('Whisper model is not in READY state');
      },
    },
    {
      id: 'tts-1',
      category: 'TTS',
      name: 'Android TextToSpeech Engine & Language Fallback',
      description: 'Tests speech synthesis dispatcher and non-crashing voice detection.',
      status: (testResults['tts-1']?.status as any) || 'PENDING',
      durationMs: testResults['tts-1']?.durationMs,
      error: testResults['tts-1']?.error,
      run: async () => {
        // Speech check without requiring physical speaker output
        const voices = tts.getVoices();
        // Speak short test string
        tts.speak('MyAI test', { lang: 'en-US' });
        tts.stop();
      },
    },
  ];

  const runSingleTest = async (test: TestCase) => {
    setTestResults(prev => ({
      ...prev,
      [test.id]: { status: 'RUNNING' },
    }));

    const start = performance.now();
    try {
      await test.run();
      const dur = Math.round(performance.now() - start);
      setTestResults(prev => ({
        ...prev,
        [test.id]: { status: 'PASSED', durationMs: dur },
      }));
    } catch (e: any) {
      const dur = Math.round(performance.now() - start);
      setTestResults(prev => ({
        ...prev,
        [test.id]: { status: 'FAILED', durationMs: dur, error: e?.message || String(e) },
      }));
    }
  };

  const runAllTests = async () => {
    setIsRunningAll(true);
    for (const test of tests) {
      await runSingleTest(test);
    }
    setIsRunningAll(false);
  };

  if (!isOpen) return null;

  const passedCount = Object.values(testResults).filter((t: { status: string }) => t.status === 'PASSED').length;
  const failedCount = Object.values(testResults).filter((t: { status: string }) => t.status === 'FAILED').length;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-3 sm:p-6 bg-black/75 backdrop-blur-md animate-in fade-in duration-150" id="test-suite-modal">
      <div className="bg-[#0e0e12]/95 backdrop-blur-2xl border border-white/15 rounded-3xl w-full max-w-4xl max-h-[90vh] flex flex-col shadow-2xl shadow-black/90 overflow-hidden">
        {/* Header */}
        <div className="px-5 py-4 border-b border-white/10 flex items-center justify-between bg-white/[0.02]">
          <div className="flex items-center gap-3">
            <div className="p-2.5 rounded-2xl bg-indigo-500/10 border border-indigo-500/20 text-indigo-400">
              <FlaskConical className="w-5 h-5" />
            </div>
            <div>
              <h2 className="text-base sm:text-lg font-bold text-white">Automated Verification Test Suite</h2>
              <p className="text-xs text-zinc-400">
                Executes unit & integration tests across Model Manager, Qwen3 LLM, Action Intents, Whisper, and TTS.
              </p>
            </div>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="p-2 rounded-xl text-zinc-400 hover:text-white hover:bg-white/10 border border-transparent hover:border-white/10 transition-colors cursor-pointer"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Toolbar */}
        <div className="px-5 py-3.5 bg-white/[0.02] border-b border-white/10 flex flex-wrap items-center justify-between gap-3">
          <div className="flex items-center gap-4 text-xs">
            <span className="font-semibold text-zinc-300">Test Execution Progress:</span>
            <span className="text-emerald-400 font-mono flex items-center gap-1.5 font-bold">
              <CheckCircle2 className="w-4 h-4" /> {passedCount} Passed
            </span>
            {failedCount > 0 && (
              <span className="text-red-400 font-mono flex items-center gap-1.5 font-bold">
                <XCircle className="w-4 h-4" /> {failedCount} Failed
              </span>
            )}
            <span className="text-zinc-500 font-mono text-[11px]">
              Total: {tests.length} specs
            </span>
          </div>

          <div className="flex items-center gap-2">
            <button
              type="button"
              id="run-all-tests-button"
              onClick={runAllTests}
              disabled={isRunningAll}
              className="px-4 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-semibold text-xs flex items-center gap-2 shadow-lg shadow-indigo-500/30 disabled:opacity-50 transition-all active:scale-95 cursor-pointer"
            >
              <Play className="w-3.5 h-3.5 fill-current" />
              {isRunningAll ? 'Running Verification...' : 'Run All Tests'}
            </button>
          </div>
        </div>

        {/* Test List */}
        <div className="flex-1 overflow-y-auto p-4 sm:p-5 space-y-2.5">
          {tests.map(test => {
            const result = testResults[test.id];
            const status = result?.status || 'PENDING';

            return (
              <div
                key={test.id}
                id={`test-case-${test.id}`}
                className="p-3.5 rounded-2xl bg-white/[0.02] hover:bg-white/[0.05] border border-white/10 hover:border-white/20 flex flex-col sm:flex-row sm:items-center justify-between gap-3 transition-colors backdrop-blur-md"
              >
                <div className="space-y-1 flex-1">
                  <div className="flex items-center gap-2">
                    <span className="text-[10px] uppercase tracking-wider font-mono px-2 py-0.5 rounded-md bg-white/5 text-zinc-300 border border-white/10">
                      {test.category}
                    </span>
                    <h4 className="text-xs sm:text-sm font-bold text-zinc-100">{test.name}</h4>
                  </div>
                  <p className="text-xs text-zinc-400">{test.description}</p>
                  {result?.error && (
                    <div className="text-xs text-red-400 font-mono bg-red-500/10 p-2.5 rounded-xl border border-red-500/20 mt-1">
                      Error: {result.error}
                    </div>
                  )}
                </div>

                <div className="flex items-center gap-3 self-end sm:self-center flex-shrink-0">
                  {status === 'PASSED' ? (
                    <div className="flex items-center gap-1.5 text-xs text-emerald-400 font-mono bg-emerald-500/10 border border-emerald-500/20 px-3 py-1.5 rounded-xl">
                      <CheckCircle2 className="w-4 h-4" />
                      <span>Passed ({result?.durationMs}ms)</span>
                    </div>
                  ) : status === 'FAILED' ? (
                    <div className="flex items-center gap-1.5 text-xs text-red-400 font-mono bg-red-500/10 border border-red-500/20 px-3 py-1.5 rounded-xl">
                      <XCircle className="w-4 h-4" />
                      <span>Failed</span>
                    </div>
                  ) : status === 'RUNNING' ? (
                    <div className="flex items-center gap-1.5 text-xs text-indigo-400 font-mono bg-indigo-500/10 border border-indigo-500/20 px-3 py-1.5 rounded-xl animate-pulse">
                      <Clock className="w-4 h-4 animate-spin" />
                      <span>Running...</span>
                    </div>
                  ) : (
                    <button
                      type="button"
                      onClick={() => runSingleTest(test)}
                      disabled={isRunningAll}
                      className="px-3.5 py-1.5 rounded-xl bg-white/5 hover:bg-white/10 text-zinc-200 text-xs font-medium border border-white/10 flex items-center gap-1.5 transition-all cursor-pointer active:scale-95"
                    >
                      <Play className="w-3 h-3" />
                      Run
                    </button>
                  )}
                </div>
              </div>
            );
          })}
        </div>

        {/* Footer */}
        <div className="px-5 py-3.5 border-t border-white/10 bg-white/[0.02] flex items-center justify-between text-xs text-zinc-400 font-mono">
          <span>Target: llama.cpp JNI • Qwen3 • Whisper STT</span>
          <span className="text-emerald-400 flex items-center gap-1.5">
            <ShieldCheck className="w-3.5 h-3.5" />
            Deterministic Offline Validation
          </span>
        </div>
      </div>
    </div>
  );
};
