---

description: "말풍선과 음성(TTS) 구현 작업 목록"
---

# Tasks: 말풍선과 음성(TTS)

**Input**: `specs/004-speech-bubble-tts/` 의 `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/speech.md`, `quickstart.md`

**Prerequisites**: 001~003 완료(브랜치 `003-real-vehicle-signal` 위에서 시작한 `004-speech-bubble-tts`). 이 기능은 엔진의 `events`, `output`(`MotionTier`)을 받아 쓰기만 하고 `:pet` 과 엔진의 기존 판정, 점수, 모션 코드는 바꾸지 않는다(SC-008).

**Tests**: 카탈로그, 규칙, 플레이어, 코디네이터는 **테스트를 먼저 쓰고 실패를 확인한 뒤 구현한다**(헌장 III). `PleosSpeechOutput`(SDK)과 `AndroidAudioFocusGateway`(`AudioManager`)는 JVM 에서 검증할 수 없으므로 에뮬레이터에서 `quickstart.md` 로 확인한 결과를 `checklists/verification-log.md` 에 남긴다.

**Organization**: 스토리별로 묶었다. US1, US2 는 P1(말풍선과 제한 중 표현), US3, US4 는 P2(오디오 포커스, 빈도와 우선순위), US5 는 P3(음성 불가와 끄기).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: 다른 파일이고 아직 끝나지 않은 작업에 의존하지 않아 병렬로 할 수 있음
- **[Story]**: 스펙의 사용자 스토리(US1~US5)

## Path Conventions

- `engine/...`, `signal/...`, `app/...` 는 각 모듈의 `src/main/java/com/example/dashpet/<경로>/` 를 줄여 쓴 것이다. 테스트는 `src/test/java/com/example/dashpet/<경로>/`.
  - `:engine` → `engine/speech/`, `:signal` → `signal/voice/`, `:app` → `ui/speech/`(연결은 `ui/demo/`, `ui/screens/`)
  - `:pet` 은 바꾸지 않는다.
- 실행 환경: `export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"` 후
  `./gradlew :signal:testDebugUnitTest :engine:testDebugUnitTest :pet:testDebugUnitTest :app:testDebugUnitTest --console=plain`
- 에뮬레이터: `export ADB=~/Library/Android/sdk/platform-tools/adb`. 부팅은 `quickstart.md` 0절(Cold boot, RAM 4 GB). 마지막 부팅(2026-11-12)이 그래픽 오류로 멈췄으므로 먼저 `pgrep -fl qemu` 로 남은 프로세스를 확인한다.

**원칙 (모든 작업에 적용)**:
- 말풍선 글자는 `PetOutput.tier == FULL` 일 때만 존재한다. `RestrictionState` 를 다시 해석하지 않는다(헌장 I, II).
- Pleos SDK(`ai.pleos.playground.tts.*`)는 `PleosSpeechOutput.kt` 한 파일에서만 import 한다(헌장 II).
- 문장 본문, 개인정보를 로그에 남기지 않는다. Client ID/Secret 을 쓰지 않는다(`ON_DEVICE`).
- 문서에 없는 값(쿨다운 등)은 `SpeechConfig`/`VoiceConfig` 한 곳에 `[확인 필요]` 주석과 함께 둔다.
- 커밋과 푸시는 사용자가 요청할 때만 한다.

---

## Phase 1: Setup (공통 설정)

**Purpose**: 의존성, 권한, 환경 확인

- [X] T001 에뮬레이터를 Cold boot 로 띄우고 스파이크를 한다: `$ADB shell pm list packages | grep -i -E "caas|tts|speech"` 와 `$ADB shell pm list permissions | grep -i TTS_SERVICE`. 결과(서비스와 권한 유무, 부팅이 또 멈췄는지)를 `specs/004-speech-bubble-tts/checklists/verification-log.md` 에 새로 만들어 적는다. 없어도 다음 단계는 진행한다(`UNAVAILABLE` 로 떨어지는 것이 정상, research 미결 1).
- [X] T002 `gradle/libs.versions.toml` 에 `pleosTts = "3.3.1"` 과 `pleos-tts = { group = "ai.pleos.playground", name = "TextToSpeech", version.ref = "pleosTts" }` 를 추가하고, `signal/build.gradle.kts` 의 `dependencies` 에 `implementation(libs.pleos.tts)` 를 더한다. `./gradlew :signal:assembleDebug` 가 통과하는지 확인하고, 병합된 manifest 의 `INTERNET` 권한과 `kotlinx-coroutines-android` 가 1.10.2 로 올라가는지 기록한다(research R10).
- [X] T003 `app/src/main/AndroidManifest.xml` 에 `<uses-permission android:name="pleos.car.permission.TTS_SERVICE" />` 하나만 추가한다. 다른 권한은 더하지 않는다(헌장 IV). 승인 여부는 `[확인 필요]` 로 주석에 남긴다.

