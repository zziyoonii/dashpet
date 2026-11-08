# Research: PetView

Phase 0 결과. 기준 구현은 캐릭터 시안(`docs/DashPet 캐릭터 시안.html`, 스크립트 약 390줄)과 개발 명세서 5장이다. 시안 스크립트를 끝까지 읽고 이식 기준과 차이를 정했다.
`docs/` 는 로컬 전용이라 시안 줄 번호는 참조용으로만 적는다. 기술 맥락의 `NEEDS CLARIFICATION` 은 없고, 확인이 필요한 값은 마지막 "미결 항목"에 모았다.

## R1. 무엇을 기준으로 이식하나 (패리티 범위)

- **Decision**: 풀 모션은 시안의 "주차(parked)" 표현을 기준으로 이식한다. 정적 등급은 시안의 "주행(moving)" 분기에서 **모든 움직임을 제거한 것**으로 새로 정의한다.
- **Rationale**: 시안의 `moving` 분기는 정적이 아니다. 물리(압력 상한 0.35, 쏠림)가 그대로 돌고, 윙크가 380ms 로 점멸하고, 기쁨 색이 `0.5 + 0.5·sin(10t)` 로 깜빡인다.
  이는 스펙의 정적 등급(반복 모션 없음, FR-005~006)과 헌장 I 에 어긋난다. 그래서 시안의 moving 모양(눈 하우징, 몸 단색, 윤곽선)만 가져오고 움직임은 모두 뺀다.
- **Alternatives considered**: 시안의 moving 분기를 그대로 이식(반복 모션 때문에 기각).
- **결과**: SC-001(시안과 나란히 비교)은 풀 모션에만 적용하고, 정적 등급은 `PetPose` 의 정의(R8)로 검증한다.

## R2. 모듈 구성

- **Decision**: `:pet` 안에서 순수 Kotlin(`SoftBody`, `PetPhysics`, `Particles`, `PetPose`, `MotionPolicy`, `PetController`)과 Compose(`PetPainter`, `PetView`)를 분리한다.
- **Rationale**: 물리와 매핑은 JVM 단위 테스트로 빠르게 검증하고(헌장 III), 화면 코드는 얇게 둔다. 개발 명세서 5-8 의 `PetController`(`corner`, `surge`, `jump`, `setMood`, `setTurnSignal`, `step`)와 `PetView` 구조를 따른다.
- **Alternatives considered**: 그리기와 물리를 한 클래스에 두기(테스트가 에뮬레이터에 묶임, 기각).

## R3. 엔진 출력과 이벤트의 매핑

- **Decision**: 상태(감정, 등급, 방향지시등)는 `PetOutput` 으로, 물리 반응(쏠림)은 `DrivingEvent` 로 받는다. 점프 같은 "감정 진입" 효과는 `PetOutput` 의 감정이 바뀌는 순간에 일으킨다.
  - `HARD_CORNER`: 방향 `RIGHT` → 시안 `corner(+1)`, `LEFT` → `corner(-1)`, 세기 `s` = 이벤트 강도.
  - `HARD_BRAKE`(방향 `FRONT`) → `surge(+1)`, `HARD_ACCEL`(`BACK`) → `surge(-1)`.
  - 감정이 `HAPPY` 로 진입: 점프(활발 820, 그 외 650 px/s, 760ms 뒤 아직 `HAPPY` 이고 바닥이면 620/480). `STARTLED` 로 진입: 위로 420, 가로 스케일 −1.5, 세로 −1 의 충격.
  - 정적 등급에서는 이벤트와 진입 효과를 모두 무시한다.
- **Rationale**: 엔진 `StateFlow` 는 "시각을 뺀 내용"이 바뀔 때만 갱신해서, 같은 감정의 연속 이벤트(예: 3.2초 안에 급제동 두 번)는 출력만으로는 구분되지 않는다.
  쏠림은 이벤트마다 일어나야 하므로 이벤트를 받는다. 시안도 물리는 항상 적용하고 감정은 우선순위로 정한다(`applyEv`).
- **방향 증거**: 시안 데모는 22초에 `cornerR`(우회전)을 쏘고 `corner(-1)` 로 왼쪽 패널에 누른다(`EV.cornerR.phys = s => corner(-1, s)`). 엔진 R5(우회전 → `LEFT`)와 일치한다.
  개발 명세서 9장의 "오른쪽으로 찌그러짐"은 "오른쪽 커브"를 뜻한 것으로 보인다. 엔진의 방위 부호 미결(001 research 미결 1)은 이 증거로 사실상 해소되지만, 데모 영상의 모양은 기획자가 한 번 확인한다.
