package com.example.dashpet.pet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.dashpet.engine.Direction4
import com.example.dashpet.engine.DrivingEvent
import com.example.dashpet.engine.EventType
import com.example.dashpet.engine.Mood
import com.example.dashpet.engine.MotionTier
import com.example.dashpet.engine.PetOutput
import com.example.dashpet.signal.TurnState
import kotlin.random.Random

// 시안과 나란히 보기 위한 1차 확인용 프리뷰(visual-parity.md A, B). 시드 고정 난수와 고정 dt 로 컨트롤러를
// 원하는 시점까지 step 해서 상태를 만든다. 강도는 시안 기본값 0.7.

private const val PreviewW = 360f
private const val PreviewH = 420f
private const val PreviewDt = 1f / 60f

private fun fullOutput(mood: Mood, direction: Direction4? = null) =
    PetOutput(mood, 0.7f, direction, MotionTier.FULL, TurnState.OFF, 0)

private fun PetController.advance(sec: Float) {
    repeat((sec / PreviewDt).toInt()) { step(PreviewDt) }
}

/** 풀 모션 컨트롤러를 만들어 [build] 로 입력을 넣고 [sec] 초 진행한다. */
private fun previewController(sec: Float, build: PetController.() -> Unit): PetController =
    PetController(random = Random(42)).also {
        it.setViewport(PreviewW, PreviewH)
        it.applyOutput(fullOutput(Mood.IDLE))
        it.build()
        it.advance(sec)
    }

@Composable
private fun PreviewFrame(sec: Float, build: PetController.() -> Unit) {
    val controller = remember { previewController(sec, build) }
    Canvas(Modifier.size(PreviewW.dp, PreviewH.dp).clipToBounds()) { drawPet(controller.renderState) }
}

private fun PetController.event(type: EventType, direction: Direction4, s: Float = 0.7f) =
    onEvent(DrivingEvent(type, 0, s, direction))

// ---- 감정 5종 ----

@Preview(name = "편안", widthDp = 360, heightDp = 420)
@Composable
private fun PreviewIdle() = PreviewFrame(0.8f) {}

@Preview(name = "기쁨(점프 중)", widthDp = 360, heightDp = 420)
@Composable
private fun PreviewHappy() = PreviewFrame(0.25f) { applyOutput(fullOutput(Mood.HAPPY)) }

@Preview(name = "찌그러짐", widthDp = 360, heightDp = 420)
@Composable
private fun PreviewSquashed() = PreviewFrame(0.4f) {
    applyOutput(fullOutput(Mood.SQUASHED, Direction4.LEFT))
    event(EventType.HARD_CORNER, Direction4.LEFT)
}

@Preview(name = "어지러움", widthDp = 360, heightDp = 420)
@Composable
private fun PreviewDizzy() = PreviewFrame(1.2f) { applyOutput(fullOutput(Mood.DIZZY)) }

@Preview(name = "깜짝", widthDp = 360, heightDp = 420)
@Composable
private fun PreviewStartled() = PreviewFrame(0.15f) { applyOutput(fullOutput(Mood.STARTLED)) }

// ---- 방향지시등 윙크(풀 모션, 감는 구간 0.2초 / 뜨는 구간 0.5초) ----

private fun fullOutput(mood: Mood, turn: TurnState) = PetOutput(mood, 0.7f, null, MotionTier.FULL, turn, 0)

@Preview(name = "윙크 우(감김)", widthDp = 360, heightDp = 420)
@Composable
private fun PreviewWinkRightClosed() = PreviewFrame(0.2f) { applyOutput(fullOutput(Mood.IDLE, TurnState.RIGHT)) }

@Preview(name = "윙크 우(뜸, 호박색 눈썹)", widthDp = 360, heightDp = 420)
@Composable
private fun PreviewWinkRightOpen() = PreviewFrame(0.6f) { applyOutput(fullOutput(Mood.IDLE, TurnState.RIGHT)) }

@Preview(name = "윙크 좌(감김)", widthDp = 360, heightDp = 420)
@Composable
private fun PreviewWinkLeftClosed() = PreviewFrame(0.2f) { applyOutput(fullOutput(Mood.IDLE, TurnState.LEFT)) }

// ---- 배경 감정 4종(풀 모션, 몸 모양이 자리 잡도록 1.5초) ----

@Preview(name = "배경 감정 더위", widthDp = 360, heightDp = 420)
@Composable
private fun PreviewAmbientHot() = PreviewFrame(1.5f) { setAmbient(AmbientMood.HOT) }

@Preview(name = "배경 감정 추위", widthDp = 360, heightDp = 420)
@Composable
private fun PreviewAmbientCold() = PreviewFrame(1.5f) { setAmbient(AmbientMood.COLD) }

@Preview(name = "배경 감정 비", widthDp = 360, heightDp = 420)
@Composable
private fun PreviewAmbientRain() = PreviewFrame(1.5f) { setAmbient(AmbientMood.RAIN) }

@Preview(name = "배경 감정 조용", widthDp = 360, heightDp = 420)
@Composable
private fun PreviewAmbientQuiet() = PreviewFrame(3f) { setAmbient(AmbientMood.QUIET) }

