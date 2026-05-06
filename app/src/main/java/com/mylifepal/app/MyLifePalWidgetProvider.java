package com.mylifepal.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.widget.RemoteViews;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class MyLifePalWidgetProvider extends AppWidgetProvider {
    private static final String PREFS = "mylifepal_state";
    private static final String STATE_KEY = "state";

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] widgetIds) {
        for (int widgetId : widgetIds) {
            updateWidget(context, manager, widgetId);
        }
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        ComponentName provider = new ComponentName(context, MyLifePalWidgetProvider.class);
        int[] widgetIds = manager.getAppWidgetIds(provider);
        for (int widgetId : widgetIds) {
            updateWidget(context, manager, widgetId);
        }
    }

    private static void updateWidget(Context context, AppWidgetManager manager, int widgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_overview);
        WidgetStats stats = readStats(context);
        views.setTextViewText(R.id.widget_title, "MyLifePal");
        views.setTextViewText(R.id.widget_score, stats.completed + "/" + stats.habits + " habits");
        views.setTextViewText(R.id.widget_status, stats.coins + " coins - " + stats.monsterName + " Lv " + stats.monsterLevel);
        views.setTextViewText(R.id.widget_next, stats.nextHabit.isEmpty() ? "Open MyLifePal" : "Next: " + stats.nextHabit);

        Intent open = new Intent(context, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context,
                91,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        views.setOnClickPendingIntent(R.id.widget_shell, pendingIntent);
        manager.updateAppWidget(widgetId, views);
    }

    private static WidgetStats readStats(Context context) {
        WidgetStats stats = new WidgetStats();
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String raw = prefs.getString(STATE_KEY, null);
        if (raw == null) {
            return stats;
        }
        try {
            JSONObject state = new JSONObject(raw);
            stats.coins = state.optInt("coins", 0);
            stats.monsterName = state.optString("monsterName", "Milo");
            stats.monsterLevel = (state.optInt("monsterXp", 0) / 90) + 1;
            JSONArray habits = state.optJSONArray("habits");
            if (habits != null) {
                stats.habits = habits.length();
                String today = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(java.util.Calendar.getInstance().getTime());
                for (int i = 0; i < habits.length(); i++) {
                    JSONObject habit = habits.optJSONObject(i);
                    if (habit == null) {
                        continue;
                    }
                    boolean done = today.equals(habit.optString("lastCompleted", ""));
                    if (done) {
                        stats.completed++;
                    } else if (stats.nextHabit.isEmpty()) {
                        stats.nextHabit = habit.optString("icon", "*") + " " + habit.optString("tinyAction", habit.optString("name", ""));
                    }
                }
            }
        } catch (JSONException ignored) {
        }
        return stats;
    }

    private static class WidgetStats {
        int habits = 0;
        int completed = 0;
        int coins = 0;
        int monsterLevel = 1;
        String monsterName = "Milo";
        String nextHabit = "";
    }
}
