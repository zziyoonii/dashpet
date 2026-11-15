# DashPet

내가 이름 지어준 내 차가 펫이 되어, 차량이 보내는 경고와 내 운전에 먼저 반응하고 함께 자라는 안전·감성 케어 앱입니다.

Pleos Connect(Android Automotive OS 기반) 차량용 앱입니다.

> **현재 구현 범위** (2026-11-15 기준)
> - 구현: 주행 이벤트 판정(급가속·급제동·급커브·경고·차선 변경), 펫 감정과 물리 반응 그리기, 주행 제한 중 정적 포즈, 실차 신호 연결(속도·기어·주행 제한), 말풍선과 기기 내 TTS 음성, 데모 시나리오 재생.
> - 아직 없음: 이름 짓기 온보딩, 성장과 점수, 도착 리포트 화면, 둥둥이(작은 창)와 멀티 윈도우 카드. 이번 범위에서 제외했습니다.
> - 주행 제한이 걸려 있거나 알 수 없으면 펫은 움직이지 않는 정적 포즈와 색 변화만 보여주고, 말풍선 글자 없이 짧은 음성 한 문장만 냅니다.

## 실행 방법

1. 저장소를 받습니다.
   ```bash
   git clone https://github.com/zziyoonii/dashpet.git
   cd dashpet
   ```
2. JDK 를 지정합니다. 시스템 JDK 가 없으면 Android Studio 내장 JDK 를 씁니다(이 JDK 로 빌드와 테스트를 했습니다).
   ```bash
   export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
   ```
3. Pleos Connect 에뮬레이터를 Cold boot 로 띄우고 Virtual Console 포트를 연결합니다.
   ```bash
   ~/Library/Android/sdk/emulator/emulator -avd Pleos_Connect_v2.1.0 -no-snapshot-load -memory 4096 &
   adb wait-for-device
   adb forward tcp:8088 tcp:8088      # Virtual Console: http://127.0.0.1:8088/
   ```
4. 빌드하고 설치합니다.
   ```bash
   ./gradlew :app:assembleDebug
   adb install -r --user 10 app/build/outputs/apk/debug/app-debug.apk
   ```
5. (선택) 속도 권한을 미리 허용합니다. 허용하지 않으면 앱이 처음 열릴 때 권한 대화상자가 뜨고, 거부해도 속도 없이 동작합니다.
   ```bash
   adb shell pm grant --user 10 com.example.dashpet android.car.permission.CAR_SPEED
   ```
6. 앱을 실행합니다.
   ```bash
   adb shell am start --user 10 -n com.example.dashpet/.MainActivity
   ```

사용법(디버그 빌드):

- 처음에는 **실차** 모드입니다. Virtual Console 에서 기어를 P, R, N, D 로 바꾸면 주행 제한 상태가 바뀌고 펫이 따라 바뀝니다. 화면 아래 확인용 패널에 연결 상태, 제한, 속도, 기어가 보입니다.
- **데모** 칩을 누르면 55초짜리 시나리오를 **재생**할 수 있습니다. 데모에서는 **제한 없음 / 있음 / 알 수 없음** 으로 제한 상태를 직접 바꾸고, **말하기** 로 이벤트를 하나씩 말하게 할 수 있습니다.
- **음성** 스위치로 음성을 켜고 끕니다(제한이 없을 때만 바꿀 수 있습니다).
- 확인용 컨트롤은 디버그 빌드에만 있습니다.

자동 테스트(차량 없이):

```bash
./gradlew :signal:testDebugUnitTest :engine:testDebugUnitTest :pet:testDebugUnitTest :app:testDebugUnitTest
```

## 테스트 환경 및 필요한 설정

| 항목 | 값 |
|-|-|
| 빌드 도구 | Gradle 9.6.0, Android Gradle Plugin 9.5.0, Kotlin 2.2.10, Compose BOM 2025.01.01 |
| Android | compileSdk 34, targetSdk 34, minSdk 29 |
| 앱 | `com.example.dashpet`, versionCode 1, versionName 1.0 |
| 모듈 | `:app` → `:pet` → `:engine` → `:signal` |

필요한 설정:

