# Implementation Plan: 말풍선과 음성(TTS)

**Branch**: `004-speech-bubble-tts` | **Date**: 2026-11-12 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-speech-bubble-tts/spec.md`

## Summary

엔진이 내보내는 주행 이벤트 10종에 미리 정한 한국어 한 문장(변형 2개)을 대응시키고, 제한이 없을 때는 말풍선과 음성으로, 제한이 있거나 알 수 없을 때는 짧은 한 문장 음성과 펫 색 변화로만 전한다.
문장 선택과 말하기 규칙(쿨다운, 우선순위, 대기, 반복 금지)은 `:engine` 의 순수 로직(`SpeechDirector`)으로 두고 엔진의 `MotionTier` 를 그대로 받아 쓴다(제한 해석을 새로 하지 않는다).
음성 출력은 Pleos TextToSpeech SDK(`ON_DEVICE`, 키 불필요)를 `:signal` 의 `SpeechOutput` 한 겹 뒤에 두고, 오디오 포커스 요청과 4가지 변경 처리, 선행 무음, 오류 시 조용히 떨어지기를 여기서 맡는다.
말풍선은 `:app` 의 Compose 로 그리며 애니메이션 없이 상태가 바뀌는 즉시 나타나고 사라진다. `:pet` 은 바꾸지 않는다(제한 중 색 변화는 002 의 감정 색을 그대로 쓴다).
접근 방식과 근거는 [research.md](./research.md), 타입은 [data-model.md](./data-model.md), 동작 약속과 문장 목록은 [contracts/speech.md](./contracts/speech.md).

## Technical Context

**Language/Version**: Kotlin(기존 모듈과 동일), Jetpack Compose(말풍선), kotlinx-coroutines 1.10.2(기존). 신규 라이브러리는 Pleos `TextToSpeech:3.3.1` 하나(`gradle/libs.versions.toml` 에 `pleosTts` 추가, `:signal` 의 `implementation`).

**Primary Dependencies**: `ai.pleos.playground:TextToSpeech:3.3.1`(Pleos 저장소는 이미 `settings.gradle.kts` 에 있음, 2026-11-12 에 artifact 접근 확인), `android.media`(`AudioManager`, `AudioFocusRequest`, `AudioAttributes`, `AudioTrack`).
SDK 가 끌어오는 `gson 2.10.1`, `universal-access 0.6.0`, `core-ktx 1.8.0`(상위 버전과 병합), `kotlinx-coroutines-android 1.6.3`(1.10.2 로 올라감).

**Storage**: `SharedPreferences` 한 키(`voice_enabled`). 다른 데이터는 메모리에만 둔다(쿨다운, 대기 슬롯, 말풍선).

**Testing**: JUnit4 + kotlinx-coroutines-test. `SpeechCatalog`, `SpeechDirector` 는 Android 없이 JVM 테스트. `SpeechPlayer` 는 가짜 포커스와 가짜 합성기로 JVM 테스트. `PleosSpeechOutput`(SDK)과 `AndroidAudioFocusGateway`(`AudioManager`)는 JVM 에서 검증할 수 없으므로 에뮬레이터에서 [quickstart.md](./quickstart.md) 로 확인한다(헌장 III 마지막 항목, 003 과 같은 방식).

**Target Platform**: Android Automotive(Pleos Connect). 에뮬레이터 `Pleos_Connect_v2.1.0`(API 34). compileSdk 34, minSdk 29(기존 유지).

**Project Type**: 기존 멀티 모듈 Android 앱. `:engine` 에 문장과 규칙, `:signal` 에 음성 출력, `:app` 에 말풍선과 연결을 더한다.

**Performance Goals**: 이벤트 판정 뒤 1초 안에 말풍선(SC-001). 제한 중 프레임 0과 CPU 5% 미만(SC-003). 포커스를 잃은 뒤 0.5초 안에 소리 멈춤(SC-005). 말풍선이 없을 때 새로 그리는 것 없음.

**Constraints**: 제한 중에는 음성과 색 변화만(글자 없음, 헌장 I). 모션 등급 판단은 `MotionGate` 한 곳(말풍선은 `tier == FULL` 만 보고 결정). SDK 호출은 `:signal` 에만(헌장 II). 오디오 포커스 4가지 처리와 선행 무음 150~200 ms(AGENTS.md 7절). 문장 길이 120자, 24어절, 한 줄 80자, 3줄. 음성은 한국어만. 화면 갱신은 메인 스레드.

**Scale/Scope**: 신규 파일 약 14개(`:engine` 4, `:signal` 5, `:app` 5), 수정 파일 약 6개(`libs.versions.toml`, `:signal` 빌드, manifest, `DemoViewModel`, `HomeScreen`, `DemoControls`). 테스트 파일 약 6개. 이벤트 10종, 문장 20개.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| 원칙 | 점검 | 결과 |
|-|-|-|
| I. 주행 안전 우선 | 제한 있음과 알 수 없음은 모두 `MotionTier.STATIC` 이라 같은 규칙(글자 없음, 한 문장 음성, 색 변화만)을 따른다. 말풍선은 애니메이션과 전환 없이 즉시 나타나고 사라지며 터치를 받지 않는다. 설정 변경은 제한이 없을 때만. 새 모션을 만들지 않는다(`:pet` 변경 없음) | PASS |
| II. 신호 소스 추상화와 모듈 경계 | Pleos SDK 호출은 `:signal` 의 `voice` 패키지에만 둔다. 의존 방향 `:app → :pet → :engine → :signal` 유지(엔진은 음성 출력을 모르고, 앱이 둘을 잇는다). 모션 등급 판단은 엔진 한 곳이고 말풍선은 그 값을 따른다. 엔진 판정, 점수, 모션 규칙은 바뀌지 않는다 | PASS (아래 주의) |
| III. 테스트 우선 | 카탈로그, 규칙, 플레이어는 테스트를 먼저 쓴다(Red-Green). 문장 길이 한도는 자동 테스트로 고정한다. SDK 와 `AudioManager` 어댑터만 JVM 검증이 불가해 에뮬레이터 확인과 기록으로 대체한다 | PASS (어댑터만 수동 근거) |
| IV. 플랫폼 접근과 권한 최소화 | 새 권한은 `pleos.car.permission.TTS_SERVICE` 하나(문서에 근거, `docs/02` 5절). 승인 여부는 `[확인 필요]`. SDK 수명주기(`initialize` → 리스너 → `release`, 해제한 인스턴스 재사용 금지)를 지킨다. 미지원이면 음성만 끈다. SDK 가 병합하는 `INTERNET` 권한은 `ON_DEVICE` 에 불필요하나 제거 가능 여부는 미확인 | CONDITIONAL PASS (Complexity Tracking) |
| V. 비밀값과 개인정보 | `ON_DEVICE` 라서 Client ID/Secret 이 필요 없다(코드와 저장소에 키 없음). 문장은 고정 목록이라 개인정보가 들어갈 자리가 없고, 로그에 문장 본문을 남기지 않는다. 저장하는 것은 불리언 하나 | PASS |
| VI. 근거 없는 값 금지 | SDK 시그니처는 artifact 를 `javap` 로 확인한 것만 쓴다. 쿨다운, 대기 한도, 표시 시간, 글자 수 한도, 오디오 용도, 선행 무음은 `SpeechConfig`/`VoiceConfig` 한 곳에 `[확인 필요]` 초안으로 두고 research 미결 요약에 7건을 기록한다 | PASS |

**Re-check after Phase 1 design**: 설계 후에도 결과는 바뀌지 않았다. 새로 생긴 위험은 없고 조건부 통과 1건(권한)이 남는다.

주의(II): 헌장은 Pleos SDK 를 `:signal` 에만 두라고 하는데 이 모듈 이름은 "신호"다. 음성 출력은 신호 입력이 아니라 출력이지만 규칙을 문자 그대로 지켜 `signal.voice` 패키지로 둔다. 모듈 이름과 역할이 어긋나는 점은 기록만 하고(별도 개정 사안) 구조는 바꾸지 않는다.

## Project Structure

### Documentation (this feature)

```text
specs/004-speech-bubble-tts/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── speech.md
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
gradle/libs.versions.toml                                # 수정: pleosTts 3.3.1

