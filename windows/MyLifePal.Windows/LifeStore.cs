using System.Globalization;
using System.IO;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace MyLifePal.Windows;

public sealed class LifeStore
{
    public static readonly string[] Attributes = ["Mind", "Body", "Craft", "Home", "Social"];
    public static readonly string[] ReminderTimes = ["07:00", "08:00", "09:00", "12:30", "17:30", "19:30", "21:30"];
    public static readonly ThemeChoice[] ThemePresets =
    [
        new() { Name = "Forest", Primary = "#2E7D68", Accent = "#F9C74F", Background = "#F5F7F1" },
        new() { Name = "Ocean", Primary = "#1F6997", Accent = "#42BEB6", Background = "#F1F7F9" },
        new() { Name = "Berry", Primary = "#814584", Accent = "#EA7E65", Background = "#F9F4F8" },
        new() { Name = "Sunrise", Primary = "#B65C38", Accent = "#F5B84A", Background = "#FAF6F0" },
        new() { Name = "Graphite", Primary = "#4B5B69", Accent = "#51A39A", Background = "#F5F6F7" },
        new() { Name = "Rose", Primary = "#AF4B6A", Accent = "#4E9C85", Background = "#FBF5F7" }
    ];

    private readonly JsonSerializerOptions _jsonOptions = new()
    {
        WriteIndented = true,
        PropertyNameCaseInsensitive = true
    };
    private readonly HashSet<string> _shownReminderKeys = [];

    public event Action? Changed;
    public LifeState State { get; private set; } = new();
    public string LastMessage { get; private set; } = "";
    public string DataDirectory { get; }
    public string StatePath { get; }

