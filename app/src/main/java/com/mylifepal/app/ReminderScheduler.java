package com.mylifepal.app;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

final class ReminderScheduler {
    static final String CHANNEL_ID = "habit_reminders";
    static final String ACTION_REMINDER = "com.mylifepal.app.ACTION_REMINDER";
    static final String EXTRA_HABIT_ID = "habit_id";

    private static final String APP_PREFS = "mylifepal_state";
    private static final String STATE_KEY = "state";
    private static final String REMINDER_PREFS = "mylifepal_reminders";
    private static final String SCHEDULED_IDS_KEY = "scheduled_ids";

    private ReminderScheduler() {
    }

    static void ensureChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) {
            return;
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) {
            return;
        }
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "Habit reminders", NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription("Tiny habit reminders from MyLifePal.");
        manager.createNotificationChannel(channel);
    }

    static void refreshAll(Context context) {
        Context appContext = context.getApplicationContext();
        ensureChannel(appContext);

        SharedPreferences reminderPrefs = appContext.getSharedPreferences(REMINDER_PREFS, Context.MODE_PRIVATE);
        for (String habitId : scheduledIds(reminderPrefs)) {
            cancelHabit(appContext, habitId);
        }

        JSONArray scheduled = new JSONArray();
        JSONObject state = loadState(appContext);
        JSONArray habits = state == null ? null : state.optJSONArray("habits");
        if (habits != null) {
            String today = today();
            for (int i = 0; i < habits.length(); i++) {
                JSONObject habit = habits.optJSONObject(i);
                if (habit == null || !habit.optBoolean("reminderEnabled", false)) {
                    continue;
                }
                String habitId = habit.optString("id", "");
                if (habitId.isEmpty()) {
                    continue;
                }
                scheduleHabit(appContext, habit, today);
                scheduled.put(habitId);
            }
        }
        reminderPrefs.edit().putString(SCHEDULED_IDS_KEY, scheduled.toString()).apply();
    }

    static void cancelHabit(Context context, String habitId) {
        if (habitId == null || habitId.isEmpty()) {
            return;
        }
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingIntent = reminderPendingIntent(context, habitId, PendingIntent.FLAG_NO_CREATE);
        if (alarmManager != null && pendingIntent != null) {
            alarmManager.cancel(pendingIntent);
            pendingIntent.cancel();
        }
    }

    static JSONObject findHabit(Context context, String habitId) {
        if (habitId == null || habitId.isEmpty()) {
            return null;
        }
        JSONObject state = loadState(context.getApplicationContext());
        JSONArray habits = state == null ? null : state.optJSONArray("habits");
        if (habits == null) {
            return null;
        }
        for (int i = 0; i < habits.length(); i++) {
            JSONObject habit = habits.optJSONObject(i);
            if (habit != null && habitId.equals(habit.optString("id"))) {
                return habit;
            }
        }
        return null;
    }

    static boolean completedToday(JSONObject habit) {
        return today().equals(habit.optString("lastCompleted", ""));
    }

    static boolean notificationsAllowed(Context context) {
        return Build.VERSION.SDK_INT < 33 || context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    static int notificationId(String habitId) {
        return 4000 + requestCode(habitId);
    }

    private static void scheduleHabit(Context context, JSONObject habit, String today) {
        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarmManager == null) {
            return;
        }
        String habitId = habit.optString("id", "");
        String reminderTime = normalizeTime(habit.optString("reminderTime", "09:00"));
        boolean skipToday = today.equals(habit.optString("lastCompleted", ""));
        long triggerAt = nextTriggerMillis(reminderTime, skipToday);
        PendingIntent pendingIntent = reminderPendingIntent(context, habitId, PendingIntent.FLAG_UPDATE_CURRENT);
        try {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        } catch (SecurityException exception) {
            alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAt, pendingIntent);
        }
    }

    private static PendingIntent reminderPendingIntent(Context context, String habitId, int flags) {
        Intent intent = new Intent(context, ReminderReceiver.class);
        intent.setAction(ACTION_REMINDER);
        intent.putExtra(EXTRA_HABIT_ID, habitId);
        return PendingIntent.getBroadcast(context, requestCode(habitId), intent, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    private static int requestCode(String habitId) {
        return habitId.hashCode() & 0x0fffffff;
    }

    private static String[] scheduledIds(SharedPreferences reminderPrefs) {
        String raw = reminderPrefs.getString(SCHEDULED_IDS_KEY, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            String[] ids = new String[array.length()];
            for (int i = 0; i < array.length(); i++) {
                ids[i] = array.optString(i, "");
            }
            return ids;
        } catch (JSONException exception) {
            return new String[0];
        }
    }

    private static JSONObject loadState(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(STATE_KEY, null);
        if (raw == null) {
            return null;
        }
        try {
            return new JSONObject(raw);
        } catch (JSONException exception) {
            return null;
        }
    }

    private static long nextTriggerMillis(String reminderTime, boolean skipToday) {
        String[] pieces = reminderTime.split(":");
        int hour = Integer.parseInt(pieces[0]);
        int minute = Integer.parseInt(pieces[1]);
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        if (skipToday || calendar.getTimeInMillis() <= System.currentTimeMillis()) {
            calendar.add(Calendar.DATE, 1);
        }
        return calendar.getTimeInMillis();
    }

    private static String normalizeTime(String value) {
        if (value == null || !value.matches("\\d{2}:\\d{2}")) {
            return "09:00";
        }
        String[] pieces = value.split(":");
        int hour = Integer.parseInt(pieces[0]);
        int minute = Integer.parseInt(pieces[1]);
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
            return "09:00";
        }
        return String.format(Locale.US, "%02d:%02d", hour, minute);
    }

    private static String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().getTime());
    }
}
