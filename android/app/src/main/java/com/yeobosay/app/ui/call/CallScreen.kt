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
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Check
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

private val OneUiBackground = Color(0xFFF0F6FF)
private val OneUiSurface = Color.White.copy(alpha = 0.84f)
private val OneUiInk = Color(0xFF111827)
private val OneUiMuted = Color(0xFF647086)
private val OneUiLine = Color(0x120F1D3A)
private val OneUiSoftLine = Color(0x18FFFFFF)
private val OneUiPanel = Color.White.copy(alpha = 0.78f)
private val HydrangeaBlue = Color(0xFF6DAAF7)
private val HydrangeaLilac = Color(0xFFC8B8F4)
private val HydrangeaViolet = Color(0xFF9B88E8)
private val HydrangeaLeaf = Color(0xFF5E9C84)
private val CallGreenLight = Color(0xFF43D78B)
private val CallGreenDeep = Color(0xFF199C61)
private val CallRedLight = Color(0xFFFF746C)
private val CallRedDeep = Color(0xFFE33A37)
private val CallBlue = Color(0xFF5C8FEF)

private val WhitePhoneBackground = Brush.verticalGradient(
    colors = listOf(
        Color(0xFFFAFCFF),
        Color(0xFFEAF4FF),
        Color(0xFFD6E9FF),
        Color(0xFFDCCDF8),
        Color(0xFFEDE9FF),
    ),
)

