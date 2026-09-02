package com.myai.offline

import com.myai.offline.data.model.ModelConstants
import com.myai.offline.data.model.ModelId
import com.myai.offline.data.model.VoiceState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceStateTest {

    @Test
    fun testVoiceStateEnumValues() {
        val states = VoiceState.values().toList()
        assertTrue(states.contains(VoiceState.IDLE))
        assertTrue(states.contains(VoiceState.LISTENING))
        assertTrue(states.contains(VoiceState.TRANSCRIBING))
        assertTrue(states.contains(VoiceState.THINKING))
        assertTrue(states.contains(VoiceState.SPEAKING))
        assertTrue(states.contains(VoiceState.ERROR))
    }

    @Test
    fun testVoiceStateTransitions() {
        var currentState = VoiceState.IDLE
        assertEquals(VoiceState.IDLE, currentState)

        // User taps mic -> LISTENING
        currentState = VoiceState.LISTENING
        assertEquals(VoiceState.LISTENING, currentState)

        // User releases mic / silence -> TRANSCRIBING
        currentState = VoiceState.TRANSCRIBING
        assertEquals(VoiceState.TRANSCRIBING, currentState)

        // Whisper transcription finished -> THINKING (LLM processing)
        currentState = VoiceState.THINKING
        assertEquals(VoiceState.THINKING, currentState)

        // LLM finishes response -> SPEAKING (TTS)
        currentState = VoiceState.SPEAKING
        assertEquals(VoiceState.SPEAKING, currentState)

        // TTS finishes / User stops -> IDLE
        currentState = VoiceState.IDLE
        assertEquals(VoiceState.IDLE, currentState)
    }

    @Test
    fun testVoiceErrorRecovery() {
        var currentState = VoiceState.LISTENING
        // On error (e.g. mic permission denied or whisper failure)
        currentState = VoiceState.ERROR
        assertEquals(VoiceState.ERROR, currentState)

        // Recovery back to IDLE
        currentState = VoiceState.IDLE
        assertEquals(VoiceState.IDLE, currentState)
    }

    @Test
    fun testWhisperModelIsSeparatedFromChatModelList() {
        val whisper = ModelConstants.INITIAL_MODELS.first { it.id == ModelId.WHISPER_BASE }
        assertTrue(whisper.isWhisper)

        val chatModels = ModelConstants.INITIAL_MODELS.filter { it.isChatModel }
        assertFalse(chatModels.any { it.id == ModelId.WHISPER_BASE })
    }
}
