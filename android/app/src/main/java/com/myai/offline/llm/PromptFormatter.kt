package com.myai.offline.llm

import com.myai.offline.data.model.ModelId

object PromptFormatter {

    const val DEFAULT_SYSTEM_PROMPT = """You are MyAI, a helpful, private, on-device conversational AI assistant.
For ordinary questions, answer normally in clear Markdown. Do not output action JSON.
Greetings, explanations, ideas, arithmetic, website creation, and requests to write or explain code are ordinary conversations. Provide the requested answer or code directly. Code examples are never device commands.
Only output an action structure when the user explicitly requests one of these supported device actions:
{"action":"OPEN_YOUTUBE"}
{"action":"SEARCH_YOUTUBE","query":"the user's search terms"}
{"action":"OPEN_APP","appName":"the requested application"}
{"action":"OPEN_CHROME"}
{"action":"OPEN_SETTINGS"}
Never infer a device action from a topic mentioned in a question. Do not invent actions. All other requests are normal conversations."""

    /**
     * Formats prompt according to the selected model's official chat template.
     */
    fun format(
        modelId: ModelId,
        systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
        conversationHistory: List<Pair<String, String>> = emptyList(),
        userQuery: String,
        enableThinking: Boolean = false
    ): String {
        return when (modelId) {
            ModelId.QWEN3_1_7B, ModelId.QWEN3_4B -> {
                // Qwen ChatML template
                val qwenUserQuery = applyQwenThinkingDirective(userQuery, enableThinking)
                buildString {
                    append("<|im_start|>system\n$systemPrompt<|im_end|>\n")
                    for ((role, content) in conversationHistory.takeLast(4)) {
                        append("<|im_start|>$role\n$content<|im_end|>\n")
                    }
                    append("<|im_start|>user\n$qwenUserQuery<|im_end|>\n")
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

    private fun applyQwenThinkingDirective(userQuery: String, enableThinking: Boolean): String {
        val query = userQuery.trim()
        val thinkPos = query.lastIndexOf("/think", ignoreCase = true)
        val noThinkPos = query.lastIndexOf("/no_think", ignoreCase = true)
        if (thinkPos >= 0 || noThinkPos >= 0) {
            return query
        }
        return if (enableThinking) "$query /think" else "$query /no_think"
    }
}
