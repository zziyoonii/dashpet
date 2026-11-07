package com.example.dashpet

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.dashpet.ui.screens.HomeScreen
import com.example.dashpet.ui.theme.DashPetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 최상위 화면에서 Back 이 눌리면 finish() 대신 태스크를 백그라운드로 숨긴다 (CCP Back 규칙).
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })

        setContent {
            DashPetTheme {
                HomeScreen()
            }
        }
    }
}
