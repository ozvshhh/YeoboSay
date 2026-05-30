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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.yeobosay.app.ui.theme.YeoboSayFontFamily
import com.yeobosay.app.ui.theme.YeoboSayTheme
import java.util.Locale

private val OneUiBackground = Color(0xFFF7F7F9)
private val OneUiSurface = Color(0xFFFFFFFF)
private val OneUiInk = Color(0xFF0B0D12)
private val OneUiMuted = Color(0xFF70737B)
private val OneUiLine = Color(0x0A000000)
private val OneUiSoftLine = Color(0x08000000)
private val OneUiPanel = Color(0xFFFDFDFE)
private val CallGreenLight = Color(0xFF29D66F)
private val CallGreenDeep = Color(0xFF11A34C)
private val CallRedLight = Color(0xFFFF675E)
private val CallRedDeep = Color(0xFFEE2F28)
private val CallBlue = Color(0xFF2D7FF9)

private val WhitePhoneBackground = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFFFFFFF),
        Color(0xFFFBFBFC),
        OneUiBackground,
    ),
)

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
    CompositionLocalProvider(
        LocalTextStyle provides LocalTextStyle.current.copy(
            fontFamily = YeoboSayFontFamily,
            letterSpacing = 0.sp,
        ),
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
    Surface(
        modifier = modifier.fillMaxSize(),
        color = OneUiBackground,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    text = "YeoboSay",
                    color = OneUiInk,
                    fontSize = 44.sp,
                    lineHeight = 50.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = state.statusText,
                    color = OneUiMuted,
                    fontSize = 23.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = state.socketStatusText,
                    color = OneUiMuted.copy(alpha = 0.74f),
                    fontSize = 17.sp,
                    lineHeight = 23.sp,
                    fontWeight = FontWeight.Medium,
                )
            }

            OutlinedButton(
                onClick = onRequestTestCall,
                enabled = !state.isRequestingTestCall,
                shape = RoundedCornerShape(25.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                Text(
                    text = if (state.isRequestingTestCall) "전화 요청 중" else "테스트 전화 요청",
                    color = OneUiInk,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            AcceptButtonSizeSetting(
                selected = state.acceptButtonSize,
                onSelected = onAcceptButtonSizeChange,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OneUiActionButton(
                    text = if (state.isStartingSession) "시작 중" else "통화 시작",
                    enabled = !state.isStartingSession,
                    containerColor = Color(0xFF55679D),
                    contentColor = Color.White,
                    onClick = onStartSession,
                    modifier = Modifier.weight(1f),
                )

                OneUiActionButton(
                    text = "녹음 시작",
                    enabled = false,
                    containerColor = Color(0xFFE3E3E8),
                    contentColor = OneUiMuted,
                    onClick = onToggleRecording,
                    modifier = Modifier.weight(1f),
                )
            }

            if (state.isPlaying) {
                OutlinedButton(
                    onClick = onStopPlayback,
                    shape = RoundedCornerShape(25.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Text("응답 재생 중지", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                }
            }

            ErrorCard(errorText = state.errorText)

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 12.dp),
            ) {
                items(state.messages) { message ->
                    MessageBubble(message = message)
                }
            }
        }
    }
}

