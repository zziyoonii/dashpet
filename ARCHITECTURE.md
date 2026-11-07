# DashPet 아키텍처

Pleos Connect(AAOS) 위에서 도는 Activity 기반 멀티 윈도우 앱. 캐릭터는 Compose Canvas 로 직접 그린다.

## 모듈과 의존 방향

```
:app  ->  :pet  ->  :engine  ->  :signal
```

| 모듈 | 패키지 | 역할 |
|-|-|-|
| `:app` | `com.example.dashpet` | Application. MainActivity, `ui/`(theme, screens). 매니페스트, 주행 규제/Back/로터리 설정 |
| `:pet` | `...dashpet.pet` | Compose 캐릭터(PetView, PetController). `:engine` 의 상태를 그린다 |
| `:engine` | `...dashpet.engine` | 이벤트 엔진, 상태 머신, 주행 상태 게이트. JVM 단위 테스트 대상 |
| `:signal` | `...dashpet.signal` | RawSignal, VehicleSignalSource, MockSignalSource, PleosSignalSource |

의존은 한 방향만 허용한다. 역방향 참조와 순환은 금지.

## 규칙

- Pleos SDK(`ai.pleos.playground:*`) 의존성과 호출은 `:signal` 에만 둔다.
- 주행 상태(주행 규제) 판단은 `:engine` 에서만 한다. `:app`/`:pet` 은 판단 결과를 받아 UI 만 바꾼다.
- `:pet` 은 SDK 와 `:signal` 의 구현체를 직접 보지 않는다. 필요한 값은 `:engine` 을 거친다.
- `:engine` 은 Android 프레임워크 의존을 최소화한다(`:signal` 이 Android library 라 모듈 타입은 Android library).
- `pleos.car.permission.*` 는 승인 범위 확인 후 필요한 것만 `:app` 매니페스트에 추가한다.
- 비동기 결과는 Flow(kotlinx-coroutines)로 전달하고, UI 갱신은 메인 스레드에서 한다.
