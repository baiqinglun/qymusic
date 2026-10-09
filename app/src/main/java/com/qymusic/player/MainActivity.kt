package com.qymusic.player

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.qymusic.player.ui.MusicViewModel
import com.qymusic.player.ui.QYMusicApp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.qymusic.player.ui.theme.QYMusicTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        installSplashScreen()
        enableEdgeToEdge()
        setContent {
            val viewModel: MusicViewModel = viewModel()
            val settings by viewModel.settings.collectAsState()
            QYMusicTheme(
                themeMode = settings.themeMode,
                themeColor = settings.themeColor,
            ) {
                QYMusicApp(viewModel = viewModel)
            }
        }
    }
}
