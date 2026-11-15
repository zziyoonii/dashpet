package com.example.dashpet.signal.car

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/**
 * 게이트웨이 여러 개를 하나로 합친다. [primary] 는 주행 제한을 알려주는 필수 경로라 실패하면 그대로 실패를 전한다
 * (그러면 [CarSignalSource] 가 제한을 "알 수 없음"으로 되돌린다, 헌장 I).
 * [extras](브레이크, 방향지시등, 안전벨트, 방위각)는 모두 보조 신호라서 하나가 실패해도 나머지는 계속 동작한다.
 * 수집이 끝나면 자식의 수집도 모두 끝난다(각자 리스너 해제와 연결 해제를 한다).
 */
class CompositeCarGateway(
    private val primary: CarGateway,
    private val extras: List<CarGateway>,
) : CarGateway {
    override fun events(): Flow<CarEvent> = channelFlow {
        launch { primary.events().collect { send(it) } }
        extras.forEach { gateway ->
            launch { gateway.events().catch { /* 보조 신호는 없어도 된다 */ }.collect { send(it) } }
        }
    }.buffer(Channel.UNLIMITED)
}
