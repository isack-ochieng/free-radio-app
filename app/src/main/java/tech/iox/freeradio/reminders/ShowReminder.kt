package tech.iox.freeradio.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

data class ShowReminder(
    val id: Long,
    val title: String,
    val frequencyMhz: Float,
    val dayOfWeek: Int,
    val hour: Int,
    val minute: Int,
    val enabled: Boolean = true
) {
    fun timeLabel(): String {
        val hour12 = when (hour % 12) { 0 -> 12; else -> hour % 12 }
        val minuteLabel = minute.toString().padStart(2, '0')
        val period = if (hour < 12) "AM" else "PM"
        return "$hour12:$minuteLabel $period"
    }

    fun dayLabel(): String = DAYS.firstOrNull { it.first == dayOfWeek }?.second ?: "Weekly"

    companion object {
        val DAYS = listOf(
            Calendar.SUNDAY to "Sunday",
            Calendar.MONDAY to "Monday",
            Calendar.TUESDAY to "Tuesday",
            Calendar.WEDNESDAY to "Wednesday",
            Calendar.THURSDAY to "Thursday",
            Calendar.FRIDAY to "Friday",
            Calendar.SATURDAY to "Saturday"
        )
    }
}

class ShowReminderStore(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun all(): List<ShowReminder> {
        val raw = preferences.getString(KEY_REMINDERS, "[]") ?: "[]"
        return runCatching {
            val json = JSONArray(raw)
            buildList {
                for (index in 0 until json.length()) {
                    val item = json.optJSONObject(index) ?: continue
                    val frequency = item.optDouble("frequency", Double.NaN).toFloat()
                    val day = item.optInt("day", Calendar.FRIDAY)
                    val hour = item.optInt("hour", 13)
                    val minute = item.optInt("minute", 30)
                    if (frequency !in 87.5f..108.0f ||
                        day !in Calendar.SUNDAY..Calendar.SATURDAY ||
                        hour !in 0..23 || minute !in 0..59
                    ) continue
                    add(
                        ShowReminder(
                            id = item.optLong("id"),
                            title = item.optString("title").ifBlank { "Radio show" },
                            frequencyMhz = (frequency * 10f).toInt() / 10f,
                            dayOfWeek = day,
                            hour = hour,
                            minute = minute,
                            enabled = item.optBoolean("enabled", true)
                        )
                    )
                }
            }.distinctBy { it.id }.sortedWith(
                compareBy<ShowReminder> { it.dayOfWeek }.thenBy { it.hour }.thenBy { it.minute }
            )
        }.getOrDefault(emptyList())
    }

    @Synchronized
    fun find(id: Long): ShowReminder? = all().firstOrNull { it.id == id }

    @Synchronized
    fun save(reminder: ShowReminder) {
        val updated = (all().filterNot { it.id == reminder.id } + reminder)
            .sortedWith(compareBy<ShowReminder> { it.dayOfWeek }.thenBy { it.hour }.thenBy { it.minute })
        val json = JSONArray()
        updated.forEach { item ->
            json.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("frequency", item.frequencyMhz.toDouble())
                    .put("day", item.dayOfWeek)
                    .put("hour", item.hour)
                    .put("minute", item.minute)
                    .put("enabled", item.enabled)
            )
        }
        preferences.edit().putString(KEY_REMINDERS, json.toString()).apply()
    }

    @Synchronized
    fun delete(id: Long) {
        val updated = all().filterNot { it.id == id }
        val json = JSONArray()
        updated.forEach { item ->
            json.put(
                JSONObject()
                    .put("id", item.id)
                    .put("title", item.title)
                    .put("frequency", item.frequencyMhz.toDouble())
                    .put("day", item.dayOfWeek)
                    .put("hour", item.hour)
                    .put("minute", item.minute)
                    .put("enabled", item.enabled)
            )
        }
        preferences.edit().putString(KEY_REMINDERS, json.toString()).apply()
    }

    companion object {
        private const val PREFERENCES_NAME = "radio_show_reminders"
        private const val KEY_REMINDERS = "items"
    }
}

object ShowReminderScheduler {
    const val EXTRA_REMINDER_ID = "reminder_id"
    const val EXTRA_FREQUENCY = "reminder_frequency"
    const val EXTRA_IS_SNOOZE = "is_snooze"

    fun canScheduleExact(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
    }

    /** Returns true when Android accepted an exact, rather than approximate, schedule. */
    fun schedule(context: Context, reminder: ShowReminder): Boolean {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = alarmPendingIntent(context, reminder.id, snooze = false)
        if (!reminder.enabled) {
            alarmManager.cancel(pendingIntent)
            return true
        }

        val triggerAt = nextOccurrence(reminder)
        val exactAllowed = canScheduleExact(context)
        if (exactAllowed) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        }
        return exactAllowed
    }

    fun cancel(context: Context, id: Long) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        alarmManager.cancel(alarmPendingIntent(context, id, snooze = false))
        alarmManager.cancel(alarmPendingIntent(context, id, snooze = true))
    }

    fun scheduleSnooze(context: Context, reminder: ShowReminder) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val pendingIntent = alarmPendingIntent(context, reminder.id, snooze = true)
        val triggerAt = System.currentTimeMillis() + SNOOZE_MILLIS
        if (canScheduleExact(context)) {
            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        } else {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                triggerAt,
                pendingIntent
            )
        }
    }

    fun rescheduleAll(context: Context) {
        ShowReminderStore(context).all()
            .filter { it.enabled }
            .forEach { schedule(context, it) }
    }

    private fun alarmPendingIntent(
        context: Context,
        id: Long,
        snooze: Boolean
    ): PendingIntent {
        val requestCode = (id xor if (snooze) SNOOZE_REQUEST_MASK else 0L).toInt()
        val alarmKind = if (snooze) "snooze" else "weekly"
        val intent = Intent(context, ShowReminderAlarmReceiver::class.java).apply {
            data = Uri.parse("radio-reminder://$id/$alarmKind")
            putExtra(EXTRA_REMINDER_ID, id)
            putExtra(EXTRA_IS_SNOOZE, snooze)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun nextOccurrence(reminder: ShowReminder): Long {
        val now = Calendar.getInstance()
        val candidate = (now.clone() as Calendar).apply {
            set(Calendar.DAY_OF_WEEK, reminder.dayOfWeek)
            set(Calendar.HOUR_OF_DAY, reminder.hour)
            set(Calendar.MINUTE, reminder.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (!after(now)) add(Calendar.WEEK_OF_YEAR, 1)
        }
        return candidate.timeInMillis
    }

    private const val SNOOZE_MILLIS = 5 * 60 * 1000L
    private const val SNOOZE_REQUEST_MASK = 0x5F000000L
}
