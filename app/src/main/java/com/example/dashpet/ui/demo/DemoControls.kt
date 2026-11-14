package com.example.dashpet.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dashpet.engine.MotionTier
import com.example.dashpet.engine.Mood
import com.example.dashpet.engine.PetOutput
import com.example.dashpet.pet.PetPalette
import com.example.dashpet.signal.RestrictionState
import com.example.dashpet.signal.car.CarLinkState
import com.example.dashpet.signal.car.SignalAvailability
import com.example.dashpet.signal.car.SignalDiagnostics

private fun moodName(mood: Mood): String = when (mood) {
    Mood.IDLE, Mood.AMBIENT -> "편안"
    Mood.HAPPY -> "기쁨"
    Mood.SQUASHED -> "찌그러짐"
    Mood.DIZZY -> "어지러움"
    Mood.STARTLED -> "깜짝"
}

private fun tierName(tier: MotionTier): String = when (tier) {
    MotionTier.STATIC -> "정적"
    MotionTier.ONE_SHOT -> "한 번 모션"
    MotionTier.FULL -> "풀 모션"
}

private fun modeName(mode: SignalMode): String = when (mode) {
    SignalMode.VEHICLE -> "실차"
    SignalMode.DEMO -> "데모"
}

private fun restrictionName(state: RestrictionState): String = when (state) {
    RestrictionState.NONE -> "없음"
    RestrictionState.ACTIVE -> "있음"
    RestrictionState.UNKNOWN -> "알 수 없음"
}

@OptIn(ExperimentalLayoutApi::class)
/**
 * 모드 선택, 데모 컨트롤, 확인용 패널. [DemoState.debugAvailable] 이 거짓이면 아무것도 그리지 않는다.
 * 실차 모드에서는 재생과 수동 제한을 숨기고 신호 진단을 보인다(수동 제한이 실차 상태를 덮어쓸 수 없다, FR-016).
 */