@Composable
fun CallRoute(
    viewModel: CallViewModel = viewModel(),
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var pendingAudioPermissionAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val action = pendingAudioPermissionAction
        pendingAudioPermissionAction = null
        if (granted) {
            action?.invoke()
        } else {
            viewModel.onAudioPermissionDenied()
        }
    }

    fun runWithAudioPermission(action: () -> Unit) {
        val isGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO,
        ) == PackageManager.PERMISSION_GRANTED

        if (isGranted) {
            action()
        } else {
            pendingAudioPermissionAction = action
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    CallScreen(
        state = state,
        onStartSession = viewModel::startSession,
        onRequestTestCall = viewModel::requestTestCall,
        onAcceptIncomingCall = {
            viewModel.stopIncomingCallAlert()
            runWithAudioPermission(viewModel::acceptIncomingCall)
        },
        onDeclineIncomingCall = viewModel::declineIncomingCall,
        onToggleRecording = { runWithAudioPermission(viewModel::toggleRecording) },
        onStopPlayback = viewModel::stopPlayback,
        onEndSession = viewModel::endSession,
        onDismissEndSummary = viewModel::dismissEndSummary,
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
    onDismissEndSummary: () -> Unit,
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
            state.showEndSummary -> CallEndedSummaryScreen(
                state = state,
                onDismiss = onDismissEndSummary,
                modifier = modifier,
            )

            state.callSessionId != null || state.isAcceptingIncomingCall -> ActiveCallScreen(
                state = state,
                onToggleRecording = onToggleRecording,
                onStopPlayback = onStopPlayback,
                onEndSession = onEndSession,
                modifier = modifier,
            )

            state.incomingCall != null -> IncomingCallScreen(
                state = state,
                incomingCall = state.incomingCall,
                onAccept = onAcceptIncomingCall,
                onDecline = onDeclineIncomingCall,
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
private fun CallEndedSummaryScreen(
    state: CallUiState,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        color = OneUiBackground,
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(WhitePhoneBackground)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            contentPadding = PaddingValues(top = 30.dp, bottom = 26.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                EndCallHaloIcon()
            }

            item {
                Text(
                    text = "통화가\n종료되었습니다",
                    color = CallRedDeep,
                    fontSize = 48.sp,
                    lineHeight = 57.sp,
                    fontWeight = FontWeight.ExtraBold,
                    textAlign = TextAlign.Center,
                )
            }

            item {
                Text(
                    text = "AI 안부 전화가 안전하게 종료되었습니다.",
                    color = OneUiInk.copy(alpha = 0.58f),
                    fontSize = 22.sp,
                    lineHeight = 30.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
            }

            item {
                CallEndedMetaCard(
                    durationText = formatKoreanDuration(state.lastCallDurationSeconds),
                    endedAtText = state.lastCallEndedAtText.ifBlank { "-" },
                )
            }

            item {
                ImmediateTasksCard()
            }

            item {
                TodayStatusSummaryCard()
            }

            item {
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(28.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CallRedDeep,
                        contentColor = Color.White,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(58.dp),
                ) {
                    Text(
                        text = "나가기",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun EndCallHaloIcon() {
    Box(
        modifier = Modifier.size(138.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .size(138.dp)
                .clip(CircleShape)
                .background(CallRedLight.copy(alpha = 0.10f)),
        )
        Box(
            modifier = Modifier
                .size(112.dp)
                .clip(CircleShape)
                .background(CallRedLight.copy(alpha = 0.15f)),
        )
        Box(
            modifier = Modifier
                .size(86.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = CircleShape,
                    ambientColor = CallRedDeep.copy(alpha = 0.20f),
                    spotColor = CallRedDeep.copy(alpha = 0.24f),
                )
                .clip(CircleShape)
                .background(Brush.verticalGradient(listOf(CallRedLight, CallRedDeep))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.CallEnd,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(42.dp),
            )
        }
    }
}

@Composable
private fun CallEndedMetaCard(
    durationText: String,
    endedAtText: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(Color.White.copy(alpha = 0.70f))
            .border(1.dp, CallRedLight.copy(alpha = 0.18f), RoundedCornerShape(15.dp))
            .padding(horizontal = 18.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(CallRedDeep),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(21.dp),
            )
        }
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = "통화 시간 ",
            color = OneUiInk,
            fontSize = 18.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = durationText,
            color = CallRedDeep,
            fontSize = 18.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .height(24.dp)
                .width(1.dp)
                .background(OneUiLine),
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = "종료 시간 ",
            color = OneUiInk,
            fontSize = 18.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = endedAtText,
            color = CallRedDeep,
            fontSize = 18.sp,
            lineHeight = 25.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun ImmediateTasksCard() {
    SummaryCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "⚡  지금 바로 해야 할 일",
                color = Color(0xFFBE6400),
                fontSize = 23.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Spacer(modifier = Modifier.weight(1f))
            StatusPill(text = "2가지", color = Color(0xFFE28410), background = Color(0xFFFFF1D8))
        }

        Spacer(modifier = Modifier.height(16.dp))
        TaskRow(icon = "💊", title = "혈압약 복용 확인", badge = "지금", urgent = true)
        SoftDivider()
        TaskRow(icon = "🏥", title = "내일 정형외과 예약 확인", badge = "내일", urgent = false)
    }
}

@Composable
private fun TodayStatusSummaryCard() {
    SummaryCard {
        Text(
            text = "오늘 상태 요약",
            color = OneUiInk.copy(alpha = 0.52f),
            fontSize = 18.sp,
            lineHeight = 24.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(modifier = Modifier.height(14.dp))
        StatusSummaryRow(icon = "🍚", title = "식사", status = "완료", statusColor = Color(0xFF18A84F))
        SoftDivider()
        StatusSummaryRow(icon = "💊", title = "복약", status = "확인", statusColor = Color(0xFFD16F00))
        SoftDivider()
        StatusSummaryRow(icon = "😊", title = "기분", status = "양호", statusColor = Color(0xFF18A84F))
        SoftDivider()
        StatusSummaryRow(icon = "🏥", title = "병원 일정", status = "내일", statusColor = Color(0xFFD16F00))
    }
}

@Composable
private fun SummaryCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 14.dp,
                shape = RoundedCornerShape(25.dp),
                ambientColor = HydrangeaViolet.copy(alpha = 0.10f),
                spotColor = HydrangeaBlue.copy(alpha = 0.12f),
            )
            .clip(RoundedCornerShape(25.dp))
            .background(Color.White.copy(alpha = 0.82f))
            .border(1.dp, Color.White.copy(alpha = 0.64f), RoundedCornerShape(25.dp))
            .padding(horizontal = 24.dp, vertical = 22.dp),
        content = content,
    )
}

@Composable
private fun TaskRow(
    icon: String,
    title: String,
    badge: String,
    urgent: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiTile(icon = icon, background = if (urgent) Color(0xFFFFDDE4) else Color(0xFFFFEFCF))
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            color = OneUiInk,
            fontSize = 24.sp,
            lineHeight = 33.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.weight(1f),
        )
        StatusPill(
            text = badge,
            color = if (urgent) CallRedDeep else Color(0xFFD16F00),
            background = if (urgent) Color(0xFFFFE2E8) else Color(0xFFFFF0D9),
        )
    }
}

@Composable
private fun StatusSummaryRow(
    icon: String,
    title: String,
    status: String,
    statusColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiTile(icon = icon, background = Color(0xFFEAF5FF))
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = title,
            color = OneUiInk,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.weight(1f),
        )
        StatusPill(
            text = status,
            color = statusColor,
            background = if (statusColor == Color(0xFF18A84F)) Color(0xFFDFF6E7) else Color(0xFFFFF0D9),
        )
    }
}

@Composable
private fun EmojiTile(
    icon: String,
    background: Color,
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = icon, fontSize = 28.sp)
    }
}

