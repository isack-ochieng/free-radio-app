package tech.iox.freeradio.reminders

import android.app.AlarmManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class ShowReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderId = intent.getLongExtra(ShowReminderScheduler.EXTRA_REMINDER_ID, -1L)
        if (reminderId < 0L) return

        val reminder = ShowReminderStore(context).find(reminderId) ?: return
        if (!reminder.enabled) return

        val isSnooze = intent.getBooleanExtra(ShowReminderScheduler.EXTRA_IS_SNOOZE, false)
        if (!isSnooze) {
            // Queue the next weekly occurrence before raising the current alert.
            ShowReminderScheduler.schedule(context, reminder)
        }

        val alertIntent = Intent(context, ReminderAlertService::class.java).apply {
            action = ReminderAlertService.ACTION_RING
            putExtra(ShowReminderScheduler.EXTRA_REMINDER_ID, reminder.id)
            putExtra(ShowReminderScheduler.EXTRA_FREQUENCY, reminder.frequencyMhz)
            putExtra(ReminderAlertService.EXTRA_TITLE, reminder.title)
            putExtra(ShowReminderScheduler.EXTRA_IS_SNOOZE, isSnooze)
        }
        ContextCompat.startForegroundService(context, alertIntent)
    }
}

class ReminderRescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED -> {
                ShowReminderScheduler.rescheduleAll(context)
            }
        }
    }
}
