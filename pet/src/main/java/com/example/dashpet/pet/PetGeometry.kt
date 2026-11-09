package com.example.dashpet.pet

import kotlin.math.max
import kotlin.math.min

/**
 * 한 프레임의 몸 기하(시안 `drawPet` 앞부분의 `cx`, `cy`, `rx`, `ry`, `R`, `Rf`, `lean`, `e`).
 * 컨트롤러(파티클 위치, 유리 자국)와 페인터가 같은 값을 쓰도록 순수 함수로 뽑았다. 길이는 시안과 같은 논리 단위다.
 *
 * - [r] 는 깊이 보정된 반지름, [rf] 는 유리에 눌릴 때 커지는 눈 크기용 반지름
 * - [lean] 은 급가속으로 뒤로 밀린 정도 0~1, [e] 는 유리에 눌린 정도(smoothstep) 0~1
 * - [groundX], [groundRx], [groundY] 는 번짐으로 퍼지기 전의 바닥 그림자 기준
 */
class PetGeometry(
    val cx: Float,
    val cy: Float,
    val rx: Float,
    val ry: Float,
    val r: Float,
    val rf: Float,
    val lean: Float,
    val e: Float,
    val groundX: Float,
    val groundRx: Float,
    val groundY: Float,
)

/** 시안 `drawPet` 의 정차(parked) 분기 계산. [w], [h] 는 표시 영역의 논리 크기다. */
fun petGeometry(s: PetPhysicsState, w: Float, h: Float, config: PetPhysicsConfig): PetGeometry {
    val groundY = h * 0.8f
    val depth = if (s.z >= 0f) 1f + 0.16f * s.z else 1f + 0.3f * s.z
    val r0 = min(w, h) * config.radiusFactor
    val r = r0 * depth
    val lean = max(0f, -s.z / 0.75f)
    val pr = s.press
    val e = pr * pr * (3f - 2f * pr)
    var rx = r * s.sx * 1.05f
    var ry = r * s.sy * 0.92f
    val bottom = 0.35f + 0.65f * 0.55f
    var cx = w / 2f + s.ox + s.jitterX
    var cy = groundY - bottom * ry - s.oy + s.jitterY + ry * 0.08f * lean
    val groundX = cx
    val groundRx = rx
    // 유리에 눌린 정도(e)만큼 화면 가운데로 번지며 거의 꽉 찬다.
    val trx = w * 0.47f
    val tryy = h * 0.43f
    cx += (w / 2f - cx) * e
    cy += (h * 0.5f - cy) * e
    rx += (trx - rx) * e
    ry += (tryy - ry) * e
    val rf = r + (min(w, h) * 0.34f - r) * e
    return PetGeometry(cx, cy, rx, ry, r, rf, lean, e, groundX, groundRx, groundY)
}