@Composable
private fun StatusPill(
    text: String,
    color: Color,
    background: Color,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(13.dp))
            .background(background)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = color,
            fontSize = 21.sp,
            lineHeight = 27.sp,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun SoftDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(OneUiLine),
    )
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
                color = OneUiInk.copy(alpha = 0.54f),
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
                color = OneUiInk.copy(alpha = 0.60f),
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
                        elevation = 10.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = HydrangeaViolet.copy(alpha = 0.08f),
                        spotColor = HydrangeaBlue.copy(alpha = 0.12f),
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.70f))
                    .border(1.dp, Color.White.copy(alpha = 0.62f), RoundedCornerShape(28.dp))
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
                        reverseLayout = true,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                    ) {
                        items(state.messages.asReversed()) { message ->
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
                isAutoConversation = state.isAutoConversation,
                isEndingSession = state.isEndingSession,
                isListening = state.isListening,
                isUserSpeaking = state.isUserSpeaking,
                speechDebugStatus = state.speechDebugStatus,
                speechRms = state.speechRms,
                speechThreshold = state.speechThreshold,
                speechNoiseFloor = state.speechNoiseFloor,
                speechAudioSource = state.speechAudioSource,
                lastSpeechDurationMs = state.lastSpeechDurationMs,
                apiDebugStatus = state.apiDebugStatus,
                apiDebugDetail = state.apiDebugDetail,
                lastClientTurnId = state.lastClientTurnId,
                lastUploadSizeBytes = state.lastUploadSizeBytes,
                lastResponseHasAudio = state.lastResponseHasAudio,
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
                    colors = listOf(Color(0xFFE7F1FF), HydrangeaLilac, HydrangeaBlue),
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
    isAutoConversation: Boolean,
    isEndingSession: Boolean,
    isListening: Boolean,
    isUserSpeaking: Boolean,
    speechDebugStatus: String,
    speechRms: Double,
    speechThreshold: Double,
    speechNoiseFloor: Double,
    speechAudioSource: String,
    lastSpeechDurationMs: Long?,
    apiDebugStatus: String,
    apiDebugDetail: String,
    lastClientTurnId: String?,
    lastUploadSizeBytes: Long?,
    lastResponseHasAudio: Boolean?,
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
                elevation = 12.dp,
                shape = RoundedCornerShape(31.dp),
                ambientColor = HydrangeaViolet.copy(alpha = 0.10f),
                spotColor = HydrangeaBlue.copy(alpha = 0.14f),
            )
            .clip(RoundedCornerShape(31.dp))
            .background(OneUiPanel)
            .border(1.dp, Color.White.copy(alpha = 0.60f), RoundedCornerShape(31.dp))
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
                isAutoConversation && isPlaying -> "AI가 먼저 인사하고 있어요."
                isAutoConversation && isUserSpeaking -> "말씀하고 계신 것을 감지했어요."
                isAutoConversation && isListening -> "말씀을 듣고 있어요."
                isAutoConversation -> "말씀하시면 자동으로 들을 준비를 할게요."
                else -> "녹음 버튼을 눌러 말씀해 주세요."
            },
            color = OneUiInk.copy(alpha = 0.58f),
            fontSize = 20.sp,
            lineHeight = 27.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )

        if (isAutoConversation) {
            SpeechDetectionDebugPanel(
                status = speechDebugStatus,
                rms = speechRms,
                threshold = speechThreshold,
                noiseFloor = speechNoiseFloor,
                audioSource = speechAudioSource,
                lastSpeechDurationMs = lastSpeechDurationMs,
            )
            ApiCommunicationDebugPanel(
                status = apiDebugStatus,
                detail = apiDebugDetail,
                lastClientTurnId = lastClientTurnId,
                lastUploadSizeBytes = lastUploadSizeBytes,
                lastResponseHasAudio = lastResponseHasAudio,
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top,
        ) {
            ControlButton(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                label = "소리크게",
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
                    isAutoConversation && isUserSpeaking -> "감지 중"
                    isAutoConversation && isListening -> "대기 중"
                    isAutoConversation -> "자동"
                    isRecording -> "녹음 종료"
                    isUploading -> "업로드 중"
                    else -> "녹음"
                },
                selected = false,
                circleBrush = if (isRecording) Brush.verticalGradient(listOf(HydrangeaViolet, CallBlue)) else null,
                contentColor = if (isRecording) Color.White else OneUiInk.copy(alpha = 0.80f),
                enabled = !isAutoConversation && !isUploading && !isEndingSession,
                onClick = onToggleRecording,
            )
        }
    }
}

