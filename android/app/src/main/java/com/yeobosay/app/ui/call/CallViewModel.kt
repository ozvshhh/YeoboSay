package com.yeobosay.app.ui.call

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yeobosay.app.data.CallInvitationResponse
import com.yeobosay.app.data.CallInvitationSocket
import com.yeobosay.app.data.CallSessionMode
import com.yeobosay.app.data.IncomingCallEvent
import com.yeobosay.app.data.YeoboSayApi
import com.yeobosay.app.voice.AudioPlayer
import com.yeobosay.app.voice.AudioRecorder
import com.yeobosay.app.voice.SpeechDetectionListener
import com.yeobosay.app.voice.SpeechDetectionSnapshot
import com.yeobosay.app.voice.SpeechRmsDetector
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import java.time.Instant

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

enum class AcceptButtonSize {
    Normal,
    Large,
}

data class CallUiState(
    val callSessionId: String? = null,
    val expiresAt: String? = null,
    val messages: List<CallMessage> = emptyList(),
    val incomingCall: IncomingCallUiState? = null,
    val acceptButtonSize: AcceptButtonSize = AcceptButtonSize.Large,
    val callElapsedSeconds: Long = 0L,
    val isAutoConversation: Boolean = false,
    val isStartingSession: Boolean = false,
    val isRequestingTestCall: Boolean = false,
    val isAcceptingIncomingCall: Boolean = false,
    val isDecliningIncomingCall: Boolean = false,
    val isEndingSession: Boolean = false,
    val isRecording: Boolean = false,
    val isUploading: Boolean = false,
    val isPlaying: Boolean = false,
    val isListening: Boolean = false,
    val isUserSpeaking: Boolean = false,
    val speechDebugStatus: String = "IDLE",
    val speechRms: Double = 0.0,
    val speechThreshold: Double = 0.0,
    val speechNoiseFloor: Double = 0.0,
    val speechAudioSource: String = "-",
    val lastSpeechDurationMs: Long? = null,
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
    private val speechDetector = SpeechRmsDetector(application.applicationContext)

    private val _uiState = MutableStateFlow(CallUiState())
    val uiState: StateFlow<CallUiState> = _uiState.asStateFlow()

    private var recordingStartedAt: Long = 0L
    private var maxRecordingJob: Job? = null
    private var callTimerJob: Job? = null
    private var callStartedAtMillis: Long = 0L
    private var autoTurnSequence: Int = 0

    init {
        connectCallInvitationSocket()
    }

    fun setAcceptButtonSize(size: AcceptButtonSize) {
        _uiState.update { it.copy(acceptButtonSize = size) }
    }

    fun startSession() {
        if (_uiState.value.isStartingSession) return

        viewModelScope.launch {
            createCallSession()
        }
    }

    fun onAudioPermissionDenied() {
        _uiState.update {
            it.copy(
                statusText = "마이크 권한이 필요합니다.",
                errorText = "통화를 위해 마이크 권한을 허용해 주세요.",
                speechDebugStatus = "PERMISSION_DENIED",
            )
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
                            statusText = "통화를 연결합니다.",
                        )
                    }
                    createCallSession(
                        mode = CallSessionMode.AutoConversation,
                        source = "incoming_call",
                        callInvitationId = incomingCall.callInvitationId,
                    )
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

    fun endSession() {
        val sessionId = _uiState.value.callSessionId ?: return
        if (_uiState.value.isEndingSession) return

        viewModelScope.launch {
            maxRecordingJob?.cancel()
            maxRecordingJob = null
            if (_uiState.value.isRecording) recorder.cancel()
            stopAutoSpeechDetection()
            player.stop()

            _uiState.update {
                it.copy(
                    isEndingSession = true,
                    isRecording = false,
                    isPlaying = false,
                    errorText = null,
                    statusText = "통화를 종료하는 중입니다.",
                )
            }

            runCatching { api.endCallSession(sessionId) }
                .onSuccess {
                    stopCallTimer()
                    autoTurnSequence = 0
                    _uiState.update {
                        it.copy(
                            callSessionId = null,
                            expiresAt = null,
                            messages = emptyList(),
                            callElapsedSeconds = 0L,
                            isAutoConversation = false,
                            isEndingSession = false,
                            isListening = false,
                            isUserSpeaking = false,
                            speechDebugStatus = "IDLE",
                            statusText = "통화가 종료되었습니다.",
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update {
                        it.copy(
                            isEndingSession = false,
                            statusText = "통화 종료 실패",
                            errorText = error.message ?: "통화를 종료할 수 없습니다.",
                        )
                    }
                }
        }
    }

    private suspend fun createCallSession(
        mode: CallSessionMode = CallSessionMode.ManualRecording,
        source: String? = null,
        callInvitationId: String? = null,
    ) {
        _uiState.update {
            it.copy(
                isStartingSession = true,
                errorText = null,
                isListening = false,
                isUserSpeaking = false,
                speechDebugStatus = "STARTING_SESSION",
                lastSpeechDurationMs = null,
                statusText = "세션을 만드는 중입니다.",
            )
        }

        runCatching {
            api.createCallSession(
                mode = mode,
                source = source,
                callInvitationId = callInvitationId,
            )
        }
            .onSuccess { session ->
                val isAutoConversation = session.mode == CallSessionMode.AutoConversation.apiValue
                val conversationPolicy = session.conversationPolicy
                val greetingText = conversationPolicy
                    ?.firstGreetingText
                    ?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_GREETING

                startCallTimer()
                autoTurnSequence = 0
                _uiState.update {
                    it.copy(
                        callSessionId = session.id,
                        expiresAt = session.expiresAt,
                        isAutoConversation = isAutoConversation,
                        isStartingSession = false,
                        isAcceptingIncomingCall = false,
                        statusText = if (isAutoConversation) {
                            "AI가 먼저 인사하고 있어요."
                        } else {
                            "녹음 버튼을 눌러 대화를 시작하세요."
                        },
                        isPlaying = isAutoConversation,
                        isListening = false,
                        isUserSpeaking = false,
                        speechDebugStatus = if (isAutoConversation) "AI_GREETING" else "MANUAL_READY",
                        messages = listOf(
                            CallMessage(
                                role = MessageRole.Assistant,
                                text = greetingText,
                            ),
                        ),
                    )
                }

                if (isAutoConversation) {
                    playFirstGreeting(
                        audioBase64 = conversationPolicy?.firstGreetingAudioBase64,
                    )
                }
            }
            .onFailure { error ->
                _uiState.update {
                    it.copy(
                        isStartingSession = false,
                        isAcceptingIncomingCall = false,
                        statusText = "세션 생성 실패",
                        errorText = error.message ?: "세션을 만들 수 없습니다.",
                    )
                }
            }
    }

    private fun startCallTimer() {
        callStartedAtMillis = System.currentTimeMillis()
        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            while (true) {
                val elapsedSeconds = (System.currentTimeMillis() - callStartedAtMillis) / 1_000L
                _uiState.update { it.copy(callElapsedSeconds = elapsedSeconds) }
                delay(1_000L)
            }
        }
    }

    private fun stopCallTimer() {
        callTimerJob?.cancel()
        callTimerJob = null
        callStartedAtMillis = 0L
    }

    private fun playFirstGreeting(audioBase64: String?) {
        val serverAudioBase64 = audioBase64?.takeIf { it.isNotBlank() }
        if (serverAudioBase64 == null) {
            _uiState.update {
                it.copy(
                    isPlaying = false,
                    isListening = false,
                    isUserSpeaking = false,
                    speechDebugStatus = "GREETING_AUDIO_MISSING",
                    statusText = "첫 인사 음성을 받을 수 없습니다.",
                    errorText = "서버 첫 인사 음성이 없어 재생하지 않았습니다.",
                )
            }
            return
        }

        val onComplete = {
            _uiState.update {
                it.copy(
                    isPlaying = false,
                    statusText = "말씀을 듣고 있어요.",
                )
            }
            startAutoSpeechDetection()
        }

        player.playBase64Mp3(serverAudioBase64, onComplete)
    }

    private fun startAutoSpeechDetection() {
        val state = _uiState.value
        if (
            !state.isAutoConversation ||
            state.callSessionId == null ||
            state.isUploading ||
            speechDetector.isRunning
        ) {
            return
        }

        _uiState.update {
            it.copy(
                isListening = true,
                isUserSpeaking = false,
                speechDebugStatus = "LISTENING",
                statusText = "말씀을 듣고 있어요.",
                errorText = null,
            )
        }

        speechDetector.start(
            object : SpeechDetectionListener {
                override fun onListening(snapshot: SpeechDetectionSnapshot) {
                    updateSpeechDetection(
                        status = if (snapshot.isSpeaking) "USER_SPEAKING" else "LISTENING",
                        snapshot = snapshot,
                    )
                }

                override fun onSpeechStarted(snapshot: SpeechDetectionSnapshot) {
                    updateSpeechDetection(
                        status = "USER_SPEAKING",
                        snapshot = snapshot,
                        statusText = "말씀하고 계신 것을 감지했어요.",
                    )
                }

                override fun onSpeechEnded(durationMs: Long, snapshot: SpeechDetectionSnapshot) {
                    updateSpeechDetection(
                        status = "SPEECH_ENDED",
                        snapshot = snapshot,
                        durationMs = durationMs,
                        statusText = "말씀을 마친 것으로 감지했어요.",
                    )
                }

                override fun onSpeechEndedWithAudio(
                    durationMs: Long,
                    snapshot: SpeechDetectionSnapshot,
                    audioFile: File?,
                ) {
                    updateSpeechDetection(
                        status = "SPEECH_ENDED",
                        snapshot = snapshot,
                        durationMs = durationMs,
                        statusText = "말씀을 마친 것으로 감지했어요.",
                    )
                    stopAutoSpeechDetection()
                    uploadAutoSpeechTurn(audioFile, durationMs)
                }

                override fun onError(message: String) {
                    _uiState.update {
                        it.copy(
                            isListening = false,
                            isUserSpeaking = false,
                            speechDebugStatus = "ERROR",
                            statusText = "음성 감지 실패",
                            errorText = message,
                        )
                    }
                }
            },
        )
    }

    private fun stopAutoSpeechDetection() {
        speechDetector.stop()
        _uiState.update {
            it.copy(
                isListening = false,
                isUserSpeaking = false,
            )
        }
    }

    private fun uploadAutoSpeechTurn(audioFile: File?, durationMs: Long) {
        val sessionId = _uiState.value.callSessionId
        if (sessionId == null) {
            audioFile?.delete()
            return
        }

        if (audioFile == null || !audioFile.exists() || audioFile.length() == 0L) {
            audioFile?.delete()
            _uiState.update {
                it.copy(
                    speechDebugStatus = "AUTO_AUDIO_FILE_MISSING",
                    statusText = "음성 파일을 만들지 못했습니다.",
                    errorText = "발화 파일이 비어 있어 업로드하지 않았습니다.",
                )
            }
            startAutoSpeechDetection()
            return
        }

        val clientTurnId = nextAutoClientTurnId(sessionId)
        val endedAt = Instant.now()
        val startedAt = endedAt.minusMillis(durationMs.coerceAtLeast(0L))

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isUploading = true,
                    isListening = false,
                    isUserSpeaking = false,
                    speechDebugStatus = "AUTO_UPLOADING",
                    statusText = "말씀을 서버로 보내는 중입니다.",
                    errorText = null,
                )
            }

            runCatching {
                api.uploadAutoAudioTurn(
                    callSessionId = sessionId,
                    clientTurnId = clientTurnId,
                    audioFile = audioFile,
                    startedAt = startedAt.toString(),
                    endedAt = endedAt.toString(),
                    durationMs = durationMs,
                    bargeIn = _uiState.value.isPlaying,
                )
            }.onSuccess { response ->
                audioFile.delete()
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
                        speechDebugStatus = "AUTO_UPLOAD_COMPLETED",
                        statusText = if (response.failed) {
                            "응답 처리에 실패했습니다."
                        } else {
                            "서버 응답을 받았습니다."
                        },
                        errorText = null,
                    )
                }

                if (_uiState.value.isAutoConversation && _uiState.value.callSessionId != null) {
                    startAutoSpeechDetection()
                }
            }.onFailure { error ->
                audioFile.delete()
                _uiState.update {
                    it.copy(
                        isUploading = false,
                        speechDebugStatus = "AUTO_UPLOAD_FAILED",
                        statusText = "자동 업로드 실패",
                        errorText = error.message ?: "자동 발화 업로드에 실패했습니다.",
                    )
                }
                if (_uiState.value.isAutoConversation && _uiState.value.callSessionId != null) {
                    startAutoSpeechDetection()
                }
            }
        }
    }

    private fun nextAutoClientTurnId(sessionId: String): String {
        autoTurnSequence += 1
        return "android-${sessionId}-${System.currentTimeMillis()}-$autoTurnSequence"
    }

    private fun updateSpeechDetection(
        status: String,
        snapshot: SpeechDetectionSnapshot,
        durationMs: Long? = null,
        statusText: String? = null,
    ) {
        _uiState.update {
            it.copy(
                isListening = status != "ERROR",
                isUserSpeaking = snapshot.isSpeaking,
                speechDebugStatus = status,
                speechRms = snapshot.rms,
                speechThreshold = snapshot.threshold,
                speechNoiseFloor = snapshot.noiseFloor,
                speechAudioSource = snapshot.audioSourceName,
                lastSpeechDurationMs = durationMs ?: it.lastSpeechDurationMs,
                statusText = statusText ?: it.statusText,
            )
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
        if (_uiState.value.isAutoConversation) {
            _uiState.update { it.copy(errorText = "자동 통화 중에는 녹음 버튼을 누르지 않아도 됩니다.") }
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
        val shouldListen = _uiState.value.isAutoConversation && _uiState.value.callSessionId != null
        _uiState.update { it.copy(isPlaying = false, statusText = "재생을 중지했습니다.") }
        if (shouldListen) startAutoSpeechDetection()
    }

    override fun onCleared() {
        invitationSocket.disconnect()
        callTimerJob?.cancel()
        maxRecordingJob?.cancel()
        speechDetector.release()
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
