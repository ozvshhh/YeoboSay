package com.yeobosay.app.ui.call

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CallStateMachineTest {
    @Test
    fun incomingCallMovesHomeToRingingScreen() {
        val state = CallStateReducer.reduce(
            CallFlowState(),
            CallFlowEvent.IncomingCallReceived(
                invitationId = "invitation-1",
                callerName = "YeoboSay",
                message = "AI 말벗 전화가 왔어요.",
                expiresAt = "2026-05-30T10:00:00.000Z",
            ),
        )

        assertEquals(CallScreenState.Incoming, state.screenState)
        assertEquals(CallFlowPhase.Ringing, state.phase)
        assertEquals("invitation-1", state.callInvitationId)
        assertEquals("YeoboSay", state.incomingCall?.callerName)
        assertEquals("AI 말벗 전화가 왔어요.", state.incomingCall?.message)
        assertEquals("2026-05-30T10:00:00.000Z", state.incomingCall?.expiresAt)
        assertEquals("ringing", state.debugStatus)
    }

    @Test
    fun acceptedCallMovesToSessionCreation() {
        val ringing = CallFlowState(
            screenState = CallScreenState.Incoming,
            phase = CallFlowPhase.Ringing,
            incomingCall = IncomingCallDetails(
                invitationId = "invitation-1",
                callerName = "YeoboSay",
                message = "AI 말벗 전화가 왔어요.",
                expiresAt = "2026-05-30T10:00:00.000Z",
            ),
        )

        val state = CallStateReducer.reduce(ringing, CallFlowEvent.IncomingCallAccepted)

        assertEquals(CallScreenState.Connecting, state.screenState)
        assertEquals(CallFlowPhase.CreatingSession, state.phase)
        assertNull(state.errorText)
    }

    @Test
    fun sessionCreatedMovesToActiveListeningWithGreeting() {
        val connecting = CallFlowState(
            screenState = CallScreenState.Connecting,
            phase = CallFlowPhase.CreatingSession,
            incomingCall = IncomingCallDetails(
                invitationId = "invitation-1",
                callerName = "YeoboSay",
                message = "AI 말벗 전화가 왔어요.",
                expiresAt = "2026-05-30T10:00:00.000Z",
            ),
        )

        val state = CallStateReducer.reduce(
            connecting,
            CallFlowEvent.SessionCreated(
                sessionId = "session-1",
                firstGreetingText = "안녕하세요 왕송길 어르신 AI통화 서비스 세요입니다!",
            ),
        )

        assertEquals(CallScreenState.Active, state.screenState)
        assertEquals(CallFlowPhase.Listening, state.phase)
        assertNull(state.incomingCall)
        assertEquals("session-1", state.callSessionId)
        assertEquals(1, state.messages.size)
        assertEquals(ConversationMessageRole.Assistant, state.messages.first().role)
    }

    @Test
    fun speechAndPlaybackEventsMoveThroughAutomaticTurnFlow() {
        val active = CallFlowState(
            screenState = CallScreenState.Active,
            phase = CallFlowPhase.Listening,
            callSessionId = "session-1",
        )

        val speaking = CallStateReducer.reduce(active, CallFlowEvent.UserSpeechStarted)
        val processing = CallStateReducer.reduce(speaking, CallFlowEvent.UserSpeechEnded)
        val responded = CallStateReducer.reduce(
            processing,
            CallFlowEvent.AssistantResponseReceived("식사는 잘 챙겨 드셨어요?"),
        )
        val aiSpeaking = CallStateReducer.reduce(responded, CallFlowEvent.AssistantPlaybackStarted)
        val listening = CallStateReducer.reduce(aiSpeaking, CallFlowEvent.AssistantPlaybackFinished)

        assertEquals(CallFlowPhase.UserSpeaking, speaking.phase)
        assertEquals(CallFlowPhase.ProcessingTurn, processing.phase)
        assertEquals(1, responded.messages.size)
        assertEquals(CallFlowPhase.AiSpeaking, aiSpeaking.phase)
        assertEquals(CallFlowPhase.Listening, listening.phase)
    }
}