@Composable
private fun OneUiActionButton(
    text: String,
    enabled: Boolean,
    containerColor: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(25.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor,
            disabledContentColor = contentColor.copy(alpha = 0.56f),
        ),
        modifier = modifier.height(54.dp),
    ) {
        Text(text = text, fontSize = 21.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun AcceptButtonSizeSetting(
    selected: AcceptButtonSize,
    onSelected: (AcceptButtonSize) -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, RoundedCornerShape(24.dp), ambientColor = Color(0x12000000)),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = OneUiSurface),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "설정",
                color = OneUiInk,
                fontSize = 27.sp,
                lineHeight = 34.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "전화받기 버튼 크기",
                color = OneUiMuted,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
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
    val backgroundColor = if (selected) Color(0xFFEFF4FF) else Color(0xFFF5F5F7)
    val borderColor = if (selected) CallBlue.copy(alpha = 0.26f) else OneUiLine
    val textColor = if (selected) Color(0xFF1F6FE8) else OneUiMuted

    Box(
        modifier = modifier
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(23.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = textColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
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
        AcceptButtonSize.Normal -> 84.dp
        AcceptButtonSize.Large -> 112.dp
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(WhitePhoneBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 30.dp, vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Call,
                    contentDescription = null,
                    tint = OneUiInk.copy(alpha = 0.78f),
                    modifier = Modifier.size(18.dp),
                )
                Text(
                    text = "수신 중",
                    color = OneUiInk.copy(alpha = 0.76f),
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            Spacer(modifier = Modifier.weight(0.72f))

            OneUiAvatar(size = 112.dp, textSize = 42.sp)
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                text = incomingCall.callerName,
                color = OneUiInk,
                fontSize = 56.sp,
                lineHeight = 62.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = incomingCall.message.ifBlank { "AI 안부 전화" },
                color = OneUiInk.copy(alpha = 0.55f),
                fontSize = 26.sp,
                lineHeight = 33.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(18.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.70f))
                    .border(1.dp, OneUiLine, RoundedCornerShape(999.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "어르신 안부 확인 전화",
                    color = OneUiInk.copy(alpha = 0.50f),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            ErrorCard(
                errorText = state.errorText,
                modifier = Modifier.padding(top = 22.dp),
            )

            Spacer(modifier = Modifier.weight(1f))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                CircleCallButton(
                    icon = Icons.Filled.Call,
                    label = if (state.isAcceptingIncomingCall) "받는 중" else "받기",
                    gradient = Brush.verticalGradient(listOf(CallGreenLight, CallGreenDeep)),
                    size = acceptButtonSize,
                    iconSize = if (state.acceptButtonSize == AcceptButtonSize.Large) 44.dp else 34.dp,
                    iconRotation = -8f,
                    labelSize = 23.sp,
                    enabled = !state.isAcceptingIncomingCall && !state.isDecliningIncomingCall,
                    onClick = onAccept,
                )
                CircleCallButton(
                    icon = Icons.Filled.CallEnd,
                    label = if (state.isDecliningIncomingCall) "거절 중" else "거절",
                    gradient = Brush.verticalGradient(listOf(CallRedLight, CallRedDeep)),
                    size = 84.dp,
                    iconSize = 36.dp,
                    labelSize = 23.sp,
                    enabled = !state.isAcceptingIncomingCall && !state.isDecliningIncomingCall,
                    onClick = onDecline,
                )
            }
            Spacer(modifier = Modifier.height(30.dp))
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
            .background(WhitePhoneBackground)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = if (state.callElapsedSeconds == 0L) "통화 중..." else formatElapsed(state.callElapsedSeconds),
                color = OneUiInk.copy(alpha = 0.48f),
                fontSize = 27.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(15.dp))
            OneUiAvatar(size = 86.dp, textSize = 34.sp)
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "여보세요",
                color = OneUiInk,
                fontSize = 46.sp,
                lineHeight = 53.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = "AI 안부 전화",
                color = OneUiInk.copy(alpha = 0.56f),
                fontSize = 23.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.SemiBold,
            )

            ErrorCard(
                errorText = state.errorText,
                modifier = Modifier.padding(top = 14.dp),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 18.dp)
                    .shadow(
                        elevation = 3.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = Color(0x08000000),
                        spotColor = Color(0x0A000000),
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .background(OneUiSurface)
                    .border(1.dp, OneUiSoftLine, RoundedCornerShape(28.dp))
                    .padding(16.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (state.messages.isEmpty()) {
                    Text(
                        text = "통화가 시작되면 안부 대화가 여기에 기록됩니다.",
                        color = OneUiInk.copy(alpha = 0.42f),
                        fontSize = 22.sp,
                        lineHeight = 31.sp,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 2.dp),
                    ) {
                        items(state.messages) { message ->
                            ActiveMessageBubble(message = message)
                        }
                    }
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
private fun OneUiAvatar(
    size: Dp,
    textSize: TextUnit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(
                Brush.linearGradient(
                    colors = listOf(Color(0xFFCFDAE9), Color(0xFFAEBED1)),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.90f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "여",
            color = Color.White,
            fontSize = textSize,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun CircleCallButton(
    icon: ImageVector,
    label: String,
    gradient: Brush,
    size: Dp,
    iconSize: Dp,
    iconRotation: Float = 0f,
    labelSize: TextUnit,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val disabledGradient = Brush.verticalGradient(
        colors = listOf(Color(0xFFBFC2C8), Color(0xFFA9ADB5)),
    )

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(size)
                .shadow(16.dp, CircleShape, ambientColor = Color(0x22000000))
                .clip(CircleShape)
                .background(if (enabled) gradient else disabledGradient)
                .border(1.dp, Color.White.copy(alpha = 0.75f), CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = if (enabled) 1f else 0.72f),
                modifier = Modifier
                    .size(iconSize)
                    .rotate(iconRotation),
            )
        }
        Text(
            text = label,
            color = OneUiInk.copy(alpha = if (enabled) 0.82f else 0.46f),
            fontSize = labelSize,
            fontWeight = FontWeight.ExtraBold,
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
            .padding(top = 16.dp)
            .shadow(
                elevation = 4.dp,
                shape = RoundedCornerShape(31.dp),
                ambientColor = Color(0x08000000),
                spotColor = Color(0x0A000000),
            )
            .clip(RoundedCornerShape(31.dp))
            .background(OneUiPanel)
            .border(1.dp, OneUiSoftLine, RoundedCornerShape(31.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (isPlaying) {
            OutlinedButton(
                onClick = onStopPlayback,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            ) {
                Text("응답 재생 중지", fontSize = 19.sp, fontWeight = FontWeight.Bold)
            }
        }

        Text(
            text = when {
                isRecording -> "말씀을 듣고 있어요."
                isUploading -> "답변을 준비하고 있어요."
                else -> "녹음 버튼을 눌러 말씀해 주세요."
            },
            color = OneUiInk.copy(alpha = 0.58f),
            fontSize = 20.sp,
            lineHeight = 27.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            ControlButton(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                label = if (isSpeakerOn) "스피커 켜짐" else "스피커",
                selected = isSpeakerOn,
                onClick = onToggleSpeaker,
            )
            ControlButton(
                icon = Icons.Filled.CallEnd,
                label = if (isEndingSession) "종료 중" else "통화 종료",
                circleSize = 76.dp,
                circleBrush = Brush.verticalGradient(listOf(CallRedLight, CallRedDeep)),
                contentColor = Color.White,
                enabled = !isEndingSession,
                onClick = onEndSession,
            )
            ControlButton(
                icon = if (isRecording) Icons.Filled.Stop else Icons.Filled.Mic,
                label = when {
                    isRecording -> "녹음 종료"
                    isUploading -> "업로드 중"
                    else -> "녹음"
                },
                selected = false,
                circleBrush = if (isRecording) Brush.verticalGradient(listOf(CallRedLight, CallRedDeep)) else null,
                contentColor = if (isRecording) Color.White else OneUiInk.copy(alpha = 0.80f),
                enabled = !isUploading && !isEndingSession,
                onClick = onToggleRecording,
            )
        }
    }
}

@Composable
private fun ControlButton(
    icon: ImageVector,
    label: String,
    selected: Boolean = false,
    circleSize: Dp = 66.dp,
    circleBrush: Brush? = null,
    contentColor: Color = if (selected) Color(0xFF1F6FE8) else OneUiInk.copy(alpha = 0.80f),
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val defaultBackground = if (selected) Color(0xFFEEF4FF) else OneUiSurface
    val defaultBorder = if (selected) CallBlue.copy(alpha = 0.22f) else OneUiLine

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            modifier = Modifier
                .size(circleSize)
                .shadow(8.dp, CircleShape, ambientColor = Color(0x12000000))
                .clip(CircleShape)
                .then(
                    if (circleBrush != null) {
                        Modifier.background(circleBrush)
                    } else {
                        Modifier.background(defaultBackground)
                    },
                )
                .border(1.dp, if (circleBrush != null) Color.White.copy(alpha = 0.70f) else defaultBorder, CircleShape)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) contentColor else contentColor.copy(alpha = 0.40f),
                modifier = Modifier.size(if (circleSize > 70.dp) 32.dp else 26.dp),
            )
        }
        Text(
            text = label,
            color = OneUiInk.copy(alpha = if (enabled) 0.76f else 0.38f),
            fontSize = 18.sp,
            lineHeight = 23.sp,
            fontWeight = FontWeight.ExtraBold,
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
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFDAD6)),
            modifier = modifier.fillMaxWidth(),
        ) {
            Text(
                text = it,
                modifier = Modifier.padding(15.dp),
                color = Color(0xFF6E1915),
                fontSize = 19.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.SemiBold,
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
        message.failed -> Color(0xFFFFDAD6)
        isUser -> Color(0xFFE7F6EC)
        else -> OneUiSurface
    }
    val contentColor = when {
        message.failed -> Color(0xFF6E1915)
        isUser -> Color(0xFF0E522A)
        else -> OneUiInk
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = containerColor),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = if (message.riskFlag) "$label · 위험 감지" else label,
                    color = contentColor.copy(alpha = 0.70f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = message.text,
                    color = contentColor,
                    fontSize = 22.sp,
                    lineHeight = 32.sp,
                    fontWeight = FontWeight.Medium,
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
    val bubbleBrush = when {
        message.failed -> Brush.verticalGradient(listOf(Color(0xFFFFE4E1), Color(0xFFFFDAD6)))
        isUser -> Brush.verticalGradient(listOf(Color(0xFF9DEDB3), Color(0xFF79DF99)))
        else -> Brush.verticalGradient(listOf(Color(0xFFF7F7F8), Color(0xFFF0F0F2)))
    }
    val textColor = when {
        message.failed -> Color(0xFF6E1915)
        isUser -> Color(0xFF06120B)
        else -> OneUiInk
    }
    val bubbleShape = if (isUser) {
        RoundedCornerShape(21.dp, 21.dp, 6.dp, 21.dp)
    } else {
        RoundedCornerShape(21.dp, 21.dp, 21.dp, 6.dp)
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.83f)
                .align(if (isUser) Alignment.CenterEnd else Alignment.CenterStart)
                .clip(bubbleShape)
                .background(bubbleBrush)
                .border(1.dp, Color(0x0C000000), bubbleShape)
                .padding(horizontal = 15.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = if (message.riskFlag) "$label · 확인 필요" else label,
                color = textColor.copy(alpha = 0.66f),
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Text(
                text = message.text,
                color = textColor,
                fontSize = 22.sp,
                lineHeight = 32.sp,
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
