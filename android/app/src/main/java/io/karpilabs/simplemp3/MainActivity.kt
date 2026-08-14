package io.karpilabs.simplemp3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import io.karpilabs.simplemp3.data.prefs.AppPreferences
import io.karpilabs.simplemp3.data.prefs.ThemeMode
import io.karpilabs.simplemp3.ui.SimpleMP3AppRoot
import io.karpilabs.simplemp3.ui.theme.SimpleMP3Theme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var appPreferences: AppPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by appPreferences.themeModeFlow.collectAsState(initial = ThemeMode.SYSTEM)
            SimpleMP3Theme(themeMode = themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SimpleMP3AppRoot()
                }
            }
        }
    }
}
