const STORAGE_KEY = "mylifepal_chrome_state";
const HABIT_ALARM_PREFIX = "habit:";
const TIMER_ALARM_PREFIX = "timer:";

chrome.runtime.onInstalled.addListener(() => {
  ensureState().then((state) => {
    scheduleAllHabitReminders(state);
    scheduleTimerIfNeeded(state);
    updateBadge(state);
  });
});

chrome.runtime.onStartup.addListener(() => {
  ensureState().then((state) => {
    scheduleAllHabitReminders(state);
    scheduleTimerIfNeeded(state);
    updateBadge(state);
  });
});

chrome.runtime.onMessage.addListener((message, sender, sendResponse) => {
  if (!message || !message.type) {
    return false;
  }

  if (message.type === "MYLIFEPAL_STATE_CHANGED") {
    ensureState().then((state) => {
      scheduleAllHabitReminders(state);
      scheduleTimerIfNeeded(state);
      updateBadge(state);
      sendResponse({ ok: true });
    });
    return true;
  }

  return false;
});

chrome.alarms.onAlarm.addListener((alarm) => {
  if (alarm.name.startsWith(HABIT_ALARM_PREFIX)) {
    handleHabitAlarm(alarm.name.slice(HABIT_ALARM_PREFIX.length));
  } else if (alarm.name.startsWith(TIMER_ALARM_PREFIX)) {
    handleTimerAlarm(alarm.name.slice(TIMER_ALARM_PREFIX.length));
  }
});

async function handleHabitAlarm(habitId) {
  const state = await ensureState();
  const habit = state.habits.find((item) => item.id === habitId);
  if (!habit || !habit.reminderEnabled) {
    return;
  }

  if (habit.lastCompleted !== todayKey()) {
    showNotification(`habit-${habit.id}`, {
      type: "basic",
      iconUrl: "assets/icon-128.png",
      title: "MyLifePal reminder",
      message: `${habit.cue || "When the cue appears"}: ${habit.tinyAction || habit.name}`,
      contextMessage: habit.reward ? `Reward: ${habit.reward}` : "Tiny action, real-life level."
    });
  }
  scheduleHabitReminder(habit);
}

async function handleTimerAlarm(sessionId) {
  const state = await ensureState();
  if (!state.timer.running || state.timer.sessionId !== sessionId) {
    return;
  }

  state.timer.running = false;
  state.timer.remainingMs = 0;
  state.timer.endAt = 0;
  state.timer.sessionId = "";
  settleTimerDay(state);
  if (state.timer.mode === 0) {
    state.totalXp += 18;
    state.coins += 12;
    state.monsterXp += 12;
    state.timer.focusSessions += 1;
    state.timer.sessionsToday += 1;
    state.timer.minutesToday += 25;
  } else {
    state.timer.breakSessions += 1;
  }
  await saveState(state);
  updateBadge(state);
  showNotification("timer-complete", {
    type: "basic",
    iconUrl: "assets/icon-128.png",
    title: state.timer.mode === 0 ? "Focus tomato complete" : "Break complete",
    message: state.timer.mode === 0 ? "+18 XP, +12 coins, and companion growth." : "Ready for the next tiny action.",
    contextMessage: "MyLifePal"
  });
}

async function scheduleAllHabitReminders(state) {
  const alarms = await chrome.alarms.getAll();
  await Promise.all(
    alarms
      .filter((alarm) => alarm.name.startsWith(HABIT_ALARM_PREFIX))
      .map((alarm) => chrome.alarms.clear(alarm.name))
  );
  await Promise.all(state.habits.filter((habit) => habit.reminderEnabled).map(scheduleHabitReminder));
}

async function scheduleHabitReminder(habit) {
  const when = nextReminderTime(habit.reminderTime);
  await chrome.alarms.create(HABIT_ALARM_PREFIX + habit.id, { when });
}

async function scheduleTimerIfNeeded(state) {
  const alarms = await chrome.alarms.getAll();
  await Promise.all(
    alarms
      .filter((alarm) => alarm.name.startsWith(TIMER_ALARM_PREFIX))
      .map((alarm) => chrome.alarms.clear(alarm.name))
  );
  if (state.timer.running && state.timer.endAt > Date.now() && state.timer.sessionId) {
    await chrome.alarms.create(TIMER_ALARM_PREFIX + state.timer.sessionId, { when: state.timer.endAt });
  }
}

