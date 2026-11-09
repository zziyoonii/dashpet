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

private fun restrictionName(state: RestrictionState): String = when (state) {
    RestrictionState.NONE -> "없음"
    RestrictionState.ACTIVE -> "있음"
    RestrictionState.UNKNOWN -> "알 수 없음"
}

@OptIn(ExperimentalLayoutApi::class)
/** 데모 컨트롤과 확인용 패널. [DemoState.debugAvailable] 이 거짓이면 아무것도 그리지 않는다. */
@Composable
fun DemoControls(
    demo: DemoState,
    output: PetOutput,
    totalMs: Long,
    onTogglePlay: () -> Unit,
    onRestriction: (RestrictionState) -> Unit,
    onPanelVisible: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
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
            Button(
                onClick = onTogglePlay,
                colors = ButtonDefaults.buttonColors(containerColor = PetPalette.Accent, contentColor = PetPalette.Bg),
            ) {
                Text(if (demo.playback == PlaybackState.PLAYING) "정지" else "재생", fontSize = 14.sp)
            }
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
        if (demo.debugPanelVisible) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("감정: ${moodName(output.mood)}", color = PetPalette.Ink, fontSize = 14.sp)
                Text("모션 등급: ${tierName(output.tier)}", color = PetPalette.Ink, fontSize = 14.sp)
                Text("시나리오: ${formatDemoTime(demo.elapsedMs, totalMs)}", color = PetPalette.Ink, fontSize = 14.sp)
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
private fun DemoControlsPreview() {
    DemoControls(
        demo = DemoState(manualRestriction = RestrictionState.NONE, debugAvailable = true, elapsedMs = 23_000),
        output = PetOutput.INITIAL.copy(mood = Mood.DIZZY, tier = MotionTier.STATIC),
        totalMs = 55_000,
        onTogglePlay = {},
        onRestriction = {},
        onPanelVisible = {},
    )
}
