package com.example.dashpet.engine

/** 같은 종류의 이벤트를 재발 간격 안에서 한 번만 인정하는 장치. 시간은 호출자가 넘기는 값만 쓴다. */
internal class Cooldown {
    private val lastMs = HashMap<EventType, Long>()

    /** 간격이 지났으면 [nowMs] 를 기록하고 true. 간격 안이면 false. */
    fun tryAcquire(type: EventType, nowMs: Long, intervalMs: Long): Boolean {
        val last = lastMs[type]
        if (last != null && nowMs - last < intervalMs) return false
        lastMs[type] = nowMs
        return true
    }
}