- **Alternatives considered**: `PetOutput` 하나만 받기(같은 감정의 반복 쏠림을 놓침, 기각).

## R4. 프레임 루프

- **Decision**: `PetView` 가 `LaunchedEffect` 안에서 `withFrameNanos` 루프를 돌리되, `controller.needsFrames`(풀 모션) 이고 생명주기가 `STARTED` 일 때만 실행한다(`repeatOnLifecycle`). `dt = min(1/30초, 프레임 간격)`.
- **Rationale**: 시안도 `dt` 를 0.033초로 자른다(큰 `dt` 는 스프링을 폭주시킨다). 루프 실행 조건을 `MotionPolicy.shouldRunLoop` 순수 함수로 빼면 정적 등급과 백그라운드에서 멈추는 규칙을 단위 테스트로 고정할 수 있다.
- **Alternatives considered**: `rememberInfiniteTransition`(정적 등급에서도 계속 도는 위험, 기각), 항상 루프를 돌리고 내부에서 무시(CPU 낭비, 기각).

## R5. 정적 등급에서 화면 갱신 0회 보장

- **Decision**: 정적 등급은 `Canvas` 가 `PetRenderState`(스냅샷 상태)의 값만 읽고, 값은 `applyOutput` 에서만 바뀐다. 프레임 루프와 애니메이터가 없으므로 입력이 없으면 다시 그리지 않는다.
- **Rationale**: Compose 는 읽은 상태가 바뀔 때만 다시 그린다(SC-002).
- **검증 방법**: 에뮬레이터에서 `adb shell dumpsys gfxinfo <패키지> reset` 후 10초 대기, 다시 `dumpsys gfxinfo` 의 `Total frames rendered` 가 0 에 가까운지 확인한다(`quickstart.md`).
  JVM 에서는 `MotionPolicy` 와 `PetController.needsFrames` 로 "정적이면 루프 없음"을 고정한다.
- **Alternatives considered**: UI 테스트로 그리기 횟수 세기(에뮬레이터 필요, 이번 범위에서 도입하지 않음).

## R6. 물리 이식

- **Decision**: 시안의 `physics`, `softStep`, `kick` 을 `SoftBody` 와 `PetPhysics` 로 그대로 옮기고, 상수는 모두 `PetPhysicsConfig` 한 곳에 둔다(시안 줄 번호를 주석으로).
  - 소프트바디: 점 22개, `K = 110`, `C = 5`(모션 줄이기 16), `KN = 240`, 프레임당 3회 분할(semi-implicit Euler), 변위 제한 −0.32~0.42, `kick(m, amp, phase)`(모션 줄이기에서 `amp × 0.4`).
  - 좌우: 코너 힘 `2600 + 3400·s`, 지속 `500 + 800·s` ms, 스프링 `kx = 3/26`, 감쇠 `cx = 1.6/4.2`, 벽 접촉 `contact = max(20, W/2 − PANEL − rxMax)`, 침투 `R·(0.16 + 0.24·s)`, 코너 시작 `vx += dir·300`, 종료 시 `vx = −dir·(170 + 200·s)`.
  - 앞뒤: `z` 스프링 `−38·z − 4.2·vz`, 지속 힘 `dir·(55 + 50·s)`, 급제동 지속 `450 + 750·s`, 급가속 `400 + 650·s`, 충격 `vz += (2.6 + 2.2·s)`(앞) 또는 `−(1.8 + 1.6·s)`(뒤), 유리 접촉 `z > 1`, 등받이 `z < −0.75`.
  - 점프: 중력 2600 px/s², 착지 시 `imp = −vy` 로 스케일과 소프트바디에 충격.
  - 스케일 스프링: `k = 230`, `c = 9`(모션 줄이기 18), 감정별 목표 스케일(편안은 `sin(2.2t)` 호흡).
  - 반지름: 앱 홈 풀 모션 `min(W, H) × 0.21`, 정적 `× 0.17`(시안의 moving, "주행 중에는 미니 카드를 크게 키우지 않는다").
