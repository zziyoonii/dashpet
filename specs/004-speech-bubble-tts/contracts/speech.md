# Contract: 말풍선과 음성

**Feature**: [spec.md](../spec.md) | **Data model**: [data-model.md](../data-model.md)

이 기능이 만드는 내부 인터페이스의 약속이다. 외부에 공개하는 API 는 없다. 시그니처 전체가 아니라 지켜야 할 동작을 적는다.

## 1. 문장 목록 (이벤트 10종 × 변형 2)

말풍선과 음성은 같은 `text` 를 쓴다(제한 없음). 제한 중(음성만)은 `shortText` 를 쓴다. 말투는 차분한 존댓말 한 문장 또는 짧은 두 문장이다. 점수, 이름, 위치, 숫자를 넣지 않는다. 모든 문장은 `SpeechCatalogTest` 가 길이 한도(120자, 24어절, 한 줄 80자, 3줄)와 `shortText` 의 `restrictedMaxChars`(초안 20자) 이하를 검사한다.

| 이벤트 | 우선순위 | 변형 | `text` (제한 없음) | `shortText` (제한 중, 음성만) |
|-|-|-|-|-|
| `TRIP_START` | INFO | A | 출발해요. 오늘도 안전하게 가요. | 안전하게 가요. |
| | | B | 시동 걸었어요. 천천히 출발해요. | 천천히 출발해요. |
| `SEATBELT_OK` | PRAISE | A | 안전벨트를 확인했어요. | 벨트 확인했어요. |
| | | B | 벨트가 잘 채워졌어요. | 벨트 좋아요. |
| `HARD_ACCEL` | WARN | A | 출발이 조금 급했어요. 부드럽게 가요. | 부드럽게 가요. |
| | | B | 가속이 빨랐어요. 천천히 밟아요. | 천천히 밟아요. |
| `HARD_BRAKE` | WARN | A | 급하게 멈췄어요. 거리를 더 두어요. | 거리를 두어요. |
| | | B | 갑자기 멈췄어요. 여유를 가져요. | 여유를 가져요. |
| `HARD_CORNER` | WARN | A | 커브가 급했어요. 천천히 돌아요. | 천천히 돌아요. |
| | | B | 회전이 빨랐어요. 속도를 줄여요. | 속도를 줄여요. |
| `LANE_DEPARTURE` | DANGER | A | 차선을 벗어났어요. 앞을 봐 주세요. | 차선을 확인해요. |
| | | B | 차선이 흔들려요. 중앙을 맞춰요. | 중앙을 맞춰요. |
| `COLLISION_RISK` | DANGER | A | 앞차와 가까워요. 거리를 두세요. | 거리를 두세요. |
| | | B | 충돌 위험이 있어요. 속도를 줄이세요. | 속도를 줄이세요. |
| `GOOD_LANE_CHANGE` | PRAISE | A | 차선 변경이 깔끔했어요. | 깔끔했어요. |
| | | B | 좋은 차선 변경이에요. | 차선 변경 좋아요. |
| `NO_SIGNAL_LANE_CHANGE` | WARN | A | 깜빡이를 켜고 차선을 바꿔요. | 깜빡이를 켜요. |
| | | B | 차선 변경 전에 깜빡이를 켜요. | 깜빡이를 먼저 켜요. |
| `TRIP_END` | INFO | A | 도착했어요. 수고하셨어요. | 도착했어요. |
| | | B | 무사히 도착했어요. 고마워요. | 무사히 도착했어요. |

문장은 초안이다. 말투와 표현은 사용자 확인 후 바꿀 수 있고, 바꿔도 테스트(길이, 자리 표시자 없음, 이벤트 10종 모두 존재)를 통과해야 한다.

## 2. `SpeechDirector` 약속 (순수 로직)

| 순서 | 사건 | 결과 |
|-|-|-|
| 1 | 이벤트 도착, 종류별 쿨다운 안 | 지시 없음. 쿨다운은 우선순위별 간격(`SpeechConfig.cooldownMs`)을 쓴다. 이 이벤트는 이후에도 말하지 않는다(나중에 다시 꺼내지 않음) |
| 2 | 이벤트 도착, 말하는 중 아님 | `Speak`. `tier == FULL` 이면 `showBubble=true, useShortText=false`, 아니면 `showBubble=false, useShortText=true` |
| 3 | 이벤트 도착, 말하는 중, 새 이벤트의 우선순위가 더 높음 | `StopSpeaking(PREEMPTED)` 뒤에 `Speak` |
| 4 | 이벤트 도착, 말하는 중, 같거나 낮은 우선순위 | 대기 슬롯에 넣는다. 이미 슬롯이 있으면 더 높은 것(같으면 더 최근 것)만 남긴다 |
| 5 | 말하기 끝남(`Finished`, `Stopped`, `Failed`) | 대기 슬롯이 있고 `queueMaxAgeMs` 안이면 `Speak`. 넘었으면 버린다 |
| 6 | 출력이 `FULL → STATIC` | `HideBubble`(떠 있을 때만). 말하는 중인 한 문장은 끝까지 허용 |
| 7 | 출력이 `STATIC → FULL` | 지시 없음(이미 지나간 이벤트의 말풍선을 되살리지 않는다) |
| 8 | 같은 이벤트 종류를 다시 말함 | 직전과 다른 변형을 쓴다 |
| 9 | `reset()` | 쿨다운, 대기 슬롯, 직전 변형, 말하는 중 여부를 모두 비운다. `HideBubble` 과 `StopSpeaking` 을 돌려준다 |

