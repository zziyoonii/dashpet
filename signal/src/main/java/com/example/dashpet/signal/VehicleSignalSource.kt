package com.example.dashpet.signal

import kotlinx.coroutines.flow.Flow

/** 엔진이 신호를 받는 유일한 경로. 구현체가 목업인지 실차인지 엔진은 알지 못한다. */
interface VehicleSignalSource {
    /** 시간순 신호 스트림. 같은 스트림 안에서 timestampMs 는 감소하지 않는다. */
    val signals: Flow<RawSignal>

    fun start()

    fun stop()
}
