package com.yeobosay.app.ui.call

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yeobosay.app.ui.theme.YeoboSayTheme
import java.util.Locale

private val IncomingGradient = Brush.linearGradient(
    colors = listOf(
        Color(0xFFF0E6FF),
        Color(0xFFFCE4F0),
        Color(0xFFE8D8F8),
        Color(0xFFFFD6E8),
        Color(0xFFE4D4F8),
    ),
)

private val AcceptGreen = Color(0xFF2E9E4F)
private val DeclineRed = Color(0xFFD93025)
private val ElderText = Color(0xFF222222)

@Composable
fun CallRoute(
    viewModel: CallViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.toggleRecording()
    }

    val toggleRecordingWithPermission = {
        val isGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (isGranted) {
            viewModel.toggleRecording()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    CallScreen(
        state = state,
        onStartSession = viewModel::startSession,
        onRequestTestCall = viewModel::requestTestCall,
        onAcceptIncomingCall = viewModel::acceptIncomingCall,
        onDeclineIncomingCall = viewModel::declineIncomingCall,
        onToggleRecording = toggleRecordingWithPermission,
        onStopPlayback = viewModel::stopPlayback,
        onEndSession = viewModel::endSession,
        onAcceptButtonSizeChange = viewModel::setAcceptButtonSize,
        modifier = modifier,
    )
}

@Composable
fun CallScreen(
    state: CallUiState,
    onStartSession: () -> Unit,
    onRequestTestCall: () -> Unit,
    onAcceptIncomingCall: () -> Unit,
    onDeclineIncomingCall: () -> Unit,
    onToggleRecording: () -> Unit,
    onStopPlayback: () -> Unit,
    onEndSession: () -> Unit,
    onAcceptButtonSizeChange: (AcceptButtonSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        state.incomingCall != null -> IncomingCallScreen(
            state = state,
            incomingCall = state.incomingCall,
            onAccept = onAcceptIncomingCall,
            onDecline = onDeclineIncomingCall,
            modifier = modifier,
        )

        state.callSessionId != null -> ActiveCallScreen(
            state = state,
            onToggleRecording = onToggleRecording,
            onStopPlayback = onStopPlayback,
            onEndSession = onEndSession,
            modifier = modifier,
        )

        else -> HomeCallScreen(
            state = state,
            onStartSession = onStartSession,
            onRequestTestCall = onRequestTestCall,
            onToggleRecording = onToggleRecording,
            onStopPlayback = onStopPlayback,
            onAcceptButtonSizeChange = onAcceptButtonSizeChange,
            modifier = modifier,
        )
    }
}

@Composable
private fun HomeCallScreen(
    state: CallUiState,
    onStartSession: () -> Unit,
    onRequestTestCall: () -> Unit,
    onToggleRecording: () -> Unit,
    onStopPlayback: () -> Unit,
    onAcceptButtonSizeChange: (AcceptButtonSize) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "YeoboSay",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = state.statusText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = state.socketStatusText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline,
                )
            }

            OutlinedButton(
                onClick = onRequestTestCall,
                enabled = !state.isRequestingTestCall,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isRequestingTestCall) "전화 요청 중" else "테스트 전화 요청")
            }

            AcceptButtonSizeSetting(
                selected = state.acceptButtonSize,
                onSelected = onAcceptButtonSizeChange,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onStartSession,
                    enabled = !state.isStartingSession,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (state.isStartingSession) "시작 중" else "통화 시작")
                }

                Button(
                    onClick = onToggleRecording,
                    enabled = false,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("녹음 시작")
                }
            }

            if (state.isPlaying) {
                OutlinedButton(
                    onClick = onStopPlayback,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("응답 재생 중지")
                }
            }

            ErrorCard(errorText = state.errorText)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.messages) { message ->
                    MessageBubble(message = message)
                }
            }
        }
    }
}

