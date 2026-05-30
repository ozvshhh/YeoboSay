package com.yeobosay.app.ui.call

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yeobosay.app.ui.theme.YeoboSayTheme

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

    CallScreen(
        state = state,
        onStartSession = viewModel::startSession,
        onRequestTestCall = viewModel::requestTestCall,
        onAcceptIncomingCall = viewModel::acceptIncomingCall,
        onDeclineIncomingCall = viewModel::declineIncomingCall,
        onToggleRecording = {
            val isGranted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.RECORD_AUDIO,
            ) == PackageManager.PERMISSION_GRANTED

            if (isGranted) {
                viewModel.toggleRecording()
            } else {
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        onStopPlayback = viewModel::stopPlayback,
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
                state.callSessionId?.let {
                    Text(
                        text = "session: $it",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }

            OutlinedButton(
                onClick = onRequestTestCall,
                enabled = !state.isRequestingTestCall &&
                    state.incomingCall == null &&
                    state.callSessionId == null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isRequestingTestCall) "전화 요청 중" else "테스트 전화 요청")
            }

            state.incomingCall?.let { incomingCall ->
                IncomingCallCard(
                    incomingCall = incomingCall,
                    isAccepting = state.isAcceptingIncomingCall,
                    isDeclining = state.isDecliningIncomingCall,
                    onAccept = onAcceptIncomingCall,
                    onDecline = onDeclineIncomingCall,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Button(
                    onClick = onStartSession,
                    enabled = !state.isStartingSession &&
                        state.incomingCall == null &&
                        state.callSessionId == null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (state.isStartingSession) "시작 중" else "통화 시작")
                }

                Button(
                    onClick = onToggleRecording,
                    enabled = state.callSessionId != null && !state.isUploading,
                    colors = if (state.isRecording) {
                        ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    } else {
                        ButtonDefaults.buttonColors()
                    },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (state.isRecording) "녹음 종료" else "녹음 시작")
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

            state.errorText?.let {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = it,
                        modifier = Modifier.padding(14.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

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
private fun IncomingCallCard(
    incomingCall: IncomingCallUiState,
    isAccepting: Boolean,
    isDeclining: Boolean,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "전화가 왔어요",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = incomingCall.callerName,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = incomingCall.message,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onDecline,
                    enabled = !isAccepting && !isDeclining,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (isDeclining) "거절 중" else "거절")
                }
                Button(
                    onClick = onAccept,
                    enabled = !isAccepting && !isDeclining,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(if (isAccepting) "받는 중" else "받기")
                }
            }
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

@Preview(showBackground = true)
@Composable
private fun CallScreenPreview() {
    YeoboSayTheme {
        CallScreen(
            state = CallUiState(
                callSessionId = "sample",
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
        )
    }
}
