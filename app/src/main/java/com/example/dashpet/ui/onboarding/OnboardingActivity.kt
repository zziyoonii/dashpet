package com.example.dashpet.ui.onboarding

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.dashpet.ui.profile.SharedPrefsProfileStore
import com.example.dashpet.ui.theme.DashPetTheme

/**
 * 이름 짓기 온보딩 화면. 키보드 입력이 필요하므로 `distractionOptimized` 를 선언하지 않아 정차 중에만 열린다(헌장 I).
 * [com.example.dashpet.MainActivity] 가 제한이 없을 때만 띄운다. 결과는 `RESULT_OK`(저장함) 또는 `RESULT_CANCELED`(저장 안 함).
 */
class OnboardingActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = SharedPrefsProfileStore(this)

        // 이 화면은 최상위가 아니라 메인 화면 위에 열린 화면이라 Back 은 닫고 돌아간다(저장하지 않음).
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() = cancel()
        })

        setContent {
            DashPetTheme {
                OnboardingScreen(
                    onDone = { profile ->
                        store.save(profile)
                        setResult(RESULT_OK)
                        finish()
                    },
                    onCancel = ::cancel,
                )
            }
        }
    }

    private fun cancel() {
        setResult(RESULT_CANCELED)
        finish()
    }
}
