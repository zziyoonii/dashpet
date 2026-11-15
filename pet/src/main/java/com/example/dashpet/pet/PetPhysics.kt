package com.example.dashpet.pet

import com.example.dashpet.engine.Mood
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sign
import kotlin.math.sin
import kotlin.random.Random

/** 좌우 반응(시안 `S.lat`). [dir] +1 은 오른쪽 패널, -1 은 왼쪽 패널로 쏠린다. */
class Lateral(val dir: Int, val s: Float, val force: Float, val untilSec: Float)

/** 앞뒤 반응(시안 `S.lon`). [dir] +1 은 급제동(유리 쪽), -1 은 급가속(등 뒤로). */
class Longitudinal(val dir: Int, val s: Float, val untilSec: Float)

/** 급제동 뒤 유리에 남는 자국(시안 `S.smudge`). */
class Smudge(val x: Float, val y: Float, val rx: Float, val ry: Float, val startSec: Float)

/**
 * 물리 상태(시안 `S` 의 물리 부분). 물리와 [PetController] 가 쓰고 페인터는 읽기만 한다.
 * 시각은 모두 [clockSec](= `step` 의 `dt` 누적) 기준이다.
 */
class PetPhysicsState(val softBody: SoftBody) {
    var ox = 0f; var vx = 0f
    var oy = 0f; var vy = 0f
    var sx = 1f; var sy = 1f; var vsx = 0f; var vsy = 0f
    var lat: Lateral? = null
    var pen = 0f
    var z = 0f; var vz = 0f
    var press = 0f
    var lon: Longitudinal? = null
    var smudge: Smudge? = null
    val splatPhases = floatArrayOf(0f, 1f, 2f, 3f)

    /** 유리에 닿은 순간 true 가 된다. 컨트롤러가 물방울을 만든 뒤 false 로 돌린다. */
    var splatBurst = false
    var onGlass = false
    var onSeat = false
    var blinkNextSec = 0f
    var blinkStartSec = -1f
    var clockSec = 0f

    /** 깜짝 떨림 오프셋. 난수는 물리 `step` 이 정하고 페인터는 읽기만 한다. */
    var jitterX = 0f
    var jitterY = 0f

    /** 기쁨 진입 뒤 재점프 예약(시계 시각, 속도). 예약이 없으면 NaN. */
    var rejumpAtSec = Float.NaN
    var rejumpVelocity = 0f

    /** 결정성 테스트용: 상태를 숫자 목록으로 펼친다. */
    fun toList(): List<Float> = buildList {
        add(ox); add(vx); add(oy); add(vy); add(sx); add(sy); add(vsx); add(vsy); add(pen)
        add(z); add(vz); add(press); add(clockSec); add(jitterX); add(jitterY)
        add(blinkNextSec); add(blinkStartSec); add(rejumpAtSec); add(rejumpVelocity)
        add(if (onGlass) 1f else 0f); add(if (onSeat) 1f else 0f); add(if (splatBurst) 1f else 0f)
        lat?.let { add(it.dir.toFloat()); add(it.s); add(it.force); add(it.untilSec) }
        lon?.let { add(it.dir.toFloat()); add(it.s); add(it.untilSec) }
        smudge?.let { add(it.x); add(it.y); add(it.rx); add(it.ry); add(it.startSec) }
        splatPhases.forEach { add(it) }
        softBody.d.forEach { add(it) }
        softBody.u.forEach { add(it) }
    }
}

/**
 * 시안 `physics`, `corner`, `surge` 의 이식(순수 Kotlin). 난수는 주입된 [random] 만 쓰고 벽시계를 읽지 않는다.
 * 길이 단위는 시안의 CSS px 와 같은 논리 단위다([setViewport]).
 */