@Composable
private fun AcceptButtonSizeSetting(
    selected: AcceptButtonSize,
    onSelected: (AcceptButtonSize) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "설정",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "전화받기 버튼 크기",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AcceptSizeButton(
                    text = "기본",
                    selected = selected == AcceptButtonSize.Normal,
                    onClick = { onSelected(AcceptButtonSize.Normal) },
                    modifier = Modifier.weight(1f),
                )
                AcceptSizeButton(
                    text = "크게",
                    selected = selected == AcceptButtonSize.Large,
                    onClick = { onSelected(AcceptButtonSize.Large) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun AcceptSizeButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) {
            Text(text)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(text)
        }
    }
}

@Composable
private fun IncomingCallScreen(
    state: CallUiState,
    incomingCall: IncomingCallUiState,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val acceptButtonSize = when (state.acceptButtonSize) {
        AcceptButtonSize.Normal -> 76.dp
        AcceptButtonSize.Large -> 114.dp
    }
    val acceptIconSize = when (state.acceptButtonSize) {
        AcceptButtonSize.Normal -> 36.sp
        AcceptButtonSize.Large -> 48.sp
    }
    val acceptLabelSize = when (state.acceptButtonSize) {
        AcceptButtonSize.Normal -> 26.sp
        AcceptButtonSize.Large -> 30.sp
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(IncomingGradient)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(modifier = Modifier.weight(0.7f))
            IncomingAvatar(size = 110.dp)
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = "전화가 왔어요",
                color = ElderText.copy(alpha = 0.72f),
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = incomingCall.callerName,
                color = ElderText,
                fontSize = 42.sp,
                lineHeight = 46.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = incomingCall.message.ifBlank { "AI 안부 전화" },
                color = ElderText.copy(alpha = 0.66f),
                fontSize = 22.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
            ErrorCard(
                errorText = state.errorText,
                modifier = Modifier.padding(top = 22.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                CircleCallButton(
                    symbol = "☎",
                    label = if (state.isAcceptingIncomingCall) "받는 중" else "전화 받기",
                    color = AcceptGreen,
                    size = acceptButtonSize,
                    iconSize = acceptIconSize,
                    labelSize = acceptLabelSize,
                    enabled = !state.isAcceptingIncomingCall && !state.isDecliningIncomingCall,
                    onClick = onAccept,
                    modifier = Modifier.align(Alignment.BottomStart),
                )
                CircleCallButton(
                    symbol = "✕",
                    label = if (state.isDecliningIncomingCall) "거절 중" else "거절",
                    color = DeclineRed,
                    size = 76.dp,
                    iconSize = 34.sp,
                    labelSize = 26.sp,
                    enabled = !state.isAcceptingIncomingCall && !state.isDecliningIncomingCall,
                    onClick = onDecline,
                    modifier = Modifier.align(Alignment.BottomEnd),
                )
            }
            Spacer(modifier = Modifier.height(26.dp))
        }
    }
}

@Composable
private fun ActiveCallScreen(
    state: CallUiState,
    onToggleRecording: () -> Unit,
    onStopPlayback: () -> Unit,
    onEndSession: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isSpeakerOn by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(IncomingGradient)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "통화 중... ${formatElapsed(state.callElapsedSeconds)}",
                color = ElderText.copy(alpha = 0.72f),
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(14.dp))
            IncomingAvatar(size = 80.dp, emojiSize = 36.sp)
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "여보세요",
                color = ElderText,
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "AI 안부 전화",
                color = ElderText.copy(alpha = 0.62f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Medium,
            )

            ErrorCard(
                errorText = state.errorText,
                modifier = Modifier.padding(top = 14.dp),
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 16.dp),
            ) {
                items(state.messages) { message ->
                    ActiveMessageBubble(message = message)
                }
            }

            ActiveCallControls(
                isSpeakerOn = isSpeakerOn,
                isRecording = state.isRecording,
                isUploading = state.isUploading,
                isPlaying = state.isPlaying,
                isEndingSession = state.isEndingSession,
                onToggleSpeaker = { isSpeakerOn = !isSpeakerOn },
                onToggleRecording = onToggleRecording,
                onStopPlayback = onStopPlayback,
                onEndSession = onEndSession,
            )
        }
    }
}

