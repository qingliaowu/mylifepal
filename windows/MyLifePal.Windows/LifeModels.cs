namespace MyLifePal.Windows;

public sealed class LifeState
{
    public int SchemaVersion { get; set; } = 1;
    public int TotalXp { get; set; }
    public int Coins { get; set; } = 25;
    public int Gems { get; set; } = 1;
    public int MonsterXp { get; set; }
    public int MonsterBond { get; set; } = 12;
    public ThemeChoice Theme { get; set; } = LifeStore.ThemePresets[0].Clone();
    public int TimerMode { get; set; }
    public long TimerDurationMs { get; set; } = 25L * 60L * 1000L;
    public long TimerRemainingMs { get; set; } = 25L * 60L * 1000L;
    public long TimerEndAtUnixMs { get; set; }
    public bool TimerRunning { get; set; }
    public int TomatoFocusSessions { get; set; }
    public int TomatoBreakSessions { get; set; }
    public int TomatoSessionsToday { get; set; }
    public int TomatoMinutesToday { get; set; }
    public int RewardClaimsToday { get; set; }
    public string TomatoLastDate { get; set; } = "";
    public string RewardLastDate { get; set; } = "";
    public string MonsterName { get; set; } = "Milo";
    public string MonsterLastCareDate { get; set; } = "";
    public bool SecurityEnabled { get; set; }
    public string PasswordSalt { get; set; } = "";
    public string PasswordHash { get; set; } = "";
    public List<LifeHabit> Habits { get; set; } = [];
    public List<LifeReward> Rewards { get; set; } = [];
    public List<MoodEntry> MoodEntries { get; set; } = [];
    public List<string> Inventory { get; set; } = [];
    public List<string> ClaimedQuestKeys { get; set; } = [];
    public Dictionary<string, int> AttributeXp { get; set; } = [];
}

public sealed class LifeHabit
{
    public string Id { get; set; } = Guid.NewGuid().ToString();
    public string Name { get; set; } = "";
    public string Icon { get; set; } = "*";
    public string Cue { get; set; } = "";
    public string TinyAction { get; set; } = "";
    public string Identity { get; set; } = "";
    public string Reward { get; set; } = "";
    public string Attribute { get; set; } = "Mind";
    public string LastCompleted { get; set; } = "";
    public bool ReminderEnabled { get; set; }
    public string ReminderTime { get; set; } = "09:00";
    public int Xp { get; set; } = 8;
    public int Coins { get; set; } = 5;
    public int Streak { get; set; }
    public int BestStreak { get; set; }
    public int Completions { get; set; }
}

public sealed class LifeReward
{
    public string Id { get; set; } = Guid.NewGuid().ToString();
    public string Title { get; set; } = "";
    public int Cost { get; set; } = 40;
    public int ClaimedCount { get; set; }
}

public sealed class MoodEntry
{
    public string Date { get; set; } = "";
    public int Mood { get; set; } = 3;
    public int Energy { get; set; } = 3;
    public int Stress { get; set; } = 3;
    public string Note { get; set; } = "";
}

public sealed class ThemeChoice
{
    public string Name { get; set; } = "Forest";
    public string Primary { get; set; } = "#2E7D68";
    public string Accent { get; set; } = "#F9C74F";
    public string Background { get; set; } = "#F5F7F1";

    public ThemeChoice Clone() => new()
    {
        Name = Name,
        Primary = Primary,
        Accent = Accent,
        Background = Background
    };
}

public sealed class BackupEnvelope
{
    public string Schema { get; set; } = "mylifepal.windows.backup";
    public int SchemaVersion { get; set; } = 1;
    public string AppName { get; set; } = "MyLifePal";
    public string ExportedAt { get; set; } = DateTimeOffset.Now.ToString("O");
    public LifeState State { get; set; } = new();
}

public sealed class ToastMessage
{
    public string Title { get; set; } = "MyLifePal";
    public string Body { get; set; } = "";
}
