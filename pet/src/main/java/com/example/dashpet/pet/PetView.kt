package com.example.dashpet.pet

import android.animation.ValueAnimator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.collectLatest

/**
 * 펫을 그린다. 크기는 [modifier] 가 정한 영역 전체이고, 영역이 0 이하이면 아무것도 그리지 않는다.
 *
 * - 프레임 루프는 `repeatOnLifecycle(STARTED)` 안에서, [MotionPolicy.shouldRunLoop] 가 참일 때(풀 모션)만 돈다.
 *   백그라운드로 가면 루프가 취소되어 시간이 멈추고, 돌아오면 첫 프레임 `dt = 0` 으로 다시 시작한다.
 * - `STARTED` 에 들어올 때마다 시스템의 모션 줄이기 설정(`ValueAnimator.areAnimatorsEnabled()`)을 다시 읽는다.
 * - 정적 모드에서는 루프, 애니메이터, 타이머, 터치 처리가 없고 컨트롤러의 상태가 바뀔 때만 다시 그린다.
 * 이름은 캔버스 안이 아니라 Compose 텍스트("펫 이름: ○○", 48sp, 대비 4.5:1 이상)로 왼쪽 위에 표시한다(아래쪽은 확인용 패널이, 위 가운데는 말풍선이 쓴다)(R15, FR-016). 비어 있으면 기본 이름이다.
 */
@Composable
fun PetView(
    controller: PetController,
    appearance: PetAppearance,
    modifier: Modifier = Modifier,
    aboveSlot: (@Composable BoxScope.() -> Unit)? = null,
) {
    val density = LocalDensity.current.density
    var heightDp by remember { mutableStateOf(0f) }
    var widthDp by remember { mutableStateOf(0f) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    SideEffect { controller.setAppearance(appearance) }

    LaunchedEffect(controller, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            // 이 Android API 호출은 여기에만 둔다(컨트롤러와 정책은 순수하게 유지).
            controller.setReducedMotion(!ValueAnimator.areAnimatorsEnabled())
            snapshotFlow { MotionPolicy.shouldRunLoop(controller.motionMode, lifecycleStarted = true) }
                .collectLatest { run -> if (run) runFrameLoop(controller) }
        }
    }

    Box(modifier.clipToBounds()) {
        Canvas(
            Modifier
                .matchParentSize()
                .onSizeChanged {
                    widthDp = it.width / density
                    heightDp = it.height / density
                    controller.setViewport(widthDp, heightDp)
                },
        ) {
            drawPet(controller.renderState)
        }
        // 가만히 있을 때 펫 몸의 위쪽 끝(논리 좌표). 말풍선 같은 위쪽 내용은 이 선 바로 위에 아래 정렬로 놓는다.
        // 뛰거나 눌릴 때는 펫이 움직이지만 말풍선은 제자리에 둔다(말풍선이 흔들리지 않게).
        if (aboveSlot != null && heightDp > 0f) {
            val restTopDp = restBodyTopDp(widthDp, heightDp, PetPhysicsConfig())
            Box(
                Modifier.align(Alignment.TopCenter).fillMaxWidth().height(restTopDp.dp).padding(bottom = 16.dp),
                contentAlignment = Alignment.BottomCenter,
            ) { aboveSlot() }
        }
        BasicText(
            text = "펫 이름: ${appearance.displayName}",
            modifier = Modifier.align(Alignment.TopStart).padding(start = 24.dp, top = 16.dp),
            style = TextStyle(color = PetPalette.NameLabel, fontSize = 48.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** 프레임 루프. 시작한 첫 프레임의 `dt` 는 0 이다. 취소(백그라운드, 정적 전환)되면 멈춘다. 앱에서 `withFrameNanos` 는 여기에만 있다. */
private suspend fun runFrameLoop(controller: PetController) {
    var lastNanos = 0L
    var first = true
    while (true) {
        withFrameNanos { now ->
            val dt = MotionPolicy.frameDt(first, (now - lastNanos) / 1_000_000_000f)
            first = false
            lastNanos = now
            controller.step(dt)
        }
    }
}

/** 가만히 서 있을 때(정차) 펫 몸의 위쪽 끝 높이. [petGeometry] 의 `cy - ry` 와 같다(기본 상태: 눌림, 기울기, 오프셋 없음). */
internal fun restBodyTopDp(widthDp: Float, heightDp: Float, config: PetPhysicsConfig): Float {
    val g = petGeometry(PetPhysicsState(SoftBody()), widthDp, heightDp, config)
    return (g.cy - g.ry).coerceAtLeast(0f)
}
