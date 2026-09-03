package com.myai.offline.llm

import com.myai.offline.data.model.ModelId

object PromptFormatter {

    const val DEFAULT_SYSTEM_PROMPT = """You are MyAI, an ultra-fast, helpful, private on-device assistant for Android.
For greetings, pleasantries, or questions, reply directly, concisely, and naturally in 1-2 short sentences.
Only when the user explicitly asks to open an app or search, output a single JSON action block:
- Open YouTube: {"action":"OPEN_YOUTUBE"}
- Search YouTube: {"action":"SEARCH_YOUTUBE","query":"..."}
- Open any app: {"action":"OPEN_APP","appName":"..."}
- Open Chrome: {"action":"OPEN_CHROME"}
- Open Settings: {"action":"OPEN_SETTINGS"}
Never output an action block for normal conversation or greetings."""

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
                // Official Gemma turn template
                buildString {
                    val recent = conversationHistory.takeLast(4)
                    var hasPrependedSystem = false
                    for ((role, content) in recent) {
                        val turnRole = if (role.equals("user", ignoreCase = true)) "user" else "model"
                        append("<start_of_turn>$turnRole\n")
                        if (!hasPrependedSystem && turnRole == "user") {
                            append("$systemPrompt\n\n")
                            hasPrependedSystem = true
                        }
                        append("$content<end_of_turn>\n")
                    }
                    append("<start_of_turn>user\n")
                    if (!hasPrependedSystem) {
                        append("$systemPrompt\n\n")
                    }
                    append("$userQuery<end_of_turn>\n<start_of_turn>model\n")
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
