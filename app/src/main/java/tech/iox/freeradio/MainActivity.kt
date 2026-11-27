package tech.iox.freeradio

import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import tech.iox.freeradio.ui.RadioApp
import tech.iox.freeradio.ui.theme.FreeRadioTheme
import tech.iox.freeradio.viewmodel.RadioViewModel

class MainActivity : ComponentActivity() {
    private val radioViewModel: RadioViewModel by viewModels {
        RadioViewModel.Factory(application)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        volumeControlStream = AudioManager.STREAM_MUSIC
        enableEdgeToEdge()

        setContent {
            FreeRadioTheme {
                RadioApp(viewModel = radioViewModel)
            }
        }
    }
}
