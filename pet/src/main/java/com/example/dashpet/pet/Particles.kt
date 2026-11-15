package com.example.dashpet.pet

import androidx.compose.ui.graphics.Color
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/** 파티클 종류(data-model). [SWEAT], [RAIN], [Z], [PUFF] 는 배경 감정이 만든다. */
enum class ParticleType { SPARK, SWEAT, RAIN, Z, PUFF, GLASS_DROP }

/** 파티클 하나. 시안 `spawn` 의 `{type, x, y, vx, vy, life, max, size, col}`. */
class Particle(
    val type: ParticleType,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var life: Float,
    val max: Float,
    val size: Float,
    val color: Color?,
)

/**
 * 파티클 풀. 상한은 [PetPhysicsConfig.maxParticles](60). 프레임 시간이 [PetPhysicsConfig.particleThrottleFrameMs]
 * 를 넘는 상태가 [PetPhysicsConfig.particleThrottleFrames] 프레임 연속이면 [throttled] 가 되어 생성기가 멈추고,
 * 그 뒤 임계값 이하의 프레임이 오면 다시 켜진다(R9, FR-014). 난수는 호출하는 쪽이 넘긴다(결정성).
 */
class Particles(private val config: PetPhysicsConfig = PetPhysicsConfig()) {
    private val pool = ArrayList<Particle>(config.maxParticles)
    private var slowFrames = 0

    val items: List<Particle> get() = pool
    val count: Int get() = pool.size

    /** 성능 저하로 생성을 멈춘 상태. */
    var throttled = false
        private set

    /** 프레임 간격(초, 자르기 전의 실제 값)을 알린다. 0 이하는 무시한다(루프 첫 프레임 등). */
    fun reportFrameTime(frameSec: Float) {
        if (!(frameSec > 0f)) return
        if (frameSec > config.particleThrottleFrameMs / 1000f) slowFrames++ else slowFrames = 0
        throttled = slowFrames >= config.particleThrottleFrames
    }

    /** 상한을 넘으면 만들지 않고 false. */
    fun spawn(
        type: ParticleType,
        x: Float, y: Float, vx: Float, vy: Float,
        life: Float, size: Float,
        color: Color? = null,
    ): Boolean {
        if (pool.size >= config.maxParticles) return false
        pool += Particle(type, x, y, vx, vy, life, life, size, color)
        return true
    }

    /** 시안 `drawParticles` 의 이동과 수명 처리. 수명이 끝났거나 화면 아래로 나가면 제거한다. */
    fun update(dt: Float, heightPx: Float) {
        for (i in pool.indices.reversed()) {
            val p = pool[i]
            p.life -= dt
            if (p.life <= 0f || p.y > heightPx + 20f) {
                pool.removeAt(i)
                continue
            }
            if (p.type == ParticleType.SWEAT) p.vy += 420f * dt
            p.x += p.vx * dt
            p.y += p.vy * dt
        }
    }

    /**
     * 기쁨의 반짝임(시안 `emitters` 의 spark). 확률 `dt * 12` 로 한 개. 성능 저하 중에는 만들지 않는다.
     * 난수 호출 순서는 시안의 인자 평가 순서와 같다.
     */
    fun emitHappySparks(dt: Float, g: PetGeometry, random: Random) {
        if (throttled) return
        if (random.nextFloat() < dt * 12f) {
            val x = g.cx + (random.nextFloat() - .5f) * g.rx * 2.6f
            val y = g.cy - g.ry * (0.4f + random.nextFloat() * 0.9f)
            val vx = (random.nextFloat() - .5f) * 40f
            val vy = -60f - random.nextFloat() * 60f
            val size = 4f + random.nextFloat() * 4f
            spawn(ParticleType.SPARK, x, y, vx, vy, 0.9f, size)
        }
    }

    /**
     * 배경 감정의 파티클(시안 `emitters`, 정차 중): 더위는 땀(확률 `dt * 3.5`), 비는 빗줄기(`dt * 55`), 조용은 Z(`dt * 1.1`),
     * 추위는 입김(`dt * 1.6`)을 한 프레임에 하나씩. 성능 저하 중에는 만들지 않는다. 해당하는 종류가 아니면 난수를 쓰지 않는다.
     * [w] 는 표시 영역 너비(빗줄기가 떨어지기 시작하는 가로 범위)다.
     */
    fun emitAmbient(ambient: AmbientMood?, dt: Float, g: PetGeometry, w: Float, random: Random) {
        if (throttled || ambient == null) return
        when (ambient) {
            AmbientMood.HOT -> if (random.nextFloat() < dt * 3.5f) {
                val s = if (random.nextFloat() < .5f) -1f else 1f
                spawn(ParticleType.SWEAT, g.cx + s * g.rx * 0.78f, g.cy - g.ry * 0.45f, s * 50f, -40f, 1.1f, 5f)
            }
            AmbientMood.RAIN -> if (random.nextFloat() < dt * 55f) {
                spawn(ParticleType.RAIN, random.nextFloat() * w, -10f, -60f, 900f, 1.2f, 1f)
            }
            AmbientMood.QUIET -> if (random.nextFloat() < dt * 1.1f) {
                spawn(ParticleType.Z, g.cx + g.rx * 0.55f, g.cy - g.ry * 0.8f, 22f, -34f, 2.4f, 14f)
            }
            AmbientMood.COLD -> if (random.nextFloat() < dt * 1.6f) {
                spawn(ParticleType.PUFF, g.cx + g.rx * 0.08f, g.cy + g.ry * 0.2f, 30f, -18f, 1.4f, 6f)
            }
        }
    }

    /** 유리에 닿는 순간 가장자리에서 튀는 물방울 12개(시안 `drawPet` 의 `splatBurst`). */
    fun emitGlassBurst(w: Float, h: Float, color: Color, random: Random) {
        if (throttled) return
        repeat(12) {
            val a = random.nextFloat() * (PI * 2).toFloat()
            val vy = 10f + random.nextFloat() * 24f
            val size = 3f + random.nextFloat() * 5f
            spawn(ParticleType.GLASS_DROP, w / 2 + cos(a) * w * 0.49f, h * 0.5f + sin(a) * h * 0.46f, 0f, vy, 2.8f, size, color)
        }
    }

    fun clear() {
        pool.clear()
    }
}
