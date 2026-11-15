package com.example.dashpet.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dashpet.engine.report.ArrivalReport
import com.example.dashpet.pet.PetPalette

/**
 * 도착 리포트: P단 뒤 점수, 지난 주행과의 차이, 눈에 띄는 장면, 한 마디. 제한이 풀린 정차 중(모션 등급 `FULL`)에만 그린다.
 * 정적인 카드라 움직임이 없고, 닫기는 터치 한 번이다. 하이라이트 리플레이는 이번 범위가 아니다(글로만 보인다).
 */
@Composable
fun ArrivalReportCard(
    report: ArrivalReport,
    petName: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .widthIn(max = 420.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(PetPalette.Panel)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("${petName}의 도착 리포트", color = PetPalette.Muted, fontSize = 14.sp)
        Text(
            "${report.score}점",
            color = PetPalette.Accent,
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.semantics { contentDescription = "이번 주행 ${report.score}점" },
        )
        Text(report.deltaLine, color = PetPalette.Ink, fontSize = 16.sp)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            report.highlights.forEach { Text("• $it", color = PetPalette.Ink, fontSize = 14.sp) }
        }
        Text(report.oneLiner, color = PetPalette.Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Button(
            onClick = onDismiss,
            modifier = Modifier.heightIn(min = 48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PetPalette.Accent, contentColor = PetPalette.Bg),
        ) { Text("확인", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
    }
}

@Preview(widthDp = 480, heightDp = 420)
@Composable
private fun ArrivalReportPreview() {
    ArrivalReportCard(
        report = ArrivalReport(92, 4, "지난 주행보다 4점 올랐어요.", listOf("급커브 1회", "차선 이탈 1회", "깜빡이 켠 차선 변경 1회"), "오늘 정말 안정적으로 달렸어요. 수고하셨어요."),
        petName = "장수",
        onDismiss = {},
    )
}
