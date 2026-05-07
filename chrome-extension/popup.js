const STORAGE_KEY = "mylifepal_chrome_state";
const VIEWS = ["today", "timer", "mood", "habits", "rewards"];
const ATTRIBUTES = ["Mind", "Body", "Craft", "Home", "Social"];
const REMINDER_TIMES = ["07:00", "08:00", "09:00", "12:30", "17:30", "19:30", "21:30"];
const TIMER_MODES = [
  { label: "Focus tomato", durationMs: 25 * 60 * 1000 },
  { label: "Short break", durationMs: 5 * 60 * 1000 },
  { label: "Long break", durationMs: 15 * 60 * 1000 }
];
const THEME_PRESETS = [
  { name: "Forest", primary: "#2E7D68", accent: "#F9C74F", background: "#F5F7F1" },
  { name: "Ocean", primary: "#1F6997", accent: "#42BEB6", background: "#F1F7F9" },
  { name: "Berry", primary: "#814584", accent: "#EA7E65", background: "#F9F4F8" },
  { name: "Sunrise", primary: "#B65C38", accent: "#F5B84A", background: "#FAF6F0" },
  { name: "Graphite", primary: "#4B5B69", accent: "#51A39A", background: "#F5F6F7" },
  { name: "Rose", primary: "#AF4B6A", accent: "#4E9C85", background: "#FBF5F7" }
];

let state = null;
let currentView = "today";
let timerInterval = null;

document.addEventListener("DOMContentLoaded", async () => {
  wireTabs();
  state = await loadState();
  applyTheme();
  render();
  timerInterval = setInterval(() => {
    if (state && state.timer.running) {
      renderHeader();
      if (currentView === "timer") {
        renderTimer();
      }
      if (timerRemainingMs() <= 0) {
        completeTimer();
      }
    }
  }, 1000);
});

window.addEventListener("unload", () => {
  if (timerInterval) {
    clearInterval(timerInterval);
  }
});

function wireTabs() {
  document.querySelectorAll(".tab-button").forEach((button) => {
    button.addEventListener("click", () => {
      currentView = button.dataset.view;
      render();
    });
  });
}

function render() {
  applyTheme();
  renderHeader();
  document.querySelectorAll(".tab-button").forEach((button) => {
    const active = button.dataset.view === currentView;
    button.classList.toggle("is-active", active);
    button.setAttribute("aria-selected", String(active));
  });

  if (currentView === "today") {
    renderToday();
  } else if (currentView === "timer") {
    renderTimer();
  } else if (currentView === "mood") {
    renderMood();
  } else if (currentView === "habits") {
    renderHabits();
  } else {
    renderRewards();
  }
}

function renderHeader() {
  document.getElementById("levelPill").textContent = `Lv ${level()}`;
  document.getElementById("identityLine").textContent = identityLine();
  document.getElementById("xpStat").textContent = `${state.totalXp} XP`;
  document.getElementById("coinStat").textContent = String(state.coins);
  document.getElementById("doneStat").textContent = `${completedTodayCount()}/${state.habits.length}`;
}

function renderToday() {
  const next = firstIncompleteHabit();
  const content = viewShell("Today", "One tiny action is enough to move the whole system.");
  content.appendChild(coachCard(next));
  if (next) {
    content.appendChild(habitCard(next, { compact: false, today: true }));
  } else {
    content.appendChild(card("is-soft", `
      <h2>Day cleared</h2>
      <p>Every completed habit is one vote for the person you are becoming.</p>
      <div class="button-row">
        <button class="primary-button" data-action="open-rewards">Open rewards</button>
        <button class="secondary-button" data-action="open-habits">Add habit</button>
      </div>
    `));
  }

  const list = document.createElement("div");
  state.habits.forEach((habit) => list.appendChild(habitCard(habit, { compact: true, today: true })));
  content.appendChild(list);
  mount(content);
}