engine/src/
├── main/java/com/example/dashpet/engine/speech/
│   ├── SpeechPriority.kt                                # 신규: 우선순위 enum, 이벤트 → 우선순위 표
│   ├── SpeechCatalog.kt                                 # 신규: 이벤트 10종 × 변형 2 고정 문장, SpeechLimits(길이 검사)
│   ├── SpeechConfig.kt                                  # 신규: 쿨다운, 대기 한도, 표시 시간, 글자 수 한도, 선행 무음(한 곳)
│   └── SpeechDirector.kt                                # 신규: 순수 규칙(쿨다운, 선점, 대기 슬롯, 변형 번호, 말풍선 지움)
└── test/java/com/example/dashpet/engine/speech/
    ├── SpeechCatalogTest.kt                             # 신규: 길이 한도, 이벤트 10종, 자리 표시자 없음
    └── SpeechDirectorTest.kt                            # 신규

signal/
├── build.gradle.kts                                     # 수정: libs.pleos.tts
└── src/
    ├── main/java/com/example/dashpet/signal/voice/
    │   ├── SpeechOutput.kt                              # 신규: 인터페이스, SpeechState, VoiceAvailability, VoiceConfig
    │   ├── AudioFocusGateway.kt                         # 신규: 인터페이스, FocusChange
    │   ├── SpeechPlayer.kt                              # 신규: 포커스 → 선행 무음 → speak → 반납 순서와 감시(순수 코루틴 로직)
    │   ├── AndroidAudioFocusGateway.kt                  # 신규: AudioManager 어댑터(포커스 요청 횟수와 반납 횟수 계수)
    │   └── PleosSpeechOutput.kt                         # 신규: Pleos TTS SDK 어댑터(internal, 이 파일만 SDK 사용)
    └── test/java/com/example/dashpet/signal/voice/
        ├── SpeechPlayerTest.kt                          # 신규: 포커스 4가지, 오류, 감시, 횟수 일치
        ├── FakeAudioFocusGateway.kt                     # 신규
        └── FakeSpeechOutput.kt                          # 신규

