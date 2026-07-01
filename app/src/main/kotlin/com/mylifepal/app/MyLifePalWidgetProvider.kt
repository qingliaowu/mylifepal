package com.mylifepal.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

class MyLifePalWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        widgetIds.forEach { widgetId ->
            updateWidget(context, manager, widgetId)
        }
    }

    private data class WidgetStats(
        var habits: Int = 0,
        var completed: Int = 0,
        var coins: Int = 0,
        var monsterLevel: Int = 1,
        var themePrimary: Int = Color.rgb(46, 125, 104),
        var themeBackground: Int = Color.rgb(245, 247, 241),
        var monsterName: String = "Milo",
        var nextHabit: String = ""
    )

    companion object {
        private const val PREFS = "mylifepal_state"
        private const val STATE_KEY = "state"

        @JvmStatic
        fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val provider = ComponentName(context, MyLifePalWidgetProvider::class.java)
            val widgetIds = manager.getAppWidgetIds(provider)
            widgetIds.forEach { widgetId ->
                updateWidget(context, manager, widgetId)
            }
        }

        private fun updateWidget(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_overview)
            val stats = readStats(context)
            views.setTextViewText(R.id.widget_title, "MyLifePal")
            views.setTextViewText(R.id.widget_score, "${stats.completed}/${stats.habits} habits")
            views.setTextViewText(
                R.id.widget_status,
                "${stats.coins} coins - ${stats.monsterName} Lv ${stats.monsterLevel}"
            )
            views.setTextViewText(
                R.id.widget_next,
                if (stats.nextHabit.isEmpty()) "Open MyLifePal" else "Next: ${stats.nextHabit}"
            )
            views.setInt(R.id.widget_shell, "setBackgroundColor", stats.themeBackground)
            views.setTextColor(R.id.widget_score, stats.themePrimary)

            val open = Intent(context, MainActivity::class.java)
            val pendingIntent = PendingIntent.getActivity(
                context,
                91,
                open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_shell, pendingIntent)
            manager.updateAppWidget(widgetId, views)
        }

        private fun readStats(context: Context): WidgetStats {
            val stats = WidgetStats()
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val raw = prefs.getString(STATE_KEY, null) ?: return stats
            try {
                val state = JSONObject(raw)
                stats.coins = state.optInt("coins", 0)
                stats.themeBackground = safeBackground(state.optInt("themeBackground", stats.themeBackground))
                stats.themePrimary = safeTextOnBackground(
                    state.optInt("themePrimary", stats.themePrimary),
                    stats.themeBackground
                )
                stats.monsterName = state.optString("monsterName", "Milo")
                stats.monsterLevel = state.optInt("monsterXp", 0) / 90 + 1
                val habits = state.optJSONArray("habits")
                if (habits != null) {
                    stats.habits = habits.length()
                    val today = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                        .format(Calendar.getInstance().time)
                    for (index in 0 until habits.length()) {
                        val habit = habits.optJSONObject(index) ?: continue
                        val done = today == habit.optString("lastCompleted", "")
                        if (done) {
                            stats.completed++
                        } else if (stats.nextHabit.isEmpty()) {
                            stats.nextHabit = "${habit.optString("icon", "*")} ${
                                habit.optString("tinyAction", habit.optString("name", ""))
                            }"
                        }
                    }
                }
            } catch (_: JSONException) {
            }
            return stats
        }

        private fun safeBackground(color: Int): Int {
            return if (relativeLuminance(color) < 0.72) Color.rgb(245, 247, 241) else color
        }

        private fun safeTextOnBackground(textColor: Int, background: Int): Int {
            return if (contrastRatio(textColor, background) >= 4.5) textColor else Color.rgb(23, 32, 27)
        }

        private fun contrastRatio(first: Int, second: Int): Double {
            val lighter = max(relativeLuminance(first), relativeLuminance(second))
            val darker = min(relativeLuminance(first), relativeLuminance(second))
            return (lighter + 0.05) / (darker + 0.05)
        }

        private fun relativeLuminance(color: Int): Double {
            val red = linearChannel(Color.red(color) / 255.0)
            val green = linearChannel(Color.green(color) / 255.0)
            val blue = linearChannel(Color.blue(color) / 255.0)
            return 0.2126 * red + 0.7152 * green + 0.0722 * blue
        }

        private fun linearChannel(channel: Double): Double {
            return if (channel <= 0.03928) channel / 12.92 else ((channel + 0.055) / 1.055).pow(2.4)
        }
    }
}