function coachCard(next) {
  const completed = completedTodayCount();
  const score = state.habits.length ? Math.round((completed / state.habits.length) * 70) + (moodForToday() ? 15 : 0) + Math.min(15, state.timer.sessionsToday * 8) : 0;
  const safeScore = Math.min(100, score);
  const copy = next
    ? `Next: ${next.tinyAction}`
    : "Next: claim a reward or design one tiny future action.";
  const element = card("is-accent", `
    <div class="row">
      <div class="grow">
        <h2>${escapeHtml(coachTitle(next))}</h2>
        <p>${escapeHtml(copy)}</p>
      </div>
      <span class="pill inline-primary">${safeScore}%</span>
    </div>
    <div class="grid-3 margin-top">
      <div class="mini-stat"><strong>${monsterLevel()}</strong><span>pal level</span></div>
      <div class="mini-stat"><strong>${state.timer.sessionsToday}</strong><span>tomatoes</span></div>
      <div class="mini-stat"><strong>${state.gems}</strong><span>gems</span></div>
    </div>
    <div class="button-row">
      <button class="primary-button" data-action="${next ? "complete-next" : "open-habits"}">${next ? "Complete next" : "Create habit"}</button>
      <button class="secondary-button" data-action="start-focus">Start focus</button>
    </div>
  `);
  return element;
}

function renderTimer() {
  settleTimerDay();
  const content = viewShell("Tomato Timer", "Protect a small block of focus and turn it into rewards.");
  const remaining = timerRemainingMs();
  const duration = Math.max(1, state.timer.durationMs);
  const elapsed = Math.max(0, duration - remaining);
  const progress = Math.min(100, Math.round((elapsed / duration) * 100));
  const mode = TIMER_MODES[state.timer.mode] || TIMER_MODES[0];
  content.appendChild(card("is-accent", `
    <div class="row">
      <div class="grow">
        <h2>${mode.label}</h2>
        <p>${state.timer.running ? "In focus" : "Ready when you are"}</p>
      </div>
      <span class="pill inline-primary">${state.timer.running ? "Running" : "Paused"}</span>
    </div>
    <div class="clock">${formatTimer(remaining)}</div>
    <div class="progress" aria-label="${progress}% complete"><span style="width:${progress}%"></span></div>
    <div class="grid-3 margin-top">
      <div class="mini-stat"><strong>${state.timer.sessionsToday}</strong><span>today</span></div>
      <div class="mini-stat"><strong>${state.timer.minutesToday}</strong><span>minutes</span></div>
      <div class="mini-stat"><strong>${state.timer.focusSessions}</strong><span>focus</span></div>
    </div>
    <div class="button-row">
      <button class="primary-button" data-action="${state.timer.running ? "pause-timer" : "start-timer"}">${state.timer.running ? "Pause" : "Start"}</button>
      <button class="secondary-button" data-action="reset-timer">Reset</button>
    </div>
  `));

  const modes = document.createElement("div");
  modes.className = "grid-3";
  TIMER_MODES.forEach((item, index) => {
    const button = document.createElement("button");
    button.type = "button";
    button.className = index === state.timer.mode ? "primary-button" : "secondary-button";
    button.textContent = item.label.replace(" tomato", "");
    button.dataset.action = "timer-mode";
    button.dataset.mode = String(index);
    modes.appendChild(button);
  });
  content.appendChild(modes);
  mount(content);
}

function renderMood() {
  const todayMood = moodForToday();
  const content = viewShell("Mood", "A quick check-in makes the habit loop more honest.");
  content.appendChild(card(todayMood ? "is-soft" : "", `
    <h2>${todayMood ? moodLabel(todayMood.mood) : "No check-in today"}</h2>
    <p>${todayMood ? `Energy ${todayMood.energy}/5, stress ${todayMood.stress}/5` : "Name the weather inside, then choose a humane next action."}</p>
    ${todayMood && todayMood.note ? `<p>${escapeHtml(todayMood.note)}</p>` : ""}
  `));

  const form = document.createElement("form");
  form.className = "card form";
  form.dataset.form = "mood";
  form.innerHTML = `
    <div class="grid-3">
      ${selectField("mood", "Mood", ["1 Rough", "2 Low", "3 Okay", "4 Good", "5 Great"], String(todayMood ? todayMood.mood : 3))}
      ${selectField("energy", "Energy", ["1", "2", "3", "4", "5"], String(todayMood ? todayMood.energy : 3))}
      ${selectField("stress", "Stress", ["1", "2", "3", "4", "5"], String(todayMood ? todayMood.stress : 3))}
    </div>
    <div class="field">
      <label for="moodNote">Note</label>
      <textarea id="moodNote" name="note" placeholder="What affected your mood?">${escapeHtml(todayMood ? todayMood.note : "")}</textarea>
    </div>
    <button class="primary-button" type="submit">${todayMood ? "Update check-in" : "Record check-in"}</button>
  `;
  content.appendChild(form);

  const recent = document.createElement("div");
  recent.innerHTML = `<h2 class="section-title">Recent feelings</h2>`;
  state.moodEntries.slice(-5).reverse().forEach((entry) => {
    recent.appendChild(card("", `
      <div class="row">
        <div class="grow">
          <h3>${escapeHtml(entry.date)} - ${moodLabel(entry.mood)}</h3>
          <p>Energy ${entry.energy}/5, stress ${entry.stress}/5</p>
        </div>
        <span class="pill inline-primary">${entry.mood}/5</span>
      </div>
    `));
  });
  content.appendChild(recent);
  mount(content);
}

