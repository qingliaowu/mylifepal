package com.mylifepal.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.Base64;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int BG = Color.rgb(245, 247, 241);
    private static final int SURFACE = Color.WHITE;
    private static final int INK = Color.rgb(23, 32, 27);
    private static final int MUTED = Color.rgb(93, 105, 98);
    private static final int LINE = Color.rgb(221, 228, 221);
    private static final int FOREST = Color.rgb(46, 125, 104);
    private static final int GOLD = Color.rgb(249, 199, 79);
    private static final int CORAL = Color.rgb(238, 116, 94);
    private static final int SKY = Color.rgb(86, 141, 189);
    private static final int LILAC = Color.rgb(139, 116, 190);
    private static final ThemePreset[] THEME_PRESETS = {
            new ThemePreset("Forest", FOREST, GOLD, BG),
            new ThemePreset("Ocean", Color.rgb(31, 105, 151), Color.rgb(66, 190, 182), Color.rgb(241, 247, 249)),
            new ThemePreset("Berry", Color.rgb(129, 69, 132), Color.rgb(234, 126, 101), Color.rgb(249, 244, 248)),
            new ThemePreset("Sunrise", Color.rgb(182, 92, 56), Color.rgb(245, 184, 74), Color.rgb(250, 246, 240)),
            new ThemePreset("Graphite", Color.rgb(75, 91, 105), Color.rgb(81, 163, 154), Color.rgb(245, 246, 247)),
            new ThemePreset("Rose", Color.rgb(175, 75, 106), Color.rgb(78, 156, 133), Color.rgb(251, 245, 247))
    };
    private static final String[] THEME_CHOICES = {"Forest", "Ocean", "Berry", "Sunrise", "Graphite", "Rose", "Custom"};

    private static final String PREFS = "mylifepal_state";
    private static final String STATE_KEY = "state";
    private static final int REQUEST_SAVE_BACKUP = 41;
    private static final int REQUEST_OPEN_BACKUP = 42;
    private static final int REQUEST_NOTIFICATIONS = 43;
    private static final String[] TABS = {"Today", "Timer", "Mood", "Habits", "Rewards", "Progress"};
    private static final String[] ATTRIBUTES = {"Mind", "Body", "Craft", "Home", "Social"};
    private static final String[] REMINDER_TIMES = {"07:00", "08:00", "09:00", "12:30", "17:30", "19:30", "21:30"};
    private static final String[] TRACKING_TYPES = {"Completion", "Quantity", "Time"};
    private static final String[] GOAL_MODES = {"Goal", "Limit"};
    private static final String[] PERIODS = {"Daily", "Weekly", "Monthly", "Yearly", "Certain weekdays"};
    private static final String[] CHEST_ITEMS = {
            "Focus badge", "Clear desk token", "Deep work spark", "Morning anchor",
            "Fresh start card", "Momentum charm", "Calm streak pin"
    };
    private static final String[] COMMON_LOOT = {
            "Clean desk card", "Fresh start token", "Stretch break ticket", "Water refill badge"
    };
    private static final String[] RARE_LOOT = {
            "Deep work charm", "Habit shield", "Double coin sticker", "Calm mind relic"
    };
    private static final String[] EPIC_LOOT = {
            "Identity crown", "Weekend quest pass", "Mastery compass"
    };

    private SharedPreferences prefs;
    private AppState state;
    private LinearLayout root;
    private int selectedTab = 0;
    private boolean appUnlocked = true;
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
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        state = loadState();
        applySystemBars();
        ReminderScheduler.ensureChannel(this);
        ReminderScheduler.refreshAll(this);

        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(backgroundColor());
        setContentView(root);
        appUnlocked = !securityEnabled();
        if (appUnlocked) {
            render();
        } else {
            renderLockedScreen();
            root.post(this::showUnlockDialog);
        }
        timerHandler.post(timerRunnable);
    }

    @Override
    protected void onDestroy() {
        timerHandler.removeCallbacks(timerRunnable);
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) {
            return;
        }
        if (securityEnabled() && !appUnlocked) {
            return;
        }
        Uri uri = data.getData();
        if (requestCode == REQUEST_SAVE_BACKUP) {
            writeBackupToUri(uri);
        } else if (requestCode == REQUEST_OPEN_BACKUP) {
            restoreBackupFromUri(uri);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_NOTIFICATIONS) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            Toast.makeText(this, granted ? "Reminders are ready" : "Reminders saved. Notifications are disabled.", Toast.LENGTH_LONG).show();
            ReminderScheduler.refreshAll(this);
        }
    }

    private void render() {
        applySystemBars();
        root.setBackgroundColor(backgroundColor());
        if (securityEnabled() && !appUnlocked) {
            renderLockedScreen();
            return;
        }
        root.removeAllViews();
        addHeader();
        addTabs();

        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(false);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(18), dp(16), dp(18), dp(28));
        scrollView.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));
        root.addView(scrollView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
        ));

        if (selectedTab == 0) {
            renderToday(content);
        } else if (selectedTab == 1) {
            renderTimer(content);
        } else if (selectedTab == 2) {
            renderMood(content);
        } else if (selectedTab == 3) {
            renderHabits(content);
        } else if (selectedTab == 4) {
            renderRewards(content);
        } else {
            renderProgress(content);
        }
    }

    private void renderLockedScreen() {
        root.removeAllViews();
        LinearLayout shell = vertical();
        shell.setGravity(Gravity.CENTER);
        shell.setPadding(dp(28), dp(28), dp(28), dp(28));
        root.addView(shell, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        TextView title = text("MyLifePal is locked", 28, primaryColor(), Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        shell.addView(title);
        TextView subtitle = text("Enter your security password to view habits, moods, rewards, backups, and progress.", 15, MUTED, Typeface.NORMAL);
        subtitle.setGravity(Gravity.CENTER);
        shell.addView(subtitle);
        addSpace(shell, 18);
        Button unlock = button("Unlock", primaryColor(), readableTextColor(primaryColor()));
        unlock.setOnClickListener(v -> showUnlockDialog());
        shell.addView(unlock, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
    }

    private void showUnlockDialog() {
        EditText password = input("Security password", "");
        password.setSingleLine(true);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Unlock MyLifePal")
                .setView(password)
                .setPositiveButton("Unlock", null)
                .setNegativeButton("Close", null)
                .create();
        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setTextColor(primaryTextColor());
            positive.setOnClickListener(v -> {
                if (!verifySecurityPassword(password.getText().toString())) {
                    password.setError("Password does not match");
                    password.setText("");
                    return;
                }
                appUnlocked = true;
                dialog.dismiss();
                render();
            });
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negative != null) {
                negative.setTextColor(MUTED);
            }
        });
        dialog.show();
    }

    private void addHeader() {
        LinearLayout header = vertical();
        header.setPadding(dp(20), dp(20), dp(20), dp(18));
        GradientDrawable headerBg = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{shade(primaryColor(), 0.28f), primaryColor(), tint(primaryColor(), 0.22f)}
        );
        header.setBackground(headerBg);
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout titleRow = horizontal();
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(titleRow);

        LinearLayout titleBlock = vertical();
        titleRow.addView(titleBlock, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        titleBlock.addView(text("MyLifePal", 30, Color.WHITE, Typeface.BOLD));
        titleBlock.addView(text("Tiny habits. Real-life levels.", 14, Color.rgb(223, 242, 230), Typeface.NORMAL));

        TextView levelPill = pill("Lv " + getLevel(), Color.rgb(255, 255, 255), primaryColor());
        levelPill.setTextSize(16);
        titleRow.addView(levelPill);

        addSpace(header, 16);
        TextView identity = text(getIdentityLine(), 15, Color.rgb(241, 250, 244), Typeface.NORMAL);
        identity.setLineSpacing(dp(2), 1f);
        header.addView(identity);

        addSpace(header, 14);
        ProgressBar xpBar = progressBar(xpIntoLevel(), xpPerLevel(), accentColor());
        header.addView(xpBar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(8)
        ));

        addSpace(header, 10);
        LinearLayout statRow = horizontal();
        statRow.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(statRow);
        statRow.addView(headerStat(state.totalXp + " XP", "total"));
        statRow.addView(headerStat(state.coins + " coins", "wallet"));
        statRow.addView(headerStat(todayCompletionCount() + "/" + activeHabits().size(), "done today"));

        addSpace(header, 14);
        LinearLayout actionRow = horizontal();
        actionRow.setGravity(Gravity.CENTER_VERTICAL);
        header.addView(actionRow);
        Button addHabit = button("Add habit", Color.WHITE, primaryColor());
        addHabit.setOnClickListener(v -> showHabitDialog(null));
        actionRow.addView(addHabit, new LinearLayout.LayoutParams(0, dp(48), 1f));
        addGap(actionRow, 10);
        Button quickStart = button("2-min start", accentColor(), readableTextColor(accentColor()));
        quickStart.setOnClickListener(v -> startTinyAction());
        actionRow.addView(quickStart, new LinearLayout.LayoutParams(0, dp(48), 1f));
    }

    private void addTabs() {
        LinearLayout shell = new LinearLayout(this);
        shell.setPadding(dp(12), dp(10), dp(12), dp(8));
        shell.setBackgroundColor(backgroundColor());
        root.addView(shell, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        LinearLayout tabs = horizontal();
        tabs.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabs.setBackground(round(tint(primaryColor(), 0.86f), 8));
        shell.addView(tabs, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(52)
        ));

        for (int i = 0; i < TABS.length; i++) {
            final int index = i;
            Button tab = button(TABS[i], selectedTab == i ? primaryColor() : Color.TRANSPARENT, selectedTab == i ? readableTextColor(primaryColor()) : INK);
            tab.setTextSize(13);
            tab.setSelected(selectedTab == i);
            tab.setContentDescription(TABS[i] + (selectedTab == i ? " tab selected" : " tab"));
            tab.setOnClickListener(v -> {
                selectedTab = index;
                render();
            });
            tabs.addView(tab, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
    }

    private void renderToday(LinearLayout content) {
        addDailyCoach(content);
        addSpace(content, 14);
        Habit next = firstIncompleteHabit();
        if (next != null) {
            addAtomicCoach(content, next);
            addSpace(content, 14);
        } else {
            LinearLayout done = card();
            done.addView(text("Day cleared", 22, INK, Typeface.BOLD));
            done.addView(text("Every completed habit is one vote for the person you are becoming. Claim a reward or add a tiny next step.", 15, MUTED, Typeface.NORMAL));
            addSpace(done, 12);
            Button reward = button("Open rewards", primaryColor(), readableTextColor(primaryColor()));
            reward.setOnClickListener(v -> {
                selectedTab = 4;
                render();
            });
            done.addView(reward, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
            content.addView(done);
            addSpace(content, 14);
        }

        addSectionHeader(content, "Today", "Tap complete once per day. Streaks grow when you return tomorrow.");
        List<Habit> active = activeHabits();
        if (active.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("No active habits yet", 20, INK, Typeface.BOLD));
            empty.addView(text("Create one tiny action with a clear cue and a satisfying reward.", 15, MUTED, Typeface.NORMAL));
            addSpace(empty, 12);
            Button add = button("Create first habit", primaryColor(), readableTextColor(primaryColor()));
            add.setOnClickListener(v -> showHabitDialog(null));
            empty.addView(add, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
            content.addView(empty);
            return;
        }

        for (Habit habit : active) {
            content.addView(habitCard(habit, true));
            addSpace(content, 12);
        }
    }

    private void addDailyCoach(LinearLayout content) {
        int action = coachAction();
        LinearLayout coach = card();
        coach.setBackground(round(coachColor(action), 8, coachAccent(action), 2));
        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        coach.addView(top);

        LinearLayout copy = vertical();
        top.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        copy.addView(text("MyLifePal Coach", 13, primaryTextColor(), Typeface.BOLD));
        copy.addView(text(coachHeadline(action), 23, INK, Typeface.BOLD));
        TextView reason = text(coachReason(action), 14, MUTED, Typeface.NORMAL);
        reason.setLineSpacing(dp(2), 1f);
        copy.addView(reason);
        top.addView(pill(todayScore() + "%", coachAccent(action), readableTextColor(coachAccent(action))));

        addSpace(coach, 12);
        coach.addView(detailLine("Today score", todayScoreLine()));
        coach.addView(detailLine("Companion", state.monsterName + " is " + monsterMoodLine() + " at level " + monsterLevel()));
        coach.addView(detailLine("Reminder", nextReminderLine()));

        addSpace(coach, 12);
        Button actionButton = button(coachButtonLabel(action), coachAccent(action), readableTextColor(coachAccent(action)));
        actionButton.setOnClickListener(v -> runCoachAction(action));
        coach.addView(actionButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        content.addView(coach);
    }

    private void addAtomicCoach(LinearLayout content, Habit habit) {
        LinearLayout coach = card();
        coach.setBackground(round(accentSoft(), 8, accentColor(), 2));

        LinearLayout row = horizontal();
        row.setGravity(Gravity.CENTER_VERTICAL);
        coach.addView(row);
        LinearLayout copy = vertical();
        row.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        copy.addView(text("Atomic loop", 13, primaryTextColor(), Typeface.BOLD));
        copy.addView(text(habit.icon + " " + habit.name, 24, INK, Typeface.BOLD));
        TextView tiny = text("Tiny action: " + habit.tinyAction, 15, MUTED, Typeface.NORMAL);
        tiny.setLineSpacing(dp(2), 1f);
        copy.addView(tiny);
        TextView points = pill("+" + habit.xp + " XP", primaryColor(), readableTextColor(primaryColor()));
        row.addView(points);

        addSpace(coach, 12);
        coach.addView(detailLine("Cue", habit.cue));
        coach.addView(detailLine("Identity", habit.identity));
        coach.addView(detailLine("Reward", habit.reward));

        addSpace(coach, 12);
        LinearLayout actions = horizontal();
        coach.addView(actions);
        Button start = button("Start 2 minutes", primaryColor(), readableTextColor(primaryColor()));
        start.setOnClickListener(v -> Toast.makeText(this, "Start small: " + habit.tinyAction, Toast.LENGTH_LONG).show());
        actions.addView(start, new LinearLayout.LayoutParams(0, dp(48), 1f));
        addGap(actions, 10);
        Button complete = button("Complete", accentColor(), readableTextColor(accentColor()));
        complete.setOnClickListener(v -> completeHabit(habit));
        actions.addView(complete, new LinearLayout.LayoutParams(0, dp(48), 1f));

        content.addView(coach);
    }

    private void renderTimer(LinearLayout content) {
        ensureTimerDay();
        addSectionHeader(content, "Tomato Timer", "A focus tomato turns one tiny action into protected work time.");

        LinearLayout timer = card();
        timer.setBackground(round(accentSoft(), 8, accentColor(), 2));
        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        timer.addView(top);
        LinearLayout copy = vertical();
        top.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        copy.addView(text(timerModeLabel(), 14, primaryTextColor(), Typeface.BOLD));
        copy.addView(text(state.timerRunning ? "In focus" : "Ready when you are", 21, INK, Typeface.BOLD));
        top.addView(pill(state.timerRunning ? "Running" : "Paused", state.timerRunning ? primaryColor() : Color.rgb(226, 232, 226), state.timerRunning ? readableTextColor(primaryColor()) : MUTED));

        addSpace(timer, 10);
        TextView clock = text(formatTimer(timerRemainingMillis()), 48, INK, Typeface.BOLD);
        clock.setGravity(Gravity.CENTER);
        timer.addView(clock);
        addSpace(timer, 8);
        long duration = Math.max(1, state.timerDurationMillis);
        int elapsed = (int) Math.max(0, Math.min(duration, duration - timerRemainingMillis()));
        timer.addView(progressBar(elapsed, (int) duration, timerModeColor()), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(10)
        ));

        addSpace(timer, 12);
        timer.addView(detailLine("Reward", state.timerMode == 0 ? "+18 XP, +12 coins, +Mind XP" : "Reset energy for the next tiny action"));
        timer.addView(detailLine("Today", state.tomatoSessionsToday + " tomatoes, " + state.tomatoMinutesToday + " focus minutes"));
        timer.addView(detailLine("Total", state.tomatoFocusSessions + " focus tomatoes"));

        addSpace(timer, 12);
        LinearLayout actions = horizontal();
        timer.addView(actions);
        Button primary = button(state.timerRunning ? "Pause" : "Start", state.timerRunning ? Color.rgb(233, 238, 234) : primaryColor(), state.timerRunning ? INK : readableTextColor(primaryColor()));
        primary.setOnClickListener(v -> {
            if (state.timerRunning) {
                pauseTimer();
            } else {
                startTimer();
            }
        });
        actions.addView(primary, new LinearLayout.LayoutParams(0, dp(48), 1f));
        addGap(actions, 10);
        Button reset = button("Reset", accentColor(), readableTextColor(accentColor()));
        reset.setOnClickListener(v -> resetTimer());
        actions.addView(reset, new LinearLayout.LayoutParams(0, dp(48), 1f));

        content.addView(timer);
        addSpace(content, 14);

        addSectionHeader(content, "Modes", "Pick the smallest time box that helps you begin.");
        LinearLayout modes = vertical();
        content.addView(modes);
        addModeButton(modes, 0, "Focus tomato", "25 minutes for deep work");
        addSpace(modes, 8);
        addModeButton(modes, 1, "Short break", "5 minutes to reset");
        addSpace(modes, 8);
        addModeButton(modes, 2, "Long break", "15 minutes after four tomatoes");
    }

    private void addModeButton(LinearLayout parent, int mode, String title, String subtitle) {
        LinearLayout row = card();
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setBackground(round(state.timerMode == mode ? primarySoft() : Color.WHITE, 8, state.timerMode == mode ? primaryColor() : LINE, 1));
        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(top);
        LinearLayout copy = vertical();
        top.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        copy.addView(text(title, 17, INK, Typeface.BOLD));
        copy.addView(text(subtitle, 13, MUTED, Typeface.NORMAL));
        int pillColor = mode == 0 ? primaryColor() : accentColor();
        top.addView(pill(formatTimer(timerDurationForMode(mode)), pillColor, readableTextColor(pillColor)));
        row.setOnClickListener(v -> selectTimerMode(mode));
        parent.addView(row);
    }

    private void renderMood(LinearLayout content) {
        addSectionHeader(content, "Emotion tracker", "Name the weather inside, then choose the next tiny action with more honesty.");
        MoodEntry todayMood = moodForDate(today());

        LinearLayout checkin = card();
        checkin.setBackground(round(todayMood == null ? Color.rgb(248, 244, 255) : primarySoft(), 8, todayMood == null ? LILAC : primaryColor(), 2));
        if (todayMood == null) {
            checkin.addView(text("No check-in today", 24, INK, Typeface.BOLD));
            checkin.addView(text("A ten-second mood check gives the life game a little more self-awareness.", 15, MUTED, Typeface.NORMAL));
        } else {
            checkin.addView(text(moodLabel(todayMood.mood), 26, INK, Typeface.BOLD));
            checkin.addView(text("Energy " + todayMood.energy + "/5  |  Stress " + todayMood.stress + "/5", 15, MUTED, Typeface.BOLD));
            if (!todayMood.note.isEmpty()) {
                addSpace(checkin, 8);
                checkin.addView(text(todayMood.note, 15, MUTED, Typeface.NORMAL));
            }
            addSpace(checkin, 8);
            checkin.addView(text(moodInsight(todayMood), 14, primaryTextColor(), Typeface.BOLD));
        }
        addSpace(checkin, 12);
        int checkInColor = todayMood == null ? LILAC : primaryColor();
        Button checkIn = button(todayMood == null ? "Check in" : "Edit check-in", checkInColor, readableTextColor(checkInColor));
        checkIn.setOnClickListener(v -> showMoodDialog(todayMood));
        checkin.addView(checkIn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        content.addView(checkin);
        addSpace(content, 14);

        addMoodTrend(content);
        addSpace(content, 14);
        addSectionHeader(content, "Recent feelings", "Patterns become easier to work with once they are visible.");
        List<MoodEntry> recent = recentMoodEntries(7);
        if (recent.isEmpty()) {
            LinearLayout empty = card();
            empty.addView(text("No mood history yet", 18, INK, Typeface.BOLD));
            empty.addView(text("Start with today's check-in.", 14, MUTED, Typeface.NORMAL));
            content.addView(empty);
        } else {
            for (MoodEntry entry : recent) {
                content.addView(moodEntryRow(entry));
                addSpace(content, 10);
            }
        }
    }

    private void addMoodTrend(LinearLayout content) {
        List<MoodEntry> recent = recentMoodEntries(7);
        LinearLayout trend = card();
        trend.addView(text("7-day mood trend", 21, INK, Typeface.BOLD));
        if (recent.isEmpty()) {
            trend.addView(text("Check in once to start a trend.", 15, MUTED, Typeface.NORMAL));
            content.addView(trend);
            return;
        }
        int mood = 0;
        int energy = 0;
        int stress = 0;
        for (MoodEntry entry : recent) {
            mood += entry.mood;
            energy += entry.energy;
            stress += entry.stress;
        }
        int count = recent.size();
        int avgMood = Math.round((float) mood / count);
        int avgEnergy = Math.round((float) energy / count);
        int avgStress = Math.round((float) stress / count);
        trend.addView(text(count + " check-ins recorded", 14, MUTED, Typeface.NORMAL));
        addSpace(trend, 10);
        trend.addView(detailLine("Average mood", moodLabel(avgMood)));
        trend.addView(progressBar(avgMood, 5, moodColor(avgMood)), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)));
        addSpace(trend, 8);
        trend.addView(detailLine("Average energy", avgEnergy + "/5"));
        trend.addView(progressBar(avgEnergy, 5, SKY), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)));
        addSpace(trend, 8);
        trend.addView(detailLine("Average stress", avgStress + "/5"));
        trend.addView(progressBar(avgStress, 5, CORAL), new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)));
        content.addView(trend);
    }

    private View moodEntryRow(MoodEntry entry) {
        LinearLayout row = card();
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setBackground(round(Color.WHITE, 8, moodColor(entry.mood), 1));
        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(top);
        LinearLayout copy = vertical();
        top.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        copy.addView(text(entry.date, 13, MUTED, Typeface.BOLD));
        copy.addView(text(moodLabel(entry.mood), 18, INK, Typeface.BOLD));
        copy.addView(text("Energy " + entry.energy + "/5  |  Stress " + entry.stress + "/5", 13, MUTED, Typeface.NORMAL));
        top.addView(pill("Mood " + entry.mood, moodColor(entry.mood), readableTextColor(moodColor(entry.mood))));
        if (!entry.note.isEmpty()) {
            addSpace(row, 8);
            row.addView(text(entry.note, 14, MUTED, Typeface.NORMAL));
        }
        return row;
    }

    private void renderHabits(LinearLayout content) {
        addSectionHeader(content, "Habit studio", "Design tiny actions with cues, identity votes, and rewards.");
        Button add = button("Add habit", primaryColor(), readableTextColor(primaryColor()));
        add.setOnClickListener(v -> showHabitDialog(null));
        content.addView(add, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        addSpace(content, 14);
        addReminderPanel(content);
        addSpace(content, 14);

        for (Habit habit : activeHabits()) {
            content.addView(habitCard(habit, false));
            addSpace(content, 12);
        }
    }

    private void addReminderPanel(LinearLayout content) {
        LinearLayout panel = card();
        panel.setBackground(round(primarySoft(), 8, tint(primaryColor(), 0.58f), 1));
        panel.addView(text("Reminder rhythm", 20, INK, Typeface.BOLD));
        panel.addView(detailLine("Enabled", enabledReminderCount() + " habits"));
        panel.addView(detailLine("Next", nextReminderLine()));
        panel.addView(detailLine("Permission", notificationsAllowed() ? "Notifications allowed" : "Notifications need approval"));
        addSpace(panel, 12);
        Button allow = button(notificationsAllowed() ? "Refresh reminders" : "Allow notifications", notificationsAllowed() ? Color.rgb(233, 238, 234) : primaryColor(), notificationsAllowed() ? INK : readableTextColor(primaryColor()));
        allow.setOnClickListener(v -> {
            if (notificationsAllowed()) {
                ReminderScheduler.refreshAll(this);
                Toast.makeText(this, "Reminders refreshed", Toast.LENGTH_LONG).show();
            } else {
                requestNotificationPermissionIfNeeded();
            }
        });
        panel.addView(allow, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        content.addView(panel);
    }

    private void renderRewards(LinearLayout content) {
        ensureGameDay();
        addSectionHeader(content, "Life game", "Complete quests, earn loot, and turn rewards into real-life power-ups.");
        addCharacterPanel(content);
        addSpace(content, 14);
        addCompanionPanel(content);
        addSpace(content, 14);
        addQuestBoard(content);
        addSpace(content, 14);
        addTreasurePanel(content);
        addSpace(content, 14);
        addCraftingPanel(content);
        addSpace(content, 14);
        addDataVaultPanel(content);
        addSpace(content, 14);
        addSecurityPanel(content);
        addSpace(content, 18);

        addSectionHeader(content, "Reward shop", "Spend coins on things that make good habits satisfying.");

        LinearLayout wallet = card();
        wallet.setBackground(round(accentSoft(), 8));
        wallet.addView(text(state.coins + " coins, " + state.gems + " gems available", 24, INK, Typeface.BOLD));
        wallet.addView(text("Keep rewards concrete: a walk, a book chapter, a favorite snack, a game session, or money set aside.", 15, MUTED, Typeface.NORMAL));
        content.addView(wallet);
        addSpace(content, 14);

        Button addReward = button("Add custom reward", primaryColor(), readableTextColor(primaryColor()));
        addReward.setOnClickListener(v -> showRewardDialog(null));
        content.addView(addReward, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        addSpace(content, 14);

        for (Reward reward : state.rewards) {
            content.addView(rewardCard(reward));
            addSpace(content, 12);
        }

        if (!state.inventory.isEmpty()) {
            addSpace(content, 8);
            addSectionHeader(content, "Inventory", "Chest drops and claimed rewards.");
            for (int i = state.inventory.size() - 1; i >= 0; i--) {
                TextView item = text(state.inventory.get(i), 15, INK, Typeface.NORMAL);
                item.setPadding(dp(14), dp(12), dp(14), dp(12));
                item.setBackground(round(Color.WHITE, 8, LINE, 1));
                content.addView(item, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                ));
                addSpace(content, 8);
            }
        }
    }

    private void addCharacterPanel(LinearLayout content) {
        LinearLayout character = card();
        character.setBackground(round(primarySoft(), 8, primaryColor(), 2));
        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        character.addView(top);
        LinearLayout copy = vertical();
        top.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        copy.addView(text(lifeTitle(), 14, primaryTextColor(), Typeface.BOLD));
        copy.addView(text("Level " + getLevel() + " Life Builder", 25, INK, Typeface.BOLD));
        copy.addView(text(bestAttributeLine(), 14, MUTED, Typeface.NORMAL));
        top.addView(pill(state.gems + " gems", LILAC, readableTextColor(LILAC)));

        addSpace(character, 12);
        character.addView(progressBar(xpIntoLevel(), xpPerLevel(), primaryColor()), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(10)
        ));
        addSpace(character, 12);
        LinearLayout stats = horizontal();
        stats.setGravity(Gravity.CENTER_VERTICAL);
        character.addView(stats);
        stats.addView(smallStat(state.coins + "", "coins"));
        stats.addView(smallStat(state.inventory.size() + "", "loot"));
        stats.addView(smallStat(claimedQuestsToday() + "", "quests"));
        stats.addView(smallStat(state.rewardClaimsToday + "", "rewards"));
        content.addView(character);
    }

    private void addCompanionPanel(LinearLayout content) {
        LinearLayout companion = card();
        companion.setBackground(round(accentSoft(), 8, accentColor(), 2));
        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        companion.addView(top);

        MonsterView monster = new MonsterView(this);
        monster.setContentDescription(state.monsterName + " companion, level " + monsterLevel() + " " + monsterStage() + ", bond " + state.monsterBond + " of 100");
        top.addView(monster, new LinearLayout.LayoutParams(dp(116), dp(116)));
        addGap(top, 14);

        LinearLayout copy = vertical();
        top.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        copy.addView(text(state.monsterName + " the " + monsterStage(), 23, INK, Typeface.BOLD));
        copy.addView(text("Level " + monsterLevel() + " companion", 14, primaryTextColor(), Typeface.BOLD));
        copy.addView(text("It grows when you keep promises, focus, check in with feelings, and care for it.", 14, MUTED, Typeface.NORMAL));

        addSpace(companion, 12);
        companion.addView(progressBar(monsterXpIntoLevel(), monsterXpPerLevel(), LILAC), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(10)
        ));
        addSpace(companion, 10);
        companion.addView(detailLine("Growth", monsterXpIntoLevel() + " / " + monsterXpPerLevel() + " XP to next level"));
        companion.addView(detailLine("Bond", state.monsterBond + " / 100 - " + monsterMoodLine()));
        companion.addView(detailLine("Next evolution", nextMonsterEvolution()));

        addSpace(companion, 12);
        LinearLayout stats = horizontal();
        stats.setGravity(Gravity.CENTER_VERTICAL);
        companion.addView(stats);
        stats.addView(smallStat(monsterLevel() + "", "level"));
        stats.addView(smallStat(state.monsterXp + "", "growth"));
        stats.addView(smallStat(state.monsterBond + "", "bond"));

        addSpace(companion, 12);
        LinearLayout actions = horizontal();
        companion.addView(actions);
        Button feed = button(state.coins >= 15 ? "Feed" : "Need coins", state.coins >= 15 ? primaryColor() : Color.rgb(224, 229, 224), state.coins >= 15 ? readableTextColor(primaryColor()) : MUTED);
        feed.setEnabled(state.coins >= 15);
        feed.setOnClickListener(v -> careForMonster(0));
        actions.addView(feed, new LinearLayout.LayoutParams(0, dp(48), 1f));
        addGap(actions, 8);
        Button train = button(state.gems >= 1 ? "Train" : "Need gem", state.gems >= 1 ? LILAC : Color.rgb(224, 229, 224), state.gems >= 1 ? readableTextColor(LILAC) : MUTED);
        train.setEnabled(state.gems >= 1);
        train.setOnClickListener(v -> careForMonster(1));
        actions.addView(train, new LinearLayout.LayoutParams(0, dp(48), 1f));
        addGap(actions, 8);
        Button bond = button(today().equals(state.monsterLastCareDate) ? "Bonded" : "Bond", today().equals(state.monsterLastCareDate) ? Color.rgb(224, 229, 224) : SKY, today().equals(state.monsterLastCareDate) ? MUTED : readableTextColor(SKY));
        bond.setEnabled(!today().equals(state.monsterLastCareDate));
        bond.setOnClickListener(v -> careForMonster(2));
        actions.addView(bond, new LinearLayout.LayoutParams(0, dp(48), 1f));

        addSpace(companion, 10);
        Button rename = button("Name companion", Color.rgb(233, 238, 234), INK);
        rename.setOnClickListener(v -> showMonsterDialog());
        companion.addView(rename, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        content.addView(companion);
    }

    private void addQuestBoard(LinearLayout content) {
        addSectionHeader(content, "Quest board", "Daily quests give the day a little adventure arc.");
        for (GameQuest quest : gameQuests()) {
            content.addView(questCard(quest));
            addSpace(content, 10);
        }
    }

    private View questCard(GameQuest quest) {
        boolean ready = quest.progress >= quest.goal;
        boolean claimed = isQuestClaimed(quest.id);
        LinearLayout card = card();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackground(round(claimed ? primarySoft() : Color.WHITE, 8, ready ? primaryColor() : LINE, ready ? 2 : 1));

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(top);
        LinearLayout copy = vertical();
        top.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        copy.addView(text(quest.title, 18, INK, Typeface.BOLD));
        copy.addView(text(quest.description, 14, MUTED, Typeface.NORMAL));
        top.addView(pill(claimed ? "Claimed" : ready ? "Ready" : quest.progress + "/" + quest.goal, ready || claimed ? primaryColor() : Color.rgb(224, 229, 224), ready || claimed ? readableTextColor(primaryColor()) : MUTED));

        addSpace(card, 10);
        card.addView(progressBar(Math.min(quest.progress, quest.goal), quest.goal, ready ? primaryColor() : accentColor()), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(8)
        ));
        addSpace(card, 10);
        card.addView(text("Reward: +" + quest.xp + " XP, +" + quest.coins + " coins" + (quest.gems > 0 ? ", +" + quest.gems + " gem" : "") + (quest.loot ? ", loot drop" : ""), 13, MUTED, Typeface.BOLD));
        addSpace(card, 10);
        Button claim = button(claimed ? "Claimed" : ready ? "Claim quest" : "Keep going", ready && !claimed ? primaryColor() : Color.rgb(224, 229, 224), ready && !claimed ? readableTextColor(primaryColor()) : MUTED);
        claim.setEnabled(ready && !claimed);
        claim.setOnClickListener(v -> claimQuest(quest));
        card.addView(claim, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        return card;
    }

    private void addTreasurePanel(LinearLayout content) {
        addSectionHeader(content, "Treasure", "Gems open loot chests. Loot is symbolic, but the dopamine is very real.");
        LinearLayout treasure = card();
        treasure.setBackground(round(Color.rgb(248, 244, 255), 8, LILAC, 2));
        treasure.addView(text("Adventure chest", 21, INK, Typeface.BOLD));
        treasure.addView(text("Costs 3 gems. Drops coins plus common, rare, or epic life loot.", 15, MUTED, Typeface.NORMAL));
        addSpace(treasure, 12);
        LinearLayout row = horizontal();
        treasure.addView(row);
        row.addView(smallStat(state.gems + "", "gems"));
        row.addView(smallStat(state.chestsOpened + "", "chests"));
        row.addView(smallStat(state.inventory.size() + "", "items"));
        addSpace(treasure, 12);
        Button open = button(state.gems >= 3 ? "Open chest" : "Need " + (3 - state.gems) + " gems", state.gems >= 3 ? LILAC : Color.rgb(224, 229, 224), state.gems >= 3 ? readableTextColor(LILAC) : MUTED);
        open.setEnabled(state.gems >= 3);
        open.setOnClickListener(v -> openAdventureChest());
        treasure.addView(open, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        content.addView(treasure);
    }

    private void addCraftingPanel(LinearLayout content) {
        addSectionHeader(content, "Crafting bench", "Turn progress currencies into tiny game items that reinforce real life.");
        addCraftRecipe(content, 0, "Focus potion", "2 gems + 40 coins -> +35 XP and a focus inventory item.", 2, 40, primaryColor());
        addSpace(content, 10);
        addCraftRecipe(content, 1, "Rest pass", "1 gem + 25 coins -> creates a guilt-free recovery reward.", 1, 25, SKY);
        addSpace(content, 10);
        addCraftRecipe(content, 2, "Legend key", "5 gems + 80 coins -> opens a premium loot roll.", 5, 80, LILAC);
    }

    private void addCraftRecipe(LinearLayout content, int recipe, String title, String description, int gemCost, int coinCost, int color) {
        LinearLayout card = card();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        boolean craftable = state.gems >= gemCost && state.coins >= coinCost;
        card.setBackground(round(craftable ? primarySoft() : Color.WHITE, 8, craftable ? color : LINE, craftable ? 2 : 1));

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(top);
        LinearLayout copy = vertical();
        top.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        copy.addView(text(title, 18, INK, Typeface.BOLD));
        copy.addView(text(description, 14, MUTED, Typeface.NORMAL));
        top.addView(pill(gemCost + " gems", color, readableTextColor(color)));

        addSpace(card, 10);
        Button craft = button(craftable ? "Craft" : "Need " + Math.max(0, gemCost - state.gems) + " gems, " + Math.max(0, coinCost - state.coins) + " coins", craftable ? color : Color.rgb(224, 229, 224), craftable ? readableTextColor(color) : MUTED);
        craft.setEnabled(craftable);
        craft.setOnClickListener(v -> craftRecipe(recipe));
        card.addView(craft, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        content.addView(card);
    }

    private void addDataVaultPanel(LinearLayout content) {
        addSectionHeader(content, "Data vault", "Save a portable backup file or restore one later. No account required.");
        LinearLayout vault = card();
        vault.addView(text("Your life-game data stays yours", 20, INK, Typeface.BOLD));
        vault.addView(text("Backups include habits, moods, rewards, quests, timer state, colors, coins, gems, loot, security settings, and achievements progress.", 15, MUTED, Typeface.NORMAL));
        addSpace(vault, 12);
        LinearLayout fileActions = horizontal();
        vault.addView(fileActions);
        Button saveFile = button("Save file", primaryColor(), readableTextColor(primaryColor()));
        saveFile.setOnClickListener(v -> saveBackupFile());
        fileActions.addView(saveFile, new LinearLayout.LayoutParams(0, dp(48), 1f));
        addGap(fileActions, 10);
        Button importFile = button("Import file", accentColor(), readableTextColor(accentColor()));
        importFile.setOnClickListener(v -> openBackupFile());
        fileActions.addView(importFile, new LinearLayout.LayoutParams(0, dp(48), 1f));
        addSpace(vault, 10);
        LinearLayout clipboardActions = horizontal();
        vault.addView(clipboardActions);
        Button copy = button("Copy JSON", Color.rgb(233, 238, 234), INK);
        copy.setOnClickListener(v -> copyBackup());
        clipboardActions.addView(copy, new LinearLayout.LayoutParams(0, dp(48), 1f));
        addGap(clipboardActions, 10);
        Button paste = button("Paste restore", Color.rgb(233, 238, 234), INK);
        paste.setOnClickListener(v -> showRestoreDialog());
        clipboardActions.addView(paste, new LinearLayout.LayoutParams(0, dp(48), 1f));
        content.addView(vault);
    }

    private void addSecurityPanel(LinearLayout content) {
        addSectionHeader(content, "Security", "Create a local password for this device.");
        LinearLayout panel = card();
        panel.setBackground(round(securityEnabled() ? primarySoft() : Color.WHITE, 8, securityEnabled() ? primaryColor() : LINE, securityEnabled() ? 2 : 1));
        panel.addView(text("Security password", 21, INK, Typeface.BOLD));
        panel.addView(text(securityEnabled() ? "Enabled. MyLifePal asks for this password on launch." : "Off. Protect your local habit data with a password.", 15, MUTED, Typeface.NORMAL));
        addSpace(panel, 12);

        Button set = button(securityEnabled() ? "Change password" : "Create password", primaryColor(), readableTextColor(primaryColor()));
        set.setOnClickListener(v -> showSetPasswordDialog());
        panel.addView(set, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));

        if (securityEnabled()) {
            addSpace(panel, 10);
            LinearLayout row = horizontal();
            panel.addView(row);
            Button lock = button("Lock now", Color.rgb(233, 238, 234), INK);
            lock.setOnClickListener(v -> {
                appUnlocked = false;
                renderLockedScreen();
                showUnlockDialog();
            });
            row.addView(lock, new LinearLayout.LayoutParams(0, dp(48), 1f));
            addGap(row, 10);
            Button disable = button("Disable", Color.rgb(255, 235, 231), CORAL);
            disable.setOnClickListener(v -> showDisablePasswordDialog());
            row.addView(disable, new LinearLayout.LayoutParams(0, dp(48), 1f));
        }
        content.addView(panel);
    }

    private void showSetPasswordDialog() {
        LinearLayout form = vertical();
        form.setPadding(dp(20), dp(12), dp(20), dp(4));
        EditText current = input("Current password", "");
        EditText next = input("New password", "");
        EditText confirm = input("Confirm password", "");
        current.setSingleLine(true);
        next.setSingleLine(true);
        confirm.setSingleLine(true);
        current.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        next.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        confirm.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        form.addView(text("Passwords are stored as salted hashes. Plain text is never saved.", 14, MUTED, Typeface.NORMAL));
        if (securityEnabled()) {
            form.addView(label("Current password"));
            form.addView(current);
        }
        form.addView(label("New password"));
        form.addView(next);
        form.addView(label("Confirm password"));
        form.addView(confirm);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(securityEnabled() ? "Change password" : "Create password")
                .setView(form)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setTextColor(primaryTextColor());
            positive.setOnClickListener(v -> {
                if (securityEnabled() && !verifySecurityPassword(current.getText().toString())) {
                    current.setError("Password does not match");
                    current.setText("");
                    return;
                }
                String nextPassword = next.getText().toString().trim();
                if (nextPassword.length() < 4) {
                    next.setError("Use at least 4 characters");
                    return;
                }
                if (!next.getText().toString().equals(confirm.getText().toString())) {
                    confirm.setError("Passwords do not match");
                    confirm.setText("");
                    return;
                }
                setSecurityPassword(nextPassword);
                appUnlocked = true;
                saveState();
                dialog.dismiss();
                Toast.makeText(this, "Security password saved", Toast.LENGTH_LONG).show();
                render();
            });
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negative != null) {
                negative.setTextColor(MUTED);
            }
        });
        dialog.show();
    }

    private void showDisablePasswordDialog() {
        EditText password = input("Current password", "");
        password.setSingleLine(true);
        password.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Disable password")
                .setView(password)
                .setPositiveButton("Disable", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setTextColor(CORAL);
            positive.setOnClickListener(v -> {
                if (!verifySecurityPassword(password.getText().toString())) {
                    password.setError("Password does not match");
                    password.setText("");
                    return;
                }
                state.securityEnabled = false;
                state.passwordSalt = "";
                state.passwordHash = "";
                appUnlocked = true;
                saveState();
                dialog.dismiss();
                Toast.makeText(this, "Security password disabled", Toast.LENGTH_LONG).show();
                render();
            });
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negative != null) {
                negative.setTextColor(MUTED);
            }
        });
        dialog.show();
    }

    private void addAppearancePanel(LinearLayout content) {
        addSectionHeader(content, "Appearance", "Pick a palette that feels personal while keeping the app readable.");
        LinearLayout panel = card();
        panel.addView(text("Color theme", 21, INK, Typeface.BOLD));
        panel.addView(detailLine("Current", themeDisplayName()));
        addSpace(panel, 12);

        LinearLayout swatches = horizontal();
        swatches.setGravity(Gravity.CENTER_VERTICAL);
        panel.addView(swatches);
        swatches.addView(colorSwatch("Primary", primaryColor()), new LinearLayout.LayoutParams(0, dp(48), 1f));
        addGap(swatches, 8);
        swatches.addView(colorSwatch("Accent", accentColor()), new LinearLayout.LayoutParams(0, dp(48), 1f));
        addGap(swatches, 8);
        swatches.addView(colorSwatch("Base", backgroundColor()), new LinearLayout.LayoutParams(0, dp(48), 1f));

        addSpace(panel, 14);
        panel.addView(text("Presets", 15, MUTED, Typeface.BOLD));
        addSpace(panel, 8);
        addThemePresetButtons(panel);

        addSpace(panel, 12);
        Button custom = button("Custom colors", primaryColor(), readableTextColor(primaryColor()));
        custom.setOnClickListener(v -> showThemeDialog());
        panel.addView(custom, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)));
        content.addView(panel);
    }

    private TextView colorSwatch(String label, int color) {
        TextView swatch = pill(label, color, readableTextColor(color));
        swatch.setContentDescription(label + " color " + formatHexColor(color));
        return swatch;
    }

    private void addThemePresetButtons(LinearLayout panel) {
        LinearLayout row = null;
        for (int i = 0; i < THEME_PRESETS.length; i++) {
            if (i % 2 == 0) {
                if (i > 0) {
                    addSpace(panel, 8);
                }
                row = horizontal();
                panel.addView(row);
            } else if (row != null) {
                addGap(row, 8);
            }
            ThemePreset preset = THEME_PRESETS[i];
            Button presetButton = button(preset.name, preset.primary, readableTextColor(preset.primary));
            presetButton.setOnClickListener(v -> applyThemePreset(preset));
            if (row != null) {
                row.addView(presetButton, new LinearLayout.LayoutParams(0, dp(48), 1f));
            }
        }
    }

    private void showThemeDialog() {
        LinearLayout form = vertical();
        form.setPadding(dp(20), dp(12), dp(20), dp(4));

        Spinner palette = spinner(THEME_CHOICES);
        EditText primary = input("Primary color #RRGGBB", formatHexColor(primaryColor()));
        EditText accent = input("Accent color #RRGGBB", formatHexColor(accentColor()));
        EditText background = input("Light background #RRGGBB", formatHexColor(backgroundColor()));
        primary.setSingleLine(true);
        accent.setSingleLine(true);
        background.setSingleLine(true);
        primary.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        accent.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);
        background.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS);

        form.addView(label("Palette"));
        form.addView(palette);
        form.addView(label("Primary"));
        form.addView(primary);
        form.addView(label("Accent"));
        form.addView(accent);
        form.addView(label("Background"));
        form.addView(background);
        palette.setSelection(themeChoiceIndex());
        palette.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                if (position >= 0 && position < THEME_PRESETS.length) {
                    ThemePreset preset = THEME_PRESETS[position];
                    primary.setText(formatHexColor(preset.primary));
                    accent.setText(formatHexColor(preset.accent));
                    background.setText(formatHexColor(preset.background));
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Customize colors")
                .setView(form)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .setNeutralButton("Reset", null)
                .create();
        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setTextColor(primaryTextColor());
            positive.setOnClickListener(v -> {
                Integer primaryValue = parseThemeColor(primary.getText().toString());
                Integer accentValue = parseThemeColor(accent.getText().toString());
                Integer backgroundValue = parseThemeColor(background.getText().toString());
                if (primaryValue == null) {
                    primary.setError("Use #RRGGBB");
                    return;
                }
                if (accentValue == null) {
                    accent.setError("Use #RRGGBB");
                    return;
                }
                if (backgroundValue == null) {
                    background.setError("Use #RRGGBB");
                    return;
                }
                if (relativeLuminance(backgroundValue) < 0.72d) {
                    background.setError("Use a light background for readability");
                    return;
                }
                state.themePrimary = primaryValue;
                state.themeAccent = accentValue;
                state.themeBackground = backgroundValue;
                int choice = palette.getSelectedItemPosition();
                state.themeName = matchingPresetName(primaryValue, accentValue, backgroundValue, choice);
                saveState();
                dialog.dismiss();
                Toast.makeText(this, "Colors updated", Toast.LENGTH_LONG).show();
                render();
            });
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negative != null) {
                negative.setTextColor(MUTED);
            }
            Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (neutral != null) {
                neutral.setTextColor(CORAL);
                neutral.setOnClickListener(v -> {
                    applyThemePreset(THEME_PRESETS[0]);
                    dialog.dismiss();
                });
            }
        });
        dialog.show();
    }

    private void applyThemePreset(ThemePreset preset) {
        state.themeName = preset.name;
        state.themePrimary = preset.primary;
        state.themeAccent = preset.accent;
        state.themeBackground = preset.background;
        saveState();
        Toast.makeText(this, preset.name + " colors applied", Toast.LENGTH_LONG).show();
        render();
    }

    private void renderProgress(LinearLayout content) {
        addSectionHeader(content, "Progress", "Skills rise from repeated votes, not perfect days.");

        LinearLayout level = card();
        level.addView(text("Level " + getLevel(), 30, INK, Typeface.BOLD));
        level.addView(text(xpIntoLevel() + " / " + xpPerLevel() + " XP to next level", 15, MUTED, Typeface.NORMAL));
        addSpace(level, 10);
        level.addView(progressBar(xpIntoLevel(), xpPerLevel(), primaryColor()), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(10)
        ));
        addSpace(level, 14);
        level.addView(text(totalCompletions() + " identity votes", 16, INK, Typeface.BOLD));
        level.addView(text(state.chestsOpened + " focus chests opened", 15, MUTED, Typeface.NORMAL));
        level.addView(text(state.tomatoFocusSessions + " focus tomatoes completed", 15, MUTED, Typeface.NORMAL));
        level.addView(text(state.moodEntries.size() + " emotion check-ins", 15, MUTED, Typeface.NORMAL));
        content.addView(level);
        addSpace(content, 14);

        addAppearancePanel(content);
        addSpace(content, 14);

        addActivityReports(content);
        addSpace(content, 14);

        addSectionHeader(content, "Attributes", "Each habit feeds one real-life skill.");
        for (String attribute : ATTRIBUTES) {
            int xp = state.attributeXp.containsKey(attribute) ? state.attributeXp.get(attribute) : 0;
            LinearLayout row = card();
            row.setPadding(dp(16), dp(14), dp(16), dp(14));
            LinearLayout top = horizontal();
            top.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(top);
            top.addView(text(attribute, 18, INK, Typeface.BOLD), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            top.addView(text(xp + " XP", 14, MUTED, Typeface.BOLD));
            addSpace(row, 10);
            row.addView(progressBar(xp % 100, 100, attributeColor(attribute)), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(8)
            ));
            content.addView(row);
            addSpace(content, 10);
        }

        addSpace(content, 4);
        addSectionHeader(content, "Achievements", "Milestones unlock naturally as the system gets used.");
        for (Achievement achievement : achievements()) {
            content.addView(achievementRow(achievement));
            addSpace(content, 10);
        }
    }

    private View habitCard(Habit habit, boolean todayMode) {
        LinearLayout card = card();
        if (isCompletedToday(habit)) {
            card.setBackground(round(primarySoft(), 8, tint(primaryColor(), 0.58f), 1));
        }

        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(top);

        LinearLayout main = vertical();
        top.addView(main, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        main.addView(text(habit.icon + " " + habit.name, 21, INK, Typeface.BOLD));
        main.addView(text(habit.identity, 14, MUTED, Typeface.NORMAL));

        TextView attribute = pill(habit.attribute, attributeColor(habit.attribute), readableTextColor(attributeColor(habit.attribute)));
        top.addView(attribute);

        addSpace(card, 12);
        card.addView(detailLine("Cue", habit.cue));
        card.addView(detailLine("Tiny action", habit.tinyAction));
        card.addView(detailLine("Reward", habit.reward));
        card.addView(detailLine("Reminder", reminderLabel(habit)));
        card.addView(detailLine("Tracking", trackingSummary(habit)));
        if (!"Completion".equals(habit.trackingType)) {
            addSpace(card, 8);
            int current = trackingProgress(habit);
            int target = Math.max(1, habit.targetValue);
            card.addView(progressBar(Math.min(current, target), target, trackingProgressColor(habit)), new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(8)
            ));
        }

        addSpace(card, 12);
        LinearLayout stats = horizontal();
        stats.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(stats);
        stats.addView(smallStat(habit.streak + "d", "streak"));
        stats.addView(smallStat(habit.bestStreak + "d", "best"));
        stats.addView(smallStat(habit.completions + "", "votes"));
        stats.addView(smallStat("+" + habit.xp, "XP"));

        addSpace(card, 12);
        LinearLayout actions = horizontal();
        card.addView(actions);
        if (todayMode) {
            if ("Quantity".equals(habit.trackingType)) {
                Button minus = button("-1", Color.rgb(233, 238, 234), INK);
                minus.setOnClickListener(v -> updateQuantity(habit, -1));
                actions.addView(minus, new LinearLayout.LayoutParams(0, dp(48), 1f));
                addGap(actions, 8);
                Button plus = button("+1", trackingProgressColor(habit), readableTextColor(trackingProgressColor(habit)));
                plus.setOnClickListener(v -> updateQuantity(habit, 1));
                actions.addView(plus, new LinearLayout.LayoutParams(0, dp(48), 1f));
                addGap(actions, 8);
                addTrackingClaimOrEdit(actions, habit);
            } else if ("Time".equals(habit.trackingType)) {
                int timerColor = habit.trackerRunning ? CORAL : primaryColor();
                Button timer = button(habit.trackerRunning ? "Stop" : "Start", timerColor, readableTextColor(timerColor));
                timer.setOnClickListener(v -> toggleActivityTimer(habit));
                actions.addView(timer, new LinearLayout.LayoutParams(0, dp(48), 1f));
                addGap(actions, 8);
                Button addTime = button("+5m", SKY, readableTextColor(SKY));
                addTime.setOnClickListener(v -> addTrackedTime(habit, 5 * 60));
                actions.addView(addTime, new LinearLayout.LayoutParams(0, dp(48), 1f));
                addGap(actions, 8);
                addTrackingClaimOrEdit(actions, habit);
            } else {
                Button complete = button(isCompletedToday(habit) ? "Done today" : "Complete", isCompletedToday(habit) ? Color.rgb(207, 217, 210) : primaryColor(), isCompletedToday(habit) ? MUTED : readableTextColor(primaryColor()));
                complete.setEnabled(!isCompletedToday(habit));
                complete.setOnClickListener(v -> completeHabit(habit));
                actions.addView(complete, new LinearLayout.LayoutParams(0, dp(48), 1f));
                addGap(actions, 10);
                Button edit = button("Edit", Color.rgb(233, 238, 234), INK);
                edit.setOnClickListener(v -> showHabitDialog(habit));
                actions.addView(edit, new LinearLayout.LayoutParams(0, dp(48), 1f));
            }
        } else {
            Button edit = button("Edit habit", primaryColor(), readableTextColor(primaryColor()));
            edit.setOnClickListener(v -> showHabitDialog(habit));
            actions.addView(edit, new LinearLayout.LayoutParams(0, dp(48), 1f));
            addGap(actions, 10);
            Button start = button("Start small", accentColor(), readableTextColor(accentColor()));
            start.setOnClickListener(v -> Toast.makeText(this, habit.tinyAction, Toast.LENGTH_LONG).show());
            actions.addView(start, new LinearLayout.LayoutParams(0, dp(48), 1f));
        }
        return card;
    }

    private void addTrackingClaimOrEdit(LinearLayout actions, Habit habit) {
        Button action;
        if ("Limit".equals(habit.goalMode)) {
            boolean safe = trackingProgress(habit) <= Math.max(1, habit.targetValue);
            action = button(isCompletedToday(habit) ? "Safe" : safe ? "Safe day" : "Limit hit", safe && !isCompletedToday(habit) ? primaryColor() : Color.rgb(224, 229, 224), safe && !isCompletedToday(habit) ? readableTextColor(primaryColor()) : MUTED);
            action.setEnabled(safe && !isCompletedToday(habit));
            action.setOnClickListener(v -> completeHabit(habit));
        } else {
            action = button(isCompletedToday(habit) ? "Done" : "Edit", Color.rgb(233, 238, 234), INK);
            action.setOnClickListener(v -> showHabitDialog(habit));
        }
        actions.addView(action, new LinearLayout.LayoutParams(0, dp(48), 1f));
    }

    private View rewardCard(Reward reward) {
        LinearLayout card = card();
        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        card.addView(top);
        LinearLayout copy = vertical();
        top.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        copy.addView(text(reward.title, 20, INK, Typeface.BOLD));
        copy.addView(text(reward.claimedCount + " claimed", 14, MUTED, Typeface.NORMAL));
        top.addView(pill(reward.cost + " coins", accentColor(), readableTextColor(accentColor())));

        addSpace(card, 12);
        LinearLayout actions = horizontal();
        card.addView(actions);
        Button claim = button(state.coins >= reward.cost ? "Claim reward" : "Need " + (reward.cost - state.coins), state.coins >= reward.cost ? primaryColor() : Color.rgb(207, 217, 210), state.coins >= reward.cost ? readableTextColor(primaryColor()) : MUTED);
        claim.setEnabled(state.coins >= reward.cost);
        claim.setOnClickListener(v -> claimReward(reward));
        actions.addView(claim, new LinearLayout.LayoutParams(0, dp(48), 1f));
        addGap(actions, 10);
        Button edit = button("Edit", Color.rgb(233, 238, 234), INK);
        edit.setOnClickListener(v -> showRewardDialog(reward));
        actions.addView(edit, new LinearLayout.LayoutParams(0, dp(48), 1f));
        return card;
    }

    private View achievementRow(Achievement achievement) {
        LinearLayout row = card();
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackground(round(achievement.unlocked ? primarySoft() : Color.WHITE, 8, achievement.unlocked ? tint(primaryColor(), 0.58f) : LINE, 1));
        LinearLayout top = horizontal();
        top.setGravity(Gravity.CENTER_VERTICAL);
        row.addView(top);
        LinearLayout copy = vertical();
        top.addView(copy, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        copy.addView(text(achievement.title, 18, INK, Typeface.BOLD));
        copy.addView(text(achievement.description, 14, MUTED, Typeface.NORMAL));
        top.addView(pill(achievement.unlocked ? "Unlocked" : "Locked", achievement.unlocked ? primaryColor() : Color.rgb(224, 229, 224), achievement.unlocked ? readableTextColor(primaryColor()) : MUTED));
        return row;
    }

    private void addActivityReports(LinearLayout content) {
        addSectionHeader(content, "Activity reports", "Timecap-style tracking across completions, counters, timers, goals, and limits.");
        LinearLayout report = card();
        report.addView(text("Today overview", 21, INK, Typeface.BOLD));
        addSpace(report, 10);
        LinearLayout stats = horizontal();
        stats.setGravity(Gravity.CENTER_VERTICAL);
        report.addView(stats);
        stats.addView(smallStat(completionHabitCount() + "", "complete"));
        stats.addView(smallStat(quantityHabitCount() + "", "counters"));
        stats.addView(smallStat(timeHabitCount() + "", "timers"));
        stats.addView(smallStat(limitExceededCount() + "", "limits hit"));
        addSpace(report, 12);
        report.addView(detailLine("Success", successPercent() + "% of tracked activities on target"));
        report.addView(progressBar(successPercent(), 100, successPercent() >= 70 ? primaryColor() : accentColor()), new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(8)
        ));
        addSpace(report, 10);
        report.addView(detailLine("Tracked time", formatDuration(totalTrackedSecondsToday())));
        report.addView(detailLine("Tracked quantity", totalTrackedQuantityToday() + " total counts"));
        content.addView(report);
    }

    private void showHabitDialog(Habit habit) {
        boolean editing = habit != null;
        LinearLayout form = vertical();
        form.setPadding(dp(20), dp(12), dp(20), dp(4));

        EditText name = input("Habit name", editing ? habit.name : "");
        EditText identity = input("Identity vote, e.g. I am a healthy person", editing ? habit.identity : "");
        EditText cue = input("Cue, e.g. After I make coffee", editing ? habit.cue : "");
        EditText tinyAction = input("Tiny action, e.g. read one page", editing ? habit.tinyAction : "");
        EditText reward = input("Reward, e.g. check off and sip tea", editing ? habit.reward : "");
        EditText icon = input("Icon or emoji", editing ? habit.icon : "*");

        form.addView(label("Name"));
        form.addView(name);
        form.addView(label("Icon"));
        form.addView(icon);
        form.addView(label("Identity"));
        form.addView(identity);
        form.addView(label("Cue"));
        form.addView(cue);
        form.addView(label("Tiny action"));
        form.addView(tinyAction);
        form.addView(label("Reward"));
        form.addView(reward);

        form.addView(label("Attribute"));
        Spinner attribute = spinner(ATTRIBUTES);
        attribute.setSelection(indexOf(ATTRIBUTES, editing ? habit.attribute : "Mind"));
        form.addView(attribute, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        form.addView(label("Effort"));
        String[] efforts = {"Tiny: 8 XP, 5 coins", "Steady: 14 XP, 9 coins", "Stretch: 22 XP, 14 coins"};
        Spinner effort = spinner(efforts);
        if (editing) {
            effort.setSelection(habit.xp >= 22 ? 2 : habit.xp >= 14 ? 1 : 0);
        }
        form.addView(effort, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        form.addView(label("Tracking type"));
        Spinner trackingType = spinner(TRACKING_TYPES);
        trackingType.setSelection(indexOf(TRACKING_TYPES, editing ? habit.trackingType : "Completion"));
        form.addView(trackingType, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        form.addView(label("Goal or limit"));
        Spinner goalMode = spinner(GOAL_MODES);
        goalMode.setSelection(indexOf(GOAL_MODES, editing ? habit.goalMode : "Goal"));
        form.addView(goalMode, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        form.addView(label("Target"));
        EditText targetValue = input("1 for completion, count, or minutes", editing ? String.valueOf(habit.targetValue) : "1");
        targetValue.setInputType(InputType.TYPE_CLASS_NUMBER);
        form.addView(targetValue);

        form.addView(label("Unit"));
        EditText unit = input("pages, glasses, cigarettes, minutes", editing ? habit.unit : "x");
        form.addView(unit);

        form.addView(label("Repeat period"));
        Spinner period = spinner(PERIODS);
        period.setSelection(indexOf(PERIODS, editing ? habit.period : "Daily"));
        form.addView(period, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        form.addView(label("Reminder"));
        CheckBox reminderEnabled = new CheckBox(this);
        reminderEnabled.setText("Daily reminder");
        reminderEnabled.setTextColor(INK);
        reminderEnabled.setTextSize(15);
        reminderEnabled.setChecked(editing && habit.reminderEnabled);
        form.addView(reminderEnabled);
        Spinner reminderTime = spinner(REMINDER_TIMES);
        reminderTime.setSelection(indexOf(REMINDER_TIMES, editing ? habit.reminderTime : "09:00"));
        form.addView(reminderTime, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));

        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(form);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(editing ? "Edit habit" : "New habit")
                .setView(scrollView)
                .setPositiveButton(editing ? "Save" : "Create", null)
                .setNegativeButton("Cancel", null)
                .setNeutralButton(editing ? "Delete" : null, null)
                .create();

        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setTextColor(primaryTextColor());
            positive.setOnClickListener(v -> {
                String habitName = name.getText().toString().trim();
                if (habitName.isEmpty()) {
                    name.setError("Name required");
                    return;
                }
                Habit target = editing ? habit : new Habit();
                if (!editing) {
                    target.id = UUID.randomUUID().toString();
                    state.habits.add(target);
                }
                target.name = habitName;
                target.icon = fallback(icon.getText().toString(), "*");
                target.identity = fallback(identity.getText().toString(), "I am someone who keeps promises to myself.");
                target.cue = fallback(cue.getText().toString(), "After an existing routine");
                target.tinyAction = fallback(tinyAction.getText().toString(), "Do the 2-minute version");
                target.reward = fallback(reward.getText().toString(), "Pause and enjoy the win");
                target.attribute = attribute.getSelectedItem().toString();
                int effortIndex = effort.getSelectedItemPosition();
                target.xp = effortIndex == 2 ? 22 : effortIndex == 1 ? 14 : 8;
                target.coins = effortIndex == 2 ? 14 : effortIndex == 1 ? 9 : 5;
                target.trackingType = trackingType.getSelectedItem().toString();
                target.goalMode = goalMode.getSelectedItem().toString();
                target.targetValue = parsePositiveInt(targetValue.getText().toString(), "Time".equals(target.trackingType) ? 25 : 1);
                target.unit = fallback(unit.getText().toString(), "Time".equals(target.trackingType) ? "minutes" : "x");
                target.period = period.getSelectedItem().toString();
                target.reminderEnabled = reminderEnabled.isChecked();
                target.reminderTime = reminderTime.getSelectedItem().toString();
                saveState();
                if (target.reminderEnabled) {
                    requestNotificationPermissionIfNeeded();
                }
                dialog.dismiss();
                render();
            });

            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negative != null) {
                negative.setTextColor(MUTED);
            }
            Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (neutral != null) {
                neutral.setTextColor(CORAL);
                neutral.setOnClickListener(v -> {
                    confirmDeleteHabit(habit, dialog);
                });
            }
        });
        dialog.show();
    }

    private void confirmDeleteHabit(Habit habit, AlertDialog parentDialog) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Delete habit?")
                .setMessage("This removes \"" + habit.name + "\" and its current tracking progress. Your past backup files will still contain older exports.")
                .setPositiveButton("Delete", (d, which) -> {
                    ReminderScheduler.cancelHabit(this, habit.id);
                    state.habits.remove(habit);
                    saveState();
                    parentDialog.dismiss();
                    render();
                })
                .setNegativeButton("Keep", null)
                .create();
        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positive != null) {
                positive.setTextColor(CORAL);
            }
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negative != null) {
                negative.setTextColor(primaryTextColor());
            }
        });
        dialog.show();
    }

    private void showRewardDialog(Reward reward) {
        boolean editing = reward != null;
        LinearLayout form = vertical();
        form.setPadding(dp(20), dp(12), dp(20), dp(4));
        EditText title = input("Reward title", editing ? reward.title : "");
        EditText cost = input("Coin cost", editing ? String.valueOf(reward.cost) : "40");
        cost.setInputType(InputType.TYPE_CLASS_NUMBER);
        form.addView(label("Reward"));
        form.addView(title);
        form.addView(label("Cost"));
        form.addView(cost);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(editing ? "Edit reward" : "New reward")
                .setView(form)
                .setPositiveButton(editing ? "Save" : "Create", null)
                .setNegativeButton("Cancel", null)
                .setNeutralButton(editing ? "Delete" : null, null)
                .create();
        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setTextColor(primaryTextColor());
            positive.setOnClickListener(v -> {
                String rewardTitle = title.getText().toString().trim();
                if (rewardTitle.isEmpty()) {
                    title.setError("Title required");
                    return;
                }
                int rewardCost;
                try {
                    rewardCost = Math.max(1, Integer.parseInt(cost.getText().toString().trim()));
                } catch (NumberFormatException exception) {
                    cost.setError("Enter a number");
                    return;
                }
                Reward target = editing ? reward : new Reward();
                if (!editing) {
                    target.id = UUID.randomUUID().toString();
                    state.rewards.add(target);
                }
                target.title = rewardTitle;
                target.cost = rewardCost;
                saveState();
                dialog.dismiss();
                render();
            });
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negative != null) {
                negative.setTextColor(MUTED);
            }
            Button neutral = dialog.getButton(AlertDialog.BUTTON_NEUTRAL);
            if (neutral != null) {
                neutral.setTextColor(CORAL);
                neutral.setOnClickListener(v -> {
                    confirmDeleteReward(reward, dialog);
                });
            }
        });
        dialog.show();
    }

    private void confirmDeleteReward(Reward reward, AlertDialog parentDialog) {
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Delete reward?")
                .setMessage("This removes \"" + reward.title + "\" from the reward shop.")
                .setPositiveButton("Delete", (d, which) -> {
                    state.rewards.remove(reward);
                    saveState();
                    parentDialog.dismiss();
                    render();
                })
                .setNegativeButton("Keep", null)
                .create();
        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            if (positive != null) {
                positive.setTextColor(CORAL);
            }
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negative != null) {
                negative.setTextColor(primaryTextColor());
            }
        });
        dialog.show();
    }

    private void showMoodDialog(MoodEntry existing) {
        boolean editing = existing != null;
        LinearLayout form = vertical();
        form.setPadding(dp(20), dp(12), dp(20), dp(4));

        String[] moods = {"Rough", "Low", "Okay", "Good", "Great"};
        String[] levels = {"1", "2", "3", "4", "5"};
        Spinner mood = spinner(moods);
        Spinner energy = spinner(levels);
        Spinner stress = spinner(levels);
        EditText note = input("What affected your mood?", editing ? existing.note : "");

        mood.setSelection(editing ? Math.max(0, Math.min(4, existing.mood - 1)) : 2);
        energy.setSelection(editing ? Math.max(0, Math.min(4, existing.energy - 1)) : 2);
        stress.setSelection(editing ? Math.max(0, Math.min(4, existing.stress - 1)) : 2);

        form.addView(label("Mood"));
        form.addView(mood, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        form.addView(label("Energy"));
        form.addView(energy, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        form.addView(label("Stress"));
        form.addView(stress, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)));
        form.addView(label("Note"));
        form.addView(note);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(editing ? "Edit mood" : "Mood check-in")
                .setView(form)
                .setPositiveButton(editing ? "Save" : "Record", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setTextColor(primaryTextColor());
            positive.setOnClickListener(v -> {
                MoodEntry target = editing ? existing : new MoodEntry();
                if (!editing) {
                    target.date = today();
                    state.moodEntries.add(target);
                    rewardMoodCheckIn();
                }
                target.mood = mood.getSelectedItemPosition() + 1;
                target.energy = energy.getSelectedItemPosition() + 1;
                target.stress = stress.getSelectedItemPosition() + 1;
                target.note = note.getText().toString().trim();
                saveState();
                dialog.dismiss();
                render();
            });
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negative != null) {
                negative.setTextColor(MUTED);
            }
        });
        dialog.show();
    }

    private void updateQuantity(Habit habit, int delta) {
        ensureTrackingPeriod(habit);
        habit.quantityValue = Math.max(0, habit.quantityValue + delta);
        if ("Limit".equals(habit.goalMode) && habit.quantityValue > habit.targetValue) {
            Toast.makeText(this, "Limit reached for " + habit.name, Toast.LENGTH_LONG).show();
        }
        checkTrackedMilestone(habit);
        saveState();
        render();
    }

    private void addTrackedTime(Habit habit, int seconds) {
        ensureTrackingPeriod(habit);
        settleActivityTimer(habit);
        habit.secondsValue = Math.max(0, habit.secondsValue + seconds);
        checkTrackedMilestone(habit);
        saveState();
        render();
    }

    private void toggleActivityTimer(Habit habit) {
        ensureTrackingPeriod(habit);
        if (habit.trackerRunning) {
            settleActivityTimer(habit);
            checkTrackedMilestone(habit);
            Toast.makeText(this, "Tracked " + formatDuration(habit.secondsValue) + " for " + habit.name, Toast.LENGTH_LONG).show();
        } else {
            habit.trackerRunning = true;
            habit.trackerStartedAtMillis = System.currentTimeMillis();
            Toast.makeText(this, "Started tracking " + habit.name, Toast.LENGTH_LONG).show();
        }
        saveState();
        render();
    }

    private void checkTrackedMilestone(Habit habit) {
        if ("Limit".equals(habit.goalMode) || isCompletedToday(habit)) {
            return;
        }
        if (trackingProgress(habit) >= Math.max(1, habit.targetValue)) {
            completeHabit(habit);
        }
    }

    private void completeHabit(Habit habit) {
        if (isCompletedToday(habit)) {
            return;
        }
        if ("Time".equals(habit.trackingType)) {
            settleActivityTimer(habit);
        }
        boolean continuesStreak = yesterday().equals(habit.lastCompleted);
        habit.streak = continuesStreak ? habit.streak + 1 : 1;
        habit.bestStreak = Math.max(habit.bestStreak, habit.streak);
        habit.completions++;
        habit.lastCompleted = today();

        int streakBonus = Math.min(12, habit.streak * 2);
        int xpGain = habit.xp + streakBonus;
        state.totalXp += xpGain;
        state.coins += habit.coins;
        int attributeXp = state.attributeXp.containsKey(habit.attribute) ? state.attributeXp.get(habit.attribute) : 0;
        state.attributeXp.put(habit.attribute, attributeXp + xpGain);

        String chestMessage = "";
        if (todayCompletionCount() >= 3 && !today().equals(state.lastChestDate)) {
            chestMessage = openChest();
        }
        String companionMessage = growMonster(Math.max(5, xpGain / 2), "habit completion");

        saveState();
        Toast.makeText(this, "Nice: +" + xpGain + " XP, +" + habit.coins + " coins" + chestMessage + "; " + companionMessage, Toast.LENGTH_LONG).show();
        render();
    }

    private String openChest() {
        String item = rollLoot();
        int bonusCoins = 25 + new Random().nextInt(16);
        state.lastChestDate = today();
        state.chestsOpened++;
        state.coins += bonusCoins;
        state.gems += 1;
        state.inventory.add("Chest: " + item + " (+" + bonusCoins + " coins)");
        return ". Chest opened: " + item + ", +1 gem";
    }

    private void claimReward(Reward reward) {
        if (state.coins < reward.cost) {
            return;
        }
        state.coins -= reward.cost;
        reward.claimedCount++;
        ensureGameDay();
        state.rewardClaimsToday++;
        state.inventory.add("Reward: " + reward.title);
        String companionMessage = growMonster(6, "reward claim");
        saveState();
        Toast.makeText(this, "Reward claimed: " + reward.title + "; " + companionMessage, Toast.LENGTH_LONG).show();
        render();
    }

    private void startTinyAction() {
        Habit next = firstIncompleteHabit();
        if (next == null) {
            Toast.makeText(this, "Today is clear. Enjoy the win.", Toast.LENGTH_LONG).show();
        } else {
            Toast.makeText(this, "Start small: " + next.tinyAction, Toast.LENGTH_LONG).show();
        }
    }

    private void ensureGameDay() {
        ensureTimerDay();
        if (!today().equals(state.rewardLastDate)) {
            state.rewardLastDate = today();
            state.rewardClaimsToday = 0;
        }
    }

    private List<GameQuest> gameQuests() {
        ensureGameDay();
        List<GameQuest> quests = new ArrayList<>();
        quests.add(new GameQuest(
                "first_vote",
                "First identity vote",
                "Complete any habit today.",
                todayCompletionCount(),
                1,
                10,
                12,
                0,
                false
        ));
        quests.add(new GameQuest(
                "triple_win",
                "Triple win",
                "Complete three habits to open the day chest feeling.",
                todayCompletionCount(),
                3,
                20,
                25,
                1,
                true
        ));
        quests.add(new GameQuest(
                "focus_tomato",
                "Deep focus tomato",
                "Finish one Tomato Timer focus session today.",
                state.tomatoSessionsToday,
                1,
                25,
                20,
                1,
                true
        ));
        quests.add(new GameQuest(
                "mood_check",
                "Name the feeling",
                "Record one emotion check-in today.",
                hasMoodToday() ? 1 : 0,
                1,
                12,
                12,
                1,
                false
        ));
        quests.add(new GameQuest(
                "real_reward",
                "Enjoy the game",
                "Claim one real-life reward from the shop today.",
                state.rewardClaimsToday,
                1,
                15,
                10,
                1,
                false
        ));
        return quests;
    }

    private void rewardMoodCheckIn() {
        state.totalXp += 8;
        state.coins += 5;
        int mindXp = state.attributeXp.containsKey("Mind") ? state.attributeXp.get("Mind") : 0;
        state.attributeXp.put("Mind", mindXp + 8);
        Toast.makeText(this, "Mood check-in recorded: +8 XP, +5 coins; " + growMonster(6, "mood check-in"), Toast.LENGTH_LONG).show();
    }

    private boolean hasMoodToday() {
        return moodForDate(today()) != null;
    }

    private MoodEntry moodForDate(String date) {
        for (MoodEntry entry : state.moodEntries) {
            if (date.equals(entry.date)) {
                return entry;
            }
        }
        return null;
    }

    private List<MoodEntry> recentMoodEntries(int limit) {
        List<MoodEntry> recent = new ArrayList<>();
        for (int i = state.moodEntries.size() - 1; i >= 0 && recent.size() < limit; i--) {
            recent.add(state.moodEntries.get(i));
        }
        return recent;
    }

    private String moodLabel(int mood) {
        if (mood <= 1) {
            return "Rough";
        }
        if (mood == 2) {
            return "Low";
        }
        if (mood == 3) {
            return "Okay";
        }
        if (mood == 4) {
            return "Good";
        }
        return "Great";
    }

    private int moodColor(int mood) {
        if (mood <= 1) {
            return CORAL;
        }
        if (mood == 2) {
            return Color.rgb(213, 139, 63);
        }
        if (mood == 3) {
            return accentColor();
        }
        if (mood == 4) {
            return SKY;
        }
        return primaryColor();
    }

    private String moodInsight(MoodEntry entry) {
        if (entry.stress >= 4 && entry.energy <= 2) {
            return "Suggested next step: choose the smallest possible task and protect recovery.";
        }
        if (entry.mood >= 4 && entry.energy >= 4) {
            return "Suggested next step: use the momentum, but keep the promise small enough for tomorrow too.";
        }
        if (entry.mood <= 2) {
            return "Suggested next step: count showing up as the win today.";
        }
        return "Suggested next step: pick one tiny action that matches your current energy.";
    }

    private void claimQuest(GameQuest quest) {
        if (isQuestClaimed(quest.id) || quest.progress < quest.goal) {
            return;
        }
        state.claimedQuestKeys.add(questKey(quest.id));
        state.totalXp += quest.xp;
        state.coins += quest.coins;
        state.gems += quest.gems;
        int mindXp = state.attributeXp.containsKey("Mind") ? state.attributeXp.get("Mind") : 0;
        state.attributeXp.put("Mind", mindXp + quest.xp);
        String loot = "";
        if (quest.loot) {
            loot = rollLoot();
            state.inventory.add("Quest: " + loot);
        }
        String companionMessage = growMonster(Math.max(5, quest.xp / 2), "quest claim");
        saveState();
        Toast.makeText(this, "Quest claimed: +" + quest.xp + " XP, +" + quest.coins + " coins" + (loot.isEmpty() ? "" : ", " + loot) + "; " + companionMessage, Toast.LENGTH_LONG).show();
        render();
    }

    private boolean isQuestClaimed(String id) {
        return state.claimedQuestKeys.contains(questKey(id));
    }

    private String questKey(String id) {
        return today() + ":" + id;
    }

    private int claimedQuestsToday() {
        int count = 0;
        String prefix = today() + ":";
        for (String key : state.claimedQuestKeys) {
            if (key.startsWith(prefix)) {
                count++;
            }
        }
        return count;
    }

    private void openAdventureChest() {
        if (state.gems < 3) {
            return;
        }
        state.gems -= 3;
        String loot = rollLoot();
        int coins = 35 + new Random().nextInt(31);
        state.coins += coins;
        state.chestsOpened++;
        state.inventory.add("Adventure chest: " + loot + " (+" + coins + " coins)");
        saveState();
        Toast.makeText(this, "Chest opened: " + loot + ", +" + coins + " coins", Toast.LENGTH_LONG).show();
        render();
    }

    private void craftRecipe(int recipe) {
        int gemCost = recipe == 2 ? 5 : recipe == 1 ? 1 : 2;
        int coinCost = recipe == 2 ? 80 : recipe == 1 ? 25 : 40;
        if (state.gems < gemCost || state.coins < coinCost) {
            return;
        }
        state.gems -= gemCost;
        state.coins -= coinCost;

        String message;
        if (recipe == 0) {
            state.totalXp += 35;
            int craftXp = state.attributeXp.containsKey("Craft") ? state.attributeXp.get("Craft") : 0;
            state.attributeXp.put("Craft", craftXp + 35);
            state.inventory.add("Crafted: Focus potion (+35 XP)");
            message = "Crafted Focus potion: +35 XP";
        } else if (recipe == 1) {
            Reward restPass = new Reward();
            restPass.id = UUID.randomUUID().toString();
            restPass.title = "Crafted rest pass";
            restPass.cost = 0;
            state.rewards.add(restPass);
            state.inventory.add("Crafted: Rest pass");
            message = "Crafted Rest pass";
        } else {
            String loot = rollLoot();
            int bonus = 90 + new Random().nextInt(61);
            state.coins += bonus;
            state.inventory.add("Crafted Legend key: " + loot + " (+" + bonus + " coins)");
            message = "Legend key opened: " + loot + ", +" + bonus + " coins";
        }
        saveState();
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        render();
    }

    private void careForMonster(int action) {
        String message;
        if (action == 0) {
            if (state.coins < 15) {
                return;
            }
            state.coins -= 15;
            message = "Fed " + state.monsterName + ": " + growMonster(18, "feeding");
        } else if (action == 1) {
            if (state.gems < 1) {
                return;
            }
            state.gems -= 1;
            message = "Trained " + state.monsterName + ": " + growMonster(32, "training");
        } else {
            if (today().equals(state.monsterLastCareDate)) {
                Toast.makeText(this, state.monsterName + " already bonded today", Toast.LENGTH_LONG).show();
                return;
            }
            state.monsterLastCareDate = today();
            message = "Bonded with " + state.monsterName + ": " + growMonster(12, "daily bonding");
        }
        saveState();
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        render();
    }

    private void showMonsterDialog() {
        LinearLayout form = vertical();
        form.setPadding(dp(20), dp(12), dp(20), dp(4));
        EditText name = input("Companion name", state.monsterName);
        form.addView(label("Name"));
        form.addView(name);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Name companion")
                .setView(form)
                .setPositiveButton("Save", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setTextColor(primaryTextColor());
            positive.setOnClickListener(v -> {
                String value = name.getText().toString().trim();
                if (value.isEmpty()) {
                    name.setError("Name required");
                    return;
                }
                state.monsterName = value;
                saveState();
                dialog.dismiss();
                render();
            });
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negative != null) {
                negative.setTextColor(MUTED);
            }
        });
        dialog.show();
    }

    private String growMonster(int amount, String source) {
        int oldLevel = monsterLevel();
        state.monsterXp += amount;
        state.monsterBond = Math.min(100, state.monsterBond + Math.max(2, amount / 5));
        int newLevel = monsterLevel();
        if (newLevel > oldLevel) {
            String growth = state.monsterName + " reached level " + newLevel + " " + monsterStage();
            state.inventory.add("Companion: " + growth + " after " + source);
            return growth;
        }
        return "+" + amount + " companion XP";
    }

    private int monsterLevel() {
        return (state.monsterXp / monsterXpPerLevel()) + 1;
    }

    private int monsterXpPerLevel() {
        return 90;
    }

    private int monsterXpIntoLevel() {
        return state.monsterXp % monsterXpPerLevel();
    }

    private String monsterStage() {
        int level = monsterLevel();
        if (level >= 8) {
            return "Mythic";
        }
        if (level >= 5) {
            return "Guardian";
        }
        if (level >= 3) {
            return "Bloomling";
        }
        return "Hatchling";
    }

    private String nextMonsterEvolution() {
        int level = monsterLevel();
        if (level < 3) {
            return "Bloomling at level 3";
        }
        if (level < 5) {
            return "Guardian at level 5";
        }
        if (level < 8) {
            return "Mythic at level 8";
        }
        return "Max known form";
    }

    private String monsterMoodLine() {
        if (state.monsterBond >= 80) {
            return "deeply bonded";
        }
        if (state.monsterBond >= 50) {
            return "trusting";
        }
        if (state.monsterBond >= 25) {
            return "curious";
        }
        return "shy";
    }

    private void copyBackup() {
        ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (clipboard == null) {
            Toast.makeText(this, "Clipboard unavailable", Toast.LENGTH_LONG).show();
            return;
        }
        clipboard.setPrimaryClip(ClipData.newPlainText("MyLifePal backup", backupJson().toString()));
        Toast.makeText(this, "Backup copied to clipboard", Toast.LENGTH_LONG).show();
    }

    private void saveBackupFile() {
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/json");
        intent.putExtra(Intent.EXTRA_TITLE, "mylifepal-backup-" + today() + ".json");
        try {
            startActivityForResult(intent, REQUEST_SAVE_BACKUP);
        } catch (Exception exception) {
            Toast.makeText(this, "File picker unavailable. Use Copy JSON instead.", Toast.LENGTH_LONG).show();
        }
    }

    private void openBackupFile() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        try {
            startActivityForResult(intent, REQUEST_OPEN_BACKUP);
        } catch (Exception exception) {
            Toast.makeText(this, "File picker unavailable. Use Paste restore instead.", Toast.LENGTH_LONG).show();
        }
    }

    private void writeBackupToUri(Uri uri) {
        try (OutputStream output = getContentResolver().openOutputStream(uri)) {
            if (output == null) {
                Toast.makeText(this, "Could not open backup file", Toast.LENGTH_LONG).show();
                return;
            }
            output.write(backupJson().toString(2).getBytes(StandardCharsets.UTF_8));
            Toast.makeText(this, "Backup file saved", Toast.LENGTH_LONG).show();
        } catch (IOException | JSONException exception) {
            Toast.makeText(this, "Backup save failed", Toast.LENGTH_LONG).show();
        }
    }

    private void restoreBackupFromUri(Uri uri) {
        try (InputStream input = getContentResolver().openInputStream(uri)) {
            if (input == null) {
                Toast.makeText(this, "Could not open backup file", Toast.LENGTH_LONG).show();
                return;
            }
            restoreBackupString(readAll(input));
            Toast.makeText(this, "Backup restored", Toast.LENGTH_LONG).show();
            render();
        } catch (IOException | JSONException exception) {
            Toast.makeText(this, "Backup restore failed", Toast.LENGTH_LONG).show();
        }
    }

    private String readAll(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toString("UTF-8");
    }

    private JSONObject backupJson() {
        JSONObject backup = new JSONObject();
        try {
            backup.put("schema", "mylifepal.backup");
            backup.put("schemaVersion", 1);
            backup.put("appName", "MyLifePal");
            backup.put("exportedAt", new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Calendar.getInstance().getTime()));
            backup.put("state", state.toJson());
        } catch (JSONException ignored) {
        }
        return backup;
    }

    private void restoreBackupString(String raw) throws JSONException {
        JSONObject parsed = new JSONObject(raw.trim());
        JSONObject stateJson = parsed.optJSONObject("state");
        JSONObject payload = stateJson != null ? stateJson : parsed;
        boolean recognized = "mylifepal.backup".equals(parsed.optString("schema"))
                || payload.has("habits")
                || payload.has("rewards")
                || payload.has("moodEntries")
                || payload.has("totalXp")
                || payload.has("attributeXp");
        if (!recognized) {
            throw new JSONException("Not a MyLifePal backup");
        }
        AppState restored = AppState.fromJson(payload);
        state = restored;
        saveState();
    }

    private void showRestoreDialog() {
        LinearLayout form = vertical();
        form.setPadding(dp(20), dp(12), dp(20), dp(4));
        EditText backup = input("Paste MyLifePal backup JSON", "");
        backup.setMinLines(5);
        backup.setMaxLines(10);
        form.addView(label("Backup JSON"));
        form.addView(backup);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle("Restore backup")
                .setView(form)
                .setPositiveButton("Restore", null)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.setOnShowListener(d -> {
            Button positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE);
            positive.setTextColor(primaryTextColor());
            positive.setOnClickListener(v -> {
                try {
                    restoreBackupString(backup.getText().toString());
                    dialog.dismiss();
                    Toast.makeText(this, "Backup restored", Toast.LENGTH_LONG).show();
                    render();
                } catch (JSONException exception) {
                    backup.setError("Invalid JSON");
                }
            });
            Button negative = dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if (negative != null) {
                negative.setTextColor(MUTED);
            }
        });
        dialog.show();
    }

    private String rollLoot() {
        Random random = new Random();
        int roll = random.nextInt(100);
        if (roll >= 92) {
            return "Epic " + EPIC_LOOT[random.nextInt(EPIC_LOOT.length)];
        }
        if (roll >= 65) {
            return "Rare " + RARE_LOOT[random.nextInt(RARE_LOOT.length)];
        }
        return "Common " + COMMON_LOOT[random.nextInt(COMMON_LOOT.length)];
    }

    private String lifeTitle() {
        if (getLevel() >= 12) {
            return "Legendary Life Architect";
        }
        if (getLevel() >= 8) {
            return "Epic Routine Builder";
        }
        if (getLevel() >= 5) {
            return "Rare Momentum Maker";
        }
        if (totalCompletions() >= 10) {
            return "Steady Adventurer";
        }
        return "New Adventurer";
    }

    private String bestAttributeLine() {
        String best = "Mind";
        int bestXp = -1;
        for (String attribute : ATTRIBUTES) {
            int xp = state.attributeXp.containsKey(attribute) ? state.attributeXp.get(attribute) : 0;
            if (xp > bestXp) {
                best = attribute;
                bestXp = xp;
            }
        }
        return "Strongest skill: " + best + " (" + Math.max(0, bestXp) + " XP)";
    }

    private void startTimer() {
        ensureTimerDay();
        long remaining = timerRemainingMillis();
        if (remaining <= 0) {
            remaining = timerDurationForMode(state.timerMode);
        }
        state.timerDurationMillis = timerDurationForMode(state.timerMode);
        state.timerRemainingMillis = remaining;
        state.timerEndAtMillis = System.currentTimeMillis() + remaining;
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
        state.timerDurationMillis = timerDurationForMode(state.timerMode);
        state.timerRemainingMillis = state.timerDurationMillis;
        state.timerEndAtMillis = 0;
        saveState();
        render();
    }

    private void selectTimerMode(int mode) {
        state.timerMode = mode;
        resetTimer();
    }

    private void tickTimer() {
        if (!state.timerRunning) {
            return;
        }
        if (timerRemainingMillis() <= 0) {
            completeTimerSession();
            return;
        }
        if (selectedTab == 1) {
            render();
        }
    }

    private void completeTimerSession() {
        ensureTimerDay();
        int completedMode = state.timerMode;
        state.timerRunning = false;
        state.timerEndAtMillis = 0;

        if (completedMode == 0) {
            int gain = 18;
            int coins = 12;
            state.totalXp += gain;
            state.coins += coins;
            state.tomatoFocusSessions++;
            state.tomatoSessionsToday++;
            state.tomatoMinutesToday += 25;
            int mindXp = state.attributeXp.containsKey("Mind") ? state.attributeXp.get("Mind") : 0;
            state.attributeXp.put("Mind", mindXp + gain);
            if (state.tomatoFocusSessions % 4 == 0) {
                state.inventory.add("Timer: 4-tomato focus set");
            }
            state.timerMode = state.tomatoFocusSessions % 4 == 0 ? 2 : 1;
            Toast.makeText(this, "Tomato complete: +" + gain + " XP, +" + coins + " coins; " + growMonster(10, "focus tomato"), Toast.LENGTH_LONG).show();
        } else {
            state.tomatoBreakSessions++;
            state.timerMode = 0;
            Toast.makeText(this, "Break complete. Ready for the next tiny action.", Toast.LENGTH_LONG).show();
        }

        state.timerDurationMillis = timerDurationForMode(state.timerMode);
        state.timerRemainingMillis = state.timerDurationMillis;
        saveState();
        render();
    }

    private void ensureTimerDay() {
        if (!today().equals(state.tomatoLastDate)) {
            state.tomatoLastDate = today();
            state.tomatoSessionsToday = 0;
            state.tomatoMinutesToday = 0;
        }
        if (state.timerDurationMillis <= 0) {
            state.timerDurationMillis = timerDurationForMode(state.timerMode);
        }
        if (state.timerRemainingMillis <= 0) {
            state.timerRemainingMillis = state.timerDurationMillis;
        }
    }

    private long timerRemainingMillis() {
        if (state.timerRunning) {
            return Math.max(0, state.timerEndAtMillis - System.currentTimeMillis());
        }
        return Math.max(0, state.timerRemainingMillis);
    }

    private long timerDurationForMode(int mode) {
        if (mode == 1) {
            return 5L * 60L * 1000L;
        }
        if (mode == 2) {
            return 15L * 60L * 1000L;
        }
        return 25L * 60L * 1000L;
    }

    private String timerModeLabel() {
        if (state.timerMode == 1) {
            return "Short break";
        }
        if (state.timerMode == 2) {
            return "Long break";
        }
        return "Focus tomato";
    }

    private int timerModeColor() {
        if (state.timerMode == 0) {
            return primaryColor();
        }
        if (state.timerMode == 1) {
            return SKY;
        }
        return LILAC;
    }

    private String formatTimer(long millis) {
        long totalSeconds = Math.max(0, (millis + 999) / 1000);
        long minutes = totalSeconds / 60;
        long seconds = totalSeconds % 60;
        return String.format(Locale.US, "%02d:%02d", minutes, seconds);
    }

    private String formatDuration(int seconds) {
        int safeSeconds = Math.max(0, seconds);
        int hours = safeSeconds / 3600;
        int minutes = (safeSeconds % 3600) / 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m";
        }
        return minutes + "m";
    }

    private void ensureTrackingPeriod(Habit habit) {
        String key = trackingPeriodKey(habit);
        if (!key.equals(habit.trackingPeriodKey)) {
            habit.trackingPeriodKey = key;
            habit.quantityValue = 0;
            habit.secondsValue = 0;
            habit.trackerRunning = false;
            habit.trackerStartedAtMillis = 0L;
        }
    }

    private String trackingPeriodKey(Habit habit) {
        Calendar calendar = Calendar.getInstance();
        if ("Weekly".equals(habit.period)) {
            return new SimpleDateFormat("yyyy-'W'ww", Locale.US).format(calendar.getTime());
        }
        if ("Monthly".equals(habit.period)) {
            return new SimpleDateFormat("yyyy-MM", Locale.US).format(calendar.getTime());
        }
        if ("Yearly".equals(habit.period)) {
            return new SimpleDateFormat("yyyy", Locale.US).format(calendar.getTime());
        }
        return today();
    }

    private int trackingProgress(Habit habit) {
        ensureTrackingPeriod(habit);
        if ("Time".equals(habit.trackingType)) {
            int seconds = habit.secondsValue;
            if (habit.trackerRunning) {
                seconds += (int) Math.max(0, (System.currentTimeMillis() - habit.trackerStartedAtMillis) / 1000L);
            }
            return Math.max(0, seconds / 60);
        }
        if ("Quantity".equals(habit.trackingType)) {
            return Math.max(0, habit.quantityValue);
        }
        return isCompletedToday(habit) ? 1 : 0;
    }

    private void settleActivityTimer(Habit habit) {
        if (!habit.trackerRunning) {
            return;
        }
        long elapsed = Math.max(0, (System.currentTimeMillis() - habit.trackerStartedAtMillis) / 1000L);
        habit.secondsValue += (int) elapsed;
        habit.trackerRunning = false;
        habit.trackerStartedAtMillis = 0L;
    }

    private String trackingSummary(Habit habit) {
        if ("Completion".equals(habit.trackingType)) {
            return "Completion goal every day";
        }
        String mode = "Limit".equals(habit.goalMode) ? "Limit" : "Goal";
        String value;
        if ("Time".equals(habit.trackingType)) {
            value = formatDuration(trackingProgress(habit) * 60) + " / " + habit.targetValue + " min";
        } else {
            value = trackingProgress(habit) + " / " + habit.targetValue + " " + habit.unit;
        }
        return habit.trackingType + " " + mode.toLowerCase(Locale.US) + " - " + value + " per " + habit.period.toLowerCase(Locale.US);
    }

    private int trackingProgressColor(Habit habit) {
        int progress = trackingProgress(habit);
        int target = Math.max(1, habit.targetValue);
        if ("Limit".equals(habit.goalMode)) {
            return progress > target ? CORAL : SKY;
        }
        return progress >= target ? primaryColor() : accentColor();
    }

    private List<Habit> activeHabits() {
        List<Habit> habits = new ArrayList<>();
        for (Habit habit : state.habits) {
            habits.add(habit);
        }
        return habits;
    }

    private Habit firstIncompleteHabit() {
        for (Habit habit : activeHabits()) {
            if (!isCompletedToday(habit)) {
                return habit;
            }
        }
        return null;
    }

    private boolean isCompletedToday(Habit habit) {
        return today().equals(habit.lastCompleted);
    }

    private int todayCompletionCount() {
        int count = 0;
        for (Habit habit : activeHabits()) {
            if (isCompletedToday(habit)) {
                count++;
            }
        }
        return count;
    }

    private int completionHabitCount() {
        return habitTypeCount("Completion");
    }

    private int quantityHabitCount() {
        return habitTypeCount("Quantity");
    }

    private int timeHabitCount() {
        return habitTypeCount("Time");
    }

    private int habitTypeCount(String type) {
        int count = 0;
        for (Habit habit : activeHabits()) {
            if (type.equals(habit.trackingType)) {
                count++;
            }
        }
        return count;
    }

    private int limitExceededCount() {
        int count = 0;
        for (Habit habit : activeHabits()) {
            if ("Limit".equals(habit.goalMode) && trackingProgress(habit) > Math.max(1, habit.targetValue)) {
                count++;
            }
        }
        return count;
    }

    private int successPercent() {
        int total = activeHabits().size();
        if (total == 0) {
            return 0;
        }
        int success = 0;
        for (Habit habit : activeHabits()) {
            int progress = trackingProgress(habit);
            int target = Math.max(1, habit.targetValue);
            if ("Limit".equals(habit.goalMode)) {
                if (progress <= target) {
                    success++;
                }
            } else if ("Completion".equals(habit.trackingType) ? isCompletedToday(habit) : progress >= target) {
                success++;
            }
        }
        return Math.round(100f * success / total);
    }

    private int totalTrackedSecondsToday() {
        int total = 0;
        for (Habit habit : activeHabits()) {
            if ("Time".equals(habit.trackingType)) {
                ensureTrackingPeriod(habit);
                total += habit.secondsValue;
                if (habit.trackerRunning) {
                    total += (int) Math.max(0, (System.currentTimeMillis() - habit.trackerStartedAtMillis) / 1000L);
                }
            }
        }
        return total;
    }

    private int totalTrackedQuantityToday() {
        int total = 0;
        for (Habit habit : activeHabits()) {
            if ("Quantity".equals(habit.trackingType)) {
                ensureTrackingPeriod(habit);
                total += habit.quantityValue;
            }
        }
        return total;
    }

    private int totalCompletions() {
        int count = 0;
        for (Habit habit : state.habits) {
            count += habit.completions;
        }
        return count;
    }

    private int longestStreak() {
        int best = 0;
        for (Habit habit : state.habits) {
            best = Math.max(best, habit.bestStreak);
        }
        return best;
    }

    private int getLevel() {
        return (state.totalXp / xpPerLevel()) + 1;
    }

    private int xpPerLevel() {
        return 120;
    }

    private int xpIntoLevel() {
        return state.totalXp % xpPerLevel();
    }

    private String getIdentityLine() {
        Habit next = firstIncompleteHabit();
        if (next != null) {
            return next.identity;
        }
        if (state.habits.isEmpty()) {
            return "Build the smallest useful routine you can repeat tomorrow.";
        }
        return "You kept today's promises. That counts.";
    }

    private int coachAction() {
        Habit next = firstIncompleteHabit();
        MoodEntry mood = moodForDate(today());
        if (activeHabits().isEmpty()) {
            return 0;
        }
        if (mood == null && todayCompletionCount() == 0) {
            return 1;
        }
        if (state.timerRunning) {
            return 4;
        }
        if (mood != null && (mood.stress >= 4 || mood.energy <= 2) && next != null) {
            return 2;
        }
        if (next != null) {
            return 3;
        }
        if (state.tomatoSessionsToday == 0) {
            return 4;
        }
        if (!today().equals(state.monsterLastCareDate)) {
            return 5;
        }
        if (readyQuestCount() > 0) {
            return 6;
        }
        return 7;
    }

    private String coachHeadline(int action) {
        switch (action) {
            case 0:
                return "Create the first tiny promise";
            case 1:
                return "Check your emotional weather";
            case 2:
                return "Use the rescue version";
            case 3:
                return "Start the next identity vote";
            case 4:
                return state.timerRunning ? "Return to the focus timer" : "Protect one focus block";
            case 5:
                return "Bond with your companion";
            case 6:
                return "Claim today’s quest rewards";
            default:
                return "Review the win and choose a reward";
        }
    }

    private String coachReason(int action) {
        Habit next = firstIncompleteHabit();
        switch (action) {
            case 0:
                return "The best habit app starts by making the first action easier than avoidance.";
            case 1:
                return "Mood, energy, and stress make the rest of today’s advice more honest.";
            case 2:
                return next == null ? "Keep the promise tiny today." : "Energy or stress is asking for the smallest useful version: " + next.tinyAction + ".";
            case 3:
                return next == null ? "All habit votes are complete." : "Your next cue is ready: " + next.cue + ".";
            case 4:
                return state.timerRunning ? "A running tomato is already creating protected attention." : "One focused tomato turns intention into visible progress.";
            case 5:
                return state.monsterName + " grows best when progress also feels cared for.";
            case 6:
                return readyQuestCount() + " quest reward" + (readyQuestCount() == 1 ? " is" : "s are") + " waiting.";
            default:
                return "You have enough progress today to reinforce the loop with something satisfying.";
        }
    }

    private String coachButtonLabel(int action) {
        switch (action) {
            case 0:
                return "Create habit";
            case 1:
                return "Check in";
            case 2:
                return "Start rescue version";
            case 3:
                return "Start tiny action";
            case 4:
                return state.timerRunning ? "Open timer" : "Start tomato";
            case 5:
                return "Bond now";
            case 6:
                return "Open quests";
            default:
                return "Open rewards";
        }
    }

    private int coachAccent(int action) {
        if (action == 1 || action == 5) {
            return LILAC;
        }
        if (action == 2 || action == 4) {
            return SKY;
        }
        if (action == 6 || action == 7) {
            return accentColor();
        }
        return primaryColor();
    }

    private int coachColor(int action) {
        if (action == 1 || action == 5) {
            return Color.rgb(248, 244, 255);
        }
        if (action == 2 || action == 4) {
            return Color.rgb(238, 247, 252);
        }
        if (action == 6 || action == 7) {
            return Color.rgb(255, 252, 238);
        }
        return Color.rgb(239, 248, 241);
    }

    private void runCoachAction(int action) {
        Habit next = firstIncompleteHabit();
        if (action == 0) {
            showHabitDialog(null);
        } else if (action == 1) {
            showMoodDialog(null);
        } else if (action == 2 || action == 3) {
            if (next == null) {
                Toast.makeText(this, "Today is clear. Enjoy the win.", Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, "Start small: " + next.tinyAction, Toast.LENGTH_LONG).show();
            }
        } else if (action == 4) {
            selectedTab = 1;
            if (!state.timerRunning) {
                startTimer();
            } else {
                render();
            }
        } else if (action == 5) {
            careForMonster(2);
        } else if (action == 6) {
            selectedTab = 4;
            render();
        } else {
            selectedTab = 4;
            render();
        }
    }

    private int todayScore() {
        int active = activeHabits().size();
        int habitScore = active == 0 ? 0 : Math.round(40f * todayCompletionCount() / active);
        int moodScore = hasMoodToday() ? 15 : 0;
        int focusScore = Math.min(15, state.tomatoSessionsToday * 8);
        int questScore = Math.min(15, claimedQuestsToday() * 5);
        int reminderScore = enabledReminderCount() > 0 ? 5 : 0;
        int companionScore = today().equals(state.monsterLastCareDate) ? 10 : 0;
        return Math.min(100, habitScore + moodScore + focusScore + questScore + reminderScore + companionScore);
    }

    private String todayScoreLine() {
        int active = activeHabits().size();
        if (active == 0) {
            return todayScore() + "% - create one tiny habit to start the loop";
        }
        return todayScore() + "% - " + todayCompletionCount() + "/" + active
                + " habits, " + state.tomatoSessionsToday + " tomatoes, " + claimedQuestsToday() + " quests";
    }

    private int readyQuestCount() {
        int count = 0;
        for (GameQuest quest : gameQuests()) {
            if (quest.progress >= quest.goal && !isQuestClaimed(quest.id)) {
                count++;
            }
        }
        return count;
    }

    private List<Achievement> achievements() {
        List<Achievement> achievements = new ArrayList<>();
        achievements.add(new Achievement("First spark", "Complete any habit once.", totalCompletions() >= 1));
        achievements.add(new Achievement("Three-day identity", "Hold a 3-day streak on one habit.", longestStreak() >= 3));
        achievements.add(new Achievement("Ten tiny votes", "Collect 10 total habit completions.", totalCompletions() >= 10));
        achievements.add(new Achievement("Reward redeemed", "Claim anything from the reward shop.", anyRewardClaimed()));
        achievements.add(new Achievement("Chest opener", "Complete 3 habits in one day.", state.chestsOpened >= 1));
        achievements.add(new Achievement("First tomato", "Complete one focus tomato.", state.tomatoFocusSessions >= 1));
        achievements.add(new Achievement("Deep work set", "Complete four focus tomatoes.", state.tomatoFocusSessions >= 4));
        achievements.add(new Achievement("Quest claimant", "Claim any daily quest.", state.claimedQuestKeys.size() >= 1));
        achievements.add(new Achievement("Gem collector", "Hold at least 5 gems.", state.gems >= 5));
        achievements.add(new Achievement("First feeling", "Record one emotion check-in.", state.moodEntries.size() >= 1));
        achievements.add(new Achievement("Mood journal", "Record seven emotion check-ins.", state.moodEntries.size() >= 7));
        achievements.add(new Achievement("Life crafter", "Craft any item at the crafting bench.", anyCraftedItem()));
        achievements.add(new Achievement("Ritual anchor", "Enable one daily habit reminder.", enabledReminderCount() >= 1));
        achievements.add(new Achievement("Companion keeper", "Raise your monster companion to level 3.", monsterLevel() >= 3));
        achievements.add(new Achievement("Monster bond", "Build 80 bond with your companion.", state.monsterBond >= 80));
        achievements.add(new Achievement("LifePal level 5", "Reach level 5 through real actions.", getLevel() >= 5));
        return achievements;
    }

    private boolean anyRewardClaimed() {
        for (Reward reward : state.rewards) {
            if (reward.claimedCount > 0) {
                return true;
            }
        }
        return false;
    }

    private boolean anyCraftedItem() {
        for (String item : state.inventory) {
            if (item.startsWith("Crafted")) {
                return true;
            }
        }
        return false;
    }

    private int enabledReminderCount() {
        int count = 0;
        for (Habit habit : activeHabits()) {
            if (habit.reminderEnabled) {
                count++;
            }
        }
        return count;
    }

    private String nextReminderLine() {
        Habit nextHabit = null;
        String nextTime = "";
        for (Habit habit : activeHabits()) {
            if (!habit.reminderEnabled) {
                continue;
            }
            String reminderTime = normalizeReminderTime(habit.reminderTime);
            if (nextHabit == null || reminderTime.compareTo(nextTime) < 0) {
                nextHabit = habit;
                nextTime = reminderTime;
            }
        }
        return nextHabit == null ? "None set" : nextTime + " - " + nextHabit.name;
    }

    private String reminderLabel(Habit habit) {
        return habit.reminderEnabled ? "Daily at " + normalizeReminderTime(habit.reminderTime) : "Off";
    }

    private String normalizeReminderTime(String value) {
        for (String time : REMINDER_TIMES) {
            if (time.equals(value)) {
                return time;
            }
        }
        return "09:00";
    }

    private boolean notificationsAllowed() {
        return Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
        }
    }

    private boolean securityEnabled() {
        return state != null
                && state.securityEnabled
                && !state.passwordSalt.isEmpty()
                && !state.passwordHash.isEmpty();
    }

    private void setSecurityPassword(String password) {
        byte[] salt = new byte[16];
        new SecureRandom().nextBytes(salt);
        state.passwordSalt = Base64.encodeToString(salt, Base64.NO_WRAP);
        state.passwordHash = hashPassword(password, state.passwordSalt);
        state.securityEnabled = true;
    }

    private boolean verifySecurityPassword(String password) {
        if (!securityEnabled()) {
            return true;
        }
        String actual = hashPassword(password.trim(), state.passwordSalt);
        return constantTimeEquals(actual, state.passwordHash);
    }

    private String hashPassword(String password, String salt) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Base64.decode(salt, Base64.NO_WRAP));
            digest.update(password.getBytes(StandardCharsets.UTF_8));
            return Base64.encodeToString(digest.digest(), Base64.NO_WRAP);
        } catch (Exception exception) {
            return "";
        }
    }

    private boolean constantTimeEquals(String first, String second) {
        byte[] left = first.getBytes(StandardCharsets.UTF_8);
        byte[] right = second.getBytes(StandardCharsets.UTF_8);
        int diff = left.length ^ right.length;
        int length = Math.min(left.length, right.length);
        for (int i = 0; i < length; i++) {
            diff |= left[i] ^ right[i];
        }
        return diff == 0;
    }

    private AppState loadState() {
        String raw = prefs.getString(STATE_KEY, null);
        if (raw == null) {
            return defaultState();
        }
        try {
            return AppState.fromJson(new JSONObject(raw));
        } catch (JSONException exception) {
            return defaultState();
        }
    }

    private void saveState() {
        prefs.edit().putString(STATE_KEY, state.toJson().toString()).apply();
        ReminderScheduler.refreshAll(this);
        MyLifePalWidgetProvider.updateAll(this);
    }

    private AppState defaultState() {
        AppState appState = new AppState();
        appState.coins = 25;
        appState.gems = 1;
        appState.monsterBond = 12;
        appState.tomatoLastDate = today();
        appState.rewardLastDate = today();

        appState.habits.add(sampleHabit(
                "Hydrate before coffee",
                "After I start the kettle",
                "drink one glass of water",
                "I am someone who protects my energy.",
                "Make coffee after the glass is empty",
                "Body",
                8,
                5
        ));
        appState.habits.add(sampleHabit(
                "Read one page",
                "After I sit down at night",
                "read one page",
                "I am a reader, even on busy days.",
                "Put a coin toward a book reward",
                "Mind",
                8,
                5
        ));
        appState.habits.add(sampleHabit(
                "Walk after lunch",
                "After I put my lunch plate away",
                "put shoes on and walk for 2 minutes",
                "I am an active person.",
                "Enjoy one favorite song",
                "Body",
                14,
                9
        ));
        appState.habits.add(sampleHabit(
                "One-surface reset",
                "After dinner",
                "move five items back home",
                "I am someone who makes my space easy to live in.",
                "Light a candle or make tea",
                "Home",
                14,
                9
        ));

        appState.rewards.add(sampleReward("Fancy coffee or tea", 35));
        appState.rewards.add(sampleReward("Guilt-free game session", 75));
        appState.rewards.add(sampleReward("Book fund deposit", 120));
        appState.rewards.add(sampleReward("Long walk somewhere nice", 55));

        for (String attribute : ATTRIBUTES) {
            appState.attributeXp.put(attribute, 0);
        }
        return appState;
    }

    private Habit sampleHabit(String name, String cue, String tinyAction, String identity, String reward, String attribute, int xp, int coins) {
        Habit habit = new Habit();
        habit.id = UUID.randomUUID().toString();
        habit.name = name;
        habit.cue = cue;
        habit.tinyAction = tinyAction;
        habit.identity = identity;
        habit.reward = reward;
        habit.attribute = attribute;
        habit.xp = xp;
        habit.coins = coins;
        return habit;
    }

    private Reward sampleReward(String title, int cost) {
        Reward reward = new Reward();
        reward.id = UUID.randomUUID().toString();
        reward.title = title;
        reward.cost = cost;
        return reward;
    }

    private void applySystemBars() {
        Window window = getWindow();
        int background = backgroundColor();
        window.setStatusBarColor(background);
        window.setNavigationBarColor(background);
        int flags = 0;
        if (relativeLuminance(background) > 0.55d) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) {
                flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            }
        }
        window.getDecorView().setSystemUiVisibility(flags);
    }

    private int primaryColor() {
        return safeThemeColor(state == null ? FOREST : state.themePrimary, FOREST);
    }

    private int accentColor() {
        return safeThemeColor(state == null ? GOLD : state.themeAccent, GOLD);
    }

    private int backgroundColor() {
        int background = safeThemeColor(state == null ? BG : state.themeBackground, BG);
        return relativeLuminance(background) < 0.72d ? BG : background;
    }

    private int primarySoft() {
        return tint(primaryColor(), 0.86f);
    }

    private int accentSoft() {
        return tint(accentColor(), 0.82f);
    }

    private int safeThemeColor(int color, int fallback) {
        if (color == 0 || Color.alpha(color) < 255) {
            return fallback;
        }
        return Color.rgb(Color.red(color), Color.green(color), Color.blue(color));
    }

    private int tint(int color, float amount) {
        return mix(color, Color.WHITE, amount);
    }

    private int shade(int color, float amount) {
        return mix(color, Color.BLACK, amount);
    }

    private int mix(int color, int target, float amount) {
        float safeAmount = Math.max(0f, Math.min(1f, amount));
        return Color.rgb(
                Math.round(Color.red(color) + (Color.red(target) - Color.red(color)) * safeAmount),
                Math.round(Color.green(color) + (Color.green(target) - Color.green(color)) * safeAmount),
                Math.round(Color.blue(color) + (Color.blue(target) - Color.blue(color)) * safeAmount)
        );
    }

    private int readableTextColor(int background) {
        if (Color.alpha(background) < 32) {
            return INK;
        }
        return relativeLuminance(background) > 0.54d ? INK : Color.WHITE;
    }

    private int primaryTextColor() {
        if (contrastRatio(primaryColor(), Color.WHITE) >= 4.5d) {
            return primaryColor();
        }
        int shaded = shade(primaryColor(), 0.42f);
        return contrastRatio(shaded, Color.WHITE) >= 4.5d ? shaded : INK;
    }

    private int readableForeground(int requested, int background) {
        if (Color.alpha(background) < 32 || contrastRatio(requested, background) >= 4.5d) {
            return requested;
        }
        return readableTextColor(background);
    }

    private double contrastRatio(int first, int second) {
        double lighter = Math.max(relativeLuminance(first), relativeLuminance(second));
        double darker = Math.min(relativeLuminance(first), relativeLuminance(second));
        return (lighter + 0.05d) / (darker + 0.05d);
    }

    private double relativeLuminance(int color) {
        double red = linearChannel(Color.red(color) / 255d);
        double green = linearChannel(Color.green(color) / 255d);
        double blue = linearChannel(Color.blue(color) / 255d);
        return 0.2126d * red + 0.7152d * green + 0.0722d * blue;
    }

    private double linearChannel(double channel) {
        return channel <= 0.03928d ? channel / 12.92d : Math.pow((channel + 0.055d) / 1.055d, 2.4d);
    }

    private String themeDisplayName() {
        return fallback(state.themeName, "Custom") + " " + formatHexColor(primaryColor()) + " / " + formatHexColor(accentColor());
    }

    private int themeChoiceIndex() {
        String name = state == null ? "" : state.themeName;
        for (int i = 0; i < THEME_PRESETS.length; i++) {
            if (THEME_PRESETS[i].name.equals(name)
                    && THEME_PRESETS[i].primary == primaryColor()
                    && THEME_PRESETS[i].accent == accentColor()
                    && THEME_PRESETS[i].background == backgroundColor()) {
                return i;
            }
        }
        return THEME_CHOICES.length - 1;
    }

    private String matchingPresetName(int primary, int accent, int background, int choice) {
        if (choice >= 0 && choice < THEME_PRESETS.length) {
            ThemePreset preset = THEME_PRESETS[choice];
            if (preset.primary == primary && preset.accent == accent && preset.background == background) {
                return preset.name;
            }
        }
        for (ThemePreset preset : THEME_PRESETS) {
            if (preset.primary == primary && preset.accent == accent && preset.background == background) {
                return preset.name;
            }
        }
        return "Custom";
    }

    private Integer parseThemeColor(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!normalized.startsWith("#")) {
            normalized = "#" + normalized;
        }
        if (!normalized.matches("#[0-9a-fA-F]{6}")) {
            return null;
        }
        try {
            return Color.parseColor(normalized);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private String formatHexColor(int color) {
        return String.format(Locale.US, "#%02X%02X%02X", Color.red(color), Color.green(color), Color.blue(color));
    }

    private TextView text(String value, int sp, int color, int style) {
        TextView textView = new TextView(this);
        textView.setText(value);
        textView.setTextSize(sp);
        textView.setTextColor(color);
        textView.setTypeface(Typeface.DEFAULT, style);
        textView.setIncludeFontPadding(true);
        textView.setLineSpacing(dp(2), 1.04f);
        return textView;
    }

    private TextView label(String value) {
        TextView label = text(value, 13, MUTED, Typeface.BOLD);
        label.setPadding(0, dp(12), 0, dp(6));
        return label;
    }

    private TextView pill(String value, int background, int foreground) {
        TextView pill = text(value, 13, readableForeground(foreground, background), Typeface.BOLD);
        pill.setGravity(Gravity.CENTER);
        pill.setPadding(dp(12), dp(7), dp(12), dp(7));
        pill.setMinHeight(dp(32));
        pill.setBackground(round(background, 999));
        pill.setContentDescription(value);
        return pill;
    }

    private TextView detailLine(String label, String value) {
        TextView line = text(label + ": " + value, 14, MUTED, Typeface.NORMAL);
        line.setLineSpacing(dp(2), 1f);
        line.setPadding(0, dp(2), 0, dp(2));
        return line;
    }

    private View smallStat(String value, String label) {
        LinearLayout stat = vertical();
        stat.setGravity(Gravity.CENTER);
        stat.setPadding(dp(8), dp(8), dp(8), dp(8));
        stat.setBackground(round(Color.rgb(244, 247, 244), 8));
        stat.setContentDescription(label + ": " + value);
        stat.addView(text(value, 16, INK, Typeface.BOLD));
        TextView labelView = text(label, 11, MUTED, Typeface.NORMAL);
        labelView.setGravity(Gravity.CENTER);
        stat.addView(labelView);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        stat.setLayoutParams(params);
        return stat;
    }

    private View headerStat(String value, String label) {
        LinearLayout stat = vertical();
        stat.setPadding(dp(10), dp(8), dp(10), dp(8));
        stat.setGravity(Gravity.CENTER);
        stat.setBackground(round(Color.argb(45, 255, 255, 255), 8, Color.argb(65, 255, 255, 255), 1));
        stat.setContentDescription(label + ": " + value);
        TextView valueView = text(value, 15, Color.WHITE, Typeface.BOLD);
        valueView.setGravity(Gravity.CENTER);
        TextView labelView = text(label, 11, Color.rgb(218, 239, 226), Typeface.NORMAL);
        labelView.setGravity(Gravity.CENTER);
        stat.addView(valueView);
        stat.addView(labelView);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(dp(3), 0, dp(3), 0);
        stat.setLayoutParams(params);
        return stat;
    }

    private void addSectionHeader(LinearLayout content, String title, String subtitle) {
        TextView titleView = text(title, 22, INK, Typeface.BOLD);
        if (Build.VERSION.SDK_INT >= 28) {
            titleView.setAccessibilityHeading(true);
        }
        content.addView(titleView);
        TextView subtitleView = text(subtitle, 14, MUTED, Typeface.NORMAL);
        subtitleView.setLineSpacing(dp(2), 1f);
        content.addView(subtitleView);
        addSpace(content, 12);
    }

    private Button button(String label, int background, int foreground) {
        Button button = new Button(this);
        button.setAllCaps(false);
        button.setText(label);
        button.setTextSize(14);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setTextColor(readableForeground(foreground, background));
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(48));
        button.setMinimumHeight(dp(48));
        button.setMinWidth(dp(48));
        button.setMinimumWidth(dp(48));
        button.setSingleLine(false);
        button.setMaxLines(2);
        button.setPadding(dp(12), dp(4), dp(12), dp(4));
        button.setBackground(round(background, 8));
        button.setContentDescription(label);
        return button;
    }

    private EditText input(String hint, String value) {
        EditText input = new EditText(this);
        input.setHint(hint);
        input.setText(value);
        input.setTextSize(15);
        input.setTextColor(INK);
        input.setHintTextColor(Color.rgb(133, 143, 136));
        input.setSingleLine(false);
        input.setMinLines(1);
        input.setMaxLines(3);
        input.setMinimumHeight(dp(48));
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.setBackground(round(Color.rgb(247, 249, 246), 8, LINE, 1));
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setContentDescription(hint);
        return input;
    }

    private Spinner spinner(String[] values) {
        Spinner spinner = new Spinner(this);
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, values);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setBackground(round(Color.rgb(247, 249, 246), 8, LINE, 1));
        spinner.setMinimumHeight(dp(48));
        spinner.setPadding(dp(8), 0, dp(8), 0);
        return spinner;
    }

    private ProgressBar progressBar(int progress, int max, int color) {
        ProgressBar progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(Math.max(1, max));
        progressBar.setProgress(Math.min(progress, max));
        progressBar.setProgressTintList(ColorStateList.valueOf(color));
        progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(tint(primaryColor(), 0.88f)));
        progressBar.setContentDescription(Math.min(progress, max) + " of " + Math.max(1, max));
        return progressBar;
    }

    private LinearLayout card() {
        LinearLayout card = vertical();
        card.setPadding(dp(18), dp(16), dp(18), dp(16));
        card.setBackground(round(SURFACE, 8, LINE, 1));
        return card;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        return layout;
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

    private void addSpace(LinearLayout parent, int dp) {
        View spacer = new View(this);
        spacer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        parent.addView(spacer, new LinearLayout.LayoutParams(1, dp(dp)));
    }

    private void addGap(LinearLayout parent, int dp) {
        View spacer = new View(this);
        spacer.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        parent.addView(spacer, new LinearLayout.LayoutParams(dp(dp), 1));
    }

    private int attributeColor(String attribute) {
        if ("Body".equals(attribute)) {
            return CORAL;
        }
        if ("Craft".equals(attribute)) {
            return LILAC;
        }
        if ("Home".equals(attribute)) {
            return SKY;
        }
        if ("Social".equals(attribute)) {
            return Color.rgb(213, 139, 63);
        }
        return primaryColor();
    }

    private int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(value)) {
                return i;
            }
        }
        return 0;
    }

    private String fallback(String value, String fallback) {
        String trimmed = value == null ? "" : value.trim();
        return trimmed.isEmpty() ? fallback : trimmed;
    }

    private int parsePositiveInt(String value, int fallback) {
        try {
            return Math.max(1, Integer.parseInt(value.trim()));
        } catch (Exception exception) {
            return fallback;
        }
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

    private class MonsterView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();

        MonsterView(android.content.Context context) {
            super(context);
            setMinimumHeight(dp(108));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float width = getWidth();
            float height = getHeight();
            int level = monsterLevel();
            int bodyColor = level >= 8 ? LILAC : level >= 5 ? primaryColor() : level >= 3 ? SKY : accentColor();

            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(255, 248, 224));
            oval.set(width * 0.06f, height * 0.06f, width * 0.94f, height * 0.94f);
            canvas.drawOval(oval, paint);

            if (level >= 5) {
                paint.setColor(Color.rgb(218, 232, 242));
                oval.set(width * 0.02f, height * 0.32f, width * 0.34f, height * 0.68f);
                canvas.drawOval(oval, paint);
                oval.set(width * 0.66f, height * 0.32f, width * 0.98f, height * 0.68f);
                canvas.drawOval(oval, paint);
            }

            if (level >= 3) {
                paint.setColor(CORAL);
                canvas.drawCircle(width * 0.31f, height * 0.24f, width * 0.08f, paint);
                canvas.drawCircle(width * 0.69f, height * 0.24f, width * 0.08f, paint);
            }

            paint.setColor(bodyColor);
            oval.set(width * 0.18f, height * 0.24f, width * 0.82f, height * 0.82f);
            canvas.drawOval(oval, paint);

            paint.setColor(Color.rgb(255, 253, 238));
            oval.set(width * 0.31f, height * 0.49f, width * 0.69f, height * 0.78f);
            canvas.drawOval(oval, paint);

            paint.setColor(INK);
            canvas.drawCircle(width * 0.41f, height * 0.48f, width * 0.035f, paint);
            canvas.drawCircle(width * 0.59f, height * 0.48f, width * 0.035f, paint);
            oval.set(width * 0.44f, height * 0.59f, width * 0.56f, height * 0.65f);
            canvas.drawOval(oval, paint);

            paint.setColor(Color.rgb(255, 231, 213));
            canvas.drawCircle(width * 0.33f, height * 0.57f, width * 0.04f, paint);
            canvas.drawCircle(width * 0.67f, height * 0.57f, width * 0.04f, paint);

            paint.setColor(bodyColor);
            oval.set(width * 0.28f, height * 0.74f, width * 0.44f, height * 0.90f);
            canvas.drawOval(oval, paint);
            oval.set(width * 0.56f, height * 0.74f, width * 0.72f, height * 0.90f);
            canvas.drawOval(oval, paint);
        }
    }

    private static class Habit {
        String id = "";
        String name = "";
        String icon = "*";
        String cue = "";
        String tinyAction = "";
        String identity = "";
        String reward = "";
        String attribute = "Mind";
        String lastCompleted = "";
        String reminderTime = "09:00";
        String trackingType = "Completion";
        String goalMode = "Goal";
        String period = "Daily";
        String unit = "x";
        String trackingPeriodKey = "";
        boolean reminderEnabled = false;
        boolean trackerRunning = false;
        int xp = 8;
        int coins = 5;
        int targetValue = 1;
        int quantityValue = 0;
        int secondsValue = 0;
        int streak = 0;
        int bestStreak = 0;
        int completions = 0;
        long trackerStartedAtMillis = 0L;

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("id", id);
                json.put("name", name);
                json.put("icon", icon);
                json.put("cue", cue);
                json.put("tinyAction", tinyAction);
                json.put("identity", identity);
                json.put("reward", reward);
                json.put("attribute", attribute);
                json.put("lastCompleted", lastCompleted);
                json.put("reminderTime", reminderTime);
                json.put("reminderEnabled", reminderEnabled);
                json.put("trackingType", trackingType);
                json.put("goalMode", goalMode);
                json.put("period", period);
                json.put("unit", unit);
                json.put("trackingPeriodKey", trackingPeriodKey);
                json.put("trackerRunning", trackerRunning);
                json.put("targetValue", targetValue);
                json.put("quantityValue", quantityValue);
                json.put("secondsValue", secondsValue);
                json.put("trackerStartedAtMillis", trackerStartedAtMillis);
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
            Habit habit = new Habit();
            habit.id = json.optString("id", UUID.randomUUID().toString());
            habit.name = json.optString("name", "");
            habit.icon = json.optString("icon", "*");
            habit.cue = json.optString("cue", "");
            habit.tinyAction = json.optString("tinyAction", "");
            habit.identity = json.optString("identity", "");
            habit.reward = json.optString("reward", "");
            habit.attribute = json.optString("attribute", "Mind");
            habit.lastCompleted = json.optString("lastCompleted", "");
            habit.reminderTime = json.optString("reminderTime", "09:00");
            habit.reminderEnabled = json.optBoolean("reminderEnabled", false);
            habit.trackingType = json.optString("trackingType", "Completion");
            habit.goalMode = json.optString("goalMode", "Goal");
            habit.period = json.optString("period", "Daily");
            habit.unit = json.optString("unit", "x");
            habit.trackingPeriodKey = json.optString("trackingPeriodKey", "");
            habit.trackerRunning = json.optBoolean("trackerRunning", false);
            habit.targetValue = json.optInt("targetValue", 1);
            habit.quantityValue = json.optInt("quantityValue", 0);
            habit.secondsValue = json.optInt("secondsValue", 0);
            habit.trackerStartedAtMillis = json.optLong("trackerStartedAtMillis", 0L);
            habit.xp = json.optInt("xp", 8);
            habit.coins = json.optInt("coins", 5);
            habit.streak = json.optInt("streak", 0);
            habit.bestStreak = json.optInt("bestStreak", 0);
            habit.completions = json.optInt("completions", 0);
            return habit;
        }
    }

    private static class Reward {
        String id = "";
        String title = "";
        int cost = 40;
        int claimedCount = 0;

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("id", id);
                json.put("title", title);
                json.put("cost", cost);
                json.put("claimedCount", claimedCount);
            } catch (JSONException ignored) {
            }
            return json;
        }

        static Reward fromJson(JSONObject json) {
            Reward reward = new Reward();
            reward.id = json.optString("id", UUID.randomUUID().toString());
            reward.title = json.optString("title", "");
            reward.cost = json.optInt("cost", 40);
            reward.claimedCount = json.optInt("claimedCount", 0);
            return reward;
        }
    }

    private static class MoodEntry {
        String date = "";
        int mood = 3;
        int energy = 3;
        int stress = 3;
        String note = "";

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("date", date);
                json.put("mood", mood);
                json.put("energy", energy);
                json.put("stress", stress);
                json.put("note", note);
            } catch (JSONException ignored) {
            }
            return json;
        }

        static MoodEntry fromJson(JSONObject json) {
            MoodEntry entry = new MoodEntry();
            entry.date = json.optString("date", "");
            entry.mood = json.optInt("mood", 3);
            entry.energy = json.optInt("energy", 3);
            entry.stress = json.optInt("stress", 3);
            entry.note = json.optString("note", "");
            return entry;
        }
    }

    private static class GameQuest {
        final String id;
        final String title;
        final String description;
        final int progress;
        final int goal;
        final int xp;
        final int coins;
        final int gems;
        final boolean loot;

        GameQuest(String id, String title, String description, int progress, int goal, int xp, int coins, int gems, boolean loot) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.progress = progress;
            this.goal = goal;
            this.xp = xp;
            this.coins = coins;
            this.gems = gems;
            this.loot = loot;
        }
    }

    private static class Achievement {
        final String title;
        final String description;
        final boolean unlocked;

        Achievement(String title, String description, boolean unlocked) {
            this.title = title;
            this.description = description;
            this.unlocked = unlocked;
        }
    }

    private static class ThemePreset {
        final String name;
        final int primary;
        final int accent;
        final int background;

        ThemePreset(String name, int primary, int accent, int background) {
            this.name = name;
            this.primary = primary;
            this.accent = accent;
            this.background = background;
        }
    }

    private static class AppState {
        int totalXp = 0;
        int coins = 0;
        int gems = 0;
        int chestsOpened = 0;
        int monsterXp = 0;
        int monsterBond = 0;
        int timerMode = 0;
        long timerDurationMillis = 25L * 60L * 1000L;
        long timerRemainingMillis = 25L * 60L * 1000L;
        long timerEndAtMillis = 0L;
        boolean timerRunning = false;
        int tomatoFocusSessions = 0;
        int tomatoBreakSessions = 0;
        int tomatoSessionsToday = 0;
        int tomatoMinutesToday = 0;
        int rewardClaimsToday = 0;
        int themePrimary = FOREST;
        int themeAccent = GOLD;
        int themeBackground = BG;
        String lastChestDate = "";
        String tomatoLastDate = "";
        String rewardLastDate = "";
        String themeName = "Forest";
        String monsterName = "Milo";
        String monsterLastCareDate = "";
        boolean securityEnabled = false;
        String passwordSalt = "";
        String passwordHash = "";
        List<Habit> habits = new ArrayList<>();
        List<Reward> rewards = new ArrayList<>();
        List<MoodEntry> moodEntries = new ArrayList<>();
        List<String> inventory = new ArrayList<>();
        List<String> claimedQuestKeys = new ArrayList<>();
        Map<String, Integer> attributeXp = new HashMap<>();

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("totalXp", totalXp);
                json.put("coins", coins);
                json.put("gems", gems);
                json.put("chestsOpened", chestsOpened);
                json.put("monsterXp", monsterXp);
                json.put("monsterBond", monsterBond);
                json.put("timerMode", timerMode);
                json.put("timerDurationMillis", timerDurationMillis);
                json.put("timerRemainingMillis", timerRemainingMillis);
                json.put("timerEndAtMillis", timerEndAtMillis);
                json.put("timerRunning", timerRunning);
                json.put("tomatoFocusSessions", tomatoFocusSessions);
                json.put("tomatoBreakSessions", tomatoBreakSessions);
                json.put("tomatoSessionsToday", tomatoSessionsToday);
                json.put("tomatoMinutesToday", tomatoMinutesToday);
                json.put("rewardClaimsToday", rewardClaimsToday);
                json.put("themeName", themeName);
                json.put("themePrimary", themePrimary);
                json.put("themeAccent", themeAccent);
                json.put("themeBackground", themeBackground);
                json.put("lastChestDate", lastChestDate);
                json.put("tomatoLastDate", tomatoLastDate);
                json.put("rewardLastDate", rewardLastDate);
                json.put("monsterName", monsterName);
                json.put("monsterLastCareDate", monsterLastCareDate);
                json.put("securityEnabled", securityEnabled);
                json.put("passwordSalt", passwordSalt);
                json.put("passwordHash", passwordHash);
                JSONArray habitArray = new JSONArray();
                for (Habit habit : habits) {
                    habitArray.put(habit.toJson());
                }
                json.put("habits", habitArray);
                JSONArray rewardArray = new JSONArray();
                for (Reward reward : rewards) {
                    rewardArray.put(reward.toJson());
                }
                json.put("rewards", rewardArray);
                JSONArray moodArray = new JSONArray();
                for (MoodEntry entry : moodEntries) {
                    moodArray.put(entry.toJson());
                }
                json.put("moodEntries", moodArray);
                JSONArray inventoryArray = new JSONArray();
                for (String item : inventory) {
                    inventoryArray.put(item);
                }
                json.put("inventory", inventoryArray);
                JSONArray questArray = new JSONArray();
                for (String key : claimedQuestKeys) {
                    questArray.put(key);
                }
                json.put("claimedQuestKeys", questArray);
                JSONObject attributes = new JSONObject();
                for (Map.Entry<String, Integer> entry : attributeXp.entrySet()) {
                    attributes.put(entry.getKey(), entry.getValue());
                }
                json.put("attributeXp", attributes);
            } catch (JSONException ignored) {
            }
            return json;
        }

        static AppState fromJson(JSONObject json) {
            AppState state = new AppState();
            state.totalXp = json.optInt("totalXp", 0);
            state.coins = json.optInt("coins", 0);
            state.gems = json.optInt("gems", 0);
            state.chestsOpened = json.optInt("chestsOpened", 0);
            state.monsterXp = json.optInt("monsterXp", 0);
            state.monsterBond = json.optInt("monsterBond", 0);
            state.timerMode = json.optInt("timerMode", 0);
            state.timerDurationMillis = json.optLong("timerDurationMillis", 25L * 60L * 1000L);
            state.timerRemainingMillis = json.optLong("timerRemainingMillis", state.timerDurationMillis);
            state.timerEndAtMillis = json.optLong("timerEndAtMillis", 0L);
            state.timerRunning = json.optBoolean("timerRunning", false);
            state.tomatoFocusSessions = json.optInt("tomatoFocusSessions", 0);
            state.tomatoBreakSessions = json.optInt("tomatoBreakSessions", 0);
            state.tomatoSessionsToday = json.optInt("tomatoSessionsToday", 0);
            state.tomatoMinutesToday = json.optInt("tomatoMinutesToday", 0);
            state.rewardClaimsToday = json.optInt("rewardClaimsToday", 0);
            state.themeName = json.optString("themeName", "Forest");
            state.themePrimary = json.optInt("themePrimary", FOREST);
            state.themeAccent = json.optInt("themeAccent", GOLD);
            state.themeBackground = json.optInt("themeBackground", BG);
            state.lastChestDate = json.optString("lastChestDate", "");
            state.tomatoLastDate = json.optString("tomatoLastDate", "");
            state.rewardLastDate = json.optString("rewardLastDate", "");
            state.monsterName = json.optString("monsterName", "Milo");
            state.monsterLastCareDate = json.optString("monsterLastCareDate", "");
            state.securityEnabled = json.optBoolean("securityEnabled", false);
            state.passwordSalt = json.optString("passwordSalt", "");
            state.passwordHash = json.optString("passwordHash", "");
            if (state.passwordSalt.isEmpty() || state.passwordHash.isEmpty()) {
                state.securityEnabled = false;
                state.passwordSalt = "";
                state.passwordHash = "";
            }

            JSONArray habits = json.optJSONArray("habits");
            if (habits != null) {
                for (int i = 0; i < habits.length(); i++) {
                    JSONObject habit = habits.optJSONObject(i);
                    if (habit != null) {
                        state.habits.add(Habit.fromJson(habit));
                    }
                }
            }

            JSONArray rewards = json.optJSONArray("rewards");
            if (rewards != null) {
                for (int i = 0; i < rewards.length(); i++) {
                    JSONObject reward = rewards.optJSONObject(i);
                    if (reward != null) {
                        state.rewards.add(Reward.fromJson(reward));
                    }
                }
            }

            JSONArray moods = json.optJSONArray("moodEntries");
            if (moods != null) {
                for (int i = 0; i < moods.length(); i++) {
                    JSONObject mood = moods.optJSONObject(i);
                    if (mood != null) {
                        state.moodEntries.add(MoodEntry.fromJson(mood));
                    }
                }
            }

            JSONArray inventory = json.optJSONArray("inventory");
            if (inventory != null) {
                for (int i = 0; i < inventory.length(); i++) {
                    state.inventory.add(inventory.optString(i));
                }
            }

            JSONArray claimedQuests = json.optJSONArray("claimedQuestKeys");
            if (claimedQuests != null) {
                for (int i = 0; i < claimedQuests.length(); i++) {
                    state.claimedQuestKeys.add(claimedQuests.optString(i));
                }
            }

            JSONObject attributes = json.optJSONObject("attributeXp");
            if (attributes != null) {
                JSONArray names = attributes.names();
                if (names != null) {
                    for (int i = 0; i < names.length(); i++) {
                        String name = names.optString(i);
                        state.attributeXp.put(name, attributes.optInt(name, 0));
                    }
                }
            }
            for (String attribute : ATTRIBUTES) {
                if (!state.attributeXp.containsKey(attribute)) {
                    state.attributeXp.put(attribute, 0);
                }
            }
            return state;
        }
    }
}
