package com.myai.offline

import com.myai.offline.actions.ActionRequestDetector
import com.myai.offline.actions.AndroidActionHandler
import com.myai.offline.assistant.AssistantResponsePipeline
import com.myai.offline.assistant.ResponsePhase
import com.myai.offline.data.model.AssistantActionType
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class AssistantResponsePipelineTest {
    private val normalRequests = listOf(
        "Hello", "Hi", "What is Android?", "What is Python?", "Explain machine learning",
        "How do I create a website?", "How do I create a wedding invitation website?",
        "How to create a website for wedding invitation website",
        "Write HTML for a wedding invitation website", "Write Python code", "Explain this code",
        "Give me ideas for my project", "What is 7+9?", "6+9",
        "Explain how to open YouTube", "Do not open Chrome", "What does Open Settings mean?",
        "Open a website", "Open https://example.com", "Open WhatsApp and send a message",
        "Open the door", "Open Chrome tutorials", "Open example.com",
        "Run this Python code", "Search the web for wedding ideas", "Open constructor",
        "YouTube", "Settings", "Do not open YouTube", "How do I open WhatsApp?",
        "Write code to open Chrome", "Open YouTube\nand open Settings",
        "Search YouTube for", "Search YouTube for ${"x".repeat(301)}",
        "{\"action\":\"OPEN_YOUTUBE\"}"
    )

    @Test
    fun normalRequestsAlwaysStreamChatTextAndFinishIdle() = runTest {
        for (request in normalRequests) {
            assertNull(request, ActionRequestDetector.detect(request))
            val phases = mutableListOf<ResponsePhase>()
            val streamed = mutableListOf<String>()
            val answer = "To create a wedding invitation website, you can use HTML, CSS and JavaScript."
            val response = AssistantResponsePipeline().respond(
                userText = request,
                generate = { flowOf(answer.take(20), answer.drop(20)) },
                execute = { error("Ordinary conversation must never execute an action: $request") },
                onPhase = { phases.add(it) },
                onText = { streamed.add(it) }
            )
            assertEquals(request, answer, response.text)
            assertEquals(answer, streamed.last())
            assertNull(response.action)
            assertEquals(listOf(ResponsePhase.GENERATING, ResponsePhase.IDLE), phases)
        }
    }

    @Test
    fun modelJsonAndCodeCannotPromoteNormalConversationIntoActions() = runTest {
        val outputs = listOf(
            "```html\n<html><body>Our wedding</body></html>\n```",
            "```python\nprint('Open YouTube')\n```",
            "```json\n{\"action\":\n```",
            "{\"title\":\"Wedding\"}",
            "{\"action\":\"UNKNOWN_ACTION\"}",
            "{\"action\":\"OPEN_YOUTUBE\"}",
            "Here is an example: ```json\n{\"action\":\"OPEN_SETTINGS\"}\n```"
        )
        for (output in outputs) {
            val phases = mutableListOf<ResponsePhase>()
            val response = AssistantResponsePipeline().respond(
                "Explain this code", { flowOf(output) },
                execute = { error("Model output must not authorize execution") },
                onPhase = { phases.add(it) }
            )
            assertEquals(output, response.text)
            assertNull(response.action)
            assertFalse(phases.contains(ResponsePhase.EXECUTING_ACTION))
            assertEquals(ResponsePhase.IDLE, phases.last())
        }
    }

    @Test
    fun explicitRequestsExecuteExactlyOnceAndReturnResultToChat() = runTest {
        val requests = mapOf(
            "Open YouTube" to AssistantActionType.OPEN_YOUTUBE,
            "Open Chrome" to AssistantActionType.OPEN_CHROME,
            "Open Settings" to AssistantActionType.OPEN_SETTINGS,
            "Open WhatsApp" to AssistantActionType.OPEN_APP,
            "Open Custom Reader app" to AssistantActionType.OPEN_APP,
            "Search YouTube for Python tutorials" to AssistantActionType.SEARCH_YOUTUBE,
            "Could you please open Chrome for me?" to AssistantActionType.OPEN_CHROME,
            "Open YouTube and search Telugu songs" to AssistantActionType.SEARCH_YOUTUBE
        )
        for ((request, type) in requests) {
            val phases = mutableListOf<ResponsePhase>()
            var executions = 0
            val response = AssistantResponsePipeline().respond(
                request,
                generate = { error("A confident command does not need conversational inference") },
                execute = { action ->
                    executions++
                    assertEquals(type, action.type)
                    AndroidActionHandler.ExecutionOutcome(true, "Action completed")
                },
                onPhase = { phases.add(it) }
            )
            assertEquals(1, executions)
            assertEquals("Action completed", response.text)
            assertEquals(true, response.action?.executed)
            assertEquals(listOf(ResponsePhase.EXECUTING_ACTION, ResponsePhase.IDLE), phases)
        }
        assertEquals("Python tutorials", ActionRequestDetector.detect("Search YouTube for Python tutorials")?.query)
        assertEquals("WhatsApp", ActionRequestDetector.detect("Open WhatsApp")?.appName)
    }

    @Test
    fun failuresAndEmptyResponsesAlwaysFinishIdle() = runTest {
        for (request in listOf("Explain Python", "Open YouTube")) {
            val phases = mutableListOf<ResponsePhase>()
            val response = AssistantResponsePipeline().respond(
                request, { flow { emit("Partial answer"); error("Inference failed") } },
                execute = { error("No installed activity") }, onPhase = { phases.add(it) }
            )
            assertTrue(response.text.contains("Unable to complete"))
            if (request == "Explain Python") assertTrue(response.text.startsWith("Partial answer"))
            assertEquals(ResponsePhase.IDLE, phases.last())
        }
        val empty = AssistantResponsePipeline().respond("Hi", { flowOf("") }, { error("Unexpected action") })
        assertTrue(empty.text.contains("empty response"))
    }

    @Test
    fun unsuccessfulActionReturnsItsResultAndDoesNotAffectTheNextConversation() = runTest {
        val pipeline = AssistantResponsePipeline()
        val failed = pipeline.respond("Open WhatsApp", { error("Unexpected inference") }, {
            AndroidActionHandler.ExecutionOutcome(false, "WhatsApp is not installed.")
        })
        assertEquals("WhatsApp is not installed.", failed.text)
        assertEquals(false, failed.action?.executed)
        val next = pipeline.respond("What is Python?", { flowOf("Python is a programming language.") }, {
            error("Unexpected action")
        })
        assertEquals("Python is a programming language.", next.text)
        assertNull(next.action)
    }

    @Test
    fun callerTimeoutIsNotSwallowedAndStillRestoresIdle() = runTest {
        val phases = mutableListOf<ResponsePhase>()
        try {
            withTimeout(10) {
                AssistantResponsePipeline().respond(
                    "Explain Python", { flow { awaitCancellation() } }, { error("Unexpected action") },
                    onPhase = { phases.add(it) }
                )
            }
            fail("The caller's cancellation must propagate")
        } catch (_: TimeoutCancellationException) {
            assertEquals(ResponsePhase.IDLE, phases.last())
        }
    }

    @Test
    fun generationAndActionTimeoutsTerminateAndPreservePartialText() = runTest {
        for (request in listOf("Explain Python", "Open YouTube")) {
            val phases = mutableListOf<ResponsePhase>()
            val response = AssistantResponsePipeline(generationTimeoutMs = 50, actionTimeoutMs = 50).respond(
                request, { flow { emit("Partial answer"); awaitCancellation() } },
                execute = { awaitCancellation() }, onPhase = { phases.add(it) }
            )
            assertTrue(response.text.contains("timed out"))
            if (request == "Explain Python") assertTrue(response.text.startsWith("Partial answer"))
            assertEquals(ResponsePhase.IDLE, phases.last())
        }
    }

    @Test
    fun cancellingGenerationOrActionAlwaysRestoresIdle() = runTest {
        for (request in listOf("Explain Python", "Open YouTube")) {
            val phases = mutableListOf<ResponsePhase>()
            var completed = false
            val job = launch {
                AssistantResponsePipeline().respond(
                    request, { flow { emit("Partial answer"); awaitCancellation() } },
                    execute = { awaitCancellation() }, onPhase = { phases.add(it) }
                )
                completed = true
            }
            runCurrent()
            job.cancelAndJoin()
            assertFalse(completed)
            assertEquals(ResponsePhase.IDLE, phases.last())
        }
    }
}