    public LifeStore()
    {
        DataDirectory = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData), "MyLifePal");
        Directory.CreateDirectory(DataDirectory);
        StatePath = Path.Combine(DataDirectory, "mylifepal-windows-state.json");
        Load();
    }

    public string Today => DateTime.Now.ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
    public int Level => State.TotalXp / 100 + 1;
    public int XpIntoLevel => State.TotalXp % 100;
    public int CompanionLevel => State.MonsterXp / 90 + 1;
    public string CompanionStage => CompanionLevel >= 8 ? "Mythic" : CompanionLevel >= 5 ? "Guardian" : CompanionLevel >= 3 ? "Bloomling" : "Hatchling";
    public int CompletedToday => State.Habits.Count(IsCompletedToday);
    public LifeHabit? NextHabit => State.Habits.FirstOrDefault(habit => !IsCompletedToday(habit));
    public MoodEntry? TodayMood => State.MoodEntries.LastOrDefault(entry => entry.Date == Today);
    public bool SecurityEnabled => State.SecurityEnabled && State.PasswordSalt.Length > 0 && State.PasswordHash.Length > 0;
    public long TimerRemainingMs => State.TimerRunning ? Math.Max(0, State.TimerEndAtUnixMs - DateTimeOffset.Now.ToUnixTimeMilliseconds()) : Math.Max(0, State.TimerRemainingMs);
    public double TimerProgress => 1d - TimerRemainingMs / (double)Math.Max(1, State.TimerDurationMs);
    public string TimerDisplay
    {
        get
        {
            var totalSeconds = (int)Math.Ceiling(TimerRemainingMs / 1000d);
            return $"{totalSeconds / 60:00}:{totalSeconds % 60:00}";
        }
    }

    public string IdentityLine
    {
        get
        {
            if (State.Habits.Count == 0) return "Create one tiny loop to start.";
            if (CompletedToday == State.Habits.Count) return "Today is clear. Your votes are in.";
            return "Tiny habits. Real-life levels.";
        }
    }

    public bool IsCompletedToday(LifeHabit habit) => habit.LastCompleted == Today;

    public void CompleteHabit(LifeHabit habit)
    {
        var target = State.Habits.FirstOrDefault(item => item.Id == habit.Id);
        if (target == null || IsCompletedToday(target)) return;

        var yesterday = DateTime.Now.AddDays(-1).ToString("yyyy-MM-dd", CultureInfo.InvariantCulture);
        var continued = target.LastCompleted == yesterday;
        target.Streak = continued ? target.Streak + 1 : 1;
        target.BestStreak = Math.Max(target.BestStreak, target.Streak);
        target.LastCompleted = Today;
        target.Completions += 1;

        var xpGain = target.Xp + Math.Min(10, target.Streak * 2);
        State.TotalXp += xpGain;
        State.Coins += target.Coins;
        AddAttributeXp(target.Attribute, xpGain);
        GrowCompanion(8);
        MaybeAwardClearDayGem();
        Save($"+{xpGain} XP, +{target.Coins} coins");
    }

    public void AddHabit(LifeHabit habit)
    {
        habit.Id = Guid.NewGuid().ToString();
        habit.Name = Fallback(habit.Name, "Tiny habit");
        habit.Icon = Fallback(habit.Icon, "*");
        habit.Cue = Fallback(habit.Cue, "After an existing routine");
        habit.TinyAction = Fallback(habit.TinyAction, "do the 2-minute version");
        habit.Identity = Fallback(habit.Identity, "I keep promises to myself.");
        habit.Reward = Fallback(habit.Reward, "Pause and enjoy the win");
        habit.Attribute = Attributes.Contains(habit.Attribute) ? habit.Attribute : "Mind";
        habit.ReminderTime = NormalizeReminderTime(habit.ReminderTime);
        State.Habits.Add(habit);
        Save("Habit added");
    }

    public void DeleteHabit(LifeHabit habit)
    {
        State.Habits.RemoveAll(item => item.Id == habit.Id);
        Save("Habit deleted");
    }

    public void AddReward(LifeReward reward)
    {
        reward.Id = Guid.NewGuid().ToString();
        reward.Title = Fallback(reward.Title, "Reward");
        reward.Cost = Math.Max(1, reward.Cost);
        State.Rewards.Add(reward);
        Save("Reward added");
    }

    public void ClaimReward(LifeReward reward)
    {
        var target = State.Rewards.FirstOrDefault(item => item.Id == reward.Id);
        if (target == null || State.Coins < target.Cost) return;
        State.Coins -= target.Cost;
        target.ClaimedCount += 1;
        State.RewardClaimsToday += 1;
        State.RewardLastDate = Today;
        State.Inventory.Add($"{Today}: {target.Title}");
        State.MonsterBond = Math.Min(100, State.MonsterBond + 5);
        Save("Reward claimed");
    }

    public void DeleteReward(LifeReward reward)
    {
        State.Rewards.RemoveAll(item => item.Id == reward.Id);
        Save("Reward deleted");
    }

    public void RecordMood(int mood, int energy, int stress, string note)
    {
        var entry = new MoodEntry
        {
            Date = Today,
            Mood = Clamp(mood),
            Energy = Clamp(energy),
            Stress = Clamp(stress),
            Note = note.Trim()
        };

        var existing = State.MoodEntries.FindIndex(item => item.Date == Today);
        if (existing >= 0)
        {
            State.MoodEntries[existing] = entry;
            Save("Mood updated");
        }
        else
        {
            State.MoodEntries.Add(entry);
            State.TotalXp += 8;
            State.Coins += 5;
            AddAttributeXp("Mind", 8);
            GrowCompanion(6);
            Save("+8 XP, +5 coins");
        }
    }

    public void StartTimer(int? mode = null)
    {
        EnsureTimerDay();
        if (mode.HasValue)
        {
            State.TimerMode = mode.Value;
            State.TimerDurationMs = TimerDurationForMode(mode.Value);
            State.TimerRemainingMs = State.TimerDurationMs;
        }
        var remaining = State.TimerRemainingMs > 0 ? State.TimerRemainingMs : TimerDurationForMode(State.TimerMode);
        State.TimerDurationMs = TimerDurationForMode(State.TimerMode);
        State.TimerRemainingMs = remaining;
        State.TimerEndAtUnixMs = DateTimeOffset.Now.ToUnixTimeMilliseconds() + remaining;
        State.TimerRunning = true;
        Save("Timer started");
    }

    public void PauseTimer()
    {
        State.TimerRemainingMs = TimerRemainingMs;
        State.TimerEndAtUnixMs = 0;
        State.TimerRunning = false;
        Save("Timer paused");
    }

    public void ResetTimer()
    {
        State.TimerDurationMs = TimerDurationForMode(State.TimerMode);
        State.TimerRemainingMs = State.TimerDurationMs;
        State.TimerEndAtUnixMs = 0;
        State.TimerRunning = false;
        Save("Timer reset");
    }

    public void SelectTimerMode(int mode)
    {
        State.TimerMode = Math.Clamp(mode, 0, 2);
        State.TimerDurationMs = TimerDurationForMode(State.TimerMode);
        State.TimerRemainingMs = State.TimerDurationMs;
        State.TimerRunning = false;
        State.TimerEndAtUnixMs = 0;
        Save("Timer mode changed");
    }

    public ToastMessage? Tick()
    {
        if (State.TimerRunning && TimerRemainingMs <= 0)
        {
            var focus = State.TimerMode == 0;
            State.TimerRunning = false;
            State.TimerEndAtUnixMs = 0;
            State.TimerRemainingMs = 0;
            EnsureTimerDay();
            if (focus)
            {
                State.TomatoFocusSessions += 1;
                State.TomatoSessionsToday += 1;
                State.TomatoMinutesToday += 25;
                State.TotalXp += 18;
                State.Coins += 12;
                AddAttributeXp("Mind", 18);
                GrowCompanion(12);
            }
            else
            {
                State.TomatoBreakSessions += 1;
            }
            Save(focus ? "+18 XP, +12 coins" : "Break complete");
            return new ToastMessage
            {
                Title = focus ? "Focus tomato complete" : "Break complete",
                Body = focus ? "+18 XP, +12 coins, and companion growth." : "Ready for the next tiny action."
            };
        }

        return null;
    }

    public List<ToastMessage> DueReminderMessages()
    {
        var messages = new List<ToastMessage>();
        var now = DateTime.Now.ToString("HH:mm", CultureInfo.InvariantCulture);
        foreach (var habit in State.Habits)
        {
            if (!habit.ReminderEnabled || IsCompletedToday(habit) || NormalizeReminderTime(habit.ReminderTime) != now) continue;
            var key = $"{Today}:{habit.Id}:{now}";
            if (!_shownReminderKeys.Add(key)) continue;
            messages.Add(new ToastMessage
            {
                Title = "MyLifePal reminder",
                Body = $"{habit.Cue}: {habit.TinyAction}"
            });
        }
        return messages;
    }

    public void ApplyTheme(ThemeChoice theme)
    {
        State.Theme = theme.Clone();
        Save($"{theme.Name} colors applied");
    }

    public void SetSecurityPassword(string password)
    {
        var trimmed = password.Trim();
        if (trimmed.Length < 4) throw new ArgumentException("Password must be at least 4 characters.");
        var salt = RandomNumberGenerator.GetBytes(16);
        State.PasswordSalt = Convert.ToBase64String(salt);
        State.PasswordHash = HashPassword(trimmed, State.PasswordSalt);
        State.SecurityEnabled = true;
        Save("Security password enabled");
    }

    public void ClearSecurityPassword()
    {
        State.SecurityEnabled = false;
        State.PasswordSalt = "";
        State.PasswordHash = "";
        Save("Security password disabled");
    }

    public bool VerifySecurityPassword(string password)
    {
        if (!SecurityEnabled) return true;
        if (string.IsNullOrWhiteSpace(password)) return false;
        try
        {
            var expected = Convert.FromBase64String(State.PasswordHash);
            var actual = Convert.FromBase64String(HashPassword(password.Trim(), State.PasswordSalt));
            return CryptographicOperations.FixedTimeEquals(expected, actual);
        }
        catch
        {
            return false;
        }
    }

    public bool SaveCustomTheme(string primary, string accent, string background)
    {
        if (!IsHexColor(primary) || !IsHexColor(accent) || !IsHexColor(background))
        {
            LastMessage = "Use #RRGGBB colors";
            Changed?.Invoke();
            return false;
        }

        State.Theme = new ThemeChoice
        {
            Name = "Custom",
            Primary = primary.ToUpperInvariant(),
            Accent = accent.ToUpperInvariant(),
            Background = background.ToUpperInvariant()
        };
        Save("Custom colors saved");
        return true;
    }

    public string ExportBackupJson()
    {
        var envelope = new BackupEnvelope
        {
            ExportedAt = DateTimeOffset.Now.ToString("O"),
            State = State
        };
        return JsonSerializer.Serialize(envelope, _jsonOptions);
    }

    public void ImportBackupJson(string json)
    {
        var node = JsonNode.Parse(json) as JsonObject ?? throw new InvalidDataException("Invalid backup JSON");
        var wrappedState = node["State"] as JsonObject ?? node["state"] as JsonObject;
        var payload = wrappedState ?? node;
        if (payload["timerDurationMillis"] != null || payload["themePrimary"] != null)
        {
            State = FromAndroidPayload(payload);
        }
        else if (wrappedState != null)
        {
            var envelope = node.Deserialize<BackupEnvelope>(_jsonOptions);
            State = envelope?.State ?? wrappedState.Deserialize<LifeState>(_jsonOptions) ?? throw new InvalidDataException("Invalid backup JSON");
        }
        else
        {
            State = payload.Deserialize<LifeState>(_jsonOptions) ?? throw new InvalidDataException("Invalid backup JSON");
        }
        State = Normalize(State);
        Save("Backup imported");
    }

    public void RevealDataFolder()
    {
        System.Diagnostics.Process.Start(new System.Diagnostics.ProcessStartInfo
        {
            FileName = DataDirectory,
            UseShellExecute = true
        });
    }

    private void Load()
    {
        if (!File.Exists(StatePath))
        {
            State = DefaultState();
            Save("");
            return;
        }

        try
        {
            var json = File.ReadAllText(StatePath);
            State = Normalize(JsonSerializer.Deserialize<LifeState>(json, _jsonOptions) ?? DefaultState());
            SyncTimerOnLoad();
        }
        catch
        {
            State = DefaultState();
            Save("");
        }
    }

    private void Save(string message)
    {
        State = Normalize(State);
        File.WriteAllText(StatePath, JsonSerializer.Serialize(State, _jsonOptions));
        LastMessage = message;
        Changed?.Invoke();
    }

    private void SyncTimerOnLoad()
    {
        if (!State.TimerRunning) return;
        if (TimerRemainingMs <= 0)
        {
            Tick();
        }
        else
        {
            State.TimerRemainingMs = TimerRemainingMs;
        }
    }

    private void EnsureTimerDay()
    {
        if (State.TomatoLastDate != Today)
        {
            State.TomatoLastDate = Today;
            State.TomatoSessionsToday = 0;
            State.TomatoMinutesToday = 0;
        }
        if (State.RewardLastDate != Today)
        {
            State.RewardLastDate = Today;
            State.RewardClaimsToday = 0;
        }
    }

    private void MaybeAwardClearDayGem()
    {
        if (State.Habits.Count == 0 || CompletedToday != State.Habits.Count) return;
        var key = $"clear:{Today}";
        if (State.ClaimedQuestKeys.Contains(key)) return;
        State.ClaimedQuestKeys.Add(key);
        State.Gems += 1;
        State.Inventory.Add($"{Today}: Clear-day gem");
    }

    private void AddAttributeXp(string attribute, int amount)
    {
        if (!State.AttributeXp.ContainsKey(attribute)) State.AttributeXp[attribute] = 0;
        State.AttributeXp[attribute] += amount;
    }

    private void GrowCompanion(int amount)
    {
        State.MonsterXp += amount;
        State.MonsterBond = Math.Min(100, State.MonsterBond + Math.Max(2, amount / 5));
    }

    private LifeState Normalize(LifeState value)
    {
        value.Theme ??= ThemePresets[0].Clone();
        value.Habits ??= [];
        value.Rewards ??= [];
        value.MoodEntries ??= [];
        value.Inventory ??= [];
        value.ClaimedQuestKeys ??= [];
        value.AttributeXp ??= [];
        if (!IsHexColor(value.Theme.Primary)) value.Theme.Primary = ThemePresets[0].Primary;
        if (!IsHexColor(value.Theme.Accent)) value.Theme.Accent = ThemePresets[0].Accent;
        if (!IsHexColor(value.Theme.Background)) value.Theme.Background = ThemePresets[0].Background;
        if (string.IsNullOrWhiteSpace(value.PasswordSalt) || string.IsNullOrWhiteSpace(value.PasswordHash))
        {
            value.SecurityEnabled = false;
            value.PasswordSalt = "";
            value.PasswordHash = "";
        }
        if (string.IsNullOrWhiteSpace(value.TomatoLastDate)) value.TomatoLastDate = Today;
        if (string.IsNullOrWhiteSpace(value.RewardLastDate)) value.RewardLastDate = Today;
        value.TimerMode = Math.Clamp(value.TimerMode, 0, 2);
        value.TimerDurationMs = TimerDurationForMode(value.TimerMode);
        foreach (var habit in value.Habits)
        {
            habit.ReminderTime = NormalizeReminderTime(habit.ReminderTime);
            habit.Attribute = Attributes.Contains(habit.Attribute) ? habit.Attribute : "Mind";
        }
        foreach (var attribute in Attributes)
        {
            value.AttributeXp.TryAdd(attribute, 0);
        }
        return value;
    }

    private LifeState DefaultState()
    {
        var state = new LifeState
        {
            Habits = DefaultHabits(),
            Rewards = DefaultRewards(),
            TomatoLastDate = Today,
            RewardLastDate = Today
        };
        foreach (var attribute in Attributes) state.AttributeXp[attribute] = 0;
        return state;
    }

    private static List<LifeHabit> DefaultHabits() =>
    [
        new()
        {
            Name = "Hydrate before coffee",
            Cue = "After I start the kettle",
            TinyAction = "drink one glass of water",
            Identity = "I protect my energy.",
            Reward = "Make coffee after the glass is empty",
            Attribute = "Body",
            Xp = 8,
            Coins = 5
        },
        new()
        {
            Name = "Read one page",
            Cue = "After I sit down at night",
            TinyAction = "read one page",
            Identity = "I am a reader, even on busy days.",
            Reward = "Put a coin toward a book reward",
            Attribute = "Mind",
            Xp = 8,
            Coins = 5
        },
        new()
        {
            Name = "One-surface reset",
            Cue = "After dinner",
            TinyAction = "move five items back home",
            Identity = "I make my space easier to live in.",
            Reward = "Make tea or light a candle",
            Attribute = "Home",
            Xp = 14,
            Coins = 9
        }
    ];

    private static List<LifeReward> DefaultRewards() =>
    [
        new() { Title = "Fancy coffee or tea", Cost = 35 },
        new() { Title = "Guilt-free game session", Cost = 75 },
        new() { Title = "Book fund deposit", Cost = 120 }
    ];

    private LifeState FromAndroidPayload(JsonObject payload)
    {
        var state = DefaultState();
        state.TotalXp = ReadInt(payload, "totalXp", state.TotalXp);
        state.Coins = ReadInt(payload, "coins", state.Coins);
        state.Gems = ReadInt(payload, "gems", state.Gems);
        state.MonsterXp = ReadInt(payload, "monsterXp", state.MonsterXp);
        state.MonsterBond = ReadInt(payload, "monsterBond", state.MonsterBond);
        state.SecurityEnabled = payload["securityEnabled"]?.GetValue<bool>() ?? false;
        state.PasswordSalt = ReadString(payload, "passwordSalt", "");
        state.PasswordHash = ReadString(payload, "passwordHash", "");
        state.Theme = new ThemeChoice
        {
            Name = payload["themeName"]?.GetValue<string>() ?? "Imported",
            Primary = IntColorToHex(payload["themePrimary"], state.Theme.Primary),
            Accent = IntColorToHex(payload["themeAccent"], state.Theme.Accent),
            Background = IntColorToHex(payload["themeBackground"], state.Theme.Background)
        };

        if (payload["habits"] is JsonArray habits)
        {
            state.Habits = habits.OfType<JsonObject>().Select(item => new LifeHabit
            {
                Id = ReadString(item, "id", Guid.NewGuid().ToString()),
                Name = ReadString(item, "name", "Tiny habit"),
                Icon = ReadString(item, "icon", "*"),
                Cue = ReadString(item, "cue", ""),
                TinyAction = ReadString(item, "tinyAction", ""),
                Identity = ReadString(item, "identity", ""),
                Reward = ReadString(item, "reward", ""),
                Attribute = ReadString(item, "attribute", "Mind"),
                LastCompleted = ReadString(item, "lastCompleted", ""),
                ReminderEnabled = item["reminderEnabled"]?.GetValue<bool>() ?? false,
                ReminderTime = ReadString(item, "reminderTime", "09:00"),
                Xp = ReadInt(item, "xp", 8),
                Coins = ReadInt(item, "coins", 5),
                Streak = ReadInt(item, "streak", 0),
                BestStreak = ReadInt(item, "bestStreak", 0),
                Completions = ReadInt(item, "completions", 0)
            }).ToList();
        }

        if (payload["rewards"] is JsonArray rewards)
        {
            state.Rewards = rewards.OfType<JsonObject>().Select(item => new LifeReward
            {
                Id = ReadString(item, "id", Guid.NewGuid().ToString()),
                Title = ReadString(item, "title", "Reward"),
                Cost = ReadInt(item, "cost", 40),
                ClaimedCount = ReadInt(item, "claimedCount", 0)
            }).ToList();
        }

        if (payload["moodEntries"] is JsonArray moods)
        {
            state.MoodEntries = moods.OfType<JsonObject>().Select(item => new MoodEntry
            {
                Date = ReadString(item, "date", Today),
                Mood = ReadInt(item, "mood", 3),
                Energy = ReadInt(item, "energy", 3),
                Stress = ReadInt(item, "stress", 3),
                Note = ReadString(item, "note", "")
            }).ToList();
        }

        if (payload["inventory"] is JsonArray inventory)
        {
            state.Inventory = inventory.Select(item => item?.GetValue<string>() ?? "").Where(item => item.Length > 0).ToList();
        }
        return state;
    }

    private static long TimerDurationForMode(int mode) => mode switch
    {
        1 => 5L * 60L * 1000L,
        2 => 15L * 60L * 1000L,
        _ => 25L * 60L * 1000L
    };

    public static string TimerModeName(int mode) => mode switch
    {
        1 => "Short break",
        2 => "Long break",
        _ => "Focus tomato"
    };

    public static string MoodLabel(int mood) => mood switch
    {
        <= 1 => "Rough",
        2 => "Low",
        3 => "Okay",
        4 => "Good",
        _ => "Great"
    };

    private static int Clamp(int value) => Math.Min(5, Math.Max(1, value));

    private static string Fallback(string? value, string fallback)
    {
        var trimmed = value?.Trim() ?? "";
        return trimmed.Length == 0 ? fallback : trimmed;
    }

    private static string NormalizeReminderTime(string? value) => value != null && ReminderTimes.Contains(value) ? value : "09:00";
    private static bool IsHexColor(string? value) => value is { Length: 7 } && value[0] == '#' && value.Skip(1).All(Uri.IsHexDigit);
    private static int ReadInt(JsonObject item, string key, int fallback) => item[key]?.GetValue<int>() ?? fallback;
    private static string ReadString(JsonObject item, string key, string fallback) => item[key]?.GetValue<string>() ?? fallback;

    private static string HashPassword(string password, string salt)
    {
        var saltBytes = Convert.FromBase64String(salt);
        var passwordBytes = Encoding.UTF8.GetBytes(password);
        var payload = new byte[saltBytes.Length + passwordBytes.Length];
        Buffer.BlockCopy(saltBytes, 0, payload, 0, saltBytes.Length);
        Buffer.BlockCopy(passwordBytes, 0, payload, saltBytes.Length, passwordBytes.Length);
        return Convert.ToBase64String(SHA256.HashData(payload));
    }

    private static string IntColorToHex(JsonNode? node, string fallback)
    {
        if (node == null) return fallback;
        var value = node.GetValue<int>() & 0xFFFFFF;
        return $"#{value:X6}";
    }
}
