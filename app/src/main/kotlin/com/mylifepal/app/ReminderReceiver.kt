package com.mylifepal.app

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONObject

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) {
            return
        }
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                ReminderScheduler.refreshAll(context)
                return
            }
            ReminderScheduler.ACTION_REMINDER -> {
                val habitId = intent.getStringExtra(ReminderScheduler.EXTRA_HABIT_ID)
                val habit = ReminderScheduler.findHabit(context, habitId)
                if (
                    habit != null &&
                    habit.optBoolean("reminderEnabled", false) &&
                    !ReminderScheduler.completedToday(habit)
                ) {
                    showReminder(context, habitId, habit)
                }
                ReminderScheduler.refreshAll(context)
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun showReminder(context: Context, habitId: String?, habit: JSONObject) {
        if (!ReminderScheduler.notificationsAllowed(context)) {
            return
        }
        ReminderScheduler.ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return

        val name = habit.optString("name", "Tiny habit")
        val tinyAction = habit.optString("tinyAction", "Do the 2-minute version")
        val cue = habit.optString("cue", "When the time arrives")
        val reward = habit.optString("reward", "Enjoy the win")
        val text = "$name: $tinyAction"
        val bigText = "Cue: $cue\nTiny action: $tinyAction\nReward: $reward"

        val openApp = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentIntent = PendingIntent.getActivity(
            context,
            ReminderScheduler.notificationId(habitId),
            openApp,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(context, ReminderScheduler.CHANNEL_ID)
        } else {
            Notification.Builder(context)
        }
        val notification = builder
            .setSmallIcon(R.drawable.ic_reminder)
            .setContentTitle("Tiny habit reminder")
            .setContentText(text)
            .setStyle(Notification.BigTextStyle().bigText(bigText))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .setPriority(Notification.PRIORITY_DEFAULT)
            .build()
        manager.notify(ReminderScheduler.notificationId(habitId), notification)
    }
}
