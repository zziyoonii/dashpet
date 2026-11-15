package com.example.dashpet.ui.speech

import com.example.dashpet.engine.EventType

/** 떠 있는 말풍선. 없으면 null. 표시 중에는 문장이 바뀌지 않고 새 말이 이전 것을 대체한다. */
data class BubbleState(val text: String, val eventType: EventType)
