package com.example.dashpet.signal.car

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.callbackFlow

/**
 * Pleos FusedLocation SDK 에서 **방위각(heading)만** 읽어 급커브 판정에 쓴다(specs/005). 이 파일이 FusedLocation SDK 를 만지는 유일한 곳이다.
 * 위도, 경도, 고도는 콜백 안에서 읽지 않고 저장하거나 로그에 남기지 않으며 [CarEvent] 에도 담지 않는다(헌장 V).
 *
 * 수명: 생성 -> `isSupported()` -> `initialize()` -> 리스너 등록 ... 수집이 끝나면 `unregisterFusedLocationCallback` 과 `release()` 를 모두 한다
 * (둘은 서로 대신할 수 없다). `FUSED_LOCATION` 은 위치 권한(dangerous)이라 허용되지 않았으면 읽지 않고 [SignalAvailability.NO_PERMISSION] 으로 알린다.
 * `[확인 필요]` heading 의 단위, 기준 방향, 갱신 주기는 문서에 없다.
 */
@SuppressLint("MissingPermission") // 아래에서 권한을 확인한 뒤에만 호출한다.
internal class FusedHeadingGateway(private val context: Context) : CarGateway {

    override fun events(): Flow<CarEvent> = callbackFlow {
        var fused: FusedLocation? = null
        var listener: FusedLocationListener? = null

        if (context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) {
            trySend(CarEvent.AvailabilityChanged(CarSignal.HEADING, SignalAvailability.NO_PERMISSION))
        } else {
            try {
                val f = FusedLocation(context.applicationContext)
                fused = f
                if (!f.isSupported()) {
                    trySend(CarEvent.AvailabilityChanged(CarSignal.HEADING, SignalAvailability.UNSUPPORTED))
                } else {
                    f.initialize()
                    val l = object : FusedLocationListener {
                        override fun onChanged(location: LocationData?) {
                            // 방위각 한 값만 꺼내고 나머지(좌표)는 읽지 않는다.
                            if (location != null) trySend(CarEvent.HeadingChanged(location.heading))
                        }

                        override fun onError(exception: Exception) {
                            trySend(CarEvent.AvailabilityChanged(CarSignal.HEADING, SignalAvailability.ERROR))
                        }
                    }
                    listener = l
                    f.registerFusedLocationCallback(l)
                    trySend(CarEvent.AvailabilityChanged(CarSignal.HEADING, SignalAvailability.OK))
                }
            } catch (e: SecurityException) {
                trySend(CarEvent.AvailabilityChanged(CarSignal.HEADING, SignalAvailability.NO_PERMISSION))
            } catch (e: Exception) {
                trySend(CarEvent.AvailabilityChanged(CarSignal.HEADING, SignalAvailability.ERROR))
            }
        }

        awaitClose {
            // 순서: 리스너 해제 -> release. 해제한 인스턴스는 다시 쓰지 않는다.
            listener?.let { l -> runCatching { fused?.unregisterFusedLocationCallback(l) } }
            runCatching { fused?.release() }
        }
    }.buffer(Channel.UNLIMITED)

    companion object {
        /** 방위각을 읽는 데 필요한 런타임 권한(위치 권한 그룹). `:app` 이 SDK 를 보지 않도록 문자열로 둔다. */
        const val PERMISSION = "pleos.car.permission.FUSED_LOCATION"
    }
}