@Composable
private fun IncomingAvatar(
    size: Dp,
    emojiSize: androidx.compose.ui.unit.TextUnit = 48.sp,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.55f))
            .border(3.dp, Color(0xFFBBA7E8).copy(alpha = 0.65f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "🤖",
            fontSize = emojiSize,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun CircleCallButton(
    symbol: String,
    label: String,
    color: Color,
    size: Dp,
    iconSize: androidx.compose.ui.unit.TextUnit,
    labelSize: androidx.compose.ui.unit.TextUnit,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val effectiveColor = if (enabled) color else color.copy(alpha = 0.45f)
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = effectiveColor,
                contentColor = Color.White,
                disabledContainerColor = effectiveColor,
                disabledContentColor = Color.White.copy(alpha = 0.72f),
            ),
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(size),
        ) {
            Text(
                text = symbol,
                fontSize = iconSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        Text(
            text = label,
            color = ElderText,
            fontSize = labelSize,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun ActiveCallControls(
    isSpeakerOn: Boolean,
    isRecording: Boolean,
    isUploading: Boolean,
    isPlaying: Boolean,
    isEndingSession: Boolean,
    onToggleSpeaker: () -> Unit,
    onToggleRecording: () -> Unit,
    onStopPlayback: () -> Unit,
    onEndSession: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(Color.White.copy(alpha = 0.56f))
            .padding(horizontal = 18.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (isPlaying) {
            OutlinedButton(
                onClick = onStopPlayback,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("응답 재생 중지")
            }
        }

        Text(
            text = when {
                isRecording -> "말씀을 듣고 있어요."
                isUploading -> "답변을 준비하고 있어요."
                else -> "녹음 버튼을 눌러 말씀해 주세요."
            },
            color = ElderText.copy(alpha = 0.72f),
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.Top,
        ) {
            ControlButton(
                symbol = "🔊",
                label = if (isSpeakerOn) "스피커 켜짐" else "스피커",
                color = if (isSpeakerOn) Color(0xFF5B6BA8) else Color.White.copy(alpha = 0.88f),
                contentColor = if (isSpeakerOn) Color.White else ElderText,
                onClick = onToggleSpeaker,
            )
            ControlButton(
                symbol = "✕",
                label = if (isEndingSession) "종료 중" else "전화 끊기",
                color = DeclineRed,
                contentColor = Color.White,
                enabled = !isEndingSession,
                onClick = onEndSession,
            )
            ControlButton(
                symbol = if (isRecording) "■" else "●",
                label = when {
                    isRecording -> "녹음 종료"
                    isUploading -> "업로드 중"
                    else -> "녹음"
                },
                color = if (isRecording) DeclineRed else Color.White.copy(alpha = 0.88f),
                contentColor = if (isRecording) Color.White else Color(0xFF5B6BA8),
                enabled = !isUploading && !isEndingSession,
                onClick = onToggleRecording,
            )
        }
    }
}

@Composable
private fun ControlButton(
    symbol: String,
    label: String,
    color: Color,
    contentColor: Color,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(62.dp)
                .clip(CircleShape)
                .background(if (enabled) color else color.copy(alpha = 0.5f))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = symbol,
                color = if (enabled) contentColor else contentColor.copy(alpha = 0.5f),
                fontSize = 25.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        Text(
            text = label,
            color = ElderText.copy(alpha = if (enabled) 0.8f else 0.45f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ErrorCard(
    errorText: String?,
    modifier: Modifier = Modifier,
) {
    errorText?.let {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
            ),
            modifier = modifier.fillMaxWidth(),
        ) {
            Text(
                text = it,
                modifier = Modifier.padding(14.dp),
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
    }
}

@Composable
private fun MessageBubble(message: CallMessage) {
    val isUser = message.role == MessageRole.User
    val label = when (message.role) {
        MessageRole.User -> "사용자"
        MessageRole.Assistant -> "AI"
        MessageRole.System -> "시스템"
    }
    val containerColor = when {
        message.failed -> MaterialTheme.colorScheme.errorContainer
        isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when {
        message.failed -> MaterialTheme.colorScheme.onErrorContainer
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart),
            colors = CardDefaults.cardColors(containerColor = containerColor),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Text(
                    text = if (message.riskFlag) "$label · 위험 감지" else label,
                    style = MaterialTheme.typography.labelMedium,
                    color = contentColor,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = contentColor,
                )
            }
        }
    }
}

@Composable
private fun ActiveMessageBubble(message: CallMessage) {
    val isUser = message.role == MessageRole.User
    val label = when (message.role) {
        MessageRole.User -> "나"
        MessageRole.Assistant -> "AI"
        MessageRole.System -> "시스템"
    }
    val bubbleColor = when {
        message.failed -> MaterialTheme.colorScheme.errorContainer
        isUser -> Color(0xFFDDF3E4).copy(alpha = 0.86f)
        else -> Color.White.copy(alpha = 0.72f)
    }
    val textColor = when {
        message.failed -> MaterialTheme.colorScheme.onErrorContainer
        isUser -> Color(0xFF1D5E35)
        else -> ElderText
    }
    val bubbleShape = if (isUser) {
        RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp)
    } else {
        RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.86f)
                .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart)
                .clip(bubbleShape)
                .background(bubbleColor)
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = if (message.riskFlag) "$label · 확인 필요" else label,
                color = textColor.copy(alpha = 0.72f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = message.text,
                color = textColor,
                fontSize = 19.sp,
                lineHeight = 27.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun formatElapsed(seconds: Long): String {
    val minutesPart = seconds / 60
    val secondsPart = seconds % 60
    return String.format(Locale.getDefault(), "%02d:%02d", minutesPart, secondsPart)
}

@Preview(showBackground = true)
@Composable
private fun HomeCallScreenPreview() {
    YeoboSayTheme {
        CallScreen(
            state = CallUiState(
                statusText = "통화 세션을 시작해 주세요.",
                socketStatusText = "전화 수신 대기 중입니다.",
            ),
            onStartSession = {},
            onRequestTestCall = {},
            onAcceptIncomingCall = {},
            onDeclineIncomingCall = {},
            onToggleRecording = {},
            onStopPlayback = {},
            onEndSession = {},
            onAcceptButtonSizeChange = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun IncomingCallScreenPreview() {
    YeoboSayTheme {
        CallScreen(
            state = CallUiState(
                incomingCall = IncomingCallUiState(
                    callInvitationId = "sample",
                    callerName = "여보세요",
                    message = "AI 안부 전화",
                    expiresAt = "2026-05-30T10:00:00.000Z",
                ),
            ),
            onStartSession = {},
            onRequestTestCall = {},
            onAcceptIncomingCall = {},
            onDeclineIncomingCall = {},
            onToggleRecording = {},
            onStopPlayback = {},
            onEndSession = {},
            onAcceptButtonSizeChange = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun ActiveCallScreenPreview() {
    YeoboSayTheme {
        CallScreen(
            state = CallUiState(
                callSessionId = "sample",
                callElapsedSeconds = 72,
                statusText = "녹음 버튼을 눌러 이어서 말하세요.",
                socketStatusText = "전화 수신 대기 중입니다.",
                messages = listOf(
                    CallMessage(MessageRole.Assistant, "안녕하세요. 저는 YeoboSay 말벗이에요."),
                    CallMessage(MessageRole.User, "오늘은 기분이 괜찮아요."),
                    CallMessage(MessageRole.Assistant, "괜찮은 하루라니 다행이에요. 어떤 일이 있었나요?"),
                ),
            ),
            onStartSession = {},
            onRequestTestCall = {},
            onAcceptIncomingCall = {},
            onDeclineIncomingCall = {},
            onToggleRecording = {},
            onStopPlayback = {},
            onEndSession = {},
            onAcceptButtonSizeChange = {},
        )
    }
}