function renderHabits() {
  const content = viewShell("Habits", "Design cues, tiny actions, identity votes, rewards, and reminders.");
  content.appendChild(habitFormCard());
  if (!state.habits.length) {
    content.appendChild(emptyCard("No habits yet."));
  }
  state.habits.forEach((habit) => content.appendChild(habitCard(habit, { compact: false, today: false })));
  mount(content);
}

function habitFormCard() {
  const form = document.createElement("form");
  form.className = "card form";
  form.dataset.form = "habit";
  form.innerHTML = `
    <h2>New tiny habit</h2>
    ${inputField("name", "Name", "Read one page")}
    ${inputField("cue", "Cue", "After I sit down at night")}
    ${inputField("tinyAction", "Tiny action", "read one page")}
    ${inputField("identity", "Identity", "I am a reader, even on busy days.")}
    ${inputField("reward", "Reward", "Put one coin toward a book reward")}
    <div class="grid-2">
      ${selectField("attribute", "Attribute", ATTRIBUTES, "Mind")}
      ${inputField("icon", "Icon", "*")}
    </div>
    <label class="checkbox-row">
      <input type="checkbox" name="reminderEnabled">
      Daily reminder
    </label>
    ${selectField("reminderTime", "Reminder time", REMINDER_TIMES, "09:00")}
    <button class="primary-button" type="submit">Add habit</button>
  `;
  return form;
}

function habitCard(habit, options) {
  const done = habit.lastCompleted === todayKey();
  const element = card(done ? "is-soft" : "", `
    <div class="row">
      <div class="grow">
        <h2>${escapeHtml(habit.icon)} ${escapeHtml(habit.name)}</h2>
        <p>${escapeHtml(options.compact ? habit.tinyAction : habit.identity)}</p>
      </div>
      <span class="pill inline-primary">${escapeHtml(habit.attribute)}</span>
    </div>
    ${options.compact ? "" : `
      <p><strong>Cue:</strong> ${escapeHtml(habit.cue)}</p>
      <p><strong>Tiny action:</strong> ${escapeHtml(habit.tinyAction)}</p>
      <p><strong>Reward:</strong> ${escapeHtml(habit.reward)}</p>
      <p><strong>Reminder:</strong> ${habit.reminderEnabled ? `Daily at ${habit.reminderTime}` : "Off"}</p>
    `}
    <div class="grid-3 margin-top">
      <div class="mini-stat"><strong>${habit.streak}d</strong><span>streak</span></div>
      <div class="mini-stat"><strong>${habit.bestStreak}d</strong><span>best</span></div>
      <div class="mini-stat"><strong>${habit.completions}</strong><span>votes</span></div>
    </div>
    <div class="button-row">
      <button class="${done ? "secondary-button" : "primary-button"}" data-action="complete-habit" data-id="${habit.id}" ${done ? "disabled" : ""}>${done ? "Done today" : "Complete"}</button>
      <button class="danger-button" data-action="delete-habit" data-id="${habit.id}">Delete</button>
    </div>
  `);
  return element;
}