// ---- 방향별 물리 반응 ----

@Preview(name = "급제동 번짐(중간)", widthDp = 360, heightDp = 420)
@Composable
private fun PreviewBrakeMid() = PreviewFrame(0.5f) {
    applyOutput(fullOutput(Mood.SQUASHED, Direction4.FRONT))
    event(EventType.HARD_BRAKE, Direction4.FRONT)
}

@Preview(name = "급커브 좌(오른쪽 벽에 눌림)", widthDp = 360, heightDp = 420)
@Composable
private fun PreviewCornerPressRight() = PreviewFrame(0.5f) {
    applyOutput(fullOutput(Mood.SQUASHED, Direction4.RIGHT))
    event(EventType.HARD_CORNER, Direction4.RIGHT) // corner(+1): 오른쪽 패널
}

@Preview(name = "급커브 우(왼쪽 벽에 눌림)", widthDp = 360, heightDp = 420)
@Composable
private fun PreviewCornerPressLeft() = PreviewFrame(0.5f) {
    applyOutput(fullOutput(Mood.SQUASHED, Direction4.LEFT))
    event(EventType.HARD_CORNER, Direction4.LEFT) // corner(-1): 왼쪽 패널
}

@Preview(name = "급가속 꺼짐", widthDp = 360, heightDp = 420)
@Composable
private fun PreviewAccel() = PreviewFrame(0.5f) {
    applyOutput(fullOutput(Mood.SQUASHED, Direction4.BACK))
    event(EventType.HARD_ACCEL, Direction4.BACK)
}

// ---- 정적 등급 (visual-parity.md C): 시간이 없는 포즈. 컨트롤러를 만들고 step 하지 않는다 ----

private fun staticOutput(
    mood: Mood,
    direction: Direction4? = null,
    turn: TurnState = TurnState.OFF,
) = PetOutput(mood, 0.7f, direction, MotionTier.STATIC, turn, 0)

@Composable
private fun StaticPreviewFrame(output: PetOutput) {
    val controller = remember {
        PetController().also {
            it.setViewport(PreviewW, PreviewH)
            it.applyOutput(output)
        }
    }
    Canvas(Modifier.size(PreviewW.dp, PreviewH.dp).clipToBounds()) { drawPet(controller.renderState) }
}

@Preview(name = "정적 편안", widthDp = 360, heightDp = 420)
@Composable
private fun PreviewStaticIdle() = StaticPreviewFrame(staticOutput(Mood.IDLE))

@Preview(name = "정적 기쁨", widthDp = 360, heightDp = 420)
@Composable
private fun PreviewStaticHappy() = StaticPreviewFrame(staticOutput(Mood.HAPPY))

@Preview(name = "정적 어지러움", widthDp = 360, heightDp = 420)
@Composable
private fun PreviewStaticDizzy() = StaticPreviewFrame(staticOutput(Mood.DIZZY))

@Preview(name = "정적 깜짝", widthDp = 360, heightDp = 420)
@Composable
private fun PreviewStaticStartled() = StaticPreviewFrame(staticOutput(Mood.STARTLED))

@Preview(name = "정적 찌그러짐 좌", widthDp = 360, heightDp = 420)
@Composable
private fun PreviewStaticSquashedLeft() = StaticPreviewFrame(staticOutput(Mood.SQUASHED, Direction4.LEFT))

@Preview(name = "정적 찌그러짐 우", widthDp = 360, heightDp = 420)
@Composable
private fun PreviewStaticSquashedRight() = StaticPreviewFrame(staticOutput(Mood.SQUASHED, Direction4.RIGHT))

@Preview(name = "정적 찌그러짐 앞", widthDp = 360, heightDp = 420)
@Composable
private fun PreviewStaticSquashedFront() = StaticPreviewFrame(staticOutput(Mood.SQUASHED, Direction4.FRONT))

@Preview(name = "정적 찌그러짐 뒤", widthDp = 360, heightDp = 420)
@Composable
private fun PreviewStaticSquashedBack() = StaticPreviewFrame(staticOutput(Mood.SQUASHED, Direction4.BACK))

@Preview(name = "정적 윙크 좌", widthDp = 360, heightDp = 420)
@Composable
private fun PreviewStaticWinkLeft() = StaticPreviewFrame(staticOutput(Mood.IDLE, turn = TurnState.LEFT))

@Preview(name = "정적 윙크 우", widthDp = 360, heightDp = 420)
@Composable
private fun PreviewStaticWinkRight() = StaticPreviewFrame(staticOutput(Mood.IDLE, turn = TurnState.RIGHT))

@Preview(name = "정적 배경 감정 비(우비)", widthDp = 360, heightDp = 420)
@Composable
private fun PreviewStaticRain() {
    val controller = remember {
        PetController().also {
            it.setViewport(PreviewW, PreviewH)
            it.applyOutput(staticOutput(Mood.IDLE))
            it.setAmbient(AmbientMood.RAIN)
        }
    }
    Canvas(Modifier.size(PreviewW.dp, PreviewH.dp).clipToBounds()) { drawPet(controller.renderState) }
}