- **Pleos Maven 저장소**: `settings.gradle.kts` 에 이미 들어 있습니다(`https://nexus-playground.pleos.ai/repository/maven-releases/`). 첫 빌드에는 네트워크가 필요합니다.
- **TTS**: `ai.pleos.playground:TextToSpeech:3.3.1`, 기기 내(ON_DEVICE) 모드입니다. Client ID/Secret 이 필요 없고 저장소에 비밀값이 없습니다. 에뮬레이터에서는 TTS 서비스(`ai.pleos.playground.caas`)가 켜져 있어야 합니다. 꺼져 있으면 음성만 건너뛰고 말풍선은 정상입니다.
- **권한**: `android.car.permission.CAR_SPEED`(속도, 런타임 요청), `android.car.permission.CAR_POWERTRAIN`(기어), `pleos.car.permission.TTS_SERVICE`. 위치와 개인정보는 읽거나 저장하지 않습니다. `CAR_SPEED` 는 위치 권한 그룹이라 대화상자에 "위치 정보"로 표시됩니다. **[확인 필요]** 대회 측이 `android.car.permission.*` 선언을 허용하는지와 이 표시 문제의 심사 대응.
- **Virtual Console**: 기어와 속도를 바꾸려면 `adb forward tcp:8088 tcp:8088` 이 필요합니다. 브라우저 탭 없이 쓰려면 콘솔 페이지가 보내는 하트비트(`POST /browser/session?client=<id>`)를 대신 보내야 합니다.
- **소리**: 에뮬레이터를 `-no-audio` 로 확인해서 실제 소리는 듣지 못했습니다. **[확인 필요]** 실기기에서 소리와 오디오 포커스 동작.

## 개발에 사용한 Pleos Connect 에뮬레이터 버전과 화면 규격

| 항목 | 값 |
|-|-|
| AVD | `Pleos_Connect_v2.1.0` |
| 시스템 이미지 | Android 14(API 34), `pleos_car_emulator`, `arm64-v8a` (Android Automotive) |
| 에뮬레이터 도구 | Android Emulator 37.2.13.0 |
| 해상도와 밀도 | 2560 × 1440 px, 160 dpi (1x) |
| RAM | 4096 MB |
| 부팅 | Cold boot (`-no-snapshot-load`) |
| 화면 구성 | 왼쪽 클러스터, 오른쪽 앱 영역, 아래 GNB (가로 2 윈도우) |
| 앱 영역 | 약 1682 × 1210 px (`dumpsys` 로 측정한 창 크기) |

- 가로 2 윈도우에서 `screenOrientation` 을 지정하지 않습니다. **[확인 필요]** 세로(1 윈도우) 확인은 하지 못했습니다. 이 에뮬레이터는 `wm size` 나 회전을 바꾸면 시스템 창 배치가 다시 계산되지 않아 앱이 화면 밖으로 밀립니다. `wm density` 변경에서는 상태가 유지됩니다.
- 에뮬레이터가 느릴 때(load average 10 이상) 프레임이 크게 떨어집니다. 성능 수치는 부하가 낮을 때 측정한 값(약 58 fps)을 기준으로 하되 실차에서 다시 재야 합니다.

## APK 파일 위치 또는 GitHub Release 링크

- **release APK (제출용)**: [GitHub Release v1.0](https://github.com/zziyoonii/dashpet/releases/tag/v1.0) 의 `Crevis_DashPet_release.apk` 입니다. 정식 키로 서명했고(`CN=DashPet`), `debuggable` 이 꺼져 있으며 버전은 1.0(versionCode 1)입니다. SHA-256 과 설치 방법은 Release 노트에 있습니다.
- **직접 빌드**: `./gradlew :app:assembleRelease` 를 하면 `app/build/outputs/apk/release/app-release.apk` 가 생깁니다. 서명하려면 저장소에 올리지 않는 `keystore.properties`(`storeFile`, `storePassword`, `keyAlias`, `keyPassword`)가 필요하고, 없으면 서명 없이 `app-release-unsigned.apk` 가 나옵니다(설치되지 않음). 서명 키는 개발자 PC 에만 있습니다.
- **디버그 APK**: 위 4번 빌드 뒤 `app/build/outputs/apk/debug/app-debug.apk` 에 생깁니다. 빌드 산출물이라 저장소에는 커밋하지 않습니다.
- release APK 에는 확인용 컨트롤(데모 재생, 제한 상태 버튼, 말하기, 음성 스위치)이 없습니다. 이 컨트롤은 디버그 빌드에만 있습니다. **[확인 필요]** 대회 측이 요구하는 서명 방식(App Market 업로드 시 재서명 여부 등)은 문서에 없습니다.
