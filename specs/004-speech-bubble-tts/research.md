# Research: 말풍선과 음성(TTS)

**Feature**: [spec.md](./spec.md) | **Plan**: [plan.md](./plan.md)

확인한 것(근거 포함)과 아직 확인하지 못한 것(`[확인 필요]`)을 나눠 적는다. 확인하지 못한 값은 추측해서 코드에 넣지 않고 설정 한 곳에 두며, 구현 첫 작업(스파이크)에서 에뮬레이터로 확인한다.

## R1. 음성 합성 수단: Pleos TextToSpeech SDK 3.3.1 (`Mode.ON_DEVICE`)

- **Decision**: `ai.pleos.playground:TextToSpeech:3.3.1` 의 `ON_DEVICE` 모드를 쓴다. 코드는 `SpeechOutput` 인터페이스 뒤에 두어 다른 구현으로 바꿀 수 있게 한다.
- **Rationale**: AGENTS.md 2절은 제조사 특화 기능을 Pleos SDK 로 접근하라고 하고 TTS 를 SDK 목록에 둔다. `ON_DEVICE` 는 앱 등록(Client ID/Secret)과 네트워크가 필요 없어 헌장 V(비밀값 비노출)에 맞다. `HYBRID`, `SERVER` 는 `registerApp` 이 필수이고 키를 앱에 넣어야 해서 쓰지 않는다.
- **확인한 것(artifact 직접 확인, 2026-11-12)**: Pleos Maven 저장소에서 `TextToSpeech-3.3.1.aar` 를 받아 `javap` 로 봤다.
  - `ai.pleos.playground.tts.TextToSpeech(Context, Mode)`: `initialize(OnInitializeListener)`, `addEventListener/removeEventListener(EventListener)`, `setLocale(Locale)`, `speak(String, Boolean)`, `stop()`, `release()`, `setAudioAttributes(AudioAttributes)`, `setAudioZoneId(Int)`, `registerApp(...)`, `setSpeakerType(String)`.
  - `EventListener`: `onStart`, `onReady`, `onDone`, `onError(ErrorType)`, `onStop`, `onUpdatedRms(Double)`. 패키지는 `ai.pleos.playground.tts.listener` (문서의 `[불명확]` 해소).
  - `OnInitializeListener`: `onSuccess()`, `onFailure(ErrorType)`. `Mode`: `SERVER`, `ON_DEVICE`, `HYBRID`. `ErrorType` 12종(문서와 일치).
  - AAR 의 manifest 는 `INTERNET` 권한과 `queries`(`ai.pleos.playground.caas.intent.action.SPEECH_START`)를 병합해 넣고, CAAS 서비스 최소 버전 4.1.29 를 요구한다. 즉 음성 합성은 이 패키지의 **별도 서비스(CAAS)** 가 한다.
  - 의존: `core-ktx 1.8.0`, `kotlinx-coroutines-android 1.6.3`, `gson 2.10.1`, `ai.pleos.playground.security:universal-access 0.6.0`.
- **[확인 필요]**: 에뮬레이터 이미지에 CAAS 서비스와 `pleos.car.permission.TTS_SERVICE` 가 있는지. 2026-11-12 에 확인하려 했으나 에뮬레이터가 부팅 중 그래픽 오류(`Failed to find ColorBuffer`)로 멈춰 확인하지 못했다. 구현 첫 작업에서 확인한다(T-스파이크). 없으면 `SpeechOutput` 의 가짜 구현만으로 개발하고 실차 확인을 미결로 남긴다.
- **Alternatives**: (a) 표준 `android.speech.tts.TextToSpeech`: 권한이 필요 없고 `synthesizeToFile` 로 데이터 수준 무음을 붙일 수 있지만, 이 이미지에 한국어 엔진이 있는지 모르고 AGENTS.md 의 SDK 지침과 다르다. Pleos 경로를 쓸 수 없을 때의 대체 후보로 남긴다(`SpeechOutput` 구현 추가만으로 가능). (b) `HYBRID`/`SERVER`: 키 관리가 필요해 제외.

## R2. 모듈 배치

