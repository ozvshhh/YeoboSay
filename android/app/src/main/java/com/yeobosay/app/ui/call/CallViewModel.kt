package com.yeobosay.app.ui.call

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yeobosay.app.data.CallInvitationResponse
import com.yeobosay.app.data.CallInvitationSocket
import com.yeobosay.app.data.IncomingCallEvent
import com.yeobosay.app.data.YeoboSayApi
import com.yeobosay.app.voice.AudioPlayer
import com.yeobosay.app.voice.AudioRecorder
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val MAX_RECORDING_MILLIS = 30_000L
private const val MIN_RECORDING_MILLIS = 500L
private const val DEFAULT_GREETING = "안녕하세요. 저는 YeoboSay 말벗이에요. 오늘은 어떻게 지내셨어요?"

data class CallMessage(
    val role: MessageRole,
    val text: String,
    val failed: Boolean = false,
    val riskFlag: Boolean = false,
)

enum class MessageRole {
    User,
    Assistant,
    System,
}

data class CallUiState(
    val callSessionId: String? = null,
    val expiresAt: String? = null,
    val messages: List<CallMessage> = emptyList(),
    val incomingCall: IncomingCallUiState? = null,
    val isStartingSession: Boolean = false,
    val isRequestingTestCall: Boolean = false,
    val isAcceptingIncomingCall: Boolean = false,
    val isDecliningIncomingCall: Boolean = false,
    val isRecording: Boolean = false,
    val isUploading: Boolean = false,
    val isPlaying: Boolean = false,
    val statusText: String = "통화 세션을 시작해 주세요.",
    val socketStatusText: String = "전화 수신 서버에 연결 중입니다.",
    val errorText: String? = null,
)

data class IncomingCallUiState(
    val callInvitationId: String,
    val callerName: String,
    val message: String,
    val expiresAt: String,
)

class CallViewModel(application: Application) : AndroidViewModel(application) {
    private val api = YeoboSayApi()
    private val invitationSocket = CallInvitationSocket()
    private val recorder = AudioRecorder(application.applicationContext)
    private val player = AudioPlayer(application.applicationContext)

    private val _uiState = MutableStateFlow(CallUiState())
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    private var recordingStartedAt: Long = 0L
    private var maxRecordingJob: Job? = null

    init {
        connectCallInvitationSocket()
    }

    fun startSession() {
        if (_uiState.value.isStartingSession) return

        viewModelScope.launch {
            createCallSession()
        }
    }

    fun requestTestCall() {
        val state = _uiState.value
        if (state.isRequestingTestCall || state.incomingCall != null || state.callSessionId != null) {
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRequestingTestCall = true,
                    errorText = null,
                    statusText = "테스트 전화를 요청하는 중입니다.",
                )
            }

