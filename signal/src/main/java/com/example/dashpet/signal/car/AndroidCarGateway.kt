package com.example.dashpet.signal.car

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

/**
 * 실제 차량 서비스(`android.car`)에 접근하는 어댑터. 이 모듈에서 `android.car` 를 만지는 유일한 파일이다.
 * JVM 에서는 검증할 수 없어 에뮬레이터 확인(quickstart)으로 대신한다.
 *
 * 수명: [events] 를 수집하는 동안에만 `Car` 를 만들고 연결한다. 수집이 끝나면 리스너를 모두 해제하고 `disconnect()` 한다.
 * 해제한 `Car` 는 다시 쓰지 않고, 다시 수집하면 새로 만든다. 콜백 스레드는 문서에 명시가 없어(`[확인 필요]`)
 * 이 세션 전용 스레드에서 받고 `trySend` 로 넘긴다(순서와 시각은 [CarSignalSource] 가 책임진다).
 *
 * 각 신호는 서로 독립이다. 한 신호가 지원되지 않거나 권한이 없거나 읽다가 실패해도 나머지는 계속 동작하고,
 * 이유는 [CarEvent.AvailabilityChanged] 로 알린다. 지원 여부는 차종명이 아니라 속성 설정의 존재로 판단한다.
 */
// CarPropertyValue.getStatus() 는 deprecated 로 표시돼 있지만 이 API 레벨(34)에서 상태를 알 수 있는 유일한 방법이다(스텁에서 확인).
@Suppress("DEPRECATION")
internal class AndroidCarGateway(
    private val context: Context,
    private val speedRate: SpeedRate = SpeedRate.UI,
) : CarGateway {

    override fun events(): Flow<CarEvent> = callbackFlow {
        val thread = HandlerThread("CarSignal").apply { start() }
        val handler = Handler(thread.looper)
        var uxr: CarUxRestrictionsManager? = null
        var props: CarPropertyManager? = null
        var propsCallback: CarPropertyManager.CarPropertyEventCallback? = null

        fun unregisterAll() {
            runCatching { uxr?.unregisterListener() } // 인자 없는 해제(스텁에서 확인)
            propsCallback?.let { cb -> runCatching { props?.unregisterCallback(cb) } }
            uxr = null
            props = null
            propsCallback = null
        }

        fun onReady(car: Car) {
            trySend(CarEvent.Ready)
            // 순서: 속도, 기어, 제한. 각 신호는 리스너를 먼저 등록한 뒤 현재 값을 읽어 그 사이의 변화를 놓치지 않는다
            // (같은 값은 소스가 걸러낸다).
            val manager = runCatching { car.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager }.getOrNull()
            props = manager
            if (manager != null) {
                val callback = propertyCallback()
                propsCallback = callback
                startSpeed(manager, callback)
                startGear(manager, callback)
            } else {
                report(CarSignal.SPEED, SignalAvailability.ERROR)
                report(CarSignal.GEAR, SignalAvailability.ERROR)
            }
            uxr = startRestriction(car)
        }

        val car: Car? = try {
            Car.createCar(context, handler, Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT) { c, ready ->
                if (ready) {
                    onReady(c)
                } else {
                    unregisterAll() // 서비스가 끊기면 관리자 객체는 쓸 수 없다. 복구 때 새로 얻는다.
                    trySend(CarEvent.Lost)
                }
            }
        } catch (e: Exception) {
            null
        }
        if (car == null) {
            for (signal in CarSignal.entries) report(signal, SignalAvailability.ERROR)
        }

        awaitClose {
            unregisterAll()
            runCatching { car?.disconnect() }
            thread.quitSafely()
        }
    }.buffer(Channel.UNLIMITED)

    // --- 속도 ---

    private fun ProducerScope<CarEvent>.startSpeed(manager: CarPropertyManager, callback: CarPropertyManager.CarPropertyEventCallback) {
        // CAR_SPEED 는 런타임 권한이다. 거부 상태면 속도만 건너뛰고 나머지는 계속한다(FR-010).
        // 권한 확인이 설정 확인보다 먼저여야 한다: 플랫폼은 권한이 없는 앱에게 속도 속성 설정을 아예 숨기므로(null),
        // 순서가 바뀌면 권한 거부가 "지원 안 함"으로 잘못 보인다(T023 에서 에뮬레이터로 확인).
        if (context.checkSelfPermission(Car.PERMISSION_SPEED) != PackageManager.PERMISSION_GRANTED) {
            report(CarSignal.SPEED, SignalAvailability.NO_PERMISSION)
            return
        }
        if (manager.getCarPropertyConfig(VehiclePropertyIds.PERF_VEHICLE_SPEED) == null) {
            report(CarSignal.SPEED, SignalAvailability.UNSUPPORTED)
            return
        }
        try {
            manager.registerCallback(callback, VehiclePropertyIds.PERF_VEHICLE_SPEED, speedRate.toSensorRate())
            val value = manager.getProperty(Float::class.javaObjectType, VehiclePropertyIds.PERF_VEHICLE_SPEED, GLOBAL_AREA)
            if (value != null && value.status == CarPropertyValue.STATUS_AVAILABLE) {
                value.value?.let { trySend(CarEvent.SpeedChanged(it)) }
            }
            report(CarSignal.SPEED, SignalAvailability.OK)
        } catch (e: Exception) {
            report(CarSignal.SPEED, e.toAvailability())
        }
    }

    // --- 기어 ---

    private fun ProducerScope<CarEvent>.startGear(manager: CarPropertyManager, callback: CarPropertyManager.CarPropertyEventCallback) {
        if (manager.getCarPropertyConfig(VehiclePropertyIds.GEAR_SELECTION) == null) {
            report(CarSignal.GEAR, SignalAvailability.UNSUPPORTED)
            return
        }
        try {
            manager.registerCallback(callback, VehiclePropertyIds.GEAR_SELECTION, CarPropertyManager.SENSOR_RATE_ONCHANGE)
            val value = manager.getProperty(Int::class.javaObjectType, VehiclePropertyIds.GEAR_SELECTION, GLOBAL_AREA)
            if (value != null && value.status == CarPropertyValue.STATUS_AVAILABLE) {
                value.value?.let { trySend(CarEvent.GearChanged(it)) }
            }
            report(CarSignal.GEAR, SignalAvailability.OK)
        } catch (e: Exception) {
            report(CarSignal.GEAR, e.toAvailability())
        }
    }

    private fun ProducerScope<CarEvent>.propertyCallback() = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            if (value.status != CarPropertyValue.STATUS_AVAILABLE) return
            when (value.propertyId) {
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> (value.value as? Float)?.let { trySend(CarEvent.SpeedChanged(it)) }
                VehiclePropertyIds.GEAR_SELECTION -> (value.value as? Int)?.let { trySend(CarEvent.GearChanged(it)) }
            }
        }

        override fun onErrorEvent(propertyId: Int, zone: Int) {
            when (propertyId) {
                VehiclePropertyIds.PERF_VEHICLE_SPEED -> report(CarSignal.SPEED, SignalAvailability.ERROR)
                VehiclePropertyIds.GEAR_SELECTION -> report(CarSignal.GEAR, SignalAvailability.ERROR)
            }
        }
    }

    // --- 주행 제한 ---

    /** 제한 상태를 읽고 변화를 구독한다. 성공하면 관리자 객체를 돌려줘 해제할 수 있게 한다. */
    private fun ProducerScope<CarEvent>.startRestriction(car: Car): CarUxRestrictionsManager? = try {
        val manager = car.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
        manager.registerListener { restrictions -> trySend(restrictions.toEvent()) }
        manager.currentCarUxRestrictions?.let { trySend(it.toEvent()) }
        report(CarSignal.RESTRICTION, SignalAvailability.OK)
        manager
    } catch (e: Exception) {
        report(CarSignal.RESTRICTION, e.toAvailability())
        null
    }

    // --- 공통 ---

    private fun ProducerScope<CarEvent>.report(signal: CarSignal, availability: SignalAvailability) {
        trySend(CarEvent.AvailabilityChanged(signal, availability))
    }

    private fun Exception.toAvailability() =
        if (this is SecurityException) SignalAvailability.NO_PERMISSION else SignalAvailability.ERROR

    private fun SpeedRate.toSensorRate(): Float = when (this) {
        SpeedRate.ONCHANGE -> CarPropertyManager.SENSOR_RATE_ONCHANGE
        SpeedRate.NORMAL -> CarPropertyManager.SENSOR_RATE_NORMAL
        SpeedRate.UI -> CarPropertyManager.SENSOR_RATE_UI
        SpeedRate.FAST -> CarPropertyManager.SENSOR_RATE_FAST
    }

    private fun CarUxRestrictions.toEvent() =
        CarEvent.RestrictionChanged(requiresDo = isRequiresDistractionOptimization, activeRestrictions = activeRestrictions)

    private companion object {
        /** 전역 영역(`VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL`). 속도와 기어는 영역이 없다. */
        const val GLOBAL_AREA = 0
    }
}
