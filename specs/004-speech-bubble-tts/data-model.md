# Data Model: 말풍선과 음성(TTS)

**Feature**: [spec.md](./spec.md) | **Research**: [research.md](./research.md)

기존 타입(`DrivingEvent`, `EventType`, `PetOutput`, `MotionTier`)은 그대로 쓴다. 새 타입만 적는다. 모듈은 `:engine`(문장과 규칙, 순수 로직), `:signal`(음성 출력과 오디오 포커스), `:app`(말풍선 표시와 연결)이다. `:pet` 은 바뀌지 않는다.
시그니처 전체가 아니라 필드와 규칙만 적는다. 동작 약속은 [contracts/speech.md](./contracts/speech.md).

## `:engine` — 문장과 말하기 규칙 (`com.example.dashpet.engine.speech`)

### SpeechPriority (enum, 큰 값이 우선)

`INFO`(0), `PRAISE`(1), `WARN`(2), `DANGER`(3).

| 우선순위 | 이벤트 |
|-|-|
| DANGER | `COLLISION_RISK`, `LANE_DEPARTURE` |
| WARN | `HARD_ACCEL`, `HARD_BRAKE`, `HARD_CORNER`, `NO_SIGNAL_LANE_CHANGE` |
| PRAISE | `GOOD_LANE_CHANGE`, `SEATBELT_OK` |
| INFO | `TRIP_START`, `TRIP_END` |

이벤트 10종 모두 정확히 하나의 우선순위를 가진다(누락은 테스트가 잡는다).

### Utterance (불변 값, 한 번에 전하는 한 문장)

| 필드 | 타입 | 설명 |
|-|-|-|
| `eventType` | `EventType` | 원인 이벤트 |
| `priority` | `SpeechPriority` | 위 표 |
| `text` | `String` | 제한 없음일 때 쓰는 문장(말풍선과 음성 공통) |
| `shortText` | `String` | 제한 중(음성만)에 쓰는 더 짧은 한 문장 |
| `atMs` | `Long` | 이벤트 시각(엔진 시각) |

`text` 와 `shortText` 는 항상 고정 목록의 문장이다. 개인정보나 점수 숫자를 넣지 않는다(FR-019, 스펙 가정).

### SpeechCatalog (고정 문장 목록)

이벤트마다 `variants: List<SpeechLine>`(2개). `SpeechLine(text, shortText)`. `pick(type, lastVariantIndex)` 는 직전과 다른 변형 번호를 돌려준다(결정적, 난수 없음).
검사(테스트): 모든 `text` 는 120자, 24어절, 한 줄 80자, 3줄 이하. 모든 `shortText` 는 `SpeechConfig.restrictedMaxChars` 이하이고 한 문장이다. 자리 표시자(`%`, `{`)가 없다.

### SpeechConfig (설정, 한 곳)

모든 값은 `[확인 필요]` 초안이다(research R7, R5, R6).

| 필드 | 초안 | 설명 |
|-|-|-|
| `cooldownMs` | DANGER 10000, WARN 8000, PRAISE 15000, INFO 0 | 같은 이벤트 종류를 다시 말하기까지의 간격(우선순위별) |
| `queueMaxAgeMs` | 2000 | 대기 중인 말을 버리는 한도 |
| `bubbleDurationMs` | 4000 | 말풍선이 떠 있는 시간(`FULL` 일 때) |
| `restrictedMaxChars` | 20 | 제한 중 한 문장의 글자 수 상한 |
| `preSilenceMs` | 180 | 선행 무음. `require(in 150..200)` |
| `speakWatchdogMs` | 8000 | 재생이 끝나지 않을 때 스스로 정리하는 시간 |

값 검증: 음수 불가, `preSilenceMs` 는 150~200.

### SpeechCommand (sealed, 규칙이 내리는 지시)

| 하위 타입 | 필드 | 설명 |
|-|-|-|
| `Speak` | `utterance`, `variantIndex`, `showBubble: Boolean`, `useShortText: Boolean` | 말하라. `showBubble` 은 `tier == FULL` 일 때만 true, `useShortText` 는 그 반대 |
| `StopSpeaking` | 이유(`PREEMPTED`, `RESTRICTED_ENTERED` 등) | 재생 중인 말을 멈춰라 |
| `HideBubble` | - | 떠 있는 말풍선을 지워라 |

### SpeechDirector (순수 상태 머신, Android 없음)

입력 세 가지를 시각과 함께 받아 `List<SpeechCommand>` 를 돌려준다. 시계를 읽지 않는다.

| 입력 | 처리 |
|-|-|
| `onEvent(event, tier, nowMs)` | 쿨다운 확인 → (말하는 중이면) 우선순위 비교 → 지시 만들기 |
| `onOutput(tier, nowMs)` | `FULL → STATIC` 이면 `HideBubble`. 대기 슬롯의 만료 확인 |
| `onSpeechState(state, nowMs)` | 말하기 시작/끝/오류에 따라 말하는 중 여부 갱신, 대기 슬롯이 있고 유효하면 이어서 `Speak` |

