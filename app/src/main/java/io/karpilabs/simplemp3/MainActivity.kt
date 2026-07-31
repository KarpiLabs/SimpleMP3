package io.karpilabs.simplemp3

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import io.karpilabs.simplemp3.ui.SimpleMP3AppRoot
import io.karpilabs.simplemp3.ui.theme.SimpleMP3Theme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            SimpleMP3Theme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SimpleMP3AppRoot()
                }
            }
        }
    }
}