function renderRewards() {
  const content = viewShell("Rewards", "Spend coins on real-life rewards and keep the life game satisfying.");
  content.appendChild(card("is-accent", `
    <div class="row">
      <div class="grow">
        <h2>${state.coins} coins, ${state.gems} gems</h2>
        <p>Companion level ${monsterLevel()}, ${state.inventory.length} inventory items.</p>
      </div>
      <span class="pill inline-primary">${lifeTitle()}</span>
    </div>
  `));

  const rewardForm = document.createElement("form");
  rewardForm.className = "card form";
  rewardForm.dataset.form = "reward";
  rewardForm.innerHTML = `
    <h2>Add reward</h2>
    ${inputField("title", "Reward", "Guilt-free game session")}
    ${inputField("cost", "Coin cost", "75", "number")}
    <button class="primary-button" type="submit">Add reward</button>
  `;
  content.appendChild(rewardForm);

  state.rewards.forEach((reward) => {
    content.appendChild(card("", `
      <div class="row">
        <div class="grow">
          <h3>${escapeHtml(reward.title)}</h3>
          <p>${reward.claimedCount} claimed</p>
        </div>
        <span class="pill inline-accent">${reward.cost} coins</span>
      </div>
      <div class="button-row">
        <button class="${state.coins >= reward.cost ? "primary-button" : "secondary-button"}" data-action="claim-reward" data-id="${reward.id}" ${state.coins >= reward.cost ? "" : "disabled"}>${state.coins >= reward.cost ? "Claim reward" : "Need coins"}</button>
        <button class="danger-button" data-action="delete-reward" data-id="${reward.id}">Delete</button>
      </div>
    `));
  });

  content.appendChild(themeCard());
  content.appendChild(backupCard());
  mount(content);
}

function themeCard() {
  const panel = document.createElement("div");
  panel.className = "card form";
  panel.innerHTML = `
    <h2>Appearance</h2>
    <div class="swatch-row">
      ${THEME_PRESETS.map((preset) => `<button type="button" class="swatch ${preset.name === state.theme.name ? "is-selected" : ""}" style="background:${preset.primary}" data-action="theme-preset" data-theme="${preset.name}">${preset.name}</button>`).join("")}
    </div>
    <div class="grid-3">
      ${inputField("themePrimary", "Primary", state.theme.primary)}
      ${inputField("themeAccent", "Accent", state.theme.accent)}
      ${inputField("themeBackground", "Base", state.theme.background)}
    </div>
    <button class="primary-button" data-action="save-theme" type="button">Save custom colors</button>
  `;
  return panel;
}

function backupCard() {
  const panel = document.createElement("div");
  panel.className = "card form";
  panel.innerHTML = `
    <h2>Backup</h2>
    <p>Exports include habits, moods, rewards, theme, reminders, timer state, coins, and companion progress.</p>
    <div class="button-row">
      <button class="primary-button" data-action="export-backup" type="button">Export JSON</button>
      <button class="secondary-button" data-action="reset-demo" type="button">Reset data</button>
    </div>
    <input class="file-input" type="file" accept="application/json,.json" data-action="import-backup">
  `;
  return panel;
}

function mount(content) {
  const root = document.getElementById("content");
  root.replaceChildren(content);
  root.querySelectorAll("[data-action]").forEach((element) => {
    if (element.matches("input[type='file']")) {
      element.addEventListener("change", handleFileImport);
    } else {
      element.addEventListener("click", handleAction);
    }
  });
  root.querySelectorAll("form[data-form]").forEach((form) => form.addEventListener("submit", handleForm));
  root.querySelectorAll(".inline-primary").forEach((node) => {
    node.style.background = state.theme.primary;
    node.style.color = readableTextColor(state.theme.primary);
  });
  root.querySelectorAll(".inline-accent").forEach((node) => {
    node.style.background = state.theme.accent;
    node.style.color = readableTextColor(state.theme.accent);
  });
}

async function handleAction(event) {
  const action = event.currentTarget.dataset.action;
  const id = event.currentTarget.dataset.id;
  if (action === "open-rewards") {
    currentView = "rewards";
    render();
  } else if (action === "open-habits") {
    currentView = "habits";
    render();
  } else if (action === "complete-next") {
    const next = firstIncompleteHabit();
    if (next) {
      await completeHabit(next.id);
    }
  } else if (action === "complete-habit") {
    await completeHabit(id);
  } else if (action === "delete-habit") {
    state.habits = state.habits.filter((habit) => habit.id !== id);
    await persist("Habit deleted");
  } else if (action === "start-focus") {
    currentView = "timer";
    await startTimer(0);
  } else if (action === "start-timer") {
    await startTimer(state.timer.mode);
  } else if (action === "pause-timer") {
    await pauseTimer();
  } else if (action === "reset-timer") {
    await resetTimer();
  } else if (action === "timer-mode") {
    await selectTimerMode(Number(event.currentTarget.dataset.mode));
  } else if (action === "claim-reward") {
    await claimReward(id);
  } else if (action === "delete-reward") {
    state.rewards = state.rewards.filter((reward) => reward.id !== id);
    await persist("Reward deleted");
  } else if (action === "theme-preset") {
    const preset = THEME_PRESETS.find((item) => item.name === event.currentTarget.dataset.theme);
    if (preset) {
      state.theme = { ...preset };
      await persist(`${preset.name} colors applied`);
    }
  } else if (action === "save-theme") {
    await saveCustomTheme();
  } else if (action === "export-backup") {
    exportBackup();
  } else if (action === "reset-demo") {
    state = defaultState();
    await persist("Data reset");
  }
}

