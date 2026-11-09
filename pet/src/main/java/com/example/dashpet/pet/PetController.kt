package com.example.dashpet.pet

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.example.dashpet.engine.Direction4
import com.example.dashpet.engine.DrivingEngine
import com.example.dashpet.engine.DrivingEvent
import com.example.dashpet.engine.EventType
import com.example.dashpet.engine.Mood
import com.example.dashpet.engine.MotionTier
import com.example.dashpet.engine.PetOutput
import com.example.dashpet.signal.TurnState
import kotlin.random.Random
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** 엔진의 모션 등급에서 정한 그리기 모드(data-model). 초기값은 [STATIC]. */
enum class MotionMode { STATIC, FULL }

/**
 * 그리는 쪽이 읽는 스냅샷(data-model `PetRenderState`). [physics] 와 [particles] 는 컨트롤러가 가진 살아 있는
 * 객체를 그대로 보여 준다(프레임마다 복사하지 않기 위해). 정적 모드에서 [physics] 는 null 이다.
 * 길이는 시안과 같은 논리 단위(dp)다.
 */
class PetRenderState(
    val mood: Mood,
    val intensity: Float,
    val direction: Direction4?,
    val motion: MotionMode,
    val turnSignal: TurnState,
    val appearance: PetAppearance,
    val viewportWidth: Float,
    val viewportHeight: Float,
    val reducedMotion: Boolean,
    val frame: Int,
    val physics: PetPhysicsState?,
    val particles: List<Particle>,
    val config: PetPhysicsConfig,
    /** 정적 모드의 포즈. 풀 모션에서는 null 이다. */
    val staticPose: StaticPose?,
)

/**
 * 엔진 출력과 이벤트를 펫의 물리 반응으로 바꾸는 컨트롤러(contracts/pet-view-api.md).
 *
 * - 결정적이다: 시계는 [step] 의 `dt` 누적([PetPhysicsState.clockSec])이고 난수는 주입된 [random] 만 쓴다.
 * - 정적 모드(초기값, 등급이 [MotionTier.FULL] 이 아닐 때)에서는 [onEvent], 감정 진입 효과, [step] 이 아무것도 바꾸지 않는다.
 * - **스레드 안전하지 않다.** 모든 메서드를 같은 스레드(보통 메인)에서 호출한다.
 */