@Composable
fun DemoControls(
    demo: DemoState,
    output: PetOutput,
    totalMs: Long,
    onTogglePlay: () -> Unit,
    onRestriction: (RestrictionState) -> Unit,
    onPanelVisible: (Boolean) -> Unit,
    onMode: (SignalMode) -> Unit,
    modifier: Modifier = Modifier,
    voiceEnabled: Boolean = true,
    onVoiceEnabled: (Boolean) -> Unit = {},
    onDebugSpeak: () -> Unit = {},
) {
    if (!demo.debugAvailable) return
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(PetPalette.Panel)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 모드 선택: 선택된 칩은 채워지고 "✓" 가 붙는다(색만으로 구분하지 않음).
            SignalMode.entries.forEach { mode ->
                val selected = demo.mode == mode
                val label = (if (selected) "✓ " else "") + modeName(mode)
                if (selected) {
                    Button(
                        onClick = { onMode(mode) },
                        colors = ButtonDefaults.buttonColors(containerColor = PetPalette.Ink, contentColor = PetPalette.Bg),
                    ) { Text(label, fontSize = 14.sp) }
                } else {
                    OutlinedButton(
                        onClick = { onMode(mode) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PetPalette.Ink),
                    ) { Text(label, fontSize = 14.sp) }
                }
            }
            // 음성 켜기/끄기. 제한이 있거나 알 수 없으면 바꿀 수 없다(비활성, FR-016).
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Text("음성", color = PetPalette.Ink, fontSize = 14.sp)
                Switch(checked = voiceEnabled, onCheckedChange = onVoiceEnabled, enabled = output.tier == MotionTier.FULL)
            }
            if (demo.mode == SignalMode.DEMO) {
                Button(
                    onClick = onTogglePlay,
                    colors = ButtonDefaults.buttonColors(containerColor = PetPalette.Accent, contentColor = PetPalette.Bg),
                ) {
                    Text(if (demo.playback == PlaybackState.PLAYING) "정지" else "재생", fontSize = 14.sp)
                }
                // 확인용: 이벤트를 하나씩 돌아가며 말하게 한다. 제한 상태는 아래 칩으로 정한다.
                OutlinedButton(
                    onClick = onDebugSpeak,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PetPalette.Ink),
                ) { Text("말하기", fontSize = 14.sp) }
                // 선택 상태 표시: 선택된 버튼은 채워지고 "✓" 가 붙는다(색만으로 구분하지 않음).
                RestrictionState.entries.forEach { state ->
                    val selected = demo.manualRestriction == state
                    val label = (if (selected) "✓ " else "") + "제한 " + restrictionName(state)
                    if (selected) {
                        Button(
                            onClick = { onRestriction(state) },
                            colors = ButtonDefaults.buttonColors(containerColor = PetPalette.Ink, contentColor = PetPalette.Bg),
                        ) { Text(label, fontSize = 14.sp) }
                    } else {
                        OutlinedButton(
                            onClick = { onRestriction(state) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = PetPalette.Ink),
                        ) { Text(label, fontSize = 14.sp) }
                    }
                }
            }
        }
        if (demo.debugPanelVisible) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("감정: ${moodName(output.mood)}", color = PetPalette.Ink, fontSize = 14.sp)
                Text("모션 등급: ${tierName(output.tier)}", color = PetPalette.Ink, fontSize = 14.sp)
                if (demo.mode == SignalMode.DEMO) {
                    Text("시나리오: ${formatDemoTime(demo.elapsedMs, totalMs)}", color = PetPalette.Ink, fontSize = 14.sp)
                } else {
                    demo.diagnostics?.let { d ->
                        // 줄마다 세로로 쌓으면 패널이 펫을 가린다. 가로로 나란히 두고 폭이 모자랄 때만 줄바꿈한다.
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            signalDiagnosticLines(d).forEach { Text(it, color = PetPalette.Ink, fontSize = 14.sp) }
                        }
                    }
                }
                Row {
                    OutlinedButton(
                        onClick = { onPanelVisible(false) },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = PetPalette.Ink),
                    ) { Text("패널 숨기기", fontSize = 12.sp) }
                }
            }
        } else {
            Row {
                OutlinedButton(
                    onClick = { onPanelVisible(true) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PetPalette.Ink),
                ) { Text("패널 보기", fontSize = 12.sp) }
            }
        }
    }
}

@Preview(widthDp = 360, heightDp = 240)
@Composable
private fun DemoControlsDemoPreview() {
    DemoControls(
        demo = DemoState(mode = SignalMode.DEMO, manualRestriction = RestrictionState.NONE, debugAvailable = true, elapsedMs = 23_000),
        output = PetOutput.INITIAL.copy(mood = Mood.DIZZY, tier = MotionTier.STATIC),
        totalMs = 55_000,
        onTogglePlay = {},
        onRestriction = {},
        onPanelVisible = {},
        onMode = {},
    )
}

@Preview(widthDp = 360, heightDp = 280)
@Composable
private fun DemoControlsVehiclePreview() {
    DemoControls(
        demo = DemoState(
            mode = SignalMode.VEHICLE,
            debugAvailable = true,
            diagnostics = SignalDiagnostics.INITIAL.copy(
                link = CarLinkState.CONNECTED,
                restriction = RestrictionState.ACTIVE,
                activeRestrictions = 0x50,
                speedMps = 11.1f,
                parked = false,
                speedAvailability = SignalAvailability.OK,
                gearAvailability = SignalAvailability.OK,
                restrictionAvailability = SignalAvailability.OK,
                lastUpdateMs = 12_300,
            ),
        ),
        output = PetOutput.INITIAL.copy(tier = MotionTier.STATIC),
        totalMs = 55_000,
        onTogglePlay = {},
        onRestriction = {},
        onPanelVisible = {},
        onMode = {},
    )
}
