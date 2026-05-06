package com.mylifepal.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
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
        views.setInt(R.id.widget_shell, "setBackgroundColor", stats.themeBackground);
        views.setTextColor(R.id.widget_score, stats.themePrimary);

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
            stats.themeBackground = safeBackground(state.optInt("themeBackground", stats.themeBackground));
            stats.themePrimary = safeTextOnBackground(state.optInt("themePrimary", stats.themePrimary), stats.themeBackground);
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

    private static int safeBackground(int color) {
        return relativeLuminance(color) < 0.72d ? Color.rgb(245, 247, 241) : color;
    }

    private static int safeTextOnBackground(int textColor, int background) {
        return contrastRatio(textColor, background) >= 4.5d ? textColor : Color.rgb(23, 32, 27);
    }

    private static double contrastRatio(int first, int second) {
        double lighter = Math.max(relativeLuminance(first), relativeLuminance(second));
        double darker = Math.min(relativeLuminance(first), relativeLuminance(second));
        return (lighter + 0.05d) / (darker + 0.05d);
    }

    private static double relativeLuminance(int color) {
        double red = linearChannel(Color.red(color) / 255d);
        double green = linearChannel(Color.green(color) / 255d);
        double blue = linearChannel(Color.blue(color) / 255d);
        return 0.2126d * red + 0.7152d * green + 0.0722d * blue;
    }

    private static double linearChannel(double channel) {
        return channel <= 0.03928d ? channel / 12.92d : Math.pow((channel + 0.055d) / 1.055d, 2.4d);
    }

    private static class WidgetStats {
        int habits = 0;
        int completed = 0;
        int coins = 0;
        int monsterLevel = 1;
        int themePrimary = Color.rgb(46, 125, 104);
        int themeBackground = Color.rgb(245, 247, 241);
        String monsterName = "Milo";
        String nextHabit = "";
    }
}
