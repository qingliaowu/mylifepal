package com.mylifepal.watch

import android.app.Activity
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class WatchActivity : Activity() {
    private lateinit var prefs: SharedPreferences
    private lateinit var state: WatchState
    private lateinit var root: LinearLayout

    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            tickTimer()
            timerHandler.postDelayed(this, 1000)
        }
    }

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val window: Window = window
        window.statusBarColor = BG
        window.navigationBarColor = BG

        prefs = getSharedPreferences("mylifepal_watch_state", MODE_PRIVATE)
        state = loadState()

        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(BG)
        }
        setContentView(root)
        render()
        timerHandler.post(timerRunnable)
    }

    override fun onDestroy() {
        timerHandler.removeCallbacks(timerRunnable)
        super.onDestroy()
    }

    private fun render() {
        root.removeAllViews()
        val scroll = ScrollView(this).apply {
            isFillViewport = false
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(24))
        }
        scroll.addView(content)
        root.addView(
            scroll,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )

        addHeader(content)
        addSpace(content, 12)

        val next = nextHabit()
        if (next == null) {
            addClearedCard(content)
        } else {
            addFocusCard(content, next)
        }

        addSpace(content, 12)
        addTinyStats(content)
        addSpace(content, 12)
        addTimerCard(content)
        addSpace(content, 12)
        addMoodCard(content)
        addSpace(content, 12)

        state.habits.forEach { habit ->
            addHabitRow(content, habit)
            addSpace(content, 8)
        }
    }

    private fun addHeader(content: LinearLayout) {
        val title = text("MyLifePal", 24, INK, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
        }
        content.addView(title, fullWrap())

        val level = pill("Lv ${level()}  |  ${state.coins} coins", GOLD, BG)
        content.addView(level, fullWrap())
        addSpace(content, 8)

        val bar = progressBar(state.totalXp % 100, 100)
        content.addView(
            bar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(8)
            )
        )
    }

    private fun addFocusCard(content: LinearLayout, habit: Habit) {
        val card = card()
        card.addView(text("Next tiny action", 13, GOLD, Typeface.BOLD))
        val name = text(habit.name, 22, INK, Typeface.BOLD).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
        card.addView(name)
        val tiny = text(habit.tinyAction, 15, MUTED, Typeface.NORMAL).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setLineSpacing(dp(2).toFloat(), 1f)
        }
        card.addView(tiny)
        addSpace(card, 12)
        val done = button("Complete", FOREST, Color.WHITE).apply {
            setOnClickListener { complete(habit) }
        }
        card.addView(
            done,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            )
        )
        content.addView(card)
    }

    private fun addMoodCard(content: LinearLayout) {
        val card = card().apply {
            background = round(Color.rgb(32, 36, 52), 18, LINE, 1)
        }
        card.addView(text("Emotion check-in", 13, GOLD, Typeface.BOLD))
        val moodText = if (today() == state.moodDate) moodLabel(state.moodScore) else "How are you?"
        val mood = text(moodText, 21, INK, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
        }
        card.addView(mood)
        val count = text("${state.moodCheckins} check-ins", 12, MUTED, Typeface.NORMAL).apply {
            gravity = Gravity.CENTER
        }
        card.addView(count)
        addSpace(card, 8)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        card.addView(actions, fullWrap())

        val low = button("Low", Color.rgb(213, 139, 63), Color.WHITE).apply {
            setOnClickListener { recordMood(2) }
        }
        actions.addView(low, LinearLayout.LayoutParams(0, dp(40), 1f))
        addGap(actions, 6)

        val okay = button("Okay", GOLD, BG).apply {
            setOnClickListener { recordMood(3) }
        }
        actions.addView(okay, LinearLayout.LayoutParams(0, dp(40), 1f))
        addGap(actions, 6)

        val good = button("Good", FOREST, Color.WHITE).apply {
            setOnClickListener { recordMood(4) }
        }
        actions.addView(good, LinearLayout.LayoutParams(0, dp(40), 1f))
        content.addView(card)
    }

    private fun addClearedCard(content: LinearLayout) {
        val card = card()
        card.addView(text("Today cleared", 22, INK, Typeface.BOLD))
        val copy = text("Your tiny votes are in.", 15, MUTED, Typeface.NORMAL).apply {
            gravity = Gravity.CENTER
        }
        card.addView(copy)
        content.addView(card)
    }

    private fun addTinyStats(content: LinearLayout) {
        val stats = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        content.addView(stats, fullWrap())
        stats.addView(stat("${todayCount()}/${state.habits.size}", "today"))
        addGap(stats, 8)
        stats.addView(stat(state.totalXp.toString(), "XP"))
    }

    private fun addTimerCard(content: LinearLayout) {
        val card = card().apply {
            background = round(Color.rgb(37, 48, 38), 18, LINE, 1)
        }
        card.addView(text("Tomato Timer", 13, GOLD, Typeface.BOLD))
        val clock = text(formatTimer(timerRemainingMillis()), 28, INK, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
        }
        card.addView(clock)
        val statusText = if (state.timerRunning) {
            "Focus running"
        } else {
            "${state.focusSessions} tomatoes done"
        }
        val status = text(statusText, 12, MUTED, Typeface.NORMAL).apply {
            gravity = Gravity.CENTER
        }
        card.addView(status)
        addSpace(card, 8)
        card.addView(
            progressBar(
                (state.timerDurationMillis - timerRemainingMillis()).toInt(),
                state.timerDurationMillis.toInt()
            ),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(7)
            )
        )
        addSpace(card, 8)

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        card.addView(actions, fullWrap())

        val primary = button(
            if (state.timerRunning) "Pause" else "Start",
            if (state.timerRunning) SURFACE else FOREST,
            if (state.timerRunning) INK else Color.WHITE
        ).apply {
            setOnClickListener {
                if (state.timerRunning) {
                    pauseTimer()
                } else {
                    startTimer()
                }
            }
        }
        actions.addView(primary, LinearLayout.LayoutParams(0, dp(42), 1f))
        addGap(actions, 8)

        val reset = button("Reset", GOLD, BG).apply {
            setOnClickListener { resetTimer() }
        }
        actions.addView(reset, LinearLayout.LayoutParams(0, dp(42), 1f))
        content.addView(card)
    }

    private fun addHabitRow(content: LinearLayout, habit: Habit) {
        val row = card().apply {
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = round(
                if (completedToday(habit)) Color.rgb(35, 58, 45) else SURFACE,
                18,
                LINE,
                1
            )
        }

        val title = text(habit.name, 17, INK, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
        }
        row.addView(title)
        val streak = text("${habit.streak}d streak  |  +${habit.xp} XP", 12, MUTED, Typeface.NORMAL).apply {
            gravity = Gravity.CENTER
        }
        row.addView(streak)

        if (!completedToday(habit)) {
            addSpace(row, 8)
            val done = button("Done", GOLD, BG).apply {
                setOnClickListener { complete(habit) }
            }
            row.addView(
                done,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(42)
                )
            )
        }
        content.addView(row)
    }

    private fun complete(habit: Habit) {
        if (completedToday(habit)) {
            return
        }
        habit.streak = if (yesterday() == habit.lastCompleted) habit.streak + 1 else 1
        habit.bestStreak = max(habit.bestStreak, habit.streak)
        habit.lastCompleted = today()
        habit.completions++
        val gain = habit.xp + min(10, habit.streak * 2)
        state.totalXp += gain
        state.coins += habit.coins
        saveState()
        Toast.makeText(this, "+$gain XP", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun nextHabit(): Habit? = state.habits.firstOrNull { !completedToday(it) }

    private fun completedToday(habit: Habit): Boolean = today() == habit.lastCompleted

    private fun todayCount(): Int = state.habits.count { completedToday(it) }

    private fun level(): Int = state.totalXp / 100 + 1

    private fun recordMood(score: Int) {
        val newDay = today() != state.moodDate
        state.moodDate = today()
        state.moodScore = score
        if (newDay) {
            state.moodCheckins++
            state.totalXp += 5
            state.coins += 3
            Toast.makeText(this, "Mood tracked: +5 XP", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Mood updated", Toast.LENGTH_SHORT).show()
        }
        saveState()
        render()
    }

    private fun moodLabel(score: Int): String = when {
        score <= 2 -> "Low"
        score == 3 -> "Okay"
        else -> "Good"
    }

    private fun startTimer() {
        var remaining = timerRemainingMillis()
        if (remaining <= 0) {
            remaining = state.timerDurationMillis
        }
        state.timerEndAtMillis = System.currentTimeMillis() + remaining
        state.timerRemainingMillis = remaining
        state.timerRunning = true
        saveState()
        render()
    }

    private fun pauseTimer() {
        state.timerRemainingMillis = timerRemainingMillis()
        state.timerEndAtMillis = 0
        state.timerRunning = false
        saveState()
        render()
    }

    private fun resetTimer() {
        state.timerRunning = false
        state.timerEndAtMillis = 0
        state.timerRemainingMillis = state.timerDurationMillis
        saveState()
        render()
    }

    private fun tickTimer() {
        if (!state.timerRunning) {
            return
        }
        if (timerRemainingMillis() <= 0) {
            completeTimer()
            return
        }
        render()
    }

    private fun completeTimer() {
        state.timerRunning = false
        state.timerEndAtMillis = 0
        state.timerRemainingMillis = state.timerDurationMillis
        state.focusSessions++
        state.totalXp += 12
        state.coins += 8
        saveState()
        Toast.makeText(this, "Tomato complete: +12 XP", Toast.LENGTH_SHORT).show()
        render()
    }

    private fun timerRemainingMillis(): Long {
        if (state.timerRunning) {
            return max(0L, state.timerEndAtMillis - System.currentTimeMillis())
        }
        return max(0L, state.timerRemainingMillis)
    }

    private fun formatTimer(millis: Long): String {
        val totalSeconds = max(0L, (millis + 999) / 1000)
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }

    private fun loadState(): WatchState {
        val raw = prefs.getString("state", null) ?: return defaultState()
        return try {
            WatchState.fromJson(JSONObject(raw))
        } catch (_: JSONException) {
            defaultState()
        }
    }

    private fun saveState() {
        prefs.edit().putString("state", state.toJson().toString()).apply()
    }

    private fun defaultState(): WatchState = WatchState().apply {
        coins = 10
        habits.add(Habit("Hydrate", "Drink one glass", 8, 5))
        habits.add(Habit("Walk", "Walk for 2 minutes", 12, 8))
        habits.add(Habit("Breathe", "Take 5 slow breaths", 8, 5))
    }

    private fun text(value: String, sp: Int, color: Int, style: Int): TextView {
        return TextView(this).apply {
            text = value
            textSize = sp.toFloat()
            setTextColor(color)
            setTypeface(Typeface.DEFAULT, style)
            includeFontPadding = true
        }
    }

    private fun pill(value: String, background: Int, foreground: Int): TextView {
        return text(value, 13, foreground, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(7), dp(12), dp(7))
            setBackground(round(background, 999))
        }
    }

    private fun stat(value: String, label: String): TextView {
        return text("$value\n$label", 13, INK, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            background = round(SURFACE, 16, LINE, 1)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setLineSpacing(dp(2).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
    }

    private fun button(label: String, background: Int, foreground: Int): Button {
        return Button(this).apply {
            isAllCaps = false
            text = label
            textSize = 14f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            setTextColor(foreground)
            gravity = Gravity.CENTER
            minHeight = 0
            minimumHeight = 0
            setPadding(dp(8), 0, dp(8), 0)
            setBackground(round(background, 999))
        }
    }

    private fun card(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            background = round(SURFACE, 20, LINE, 1)
        }
    }

    private fun progressBar(progress: Int, max: Int): ProgressBar {
        return ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            this.max = max
            this.progress = progress.coerceIn(0, max)
            progressTintList = ColorStateList.valueOf(GOLD)
            progressBackgroundTintList = ColorStateList.valueOf(Color.rgb(52, 67, 59))
        }
    }

    private fun round(color: Int, radiusDp: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
        }
    }

    private fun round(color: Int, radiusDp: Int, strokeColor: Int, strokeDp: Int): GradientDrawable {
        return round(color, radiusDp).apply {
            setStroke(dp(strokeDp), strokeColor)
        }
    }

    private fun fullWrap(): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun addSpace(parent: LinearLayout, value: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(1, dp(value)))
    }

    private fun addGap(parent: LinearLayout, value: Int) {
        parent.addView(View(this), LinearLayout.LayoutParams(dp(value), 1))
    }

    private fun today(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().time)
    }

    private fun yesterday(): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(
            Calendar.getInstance().apply {
                add(Calendar.DATE, -1)
            }.time
        )
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).roundToInt()
    }

    private data class Habit(
        var name: String,
        var tinyAction: String,
        var xp: Int,
        var coins: Int,
        var lastCompleted: String = "",
        var streak: Int = 0,
        var bestStreak: Int = 0,
        var completions: Int = 0
    ) {
        fun toJson(): JSONObject {
            val json = JSONObject()
            try {
                json.put("name", name)
                json.put("tinyAction", tinyAction)
                json.put("lastCompleted", lastCompleted)
                json.put("xp", xp)
                json.put("coins", coins)
                json.put("streak", streak)
                json.put("bestStreak", bestStreak)
                json.put("completions", completions)
            } catch (_: JSONException) {
            }
            return json
        }

        companion object {
            fun fromJson(json: JSONObject): Habit {
                return Habit(
                    name = json.optString("name", "Tiny habit"),
                    tinyAction = json.optString("tinyAction", "Do the 2-minute version"),
                    xp = json.optInt("xp", 8),
                    coins = json.optInt("coins", 5),
                    lastCompleted = json.optString("lastCompleted", ""),
                    streak = json.optInt("streak", 0),
                    bestStreak = json.optInt("bestStreak", 0),
                    completions = json.optInt("completions", 0)
                )
            }
        }
    }

    private data class WatchState(
        var totalXp: Int = 0,
        var coins: Int = 0,
        var timerDurationMillis: Long = 25L * 60L * 1000L,
        var timerRemainingMillis: Long = 25L * 60L * 1000L,
        var timerEndAtMillis: Long = 0L,
        var timerRunning: Boolean = false,
        var focusSessions: Int = 0,
        var moodDate: String = "",
        var moodScore: Int = 3,
        var moodCheckins: Int = 0,
        val habits: MutableList<Habit> = mutableListOf()
    ) {
        fun toJson(): JSONObject {
            val json = JSONObject()
            try {
                json.put("totalXp", totalXp)
                json.put("coins", coins)
                json.put("timerDurationMillis", timerDurationMillis)
                json.put("timerRemainingMillis", timerRemainingMillis)
                json.put("timerEndAtMillis", timerEndAtMillis)
                json.put("timerRunning", timerRunning)
                json.put("focusSessions", focusSessions)
                json.put("moodDate", moodDate)
                json.put("moodScore", moodScore)
                json.put("moodCheckins", moodCheckins)
                val habitsJson = JSONArray()
                habits.forEach { habit ->
                    habitsJson.put(habit.toJson())
                }
                json.put("habits", habitsJson)
            } catch (_: JSONException) {
            }
            return json
        }

        companion object {
            fun fromJson(json: JSONObject): WatchState {
                val state = WatchState(
                    totalXp = json.optInt("totalXp", 0),
                    coins = json.optInt("coins", 0),
                    timerDurationMillis = json.optLong("timerDurationMillis", 25L * 60L * 1000L),
                    timerEndAtMillis = json.optLong("timerEndAtMillis", 0L),
                    timerRunning = json.optBoolean("timerRunning", false),
                    focusSessions = json.optInt("focusSessions", 0),
                    moodDate = json.optString("moodDate", ""),
                    moodScore = json.optInt("moodScore", 3),
                    moodCheckins = json.optInt("moodCheckins", 0)
                )
                state.timerRemainingMillis = json.optLong("timerRemainingMillis", state.timerDurationMillis)

                val habitsJson = json.optJSONArray("habits")
                if (habitsJson != null) {
                    for (index in 0 until habitsJson.length()) {
                        val habit = habitsJson.optJSONObject(index)
                        if (habit != null) {
                            state.habits.add(Habit.fromJson(habit))
                        }
                    }
                }
                if (state.habits.isEmpty()) {
                    state.habits.add(Habit("Hydrate", "Drink one glass", 8, 5))
                }
                return state
            }
        }
    }

    companion object {
        private val BG = Color.rgb(16, 24, 20)
        private val SURFACE = Color.rgb(28, 40, 34)
        private val INK = Color.rgb(245, 247, 241)
        private val MUTED = Color.rgb(174, 190, 180)
        private val FOREST = Color.rgb(69, 161, 129)
        private val GOLD = Color.rgb(249, 199, 79)
        private val LINE = Color.rgb(57, 75, 65)
    }
}