class PetController(
    private val config: PetPhysicsConfig = PetPhysicsConfig(),
    random: Random = Random.Default,
) {
    private val random = random
    private val physics = PetPhysics(config, random)
    private val particles = Particles(config)

    private var motion by mutableStateOf(MotionMode.STATIC)

    // 다시 그리기 트리거. frame 은 풀 모션의 step 마다, version 은 그 밖의 입력이 바뀔 때 오른다.
    private var frame by mutableIntStateOf(0)
    private var version by mutableIntStateOf(0)

    private var mood = Mood.IDLE
    private var intensity = 0f
    private var direction: Direction4? = null
    private var turnSignal = TurnState.OFF
    private var appearance = PetAppearance()
    private var reduced = false
    private var viewportW = 0f
    private var viewportH = 0f

    /** 테스트용: 다시 그리기 트리거 중 입력 변경 쪽 카운터. 정적에서는 입력이 바뀔 때만 오른다. */
    internal val stateVersion: Int get() = version

    /** 현재 그리기 모드(상태 읽기). [MotionPolicy.shouldRunLoop] 에 넘긴다. */
    val motionMode: MotionMode get() = motion

    /** 풀 모션이면 true. 프레임 루프를 돌릴지의 유일한 기준. */
    val needsFrames: Boolean get() = motion == MotionMode.FULL

    /** 지금 그릴 상태. 읽으면 Compose 가 다시 그리기를 구독한다. */
    val renderState: PetRenderState
        get() {
            val f = frame
            version // 구독용 읽기
            val full = motion == MotionMode.FULL
            return PetRenderState(
                mood = mood,
                intensity = intensity,
                direction = direction,
                motion = motion,
                turnSignal = turnSignal,
                appearance = appearance,
                viewportWidth = viewportW,
                viewportHeight = viewportH,
                reducedMotion = reduced,
                frame = f,
                physics = if (full) physics.state else null,
                particles = if (full) particles.items else emptyList(),
                config = config,
                staticPose = if (full) null else petPose(mood, direction, turnSignal, appearance, null, config),
            )
        }

    /** 성격이 점프 속도를 정한다(활발 820, 그 외 650). 외형이 바뀌면 다시 호출한다. */
    fun setAppearance(appearance: PetAppearance) {
        if (this.appearance == appearance) return
        this.appearance = appearance
        version++
    }

    fun setReducedMotion(reduced: Boolean) {
        if (this.reduced == reduced) return
        this.reduced = reduced
        physics.reducedMotion = reduced
        if (reduced) particles.clear()
        version++
    }

    /** 표시 영역의 논리 크기(시안의 CSS px 와 같은 단위, dp). 0 이하이면 그리지 않는다. */
    fun setViewport(widthDp: Float, heightDp: Float) {
        if (viewportW == widthDp && viewportH == heightDp) return
        viewportW = widthDp
        viewportH = heightDp
        physics.setViewport(widthDp, heightDp)
        version++
    }

    /**
     * 엔진 출력을 반영한다. 등급이 바뀌면 모드를 전환하고(풀 → 정적은 같은 호출 안에서 즉시 초기화), 풀 모션에서 감정이 바뀌면
     * 진입 효과를 일으킨다. 정적 모드에서는 진입 효과가 없고, 포즈에 영향을 주는 값이 바뀔 때만 다시 그리게 한다.
     * 등급이 [MotionTier.FULL] 이 아니면(`STATIC`, `ONE_SHOT`) 정적이다.
     */
    fun applyOutput(output: PetOutput) {
        val previousMood = mood
        val changed = output.mood != mood || output.direction != direction || output.turnSignal != turnSignal
        mood = output.mood
        intensity = output.intensity
        direction = output.direction
        turnSignal = output.turnSignal

        val next = if (output.tier == MotionTier.FULL) MotionMode.FULL else MotionMode.STATIC
        if (next != motion) {
            when (next) {
                MotionMode.STATIC -> enterStatic()
                MotionMode.FULL -> enterFull()
            }
            version++
        } else if (motion == MotionMode.FULL && output.mood != previousMood) {
            onMoodEntered(output.mood)
            version++
        } else if (changed) {
            version++
        }
    }

    /** 풀 → 정적: 물리(접촉 플래그, 번짐, 재점프 예약 포함)와 파티클을 즉시 비운다. 서서히 줄어드는 효과는 없다. */
    private fun enterStatic() {
        physics.reset()
        particles.clear()
        motion = MotionMode.STATIC
    }

    /** 정적 → 풀: 물리를 새로 시작한다. 정적이던 동안의 이벤트와 감정 진입 효과는 재현하지 않는다. */
    private fun enterFull() {
        physics.reset()
        particles.clear()
        motion = MotionMode.FULL
    }

    private fun onMoodEntered(mood: Mood) {
        when (mood) {
            Mood.HAPPY -> {
                physics.jump(config.jumpVelocity(appearance.personality))
                physics.scheduleRejump(config.rejumpDelaySec, config.rejumpVelocity(appearance.personality))
            }
            Mood.STARTLED -> physics.startle()
            else -> Unit
        }
    }

    /**
     * 가속도 이벤트의 쏠림. 방향 [Direction4.RIGHT] 는 `corner(+1)`, [Direction4.LEFT] 는 `corner(-1)`,
     * [Direction4.FRONT] 는 `surge(+1)`, [Direction4.BACK] 는 `surge(-1)`. 세기는 이벤트 강도.
     * `HARD_CORNER`, `HARD_BRAKE`, `HARD_ACCEL` 만 반응하고, 정적 모드에서는 무시한다.
     */
    fun onEvent(event: DrivingEvent) {
        if (motion != MotionMode.FULL) return
        val s = event.intensity
        when (event.type) {
            EventType.HARD_CORNER -> when (event.direction) {
                Direction4.RIGHT -> physics.corner(+1, s)
                Direction4.LEFT -> physics.corner(-1, s)
                else -> return
            }
            EventType.HARD_BRAKE, EventType.HARD_ACCEL -> when (event.direction) {
                Direction4.FRONT -> physics.surge(+1, s)
                Direction4.BACK -> physics.surge(-1, s)
                else -> return
            }
            else -> return
        }
        version++
    }

    /**
     * 물리 시계를 [dtSec] 초 진행한다. 풀 모션에서만 동작한다. [dtSec] 가 0 이하이거나 NaN 이면 시간은 진행하지 않고
     * (프레임 루프의 첫 프레임), 큰 값은 1/30 초로 자른다. 파티클 감축 판단에는 자르기 전의 값을 쓴다.
     */
    fun step(dtSec: Float) {
        if (motion != MotionMode.FULL) return
        if (dtSec > 0f) { // NaN 은 false
            particles.reportFrameTime(dtSec)
            val dt = MotionPolicy.clampDt(dtSec, config)
            physics.step(dt, mood)
            if (physics.state.splatBurst) {
                physics.state.splatBurst = false
                if (!reduced) particles.emitGlassBurst(viewportW, viewportH, appearance.bodyColor, random)
            }
            particles.update(dt, viewportH)
            if (!reduced && mood == Mood.HAPPY) {
                particles.emitHappySparks(dt, petGeometry(physics.state, viewportW, viewportH, config), random)
            }
        }
        frame++
    }
}

/**
 * 엔진을 컨트롤러에 연결한다. `output` 과 `events` 를 함께 수집하고, 취소되면 함께 멈춘다.
 * 호출하기 전에 구독이 시작되도록 두 수집은 즉시 시작한다.
 *
 * [PetController] 는 스레드 안전하지 않으므로 **메인 디스패처의 코루틴에서 호출한다**
 * (예: `LaunchedEffect`). 엔진은 호출한 scope 의 디스패처에서 값을 내보낸다.
 */
suspend fun PetController.collectFrom(engine: DrivingEngine) = coroutineScope {
    launch(start = CoroutineStart.UNDISPATCHED) { engine.output.collect { applyOutput(it) } }
    launch(start = CoroutineStart.UNDISPATCHED) { engine.events.collect { onEvent(it) } }
}