function nextReminderTime(value) {
  const [hour, minute] = normalizeReminderTime(value).split(":").map((part) => Number.parseInt(part, 10));
  const next = new Date();
  next.setHours(hour, minute, 0, 0);
  if (next.getTime() <= Date.now() + 1000) {
    next.setDate(next.getDate() + 1);
  }
  return next.getTime();
}

function normalizeReminderTime(value) {
  return /^([01]\d|2[0-3]):[0-5]\d$/.test(value || "") ? value : "09:00";
}

async function ensureState() {
  const result = await chrome.storage.local.get(STORAGE_KEY);
  if (result[STORAGE_KEY]) {
    return normalizeState(result[STORAGE_KEY]);
  }
  const state = defaultState();
  await saveState(state);
  return state;
}

async function saveState(state) {
  await chrome.storage.local.set({ [STORAGE_KEY]: normalizeState(state) });
}

function showNotification(id, options) {
  chrome.notifications.create(id, options, () => {
    if (chrome.runtime.lastError) {
      // Notification permission can be disabled by the user; the extension keeps working offline.
    }
  });
}

function updateBadge(state) {
  const total = state.habits.length;
  const done = state.habits.filter((habit) => habit.lastCompleted === todayKey()).length;
  chrome.action.setBadgeText({ text: total ? `${done}/${total}` : "" });
  chrome.action.setBadgeBackgroundColor({ color: state.theme.primary || "#2E7D68" });
}

function settleTimerDay(state) {
  if (state.timer.lastDate !== todayKey()) {
    state.timer.lastDate = todayKey();
    state.timer.sessionsToday = 0;
    state.timer.minutesToday = 0;
  }
}

function todayKey() {
  return localDateKey(new Date());
}