- **Decision**: 문장 고르기와 말하기 규칙(순수 로직)은 `:engine` 의 `speech` 패키지, 음성 출력과 오디오 포커스는 `:signal` 의 `voice` 패키지, 말풍선 화면과 연결은 `:app`. `:pet` 은 바꾸지 않는다.
- **Rationale**: 헌장 II 는 Pleos SDK 의존과 호출을 `:signal` 에만 두라고 한다(TTS 포함). 주행 상태와 모션 등급 판단은 `:engine` 에서만 하므로, 제한 상태에 따른 표현 허용 여부도 엔진의 `MotionTier` 를 그대로 받아 쓰고 새로 판단하지 않는다. 의존 방향 `:app → :pet → :engine → :signal` 을 지킨다(엔진이 음성 출력 인터페이스를 모르고, 앱이 둘을 이어 준다).
- **펫 색 변화**: 002 에서 이미 감정(`Mood`)에 따라 정적 포즈의 몸 색이 바뀐다(`StaticPainter` 가 `pose.bodyColor` 를 칠함). 제한 중 "색 변화" 알림은 이 동작을 그대로 쓴다. 새 `:pet` 코드는 없다.
- **Alternatives**: 새 모듈 `:voice` 를 만드는 안은 헌장 II 의 모듈 사슬을 바꿔야 해서 제외. 문장 로직을 `:app` 에 두는 안은 JVM 테스트가 어렵고 "앱은 그리기만 한다"는 원칙에 어긋나 제외.

## R3. 제한 중 표현: `MotionTier` 로 판단

- **Decision**: 말풍선 글자는 `PetOutput.tier == FULL` 일 때만 보이고, `STATIC`(제한 있음 또는 알 수 없음)에서는 음성과 색 변화만 쓴다(사용자 결정 2026-11-12).
- **Rationale**: 제한 있음과 알 수 없음은 이미 엔진이 둘 다 `STATIC` 으로 묶는다(`MotionGate`). 그 값을 재사용하면 "알 수 없음은 제한과 같은 규칙"(스펙 US2-5)이 별도 코드 없이 보장되고 판단이 한 곳에 남는다. `ONE_SHOT` 은 기본 꺼짐이며(`allowOneShot=false`) 켜져도 정적으로 취급한다(말풍선은 `FULL` 만).
- **전환 순간**: 출력이 `FULL → STATIC` 으로 바뀌면 같은 값 갱신 안에서 말풍선을 지운다. 엔진 출력은 시각을 뺀 내용이 바뀔 때만 갱신되므로(`sameStateAs`) 지연은 한 번의 상태 흐름 전달뿐이다(1초 한도 안).

## R4. 오디오 포커스와 `AudioAttributes`

- **Decision**: 말하기 전에 `AudioFocusRequest`(`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`)를 요청하고 얻은 뒤에만 `speak` 한다. 포커스 변경 4가지를 모두 처리한다.
  - `GAIN` → 정상(대기 중이던 말은 이어 하지 않는다)
  - `LOSS` → 즉시 `stop()`, 포커스 반납, 자동 재개 없음
  - `LOSS_TRANSIENT` → 즉시 `stop()` 후 반납(말은 한 문장이라 일시정지 후 복귀 대신 버린다; Gleo 호출 규칙 AGENTS 7절 준수)
  - `LOSS_TRANSIENT_CAN_DUCK` → 볼륨 감쇄 요구를 따르지 못하는 한 문장이므로 `stop()` 으로 처리한다.
- **근거**: `docs/03` 1.2~1.5 (요청 전 포커스 필수, 4가지 변경 MUST, Gleo `LOSS_TRANSIENT` 즉시 중단). 일시 해제를 "일시정지 후 복귀"로 처리하지 않는 이유는 오래된 말을 뒤늦게 하지 않기 위해서다(스펙 US4-3).
- **`AudioAttributes`**: `CONTENT_TYPE_SPEECH`. `usage` 는 `USAGE_NOTIFICATION` 을 기본으로 한다. **[확인 필요]**: 상호작용 매트릭스(원문에서 이미지로만 제공)에서 이 컨텍스트가 내비/음악과 `C`(동시)인지 `E`(독점)인지 모른다. 값은 `VoiceConfig.audioUsage` 한 곳에 두고 에뮬레이터에서 음악 재생 중 요청해 보고 정한다. `USAGE_ASSISTANT`(= `VOICE_COMMAND`, Gleo 와 같은 컨텍스트)는 음악을 독점해 끊으므로 쓰지 않는다.
- **SDK 와의 관계**: SDK 는 `setAudioAttributes` 를 제공하므로 같은 `AudioAttributes` 를 SDK 와 `AudioFocusRequest` 양쪽에 넘긴다. SDK 가 내부에서 포커스를 따로 요청하는지는 문서에 없다 `[확인 필요]`. 이중 요청이면 앱 쪽 요청이 SDK 요청과 충돌하는지 스파이크에서 본다.
- **반납**: `onDone`, `onStop`, `onError` 어디서든 한 번만 반납한다(요청 횟수 = 반납 횟수, SC-009).

