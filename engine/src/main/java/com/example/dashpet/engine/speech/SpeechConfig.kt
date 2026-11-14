package com.example.dashpet.engine.speech

/**
 * 말하기 설정 한 곳. 아래 값은 모두 **측정하지 않은 초안**이다 `[확인 필요]`(specs/004 research R5~R7).
 * 값을 바꿀 때는 이 파일만 고친다.
 */
data class SpeechConfig(
    /** 같은 이벤트 종류를 다시 말하기까지의 간격(ms). 우선순위별 `[확인 필요]`. */
    val cooldownMs: Map<SpeechPriority, Long> = mapOf(
        SpeechPriority.DANGER to 10_000L,
        SpeechPriority.WARN to 8_000L,
        SpeechPriority.PRAISE to 15_000L,
        SpeechPriority.INFO to 0L,
    ),
    /** 대기 중인 말을 버리는 한도(ms) `[확인 필요]`. */
    val queueMaxAgeMs: Long = 2_000L,
    /** 말풍선이 떠 있는 시간(ms), 제한 없음일 때만 `[확인 필요]`. */
    val bubbleDurationMs: Long = 4_000L,
    /** 제한 중 한 문장의 글자 수 상한 `[확인 필요]`. */
    val restrictedMaxChars: Int = 20,
    /** 음성 앞 무음(ms). AGENTS.md 7절의 150~200 ms 안에서 고른 초안 `[확인 필요]`. */
    val preSilenceMs: Int = 180,
    /** 재생이 끝나지 않을 때 스스로 정리하는 시간(ms) `[확인 필요]`. */
    val speakWatchdogMs: Long = 8_000L,
) {
    init {
        require(SpeechPriority.entries.all { cooldownMs[it]?.let { v -> v >= 0 } == true }) { "cooldownMs must have a non-negative value for every priority" }
        require(queueMaxAgeMs >= 0) { "queueMaxAgeMs must be >= 0" }
        require(bubbleDurationMs >= 0) { "bubbleDurationMs must be >= 0" }
        require(restrictedMaxChars > 0) { "restrictedMaxChars must be > 0" }
        require(preSilenceMs in 150..200) { "preSilenceMs must be in 150..200 (AGENTS.md 7): $preSilenceMs" }
        require(speakWatchdogMs > 0) { "speakWatchdogMs must be > 0" }
    }

    fun cooldownFor(priority: SpeechPriority): Long = cooldownMs.getValue(priority)
}