@Composable
private fun SpeechDetectionDebugPanel(
    status: String,
    rms: Double,
    threshold: Double,
    noiseFloor: Double,
    audioSource: String,
    lastSpeechDurationMs: Long?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.54f))
            .border(1.dp, Color.White.copy(alpha = 0.42f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = "디버그 감지 상태: $status",
            color = OneUiInk.copy(alpha = 0.72f),
            fontSize = 16.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = "소스 $audioSource / RMS ${formatAudioMetric(rms)} / 기준 ${formatAudioMetric(threshold)} / 소음 ${formatAudioMetric(noiseFloor)}",
            color = OneUiInk.copy(alpha = 0.56f),
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        lastSpeechDurationMs?.let { durationMs ->
            Text(
                text = "마지막 발화 ${durationMs}ms",
                color = OneUiInk.copy(alpha = 0.56f),
                fontSize = 15.sp,
                lineHeight = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun ApiCommunicationDebugPanel(
    status: String,
    detail: String,
    lastClientTurnId: String?,
    lastUploadSizeBytes: Long?,
    lastResponseHasAudio: Boolean?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.58f))
            .border(1.dp, HydrangeaBlue.copy(alpha = 0.20f), RoundedCornerShape(18.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = "API 통신 상태: $status",
            color = CallBlue.copy(alpha = 0.88f),
            fontSize = 16.sp,
            lineHeight = 21.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Text(
            text = detail,
            color = OneUiInk.copy(alpha = 0.62f),
            fontSize = 15.sp,
            lineHeight = 20.sp,
            fontWeight = FontWeight.SemiBold,
        )
        lastUploadSizeBytes?.let { bytes ->
            Text(
                text = "마지막 음성 파일 ${formatBytes(bytes)}",
                color = OneUiInk.copy(alpha = 0.54f),
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        lastResponseHasAudio?.let { hasAudio ->
            Text(
                text = if (hasAudio) "응답 음성: 도착" else "응답 음성: 없음",
                color = OneUiInk.copy(alpha = 0.54f),
                fontSize = 14.sp,
                lineHeight = 19.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        lastClientTurnId?.let { turnId ->
            Text(
                text = "turn ${turnId.takeLast(12)}",
                color = OneUiInk.copy(alpha = 0.40f),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.SemiBold,
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
    val defaultBackground = if (selected) Color.White.copy(alpha = 0.90f) else Color.White.copy(alpha = 0.74f)
    val defaultBorder = if (selected) HydrangeaBlue.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.46f)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Box(
            modifier = Modifier
                .size(circleSize)
                .shadow(12.dp, CircleShape, ambientColor = HydrangeaViolet.copy(alpha = 0.16f))
                .clip(CircleShape)
                .then(
                    if (circleBrush != null) {
                        Modifier.background(circleBrush)
                    } else {
                        Modifier.background(defaultBackground)
                    },
                )
                .border(1.dp, if (circleBrush != null) Color.White.copy(alpha = 0.78f) else defaultBorder, CircleShape)
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
        isUser -> Brush.verticalGradient(listOf(Color(0xFFDDF7EE), Color(0xFFAEE7CF)))
        else -> Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.92f), Color(0xFFF3F0FF).copy(alpha = 0.90f)))
    }
    val textColor = when {
        message.failed -> Color(0xFF6E1915)
        isUser -> Color(0xFF123A2E)
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
                .border(1.dp, Color.White.copy(alpha = 0.54f), bubbleShape)
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

private fun formatKoreanDuration(seconds: Long): String {
    val minutesPart = seconds / 60
    val secondsPart = seconds % 60
    return when {
        minutesPart > 0 -> "${minutesPart}분 ${secondsPart}초"
        else -> "${secondsPart}초"
    }
}

private fun formatAudioMetric(value: Double): String =
    String.format(Locale.getDefault(), "%.3f", value)

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "${bytes}B"
    val kib = bytes / 1024.0
    if (kib < 1024.0) return String.format(Locale.getDefault(), "%.1fKB", kib)
    return String.format(Locale.getDefault(), "%.1fMB", kib / 1024.0)
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
            onDismissEndSummary = {},
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
            onDismissEndSummary = {},
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
            onDismissEndSummary = {},
            onAcceptButtonSizeChange = {},
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun CallEndedSummaryScreenPreview() {
    YeoboSayTheme {
        CallScreen(
            state = CallUiState(
                showEndSummary = true,
                lastCallDurationSeconds = 372,
                lastCallEndedAtText = "오전 9:47",
            ),
            onStartSession = {},
            onRequestTestCall = {},
            onAcceptIncomingCall = {},
            onDeclineIncomingCall = {},
            onToggleRecording = {},
            onStopPlayback = {},
            onEndSession = {},
            onDismissEndSummary = {},
            onAcceptButtonSizeChange = {},
        )
    }
}