## R5. 선행 무음 150~200 ms

- **문제**: AGENTS.md 7절은 "오디오 데이터 레벨로 150~200 ms 무음을 붙이고 재생 지연만으로 처리하지 않는다"고 한다. SDK 의 `speak(text)` 는 PCM 을 받지 않아 스트림 앞에 무음을 끼울 수 없다.
- **Decision**: 포커스를 얻은 뒤 `AudioTrack`(`MODE_STATIC`, 0으로 채운 PCM 180 ms, 같은 `AudioAttributes`)을 먼저 재생해 오디오 경로를 열어 두고, 끝나면 곧바로 `speak` 한다. 무음 길이 180 ms 는 `VoiceConfig.preSilenceMs` 로 두고 150~200 안에서만 바꿀 수 있게 검증한다(`require`).
- **[확인 필요]**: 이 방식이 "첫 음절 잘림"을 실제로 막는지, 무음 트랙 종료와 SDK 재생 시작 사이 틈이 얼마인지는 실차 또는 에뮬레이터 오디오로 재야 한다. 틈이 크면 무음 트랙을 `speak` 직전에 같이 시작하는 방법을 쓴다. 이 한계를 검증 기록에 남긴다.
- **Alternatives**: 단순 지연(`delay(180)`)은 규칙이 금지한 방식이라 제외. 표준 `TextToSpeech.synthesizeToFile` 로 합성해 앞에 무음 PCM 을 붙이는 안은 R1 의 대체 구현 후보.

## R6. 문장 길이 규칙을 테스트로 고정

- **Decision**: `SpeechCatalog` 의 모든 문장을 `SpeechLimits` 로 검사한다: 120자 이하, 24 어절 이하, 한 줄 80자 이하, 3줄 이하. 제한 중(음성만)의 한 문장은 더 짧은 `restrictedMaxChars` 이하로 검사한다. `[확인 필요]` 기본값 20자(소리 한 번에 끝나는 길이를 가정, 값은 측정하지 않은 추정이므로 설정으로 두고 주행 시험 뒤 조정).
- 어절은 공백으로 센다(한국어 "단어"의 근사). 정확한 단어 정의는 원문에 없다 `[확인 필요]`.
- 개인정보 금지(FR-019)는 문장이 고정 목록이라 구조적으로 만족한다. 문장에 `%s` 같은 자리 표시자를 두지 않는 것을 테스트한다.

## R7. 말하기 규칙 (빈도, 순서, 반복)

- **우선순위**: `DANGER`(충돌 위험, 차선 이탈) > `WARN`(급가속, 급감속, 급회전, 깜빡이 없는 차선 변경) > `PRAISE`(안전한 차선 변경, 안전벨트 확인) > `INFO`(출발, 도착). 위험 경고가 칭찬보다 먼저라는 스펙 US4-2 를 만족한다. 엔진의 점수 가중치와는 별개로 둔다(엔진을 바꾸지 않는다).
- **말하는 중 새 이벤트**: 더 높은 우선순위면 현재 말을 `stop()` 하고 새 말을 한다. 같거나 낮으면 대기 슬롯 1칸에 넣는다(더 높은 것이 덮어씀). 대기 한도(`queueMaxAgeMs`)를 넘으면 버린다.
- **쿨다운**: 이벤트 종류별로 마지막으로 말한 시각 + 간격 안이면 말하지 않는다. 엔진에도 판정용 재발 간격이 있지만(`EngineConfig.cooldown*`) 그것은 "이벤트로 인정할지"이고 이 쿨다운은 "말할지"여서 따로 둔다. 시간은 이벤트 시각(`DrivingEvent.atMs`)과 호출자가 넘기는 값만 쓴다(벽시계 미사용, `Cooldown` 의 기존 방식과 같음).
- **같은 문장 반복 금지**: 이벤트마다 변형 2개를 두고 직전에 쓴 변형이 아닌 것을 고른다. 기본 변형은 결정적이다(난수 미사용, 테스트가 쉬움).
- **도착**: 주행 끝 이벤트는 한 번만. 엔진이 이미 한 세션에 한 번만 내보내므로 별도 처리 없이 쿨다운 0 으로 둔다.
- 모든 간격과 한도 값은 `SpeechConfig` 한 곳에 두고 `[확인 필요]`(측정 전 초안)로 표시한다. 초안: 쿨다운 위험 10 s, 경고 8 s, 칭찬 15 s, 정보 0; 대기 한도 2 s; 말풍선 표시 4 s.

