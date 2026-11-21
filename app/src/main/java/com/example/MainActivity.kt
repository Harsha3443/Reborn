package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.harsha.reborn.MainUiState
import com.harsha.reborn.MainViewModel
import com.harsha.reborn.RebornRoot
import com.harsha.reborn.ui.theme.RebornTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Install Android 12+ SplashScreen before super.onCreate() so the Phoenix icon transitions smoothly.
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        splashScreen.setKeepOnScreenCondition {
            mainViewModel.uiState.value is MainUiState.Loading
        }

        setContent {
            val uiState by mainViewModel.uiState.collectAsStateWithLifecycle()
            if (uiState is MainUiState.Ready) {
                val prefs = (uiState as MainUiState.Ready).preferences
                RebornTheme(
                    themeMode = prefs.themeMode,
                    dynamicColor = prefs.dynamicColorEnabled,
                ) {
                    RebornRoot(onboardingCompleted = prefs.onboardingCompleted)
                }
            }
        }
    }
}
