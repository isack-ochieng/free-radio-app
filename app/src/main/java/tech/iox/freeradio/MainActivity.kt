package tech.iox.freeradio

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
        enableEdgeToEdge()

        setContent {
            FreeRadioTheme {
                RadioApp(viewModel = radioViewModel)
            }
        }
    }
}