## R8. 말풍선 표시 (움직임 없음)

- **Decision**: Compose `Surface` + `Text` 로 그리고 `AnimatedVisibility` 같은 전환을 쓰지 않는다. 나타남과 사라짐은 상태 값이 바뀌는 즉시 한 번 그려진다. 사라짐은 `delay` 로만 센다.
- **Rationale**: 헌장 I 의 제한 상태 금지 목록(반복 모션, 점멸, 화면을 덮는 표현)과 B 등급 기본 꺼짐. 제한이 풀린 상태에서도 전환 효과를 쓰지 않아 규칙을 단순하게 유지한다(스펙 가정). 말풍선이 없을 때는 그리는 노드가 없어 재그리기가 일어나지 않는다.
- **레이아웃**: 펫 위쪽에 두고 폭을 제한(`widthIn(max)`), `maxLines = 3`, 글자 14 sp 이상(최소 12 sp), 대비 4.5:1(배경 어두운 면 + 밝은 글자). 화면 회전과 크기 변경은 `configChanges` 선언으로 Activity 가 다시 만들어지지 않고 `ViewModel` 이 상태를 가진다(003 FR-018 과 같음).
- **[확인 필요]**: 글자 높이 5 mm 의 dp 환산. 원문에 이미지로만 있다. 지금은 sp 기준(14 sp)으로 두고 기록한다.

## R9. 음성 켜기/끄기 저장

- **Decision**: `SharedPreferences` 에 불리언 하나(`voice_enabled`, 기본 켜짐)를 저장한다. 설정 변경은 제한이 없을 때(`tier == FULL`)만 받는다.
- **Rationale**: 값이 하나이고 민감정보가 아니다. 새 의존성(DataStore)을 늘리지 않는다. 암호화가 필요한 값이 아니다(헌장 V 의 "민감 데이터"에 해당하지 않음).
- 설정 UI 는 최소한의 스위치 하나. 제한 중에는 비활성이다(입력이 필요한 화면은 정지 중에만, 헌장 I).

## R10. 권한과 manifest

- **필요**: `pleos.car.permission.TTS_SERVICE`(초기화, 리스너, 합성 요청 전부, 미선언 시 `PERMISSION_DENIED`, `docs/02` 5절). 승인 상태는 확인되지 않았다 `[확인 필요]`. 거부되면 `onFailure(PERMISSION_DENIED)` 로 음성 불가 상태가 되고 앱은 계속 동작한다(FR-015).
- **병합되는 것**: SDK 의 `INTERNET` 권한. `ON_DEVICE` 는 네트워크가 필요 없으므로 불필요한 권한이다(헌장 IV 최소 권한). `tools:node="remove"` 로 제거할 수 있지만 SDK 가 이 권한을 "CCG Validation 체크용"이라고 주석으로 밝혀 제거 시 검증에 걸릴 수 있다 `[확인 필요]`. 일단 병합된 상태를 그대로 두고 사용자에게 알린다.
- 그 밖의 새 권한은 없다.

## R11. 수명주기

