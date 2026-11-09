package com.example.dashpet.ui.screens

import android.content.pm.ApplicationInfo
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dashpet.pet.PetAppearance
import com.example.dashpet.pet.PetController
import com.example.dashpet.pet.PetView
import com.example.dashpet.pet.Personality
import com.example.dashpet.pet.collectFrom
import com.example.dashpet.ui.demo.DemoControls
import com.example.dashpet.ui.demo.DemoViewModel
import com.example.dashpet.ui.theme.DashPetTheme

private val DemoAppearance = PetAppearance(name = "장수", bodyColor = Color(0xFF4F7CFF), personality = Personality.STURDY)

@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val debuggable = (context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    val viewModel: DemoViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                DemoViewModel(debugAvailable = debuggable) as T
        },
    )
    val controller = remember { PetController() }
    LaunchedEffect(viewModel) { controller.collectFrom(viewModel.engine) }

    val demo by viewModel.demo.collectAsState()
    val output by viewModel.output.collectAsState()

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