async function handleForm(event) {
  event.preventDefault();
  const form = event.currentTarget;
  const data = new FormData(form);
  if (form.dataset.form === "habit") {
    const name = String(data.get("name") || "").trim();
    if (!name) {
      showToast("Habit name required");
      return;
    }
    state.habits.push({
      id: crypto.randomUUID(),
      name,
      icon: fallback(data.get("icon"), "*"),
      cue: fallback(data.get("cue"), "After an existing routine"),
      tinyAction: fallback(data.get("tinyAction"), "do the 2-minute version"),
      identity: fallback(data.get("identity"), "I keep promises to myself."),
      reward: fallback(data.get("reward"), "Pause and enjoy the win"),
      attribute: fallback(data.get("attribute"), "Mind"),
      lastCompleted: "",
      reminderEnabled: data.get("reminderEnabled") === "on",
      reminderTime: fallback(data.get("reminderTime"), "09:00"),
      streak: 0,
      bestStreak: 0,
      completions: 0,
      xp: 8,
      coins: 5
    });
    await persist("Habit added");
  } else if (form.dataset.form === "mood") {
    await saveMood(data);
  } else if (form.dataset.form === "reward") {
    const title = String(data.get("title") || "").trim();
    if (!title) {
      showToast("Reward title required");
      return;
    }
    state.rewards.push({
      id: crypto.randomUUID(),
      title,
      cost: Math.max(1, Number.parseInt(data.get("cost"), 10) || 40),
      claimedCount: 0
    });
    await persist("Reward added");
  }
}

async function completeHabit(id) {
  const habit = state.habits.find((item) => item.id === id);
  if (!habit || habit.lastCompleted === todayKey()) {
    return;
  }
  const previousCompletion = habit.lastCompleted;
  habit.lastCompleted = todayKey();
  habit.streak = previousCompletion === yesterdayKey() ? habit.streak + 1 : 1;
  habit.bestStreak = Math.max(habit.bestStreak, habit.streak);
  habit.completions += 1;
  state.totalXp += habit.xp;
  state.coins += habit.coins;
  state.monsterXp += 8;
  state.monsterBond = Math.min(100, state.monsterBond + 3);
  maybeAwardGem();
  await persist(`+${habit.xp} XP, +${habit.coins} coins`);
}

async function saveMood(data) {
  const entry = {
    date: todayKey(),
    mood: Number.parseInt(String(data.get("mood")).slice(0, 1), 10) || 3,
    energy: Number.parseInt(data.get("energy"), 10) || 3,
    stress: Number.parseInt(data.get("stress"), 10) || 3,
    note: String(data.get("note") || "").trim()
  };
  const index = state.moodEntries.findIndex((item) => item.date === todayKey());
  if (index >= 0) {
    state.moodEntries[index] = entry;
    await persist("Mood updated");
  } else {
    state.moodEntries.push(entry);
    state.totalXp += 8;
    state.coins += 5;
    state.monsterXp += 6;
    await persist("+8 XP, +5 coins");
  }
}

async function startTimer(mode) {
  settleTimerDay();
  state.timer.mode = mode;
  const duration = TIMER_MODES[mode].durationMs;
  const remaining = state.timer.remainingMs > 0 && state.timer.remainingMs <= duration ? state.timer.remainingMs : duration;
  state.timer.durationMs = duration;
  state.timer.remainingMs = remaining;
  state.timer.endAt = Date.now() + remaining;
  state.timer.running = true;
  state.timer.sessionId = crypto.randomUUID();
  await persist("Timer started");
}

async function pauseTimer() {
  state.timer.remainingMs = timerRemainingMs();
  state.timer.running = false;
  state.timer.endAt = 0;
  state.timer.sessionId = "";
  await persist("Timer paused");
}

async function resetTimer() {
  state.timer.durationMs = TIMER_MODES[state.timer.mode].durationMs;
  state.timer.remainingMs = state.timer.durationMs;
  state.timer.running = false;
  state.timer.endAt = 0;
  state.timer.sessionId = "";
  await persist("Timer reset");
}

