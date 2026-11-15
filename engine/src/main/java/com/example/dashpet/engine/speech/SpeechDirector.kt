package com.example.dashpet.engine.speech

import com.example.dashpet.engine.DrivingEvent
import com.example.dashpet.engine.EventType
import com.example.dashpet.engine.MotionTier
import com.example.dashpet.engine.speech.SpeechCommand.HideBubble
import com.example.dashpet.engine.speech.SpeechCommand.Speak
import com.example.dashpet.engine.speech.SpeechCommand.StopSpeaking

/**
 * 말하기 규칙(쿨다운, 우선순위 선점, 대기 슬롯, 변형 번호, 말풍선 지움). Android 를 모르는 순수 상태 머신이다.
 * 시각은 호출자가 넘기는 값만 쓰고 난수를 쓰지 않으므로 같은 입력에는 같은 지시가 나온다.
 * 모션 등급은 `PetOutput.tier` 값을 그대로 받는다. 제한 상태를 다시 해석하지 않는다(헌장 I, II).
 * 한 인스턴스는 한 스레드(또는 한 코루틴)에서만 쓴다.
 *
 * 이벤트가 말하지 못하거나 밀려난 이유는 [log] 로 알린다(`skip event=… reason=…`, `queued …`, `preempt …`).
 * 이벤트 종류와 이유, 시간 값만 담고 문장 본문은 담지 않는다(FR-019, FR-020).
 */
class SpeechDirector(
    private val config: SpeechConfig = SpeechConfig(),
    private val catalog: SpeechCatalog = SpeechCatalog(),
    private val log: (String) -> Unit = {},
) {
    private val lastSpokenAt = mutableMapOf<EventType, Long>()
    private val lastVariant = mutableMapOf<EventType, Int>()
    private var nextId = 1L
    private var speaking: Speak? = null
    private var pending: Pending? = null
    private var bubbleVisible = false
    private var tierNow = MotionTier.STATIC // 첫 출력 전에는 제한 상태를 모른다: 정적과 같은 규칙
    private var tripEndSpoken = false

    private class Pending(val utterance: Utterance, val queuedAtMs: Long)

    fun onEvent(event: DrivingEvent, tier: MotionTier, nowMs: Long): List<SpeechCommand> {
        tierNow = tier
        val type = event.type
        val priority = type.speechPriority()
        if (type == EventType.TRIP_END && tripEndSpoken) { // 도착 인사는 한 번만
            log("skip event=$type reason=already-spoken")
            return emptyList()
        }
        val last = lastSpokenAt[type]
        if (last != null && nowMs - last < config.cooldownFor(priority)) {
            log("skip event=$type reason=cooldown remainingMs=${config.cooldownFor(priority) - (nowMs - last)}")
            return emptyList()
        }

        val line = catalog.variants(type)[0] // 변형은 말할 때 고른다. 대기 중에는 문장 쌍만 필요
        val utterance = Utterance(type, priority, line.text, line.shortText, event.atMs)
        val current = speaking
        return when {
            current == null -> speak(utterance, nowMs)
            priority.rank > current.utterance.priority.rank -> {
                pending?.let { log("skip event=${it.utterance.eventType} reason=dropped-by-preemption by=$type") }
                pending = null
                log("preempt event=$type over=${current.utterance.eventType}")
                listOf(StopSpeaking(StopReason.PREEMPTED)) + speak(utterance, nowMs)
            }
            else -> {
                queue(utterance, nowMs, behind = current.utterance.eventType)
                emptyList()
            }
        }
    }

    /** 모션 등급이 바뀌었다. `FULL` 에서 벗어나면 떠 있던 말풍선을 지운다(말하는 중인 한 문장은 끝까지 허용). */
    fun onOutput(tier: MotionTier, @Suppress("UNUSED_PARAMETER") nowMs: Long): List<SpeechCommand> {
        val wasFull = tierNow == MotionTier.FULL
        tierNow = tier
        if (wasFull && tier != MotionTier.FULL && bubbleVisible) {
            bubbleVisible = false
            return listOf(HideBubble)
        }
        return emptyList()
    }

    /** 말하기가 끝났다(정상, 중지, 실패 모두). 이미 끊긴 이전 말의 보고([id] 불일치)는 무시한다. */
    fun onSpeechEnded(id: Long, nowMs: Long): List<SpeechCommand> {
        if (speaking?.id != id) return emptyList()
        speaking = null
        val p = pending ?: return emptyList()
        pending = null
        if (nowMs - p.queuedAtMs > config.queueMaxAgeMs) { // 오래된 말은 버린다
            log("skip event=${p.utterance.eventType} reason=queue-expired waitedMs=${nowMs - p.queuedAtMs}")
            return emptyList()
        }
        return speak(p.utterance, nowMs)
    }

    /** 말풍선 표시 시간이 지나 호출자가 지웠다. */
    fun onBubbleExpired() {
        bubbleVisible = false
    }

    /** 새 세션(모드 전환, 화면 재시작). 모든 상태를 비우고 떠 있는 것을 정리하는 지시를 돌려준다. */
    fun reset(): List<SpeechCommand> {
        val out = buildList {
            if (bubbleVisible) add(HideBubble)
            if (speaking != null) add(StopSpeaking(StopReason.RESET))
        }
        lastSpokenAt.clear()
        lastVariant.clear()
        speaking = null
        pending = null
        bubbleVisible = false
        tripEndSpoken = false
        tierNow = MotionTier.STATIC
        return out
    }

    private fun queue(utterance: Utterance, nowMs: Long, behind: EventType) {
        val p = pending
        if (p == null || utterance.priority.rank >= p.utterance.priority.rank) {
            if (p != null) log("skip event=${p.utterance.eventType} reason=replaced-in-queue by=${utterance.eventType}")
            pending = Pending(utterance, nowMs)
            log("queued event=${utterance.eventType} behind=$behind")
        } else {
            log("skip event=${utterance.eventType} reason=lower-than-queued queued=${p.utterance.eventType}")
        }
    }

    private fun speak(utterance: Utterance, nowMs: Long): List<SpeechCommand> {
        val type = utterance.eventType
        val index = catalog.pick(type, lastVariant[type])
        val line = catalog.variants(type)[index]
        val full = tierNow == MotionTier.FULL
        val cmd = Speak(
            id = nextId++,
            utterance = utterance.copy(text = line.text, shortText = line.shortText),
            variantIndex = index,
            showBubble = full,
            useShortText = !full,
        )
        lastSpokenAt[type] = nowMs
        lastVariant[type] = index
        if (type == EventType.TRIP_END) tripEndSpoken = true
        speaking = cmd
        bubbleVisible = full
        return listOf(cmd)
    }
}
