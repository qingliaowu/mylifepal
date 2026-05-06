package com.mylifepal.app;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import org.json.JSONObject;

public class ReminderReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) {
            return;
        }
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action) || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            ReminderScheduler.refreshAll(context);
            return;
        }
        if (!ReminderScheduler.ACTION_REMINDER.equals(action)) {
            return;
        }

        String habitId = intent.getStringExtra(ReminderScheduler.EXTRA_HABIT_ID);
        JSONObject habit = ReminderScheduler.findHabit(context, habitId);
        if (habit != null && habit.optBoolean("reminderEnabled", false) && !ReminderScheduler.completedToday(habit)) {
            showReminder(context, habitId, habit);
        }
        ReminderScheduler.refreshAll(context);
    }

    private void showReminder(Context context, String habitId, JSONObject habit) {
        if (!ReminderScheduler.notificationsAllowed(context)) {
            return;
        }
        ReminderScheduler.ensureChannel(context);
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) {
            return;
        }

        String name = habit.optString("name", "Tiny habit");
        String tinyAction = habit.optString("tinyAction", "Do the 2-minute version");
        String cue = habit.optString("cue", "When the time arrives");
        String reward = habit.optString("reward", "Enjoy the win");
        String text = name + ": " + tinyAction;
        String bigText = "Cue: " + cue + "\nTiny action: " + tinyAction + "\nReward: " + reward;

        Intent openApp = new Intent(context, MainActivity.class);
        openApp.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                ReminderScheduler.notificationId(habitId),
                openApp,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, ReminderScheduler.CHANNEL_ID)
                : new Notification.Builder(context);
        Notification notification = builder
                .setSmallIcon(R.drawable.ic_reminder)
                .setContentTitle("Tiny habit reminder")
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(bigText))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setPriority(Notification.PRIORITY_DEFAULT)
                .build();
        manager.notify(ReminderScheduler.notificationId(habitId), notification);
    }
}
