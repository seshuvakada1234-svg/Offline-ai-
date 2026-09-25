package com.myai.offline.assistant

import com.myai.offline.actions.ActionRequestDetector
import com.myai.offline.actions.ActionValidator
import com.myai.offline.actions.AndroidActionHandler
import com.myai.offline.data.model.AssistantAction
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withTimeout

enum class ResponsePhase { IDLE, GENERATING, EXECUTING_ACTION }

data class AssistantResponse(
    val text: String,
    val action: AssistantAction? = null
)

/** A user request selects the route; generated text can never promote a conversation into an action. */
class AssistantResponsePipeline(
    private val generationTimeoutMs: Long = 120_000L,
    private val actionTimeoutMs: Long = 8_000L
) {
    suspend fun respond(
        userText: String,
        generate: () -> Flow<String>,
        execute: suspend (AssistantAction) -> AndroidActionHandler.ExecutionOutcome,
        onPhase: (ResponsePhase) -> Unit = {},
        onText: (String) -> Unit = {}
    ): AssistantResponse {
        val text = StringBuilder()
        try {
            currentCoroutineContext().ensureActive()
            val requestedAction = ActionRequestDetector.detect(userText)
                ?.takeIf { ActionValidator.validate(it) is ActionValidator.ValidationResult.Valid }
            if (requestedAction != null) {
                onPhase(ResponsePhase.EXECUTING_ACTION)
                val outcome = withTimeout(actionTimeoutMs) { execute(requestedAction) }
                return AssistantResponse(
                    text = outcome.message,
                    action = requestedAction.copy(executed = outcome.success, resultMessage = outcome.message)
                )
            }

            onPhase(ResponsePhase.GENERATING)
            withTimeout(generationTimeoutMs) {
                generate().collect { chunk ->
                    text.append(chunk)
                    onText(text.toString())
                }
            }
            return AssistantResponse(text.toString().trim().ifBlank {
                "The model returned an empty response. Please try again."
            })
        } catch (e: TimeoutCancellationException) {
            // A timeout belonging to our caller is cancellation, not a completed response.
            currentCoroutineContext().ensureActive()
            return AssistantResponse(withPartial(text, "The request timed out. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            return AssistantResponse(withPartial(text, "Unable to complete the request: ${e.localizedMessage ?: "unknown error"}"))
        } finally {
            onPhase(ResponsePhase.IDLE)
        }
    }

    private fun withPartial(text: StringBuilder, error: String): String {
        val partial = text.toString().trim()
        return if (partial.isEmpty()) error else "$partial\n\n$error"
    }
}