function localDateKey(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function defaultState() {
  return normalizeState({
    schemaVersion: 1,
    totalXp: 0,
    coins: 25,
    gems: 1,
    monsterXp: 0,
    monsterBond: 12,
    inventory: [],
    claimedQuestKeys: [],
    theme: {
      name: "Forest",
      primary: "#2E7D68",
      accent: "#F9C74F",
      background: "#F5F7F1"
    },
    timer: {
      mode: 0,
      durationMs: 25 * 60 * 1000,
      remainingMs: 25 * 60 * 1000,
      endAt: 0,
      running: false,
      sessionId: "",
      focusSessions: 0,
      breakSessions: 0,
      sessionsToday: 0,
      minutesToday: 0,
      lastDate: todayKey()
    },
    habits: [
      sampleHabit("Hydrate before coffee", "After I start the kettle", "drink one glass of water", "I protect my energy.", "Make coffee after the glass is empty", "Body", 8, 5),
      sampleHabit("Read one page", "After I sit down at night", "read one page", "I am a reader, even on busy days.", "Put a coin toward a book reward", "Mind", 8, 5),
      sampleHabit("One-surface reset", "After dinner", "move five items back home", "I make my space easier to live in.", "Make tea or light a candle", "Home", 14, 9)
    ],
    rewards: [
      { id: crypto.randomUUID(), title: "Fancy coffee or tea", cost: 35, claimedCount: 0 },
      { id: crypto.randomUUID(), title: "Guilt-free game session", cost: 75, claimedCount: 0 },
      { id: crypto.randomUUID(), title: "Book fund deposit", cost: 120, claimedCount: 0 }
    ],
    moodEntries: []
  });
}

function sampleHabit(name, cue, tinyAction, identity, reward, attribute, xp, coins) {
  return {
    id: crypto.randomUUID(),
    name,
    icon: "*",
    cue,
    tinyAction,
    identity,
    reward,
    attribute,
    lastCompleted: "",
    reminderEnabled: false,
    reminderTime: "09:00",
    streak: 0,
    bestStreak: 0,
    completions: 0,
    xp,
    coins
  };
}

function normalizeState(raw) {
  const base = raw && typeof raw === "object" ? raw : {};
  return {
    schemaVersion: 1,
    totalXp: numberOr(base.totalXp, 0),
    coins: numberOr(base.coins, 0),
    gems: numberOr(base.gems, 0),
    monsterXp: numberOr(base.monsterXp, 0),
    monsterBond: numberOr(base.monsterBond, 0),
    inventory: Array.isArray(base.inventory) ? base.inventory : [],
    claimedQuestKeys: Array.isArray(base.claimedQuestKeys) ? base.claimedQuestKeys : [],
    theme: normalizeTheme(base.theme),
    timer: normalizeTimer(base.timer),
    habits: Array.isArray(base.habits) ? base.habits.map(normalizeHabit) : [],
    rewards: Array.isArray(base.rewards) ? base.rewards.map(normalizeReward) : [],
    moodEntries: Array.isArray(base.moodEntries) ? base.moodEntries.map(normalizeMood) : []
  };
}

function normalizeTheme(theme) {
  const value = theme && typeof theme === "object" ? theme : {};
  const background = validHex(value.background) && relativeLuminance(value.background) >= 0.72 ? value.background.toUpperCase() : "#F5F7F1";
  return {
    name: String(value.name || "Forest"),
    primary: validHex(value.primary) ? value.primary.toUpperCase() : "#2E7D68",
    accent: validHex(value.accent) ? value.accent.toUpperCase() : "#F9C74F",
    background
  };
}

function normalizeTimer(timer) {
  const value = timer && typeof timer === "object" ? timer : {};
  return {
    mode: numberOr(value.mode, 0),
    durationMs: numberOr(value.durationMs, 25 * 60 * 1000),
    remainingMs: numberOr(value.remainingMs, 25 * 60 * 1000),
    endAt: numberOr(value.endAt, 0),
    running: Boolean(value.running),
    sessionId: String(value.sessionId || ""),
    focusSessions: numberOr(value.focusSessions, 0),
    breakSessions: numberOr(value.breakSessions, 0),
    sessionsToday: numberOr(value.sessionsToday, 0),
    minutesToday: numberOr(value.minutesToday, 0),
    lastDate: String(value.lastDate || todayKey())
  };
}

function normalizeHabit(habit) {
  const value = habit && typeof habit === "object" ? habit : {};
  return {
    id: String(value.id || crypto.randomUUID()),
    name: String(value.name || "Tiny habit"),
    icon: String(value.icon || "*"),
    cue: String(value.cue || "After an existing routine"),
    tinyAction: String(value.tinyAction || "do the 2-minute version"),
    identity: String(value.identity || "I keep promises to myself."),
    reward: String(value.reward || "Pause and enjoy the win"),
    attribute: String(value.attribute || "Mind"),
    lastCompleted: String(value.lastCompleted || ""),
    reminderEnabled: Boolean(value.reminderEnabled),
    reminderTime: normalizeReminderTime(value.reminderTime),
    streak: numberOr(value.streak, 0),
    bestStreak: numberOr(value.bestStreak, 0),
    completions: numberOr(value.completions, 0),
    xp: numberOr(value.xp, 8),
    coins: numberOr(value.coins, 5)
  };
}

function normalizeReward(reward) {
  const value = reward && typeof reward === "object" ? reward : {};
  return {
    id: String(value.id || crypto.randomUUID()),
    title: String(value.title || "Reward"),
    cost: numberOr(value.cost, 40),
    claimedCount: numberOr(value.claimedCount, 0)
  };
}

function normalizeMood(entry) {
  const value = entry && typeof entry === "object" ? entry : {};
  return {
    date: String(value.date || todayKey()),
    mood: clamp(numberOr(value.mood, 3), 1, 5),
    energy: clamp(numberOr(value.energy, 3), 1, 5),
    stress: clamp(numberOr(value.stress, 3), 1, 5),
    note: String(value.note || "")
  };
}

function validHex(value) {
  return /^#[0-9A-Fa-f]{6}$/.test(value || "");
}

function relativeLuminance(hex) {
  const color = hexToRgb(hex);
  const red = linearChannel(color.r / 255);
  const green = linearChannel(color.g / 255);
  const blue = linearChannel(color.b / 255);
  return 0.2126 * red + 0.7152 * green + 0.0722 * blue;
}

function linearChannel(value) {
  return value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
}

function hexToRgb(hex) {
  const normalized = validHex(hex) ? hex.slice(1) : "F5F7F1";
  return {
    r: Number.parseInt(normalized.slice(0, 2), 16),
    g: Number.parseInt(normalized.slice(2, 4), 16),
    b: Number.parseInt(normalized.slice(4, 6), 16)
  };
}

function numberOr(value, fallback) {
  const number = Number(value);
  return Number.isFinite(number) ? number : fallback;
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}
