package com.example.dashpet.pet

/**
 * 시안의 물리 상수를 한 곳에 모은다. 출처는 `specs/002-pet-view/research.md` R6 과
 * 시안 `docs/DashPet 캐릭터 시안.html` 의 함수 이름이다(`softStep`, `kick`, `physics`, `corner`, `surge`, `onEnter`).
 * 바꾸기 전에 시안과 R6 을 확인한다. 시간은 ms, 속도/중력은 px 와 초 단위다(시안과 같음).
 */
data class PetPhysicsConfig(
    // ---- 소프트바디 (R6 소프트바디, 시안 SB 와 softStep, kick) ----
    /** 둘레 점 개수. 시안 `SB.N = 22`. */
    val softBodyPoints: Int = 22,
    /** 점마다 원위치로 당기는 스프링. 시안 `softStep` 의 `K`. */
    val softBodyK: Float = 110f,
    /** 감쇠. 시안 `softStep` 의 `C = reduce ? 16 : 5`. */
    val softBodyC: Float = 5f,
    /** 모션 줄이기 감쇠. */
    val softBodyCReduced: Float = 16f,
    /** 이웃 점 연결(라플라시안) 강도. 시안 `softStep` 의 `KN`. */
    val softBodyKn: Float = 240f,
    /** 프레임당 분할 횟수(semi-implicit Euler). 시안 `softStep` 의 `steps`. */
    val softBodySubSteps: Int = 3,
    /** 변위 제한. 시안 `softStep` 의 `Math.max(-0.32, Math.min(0.42, ...))`. */
    val displacementMin: Float = -0.32f,
    val displacementMax: Float = 0.42f,
    /** 모션 줄이기에서 `kick` 진폭 배율. 시안 `kick` 의 `if(reduce) amp *= 0.4`. */
    val kickAmpReducedFactor: Float = 0.4f,

    // ---- 스케일 스프링 (R6 스케일 스프링, 시안 physics 끝부분) ----
    val scaleK: Float = 230f,
    /** 시안 `c = reduce ? 18 : 9`. */
    val scaleC: Float = 9f,
    val scaleCReduced: Float = 18f,
    /** 스케일 제한. 시안 `physics` 의 `S.sx` 0.55~1.7, `S.sy` 0.5~1.6. */
    val scaleXMin: Float = 0.55f,
    val scaleXMax: Float = 1.7f,
    val scaleYMin: Float = 0.5f,
    val scaleYMax: Float = 1.6f,

    // ---- 좌우 (R6 좌우, 시안 corner 와 physics) ----
    /** 코너 힘 `2600 + 3400·s`. 시안 `corner`. */
    val cornerForceBase: Float = 2600f,
    val cornerForcePerStrength: Float = 3400f,
    /** 코너 지속 `500 + 800·s` ms. 시안 `corner`. */
    val cornerDurationBaseMs: Float = 500f,
    val cornerDurationPerStrengthMs: Float = 800f,

    // ---- 앞뒤 (R6 앞뒤, 시안 surge) ----
    /** 급제동 지속 `450 + 750·s` ms. 시안 `surge`(dir > 0). */
    val brakeDurationBaseMs: Float = 450f,
    val brakeDurationPerStrengthMs: Float = 750f,
    /** 급가속 지속 `400 + 650·s` ms. 시안 `surge`(dir < 0). */
    val accelDurationBaseMs: Float = 400f,
    val accelDurationPerStrengthMs: Float = 650f,

    // ---- 점프 (R6 점프, 시안 onEnter 와 physics) ----
    /** 중력 px/s². 시안 `physics` 의 `S.vy -= 2600*dt`. */
    val gravity: Float = 2600f,
    /** 방향지시등 윙크 주기(초). 시안 `winkClosed`: `floor((now - start) / 380ms) % 2 == 0` 일 때 감는다. */
    val winkPeriodSec: Float = 0.38f,

    /** 기쁨 진입 점프 초기 속도. 시안 `onEnter`: 활발 820, 그 외 650. */
    val jumpVelocityLively: Float = 820f,
    val jumpVelocityDefault: Float = 650f,

    /** 재점프 속도. 시안 `onEnter` 의 setTimeout: 활발 620, 그 외 480. */
    val rejumpVelocityLively: Float = 620f,
    val rejumpVelocityDefault: Float = 480f,
    /** 재점프까지 기다리는 시간(초). 시안 `onEnter` 의 760ms. */
    val rejumpDelaySec: Float = 0.76f,
    /** 재점프는 이 높이(px) 미만일 때만. 시안 `S.oy < 4`. */
    val rejumpMaxHeight: Float = 4f,
    /** 깜짝 진입 충격. 시안 `onEnter`: `vy = 420`, `vsx -= 1.5`, `vsy -= 1`. */
    val startleVelocity: Float = 420f,
    val startleScaleXImpulse: Float = -1.5f,
    val startleScaleYImpulse: Float = -1f,

    // ---- 화면 (R6 반지름, 시안 petR 과 PANEL) ----
    /** 몸 반지름 비율. 시안 `petR` 의 앱 홈 정차(parked) 값 `min(W, H) * 0.21`. */
    val radiusFactor: Float = 0.21f,
    /** 벽(패널) 두께. 시안 `PANEL = 12`(홈 화면). */
    val panelWidth: Float = 12f,
    /** 한 번의 `step` 이 처리하는 최대 시간(초). 시안 `Math.min(0.033, ...)` 와 R4 의 1/30. */
    val maxStepSec: Float = 1f / 30f,
    /** 유리 자국 지속 시간(초). 시안 `drawGlass` 의 1800ms. */
    val smudgeDurationSec: Float = 1.8f,

    // ---- 파티클 (R9) ----
    /** 파티클 상한. R9 "상한 60개". */
    val maxParticles: Int = 60,

    // ---- 아래 값은 시안에 근거가 없는 초안이다 ----
    /** [확인 필요] 정적 포즈 기울기 ±10°(R8, 미결 항목 1). */
    val staticTiltDeg: Float = 10f,
    /** [확인 필요] 정적 포즈 앞(급제동) 배율 1.08(R8, 미결 항목 1). */
    val staticFrontScale: Float = 1.08f,
    /** [확인 필요] 정적 포즈 뒤(급가속) 배율 0.9(R8, 미결 항목 1). */
    val staticBackScale: Float = 0.9f,
    /** [확인 필요] 정적 포즈 색 혼합 비율. 기쁨 0.9, 어지러움 0.8, 깜짝 0.85(R8. 기쁨은 시안 moving 값에서 깜빡임을 뺀 값). */
    val staticHappyMix: Float = 0.9f,
    val staticDizzyMix: Float = 0.8f,
    val staticStartledMix: Float = 0.85f,
    /** [확인 필요] 정적 몸 반지름 비율. 시안 `petR` 의 moving 분기 `min(W, H) * 0.17`(홈 화면). */
    val staticRadiusFactor: Float = 0.17f,
    /** [확인 필요] 파티클 감축 프레임 시간 임계값 24ms(R9, 미결 항목 2). */
    val particleThrottleFrameMs: Float = 24f,
    /** [확인 필요] 파티클 감축 연속 프레임 수 30(R9, 미결 항목 2). */
    val particleThrottleFrames: Int = 30,
) {
    /** 기쁨 진입 점프의 초기 속도(시안 `onEnter`: 활발 820, 그 외 650). */
    fun jumpVelocity(personality: Personality): Float =
        if (personality == Personality.LIVELY) jumpVelocityLively else jumpVelocityDefault

    /** 재점프의 초기 속도(시안 `onEnter` 의 setTimeout: 활발 620, 그 외 480). */
    fun rejumpVelocity(personality: Personality): Float =
        if (personality == Personality.LIVELY) rejumpVelocityLively else rejumpVelocityDefault
}
