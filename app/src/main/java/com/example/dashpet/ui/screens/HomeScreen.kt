package com.example.dashpet.ui.screens

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dashpet.pet.PetAppearance
import com.example.dashpet.pet.PetController
import com.example.dashpet.pet.PetView
import com.example.dashpet.pet.Personality
import com.example.dashpet.pet.collectFrom
import com.example.dashpet.signal.car.CarSignalSource
import com.example.dashpet.ui.demo.DemoControls
import com.example.dashpet.ui.demo.DemoViewModel
import com.example.dashpet.ui.demo.SignalMode
import com.example.dashpet.ui.theme.DashPetTheme

private val DemoAppearance = PetAppearance(name = "장수", bodyColor = Color(0xFF4F7CFF), personality = Personality.STURDY)

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

    // 속도(CAR_SPEED)는 런타임 권한이다. 실차 모드로 열릴 때 한 번만 요청하고(회전해도 다시 묻지 않음),
    // 결과가 오면 소스를 다시 시작한다. 거부해도 앱은 속도 없이 동작한다.
    val speedPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        viewModel.onSpeedPermissionResult(granted)
    }
    var speedPermissionAsked by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val granted = context.checkSelfPermission(CarSignalSource.SPEED_PERMISSION) == PackageManager.PERMISSION_GRANTED
        if (!speedPermissionAsked && !granted && viewModel.demo.value.mode == SignalMode.VEHICLE) {
            speedPermissionAsked = true
            speedPermission.launch(CarSignalSource.SPEED_PERMISSION)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        PetView(controller = controller, appearance = DemoAppearance, modifier = Modifier.fillMaxSize())
        // 컨트롤은 아래쪽에 두고, 가로 화면에서는 폭을 제한한다. 내용이 많아지면 FlowRow 가 줄바꿈한다.
        DemoControls(
            demo = demo,
            output = output,
            totalMs = viewModel.totalMs,
            onTogglePlay = viewModel::togglePlay,
            onRestriction = viewModel::setRestriction,
            onPanelVisible = viewModel::setDebugPanelVisible,
            onMode = viewModel::setMode,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .safeDrawingPadding()
                .padding(8.dp)
                .widthIn(max = 640.dp)
                .fillMaxWidth()
                .wrapContentHeight(),
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640)
@Composable
private fun HomeScreenPreview() {
    DashPetTheme { HomeScreen() }
}
