package com.example.dashpet.ui.speech

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 말풍선. [state] 가 null 이면 아무것도 그리지 않는다(재그리기 없음).
 * 애니메이션, 전환, 글자 효과, 점멸, 자동 스크롤, 터치 처리가 없다. 나타남과 사라짐은 상태가 바뀌는 즉시다(헌장 I).
 * 글자 14 sp(최소 12 sp), 흰 글자와 어두운 배경(대비 약 15:1), 최대 3줄. 색은 `ivi` 토큰이 확인되기 전까지의 임시값이다 `[확인 필요]`.
 */
@Composable
fun SpeechBubble(state: BubbleState?, modifier: Modifier = Modifier) {
    if (state == null) return
    Text(
        text = state.text,
        color = TextColor,
        fontSize = 14.sp,
        maxLines = 3, // 문장은 120자, 24어절 이하라 잘리지 않는다(SpeechCatalogTest)
        overflow = TextOverflow.Clip,
        modifier = modifier
            .widthIn(max = 320.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(BackgroundColor)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics { contentDescription = state.text },
    )
}

private val TextColor = Color(0xFFFFFFFF)
private val BackgroundColor = Color(0xFF1B2430)