async function completeTimer() {
  if (!state.timer.running) {
    return;
  }
  const wasFocus = state.timer.mode === 0;
  state.timer.running = false;
  state.timer.remainingMs = 0;
  state.timer.endAt = 0;
  state.timer.sessionId = "";
  settleTimerDay();
  if (wasFocus) {
    state.totalXp += 18;
    state.coins += 12;
    state.monsterXp += 12;
    state.timer.focusSessions += 1;
    state.timer.sessionsToday += 1;
    state.timer.minutesToday += 25;
  } else {
    state.timer.breakSessions += 1;
  }
  await persist(wasFocus ? "+18 XP, +12 coins" : "Break complete");
}

async function selectTimerMode(mode) {
  state.timer.mode = mode;
  state.timer.durationMs = TIMER_MODES[mode].durationMs;
  state.timer.remainingMs = state.timer.durationMs;
  state.timer.running = false;
  state.timer.endAt = 0;
  state.timer.sessionId = "";
  await persist("Timer mode changed");
}

async function claimReward(id) {
  const reward = state.rewards.find((item) => item.id === id);
  if (!reward || state.coins < reward.cost) {
    return;
  }
  state.coins -= reward.cost;
  reward.claimedCount += 1;
  state.inventory.push(`${todayKey()}: ${reward.title}`);
  state.monsterBond = Math.min(100, state.monsterBond + 5);
  await persist("Reward claimed");
}

async function saveCustomTheme() {
  const primary = document.querySelector("[name='themePrimary']").value.trim();
  const accent = document.querySelector("[name='themeAccent']").value.trim();
  const background = document.querySelector("[name='themeBackground']").value.trim();
  if (![primary, accent, background].every(validHex)) {
    showToast("Use #RRGGBB colors");
    return;
  }
  if (relativeLuminance(background) < 0.72) {
    showToast("Use a light background");
    return;
  }
  state.theme = {
    name: "Custom",
    primary: primary.toUpperCase(),
    accent: accent.toUpperCase(),
    background: background.toUpperCase()
  };
  await persist("Custom colors saved");
}

async function handleFileImport(event) {
  const file = event.currentTarget.files && event.currentTarget.files[0];
  if (!file) {
    return;
  }
  try {
    const text = await file.text();
    const parsed = JSON.parse(text);
    state = normalizeImportedState(parsed);
    await persist("Backup imported");
  } catch (error) {
    showToast("Invalid backup JSON");
  }
}

function exportBackup() {
  const backup = {
    schema: "mylifepal.chrome.backup",
    schemaVersion: 1,
    appName: "MyLifePal",
    exportedAt: new Date().toISOString(),
    state
  };
  const blob = new Blob([JSON.stringify(backup, null, 2)], { type: "application/json" });
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = `mylifepal-chrome-backup-${todayKey()}.json`;
  link.click();
  URL.revokeObjectURL(url);
}

async function persist(message) {
  state = normalizeState(state);
  await saveState(state);
  await notifyBackground();
  render();
  showToast(message);
}

async function notifyBackground() {
  if (!chromeAvailable()) {
    return;
  }
  try {
    await chrome.runtime.sendMessage({ type: "MYLIFEPAL_STATE_CHANGED" });
  } catch (error) {
    // The popup stays usable even if the service worker is restarting.
  }
}

async function loadState() {
  if (!chromeAvailable()) {
    const raw = localStorage.getItem(STORAGE_KEY);
    return raw ? normalizeState(JSON.parse(raw)) : defaultState();
  }
  const result = await chrome.storage.local.get(STORAGE_KEY);
  if (result[STORAGE_KEY]) {
    return normalizeState(result[STORAGE_KEY]);
  }
  const value = defaultState();
  await chrome.storage.local.set({ [STORAGE_KEY]: value });
  return value;
}

async function saveState(value) {
  if (!chromeAvailable()) {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(value));
    return;
  }
  await chrome.storage.local.set({ [STORAGE_KEY]: value });
}

function chromeAvailable() {
  return typeof chrome !== "undefined" && chrome.storage && chrome.storage.local;
}

function normalizeImportedState(parsed) {
  const payload = parsed && parsed.state ? parsed.state : parsed;
  if (payload && payload.timerDurationMillis !== undefined) {
    return normalizeState(fromAndroidState(payload));
  }
  return normalizeState(payload);
}

