package tech.iox.freeradio.reminders

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.Ringtone
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import tech.iox.freeradio.MainActivity
import java.util.Locale

class ReminderAlertService : Service() {
    private var ringtone: Ringtone? = null
    private var activeNotificationId = NOTIFICATION_ID

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISMISS -> {
                stopAlert()
                return START_NOT_STICKY
            }
            ACTION_SNOOZE -> {
                val id = intent.getLongExtra(ShowReminderScheduler.EXTRA_REMINDER_ID, -1L)
                ShowReminderStore(this).find(id)?.let {
                    ShowReminderScheduler.scheduleSnooze(this, it)
                }
                stopAlert()
                return START_NOT_STICKY
            }
        }

        val reminderId = intent?.getLongExtra(ShowReminderScheduler.EXTRA_REMINDER_ID, -1L) ?: -1L
        val title = intent?.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "Radio show" }
        val frequency = intent?.getFloatExtra(ShowReminderScheduler.EXTRA_FREQUENCY, 0f) ?: 0f
        val isSnooze = intent?.getBooleanExtra(ShowReminderScheduler.EXTRA_IS_SNOOZE, false) ?: false
        activeNotificationId = NOTIFICATION_ID + (reminderId.toInt() and 0x0000FFFF)

        startForeground(
            activeNotificationId,
            buildNotification(reminderId, title, frequency, isSnooze)
        )
        startRinging()
        return START_NOT_STICKY
    }

    private fun startRinging() {
        stopRinging()
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            ?: return

        ringtone = RingtoneManager.getRingtone(applicationContext, alarmUri)?.apply {
            audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) isLooping = true
            play()
        }
    }

    private fun stopRinging() {
        ringtone?.stop()
        ringtone = null
    }

    private fun stopAlert() {
        stopRinging()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(
        reminderId: Long,
        title: String,
        frequency: Float,
        isSnooze: Boolean
    ): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_REMINDER
            putExtra(ShowReminderScheduler.EXTRA_FREQUENCY, frequency)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            reminderId.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(title)
            .setContentText(
                String.format(
                    Locale.getDefault(),
                    "%s · %.1f MHz",
                    if (isSnooze) "Reminder" else "It's time",
                    frequency
                )
            )
            .setContentIntent(openPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setOngoing(true)
            .setAutoCancel(false)
            .addAction(
                android.R.drawable.ic_lock_idle_alarm,
                "Snooze 5 min",
                serviceAction(ACTION_SNOOZE, reminderId)
            )
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Dismiss",
                serviceAction(ACTION_DISMISS, reminderId)
            )
            .build()
    }

    private fun serviceAction(action: String, reminderId: Long): PendingIntent {
        val actionIntent = Intent(this, ReminderAlertService::class.java).apply {
            this.action = action
            putExtra(ShowReminderScheduler.EXTRA_REMINDER_ID, reminderId)
        }
        return PendingIntent.getService(
            this,
            action.hashCode() xor reminderId.toInt(),
            actionIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Show reminders",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Ringing alerts for saved radio shows"
            setSound(null, null)
            enableVibration(false)
            setShowBadge(true)
        }
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    override fun onDestroy() {
        stopRinging()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val ACTION_RING = "tech.iox.freeradio.reminder.RING"
        const val ACTION_SNOOZE = "tech.iox.freeradio.reminder.SNOOZE"
        const val ACTION_DISMISS = "tech.iox.freeradio.reminder.DISMISS"
        const val EXTRA_TITLE = "reminder_title"

        private const val CHANNEL_ID = "radio_show_reminders"
        private const val NOTIFICATION_ID = 7300
    }
}


object ReminderFallbackNotifier {
    private const val CHANNEL_ID = "radio_show_reminders_fallback"
    private const val ID_BASE = 18000

    fun show(context: Context, reminder: ShowReminder, isSnooze: Boolean) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Show reminder alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Sound and vibration for scheduled radio shows"
                if (alarmUri != null) {
                    setSound(
                        alarmUri,
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                }
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 650, 300, 650, 300, 900)
            }
            manager.createNotificationChannel(channel)
        }

        val notificationId = ID_BASE + (reminder.id.toInt() and 0x0000FFFF)
        val openIntent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_OPEN_REMINDER
            putExtra(ShowReminderScheduler.EXTRA_FREQUENCY, reminder.frequencyMhz)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            reminder.id.toInt(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozeIntent = Intent(context, ReminderSnoozeReceiver::class.java).apply {
            putExtra(ShowReminderScheduler.EXTRA_REMINDER_ID, reminder.id)
        }
        val dismissIntent = Intent(context, ReminderDismissReceiver::class.java).apply {
            putExtra(ShowReminderScheduler.EXTRA_REMINDER_ID, reminder.id)
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.toInt() xor 0x310000,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val dismissPendingIntent = PendingIntent.getBroadcast(
            context,
            reminder.id.toInt() xor 0x420000,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle(reminder.title)
            .setContentText(
                String.format(
                    Locale.getDefault(),
                    "%s · %.1f MHz",
                    if (isSnooze) "Reminder" else "It's time",
                    reminder.frequencyMhz
                )
            )
            .setContentIntent(contentPendingIntent)
            .setDeleteIntent(dismissPendingIntent)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setAutoCancel(true)
            .addAction(android.R.drawable.ic_lock_idle_alarm, "Snooze 5 min", snoozePendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Dismiss", dismissPendingIntent)
            .build()

        manager.notify(notificationId, notification)
    }

    fun dismiss(context: Context, reminderId: Long) {
        val notificationId = ID_BASE + (reminderId.toInt() and 0x0000FFFF)
        context.getSystemService(NotificationManager::class.java).cancel(notificationId)
    }
}
