package com.mylifepal.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object ReminderScheduler {
    const val CHANNEL_ID = "habit_reminders"
    const val ACTION_REMINDER = "com.mylifepal.app.ACTION_REMINDER"
    const val EXTRA_HABIT_ID = "habit_id"

    private const val APP_PREFS = "mylifepal_state"
    private const val STATE_KEY = "state"
    private const val REMINDER_PREFS = "mylifepal_reminders"
    private const val SCHEDULED_IDS_KEY = "scheduled_ids"

    @JvmStatic
    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) {
            return
        }
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Habit reminders",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Tiny habit reminders from MyLifePal."
        }
        manager.createNotificationChannel(channel)
    }

    @JvmStatic
    fun refreshAll(context: Context) {
        val appContext = context.applicationContext
        ensureChannel(appContext)

        val reminderPrefs = appContext.getSharedPreferences(REMINDER_PREFS, Context.MODE_PRIVATE)
        scheduledIds(reminderPrefs).forEach { habitId ->
            cancelHabit(appContext, habitId)
        }

        val scheduled = JSONArray()
        val state = loadState(appContext)
        val habits = state?.optJSONArray("habits")
        if (habits != null) {
            val today = today()
            for (index in 0 until habits.length()) {
                val habit = habits.optJSONObject(index)
                if (habit == null || !habit.optBoolean("reminderEnabled", false)) {
                    continue
                }
                val habitId = habit.optString("id", "")
                if (habitId.isEmpty()) {
                    continue
                }
                scheduleHabit(appContext, habit, today)
                scheduled.put(habitId)
            }
        }
        reminderPrefs.edit().putString(SCHEDULED_IDS_KEY, scheduled.toString()).apply()
    }

    @JvmStatic
    fun cancelHabit(context: Context, habitId: String?) {
        if (habitId.isNullOrEmpty()) {
            return
        }
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val pendingIntent = reminderPendingIntent(context, habitId, PendingIntent.FLAG_NO_CREATE)
        if (alarmManager != null && pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }

    @JvmStatic
    fun findHabit(context: Context, habitId: String?): JSONObject? {
        if (habitId.isNullOrEmpty()) {
            return null
        }
        val state = loadState(context.applicationContext)
        val habits = state?.optJSONArray("habits") ?: return null
        for (index in 0 until habits.length()) {
            val habit = habits.optJSONObject(index)
            if (habit != null && habitId == habit.optString("id")) {
                return habit
            }
        }
        return null
    }

    @JvmStatic
    fun completedToday(habit: JSONObject): Boolean {
        return today() == habit.optString("lastCompleted", "")
    }

    @JvmStatic
    fun notificationsAllowed(context: Context): Boolean {
        return Build.VERSION.SDK_INT < 33 ||
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    @JvmStatic
    fun notificationId(habitId: String?): Int {
        return 4000 + requestCode(habitId.orEmpty())
    }

    private fun scheduleHabit(context: Context, habit: JSONObject, today: String) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val habitId = habit.optString("id", "")
        val reminderTime = normalizeTime(habit.optString("reminderTime", "09:00"))
        val skipToday = today == habit.optString("lastCompleted", "")
        val triggerAt = nextTriggerMillis(reminderTime, skipToday)
        val pendingIntent = reminderPendingIntent(context, habitId, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        try {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        } catch (_: SecurityException) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent)
        }
    }

    private fun reminderPendingIntent(context: Context, habitId: String, flags: Int): PendingIntent? {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            action = ACTION_REMINDER
            putExtra(EXTRA_HABIT_ID, habitId)
        }
        return PendingIntent.getBroadcast(
            context,
            requestCode(habitId),
            intent,
            flags or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun requestCode(habitId: String): Int {
        return habitId.hashCode() and 0x0fffffff
    }

    private fun scheduledIds(reminderPrefs: SharedPreferences): Array<String> {
        val raw = reminderPrefs.getString(SCHEDULED_IDS_KEY, "[]")
        return try {
            val array = JSONArray(raw)
            Array(array.length()) { index ->
                array.optString(index, "")
            }
        } catch (_: JSONException) {
            emptyArray()
        }
    }

    private fun loadState(context: Context): JSONObject? {
        val prefs = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(STATE_KEY, null) ?: return null
        return try {
            JSONObject(raw)
        } catch (_: JSONException) {
            null
        }
    }

    private fun nextTriggerMillis(reminderTime: String, skipToday: Boolean): Long {
        val pieces = reminderTime.split(":")
        val hour = pieces[0].toInt()
        val minute = pieces[1].toInt()
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (skipToday || calendar.timeInMillis <= System.currentTimeMillis()) {
            calendar.add(Calendar.DATE, 1)
        }
        return calendar.timeInMillis
    }

    private fun normalizeTime(value: String?): String {
        if (value == null || !value.matches(Regex("\\d{2}:\\d{2}"))) {
            return "09:00"
        }
        val pieces = value.split(":")
        val hour = pieces[0].toInt()
        val minute = pieces[1].toInt()
        if (hour !in 0..23 || minute !in 0..59) {
            return "09:00"
        }
        return String.format(Locale.US, "%02d:%02d", hour, minute)
    }

    private fun today(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
    }
}
