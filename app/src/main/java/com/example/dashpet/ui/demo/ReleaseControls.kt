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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dashpet.engine.MotionTier
import com.example.dashpet.engine.PetOutput
import com.example.dashpet.pet.AmbientMood
import com.example.dashpet.pet.PetPalette

/** 배경 감정(더위, 추위, 비, 조용) 이름. */
fun ambientName(mood: AmbientMood): String = when (mood) {
    AmbientMood.HOT -> "더움"
    AmbientMood.COLD -> "추움"
    AmbientMood.RAIN -> "비"
    AmbientMood.QUIET -> "조용"
}

/**
 * 컨트롤 패널의 버튼. 선택된 버튼은 채워지고 "✓" 가 붙는다(색만으로 구분하지 않음). [primary] 는 강조 버튼(재생 등).
 * 글자 22sp, 높이 56dp 이상(디버그 패널과 제출용 컨트롤이 함께 쓴다).
 */
@Composable
internal fun ControlChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    primary: Boolean = false,
    enabled: Boolean = true,
) {
    val m = modifier.heightIn(min = 56.dp)
    val text = (if (selected) "✓ " else "") + label
    when {
        primary -> Button(
            onClick = onClick, modifier = m, enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = PetPalette.Accent, contentColor = PetPalette.Bg),
        ) { Text(text, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
        selected -> Button(
            onClick = onClick, modifier = m, enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = PetPalette.Ink, contentColor = PetPalette.Bg),
        ) { Text(text, fontSize = 22.sp) }
        else -> OutlinedButton(
            onClick = onClick, modifier = m, enabled = enabled,
            // 비활성일 때도 배경에서 보여야 한다(켠 칩이 없을 때의 "해제").
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PetPalette.Ink, disabledContentColor = PetPalette.Muted),
        ) { Text(text, fontSize = 22.sp) }
    }
}

/** 날씨 반응 데모 칩. 선택된 칩은 채워지고 "✓" 가 붙는다. 한 번 더 누르거나 "해제"로 끈다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AmbientChips(selected: AmbientMood?, onSelect: (AmbientMood?) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AmbientMood.entries.forEach { mood ->
            val on = selected == mood
            ControlChip(ambientName(mood), onClick = { onSelect(if (on) null else mood) }, selected = on)
        }
        ControlChip("해제", onClick = { onSelect(null) }, enabled = selected != null)
    }
}

/** 컨트롤 구역: 작은 제목 아래에 버튼을 줄바꿈하며 둔다. 구역 사이는 [ControlDivider] 로 나눈다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ControlSection(title: String, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = PetPalette.Muted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
    }
}

/** 구역 사이의 가는 구분선. */
@Composable
internal fun ControlDivider() {
    HorizontalDivider(color = PetPalette.Muted.copy(alpha = 0.35f), thickness = 1.dp)
}

/** 컨트롤 패널 틀: 오른쪽 위 버튼 아래에 놓는 둥근 패널. 내용이 길면 안에서 스크롤한다. */
@Composable
internal fun ControlPanelFrame(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier
            .width(460.dp)
            .heightIn(max = 760.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(PetPalette.Panel)
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) { content() }
}

/**
 * 제출용(release) 빌드의 시연 컨트롤. **정차 중(모션 등급 `FULL`)에만** 보이고 제한이 걸리면 사라진다(헌장 I).
 * 모드(실차/데모), 데모 재생, 날씨 반응, 음성 켜기/끄기, 이름 다시 짓기만 있고 제한 상태를 바꾸는 버튼은 없다(그건 디버그 빌드 전용).
 * 오른쪽 위의 "패널 숨기기/보기" 버튼으로 패널을 접고 편다. [DemoState.debugAvailable] 이 참이면(디버그 빌드) 아무것도 그리지 않는다.
 * 그쪽은 [DemoControls] 를 쓴다. 영역 전체를 덮는 [Box] 라서 호출하는 쪽이 크기와 여백을 정한다(터치는 버튼만 받는다).
 */
@Composable
fun ReleaseControls(
    demo: DemoState,
    output: PetOutput,
    ambient: AmbientMood?,
    voiceEnabled: Boolean,
    onMode: (SignalMode) -> Unit,
    onTogglePlay: () -> Unit,
    onAmbient: (AmbientMood?) -> Unit,
    onVoiceEnabled: (Boolean) -> Unit,
    onRename: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (demo.debugAvailable || output.tier != MotionTier.FULL) return
    var open by rememberSaveable { mutableStateOf(true) }
    Box(modifier) {
        ControlChip(
            label = if (open) "패널 숨기기" else "패널 보기",
            onClick = { open = !open },
            modifier = Modifier.align(Alignment.TopEnd),
        )
        if (open) {
            ControlPanelFrame(Modifier.align(Alignment.TopEnd).padding(top = 72.dp)) {
                ControlSection("화면 모드") {
                    SignalMode.entries.forEach { mode ->
                        ControlChip(
                            label = if (mode == SignalMode.VEHICLE) "실차" else "데모",
                            onClick = { onMode(mode) },
                            selected = demo.mode == mode,
                        )
                    }
                }
                if (demo.mode == SignalMode.DEMO) {
                    ControlDivider()
                    ControlSection("시나리오") {
                        ControlChip(if (demo.playback == PlaybackState.PLAYING) "정지" else "재생", onClick = onTogglePlay, primary = true)
                    }
                    ControlDivider()
                    ControlSection("날씨 반응") { AmbientChips(ambient, onAmbient) }
                }
                ControlDivider()
                ControlSection("설정") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("음성", color = PetPalette.Ink, fontSize = 22.sp)
                        Switch(checked = voiceEnabled, onCheckedChange = onVoiceEnabled)
                    }
                    ControlChip("이름 다시 짓기", onClick = onRename)
                }
            }
        }
    }
}

@Preview(widthDp = 720, heightDp = 640)
@Composable
private fun ReleaseControlsPreview() {
    ReleaseControls(
        demo = DemoState(mode = SignalMode.DEMO),
        output = PetOutput.INITIAL.copy(tier = MotionTier.FULL),
        ambient = AmbientMood.RAIN,
        voiceEnabled = true,
        onMode = {},
        onTogglePlay = {},
        onAmbient = {},
        onVoiceEnabled = {},
        onRename = {},
    )
}
