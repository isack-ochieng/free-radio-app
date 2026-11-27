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
        try {
            ContextCompat.startForegroundService(context, alertIntent)
        } catch (_: RuntimeException) {
            // Android can block a background foreground-service launch when only
            // an inexact alarm is available. Preserve the reminder as a ringing notification.
            ReminderFallbackNotifier.show(context, reminder, isSnooze)
        }
    }
}

class ReminderSnoozeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ShowReminderScheduler.EXTRA_REMINDER_ID, -1L)
        val reminder = ShowReminderStore(context).find(id) ?: return
        ShowReminderScheduler.scheduleSnooze(context, reminder)
        ReminderFallbackNotifier.dismiss(context, reminder.id)
    }
}

class ReminderDismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(ShowReminderScheduler.EXTRA_REMINDER_ID, -1L)
        ReminderFallbackNotifier.dismiss(context, id)
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
