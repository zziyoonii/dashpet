package com.example.dashpet.ui.demo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

/** 날씨 반응 데모 칩. 선택된 칩은 채워지고 "✓" 가 붙는다(색만으로 구분하지 않음). 한 번 더 누르거나 "해제"로 끈다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AmbientChips(selected: AmbientMood?, onSelect: (AmbientMood?) -> Unit, modifier: Modifier = Modifier) {
    FlowRow(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        AmbientMood.entries.forEach { mood ->
            val on = selected == mood
            val label = (if (on) "✓ " else "") + ambientName(mood)
            if (on) {
                Button(
                    onClick = { onSelect(null) },
                    colors = ButtonDefaults.buttonColors(containerColor = PetPalette.Ink, contentColor = PetPalette.Bg),
                    modifier = Modifier.heightIn(min = 40.dp),
                ) { Text(label, fontSize = 14.sp) }
            } else {
                OutlinedButton(
                    onClick = { onSelect(mood) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PetPalette.Ink),
                    modifier = Modifier.heightIn(min = 40.dp),
                ) { Text(label, fontSize = 14.sp) }
            }
        }
        OutlinedButton(
            onClick = { onSelect(null) },
            enabled = selected != null,
            // 비활성일 때도 배경에서 보여야 한다(켠 칩이 없을 때의 "해제").
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PetPalette.Ink, disabledContentColor = PetPalette.Muted),
            modifier = Modifier.heightIn(min = 40.dp),
        ) { Text("해제", fontSize = 14.sp) }
    }
}

/**
 * 제출용(release) 빌드의 작은 시연 컨트롤. **정차 중(모션 등급 `FULL`)에만** 보이고 제한이 걸리면 사라진다(헌장 I).
 * 모드(실차/데모), 데모 재생, 날씨 반응 칩, 음성 켜기/끄기, 이름 다시 짓기만 있고 제한 상태를 바꾸는 버튼은 없다(그건 디버그 빌드 전용).
 * [DemoState.debugAvailable] 이 참이면(디버그 빌드) 아무것도 그리지 않는다. 그쪽은 [DemoControls] 를 쓴다.
 */
@OptIn(ExperimentalLayoutApi::class)
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
    FlowRow(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(PetPalette.Panel)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        SignalMode.entries.forEach { mode ->
            val on = demo.mode == mode
            val label = (if (on) "✓ " else "") + if (mode == SignalMode.VEHICLE) "실차" else "데모"
            if (on) {
                Button(
                    onClick = { onMode(mode) },
                    colors = ButtonDefaults.buttonColors(containerColor = PetPalette.Ink, contentColor = PetPalette.Bg),
                    modifier = Modifier.heightIn(min = 40.dp),
                ) { Text(label, fontSize = 14.sp) }
            } else {
                OutlinedButton(
                    onClick = { onMode(mode) },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = PetPalette.Ink),
                    modifier = Modifier.heightIn(min = 40.dp),
                ) { Text(label, fontSize = 14.sp) }
            }
        }
        if (demo.mode == SignalMode.DEMO) {
            Button(
                onClick = onTogglePlay,
                colors = ButtonDefaults.buttonColors(containerColor = PetPalette.Accent, contentColor = PetPalette.Bg),
                modifier = Modifier.heightIn(min = 40.dp),
            ) { Text(if (demo.playback == PlaybackState.PLAYING) "정지" else "재생", fontSize = 14.sp) }
            AmbientChips(ambient, onAmbient)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("음성", color = PetPalette.Ink, fontSize = 14.sp)
            Switch(checked = voiceEnabled, onCheckedChange = onVoiceEnabled)
        }
        OutlinedButton(
            onClick = onRename,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = PetPalette.Ink),
            modifier = Modifier.heightIn(min = 40.dp),
        ) { Text("이름 다시 짓기", fontSize = 14.sp) }
    }
}

@Preview(widthDp = 480, heightDp = 160)
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