class PetPhysics(
    private val config: PetPhysicsConfig = PetPhysicsConfig(),
    private val random: Random = Random.Default,
) {
    var state = PetPhysicsState(SoftBody(config))
        private set

    var widthPx = 0f
        private set
    var heightPx = 0f
        private set

    /** 모션 줄이기: 소프트바디 `C = 16`, 스케일 `c = 18`, `kick` ×0.4. */
    var reducedMotion = false
        set(value) {
            field = value
            state.softBody.reducedMotion = value
        }

    fun setViewport(w: Float, h: Float) {
        widthPx = w
        heightPx = h
    }

    /** 물리를 처음 상태로 되돌린다(정적 전환, 풀 모션 시작). */
    fun reset() {
        state = PetPhysicsState(SoftBody(config)).also { it.softBody.reducedMotion = reducedMotion }
    }

    /** 몸 반지름. 시안 `petR` 의 홈 화면 정차 값. */
    fun radius(): Float = min(widthPx, heightPx) * config.radiusFactor

    /** 시안 `contact = max(20, W/2 - PANEL - rxMax)`. 몸이 벽에 닿을 때까지 움직일 수 있는 거리. */
    fun contactDistance(): Float {
        val rxMax = radius() * state.sx * 1.05f * 1.13f
        return max(20f, widthPx / 2f - config.panelWidth - rxMax)
    }

    /** 시안 `corner(dir, s)`. 오른쪽(+1)이면 오른쪽 패널로 쏠린다. */
    fun corner(dir: Int, s: Float) {
        val st = clamp01(s)
        val sec = state.clockSec
        state.lat = Lateral(
            dir = dir,
            s = st,
            force = config.cornerForceBase + config.cornerForcePerStrength * st,
            untilSec = sec + (config.cornerDurationBaseMs + config.cornerDurationPerStrengthMs * st) / 1000f,
        )
        state.vx += dir * 300f
    }

    /** 시안 `surge(dir, s)`. 급제동(+1)은 유리 쪽, 급가속(-1)은 등받이 쪽. */
    fun surge(dir: Int, s: Float) {
        val st = clamp01(s)
        val ms = if (dir > 0) {
            config.brakeDurationBaseMs + config.brakeDurationPerStrengthMs * st
        } else {
            config.accelDurationBaseMs + config.accelDurationPerStrengthMs * st
        }
        state.lon = Longitudinal(dir, st, state.clockSec + ms / 1000f)
        state.vz += if (dir > 0) 2.6f + 2.2f * st else -(1.8f + 1.6f * st)
    }

    fun jump(velocity: Float) {
        state.vy = velocity
    }

    /** 깜짝 진입 충격(시안 `onEnter`): 위로 튀고 가로세로가 오므라든다. */
    fun startle() {
        state.vy = config.startleVelocity
        state.vsx += config.startleScaleXImpulse
        state.vsy += config.startleScaleYImpulse
    }

    /** [delaySec] 뒤에 아직 기쁨이고 바닥이면 [velocity] 로 다시 점프한다(시안 `setTimeout`). */
    fun scheduleRejump(delaySec: Float, velocity: Float) {
        state.rejumpAtSec = state.clockSec + delaySec
        state.rejumpVelocity = velocity
    }

    fun cancelRejump() {
        state.rejumpAtSec = Float.NaN
    }

    /**
     * 시계를 [dt] 초 진행하고 물리를 한 번 계산한다. [dt] 는 호출하는 쪽이 (0, 1/30] 로 자른 값이다.
     * [mood] 는 어지러움(좌우 비틀거림)과 깜짝(작은 목표 스케일), 편안(숨쉬기, 깜빡임)에 쓴다.
     * [ambient] 는 엔진 감정이 편안일 때만 넘기는 배경 감정이다(더위, 추위, 조용은 목표 스케일을 바꾸고 깜빡임을 멈추며,
     * 추위는 모션 줄이기가 아닐 때 떤다. 비는 숨쉬기와 깜빡임을 그대로 둔다).
     */
    fun step(dt: Float, mood: Mood, ambient: AmbientMood? = null) {
        val st = state
        st.clockSec += dt
        val t = st.clockSec
        val w = widthPx
        val panel = config.panelWidth

        if (!st.rejumpAtSec.isNaN() && t >= st.rejumpAtSec) {
            if (mood == Mood.HAPPY && st.oy < config.rejumpMaxHeight) st.vy = st.rejumpVelocity
            st.rejumpAtSec = Float.NaN
        }

        val r = radius()
        val lat = st.lat?.takeIf { t < it.untilSec }
        st.lat?.let { old ->
            if (lat == null) {
                st.lat = null
                st.vx = -old.dir * (170f + 200f * old.s)
                st.vsx += 2.4f
                st.vsy -= 1.6f
                st.softBody.kick(2, 0.5f + 0.3f * old.s)
                st.softBody.kick(3, -0.28f)
            }
        }
        val contact = contactDistance()
        val target = if (mood == Mood.DIZZY) sin(t * 4.5f) * r * 0.32f else 0f
        val kx = if (lat != null) 3f else 26f
        val cx = if (lat != null) 1.6f else 4.2f
        st.vx += (-(st.ox - target) * kx - st.vx * cx + (if (lat != null) lat.dir * lat.force else 0f)) * dt
        st.ox += st.vx * dt
        val penTarget = if (lat != null) r * (0.16f + 0.24f * lat.s) else 0f
        st.pen += (penTarget - st.pen) * min(1f, dt * (if (lat != null) 12f else 7f))
        val lim = contact + st.pen
        if (abs(st.ox) > lim) {
            val imp = abs(st.vx)
            val side = sign(st.ox).toInt()
            st.ox = side * lim
            if (lat != null && side == lat.dir) {
                if (imp > 250f) {
                    st.vsy += imp * 0.0026f
                    st.vsx -= imp * 0.0022f
                    st.softBody.kick(1, -min(0.6f, imp * 0.00042f), if (side > 0) 0f else PI.toFloat())
                    st.softBody.kick(2, min(0.35f, imp * 0.00025f))
                }
                st.vx = 0f
            } else {
                st.vx = -st.vx * 0.22f
                st.vsx -= imp * 0.0042f
                st.vsy += imp * 0.0032f
            }
        }

        val lon = st.lon?.takeIf { t < it.untilSec }
        st.lon?.let { old ->
            if (lon == null) {
                st.lon = null
                if (old.dir > 0) {
                    st.vz = -(2.2f + 1.6f * old.s)
                    st.softBody.kick(2, -0.55f - 0.3f * old.s)
                    st.softBody.kick(3, 0.35f)
                    st.softBody.kick(4, -0.2f)
                    if (st.press > 0.2f) {
                        // 시안은 직전 프레임의 `S.face`(= 몸 중심, rx*0.85, ry*0.8)를 쓴다.
                        val g = petGeometry(st, widthPx, heightPx, config)
                        st.smudge = Smudge(g.cx, g.cy, g.rx * 0.85f, g.ry * 0.8f, t)
                    }
                } else {
                    st.vz = 1.7f + 1.3f * old.s
                    st.softBody.kick(2, 0.4f)
                    st.softBody.kick(3, -0.25f)
                }
            }
        }
        st.vz += (-38f * st.z - 4.2f * st.vz + (if (lon != null) lon.dir * (55f + 50f * lon.s) else 0f)) * dt
        st.z += st.vz * dt
        var pressT = 0f
        if (st.z > 1f) {
            val imp = abs(st.vz)
            st.z = 1f
            if (lon != null && lon.dir > 0) {
                if (!st.onGlass) {
                    st.onGlass = true
                    st.softBody.kick(0, min(0.5f, 0.1f * imp))
                    st.softBody.kick(2, min(0.45f, 0.08f * imp))
                    for (i in st.splatPhases.indices) st.splatPhases[i] = random.nextFloat() * (PI * 2).toFloat()
                    st.splatBurst = true
                }
                st.vz = 0f
                pressT = 0.8f + 0.2f * lon.s
            } else {
                st.vz = -st.vz * 0.3f
                st.softBody.kick(2, 0.05f * imp)
            }
        }
        if (st.z < 0.95f) st.onGlass = false
        if (st.z > -0.7f) st.onSeat = false
        if (st.z < -0.75f) {
            val imp = abs(st.vz)
            st.z = -0.75f
            if (lon != null && lon.dir < 0) {
                if (!st.onSeat) {
                    st.onSeat = true
                    st.softBody.kick(2, min(0.4f, 0.08f * imp))
                }
                st.vz = 0f
            } else {
                st.vz = -st.vz * 0.3f
            }
        }
        st.press += (pressT - st.press) * min(1f, dt * (if (pressT > st.press) 16f else 9f))
        st.softBody.step(dt)

        st.vy -= config.gravity * dt
        st.oy += st.vy * dt
        if (st.oy <= 0f) {
            if (st.vy < -60f) {
                val imp = -st.vy
                st.vsy -= imp * 0.0034f
                st.vsx += imp * 0.0028f
                st.softBody.kick(2, min(0.35f, imp * 0.0004f))
            }
            st.oy = 0f
            st.vy = 0f
        }

        val br = sin(t * 2.2f)
        val tsx: Float
        val tsy: Float
        if (lat != null) {
            tsx = 0.9f - 0.06f * lat.s; tsy = 1.1f + 0.14f * lat.s
        } else if (lon != null && lon.dir < 0 && st.z < -0.7f) {
            tsx = 1.13f; tsy = 0.85f
        } else if (mood == Mood.STARTLED) {
            tsx = 0.86f; tsy = 0.9f
        } else if (ambient == AmbientMood.HOT) {
            tsx = 1.24f; tsy = 0.74f
        } else if (ambient == AmbientMood.QUIET) {
            tsx = 1.06f; tsy = 0.9f + 0.02f * br
        } else if (ambient == AmbientMood.COLD) {
            tsx = 0.94f; tsy = 0.95f
        } else {
            tsx = 1f - 0.018f * br; tsy = 1f + 0.028f * br
        }
        val k = config.scaleK
        val c = if (reducedMotion) config.scaleCReduced else config.scaleC
        st.vsx += (-(st.sx - tsx) * k - st.vsx * c) * dt
        st.vsy += (-(st.sy - tsy) * k - st.vsy * c) * dt
        st.sx += st.vsx * dt
        st.sy += st.vsy * dt
        st.sx = st.sx.coerceIn(config.scaleXMin, config.scaleXMax)
        st.sy = st.sy.coerceIn(config.scaleYMin, config.scaleYMax)

        // 유리 자국은 1.8초 뒤에 사라진다(시안은 그릴 때 지우지만 그리기 쪽은 상태를 바꾸지 않는다).
        st.smudge?.let { if ((t - it.startSec) / config.smudgeDurationSec >= 1f) st.smudge = null }

        // 눈 깜빡임 일정(시안 `drawPet`): 편안할 때만 2.6~5.2초마다.
        if ((mood == Mood.IDLE || mood == Mood.AMBIENT) && (ambient == null || ambient == AmbientMood.RAIN)) {
            if (t > st.blinkNextSec) {
                st.blinkStartSec = t
                st.blinkNextSec = t + 2.6f + random.nextFloat() * 2.6f
            }
        }
        // 깜짝 떨림(시안 `jx`, `jy`)은 모션 줄이기에서 생략한다.
        if (mood == Mood.STARTLED && !reducedMotion) {
            st.jitterX = (random.nextFloat() - 0.5f) * 3f
            st.jitterY = (random.nextFloat() - 0.5f) * 2f
        } else if (ambient == AmbientMood.COLD && !reducedMotion) {
            // 추위의 떨림(시안 `jx = sin(t * 60) * 1.6`). 난수 없이 시계만 쓴다.
            st.jitterX = sin(t * 60f) * 1.6f
            st.jitterY = 0f
        } else {
            st.jitterX = 0f
            st.jitterY = 0f
        }
    }

    private fun clamp01(s: Float): Float = if (s.isNaN()) 0f else s.coerceIn(0f, 1f)
}
