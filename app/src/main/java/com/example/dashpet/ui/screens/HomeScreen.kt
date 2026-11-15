package com.example.dashpet.ui.screens

import android.app.Activity
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dashpet.engine.MotionTier
import com.example.dashpet.pet.AmbientMood
import com.example.dashpet.pet.PetAppearance
import com.example.dashpet.pet.PetController
import com.example.dashpet.pet.PetView
import com.example.dashpet.pet.collectFrom
import com.example.dashpet.signal.car.CarSignalSource
import com.example.dashpet.signal.voice.createPleosSpeechOutput
import com.example.dashpet.ui.speech.SharedPrefsVoiceSettings
import com.example.dashpet.ui.speech.SpeechBubble
import com.example.dashpet.ui.demo.DemoControls
import com.example.dashpet.ui.demo.ReleaseControls
import com.example.dashpet.ui.onboarding.OnboardingActivity
import com.example.dashpet.ui.profile.SharedPrefsProfileStore
import com.example.dashpet.ui.profile.toAppearance
import com.example.dashpet.ui.report.ArrivalReportCard
import com.example.dashpet.ui.demo.DemoViewModel
import com.example.dashpet.ui.demo.SignalMode
import com.example.dashpet.ui.theme.DashPetTheme

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val viewModel: DemoViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                val vehicle = CarSignalSource.create(context, log = { Log.i("CarSignal", it) })
                return DemoViewModel(
                    vehicleSource = vehicle,
                    debugAvailable = debuggable,
                    diagnostics = vehicle.diagnostics,
                    log = { Log.i("CarSignal", it) },
                    speechFactory = { scope -> createPleosSpeechOutput(context, scope, log = { Log.i("Speech", it) }) },
                    voiceSettings = SharedPrefsVoiceSettings(context),
                    speechLog = { Log.i("Speech", it) },
                    profileStore = SharedPrefsProfileStore(context),
                ) as T
            }
        },
    )
    // 실차 신호는 화면에 보이는 동안에만 받는다. 홈이나 다른 앱으로 가면 수신을 멈추고 차량 서비스 연결을 정리하고,
    // 다시 보이면 현재 값부터 새로 시작한다. 화면 회전으로 다시 만들어져도 ViewModel 이 중복 시작을 막는다.
    LifecycleStartEffect(viewModel) {
        viewModel.onForeground()
        onStopOrDispose { viewModel.onBackground() }
    }

    val controller = remember { PetController() }
    LaunchedEffect(viewModel) { controller.collectFrom(viewModel.engine) }

    val demo by viewModel.demo.collectAsState()
    val output by viewModel.output.collectAsState()
    val bubble by viewModel.speech.bubble.collectAsState()
    val voiceEnabled by viewModel.speech.voiceEnabled.collectAsState()
    val profile by viewModel.profile.collectAsState()
    val report by viewModel.report.collectAsState()

    // 날씨 반응(더위, 추위, 비, 조용)은 데모에서 시연한다. 실차에는 연결하지 않았으므로 실차 모드로 가면 끈다.
    var ambient by rememberSaveable { mutableStateOf<String?>(null) }
    val ambientMood = ambient?.let { runCatching { AmbientMood.valueOf(it) }.getOrNull() }
    LaunchedEffect(ambientMood, demo.mode) {
        controller.setAmbient(if (demo.mode == SignalMode.DEMO) ambientMood else null)
    }

    // 이름 짓기 온보딩은 제한이 없는 정차 중에만 연다(입력 화면, 헌장 I). 저장하고 돌아오면 프로필을 다시 읽고 첫 인사를 한다.
    val onboarding = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) viewModel.onProfileSaved()
    }
    val openOnboarding = { onboarding.launch(Intent(context, OnboardingActivity::class.java)) }
    var onboardingAsked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(profile, output.tier) {
        if (profile == null && !onboardingAsked && output.tier == MotionTier.FULL) {
            onboardingAsked = true // 나중에 하기를 눌러도 이번 실행에서는 다시 묻지 않는다
            openOnboarding()
        }
    }

    // 런타임 권한은 실차 모드로 열릴 때 한 번만 요청한다(회전해도 다시 묻지 않음). 거부해도 앱은 해당 신호 없이 동작한다.
    // - 속도(CAR_SPEED): 급가속. 처음부터 요청한다.
    // - 방위각(FUSED_LOCATION): 급커브. 위치 권한 그룹이라 동의 화면에서 "동의하고 시작"을 누른 뒤에만 요청한다.
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        viewModel.onSpeedPermissionResult(true) // 소스를 다시 시작해 허용된 신호를 읽는다(거부된 신호는 진단에 이유가 보인다)
    }
    var speedAsked by rememberSaveable { mutableStateOf(false) }
    var headingAsked by rememberSaveable { mutableStateOf(false) }
    val consented = profile?.consented == true
    LaunchedEffect(consented, demo.mode) {
        if (demo.mode != SignalMode.VEHICLE) return@LaunchedEffect
        fun granted(p: String) = context.checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED
        val missing = buildList {
            if (!speedAsked && !granted(CarSignalSource.SPEED_PERMISSION)) add(CarSignalSource.SPEED_PERMISSION)
            if (consented && !headingAsked && !granted(CarSignalSource.HEADING_PERMISSION)) add(CarSignalSource.HEADING_PERMISSION)
        }
        if (missing.isEmpty()) return@LaunchedEffect
        if (CarSignalSource.SPEED_PERMISSION in missing) speedAsked = true
        if (CarSignalSource.HEADING_PERMISSION in missing) headingAsked = true
        permissions.launch(missing.toTypedArray())
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PetView(controller = controller, appearance = profile?.toAppearance() ?: PetAppearance(), modifier = Modifier.fillMaxSize())
        // 말풍선은 위쪽에 둔다. 제한 중에도 전체 문장을 보이고(애니메이션 없음), 말풍선이 없으면 아무것도 그리지 않는다.
        SpeechBubble(
            state = bubble,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .safeDrawingPadding()
                .padding(16.dp),
        )
        // 컨트롤은 아래쪽에 두고, 가로 화면에서는 폭을 제한한다. 내용이 많아지면 FlowRow 가 줄바꿈한다.
        DemoControls(
            demo = demo,
            output = output,
            totalMs = viewModel.totalMs,
            onTogglePlay = viewModel::togglePlay,
            onRestriction = viewModel::setRestriction,
            onPanelVisible = viewModel::setDebugPanelVisible,
            onMode = viewModel::setMode,
            voiceEnabled = voiceEnabled,
            onVoiceEnabled = { viewModel.setVoiceEnabled(it) },
            onDebugSpeak = viewModel::debugSpeak,
            ambient = ambientMood,
            onAmbient = { ambient = it?.name },
            onRename = { if (output.tier == MotionTier.FULL) openOnboarding() },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(8.dp)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .wrapContentHeight(),
        )
        // 제출용 빌드의 작은 시연 컨트롤. 정차 중에만 보이고 제한이 걸리면 사라진다.
        ReleaseControls(
            demo = demo,
            output = output,
            ambient = ambientMood,
            voiceEnabled = voiceEnabled,
            onMode = viewModel::setMode,
            onTogglePlay = viewModel::togglePlay,
            onAmbient = { ambient = it?.name },
            onVoiceEnabled = { viewModel.setVoiceEnabled(it) },
            onRename = openOnboarding,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(8.dp)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .wrapContentHeight(),
        )
        // 도착 리포트: P단 뒤 제한이 풀린 정차 중에만 보인다(제한이 걸리면 사라지고, 확인을 누르면 닫힌다).
        report?.let { r ->
            if (output.tier == MotionTier.FULL) {
                ArrivalReportCard(
                    report = r,
                    petName = profile?.name ?: PetAppearance.DEFAULT_NAME,
                    onDismiss = viewModel::dismissReport,
                    modifier = Modifier.align(Alignment.TopCenter).safeDrawingPadding().padding(16.dp), // 펫의 얼굴을 가리지 않게 위쪽에 둔다
                )
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun HomeScreenPreview() {
    DashPetTheme { HomeScreen() }
}