내부 상태(모두 세션 안): 종류별 마지막으로 말한 시각, 종류별 직전 변형 번호, 말하는 중인 `Utterance?`, 대기 슬롯 1칸(`Utterance?` + 넣은 시각), 떠 있는 말풍선 여부. 새 세션(`reset()`)이면 모두 비운다(모드 전환과 화면 재시작, FR-017).

## `:signal` — 음성 출력 (`com.example.dashpet.signal.voice`)

### SpeechState (sealed, 출력 쪽에서 올라오는 사건)

| 하위 타입 | 설명 |
|-|-|
| `Started(id)` | 재생 시작 |
| `Finished(id)` | 정상 완료 |
| `Stopped(id)` | 중지됨(우리가 멈췄거나 포커스를 잃어서) |
| `Failed(id, reason)` | 재생 실패. `reason`: `NO_FOCUS`, `ENGINE_ERROR`, `TIMEOUT`, `BUSY`(이미 재생 중일 때 호출됨) |

### VoiceAvailability (enum)

`UNKNOWN`(초기화 전), `READY`, `UNAVAILABLE`(미지원, 권한 없음, 초기화 실패, 서비스 없음). 한번 `UNAVAILABLE` 이 되면 그 세션에서는 다시 시도하지 않는다(앱 재시작 또는 화면 복귀 때만 재초기화).

### SpeechOutput (인터페이스, 음성 출력 한 겹)

`speak(id, text)`, `stop()`, `release()` 와 `states: Flow<SpeechState>`, `availability: StateFlow<VoiceAvailability>`. 구현은 `PleosSpeechOutput`(실제 SDK, `internal`)과 테스트용 `FakeSpeechOutput`(`src/test`). 상세는 contracts.

### AudioFocusGateway (인터페이스, 오디오 포커스 한 겹)

`request(): Boolean`, `abandon()` 과 `changes: Flow<FocusChange>`. `FocusChange`: `GAIN`, `LOSS`, `LOSS_TRANSIENT`, `LOSS_TRANSIENT_CAN_DUCK`. 구현은 `AndroidAudioFocusGateway`(`AudioManager`)와 테스트용 가짜. 요청 횟수와 반납 횟수를 센다(SC-009 검증용).

### VoiceConfig (설정)

`audioUsage`(기본 `USAGE_NOTIFICATION`, `[확인 필요]`), `contentType`(`CONTENT_TYPE_SPEECH`), `focusGain`(`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`), `locale`(`ko-KR`), `ttsMode`(`ON_DEVICE`), `preSilenceMs`(`SpeechConfig` 와 같은 값을 넘겨 받는다). Android 상수는 `:signal` 안에서만 쓴다.

## `:app` — 말풍선과 연결

### BubbleState (불변 값)

`text: String`, `eventType: EventType`. 없으면 null. 표시 중에는 문장이 바뀌지 않고, 새 말은 이전 것을 대체한다.

### VoiceSettings

`voiceEnabled: Boolean`(기본 true). `SharedPreferences` 한 키. 읽기와 쓰기만 하는 얇은 래퍼(테스트에서는 메모리 구현).

### SpeechCoordinator (연결 클래스, `:app` 안)

`DrivingEngine.events`, `DrivingEngine.output` 을 구독해 `SpeechDirector` 에 넣고, 지시를 `SpeechOutput` 과 `bubble: StateFlow<BubbleState?>` 로 옮긴다. `VoiceSettings.voiceEnabled == false` 이면 `Speak` 의 음성 부분을 생략한다(말풍선은 `FULL` 에서만). 수명: 화면에 보이는 동안(`onForeground`~`onBackground`)과 `DemoViewModel` 의 세션 단위(모드 전환 시 `reset`).

## 상태 전이

```text
화면에 보임 ──▶ 음성 초기화(UNKNOWN → READY | UNAVAILABLE)
이벤트 발생 ──▶ 쿨다운 통과? ──▶ 말하는 중? ──▶ (없음) 말하기 | (더 높은 우선순위) 멈추고 말하기 | (같거나 낮음) 대기 슬롯
말하기 ──▶ 포커스 요청 ──▶ 선행 무음 ──▶ speak ──▶ Finished | Stopped | Failed ──▶ 포커스 반납
tier FULL → STATIC ──▶ 말풍선 즉시 지움 (음성은 한 문장이라 끝까지 허용)
화면에서 사라짐 ──▶ 재생 중지 ──▶ 포커스 반납 ──▶ SDK release
모드 전환 ──▶ reset(말풍선, 대기, 쿨다운 모두 비움)
```

## 검증 규칙 요약

- 말풍선 글자는 `tier == FULL` 일 때만 존재한다. `STATIC` 으로 바뀌는 순간 지운다.
- 한 번에 재생 중인 말은 최대 1개이고, 대기 슬롯은 최대 1개다.
- 같은 이벤트 종류는 쿨다운 안에서 말하지 않고, 같은 변형을 연달아 쓰지 않는다.
- 포커스 요청 횟수와 반납 횟수는 세션이 끝나면 같다.
- 음성이 `UNAVAILABLE` 이어도 `Speak` 지시의 말풍선 부분은 정상 처리한다(`FULL` 일 때).
