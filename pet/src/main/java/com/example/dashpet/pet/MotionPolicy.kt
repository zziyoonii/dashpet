package com.example.dashpet.pet

/** 모션 줄이기에서 쓰는 보정값 묶음(research R10). 값은 [PetPhysicsConfig] 에서 읽는다. */
data class MotionCorrection(
    val softBodyC: Float,
    val scaleC: Float,
    val kickFactor: Float,
    val particles: Boolean,
    val speedLines: Boolean,
    val bodyWobble: Boolean,
)

/**
 * 프레임 루프를 돌릴지, 시간을 어떻게 진행할지, 모션 줄이기를 어떻게 보정할지를 정하는 순수 규칙(research R4, R10, R11).
 * Android API 를 읽지 않는다(모션 줄이기 여부는 [PetView] 가 읽어서 컨트롤러에 넣는다).
 */
object MotionPolicy {
    /** 풀 모션이고 생명주기가 `STARTED` 일 때만 루프를 돌린다. 정적이나 백그라운드에서는 돌리지 않는다. */
    fun shouldRunLoop(mode: MotionMode, lifecycleStarted: Boolean): Boolean =
        mode == MotionMode.FULL && lifecycleStarted

    /**
     * 프레임 사이의 경과 시간에서 `step` 에 넘길 값을 정한다. 루프를 (재)시작한 첫 프레임([isFirstFrame])은 0 이고,
     * 음수나 NaN 도 0 이다. 자르기는 하지 않는다(컨트롤러가 파티클 감축 판단에 원래 값을 쓰기 때문, [clampDt]).
     */
    fun frameDt(isFirstFrame: Boolean, elapsedSec: Float): Float =
        if (isFirstFrame || !(elapsedSec > 0f)) 0f else elapsedSec

    /** 시간 진행에 쓰는 `dt` 를 (0, [PetPhysicsConfig.maxStepSec]] 로 자른다. 0 이하나 NaN 이면 0(진행 없음). */
    fun clampDt(dtSec: Float, config: PetPhysicsConfig): Float =
        if (dtSec > 0f) minOf(dtSec, config.maxStepSec) else 0f

    /** 모션 줄이기 보정값. 켜면 감쇠를 키우고 `kick` 을 줄이고 파티클, 속도선, 몸 흔들림을 생략한다. */
    fun correction(reduced: Boolean, config: PetPhysicsConfig): MotionCorrection =
        if (reduced) {
            MotionCorrection(config.softBodyCReduced, config.scaleCReduced, config.kickAmpReducedFactor,
                particles = false, speedLines = false, bodyWobble = false)
        } else {
            MotionCorrection(config.softBodyC, config.scaleC, 1f,
                particles = true, speedLines = true, bodyWobble = true)
        }
}