- `showBubble` 은 `tier == FULL` 일 때만 true 다. 판단은 `PetOutput.tier` 값을 그대로 쓰고 `RestrictionState` 를 다시 해석하지 않는다(헌장 I, II).
- 시각은 호출자가 넘기는 값만 쓴다. 같은 입력에는 항상 같은 지시가 나온다(결정적, 난수 없음).
- 말풍선이 뜬 뒤 `bubbleDurationMs` 가 지나면 사라지는 것은 `SpeechCoordinator` 의 `delay` 가 맡는다(규칙은 시간 값만 알려 준다).

## 3. `SpeechOutput` 약속 (음성 출력 한 겹)

- `speak(id, text)`: 한 번에 한 문장만 받는다. 이미 재생 중일 때 부르면 `Failed(BUSY)` 를 올리고 아무것도 바꾸지 않는다. 앞의 말을 끊는 것은 호출자가 먼저 `stop()` 을 부르는 방식으로 하며(우선순위 판단은 규칙이 한다) 출력 쪽이 알아서 끊지 않는다.
- 순서: ① 가용성 확인(`READY` 아니면 `Failed(ENGINE_ERROR)` 한 번) → ② 오디오 포커스 요청(`AudioFocusGateway.request`, 실패면 `Failed(NO_FOCUS)`) → ③ 선행 무음(`preSilenceMs`, 같은 `AudioAttributes`) → ④ SDK `speak` → ⑤ `onStart` 에서 `Started`, `onDone` 에서 `Finished`.
- 어느 경로로 끝나든(`Finished`, `Stopped`, `Failed`) 포커스는 **정확히 한 번** 반납한다. `speakWatchdogMs` 안에 끝나지 않으면 `stop()` 후 `Failed(TIMEOUT)`.
- 포커스 변경 처리: `LOSS`, `LOSS_TRANSIENT`, `LOSS_TRANSIENT_CAN_DUCK` 는 즉시 `stop()` + 반납 + `Stopped`. `LOSS` 뒤 자동 재개 없음. `GAIN` 은 재생 중이 아니면 아무것도 하지 않는다.
- 오류: SDK 오류(`ErrorType` 전부, `else` 분기 포함)는 `Failed(ENGINE_ERROR)` 로 바꿔 올린다. `PERMISSION_DENIED`, `NOT_SUPPORTED_API`, 초기화 실패는 `availability = UNAVAILABLE` 로 만든다. 예외가 밖으로 새지 않는다.
- 해제: `release()` 는 재생 중지 → 리스너 제거 → SDK `release()` → 포커스 반납 순서이고, 해제한 인스턴스를 다시 쓰지 않는다(다시 필요하면 새로 만든다, AGENTS.md 3절).
- 콜백은 어느 스레드에서 와도 채널로 넘기고, 상태 갱신은 한 코루틴에서 한다.

## 4. `SpeechCoordinator` 약속 (`:app`)

| 호출 | 동작 |
|-|-|
| `onForeground()` | 음성 초기화. 이미 초기화돼 있으면 아무것도 하지 않는다 |
| `onBackground()` | 재생 중지, 포커스 반납, SDK 해제, 말풍선 지움 |
| `setVoiceEnabled(on)` | `tier == FULL` 일 때만 반영하고 저장한다. 아니면 무시한다. 끄면 재생 중인 음성을 멈춘다 |
| `reset()` | 모드 전환 때. `SpeechDirector.reset()` 과 같고 말풍선과 대기 중인 음성이 남지 않는다 |

- `voiceEnabled == false` 이면 `Speak` 의 음성 부분만 생략한다. 말풍선은 `FULL` 에서 그대로 보인다.
- 화면에 보이지 않는 동안 이벤트가 와도 말하지 않는다(FR-011).
- 말풍선은 `bubbleDurationMs` 뒤에 지우고, 새 `Speak` 가 오면 이전 것을 즉시 대체한다.
- `engine.events` 는 구독 전 이벤트를 받지 않는다. 구독은 세션 시작과 함께 건다.
- 로그(`Speech` 태그)는 말하기 시작/건너뜀(이유), 포커스 요청/반납/변경, 가용 상태 변화만 한 줄씩 남긴다. 문장 본문은 로그에 남기지 않는다(FR-019, FR-020).

## 5. 말풍선 표시 약속 (`SpeechBubble`)

- 입력은 `BubbleState?`. null 이면 노드를 그리지 않는다(재그리기 없음).
- 애니메이션, 전환, 글자 효과, 점멸, 자동 스크롤이 없다. 나타남과 사라짐은 상태가 바뀌는 즉시다.
- 터치 처리를 두지 않는다(`clickable` 없음). 접근성 설명(`contentDescription`)은 문장 본문과 같다.
- 글자 14 sp 이상, 대비 4.5:1 이상, `maxLines = 3`. 폭은 `widthIn(max)` 로 제한하고 가로와 세로 모두 잘리지 않는다.
- 제한 중(`tier != FULL`)에는 어떤 경로로도 그려지지 않는다(상태 자체가 null).

## 6. manifest 와 의존성 약속

```xml
<uses-permission android:name="pleos.car.permission.TTS_SERVICE" />
```

- 이 하나만 새로 선언한다. 승인 여부는 `[확인 필요]`(research R10). 권한이 없어도 앱은 시작하고 음성 없이 동작한다.
- 의존성: `ai.pleos.playground:TextToSpeech:3.3.1`(`gradle/libs.versions.toml` 에 `pleosTts` 추가, `:signal` 의 `implementation`). `:engine`, `:pet`, `:app` 은 SDK 를 직접 보지 않는다.
- SDK AAR 이 병합하는 `INTERNET` 권한과 `queries` 는 그대로 둔다(research R10).
