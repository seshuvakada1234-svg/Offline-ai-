package com.myai.offline.llm

import com.myai.offline.data.model.ModelId

object PromptFormatter {

    const val DEFAULT_SYSTEM_PROMPT = """You are MyAI, a high-performance, private, on-device AI assistant for Android.
When the user asks to open an app or search, output a structured JSON action block enclosed in ```json ``` with one of the allowed actions:
- OPEN_YOUTUBE
- SEARCH_YOUTUBE (with query parameter)
- OPEN_APP (with appName parameter)
- OPEN_CHROME
- OPEN_SETTINGS
For all other queries, answer directly with clear, concise markdown."""

    /**
     * Formats prompt according to the selected model's official chat template.
     */
    fun format(
        modelId: ModelId,
        systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
        conversationHistory: List<Pair<String, String>> = emptyList(),
        userQuery: String
    ): String {
        return when (modelId) {
            ModelId.QWEN3_1_7B, ModelId.QWEN3_4B -> {
                // Qwen ChatML template
                buildString {
                    append("<|im_start|>system\n$systemPrompt<|im_end|>\n")
                    for ((role, content) in conversationHistory.takeLast(4)) {
                        append("<|im_start|>$role\n$content<|im_end|>\n")
                    }
                    append("<|im_start|>user\n$userQuery<|im_end|>\n")
                    append("<|im_start|>assistant\n")
                }
            }
            ModelId.PHI4_MINI -> {
                // Phi-4 template
                buildString {
                    append("<|system|>\n$systemPrompt<|end|>\n")
                    for ((role, content) in conversationHistory.takeLast(4)) {
                        append("<|$role|>\n$content<|end|>\n")
                    }
                    append("<|user|>\n$userQuery<|end|>\n")
                    append("<|assistant|>\n")
                }
            }
            ModelId.GEMMA3_1B, ModelId.GEMMA3_4B -> {
                // Gemma turn template
                buildString {
                    append("<start_of_turn>user\n$systemPrompt\n\n")
                    for ((role, content) in conversationHistory.takeLast(4)) {
                        append("$role: $content\n")
                    }
                    append("User: $userQuery<end_of_turn>\n<start_of_turn>model\n")
                }
            }
            else -> {
                // Default fallback template
                buildString {
                    append("System: $systemPrompt\n\n")
                    for ((role, content) in conversationHistory.takeLast(4)) {
                        append("$role: $content\n")
                    }
                    append("User: $userQuery\nAssistant: ")
                }
            }
        }
    }
}
