package com.example.dashpet.pet

import android.animation.ValueAnimator
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
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
 * 이름은 캔버스 안이 아니라 Compose 텍스트(14sp, 대비 4.5:1 이상)로 왼쪽 위에 표시한다(아래쪽은 확인용 패널이, 위 가운데는 말풍선이 쓴다)(R15, FR-016). 비어 있으면 기본 이름이다.
 */
@Composable
fun PetView(
    controller: PetController,
    appearance: PetAppearance,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current.density
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
                .onSizeChanged { controller.setViewport(it.width / density, it.height / density) },
        ) {
            drawPet(controller.renderState)
        }
        BasicText(
            text = appearance.displayName,
            modifier = Modifier.align(Alignment.TopStart).padding(start = 12.dp, top = 8.dp),
            style = TextStyle(color = PetPalette.NameLabel, fontSize = 14.sp),
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
