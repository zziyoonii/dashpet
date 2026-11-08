package com.example.dashpet.engine

/** 펫 감정. [priority] 가 높을수록 낮은 감정을 끊고 들어온다. */
enum class Mood(val priority: Int) {
    IDLE(0),
    AMBIENT(1),
    HAPPY(2),
    SQUASHED(3),
    DIZZY(4),
    STARTLED(5),
}

/** [untilMs] 는 이 시각에 배경 상태(없으면 IDLE)로 복귀한다는 뜻이며 복귀 시각이 없으면 null. */
data class PetState(
    val mood: Mood,
    val intensity: Float,
    val direction: Direction4?,
    val untilMs: Long?,
) {
    companion object {
        val IDLE = PetState(Mood.IDLE, 0f, null, null)
    }
}