- SDK 규칙(AGENTS.md 3절): 생성 → `initialize` → `addEventListener` → `setLocale(ko-KR)` → `speak` → `removeEventListener` → `release`. `release()` 뒤 인스턴스는 재사용하지 않고 새로 만든다.
- 앱이 화면에서 사라지면(`onBackground`) 재생을 멈추고 포커스를 반납한 뒤 SDK 를 해제한다. 다시 보이면 새로 초기화한다(003 의 신호 수신과 같은 생명주기).
- 초기화는 비동기(`onSuccess`)이므로 그 전에 온 이벤트는 음성 없이 처리하거나(말풍선만) 버린다. 오래된 말을 뒤늦게 하지 않는다.
- 콜백 스레드는 문서에 없다(`[확인 필요]`, AGENTS.md 3절). 콜백에서는 채널로 넘기기만 하고 상태 갱신은 한 코루틴에서 한다.
- 재생이 끝나지 않는 경우(콜백 누락)에 대비해 `speakWatchdogMs` 후 `stop()` 과 반납을 한다(스펙 엣지 케이스).

## R12. 차량 경고음 중 말하지 않기 (헌장 플랫폼 제약)

- **상태**: 이 앱은 차량 경고음이 울리는 중인지 알 방법이 없다(수신하는 신호에 없음). 오디오 포커스 변경(`LOSS*`)으로 부딪침을 피하는 것이 현재 가능한 전부다. 경고음 감지는 `[확인 필요]` 로 남기고 사용자에게 알린다.

## 미결 요약 (사용자에게 알릴 것)

| # | 항목 | 영향 | 해소 방법 |
|-|-|-|-|
| 1 | 에뮬레이터에 CAAS 서비스와 `TTS_SERVICE` 권한이 있는지 | 에뮬레이터에서 실제 음성 확인 가능 여부 | 구현 첫 스파이크, 안 되면 실차 |
| 2 | `pleos.car.permission.TTS_SERVICE` 승인 여부 | 앱 시장 배포 시 권한 허용 | 대회 측 확인 |
| 3 | `USAGE_NOTIFICATION` 이 상호작용 매트릭스에서 어떻게 동작하는지 | 음악, 내비를 끊는지 | 에뮬레이터에서 음악 재생 중 시험 |
| 4 | 무음 트랙 방식이 첫 음절 잘림을 막는지 | FR-010 만족 | 실차 또는 에뮬레이터 오디오 |
| 5 | 병합되는 `INTERNET` 권한을 제거해도 되는지 | 권한 최소화 | 대회 측 확인 |
| 6 | 쿨다운, 대기 한도, 표시 시간, 제한 중 글자 수 한도 | 사용감 | 주행 시험 후 조정(설정 한 곳) |
| 7 | 차량 경고음 감지 방법 | 경고음 중 말하지 않기 | 플랫폼 문서 확인 |

## 구현 뒤 상태 (2026-11-13)

| # | 상태 |
|-|-|
| 1 | **미확인**. 에뮬레이터 부팅을 2번 시도했으나 둘 다 그래픽 오류(`Failed to find ColorBuffer`)와 `hanging thread 'QEMU2 main loop'` 로 멈췄다(기본 GPU, `-gpu swiftshader_indirect`). 서비스와 권한 유무는 아직 모른다 |
| 2 | 미확인. 대회 측 확인 필요(매니페스트에는 선언함) |
| 3 | 미확인(에뮬레이터 필요) |
| 4 | 미확인(에뮬레이터 오디오 또는 실차 필요). 무음 `AudioTrack` 방식으로 구현만 했다 |
| 5 | 미확인. 병합된 매니페스트에 `INTERNET` 이 있음을 확인했다(SDK 가 병합). 제거하지 않았다 |
| 6 | 초안 그대로(`SpeechConfig`, `VoiceConfig` 한 곳) |
| 7 | 미확인 |
| 8 (새) | 제한 중 색 변화: `MoodMachine` 은 `TRIP_START`, `NO_SIGNAL_LANE_CHANGE` 에 감정 변화를 만들지 않는다(그 외 8종은 만든다). 이 두 이벤트는 제한 중 음성만 나오고 색 변화가 없다. `:pet`, 엔진 규칙을 바꾸지 않아 그대로 두었다. 바꿀지는 사용자 결정 |
| 9 (새) | 릴리즈 빌드에서 사용자가 음성을 끄는 진입점이 없다(음성 스위치는 디버그 컨트롤 안에 있음). 어디에 둘지 `[확인 필요]` |