- **Rationale**: 개발 명세서 5장이 "기준 구현은 시안이고 수식과 상수를 그대로 옮긴다"고 정했다.
- **난수**: 시안은 `Math.random` 을 파티클, 깜빡임 간격, 얼룩 위상, 깜짝 떨림에 쓴다. `PetController` 가 `kotlin.random.Random` 을 주입받게 해서 테스트에서 시드를 고정한다.
- **Alternatives considered**: 물리 라이브러리 사용(과하고 시안과 느낌이 달라짐, 기각).

## R7. 그리기 이식과 글로우 처리

- **Decision**: `DrawScope` 로 시안의 `bodyPath`(점 22개의 중점 이차 곡선), 눈 하우징과 눈동자, LED 눈썹, 입, 유리 번짐, 반사광, 자국, 속도선을 옮긴다.
  시안의 `shadowBlur` 글로우는 Compose 에 같은 API 가 없어서 **넓은 반투명 선 + 좁은 밝은 선**을 겹쳐 그리거나 방사형 그라디언트 원으로 대체한다(`glow()` 는 이미 방사형 그라디언트라 그대로).
- **Rationale**: `Paint.setShadowLayer` 는 하드웨어 캔버스에서 도형에 안정적으로 동작하지 않는다. 겹쳐 그리는 방식이 에뮬레이터에서도 같은 결과를 낸다.
- **위험**: 글로우가 시안과 약간 다르게 보일 수 있다. 시각 비교 체크리스트의 눈 항목에서 확인하고 필요하면 선 두께와 알파를 조정한다.
- **Alternatives considered**: 소프트웨어 레이어 + `BlurMaskFilter`(프레임 비용이 커서 60fps 에 불리, 기각).

## R8. 정적 포즈

- **Decision**: `PetPose` 순수 함수가 (감정, 방향, 방향지시등, 외형)에서 정적 포즈를 정한다.
  - 몸: 단색(시안 moving 분기), 윤곽선 `rgba(255,255,255,.18)`, 눈은 어두운 하우징 다각형(동공 없음), 입 없음.
  - 색: 편안은 몸 색 그대로, 기쁨은 `#3ddc84` 와 0.9 로 혼합, 어지러움은 `#f5a524` 0.8, 깜짝은 `#ff5a5a` 0.85(시안의 moving 값. 기쁨의 깜빡임은 제거).
  - 찌그러짐: 몸 색 그대로이고 방향 쪽으로 기울어진 포즈. 좌/우는 몸 아래 중앙을 축으로 ±10° 회전, 앞은 가로세로 1.08 배, 뒤는 0.9 배로 낮게.
  - 방향지시등: 같은 쪽 눈을 선으로 감고 호박색(`#ffb020`) 눈썹 선을 정적으로 표시한다(점멸 없음).
  - 비(배경 감정): 우비 실루엣(시안의 `hood(flat)`).
- **Rationale**: 시안에 정적 포즈가 없어서 시안의 moving 기본 모양에서 움직임을 뺀 값으로 정의했다. 기울기와 크기 배율은 시안에 근거가 없는 **초안**이다(`[확인 필요]`).
- **Alternatives considered**: 찌그러짐을 정적 색으로만 표시(스펙 FR-005 가 "이벤트 방향으로 기울어진 포즈"를 요구, 기각).

## R9. 파티클

- **Decision**: 파티클 풀은 상한 60개이고 종류는 반짝임, 땀, 빗줄기, Z, 입김, 유리 물방울이다. 프레임 시간이 24ms 를 넘는 상태가 30프레임 연속되면 파티클 생성을 멈추고 그 뒤로 회복되면 다시 켠다.
  정적 등급과 모션 줄이기에서는 생성하지 않는다(시안도 `reduce` 이거나 주행이면 비 외에는 만들지 않음).
- **Rationale**: FR-014("유지하지 못하면 파티클과 부가 효과부터 줄인다"). 임계값 24ms 와 30프레임은 초안이며 에뮬레이터 측정 후 조정한다.
- **Alternatives considered**: 성능과 상관없이 항상 생성(느린 기기에서 핵심 반응이 끊김, 기각).

## R10. 모션 줄이기