app/
├── src/main/AndroidManifest.xml                         # 수정: TTS_SERVICE 권한
└── src/main/java/com/example/dashpet/
    ├── ui/speech/
    │   ├── BubbleState.kt                               # 신규
    │   ├── SpeechBubble.kt                              # 신규: 애니메이션 없는 말풍선 Compose
    │   ├── SpeechCoordinator.kt                         # 신규: engine 이벤트와 출력 → SpeechDirector → SpeechOutput, 말풍선 상태
    │   └── VoiceSettings.kt                             # 신규: voice_enabled 저장(SharedPreferences 래퍼, 메모리 구현 포함)
    ├── ui/demo/DemoViewModel.kt                         # 수정: 코디네이터 소유, setMode 에서 reset, onForeground/onBackground 전달
    ├── ui/demo/DemoControls.kt                          # 수정: 음성 스위치(제한 중 비활성)
    └── ui/screens/HomeScreen.kt                         # 수정: 말풍선 표시, 팩토리에 음성 출력과 설정 전달
app/src/test/java/com/example/dashpet/ui/speech/
└── SpeechCoordinatorTest.kt                             # 신규: 생명주기, 음성 끄기, 제한 중 설정 무시, 모드 전환
```

**Structure Decision**: 기존 4개 모듈을 그대로 쓰고 새 모듈을 만들지 않는다. 문장과 규칙은 `:engine`(JVM 테스트 가능), 음성과 포커스는 `:signal` 의 `voice` 패키지, 말풍선과 연결은 `:app`. `:pet` 은 바꾸지 않는다. `:engine` 의 기존 판정, 점수, 모션 코드는 건드리지 않고 `speech` 패키지만 더한다(스펙 SC-008).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| `pleos.car.permission.TTS_SERVICE` 선언(승인 여부 미확인, 헌장 IV 는 "승인되지 않은 권한 선언 금지") | Pleos TTS SDK 의 초기화, 리스너 등록, 합성 요청이 모두 이 권한을 요구한다(`docs/02` 5절). 거부돼도 앱은 음성 없이 동작한다(FR-015) | 권한 없이 쓸 수 있는 방법은 표준 `android.speech.tts.TextToSpeech` 뿐이고, 이 이미지의 한국어 엔진 유무가 미확인이며 SDK 지침과 다르다. 구현은 `SpeechOutput` 뒤에 두므로 승인이 안 되면 그 구현으로 바꿀 수 있다. 승인 확인은 대회 측에 요청한다 |
| 선행 무음을 무음 `AudioTrack` 로 먼저 재생(SDK 에 PCM 을 직접 넣을 수 없음) | AGENTS.md 7절은 "오디오 데이터 레벨 무음, 재생 지연만으로 처리 금지". 같은 `AudioAttributes` 로 무음 PCM 을 먼저 흘려 오디오 경로를 연다 | 단순 `delay` 는 규칙이 금지한 방식이다. 표준 TTS 로 합성 파일에 무음을 붙이는 안은 SDK 를 쓰지 않게 되어 R1 과 어긋난다. 효과는 실차나 에뮬레이터 오디오로 확인해야 하므로 미결로 기록한다 |
| `signal.voice` 에 출력 쪽 코드를 둠(모듈 이름은 "신호") | 헌장 II 가 Pleos SDK 호출을 `:signal` 에만 두라고 한다 | 새 모듈 `:voice` 는 모듈 사슬(`:app → :pet → :engine → :signal`)을 바꿔야 하므로 헌장 개정이 먼저다. 이번에는 규칙을 그대로 지키고 개정 필요성만 기록한다 |
