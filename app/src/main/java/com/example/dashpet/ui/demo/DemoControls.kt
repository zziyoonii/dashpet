package com.example.dashpet.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
    RestrictionState.NONE -> "제한 해제 (움직임)"
    RestrictionState.ACTIVE -> "제한 걸림 (정적)"
    RestrictionState.UNKNOWN -> "신호 끊김 (정적)"
}

/**
 * 디버그 빌드의 시연 컨트롤. [DemoState.debugAvailable] 이 거짓이면 아무것도 그리지 않는다. 영역 전체를 덮는 [Box] 라서
 * 호출하는 쪽이 크기와 여백을 정한다(터치는 버튼만 받는다).
 * - 오른쪽 위: "실차 데이터 보기/숨기기"와 "패널 숨기기/보기" 버튼. 항상 보인다.
 * - 오른쪽: 컨트롤 패널(화면 모드, 시나리오, 날씨 반응, 주행 제한 상태, 설정). 펫을 가리지 않게 옆에 둔다.
 * - 아래 가운데: 실차 데이터(감정, 모션 등급, 시나리오 시간 또는 신호 진단). 컨트롤 패널과 따로 열고 닫는다.
 * 실차 모드에서는 재생과 수동 제한을 숨긴다(수동 제한이 실차 상태를 덮어쓸 수 없다, FR-016).
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
    ambient: com.example.dashpet.pet.AmbientMood? = null,
    onAmbient: (com.example.dashpet.pet.AmbientMood?) -> Unit = {},
    onRename: () -> Unit = {},
) {
    if (!demo.debugAvailable) return
    var open by rememberSaveable { mutableStateOf(true) }
    Box(modifier) {
        Row(Modifier.align(Alignment.TopEnd), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // [DemoState.debugPanelVisible] 는 이제 "실차 데이터" 영역을 보일지 정한다.
            ControlChip(
                label = if (demo.debugPanelVisible) "실차 데이터 숨기기" else "실차 데이터 보기",
                onClick = { onPanelVisible(!demo.debugPanelVisible) },
            )
            ControlChip(label = if (open) "패널 숨기기" else "패널 보기", onClick = { open = !open })
        }
        if (open) {
            ControlPanelFrame(Modifier.align(Alignment.TopEnd).padding(top = 72.dp)) {
                ControlSection("화면 모드") {
                    SignalMode.entries.forEach { mode ->
                        ControlChip(modeName(mode), onClick = { onMode(mode) }, selected = demo.mode == mode)
                    }
                }
                if (demo.mode == SignalMode.DEMO) {
                    ControlDivider()
                    ControlSection("시나리오") {
                        ControlChip(
                            if (demo.playback == PlaybackState.PLAYING) "정지" else "재생",
                            onClick = onTogglePlay,
                            primary = true,
                        )
                        // 확인용: 이벤트를 하나씩 돌아가며 말하게 한다. 제한 상태는 아래 구역에서 정한다.
                        ControlChip("말하기", onClick = onDebugSpeak)
                    }
                    ControlDivider()
                    ControlSection("날씨 반응") { AmbientChips(ambient, onAmbient) }
                    ControlDivider()
                    ControlSection("주행 제한 상태 (확인용)") {
                        RestrictionState.entries.forEach { state ->
                            ControlChip(restrictionName(state), onClick = { onRestriction(state) }, selected = demo.manualRestriction == state)
                        }
                    }
                }
                ControlDivider()
                ControlSection("설정") {
                    // 음성 켜기/끄기. 제한이 있거나 알 수 없으면 바꿀 수 없다(비활성, FR-016).
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("음성", color = PetPalette.Ink, fontSize = 22.sp)
                        Switch(checked = voiceEnabled, onCheckedChange = onVoiceEnabled, enabled = output.tier == MotionTier.FULL)
                    }
                    ControlChip("이름 짓기", onClick = onRename)
                }
            }
        }
        if (demo.debugPanelVisible) {
            DataArea(demo, output, totalMs, Modifier.align(Alignment.BottomCenter))
        }
    }
}

/** 실차 데이터 영역: 펫 아래쪽에 컨트롤 패널과 따로 띄운다. 줄마다 쌓으면 펫을 가리므로 가로로 나란히 두고 폭이 모자랄 때만 줄바꿈한다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DataArea(demo: DemoState, output: PetOutput, totalMs: Long, modifier: Modifier = Modifier) {
    FlowRow(
        modifier
            .heightIn(max = 220.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PetPalette.Panel)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("감정: ${moodName(output.mood)}", color = PetPalette.Ink, fontSize = 22.sp)
        Text("모션 등급: ${tierName(output.tier)}", color = PetPalette.Ink, fontSize = 22.sp)
        if (demo.mode == SignalMode.DEMO) {
            Text("시나리오: ${formatDemoTime(demo.elapsedMs, totalMs)}", color = PetPalette.Ink, fontSize = 22.sp)
        } else {
            demo.diagnostics?.let { d ->
                (signalDiagnosticLines(d) + extraSignalDiagnosticLines(d)).forEach { Text(it, color = PetPalette.Ink, fontSize = 22.sp) }
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
