package com.example.dashpet.pet

import androidx.compose.ui.graphics.Color
import com.example.dashpet.engine.Direction4
import com.example.dashpet.engine.Mood
import com.example.dashpet.signal.Side
import com.example.dashpet.signal.TurnState

/**
 * 정적 등급의 포즈(data-model `StaticPose`). 시간 값이 없는 순수 값이다.
 * [tiltDeg] 는 몸 아래 중앙을 축으로 한 회전(양수는 오른쪽으로 기울어짐), [scaleX], [scaleY] 는 몸 크기 배율이다.
 * [closedEye] 는 선으로 감을 눈, [amberBrow] 는 호박색 눈썹을 그릴 쪽, [hood] 는 우비 실루엣 여부다.
 */
data class StaticPose(
    val bodyColor: Color,
    val tiltDeg: Float = 0f,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val closedEye: Side? = null,
    val amberBrow: Side? = null,
    val hood: Boolean = false,
)

/**
 * 감정, 방향, 방향지시등, 외형, 배경 감정에서 정적 포즈를 정한다(research R8). 시간 입력이 없고 같은 입력이면 항상 같은 값이다.
 *
 * - 색: 편안(과 배경 감정 AMBIENT)은 몸 색 그대로, 기쁨은 `#3DDC84`, 어지러움은 `#F5A524`, 깜짝은 `#FF5A5A` 와 섞는다.
 * - 찌그러짐: 몸 색 그대로이고 [direction] 쪽으로 쏠린 포즈. 오른쪽은 +[PetPhysicsConfig.staticTiltDeg], 왼쪽은 −,
 *   앞은 [PetPhysicsConfig.staticFrontScale] 배, 뒤는 [PetPhysicsConfig.staticBackScale] 배. 방향이 없으면 곧게 선다.
 *   **[확인 필요]** 기울기, 배율, 혼합비는 시안에 근거가 없는 초안이다.
 * - 방향지시등: 같은 쪽 눈을 선으로 감고 같은 쪽에 호박색 눈썹을 둔다(점멸 없음).
 * - [ambient] 가 비([AmbientMood.RAIN])일 때만 우비를 쓴다.
 */
fun petPose(
    mood: Mood,
    direction: Direction4?,
    turnSignal: TurnState,
    appearance: PetAppearance,
    ambient: AmbientMood?,
    config: PetPhysicsConfig = PetPhysicsConfig(),
): StaticPose {
    val base = appearance.bodyColor
    val color = when (mood) {
        Mood.HAPPY -> mixColor(base, PetPalette.Good, config.staticHappyMix)
        Mood.DIZZY -> mixColor(base, PetPalette.Warn, config.staticDizzyMix)
        Mood.STARTLED -> mixColor(base, PetPalette.Crit, config.staticStartledMix)
        Mood.IDLE, Mood.AMBIENT, Mood.SQUASHED -> base
    }
    var tilt = 0f
    var sx = 1f
    var sy = 1f
    if (mood == Mood.SQUASHED) {
        when (direction) {
            Direction4.RIGHT -> tilt = config.staticTiltDeg
            Direction4.LEFT -> tilt = -config.staticTiltDeg
            Direction4.FRONT -> { sx = config.staticFrontScale; sy = config.staticFrontScale }
            Direction4.BACK -> { sx = config.staticBackScale; sy = config.staticBackScale }
            null -> Unit
        }
    }
    val side = when (turnSignal) {
        TurnState.LEFT -> Side.LEFT
        TurnState.RIGHT -> Side.RIGHT
        TurnState.OFF -> null
    }
    return StaticPose(color, tilt, sx, sy, side, side, ambient == AmbientMood.RAIN)
}
