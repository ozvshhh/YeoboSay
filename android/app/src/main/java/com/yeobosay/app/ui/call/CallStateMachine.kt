package com.yeobosay.app.ui.call

enum class CallScreenState {
    Home,
    Incoming,
    Connecting,
    Active,
    Ending,
    Summary,
}

enum class CallFlowPhase {
    Idle,
    Ringing,
    CreatingSession,
    Listening,
    UserSpeaking,
    ProcessingTurn,
    AiSpeaking,
    Ending,
    Ended,
    Error,
}

enum class ConversationMessageRole {
    User,
    Assistant,
    System,
}

data class ConversationMessage(
    val id: String,
    val role: ConversationMessageRole,
    val text: String,
    val failed: Boolean = false,
    val riskFlag: Boolean = false,
)

data class CallFlowState(
    val screenState: CallScreenState = CallScreenState.Home,
    val phase: CallFlowPhase = CallFlowPhase.Idle,
    val callInvitationId: String? = null,
    val callSessionId: String? = null,
    val messages: List<ConversationMessage> = emptyList(),
    val debugStatus: String = "idle",
    val errorText: String? = null,
)

sealed interface CallFlowEvent {
    data class IncomingCallReceived(val invitationId: String) : CallFlowEvent
    data object IncomingCallAccepted : CallFlowEvent
    data object IncomingCallDeclined : CallFlowEvent
    data class SessionCreated(
        val sessionId: String,
        val firstGreetingText: String?,
    ) : CallFlowEvent
    data object ListeningStarted : CallFlowEvent
    data object UserSpeechStarted : CallFlowEvent
    data object UserSpeechEnded : CallFlowEvent
    data class AssistantResponseReceived(val text: String, val failed: Boolean = false) :
        CallFlowEvent
    data object AssistantPlaybackStarted : CallFlowEvent
    data object AssistantPlaybackFinished : CallFlowEvent
    data object EndingStarted : CallFlowEvent
    data object SessionEnded : CallFlowEvent
    data class Failed(val message: String) : CallFlowEvent
}

object CallStateReducer {
    fun reduce(state: CallFlowState, event: CallFlowEvent): CallFlowState =
        when (event) {
            is CallFlowEvent.IncomingCallReceived -> state.copy(
                screenState = CallScreenState.Incoming,
                phase = CallFlowPhase.Ringing,
                callInvitationId = event.invitationId,
                errorText = null,
                debugStatus = "ringing",
            )

            CallFlowEvent.IncomingCallAccepted -> state.copy(
                screenState = CallScreenState.Connecting,
                phase = CallFlowPhase.CreatingSession,
                errorText = null,
                debugStatus = "creating_session",
            )

            CallFlowEvent.IncomingCallDeclined -> CallFlowState(
                debugStatus = "declined",
            )

            is CallFlowEvent.SessionCreated -> state.copy(
                screenState = CallScreenState.Active,
                phase = CallFlowPhase.Listening,
                callSessionId = event.sessionId,
                messages = event.firstGreetingText
                    ?.takeIf { it.isNotBlank() }
                    ?.let { greeting ->
                        state.messages + ConversationMessage(
                            id = "greeting",
                            role = ConversationMessageRole.Assistant,
                            text = greeting,
                        )
                    }
                    ?: state.messages,
                errorText = null,
                debugStatus = "listening",
            )

            CallFlowEvent.ListeningStarted -> state.copy(
                screenState = CallScreenState.Active,
                phase = CallFlowPhase.Listening,
                debugStatus = "listening",
            )

            CallFlowEvent.UserSpeechStarted -> state.copy(
                screenState = CallScreenState.Active,
                phase = CallFlowPhase.UserSpeaking,
                debugStatus = "user_speaking",
            )

            CallFlowEvent.UserSpeechEnded -> state.copy(
                screenState = CallScreenState.Active,
                phase = CallFlowPhase.ProcessingTurn,
                debugStatus = "processing_turn",
            )

            is CallFlowEvent.AssistantResponseReceived -> state.copy(
                screenState = CallScreenState.Active,
                phase = CallFlowPhase.ProcessingTurn,
                messages = state.messages + ConversationMessage(
                    id = "assistant-${state.messages.size + 1}",
                    role = ConversationMessageRole.Assistant,
                    text = event.text,
                    failed = event.failed,
                ),
                debugStatus = "assistant_response_received",
            )

            CallFlowEvent.AssistantPlaybackStarted -> state.copy(
                screenState = CallScreenState.Active,
                phase = CallFlowPhase.AiSpeaking,
                debugStatus = "ai_speaking",
            )

            CallFlowEvent.AssistantPlaybackFinished -> state.copy(
                screenState = CallScreenState.Active,
                phase = CallFlowPhase.Listening,
                debugStatus = "listening",
            )

            CallFlowEvent.EndingStarted -> state.copy(
                screenState = CallScreenState.Ending,
                phase = CallFlowPhase.Ending,
                debugStatus = "ending",
            )

            CallFlowEvent.SessionEnded -> state.copy(
                screenState = CallScreenState.Summary,
                phase = CallFlowPhase.Ended,
                debugStatus = "ended",
            )

            is CallFlowEvent.Failed -> state.copy(
                phase = CallFlowPhase.Error,
                errorText = event.message,
                debugStatus = "error",
            )
        }
}
