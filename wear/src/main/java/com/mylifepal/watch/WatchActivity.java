package com.mylifepal.watch;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class WatchActivity extends Activity {
    private static final int BG = Color.rgb(16, 24, 20);
    private static final int SURFACE = Color.rgb(28, 40, 34);
    private static final int INK = Color.rgb(245, 247, 241);
    private static final int MUTED = Color.rgb(174, 190, 180);
    private static final int FOREST = Color.rgb(69, 161, 129);
    private static final int GOLD = Color.rgb(249, 199, 79);
    private static final int LINE = Color.rgb(57, 75, 65);

    private SharedPreferences prefs;
    private WatchState state;
    private LinearLayout root;
    private final Handler timerHandler = new Handler(Looper.getMainLooper());
    private final Runnable timerRunnable = new Runnable() {
        @Override
        public void run() {
            tickTimer();
            timerHandler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);

        prefs = getSharedPreferences("mylifepal_watch_state", MODE_PRIVATE);
        state = loadState();

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        setContentView(root);
        render();
        timerHandler.post(timerRunnable);
    }

    @Override
    protected void onDestroy() {
        timerHandler.removeCallbacks(timerRunnable);
        super.onDestroy();
    }

    private void render() {
        root.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(18), dp(18), dp(24));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        addHeader(content);
        addSpace(content, 12);

        Habit next = nextHabit();
        if (next == null) {
            addClearedCard(content);
        } else {
            addFocusCard(content, next);
        }

        addSpace(content, 12);
        addTinyStats(content);
        addSpace(content, 12);
        addTimerCard(content);
        addSpace(content, 12);
        addMoodCard(content);
        addSpace(content, 12);

        for (Habit habit : state.habits) {
            addHabitRow(content, habit);
            addSpace(content, 8);
        }
    }

    private void addHeader(LinearLayout content) {
        TextView title = text("MyLifePal", 24, INK, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        content.addView(title, fullWrap());

        TextView level = pill("Lv " + level() + "  |  " + state.coins + " coins", GOLD, BG);
        content.addView(level, fullWrap());
        addSpace(content, 8);

        ProgressBar bar = progressBar(state.totalXp % 100, 100);
        content.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(8)
        ));
    }

    private void addFocusCard(LinearLayout content, Habit habit) {
        LinearLayout card = card();
        card.addView(text("Next tiny action", 13, GOLD, Typeface.BOLD));
        TextView name = text(habit.name, 22, INK, Typeface.BOLD);
        name.setGravity(Gravity.CENTER_HORIZONTAL);
        card.addView(name);
        TextView tiny = text(habit.tinyAction, 15, MUTED, Typeface.NORMAL);
        tiny.setGravity(Gravity.CENTER_HORIZONTAL);
        tiny.setLineSpacing(dp(2), 1f);
        card.addView(tiny);
        addSpace(card, 12);
        Button done = button("Complete", FOREST, Color.WHITE);
        done.setOnClickListener(v -> complete(habit));
        card.addView(done, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
        ));
        content.addView(card);
    }

    private void addMoodCard(LinearLayout content) {
        LinearLayout card = card();
        card.setBackground(round(Color.rgb(32, 36, 52), 18, LINE, 1));
        card.addView(text("Emotion check-in", 13, GOLD, Typeface.BOLD));
        TextView mood = text(today().equals(state.moodDate) ? moodLabel(state.moodScore) : "How are you?", 21, INK, Typeface.BOLD);
        mood.setGravity(Gravity.CENTER);
        card.addView(mood);
        TextView count = text(state.moodCheckins + " check-ins", 12, MUTED, Typeface.NORMAL);
        count.setGravity(Gravity.CENTER);
        card.addView(count);
        addSpace(card, 8);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(actions, fullWrap());
        Button low = button("Low", Color.rgb(213, 139, 63), Color.WHITE);
        low.setOnClickListener(v -> recordMood(2));
        actions.addView(low, new LinearLayout.LayoutParams(0, dp(40), 1f));
        addGap(actions, 6);
        Button okay = button("Okay", GOLD, BG);
        okay.setOnClickListener(v -> recordMood(3));
        actions.addView(okay, new LinearLayout.LayoutParams(0, dp(40), 1f));
        addGap(actions, 6);
        Button good = button("Good", FOREST, Color.WHITE);
        good.setOnClickListener(v -> recordMood(4));
        actions.addView(good, new LinearLayout.LayoutParams(0, dp(40), 1f));
        content.addView(card);
    }

    private void addClearedCard(LinearLayout content) {
        LinearLayout card = card();
        card.addView(text("Today cleared", 22, INK, Typeface.BOLD));
        TextView copy = text("Your tiny votes are in.", 15, MUTED, Typeface.NORMAL);
        copy.setGravity(Gravity.CENTER);
        card.addView(copy);
        content.addView(card);
    }

    private void addTinyStats(LinearLayout content) {
        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setGravity(Gravity.CENTER);
        content.addView(stats, fullWrap());
        stats.addView(stat(todayCount() + "/" + state.habits.size(), "today"));
        addGap(stats, 8);
        stats.addView(stat(state.totalXp + "", "XP"));
    }

    private void addTimerCard(LinearLayout content) {
        LinearLayout card = card();
        card.setBackground(round(Color.rgb(37, 48, 38), 18, LINE, 1));
        card.addView(text("Tomato Timer", 13, GOLD, Typeface.BOLD));
        TextView clock = text(formatTimer(timerRemainingMillis()), 28, INK, Typeface.BOLD);
        clock.setGravity(Gravity.CENTER);
        card.addView(clock);
        TextView status = text(state.timerRunning ? "Focus running" : state.focusSessions + " tomatoes done", 12, MUTED, Typeface.NORMAL);
        status.setGravity(Gravity.CENTER);
        card.addView(status);
        addSpace(card, 8);
        card.addView(progressBar((int) (state.timerDurationMillis - timerRemainingMillis()), (int) state.timerDurationMillis), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(7)
        ));
        addSpace(card, 8);
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        card.addView(actions, fullWrap());
        Button primary = button(state.timerRunning ? "Pause" : "Start", state.timerRunning ? SURFACE : FOREST, state.timerRunning ? INK : Color.WHITE);
        primary.setOnClickListener(v -> {
            if (state.timerRunning) {
                pauseTimer();
            } else {
                startTimer();
            }
        });
        actions.addView(primary, new LinearLayout.LayoutParams(0, dp(42), 1f));
        addGap(actions, 8);
        Button reset = button("Reset", GOLD, BG);
        reset.setOnClickListener(v -> resetTimer());
        actions.addView(reset, new LinearLayout.LayoutParams(0, dp(42), 1f));
        content.addView(card);
    }

    private void addHabitRow(LinearLayout content, Habit habit) {
        LinearLayout row = card();
        row.setPadding(dp(14), dp(12), dp(14), dp(12));
        row.setBackground(round(completedToday(habit) ? Color.rgb(35, 58, 45) : SURFACE, 18, LINE, 1));

        TextView title = text(habit.name, 17, INK, Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        row.addView(title);
        TextView streak = text(habit.streak + "d streak  |  +" + habit.xp + " XP", 12, MUTED, Typeface.NORMAL);
        streak.setGravity(Gravity.CENTER);
        row.addView(streak);

        if (!completedToday(habit)) {
            addSpace(row, 8);
            Button done = button("Done", GOLD, BG);
            done.setOnClickListener(v -> complete(habit));
            row.addView(done, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(42)
            ));
        }
        content.addView(row);
    }

    private void complete(Habit habit) {
        if (completedToday(habit)) {
            return;
        }
        habit.streak = yesterday().equals(habit.lastCompleted) ? habit.streak + 1 : 1;
        habit.bestStreak = Math.max(habit.bestStreak, habit.streak);
        habit.lastCompleted = today();
        habit.completions++;
        int gain = habit.xp + Math.min(10, habit.streak * 2);
        state.totalXp += gain;
        state.coins += habit.coins;
        saveState();
        Toast.makeText(this, "+" + gain + " XP", Toast.LENGTH_SHORT).show();
        render();
    }

    private Habit nextHabit() {
        for (Habit habit : state.habits) {
            if (!completedToday(habit)) {
                return habit;
            }
        }
        return null;
    }

    private boolean completedToday(Habit habit) {
        return today().equals(habit.lastCompleted);
    }

    private int todayCount() {
        int count = 0;
        for (Habit habit : state.habits) {
            if (completedToday(habit)) {
                count++;
            }
        }
        return count;
    }

    private int level() {
        return state.totalXp / 100 + 1;
    }

    private void recordMood(int score) {
        boolean newDay = !today().equals(state.moodDate);
        state.moodDate = today();
        state.moodScore = score;
        if (newDay) {
            state.moodCheckins++;
            state.totalXp += 5;
            state.coins += 3;
            Toast.makeText(this, "Mood tracked: +5 XP", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Mood updated", Toast.LENGTH_SHORT).show();
        }
        saveState();
        render();
    }

    private String moodLabel(int score) {
        if (score <= 2) {
            return "Low";
        }
        if (score == 3) {
            return "Okay";
        }
        return "Good";
    }

    private void startTimer() {
        long remaining = timerRemainingMillis();
        if (remaining <= 0) {
            remaining = state.timerDurationMillis;
        }
        state.timerEndAtMillis = System.currentTimeMillis() + remaining;
        state.timerRemainingMillis = remaining;
        state.timerRunning = true;
        saveState();
        render();
    }

    private void pauseTimer() {
        state.timerRemainingMillis = timerRemainingMillis();
        state.timerEndAtMillis = 0;
        state.timerRunning = false;
        saveState();
        render();
    }

    private void resetTimer() {
        state.timerRunning = false;
        state.timerEndAtMillis = 0;
        state.timerRemainingMillis = state.timerDurationMillis;
        saveState();
        render();
    }

    private void tickTimer() {
        if (!state.timerRunning) {
            return;
        }
        if (timerRemainingMillis() <= 0) {
            completeTimer();
            return;
        }
        render();
    }

    private void completeTimer() {
        state.timerRunning = false;
        state.timerEndAtMillis = 0;
        state.timerRemainingMillis = state.timerDurationMillis;
        state.focusSessions++;
        state.totalXp += 12;
        state.coins += 8;
        saveState();
        Toast.makeText(this, "Tomato complete: +12 XP", Toast.LENGTH_SHORT).show();
        render();
    }

    private long timerRemainingMillis() {
        if (state.timerRunning) {
            return Math.max(0, state.timerEndAtMillis - System.currentTimeMillis());
        }
        return Math.max(0, state.timerRemainingMillis);
    }

    private String formatTimer(long millis) {
        long totalSeconds = Math.max(0, (millis + 999) / 1000);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private WatchState loadState() {
        String raw = prefs.getString("state", null);
        if (raw == null) {
            return defaultState();
        }
        try {
            return WatchState.fromJson(new JSONObject(raw));
        } catch (JSONException exception) {
            return defaultState();
        }
    }

    private void saveState() {
        prefs.edit().putString("state", state.toJson().toString()).apply();
    }

    private WatchState defaultState() {
        WatchState defaultState = new WatchState();
        defaultState.coins = 10;
        defaultState.habits.add(new Habit("Hydrate", "Drink one glass", 8, 5));
        defaultState.habits.add(new Habit("Walk", "Walk for 2 minutes", 12, 8));
        defaultState.habits.add(new Habit("Breathe", "Take 5 slow breaths", 8, 5));
        return defaultState;
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(sp);
        text.setTextColor(color);
        text.setTypeface(Typeface.DEFAULT, style);
        text.setIncludeFontPadding(true);
        return text;
    }

    private TextView pill(String value, int background, int foreground) {
        TextView pill = text(value, 13, foreground, Typeface.BOLD);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(12), dp(7), dp(12), dp(7));
        pill.setBackground(round(background, 999));
        return pill;
    }

    private TextView stat(String value, String label) {
        TextView stat = text(value + "\n" + label, 13, INK, Typeface.BOLD);
        stat.setGravity(Gravity.CENTER);
        stat.setBackground(round(SURFACE, 16, LINE, 1));
        stat.setPadding(dp(12), dp(8), dp(12), dp(8));
        stat.setLineSpacing(dp(2), 1f);
        stat.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return stat;
    }

    private Button button(String label, int background, int foreground) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(foreground);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setBackground(round(background, 999));
        return button;
    }

    private LinearLayout card() {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(round(SURFACE, 20, LINE, 1));
        return card;
    }

    private ProgressBar progressBar(int progress, int max) {
        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(max);
        bar.setProgress(progress);
        bar.setProgressTintList(ColorStateList.valueOf(GOLD));
        bar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(52, 67, 59)));
        return bar;
    }

    private GradientDrawable round(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private GradientDrawable round(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable drawable = round(color, radiusDp);
        drawable.setStroke(dp(strokeDp), strokeColor);
        return drawable;
    }

    private LinearLayout.LayoutParams fullWrap() {
        return new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
    }

    private void addSpace(LinearLayout parent, int value) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(1, dp(value)));
    }

    private void addGap(LinearLayout parent, int value) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(dp(value), 1));
    }

    private String today() {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Calendar.getInstance().getTime());
    }

    private String yesterday() {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -1);
        return new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(calendar.getTime());
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private static class Habit {
        String name;
        String tinyAction;
        String lastCompleted = "";
        int xp;
        int coins;
        int streak = 0;
        int bestStreak = 0;
        int completions = 0;

        Habit(String name, String tinyAction, int xp, int coins) {
            this.name = name;
            this.tinyAction = tinyAction;
            this.xp = xp;
            this.coins = coins;
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("name", name);
                json.put("tinyAction", tinyAction);
                json.put("lastCompleted", lastCompleted);
                json.put("xp", xp);
                json.put("coins", coins);
                json.put("streak", streak);
                json.put("bestStreak", bestStreak);
                json.put("completions", completions);
            } catch (JSONException ignored) {
            }
            return json;
        }

        static Habit fromJson(JSONObject json) {
            Habit habit = new Habit(
                    json.optString("name", "Tiny habit"),
                    json.optString("tinyAction", "Do the 2-minute version"),
                    json.optInt("xp", 8),
                    json.optInt("coins", 5)
            );
            habit.lastCompleted = json.optString("lastCompleted", "");
            habit.streak = json.optInt("streak", 0);
            habit.bestStreak = json.optInt("bestStreak", 0);
            habit.completions = json.optInt("completions", 0);
            return habit;
        }
    }

    private static class WatchState {
        int totalXp = 0;
        int coins = 0;
        long timerDurationMillis = 25L * 60L * 1000L;
        long timerRemainingMillis = 25L * 60L * 1000L;
        long timerEndAtMillis = 0L;
        boolean timerRunning = false;
        int focusSessions = 0;
        String moodDate = "";
        int moodScore = 3;
        int moodCheckins = 0;
        List<Habit> habits = new ArrayList<>();

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("totalXp", totalXp);
                json.put("coins", coins);
                json.put("timerDurationMillis", timerDurationMillis);
                json.put("timerRemainingMillis", timerRemainingMillis);
                json.put("timerEndAtMillis", timerEndAtMillis);
                json.put("timerRunning", timerRunning);
                json.put("focusSessions", focusSessions);
                json.put("moodDate", moodDate);
                json.put("moodScore", moodScore);
                json.put("moodCheckins", moodCheckins);
                JSONArray habitsJson = new JSONArray();
                for (Habit habit : habits) {
                    habitsJson.put(habit.toJson());
                }
                json.put("habits", habitsJson);
            } catch (JSONException ignored) {
            }
            return json;
        }

        static WatchState fromJson(JSONObject json) {
            WatchState state = new WatchState();
            state.totalXp = json.optInt("totalXp", 0);
            state.coins = json.optInt("coins", 0);
            state.timerDurationMillis = json.optLong("timerDurationMillis", 25L * 60L * 1000L);
            state.timerRemainingMillis = json.optLong("timerRemainingMillis", state.timerDurationMillis);
            state.timerEndAtMillis = json.optLong("timerEndAtMillis", 0L);
            state.timerRunning = json.optBoolean("timerRunning", false);
            state.focusSessions = json.optInt("focusSessions", 0);
            state.moodDate = json.optString("moodDate", "");
            state.moodScore = json.optInt("moodScore", 3);
            state.moodCheckins = json.optInt("moodCheckins", 0);
            JSONArray habitsJson = json.optJSONArray("habits");
            if (habitsJson != null) {
                for (int i = 0; i < habitsJson.length(); i++) {
                    JSONObject habit = habitsJson.optJSONObject(i);
                    if (habit != null) {
                        state.habits.add(Habit.fromJson(habit));
                    }
                }
            }
            if (state.habits.isEmpty()) {
                state.habits.add(new Habit("Hydrate", "Drink one glass", 8, 5));
            }
            return state;
        }
    }
}