- **Decision**: `ValueAnimator.areAnimatorsEnabled()` 가 `false` 이면 모션 줄이기로 본다(시스템 애니메이션 배율 0 또는 절전 모드). 앱이 시작될 때와 `STARTED` 로 돌아올 때 읽는다.
  모션 줄이기에서는 시안의 `reduce` 처리를 따른다: 소프트바디 감쇠 `C = 16`, 스케일 감쇠 `c = 18`, `kick` 진폭 ×0.4, 파티클과 속도선과 몸 흔들림을 생략.
- **Rationale**: 시안이 `prefers-reduced-motion` 에서 같은 값을 쓴다. Compose 1.7 에는 전용 API 가 없다.
- **Alternatives considered**: `Settings.Global.ANIMATOR_DURATION_SCALE` 직접 읽기(같은 정보지만 절전 모드를 놓침, 기각).

## R11. 생명주기

- **Decision**: `repeatOnLifecycle(Lifecycle.State.STARTED)` 안에서만 프레임 루프를 돌린다. 백그라운드로 가면 루프가 취소되어 시간 진행이 멈추고, 돌아오면 `dt` 를 새로 시작한다(첫 프레임의 `dt` 는 0 으로 처리).
- **Rationale**: FR-013, SC-007. 돌아온 첫 프레임에 큰 `dt` 가 들어가 물리가 튀는 것을 막는다.

## R12. 엔진 시간 진행 문제 (001 에서 드러난 빈틈)

- **Finding**: `DrivingEngine` 은 신호가 올 때만 `EngineCore.process` 를 호출하고, 코어의 시간은 신호의 시각으로만 진행한다. 데모 시나리오에서 22~23초 급커브 뒤 30초까지 신호가 없어서
  찌그러짐(1.2초)이 약 7초 동안 표시된다. 32초 기쁨도 38초까지, 38초 어지러움도 40초 이후까지 남는다.
- **Decision**: `RawSignal.Tick(timestampMs)` 를 추가한다. 코어는 `Tick` 을 `advanceTo(timestampMs)` 로 처리한다. `MockSignalSource` 는 시나리오 시각을 기준으로 100ms 간격 `Tick` 을
  신호 사이에 섞어 방출한다(같은 시각이면 신호 뒤에 `Tick`). 시나리오의 마지막 신호 뒤에도 감정이 만료되도록 3초 더 방출한다. 두 재생 속도(`REAL_TIME`, `IMMEDIATE`)가 같은 열을 낸다.
- **Rationale**: 틱을 소스가 자기 시간 기준으로 내면 엔진의 결정성(헌장 II, III)을 유지하면서 어떤 재생 속도에서도 맞다. 실제 소스(이후 기능)도 같은 규칙을 지켜야 한다(`contracts` 에 기록).
- **영향**: `Scenario.expand()` 와 `DemoScenarioTest` 는 그대로다(틱은 `MockSignalSource` 에서만 섞는다). `MockSignalSourceTest` 와 `EngineCore` 테스트에 틱 케이스를 추가한다.
- **Alternatives considered**: `DrivingEngine` 에 `delay` 타이머(결정성과 `IMMEDIATE` 재생을 깨서 기각), `PetController` 가 감정을 직접 만료(엔진 판단과 이중화되어 헌장 I 위반, 기각).

## R13. 수동 제한 상태 (`injectRestriction`)

- **Decision**: `DrivingEngine.injectRestriction(state)` 를 추가한다. 이 함수는 엔진이 마지막으로 처리한 신호 시각으로 `Restriction` 신호를 만들어 같은 처리 경로(한 코루틴, 순서 보장)에 넣는다.
  수동 값은 다음 시나리오 제한 신호가 올 때까지 유지된다. 재생이 멈춘 상태에서도 쓸 수 있다(시각은 마지막 값 유지).
- **Rationale**: 모션 등급 판단을 엔진 한 곳에 남기려는 것이다(헌장 I). 시각을 되돌리지 않는 엔진 규칙(과거 시각 무시)과 충돌하지 않는다.
- **Alternatives considered**: UI 에서 `tier` 덮어쓰기(판단이 둘이 되어 기각), 데모용 별도 엔진 인스턴스(재생 중 엔진과 상태가 갈라져 기각).

## R14. 데모 화면 (`DemoViewModel`)