---

## Phase 2: Foundational (모든 스토리가 기대는 타입)

**Purpose**: 스토리 구현 전에 필요한 값 타입과 인터페이스. 동작 로직은 없다.

**⚠️ CRITICAL**: 이 단계가 끝나야 US1~US5 를 시작할 수 있다.

- [X] T004 [P] `engine/speech/SpeechPriority.kt` 에 `enum SpeechPriority { INFO(0), PRAISE(1), WARN(2), DANGER(3) }` 와 `EventType → SpeechPriority` 표를 만든다(`data-model.md` 표와 같게: DANGER=`COLLISION_RISK`,`LANE_DEPARTURE` / WARN=`HARD_ACCEL`,`HARD_BRAKE`,`HARD_CORNER`,`NO_SIGNAL_LANE_CHANGE` / PRAISE=`GOOD_LANE_CHANGE`,`SEATBELT_OK` / INFO=`TRIP_START`,`TRIP_END`). 이벤트 10종 모두 정확히 하나의 우선순위를 가지며 `when` 에 `else` 를 두지 않는다.
- [X] T005 [P] `engine/speech/SpeechConfig.kt` 에 설정 한 곳을 만든다. 모두 `[확인 필요]` 초안: `cooldownMs`(DANGER 10000, WARN 8000, PRAISE 15000, INFO 0), `queueMaxAgeMs` 2000, `bubbleDurationMs` 4000, `restrictedMaxChars` 20, `preSilenceMs` 180, `speakWatchdogMs` 8000. 검증: 음수 불가, `preSilenceMs` 는 `require(in 150..200)`.
- [X] T006 [P] `engine/speech/SpeechModels.kt` 에 `Utterance(eventType, priority, text, shortText, atMs)`(불변, `text`/`shortText` 는 고정 목록의 문장), `SpeechLine(text, shortText)`, `sealed SpeechCommand { Speak(utterance, variantIndex, showBubble, useShortText) / StopSpeaking(reason) / HideBubble }` 와 `StopReason { PREEMPTED, RESTRICTED_ENTERED, RESET, DISABLED }` 를 만든다. `Speak` 는 `showBubble == !useShortText` 를 `init` 에서 검증한다.
- [X] T007 [P] `signal/voice/SpeechOutput.kt` 에 `SpeechState`(`Started(id)`, `Finished(id)`, `Stopped(id)`, `Failed(id, reason)`, reason: `NO_FOCUS`, `ENGINE_ERROR`, `TIMEOUT`, `BUSY`), `VoiceAvailability`(`UNKNOWN`, `READY`, `UNAVAILABLE`), `VoiceConfig`(`audioUsage` 기본 `USAGE_NOTIFICATION` `[확인 필요]`, `contentType` `CONTENT_TYPE_SPEECH`, `focusGain` `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, `locale` `ko-KR`, `preSilenceMs`), 인터페이스 `SpeechOutput { speak(id, text); stop(); release(); states: Flow<SpeechState>; availability: StateFlow<VoiceAvailability> }` 를 만든다. `ttsMode` 는 `PleosSpeechOutput` 안에서만 쓰며 여기서 SDK 타입을 노출하지 않는다.
- [X] T008 [P] `signal/voice/AudioFocusGateway.kt` 에 `FocusChange`(`GAIN`, `LOSS`, `LOSS_TRANSIENT`, `LOSS_TRANSIENT_CAN_DUCK`)와 인터페이스 `AudioFocusGateway { request(): Boolean; abandon(); changes: Flow<FocusChange>; requestCount; abandonCount }` 를 만든다.
- [X] T009 [P] `signal/src/test/.../voice/FakeAudioFocusGateway.kt`, `FakeSpeechOutput.kt` 를 만든다. 가짜 게이트웨이는 `request` 결과를 설정할 수 있고 포커스 변경을 밀어 넣을 수 있으며 요청과 반납 횟수를 센다. 가짜 출력은 `speak` 호출을 기록하고 `SpeechState` 를 밀어 넣을 수 있다.
- [X] T010 `./gradlew :engine:testDebugUnitTest :signal:testDebugUnitTest` 로 기존 테스트가 그대로 통과하는지 확인한다(새 타입만 추가된 상태).

**Checkpoint**: 타입이 갖춰졌고 기존 테스트가 통과한다.

---

## Phase 3: User Story 1 - 이벤트에 맞는 말풍선과 음성 (Priority: P1) 🎯 MVP

**Goal**: 이벤트가 판정되면 문장을 말풍선으로 보여주고 같은 문장을 음성 출력에 넘긴다(제한 없음일 때).

**Independent Test**: 단위 테스트로 이벤트 10종의 문장과 길이 한도를 확인하고, 데모 모드에서 이벤트 시나리오를 재생해 말풍선이 1초 안에 나타나고 4초 뒤 사라지는지 본다(`quickstart.md` 3절 1~4번).

### Tests for User Story 1 (먼저 쓰고 실패 확인)

- [X] T011 [P] [US1] `engine/src/test/.../speech/SpeechCatalogTest.kt`: 이벤트 10종 모두 변형 2개와 우선순위가 있음, 모든 `text` 가 120자 이하, 24어절 이하, 한 줄 80자 이하, 3줄 이하, 모든 `shortText` 가 `restrictedMaxChars` 이하이고 한 문장, 자리 표시자(`%`, `{`) 없음, 이벤트마다 변형 2개가 서로 다름, `pick(type, last)` 가 직전과 다른 번호를 돌려주고 난수를 쓰지 않음(SC-001, SC-002, FR-004).
- [X] T012 [P] [US1] `engine/src/test/.../speech/SpeechDirectorTest.kt` 에 기본 동작 케이스: `tier == FULL` 에서 이벤트가 오면 `Speak(showBubble=true, useShortText=false)` 하나, 말하는 중이 아니면 즉시, 같은 입력은 같은 지시(결정적), 시계를 읽지 않음(US1-1, US1-2).
- [X] T013 [P] [US1] `app/src/test/.../ui/speech/SpeechCoordinatorTest.kt` 에 기본 케이스: 가짜 엔진 흐름에 이벤트를 넣으면 `bubble` 이 `BubbleState(text, eventType)` 가 되고 가짜 `SpeechOutput.speak` 이 같은 `text` 로 호출됨(FR-003), `bubbleDurationMs` 뒤 `bubble` 이 null(가상 시간), 새 말이 오면 이전 말풍선을 즉시 대체(US1-4).

### Implementation for User Story 1

- [X] T014 [US1] `engine/speech/SpeechCatalog.kt`: `contracts/speech.md` 1절 표의 문장 20개를 그대로 넣는다(문장은 초안, 바꾸면 T011 을 통과해야 함). `SpeechLimits`(길이 검사 함수)와 `pick(type, lastVariantIndex)` 를 둔다. T011 통과.
- [X] T015 [US1] `engine/speech/SpeechDirector.kt` 의 기본 경로: `onEvent(event, tier, nowMs)` 가 카탈로그에서 변형을 골라 `Speak` 를 돌려준다. `showBubble = (tier == FULL)`, `useShortText = !showBubble`. 쿨다운, 선점, 대기는 US4 에서 더한다. T012 통과.
- [X] T016 [P] [US1] `app/.../ui/speech/BubbleState.kt`(`text`, `eventType`, 불변)와 `SpeechBubble.kt`: 입력 `BubbleState?`, null 이면 아무것도 그리지 않음. 애니메이션, 전환, 글자 효과, 점멸, 자동 스크롤 없음. `clickable`/터치 처리 없음. `contentDescription` 은 문장 본문. 글자 14 sp 이상(최소 12 sp), 대비 4.5:1 이상, `maxLines = 3`, `widthIn(max)` 로 폭 제한, 잘리지 않음. 색은 `ivi` 접두사 토큰이 없으면 기존 테마 색을 쓰고 `[확인 필요]` 로 남긴다.
- [X] T017 [US1] `app/.../ui/speech/SpeechCoordinator.kt`: `engine.events`, `engine.output` 을 구독해 `SpeechDirector` 에 넣고 지시를 `SpeechOutput.speak` 과 `bubble: StateFlow<BubbleState?>` 로 옮긴다. 말풍선은 `bubbleDurationMs` 뒤 `delay` 로 지운다. 구독은 세션 시작과 함께 건다(구독 전 이벤트는 받지 않음). 로그(`Speech` 태그)는 말하기 시작/건너뜀과 이유만 한 줄씩, 문장 본문은 남기지 않는다(FR-019, FR-020). T013 통과.
- [X] T018 [US1] `app/.../ui/demo/DemoViewModel.kt` 가 `SpeechCoordinator` 를 소유하고 세션 시작 때 구독을 건다. `app/.../ui/screens/HomeScreen.kt` 에서 `bubble` 을 모아 `SpeechBubble` 을 펫 근처에 그린다(가로와 세로 모두 잘리지 않게). 음성 출력은 이 단계에서 `NoOpSpeechOutput`(소리 없음, 항상 `UNAVAILABLE`)을 주입한다. `DemoViewModel` 팩토리에 출력을 넘기는 자리만 만든다. **(구현 노트: 실제 출력 연결은 T031 과 함께 한꺼번에 했다.)**
- [X] T019 [US1] 모듈 테스트 전부 실행(`:signal :engine :pet :app` `testDebugUnitTest`), 통과 확인.

**Checkpoint**: 제한 없음에서 이벤트마다 말풍선이 나오고 사라진다. 소리는 아직 없다.

---

## Phase 4: User Story 2 - 제한 중에는 음성과 색 변화로만 (Priority: P1)

**Goal**: `tier != FULL`(제한 있음, 알 수 없음)에서는 글자 없이 짧은 한 문장 음성과 색 변화만, 제한이 걸리는 순간 떠 있던 말풍선을 즉시 지운다.

**Independent Test**: 에뮬레이터에서 기어 D 로 제한을 걸고 이벤트를 만들어 글자가 없는지, 터치가 무시되는지, 프레임 0 과 CPU 5% 미만인지 본다(`quickstart.md` 3절 5~8번, 4절).

### Tests for User Story 2 (먼저 쓰고 실패 확인)

- [X] T020 [P] [US2] `SpeechDirectorTest.kt` 에 추가: `STATIC` 에서 `Speak(showBubble=false, useShortText=true)`, `FULL → STATIC` 에서 말풍선이 떠 있을 때만 `HideBubble`(말하는 중인 음성은 끝까지 허용), `STATIC → FULL` 은 지시 없음(지난 이벤트의 말풍선을 되살리지 않음), 처음 첫 `output` 이 오기 전의 알 수 없음은 `STATIC` 과 같음(US2-1,2,3,5, `contracts/speech.md` 2절 6~7행).
- [X] T021 [P] [US2] `SpeechCoordinatorTest.kt` 에 추가: `STATIC` 에서 `bubble` 이 항상 null, 말풍선이 떠 있는 동안 `FULL → STATIC` 이 오면 `bubble` 이 즉시 null(가상 시간 1초 이내, SC-004), 음성이 꺼져 있거나 불가여도 `STATIC` 에서 글자가 나오지 않음(US2-6).

### Implementation for User Story 2

- [X] T022 [US2] `SpeechDirector.onOutput` 에 `FULL → STATIC` 처리를 구현하고 T020 을 통과시킨다. 모션 등급은 `PetOutput.tier` 값만 쓰고 제한 비트를 다시 읽지 않는다.
- [X] T023 [US2] `SpeechCoordinator` 가 `HideBubble` 을 받으면 즉시 `bubble = null` 로 만든다(지연 없음, 애니메이션 없음). T021 통과.
- [X] T024 [US2] 제한 중 색 변화 확인: 이벤트가 오면 002 의 감정 색이 이미 바뀌는지 코드로 확인한다(`:pet` 변경 없음). 바뀌지 않는 이벤트가 있으면 그 목록과 이유를 `research.md` 미결에 적고 사용자에게 알린다(`:pet` 을 바꾸지 않는다).
- [X] T025 [US2] `SpeechBubble` 에 터치 입력이 없는지, `STATIC` 에서 노드가 그려지지 않는지 Compose 테스트 또는 코드 점검으로 확인한다. Compose 테스트 환경이 없으면 점검 결과를 `verification-log.md` 에 적는다.

**Checkpoint**: P1 두 스토리가 단위 테스트 수준에서 완성됐다. 에뮬레이터 확인은 Phase 8.

---

## Phase 5: User Story 3 - 오디오 포커스와 음성 출력 (Priority: P2)

**Goal**: 말하기 전에 포커스를 요청하고, 선행 무음을 붙여 재생하고, 포커스 변경 4가지를 처리하고, 어떤 경로로 끝나도 포커스를 한 번만 반납한다. 실제 Pleos TTS 를 `:signal` 에서 연결한다.

**Independent Test**: 가짜로 `SpeechPlayerTest` 를 통과시키고, 에뮬레이터에서 음악 재생 중 말하기와 포커스 변경을 만든다(`quickstart.md` 3절 9~11번, 15번).

### Tests for User Story 3 (먼저 쓰고 실패 확인)

- [X] T026 [P] [US3] `signal/src/test/.../voice/SpeechPlayerTest.kt`(가짜 포커스, 가짜 합성기, 가상 시간): ① 순서가 포커스 요청 → 선행 무음 → speak ② `request` 실패면 `Failed(NO_FOCUS)` 이고 speak 하지 않음 ③ `LOSS`, `LOSS_TRANSIENT`, `LOSS_TRANSIENT_CAN_DUCK` 각각 즉시 stop + 반납 + `Stopped`, 0.5초 안(가상 시간), `LOSS` 뒤 자동 재개 없음 ④ `GAIN` 은 재생 중이 아니면 무시 ⑤ `Finished`, `Stopped`, `Failed`, 감시 시간 초과(`speakWatchdogMs`, `Failed(TIMEOUT)`) 모든 경로에서 반납이 정확히 1회, 최종 `requestCount == abandonCount` ⑥ 재생 중 `speak` 를 또 부르면 `Failed(BUSY)` 이고 상태가 바뀌지 않음 ⑦ `UNAVAILABLE` 이면 예외 없이 `Failed(ENGINE_ERROR)` 한 번(SC-005, SC-007, SC-009, FR-008~010, FR-015).

### Implementation for User Story 3

- [X] T027 [US3] `signal/voice/SpeechPlayer.kt`: 포커스 → 선행 무음(`preSilenceMs`) → 합성기 `speak` → 반납 순서와 포커스 변경 처리, 감시 타이머를 순수 코루틴 로직으로 구현한다. 의존은 `AudioFocusGateway` 와 내부 합성기 인터페이스(`speak(text)`, `stop()`, 이벤트 콜백, `playSilence(ms)`)뿐이다. 콜백은 어느 스레드에서 와도 채널로 넘기고 상태 갱신은 한 코루틴에서 한다. T026 통과.
- [X] T028 [P] [US3] `signal/voice/AndroidAudioFocusGateway.kt`: `AudioManager` 와 `AudioFocusRequest`(`GAIN_TRANSIENT_MAY_DUCK`, `AudioAttributes` 는 `VoiceConfig` 의 용도와 `CONTENT_TYPE_SPEECH`)로 구현한다. 4가지 변경을 `FocusChange` 로 올리고 요청과 반납을 센다. `Speech` 로그에 요청/반납/변경을 한 줄씩 남긴다.
- [X] T029 [P] [US3] `signal/voice/PleosSpeechOutput.kt`(`internal`, SDK 를 import 하는 유일한 파일): `TextToSpeech(context, Mode.ON_DEVICE)` → `initialize(OnInitializeListener)` 성공 시 리스너 등록, `setLocale(ko-KR)`, `setAudioAttributes`. `EventListener` 의 `onStart/onDone/onStop` 을 `Started/Finished/Stopped` 로, `onError(ErrorType)` 를 `else` 분기까지 포함해 `Failed(ENGINE_ERROR)` 로 바꾼다. `PERMISSION_DENIED`, `NOT_SUPPORTED_API`, 초기화 실패는 `availability = UNAVAILABLE`(한 번 불가면 그 세션에서 재시도 안 함). 선행 무음은 같은 `AudioAttributes` 의 무음 `AudioTrack` 으로 흘린다(`[확인 필요]` 첫 음절 잘림 방지 효과). `release()` 는 stop → 리스너 제거 → SDK `release()` → 포커스 반납 순서이고 해제한 인스턴스를 다시 쓰지 않는다. 예외가 밖으로 새지 않는다. `isSupported` 류 확인은 `docs/02-sdk-reference.md` 에서 시그니처를 확인한 것만 쓰고 모르면 `[확인 필요]`.
- [X] T030 [US3] `SpeechCoordinator.onForeground()` 에서 음성을 초기화(이미 되어 있으면 아무것도 안 함)하고 `onBackground()` 에서 재생 중지 → 포커스 반납 → SDK 해제 → 말풍선 지움을 한다. 화면에 보이지 않는 동안 온 이벤트는 말하지 않는다(FR-011). `SpeechCoordinatorTest` 에 이 두 동작과 20회 반복 시 `requestCount == abandonCount` 케이스를 추가해 통과시킨다.
- [X] T031 [US3] `DemoViewModel`/`HomeScreen` 에서 `NoOpSpeechOutput` 대신 실제 `SpeechPlayer` + `PleosSpeechOutput` + `AndroidAudioFocusGateway` 를 주입하고 `onForeground/onBackground` 를 화면 생명주기(`LifecycleEventObserver` 또는 기존 방식)에 연결한다. 에뮬레이터에 CAAS 가 없으면 `voice=UNAVAILABLE` 로그가 한 줄 찍히고 앱은 정상이어야 한다(T001 결과 참고).
- [X] T032 [US3] `./gradlew :app:installDebug` 후 `quickstart.md` 2절을 실행해 첫 실행 로그(`Speech`, `CarSignal`)를 확인하고 결과를 `verification-log.md` 에 적는다(음성이 `UNAVAILABLE` 이면 그 이유와 함께 소리 확인은 실차로 미룸으로 기록).

**Checkpoint**: 가짜 기준으로 포커스 규칙이 닫혔고 실제 TTS 가 연결됐다.

---

## Phase 6: User Story 4 - 빈도와 우선순위 (Priority: P2)

**Goal**: 쿨다운, 위험 경고 우선, 대기 슬롯과 만료, 같은 문장 반복 금지.

**Independent Test**: `SpeechDirectorTest` 와 에뮬레이터 이벤트 연속 발생(`quickstart.md` 3절 3번, 12번).

### Tests for User Story 4 (먼저 쓰고 실패 확인)

- [X] T033 [P] [US4] `SpeechDirectorTest.kt` 에 추가(`contracts/speech.md` 2절 1~9행): 쿨다운 안의 같은 종류는 지시 없음이고 나중에 다시 꺼내지 않음, 우선순위별 쿨다운 간격, 말하는 중 더 높은 우선순위는 `StopSpeaking(PREEMPTED)` 뒤 `Speak`, 같거나 낮은 우선순위는 대기 슬롯 1칸(이미 있으면 더 높은 것, 같으면 더 최근 것만 유지), 말하기가 끝나면(`Finished/Stopped/Failed`) 슬롯이 `queueMaxAgeMs` 안이면 `Speak`, 넘으면 버림, 같은 종류를 다시 말하면 직전과 다른 변형, `TRIP_END` 는 한 번만, `reset()` 이 쿨다운, 슬롯, 변형, 말하는 중 여부를 모두 비우고 `HideBubble` 과 `StopSpeaking` 을 돌려줌(SC-006, FR-012~014, FR-017).
- [X] T034 [P] [US4] `SpeechCoordinatorTest.kt` 에 추가: 위험 경고가 칭찬을 끊으면 `SpeechOutput.stop()` 후 새 `speak`, 대기 중이던 말이 만료되면 말하지 않음, 모드 전환 때 `reset()` 으로 말풍선과 대기 음성이 남지 않음(FR-017).

### Implementation for User Story 4

- [X] T035 [US4] `SpeechDirector` 에 쿨다운, 선점, 대기 슬롯, 직전 변형 기억, `onSpeechState`, `reset()` 을 구현한다. 시각은 호출자가 넘기는 값만 쓰고 난수를 쓰지 않는다. 내부 상태는 세션 안 메모리뿐이다. T033 통과.
- [X] T036 [US4] `SpeechCoordinator` 가 `SpeechOutput.states` 를 `onSpeechState` 로 넘기고 `StopSpeaking` 지시를 `SpeechOutput.stop()` 으로, 대기 중 말을 이어 말하게 한다. `DemoViewModel.setMode` 에서 `reset()` 을 호출한다. T034 통과.

**Checkpoint**: 말하는 빈도와 순서가 규칙대로 동작한다.

---

## Phase 7: User Story 5 - 음성 불가와 끄기 (Priority: P3)

**Goal**: 음성을 쓸 수 없어도 앱이 정상 동작하고, 사용자가 음성을 켜고 끌 수 있으며 설정이 유지된다(제한 중에는 변경 불가).

**Independent Test**: 가짜 `UNAVAILABLE` 에서 이벤트 100개, 음성 스위치 동작(`quickstart.md` 3절 13~14번).

### Tests for User Story 5 (먼저 쓰고 실패 확인)

- [X] T037 [P] [US5] `SpeechCoordinatorTest.kt` 에 추가: 출력이 `UNAVAILABLE` 일 때 이벤트 100개에도 예외 없고 `FULL` 이면 말풍선은 정상(SC-007, US5-1), `setVoiceEnabled(false)` 이면 `speak` 호출 없이 말풍선만(`FULL`), `STATIC` 중 `setVoiceEnabled` 는 무시되고 값이 바뀌지 않음(FR-016, US5-3), 끄는 순간 재생 중인 음성을 멈춤, `VoiceSettings` 가 재시작 뒤에도 값을 유지(메모리 구현 두 개로 확인).

### Implementation for User Story 5

- [X] T038 [P] [US5] `app/.../ui/speech/VoiceSettings.kt`: `voice_enabled`(기본 true) 한 키의 `SharedPreferences` 얇은 래퍼와 테스트용 메모리 구현. 다른 값은 저장하지 않는다.
- [X] T039 [US5] `SpeechCoordinator.setVoiceEnabled(on)` 를 구현한다(`tier == FULL` 일 때만 반영하고 저장, 끄면 재생 중지). `voiceEnabled == false` 이면 `Speak` 의 음성 부분만 생략한다. T037 통과.
- [X] T040 [US5] `app/.../ui/demo/DemoControls.kt` 에 음성 켜기/끄기 스위치를 추가한다. `tier != FULL` 이면 비활성으로 보인다. 기존 규칙대로 `debugAvailable` 이 거짓이면 그리지 않는다. 릴리즈 빌드에서 사용자 설정 진입점이 필요한지는 `[확인 필요]` 로 `research.md` 미결에 한 줄 더하고 사용자에게 알린다.

**Checkpoint**: 다섯 스토리가 모두 단위 테스트 수준에서 완성됐다.

---

## Phase 8: Polish & 검증

**Purpose**: 에뮬레이터 확인, 문서, 기존 기능 회귀

- [X] T041 전체 자동 테스트: `./gradlew :signal:testDebugUnitTest :engine:testDebugUnitTest :pet:testDebugUnitTest :app:testDebugUnitTest`. 기존 001~003 테스트가 그대로 통과하는지 확인한다(SC-008).
- [ ] T042 에뮬레이터에서 `quickstart.md` 3절 17개 시나리오와 4절 성능 점검을 순서대로 실행하고 결과표를 `checklists/verification-log.md` 에 적는다. 음성이 `UNAVAILABLE` 이라 확인하지 못한 항목(소리, 포커스 4가지 실제 변경, 선행 무음)은 "미확인(실차 필요)" 로 적는다. 풀 모션 CPU 는 003 미결 3번과 별개이므로 말풍선이 더해 늘어난 정도만 기록한다.
- [X] T043 `research.md` 미결 요약 7건과 이번에 더해진 것의 상태를 갱신한다(풀린 것, 대회 측 확인 필요, 실차 필요로 분류). 확인하지 못한 값(쿨다운 등)이 `[확인 필요]` 로 남아 있는지 코드 주석과 맞춘다.
- [X] T044 점검: `rg "ai.pleos.playground.tts" --glob '*.kt'` 결과가 `PleosSpeechOutput.kt` 한 파일뿐인지, `rg -i "client_?id|client_?secret"` 가 소스에 없는지, 로그 호출에 문장 본문이 들어가지 않았는지 확인한다.
- [X] T045 `AGENTS.md` 12절 새 기능 체크(권한 최소화, 주행 규제, 오디오 포커스, 가로/세로, CCP Back, release 시점, 비밀값)를 항목별로 점검한 결과를 `verification-log.md` 에 적는다. 심사 체크리스트(`docs/04`)에서 이 기능과 관련된 항목도 같이 본다.
- [ ] T046 사용자에게 결과를 보고한다: 통과한 것, 확인하지 못한 것(`[확인 필요]` 목록), 대회 측에 물어야 할 것(`TTS_SERVICE` 승인, `INTERNET` 권한 제거 가능 여부, 경고음 감지). 커밋은 사용자가 요청할 때 한다.

---

## Dependencies & Execution Order

- **Phase 1 → Phase 2 → US1/US2 → (US3, US4, US5) → Phase 8**
  - Phase 1 의 T001 은 나머지와 독립이다(다음 단계를 막지 않음). T002 는 T029 전에, T003 은 T032 전에 끝나 있어야 한다.
  - Phase 2 가 끝나야 모든 스토리를 시작할 수 있다.
- **US1 → US2**: US2 는 `SpeechDirector`, `SpeechCoordinator` 기본 경로(T015, T017)를 확장한다.
- **US3**: Phase 2 의 인터페이스(T007, T008)만 있으면 US1 과 병행할 수 있다. 앱 연결(T030, T031)은 US1 의 `SpeechCoordinator`(T017) 뒤에 한다.
- **US4**: `SpeechDirector` 를 확장하므로 US1, US2 뒤에 한다(같은 파일).
- **US5**: `SpeechCoordinator` 를 확장하므로 US1 뒤에 한다. 데모 UI(T040)는 US2 뒤.
- **같은 파일을 만지는 작업은 순서대로 한다**: `SpeechDirector.kt`(T015 → T022 → T035), `SpeechDirectorTest.kt`(T012 → T020 → T033), `SpeechCoordinator.kt`(T017 → T023 → T030 → T036 → T039), `SpeechCoordinatorTest.kt`(T013 → T021 → T030 → T034 → T037), `DemoViewModel.kt`(T018 → T031 → T036).

### Parallel Opportunities

- Phase 2: T004, T005, T006, T007, T008, T009 는 서로 다른 파일이라 병렬로 가능.
- US1: T011, T012, T013 테스트와 T016(`SpeechBubble`)은 병렬로 가능.
- US3 의 T026 과 T028, T029 는 US1/US2 와 병렬로 가능(모듈이 다름).
- US4 의 T033, T034 와 US5 의 T037, T038 은 파일이 겹치지 않는 부분만 병렬.

## Implementation Strategy

- **MVP**: Phase 1~2 + US1 + US2(T001~T025). 소리 없이 말풍선과 제한 중 규칙이 단위 테스트로 닫힌다. 단, 사용자에게 들리는 음성은 US3(T026~T032)이 끝나야 생기므로 사용자에게 보이는 첫 데모는 US3 까지 포함한다.
- **그다음**: US3(포커스와 실제 TTS) → US4(빈도) → US5(끄기).
- 각 Phase 끝에서 모듈 테스트를 돌리고, 에뮬레이터 확인은 Phase 8 에서 한꺼번에 한다(T001 결과에 따라 음성 소리 확인은 실차로 미룰 수 있음).
- 값(쿨다운 등)은 모두 `[확인 필요]` 초안이다. 측정 없이 확정하지 않고 결과를 보고한다.
