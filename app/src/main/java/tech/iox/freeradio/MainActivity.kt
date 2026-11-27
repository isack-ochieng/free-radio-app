package tech.iox.freeradio

import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import tech.iox.freeradio.reminders.ShowReminderScheduler
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
        openReminderFrequency(intent)

        setContent {
            FreeRadioTheme {
                RadioApp(viewModel = radioViewModel)
            }
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openReminderFrequency(intent)
    }

    override fun onResume() {
        super.onResume()
        ShowReminderScheduler.rescheduleAll(this)
        radioViewModel.refreshReminders()
    }

    private fun openReminderFrequency(intent: android.content.Intent?) {
        if (intent?.action == ACTION_OPEN_REMINDER) {
            val frequency = intent.getFloatExtra(
                ShowReminderScheduler.EXTRA_FREQUENCY,
                98.4f
            )
            radioViewModel.tuneTo(frequency)
        }
    }

    companion object {
        const val ACTION_OPEN_REMINDER = "tech.iox.freeradio.action.OPEN_REMINDER"
    }
}