- **Decision**: `DemoViewModel`(`ViewModel`)이 `MockSignalSource(demoCommute, REAL_TIME)` 와 `DrivingEngine` 을 소유한다. 화면 회전에도 재생이 이어진다(SC-008).
  재생/정지는 `DrivingEngine.start()/stop()`(새 세션)이고, 확인용 시나리오 시각은 ViewModel 의 경과 시간 타이머로 표시만 한다.
  데모 컨트롤과 확인용 패널은 앱이 디버그 가능 빌드일 때만 표시한다(`ApplicationInfo.FLAG_DEBUGGABLE`). 제품 화면으로 가기 전에 사라져야 한다.
- **Rationale**: 11/15 영상을 찍으려면 필요하고, 수동 제한 상태 전환(`injectRestriction`)은 제품 경로에 남으면 안 된다(헌장 I).
- **Alternatives considered**: `remember` 만 사용(회전 시 재생이 끊김, 기각), 별도 데모 Activity(`distractionOptimized` 선언 범위가 늘어남, 기각).

## R15. 텍스트와 이름 표시

- **Decision**: 이름 라벨은 `Canvas` 안이 아니라 Compose `Text`(Material3)로 표시한다. 크기 14sp, 대비는 시안 팔레트 기준 4.5:1 이상으로 설정한다.
- **Rationale**: FR-016. 직접 그리는 텍스트는 글꼴 크기, 대비 검증이 어렵다. 말풍선과 자막은 이번 범위가 아니다.

## R16. 테스트 전략

- **Decision**:
  - JVM 단위 테스트: `SoftBody`(감쇠, 제한, 방향 부호), `PetPhysics`(코너/급제동/급가속 반응 수식과 지속 시간, 벽·유리 접촉, 점프), `Particles`(상한, 성능 저하 시 생성 중단), `PetPose`(감정×방향×등급),
    `MotionPolicy`(루프 실행 조건, 모션 줄이기 보정), `PetController`(출력/이벤트 매핑, 정적에서 무시, 등급 전환 시 상태 초기화, 첫 프레임 `dt = 0`), `DemoViewModelTest`(재생/정지, 제한 주입).
  - 시각 검증: `checklists/visual-parity.md`(감정 5 × 방향 4 + 정적 5 + 윙크 + 외형 + 회전)를 시안과 나란히 보며 기획자가 승인한다.
  - 에뮬레이터 측정: `dumpsys gfxinfo` 로 정적 0 프레임과 풀 모션 fps 를 잰다.
- **Rationale**: 헌장 III 의 "UI 와 실제 SDK 연동은 에뮬레이터에서 확인하고 방법과 결과를 남긴다"를 따른다. UI 스냅샷 도구는 빌드 복잡도가 커서 11/15 일정에 맞지 않는다.

## R17. 의존성

- **Decision**: `:pet` 에 `androidx.compose.foundation`, `androidx.lifecycle:lifecycle-runtime-compose`(`repeatOnLifecycle`, `LocalLifecycleOwner`)를 추가한다. `:app` 에 `androidx.lifecycle:lifecycle-viewmodel-compose` 와
  테스트용 `kotlinx-coroutines-test`, `junit` 을 추가하되, 먼저 `./gradlew :app:dependencies` 로 이미 transitive 로 들어오는지 확인한다. 모두 compileSdk 34 와 호환되는 버전(기존에 해석된 lifecycle 2.8.3 계열)을 쓴다.
- **Rationale**: BOM 을 2025.01.01 로 내리며 정한 호환 범위를 깨지 않는다.

## 미결 항목

1. **[확인 필요] 정적 포즈 값.** 찌그러짐 기울기 ±10°, 앞 1.08 배, 뒤 0.9 배, 색 혼합 비율은 시안에 정적 포즈가 없어서 초안으로 정했다. 기획자와 함께 보고 조정한다(R8).
2. **[확인 필요] 에뮬레이터 성능.** 60fps 와 파티클 감축 임계값(24ms, 30프레임)은 에뮬레이터 GPU 성능을 확인한 뒤 조정한다(R9, SC-005).
3. **[확인 필요] 데모 급커브의 모양.** 엔진의 방위 부호는 시안 증거로 일치하지만(R3), 데모 영상에서 어느 쪽 벽에 눌리는지는 기획자가 최종 확인한다.
4. **[확인 필요] 글로우 근사.** 시안의 `shadowBlur` 를 겹쳐 그리기로 대체해서 눈 빛이 약간 다를 수 있다. 시각 비교에서 확인한다(R7).