            runCatching { api.createTestCallInvitation() }
                .onSuccess { invitation ->
                    _uiState.update {
                        it.copy(
                            incomingCall = invitation.toIncomingCallUiState(),
                            isRequestingTestCall = false,
                            statusText = "전화 요청을 보냈습니다.",
                            errorText = null,
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isRequestingTestCall = false,
                            statusText = "전화 요청 실패",
                            errorText = error.message ?: "테스트 전화를 요청할 수 없습니다.",
                        )
                    }
                }
        }
    }

    fun acceptIncomingCall() {
        val incomingCall = _uiState.value.incomingCall ?: return
        if (_uiState.value.isAcceptingIncomingCall) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isAcceptingIncomingCall = true,
                    errorText = null,
                    statusText = "전화를 받는 중입니다.",
                )
            }

            runCatching { api.acceptCallInvitation(incomingCall.callInvitationId) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            incomingCall = null,
                            isAcceptingIncomingCall = false,
                            statusText = "통화를 연결합니다.",
                        )
                    }
                    createCallSession()
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isAcceptingIncomingCall = false,
                            statusText = "전화 수락 실패",
                            errorText = error.message ?: "전화를 받을 수 없습니다.",
                        )
                    }
                }
        }
    }

    fun declineIncomingCall() {
        val incomingCall = _uiState.value.incomingCall ?: return
        if (_uiState.value.isDecliningIncomingCall) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isDecliningIncomingCall = true,
                    errorText = null,
                    statusText = "전화를 거절하는 중입니다.",
                )
            }

            runCatching { api.declineCallInvitation(incomingCall.callInvitationId) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            incomingCall = null,
                            isDecliningIncomingCall = false,
                            statusText = "전화를 거절했습니다.",
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isDecliningIncomingCall = false,
                            statusText = "전화 거절 실패",
                            errorText = error.message ?: "전화를 거절할 수 없습니다.",
                        )
                    }
                }
        }
    }

    private fun connectCallInvitationSocket() {
        invitationSocket.connect(
            onConnected = {
                _uiState.update { it.copy(socketStatusText = "전화 수신 대기 중입니다.") }
            },
            onDisconnected = {
                _uiState.update { it.copy(socketStatusText = "전화 수신 연결이 끊겼습니다.") }
            },
            onIncomingCall = { event ->
                _uiState.update {
                    if (it.callSessionId != null) {
                        it.copy(socketStatusText = "통화 중이라 새 전화를 표시하지 않았습니다.")
                    } else {
                        it.copy(
                            incomingCall = event.toIncomingCallUiState(),
                            socketStatusText = "전화가 도착했습니다.",
                            statusText = "전화가 왔어요.",
                            errorText = null,
                        )
                    }
                }
            },
            onError = { message ->
                _uiState.update { it.copy(socketStatusText = message) }
            },
        )
    }

    private suspend fun createCallSession() {
        _uiState.update {
            it.copy(
                isStartingSession = true,
                errorText = null,
                statusText = "세션을 만드는 중입니다.",
            )
        }

        runCatching { api.createCallSession() }
            .onSuccess { session ->
                _uiState.update {
                    it.copy(
                        callSessionId = session.id,
                        expiresAt = session.expiresAt,
                        isStartingSession = false,
                        statusText = "녹음 버튼을 눌러 대화를 시작하세요.",
                        messages = listOf(
                            CallMessage(
                                role = MessageRole.Assistant,
                                text = DEFAULT_GREETING,
                            ),
                        ),
                    )
                }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        isStartingSession = false,
                        statusText = "세션 생성 실패",
                        errorText = error.message ?: "세션을 만들 수 없습니다.",
                    )
                }
            }
    }

    fun toggleRecording() {
        if (_uiState.value.isRecording) {
            stopRecordingAndUpload()
        } else {
            startRecording()
        }
    }

    private fun startRecording() {
        val sessionId = _uiState.value.callSessionId
        if (sessionId == null) {
            _uiState.update { it.copy(errorText = "먼저 통화 세션을 시작해 주세요.") }
            return
        }
        if (_uiState.value.isUploading) return

        runCatching {
            recorder.start()
            recordingStartedAt = System.currentTimeMillis()
        }.onSuccess {
            _uiState.update {
                it.copy(
                    isRecording = true,
                    errorText = null,
                    statusText = "녹음 중입니다.",
                )
            }
            maxRecordingJob?.cancel()
            maxRecordingJob = viewModelScope.launch {
                delay(MAX_RECORDING_MILLIS)
                if (_uiState.value.isRecording) stopRecordingAndUpload()
            }
        }.onFailure { error ->
            _uiState.update {
                it.copy(errorText = error.message ?: "녹음을 시작할 수 없습니다.")
            }
        }
    }

    private fun stopRecordingAndUpload() {
        val sessionId = _uiState.value.callSessionId ?: return
        maxRecordingJob?.cancel()
        maxRecordingJob = null

        val duration = System.currentTimeMillis() - recordingStartedAt
        val file = recorder.stop()
        _uiState.update { it.copy(isRecording = false) }

        if (duration < MIN_RECORDING_MILLIS) {
            file?.delete()
            _uiState.update { it.copy(statusText = "너무 짧은 녹음은 무시했습니다.") }
            return
        }

        if (file == null || !file.exists() || file.length() == 0L) {
            file?.delete()
            _uiState.update { it.copy(errorText = "녹음 파일을 만들지 못했습니다.") }
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUploading = true,
                    errorText = null,
                    statusText = "AI 응답을 생성하는 중입니다.",
                )
            }

            runCatching { api.uploadAudioTurn(sessionId, file) }
                .onSuccess { response ->
                    file.delete()
                    val messages = buildList {
                        addAll(_uiState.value.messages)
                        add(
                            CallMessage(
                                role = MessageRole.User,
                                text = response.userText,
                                riskFlag = response.riskFlag,
                            ),
                        )
                        add(
                            CallMessage(
                                role = MessageRole.Assistant,
                                text = response.assistantText,
                                failed = response.failed,
                            ),
                        )
                    }
                    _uiState.update {
                        it.copy(
                            messages = messages,
                            isUploading = false,
                            statusText = if (response.failed) "AI 응답 생성에 실패했습니다." else "응답을 재생합니다.",
                        )
                    }
                    response.audioBase64?.let { audioBase64 ->
                        _uiState.update { it.copy(isPlaying = true) }
                        player.playBase64Mp3(audioBase64) {
                            _uiState.update {
                                it.copy(isPlaying = false, statusText = "녹음 버튼을 눌러 이어서 말하세요.")
                            }
                        }
                    } ?: _uiState.update {
                        it.copy(statusText = "오디오 없이 응답을 받았습니다.")
                    }
                }
                .onFailure { error ->
                    file.delete()
                    _uiState.update {
                        it.copy(
                            isUploading = false,
                            statusText = "업로드 실패",
                            errorText = error.message ?: "음성 업로드에 실패했습니다.",
                        )
                    }
                }
        }
    }

    fun stopPlayback() {
        player.stop()
        _uiState.update { it.copy(isPlaying = false, statusText = "재생을 중지했습니다.") }
    }

    override fun onCleared() {
        invitationSocket.disconnect()
        recorder.cancel()
        player.stop()
        super.onCleared()
    }
}

private fun IncomingCallEvent.toIncomingCallUiState(): IncomingCallUiState =
    IncomingCallUiState(
        callInvitationId = callInvitationId,
        callerName = callerName,
        message = message,
        expiresAt = expiresAt,
    )

private fun CallInvitationResponse.toIncomingCallUiState(): IncomingCallUiState =
    IncomingCallUiState(
        callInvitationId = id,
        callerName = callerName,
        message = message,
        expiresAt = expiresAt,
    )