function fromAndroidState(androidState) {
  return {
    schemaVersion: 1,
    totalXp: androidState.totalXp || 0,
    coins: androidState.coins || 0,
    gems: androidState.gems || 0,
    monsterXp: androidState.monsterXp || 0,
    monsterBond: androidState.monsterBond || 0,
    inventory: Array.isArray(androidState.inventory) ? androidState.inventory : [],
    claimedQuestKeys: Array.isArray(androidState.claimedQuestKeys) ? androidState.claimedQuestKeys : [],
    theme: {
      name: androidState.themeName || "Imported",
      primary: intColorToHex(androidState.themePrimary, "#2E7D68"),
      accent: intColorToHex(androidState.themeAccent, "#F9C74F"),
      background: intColorToHex(androidState.themeBackground, "#F5F7F1")
    },
    timer: {
      mode: androidState.timerMode || 0,
      durationMs: androidState.timerDurationMillis || 25 * 60 * 1000,
      remainingMs: androidState.timerRemainingMillis || 25 * 60 * 1000,
      endAt: androidState.timerEndAtMillis || 0,
      running: Boolean(androidState.timerRunning),
      sessionId: "",
      focusSessions: androidState.tomatoFocusSessions || 0,
      breakSessions: androidState.tomatoBreakSessions || 0,
      sessionsToday: androidState.tomatoSessionsToday || 0,
      minutesToday: androidState.tomatoMinutesToday || 0,
      lastDate: androidState.tomatoLastDate || todayKey()
    },
    habits: Array.isArray(androidState.habits) ? androidState.habits : [],
    rewards: Array.isArray(androidState.rewards) ? androidState.rewards : [],
    moodEntries: Array.isArray(androidState.moodEntries) ? androidState.moodEntries : []
  };
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
    theme: { ...THEME_PRESETS[0] },
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
  const mode = Math.max(0, Math.min(2, numberOr(value.mode, 0)));
  return {
    mode,
    durationMs: numberOr(value.durationMs, TIMER_MODES[mode].durationMs),
    remainingMs: numberOr(value.remainingMs, TIMER_MODES[mode].durationMs),
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

function applyTheme() {
  const root = document.documentElement;
  root.style.setProperty("--primary", state.theme.primary);
  root.style.setProperty("--accent", state.theme.accent);
  root.style.setProperty("--bg", state.theme.background);
  root.style.setProperty("--primary-soft", mix(state.theme.primary, "#FFFFFF", 0.86));
  root.style.setProperty("--accent-soft", mix(state.theme.accent, "#FFFFFF", 0.78));
}

function viewShell(title, copy) {
  const content = document.createElement("div");
  content.innerHTML = `
    <h2 class="section-title">${escapeHtml(title)}</h2>
    <p class="section-copy">${escapeHtml(copy)}</p>
  `;
  return content;
}

function card(extraClass, html) {
  const element = document.createElement("article");
  element.className = ["card", extraClass].filter(Boolean).join(" ");
  element.innerHTML = html;
  return element;
}

function emptyCard(message) {
  return card("", `<div class="empty">${escapeHtml(message)}</div>`);
}

function inputField(name, label, value, type = "text") {
  return `
    <div class="field">
      <label for="${name}">${label}</label>
      <input id="${name}" name="${name}" type="${type}" value="${escapeHtml(value)}">
    </div>
  `;
}

function selectField(name, label, options, selected) {
  const items = options.map((option) => {
    const value = String(option).split(" ")[0];
    const isSelected = value === selected || option === selected;
    return `<option value="${escapeHtml(value)}" ${isSelected ? "selected" : ""}>${escapeHtml(option)}</option>`;
  }).join("");
  return `
    <div class="field">
      <label for="${name}">${label}</label>
      <select id="${name}" name="${name}">${items}</select>
    </div>
  `;
}

function timerRemainingMs() {
  if (state.timer.running) {
    return Math.max(0, state.timer.endAt - Date.now());
  }
  return Math.max(0, state.timer.remainingMs);
}

function formatTimer(ms) {
  const totalSeconds = Math.max(0, Math.ceil(ms / 1000));
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  return `${String(minutes).padStart(2, "0")}:${String(seconds).padStart(2, "0")}`;
}

function firstIncompleteHabit() {
  return state.habits.find((habit) => habit.lastCompleted !== todayKey()) || null;
}

function completedTodayCount() {
  return state.habits.filter((habit) => habit.lastCompleted === todayKey()).length;
}

function moodForToday() {
  return state.moodEntries.find((entry) => entry.date === todayKey()) || null;
}

function level() {
  return Math.floor(state.totalXp / 100) + 1;
}

function monsterLevel() {
  return Math.floor(state.monsterXp / 90) + 1;
}

function identityLine() {
  if (level() >= 8) {
    return "Epic routine builder.";
  }
  if (state.habits.length && completedTodayCount() === state.habits.length) {
    return "Today is clear. Nice and clean.";
  }
  return "Tiny habits. Real-life levels.";
}

function lifeTitle() {
  if (level() >= 8) {
    return "Epic";
  }
  if (level() >= 5) {
    return "Rare";
  }
  return "Starter";
}

function coachTitle(next) {
  if (!state.habits.length) {
    return "Create one tiny loop";
  }
  if (!next) {
    return "Claim the win";
  }
  const mood = moodForToday();
  if (mood && mood.stress >= 4 && mood.energy <= 2) {
    return "Use the smallest version";
  }
  return "Next useful action";
}

function moodLabel(value) {
  if (value <= 1) return "Rough";
  if (value === 2) return "Low";
  if (value === 3) return "Okay";
  if (value === 4) return "Good";
  return "Great";
}

function settleTimerDay() {
  if (state.timer.lastDate !== todayKey()) {
    state.timer.lastDate = todayKey();
    state.timer.sessionsToday = 0;
    state.timer.minutesToday = 0;
  }
}

function maybeAwardGem() {
  if (state.habits.length && completedTodayCount() === state.habits.length) {
    const key = `clear:${todayKey()}`;
    if (!state.claimedQuestKeys.includes(key)) {
      state.claimedQuestKeys.push(key);
      state.gems += 1;
      state.inventory.push(`${todayKey()}: Clear-day gem`);
    }
  }
}

function normalizeReminderTime(value) {
  return /^([01]\d|2[0-3]):[0-5]\d$/.test(value || "") ? value : "09:00";
}

function validHex(value) {
  return /^#[0-9A-Fa-f]{6}$/.test(value || "");
}

function intColorToHex(value, fallback) {
  const number = Number(value);
  if (!Number.isFinite(number)) {
    return fallback;
  }
  const unsigned = number >>> 0;
  return `#${(unsigned & 0xFFFFFF).toString(16).padStart(6, "0").toUpperCase()}`;
}

function readableTextColor(hex) {
  return relativeLuminance(hex) > 0.54 ? "#17201B" : "#FFFFFF";
}

function mix(hex, target, amount) {
  const from = hexToRgb(hex);
  const to = hexToRgb(target);
  const ratio = clamp(amount, 0, 1);
  return rgbToHex({
    r: Math.round(from.r + (to.r - from.r) * ratio),
    g: Math.round(from.g + (to.g - from.g) * ratio),
    b: Math.round(from.b + (to.b - from.b) * ratio)
  });
}

function relativeLuminance(hex) {
  const color = hexToRgb(hex);
  const r = linearChannel(color.r / 255);
  const g = linearChannel(color.g / 255);
  const b = linearChannel(color.b / 255);
  return 0.2126 * r + 0.7152 * g + 0.0722 * b;
}

function linearChannel(value) {
  return value <= 0.03928 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
}

function hexToRgb(hex) {
  const normalized = validHex(hex) ? hex.slice(1) : "2E7D68";
  return {
    r: Number.parseInt(normalized.slice(0, 2), 16),
    g: Number.parseInt(normalized.slice(2, 4), 16),
    b: Number.parseInt(normalized.slice(4, 6), 16)
  };
}

function rgbToHex(color) {
  return `#${[color.r, color.g, color.b].map((value) => value.toString(16).padStart(2, "0")).join("").toUpperCase()}`;
}

function todayKey() {
  return localDateKey(new Date());
}

function yesterdayKey() {
  const date = new Date();
  date.setDate(date.getDate() - 1);
  return localDateKey(date);
}

function localDateKey(date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function numberOr(value, fallback) {
  const number = Number(value);
  return Number.isFinite(number) ? number : fallback;
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value));
}

function fallback(value, defaultValue) {
  const text = String(value || "").trim();
  return text || defaultValue;
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#039;");
}

function showToast(message) {
  const template = document.getElementById("toastTemplate");
  const toast = template.content.firstElementChild.cloneNode(true);
  toast.textContent = message;
  document.getElementById("toastHost").appendChild(toast);
  setTimeout(() => toast.remove(), 2400);
}
