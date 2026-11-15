package com.example.dashpet.pet

import kotlin.math.PI
import kotlin.math.cos

/**
 * 둘레 22개 점마다 스프링을 달고 이웃끼리 연결한 소프트바디(시안 `SB`, `softStep`, `kick`).
 * 순수 Kotlin 이고 시간은 [step] 의 `dt` 로만 진행한다. 스레드 안전하지 않다.
 *
 * [d] 는 점의 반지름 방향 변위, [u] 는 그 속도다. 변위는 [PetPhysicsConfig.displacementMin]~
 * [PetPhysicsConfig.displacementMax] 로 제한한다.
 */
class SoftBody(private val config: PetPhysicsConfig = PetPhysicsConfig()) {
    val n: Int = config.softBodyPoints

    /** 점의 각도. 시안 `SB.ang`: `i / N * 2π - π/2`(맨 위 점부터 시계 방향). */
    val angles = FloatArray(n) { (it.toDouble() / n * PI * 2 - PI / 2).toFloat() }
    val d = FloatArray(n)
    val u = FloatArray(n)

    /** 모션 줄이기: 감쇠 `C = 16`, `kick` 진폭 ×0.4(시안 `reduce`). */
    var reducedMotion = false

    /**
     * 충격을 준다(시안 `kick`). m=0 고르게 퍼짐, m=1 한쪽으로 밀림, m=2 납작/길쭉, m=3 이상은 잔물결.
     * [phase] 는 밀리는 쪽의 각도.
     */
    fun kick(m: Int, amp: Float, phase: Float = 0f) {
        val a = if (reducedMotion) amp * config.kickAmpReducedFactor else amp
        for (i in 0 until n) u[i] += a * cos(m * (angles[i] - phase))
    }

    /** semi-implicit Euler 를 [PetPhysicsConfig.softBodySubSteps] 회로 나눠 진행한다(시안 `softStep`). */
    fun step(dt: Float) {
        val k = config.softBodyK
        val c = if (reducedMotion) config.softBodyCReduced else config.softBodyC
        val kn = config.softBodyKn
        val h = dt / config.softBodySubSteps
        repeat(config.softBodySubSteps) {
            for (i in 0 until n) {
                val lap = d[(i + n - 1) % n] + d[(i + 1) % n] - 2 * d[i]
                u[i] += (-k * d[i] - c * u[i] + kn * lap) * h
            }
            for (i in 0 until n) {
                d[i] = (d[i] + u[i] * h).coerceIn(config.displacementMin, config.displacementMax)
            }
        }
    }

    fun reset() {
        d.fill(0f)
        u.fill(0f)
    }
}
