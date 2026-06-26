using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Controls.Primitives;
using System.Windows.Media;
using System.Windows.Shapes;
using System.Windows.Threading;
using Microsoft.Win32;
using WpfBrush = System.Windows.Media.Brush;
using WpfButton = System.Windows.Controls.Button;
using WpfComboBox = System.Windows.Controls.ComboBox;
using WpfControl = System.Windows.Controls.Control;

namespace MyLifePal.Windows;

public sealed class MainWindow : Window
{
    private readonly LifeStore _store;
    private readonly WindowsNotifier _notifier;
    private readonly DispatcherTimer _timer = new();
    private readonly Grid _root = new();
    private readonly StackPanel _sidebar = new();
    private readonly Border _contentHost = new();
    private Section _section = Section.Today;
    private bool _unlocked;

    public MainWindow(LifeStore store, WindowsNotifier notifier)
    {
        _store = store;
        _notifier = notifier;
        _unlocked = !store.SecurityEnabled;
        Title = "MyLifePal";
        Width = 1180;
        Height = 780;
        MinWidth = 980;
        MinHeight = 660;
        WindowStartupLocation = WindowStartupLocation.CenterScreen;

        _root.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(240) });
        _root.ColumnDefinitions.Add(new ColumnDefinition { Width = new GridLength(1, GridUnitType.Star) });
        _sidebar.Margin = new Thickness(12);
        _contentHost.Margin = new Thickness(0, 12, 12, 12);
        Grid.SetColumn(_sidebar, 0);
        Grid.SetColumn(_contentHost, 1);
        _root.Children.Add(_sidebar);
        _root.Children.Add(_contentHost);
        Content = _root;

        _store.Changed += Render;
        _timer.Interval = TimeSpan.FromSeconds(1);
        _timer.Tick += (_, _) =>
        {
            var timerMessage = _store.Tick();
            if (timerMessage != null)
            {
                _notifier.Show(timerMessage.Title, timerMessage.Body);
                Render();
            }

            if (!_unlocked) return;
            foreach (var message in _store.DueReminderMessages())
            {
                _notifier.Show(message.Title, message.Body);
            }

            if (_section is Section.Today or Section.Timer or Section.Progress)
            {
                Render();
            }
        };
        _timer.Start();
        Loaded += (_, _) =>
        {
            if (!_unlocked && _store.SecurityEnabled)
            {
                ShowUnlockDialog();
            }
        };
        Render();
    }

    private void Render()
    {
        Background = Solid(_store.State.Theme.Background);
        if (_store.SecurityEnabled && !_unlocked)
        {
            RenderLocked();
            return;
        }
        RenderSidebar();
        RenderContent();
    }

    private void RenderLocked()
    {
        _sidebar.Children.Clear();
        _sidebar.Children.Add(Text("MyLifePal", 30, FontWeights.Bold, Solid(_store.State.Theme.Primary)));
        _sidebar.Children.Add(Text("Locked", 13, FontWeights.Normal, Brushes.DimGray));

        var panel = new StackPanel { Width = 420, HorizontalAlignment = HorizontalAlignment.Center, VerticalAlignment = VerticalAlignment.Center };
        panel.Children.Add(Text("Security password required", 28, FontWeights.Bold));
        panel.Children.Add(Text("Unlock to view habits, moods, rewards, backups, and life-game progress.", 15, FontWeights.Normal, Brushes.DimGray));
        panel.Children.Add(Space(14));
        panel.Children.Add(ActionButton("Unlock", (_, _) => ShowUnlockDialog(), true));
        _contentHost.Child = panel;
    }

    private void RenderSidebar()
    {
        _sidebar.Children.Clear();
        _sidebar.Children.Add(Text("MyLifePal", 30, FontWeights.Bold, Solid(_store.State.Theme.Primary)));
        _sidebar.Children.Add(Text("Tiny habits. Real-life levels.", 13, FontWeights.Normal, Brushes.DimGray));
        _sidebar.Children.Add(Space(16));

        foreach (var section in Enum.GetValues<Section>())
        {
            var active = section == _section;
            var button = new WpfButton
            {
                Content = SectionTitle(section),
                MinHeight = 44,
                HorizontalContentAlignment = HorizontalAlignment.Left,
                Padding = new Thickness(12, 6, 12, 6),
                Margin = new Thickness(0, 0, 0, 7),
                Background = active ? Solid(_store.State.Theme.Primary) : Brushes.Transparent,
                Foreground = active ? Brushes.White : Brushes.Black,
                BorderBrush = active ? Solid(_store.State.Theme.Primary) : Brushes.Transparent,
                FontWeight = FontWeights.SemiBold
            };
            button.Click += (_, _) =>
            {
                _section = section;
                Render();
            };
            _sidebar.Children.Add(button);
        }

        _sidebar.Children.Add(new Border { Height = 1, Margin = new Thickness(0, 10, 0, 12), Background = Brushes.Gainsboro });
        _sidebar.Children.Add(Text($"Lv {_store.Level}", 24, FontWeights.Bold));
        _sidebar.Children.Add(Text($"{_store.State.Coins} coins  |  {_store.State.Gems} gems", 13, FontWeights.Normal, Brushes.DimGray));
        _sidebar.Children.Add(Progress(_store.XpIntoLevel, 100, _store.State.Theme.Accent));
        _sidebar.Children.Add(Space(12));
        _sidebar.Children.Add(ActionButton("Add Habit", (_, _) => ShowHabitDialog(), true));
        _sidebar.Children.Add(ActionButton("Start Focus", (_, _) =>
        {
            _store.StartTimer(0);
            _section = Section.Timer;
            Render();
        }, false));
    }

    private void RenderContent()
    {
        var scroll = new ScrollViewer { VerticalScrollBarVisibility = ScrollBarVisibility.Auto };
        var content = new StackPanel { Margin = new Thickness(22), Orientation = Orientation.Vertical };
        scroll.Content = content;
        _contentHost.Child = scroll;

        content.Children.Add(Header());
        content.Children.Add(Space(18));

        switch (_section)
        {
            case Section.Today:
                RenderToday(content);
                break;
            case Section.Timer:
                RenderTimer(content);
                break;
            case Section.Mood:
                RenderMood(content);
                break;
            case Section.Habits:
                RenderHabits(content);
                break;
            case Section.Rewards:
                RenderRewards(content);
                break;
            case Section.Progress:
                RenderProgress(content);
                break;
            case Section.Appearance:
                RenderAppearance(content);
                break;
        }
    }

    private UIElement Header()
    {
        var panel = new StackPanel { Orientation = Orientation.Vertical };
        var top = new DockPanel();
        var titleBlock = new StackPanel();
        titleBlock.Children.Add(Text(SectionTitle(_section), 34, FontWeights.Bold));
        titleBlock.Children.Add(Text(_store.IdentityLine, 17, FontWeights.Normal, Brushes.DimGray));
        DockPanel.SetDock(titleBlock, Dock.Left);
        top.Children.Add(titleBlock);

        var status = new StackPanel { HorizontalAlignment = HorizontalAlignment.Right };
        status.Children.Add(Text("MyLifePal", 15, FontWeights.Bold, Solid(_store.State.Theme.Primary), TextAlignment.Right));
        status.Children.Add(Text(string.IsNullOrWhiteSpace(_store.LastMessage) ? "Offline-first Windows app" : _store.LastMessage, 13, FontWeights.Normal, Brushes.DimGray, TextAlignment.Right));
        DockPanel.SetDock(status, Dock.Right);
        top.Children.Add(status);
        panel.Children.Add(top);

        var metrics = new UniformGrid { Columns = 4, Margin = new Thickness(0, 16, 0, 0) };
        metrics.Children.Add(Metric($"{_store.State.TotalXp}", "total XP"));
        metrics.Children.Add(Metric($"{_store.CompletedToday}/{_store.State.Habits.Count}", "done today"));
        metrics.Children.Add(Metric($"{_store.State.TomatoSessionsToday}", "tomatoes"));
        metrics.Children.Add(Metric($"{_store.CompanionLevel}", "companion"));
        panel.Children.Add(metrics);
        return panel;
    }

    private void RenderToday(StackPanel content)
    {
        var grid = new UniformGrid { Columns = 2 };
        grid.Children.Add(CoachCard());
        grid.Children.Add(CompanionCard());
        grid.Children.Add(TimerCard(compact: true));
        grid.Children.Add(MoodSummaryCard());
        content.Children.Add(grid);
        content.Children.Add(SectionHeader("Today", "Complete the smallest promise that counts."));

        if (_store.State.Habits.Count == 0)
        {
            content.Children.Add(Card(Text("No habits yet. Add one tiny loop to begin.", 15)));
            return;
        }

        foreach (var habit in _store.State.Habits)
        {
            content.Children.Add(HabitCard(habit));
        }
    }

    private Border CoachCard()
    {
        var panel = new StackPanel();
        var next = _store.NextHabit;
        var score = _store.State.Habits.Count == 0 ? 0 : Math.Min(100, (int)(65d * _store.CompletedToday / _store.State.Habits.Count) + (_store.TodayMood == null ? 0 : 15) + Math.Min(20, _store.State.TomatoSessionsToday * 8));
        panel.Children.Add(Text(next == null ? "Claim the day" : "Next useful action", 24, FontWeights.Bold));
        panel.Children.Add(Text(next == null ? "Your habit votes are in. Spend coins or design tomorrow's first move." : $"{next.Cue}: {next.TinyAction}", 14, FontWeights.Normal, Brushes.DimGray));
        panel.Children.Add(Space(12));
        panel.Children.Add(Text($"{score}% today score", 15, FontWeights.Bold, Solid(_store.State.Theme.Primary)));
        panel.Children.Add(Space(10));
        panel.Children.Add(ActionButton(next == null ? "Open Rewards" : "Complete Next", (_, _) =>
        {
            if (next == null)
            {
                _section = Section.Rewards;
                Render();
            }
            else
            {
                _store.CompleteHabit(next);
            }
        }, true));
        return Card(panel, accent: true);
    }

    private Border CompanionCard()
    {
        var row = new StackPanel { Orientation = Orientation.Horizontal };
        row.Children.Add(CompanionMark());
        row.Children.Add(Space(width: 16));
        var copy = new StackPanel();
        copy.Children.Add(Text($"{_store.State.MonsterName} the {_store.CompanionStage}", 22, FontWeights.Bold));
        copy.Children.Add(Text($"Level {_store.CompanionLevel} companion", 14, FontWeights.Bold, Solid(_store.State.Theme.Primary)));
        copy.Children.Add(Progress(_store.State.MonsterXp % 90, 90, _store.State.Theme.Primary));
        copy.Children.Add(Text($"Bond {_store.State.MonsterBond}/100", 13, FontWeights.Normal, Brushes.DimGray));
        row.Children.Add(copy);
        return Card(row);
    }

    private Border TimerCard(bool compact)
    {
        var panel = new StackPanel();
        panel.Children.Add(Text("Tomato Timer", 22, FontWeights.Bold));
        panel.Children.Add(Text(_store.TimerDisplay, compact ? 42 : 72, FontWeights.Bold));
        panel.Children.Add(Progress((int)(_store.TimerProgress * 100), 100, _store.State.Theme.Accent));
        panel.Children.Add(Space(12));
        var buttons = new StackPanel { Orientation = Orientation.Horizontal };
        buttons.Children.Add(ActionButton(_store.State.TimerRunning ? "Pause" : "Start", (_, _) =>
        {
            if (_store.State.TimerRunning) _store.PauseTimer();
            else _store.StartTimer();
        }, true));
        buttons.Children.Add(Space(width: 8));
        buttons.Children.Add(ActionButton("Reset", (_, _) => _store.ResetTimer(), false));
        panel.Children.Add(buttons);
        return Card(panel, accent: true);
    }

    private Border MoodSummaryCard()
    {
        var panel = new StackPanel();
        panel.Children.Add(Text("Emotion check-in", 22, FontWeights.Bold));
        var mood = _store.TodayMood;
        panel.Children.Add(Text(mood == null ? "No check-in today" : LifeStore.MoodLabel(mood.Mood), 18, FontWeights.SemiBold));
        panel.Children.Add(Text(mood == null ? "A quick check-in makes the habit loop more humane." : $"Energy {mood.Energy}/5, stress {mood.Stress}/5", 13, FontWeights.Normal, Brushes.DimGray));
        panel.Children.Add(Space(10));
        panel.Children.Add(ActionButton("Open Mood", (_, _) =>
        {
            _section = Section.Mood;
            Render();
        }, false));
        return Card(panel);
    }

    private void RenderTimer(StackPanel content)
    {
        content.Children.Add(TimerCard(compact: false));
        var modes = new StackPanel { Orientation = Orientation.Horizontal, Margin = new Thickness(0, 0, 0, 14) };
        for (var i = 0; i < 3; i++)
        {
            var mode = i;
            modes.Children.Add(ActionButton(LifeStore.TimerModeName(mode), (_, _) => _store.SelectTimerMode(mode), _store.State.TimerMode == mode));
            modes.Children.Add(Space(width: 8));
        }
        content.Children.Add(modes);
        var stats = new UniformGrid { Columns = 3 };
        stats.Children.Add(Metric($"{_store.State.TomatoFocusSessions}", "focus tomatoes"));
        stats.Children.Add(Metric($"{_store.State.TomatoMinutesToday}", "focus minutes today"));
        stats.Children.Add(Metric($"{_store.State.TomatoBreakSessions}", "break sessions"));
        content.Children.Add(stats);
    }

    private void RenderMood(StackPanel content)
    {
        content.Children.Add(SectionHeader("Daily emotion check-in", "Name the weather inside, then choose a humane next action."));
        var mood = _store.TodayMood;
        var moodBox = Combo(["1 Rough", "2 Low", "3 Okay", "4 Good", "5 Great"], mood == null ? 2 : Math.Max(0, mood.Mood - 1));
        var energyBox = Combo(["1", "2", "3", "4", "5"], mood == null ? 2 : Math.Max(0, mood.Energy - 1));
        var stressBox = Combo(["1", "2", "3", "4", "5"], mood == null ? 2 : Math.Max(0, mood.Stress - 1));
        var note = new TextBox { Text = mood?.Note ?? "", MinHeight = 70, AcceptsReturn = true, TextWrapping = TextWrapping.Wrap };
        var form = new StackPanel();
        var row = new UniformGrid { Columns = 3 };
        row.Children.Add(Labeled("Mood", moodBox));
        row.Children.Add(Labeled("Energy", energyBox));
        row.Children.Add(Labeled("Stress", stressBox));
        form.Children.Add(row);
        form.Children.Add(Labeled("Note", note));
        form.Children.Add(ActionButton(mood == null ? "Record Check-in" : "Update Check-in", (_, _) =>
        {
            _store.RecordMood(moodBox.SelectedIndex + 1, energyBox.SelectedIndex + 1, stressBox.SelectedIndex + 1, note.Text);
        }, true));
        content.Children.Add(Card(form));

        content.Children.Add(SectionHeader("Recent feelings", "Patterns become visible once they have a place to land."));
        foreach (var entry in _store.State.MoodEntries.TakeLast(8).Reverse())
        {
            content.Children.Add(Card(Text($"{entry.Date} - {LifeStore.MoodLabel(entry.Mood)} | Energy {entry.Energy}/5, stress {entry.Stress}/5", 15, FontWeights.SemiBold)));
        }
    }

    private void RenderHabits(StackPanel content)
    {
        var header = new DockPanel();
        header.Children.Add(SectionHeader("Habit Studio", "Design cues, tiny actions, identity votes, rewards, and reminders."));
        var add = ActionButton("Add Habit", (_, _) => ShowHabitDialog(), true);
        DockPanel.SetDock(add, Dock.Right);
        header.Children.Add(add);
        content.Children.Add(header);

        foreach (var habit in _store.State.Habits)
        {
            content.Children.Add(HabitCard(habit));
        }
    }

    private Border HabitCard(LifeHabit habit)
    {
        var panel = new StackPanel();
        var title = new DockPanel();
        title.Children.Add(Text($"{habit.Icon} {habit.Name}", 22, FontWeights.Bold));
        var attribute = Text(habit.Attribute, 12, FontWeights.Bold, Brushes.White);
        attribute.Background = Solid(_store.State.Theme.Primary);
        attribute.Padding = new Thickness(10, 5, 10, 5);
        DockPanel.SetDock(attribute, Dock.Right);
        title.Children.Add(attribute);
        panel.Children.Add(title);
        panel.Children.Add(Text(habit.Identity, 13, FontWeights.Normal, Brushes.DimGray));
        panel.Children.Add(Space(10));
        panel.Children.Add(Detail("Cue", habit.Cue));
        panel.Children.Add(Detail("Tiny action", habit.TinyAction));
        panel.Children.Add(Detail("Reward", habit.Reward));
        panel.Children.Add(Detail("Reminder", habit.ReminderEnabled ? $"Daily at {habit.ReminderTime}" : "Off"));

        var stats = new UniformGrid { Columns = 3, Margin = new Thickness(0, 10, 0, 10) };
        stats.Children.Add(Metric($"{habit.Streak}d", "streak"));
        stats.Children.Add(Metric($"{habit.BestStreak}d", "best"));
        stats.Children.Add(Metric($"{habit.Completions}", "votes"));
        panel.Children.Add(stats);

        var actions = new StackPanel { Orientation = Orientation.Horizontal };
        actions.Children.Add(ActionButton(_store.IsCompletedToday(habit) ? "Done Today" : "Complete", (_, _) => _store.CompleteHabit(habit), true, !_store.IsCompletedToday(habit)));
        actions.Children.Add(Space(width: 8));
        actions.Children.Add(ActionButton("Delete", (_, _) =>
        {
            if (MessageBox.Show(this, $"Delete \"{habit.Name}\"?", "Delete habit", MessageBoxButton.YesNo, MessageBoxImage.Warning) == MessageBoxResult.Yes)
            {
                _store.DeleteHabit(habit);
            }
        }, false));
        panel.Children.Add(actions);
        return Card(panel, soft: _store.IsCompletedToday(habit));
    }

    private void RenderRewards(StackPanel content)
    {
        var stats = new UniformGrid { Columns = 4 };
        stats.Children.Add(Metric($"{_store.State.Coins}", "coins"));
        stats.Children.Add(Metric($"{_store.State.Gems}", "gems"));
        stats.Children.Add(Metric($"{_store.State.Inventory.Count}", "inventory"));
        stats.Children.Add(Metric($"{_store.State.RewardClaimsToday}", "claimed today"));
        content.Children.Add(stats);
        content.Children.Add(Space(14));
        content.Children.Add(ActionButton("Add Reward", (_, _) => ShowRewardDialog(), true));
        content.Children.Add(SectionHeader("Reward Shop", "Spend coins on concrete real-life rewards."));

        foreach (var reward in _store.State.Rewards)
        {
            var panel = new StackPanel();
            panel.Children.Add(Text(reward.Title, 20, FontWeights.Bold));
            panel.Children.Add(Text($"{reward.Cost} coins | {reward.ClaimedCount} claimed", 13, FontWeights.Normal, Brushes.DimGray));
            panel.Children.Add(Space(10));
            var actions = new StackPanel { Orientation = Orientation.Horizontal };
            actions.Children.Add(ActionButton(_store.State.Coins >= reward.Cost ? "Claim Reward" : "Need Coins", (_, _) => _store.ClaimReward(reward), true, _store.State.Coins >= reward.Cost));
            actions.Children.Add(Space(width: 8));
            actions.Children.Add(ActionButton("Delete", (_, _) => _store.DeleteReward(reward), false));
            panel.Children.Add(actions);
            content.Children.Add(Card(panel));
        }

        content.Children.Add(SectionHeader("Inventory", "Claimed rewards and symbolic life-game loot."));
        foreach (var item in _store.State.Inventory.AsEnumerable().Reverse())
        {
            content.Children.Add(Card(Text(item, 14, FontWeights.SemiBold), soft: true));
        }
    }

    private void RenderProgress(StackPanel content)
    {
        var level = new StackPanel();
        level.Children.Add(Text($"Level {_store.Level}", 42, FontWeights.Bold));
        level.Children.Add(Text($"{_store.XpIntoLevel} / 100 XP to next level", 14, FontWeights.Normal, Brushes.DimGray));
        level.Children.Add(Progress(_store.XpIntoLevel, 100, _store.State.Theme.Primary));
        content.Children.Add(Card(level));

        content.Children.Add(SectionHeader("Attributes", "Each habit feeds one real-life skill."));
        foreach (var attribute in LifeStore.Attributes)
        {
            var xp = _store.State.AttributeXp.GetValueOrDefault(attribute, 0);
            var panel = new StackPanel();
            panel.Children.Add(Text($"{attribute}: {xp} XP", 16, FontWeights.Bold));
            panel.Children.Add(Progress(xp % 100, 100, _store.State.Theme.Primary));
            content.Children.Add(Card(panel));
        }

        content.Children.Add(SectionHeader("Achievements", "Milestones unlock naturally as the system gets used."));
        var achievements = new[]
        {
            ("First spark", _store.State.Habits.Any(habit => habit.Completions > 0)),
            ("Three-day identity", _store.State.Habits.Any(habit => habit.BestStreak >= 3)),
            ("First tomato", _store.State.TomatoFocusSessions >= 1),
            ("Mood journal", _store.State.MoodEntries.Count >= 7),
            ("Companion keeper", _store.CompanionLevel >= 3),
            ("LifePal level 5", _store.Level >= 5)
        };
        foreach (var achievement in achievements)
        {
            content.Children.Add(Card(Text($"{(achievement.Item2 ? "Unlocked" : "Locked")} - {achievement.Item1}", 15, FontWeights.SemiBold), soft: achievement.Item2));
        }
    }

    private void RenderAppearance(StackPanel content)
    {
        content.Children.Add(SectionHeader("Appearance", "Pick a palette that feels personal while keeping the app readable."));
        var presets = new UniformGrid { Columns = 3 };
        foreach (var theme in LifeStore.ThemePresets)
        {
            presets.Children.Add(ActionButton(theme.Name, (_, _) => _store.ApplyTheme(theme), theme.Name == _store.State.Theme.Name));
        }
        content.Children.Add(Card(presets));

        var primary = new TextBox { Text = _store.State.Theme.Primary };
        var accent = new TextBox { Text = _store.State.Theme.Accent };
        var background = new TextBox { Text = _store.State.Theme.Background };
        var custom = new StackPanel();
        var customRow = new UniformGrid { Columns = 3 };
        customRow.Children.Add(Labeled("Primary", primary));
        customRow.Children.Add(Labeled("Accent", accent));
        customRow.Children.Add(Labeled("Background", background));
        custom.Children.Add(customRow);
        custom.Children.Add(ActionButton("Save Custom Colors", (_, _) => _store.SaveCustomTheme(primary.Text, accent.Text, background.Text), true));
        content.Children.Add(Card(custom));

        var data = new StackPanel { Orientation = Orientation.Horizontal };
        data.Children.Add(ActionButton("Export JSON", (_, _) => ExportBackup(), true));
        data.Children.Add(Space(width: 8));
        data.Children.Add(ActionButton("Import JSON", (_, _) => ImportBackup(), false));
        data.Children.Add(Space(width: 8));
        data.Children.Add(ActionButton("Reveal Data Folder", (_, _) => _store.RevealDataFolder(), false));
        content.Children.Add(Card(data, soft: true));
        content.Children.Add(SecurityCard());
    }

    private Border SecurityCard()
    {
        var panel = new StackPanel();
        panel.Children.Add(Text("Security password", 22, FontWeights.Bold));
        panel.Children.Add(Text(_store.SecurityEnabled ? "Enabled. The app asks for this password on launch." : "Off. Create a local password to protect this device.", 14, FontWeights.Normal, Brushes.DimGray));
        panel.Children.Add(Space(10));
        var actions = new StackPanel { Orientation = Orientation.Horizontal };
        actions.Children.Add(ActionButton(_store.SecurityEnabled ? "Change Password" : "Create Password", (_, _) => ShowSetPasswordDialog(), true));
        if (_store.SecurityEnabled)
        {
            actions.Children.Add(Space(width: 8));
            actions.Children.Add(ActionButton("Disable", (_, _) => ShowDisablePasswordDialog(), false));
            actions.Children.Add(Space(width: 8));
            actions.Children.Add(ActionButton("Lock Now", (_, _) =>
            {
                _unlocked = false;
                Render();
                ShowUnlockDialog();
            }, false));
        }
        panel.Children.Add(actions);
        return Card(panel, soft: _store.SecurityEnabled);
    }

    private void ShowUnlockDialog()
    {
        var dialog = Dialog("Unlock MyLifePal", 420);
        var password = new PasswordBox();
        var panel = (StackPanel)dialog.Content;
        panel.Children.Add(Text("Enter your security password.", 15, FontWeights.Normal, Brushes.DimGray));
        panel.Children.Add(Labeled("Password", password));
        panel.Children.Add(DialogButtons(dialog, () =>
        {
            if (!_store.VerifySecurityPassword(password.Password))
            {
                MessageBox.Show(dialog, "Password does not match.", "MyLifePal", MessageBoxButton.OK, MessageBoxImage.Warning);
                password.Clear();
                password.Focus();
                return false;
            }

            _unlocked = true;
            Render();
            return true;
        }));
        dialog.ShowDialog();
    }

    private void ShowSetPasswordDialog()
    {
        var dialog = Dialog(_store.SecurityEnabled ? "Change Password" : "Create Password", 460);
        var current = new PasswordBox();
        var next = new PasswordBox();
        var confirm = new PasswordBox();
        var panel = (StackPanel)dialog.Content;
        panel.Children.Add(Text("Use at least 4 characters. MyLifePal stores a salted hash, never the plain password.", 14, FontWeights.Normal, Brushes.DimGray));
        if (_store.SecurityEnabled)
        {
            panel.Children.Add(Labeled("Current password", current));
        }
        panel.Children.Add(Labeled("New password", next));
        panel.Children.Add(Labeled("Confirm password", confirm));
        panel.Children.Add(DialogButtons(dialog, () =>
        {
            if (_store.SecurityEnabled && !_store.VerifySecurityPassword(current.Password))
            {
                MessageBox.Show(dialog, "Current password does not match.", "MyLifePal", MessageBoxButton.OK, MessageBoxImage.Warning);
                current.Clear();
                current.Focus();
                return false;
            }
            if (next.Password.Trim().Length < 4)
            {
                MessageBox.Show(dialog, "Use at least 4 characters.", "MyLifePal", MessageBoxButton.OK, MessageBoxImage.Information);
                next.Focus();
                return false;
            }
            if (next.Password != confirm.Password)
            {
                MessageBox.Show(dialog, "The new passwords do not match.", "MyLifePal", MessageBoxButton.OK, MessageBoxImage.Information);
                confirm.Clear();
                confirm.Focus();
                return false;
            }

            _store.SetSecurityPassword(next.Password);
            _unlocked = true;
            Render();
            return true;
        }));
        dialog.ShowDialog();
    }

    private void ShowDisablePasswordDialog()
    {
        var dialog = Dialog("Disable Password", 420);
        var password = new PasswordBox();
        var panel = (StackPanel)dialog.Content;
        panel.Children.Add(Text("Enter the current password to remove the local app lock.", 14, FontWeights.Normal, Brushes.DimGray));
        panel.Children.Add(Labeled("Password", password));
        panel.Children.Add(DialogButtons(dialog, () =>
        {
            if (!_store.VerifySecurityPassword(password.Password))
            {
                MessageBox.Show(dialog, "Password does not match.", "MyLifePal", MessageBoxButton.OK, MessageBoxImage.Warning);
                password.Clear();
                password.Focus();
                return false;
            }

            _store.ClearSecurityPassword();
            _unlocked = true;
            Render();
            return true;
        }));
        dialog.ShowDialog();
    }

    private void ShowHabitDialog()
    {
        var dialog = Dialog("New Habit", 560);
        var name = new TextBox();
        var icon = new TextBox { Text = "*" };
        var cue = new TextBox();
        var tinyAction = new TextBox();
        var identity = new TextBox();
        var reward = new TextBox();
        var attribute = Combo(LifeStore.Attributes, 0);
        var reminderEnabled = new CheckBox { Content = "Daily reminder" };
        var reminderTime = Combo(LifeStore.ReminderTimes, 2);

        var panel = (StackPanel)dialog.Content;
        panel.Children.Add(Labeled("Name", name));
        panel.Children.Add(Labeled("Icon", icon));
        panel.Children.Add(Labeled("Cue", cue));
        panel.Children.Add(Labeled("Tiny action", tinyAction));
        panel.Children.Add(Labeled("Identity", identity));
        panel.Children.Add(Labeled("Reward", reward));
        panel.Children.Add(Labeled("Attribute", attribute));
        panel.Children.Add(reminderEnabled);
        panel.Children.Add(Labeled("Reminder time", reminderTime));
        panel.Children.Add(DialogButtons(dialog, () =>
        {
            if (string.IsNullOrWhiteSpace(name.Text))
            {
                MessageBox.Show(dialog, "Habit name is required.", "MyLifePal", MessageBoxButton.OK, MessageBoxImage.Information);
                return false;
            }
            _store.AddHabit(new LifeHabit
            {
                Name = name.Text,
                Icon = icon.Text,
                Cue = cue.Text,
                TinyAction = tinyAction.Text,
                Identity = identity.Text,
                Reward = reward.Text,
                Attribute = attribute.SelectedItem?.ToString() ?? "Mind",
                ReminderEnabled = reminderEnabled.IsChecked == true,
                ReminderTime = reminderTime.SelectedItem?.ToString() ?? "09:00"
            });
            return true;
        }));
        dialog.ShowDialog();
    }

    private void ShowRewardDialog()
    {
        var dialog = Dialog("New Reward", 440);
        var title = new TextBox();
        var cost = new TextBox { Text = "40" };
        var panel = (StackPanel)dialog.Content;
        panel.Children.Add(Labeled("Reward", title));
        panel.Children.Add(Labeled("Coin cost", cost));
        panel.Children.Add(DialogButtons(dialog, () =>
        {
            if (string.IsNullOrWhiteSpace(title.Text))
            {
                MessageBox.Show(dialog, "Reward title is required.", "MyLifePal", MessageBoxButton.OK, MessageBoxImage.Information);
                return false;
            }
            _store.AddReward(new LifeReward
            {
                Title = title.Text,
                Cost = int.TryParse(cost.Text, out var parsed) ? Math.Max(1, parsed) : 40
            });
            return true;
        }));
        dialog.ShowDialog();
    }

    private Window Dialog(string title, double width)
    {
        return new Window
        {
            Title = title,
            Width = width,
            SizeToContent = SizeToContent.Height,
            WindowStartupLocation = WindowStartupLocation.CenterOwner,
            Owner = this,
            Content = new StackPanel { Margin = new Thickness(20) }
        };
    }

    private UIElement DialogButtons(Window dialog, Func<bool> save)
    {
        var row = new StackPanel { Orientation = Orientation.Horizontal, HorizontalAlignment = HorizontalAlignment.Right, Margin = new Thickness(0, 14, 0, 0) };
        row.Children.Add(ActionButton("Cancel", (_, _) => dialog.Close(), false));
        row.Children.Add(Space(width: 8));
        row.Children.Add(ActionButton("Save", (_, _) =>
        {
            if (save()) dialog.Close();
        }, true));
        return row;
    }

    private void ExportBackup()
    {
        var dialog = new SaveFileDialog
        {
            Filter = "JSON files (*.json)|*.json",
            FileName = $"mylifepal-windows-backup-{_store.Today}.json"
        };
        if (dialog.ShowDialog(this) == true)
        {
            File.WriteAllText(dialog.FileName, _store.ExportBackupJson());
        }
    }

    private void ImportBackup()
    {
        var dialog = new OpenFileDialog
        {
            Filter = "JSON files (*.json)|*.json|All files (*.*)|*.*"
        };
        if (dialog.ShowDialog(this) != true) return;
        try
        {
            _store.ImportBackupJson(File.ReadAllText(dialog.FileName));
        }
        catch
        {
            MessageBox.Show(this, "Backup import failed.", "MyLifePal", MessageBoxButton.OK, MessageBoxImage.Error);
        }
    }

    private TextBlock Text(string value, double size, FontWeight? weight = null, WpfBrush? brush = null, TextAlignment alignment = TextAlignment.Left)
    {
        return new TextBlock
        {
            Text = value,
            FontSize = size,
            FontWeight = weight ?? FontWeights.Normal,
            Foreground = brush ?? Brushes.Black,
            TextWrapping = TextWrapping.Wrap,
            TextAlignment = alignment,
            Margin = new Thickness(0, 2, 0, 2)
        };
    }

    private Border Card(UIElement child, bool soft = false, bool accent = false)
    {
        return new Border
        {
            Child = child,
            Padding = new Thickness(18),
            Margin = new Thickness(0, 0, 12, 12),
            CornerRadius = new CornerRadius(8),
            Background = accent ? WithOpacity(_store.State.Theme.Accent, 0.20) : soft ? WithOpacity(_store.State.Theme.Primary, 0.12) : Brushes.White,
            BorderBrush = accent ? WithOpacity(_store.State.Theme.Accent, 0.65) : soft ? WithOpacity(_store.State.Theme.Primary, 0.45) : Brushes.Gainsboro,
            BorderThickness = new Thickness(1)
        };
    }

    private WpfButton ActionButton(string label, RoutedEventHandler click, bool primary, bool enabled = true)
    {
        var button = new WpfButton
        {
            Content = label,
            MinHeight = 42,
            Padding = new Thickness(14, 6, 14, 6),
            Margin = new Thickness(0, 0, 0, 8),
            FontWeight = FontWeights.SemiBold,
            IsEnabled = enabled,
            Background = primary ? Solid(_store.State.Theme.Primary) : Brushes.White,
            Foreground = primary ? Brushes.White : Brushes.Black,
            BorderBrush = primary ? Solid(_store.State.Theme.Primary) : Brushes.Gainsboro
        };
        button.Click += click;
        return button;
    }

    private Border Metric(string value, string label)
    {
        var panel = new StackPanel();
        panel.Children.Add(Text(value, 21, FontWeights.Bold));
        panel.Children.Add(Text(label, 12, FontWeights.Normal, Brushes.DimGray));
        return Card(panel);
    }

    private UIElement SectionHeader(string title, string subtitle)
    {
        var panel = new StackPanel { Margin = new Thickness(0, 10, 0, 10) };
        panel.Children.Add(Text(title, 24, FontWeights.Bold));
        panel.Children.Add(Text(subtitle, 14, FontWeights.Normal, Brushes.DimGray));
        return panel;
    }

    private UIElement Detail(string label, string value) => Text($"{label}: {value}", 13, FontWeights.Normal, Brushes.DimGray);

    private UIElement Labeled(string label, WpfControl control)
    {
        control.Margin = new Thickness(0, 4, 0, 8);
        control.MinHeight = 34;
        var panel = new StackPanel();
        panel.Children.Add(Text(label, 12, FontWeights.SemiBold, Brushes.DimGray));
        panel.Children.Add(control);
        return panel;
    }

    private WpfComboBox Combo(IEnumerable<string> values, int selectedIndex)
    {
        var combo = new WpfComboBox { MinHeight = 34 };
        foreach (var value in values) combo.Items.Add(value);
        combo.SelectedIndex = Math.Max(0, Math.Min(selectedIndex, combo.Items.Count - 1));
        return combo;
    }

    private UIElement Progress(int value, int max, string color)
    {
        return new ProgressBar
        {
            Value = Math.Max(0, Math.Min(value, max)),
            Maximum = Math.Max(1, max),
            Height = 9,
            Foreground = Solid(color),
            Background = WithOpacity(_store.State.Theme.Primary, 0.16),
            Margin = new Thickness(0, 8, 0, 4)
        };
    }

    private UIElement CompanionMark()
    {
        var canvas = new Canvas { Width = 92, Height = 92, Margin = new Thickness(0, 0, 0, 0) };
        canvas.Children.Add(new Ellipse { Width = 92, Height = 92, Fill = WithOpacity(_store.State.Theme.Accent, 0.26) });
        var body = new Ellipse { Width = 62, Height = 62, Fill = Solid(_store.State.Theme.Primary) };
        Canvas.SetLeft(body, 15);
        Canvas.SetTop(body, 15);
        canvas.Children.Add(body);
        foreach (var x in new[] { 33d, 54d })
        {
            var eye = new Ellipse { Width = 9, Height = 9, Fill = Brushes.White };
            Canvas.SetLeft(eye, x);
            Canvas.SetTop(eye, 34);
            canvas.Children.Add(eye);
        }
        var mouth = new Rectangle { Width = 24, Height = 8, RadiusX = 4, RadiusY = 4, Fill = Brushes.White };
        Canvas.SetLeft(mouth, 34);
        Canvas.SetTop(mouth, 57);
        canvas.Children.Add(mouth);
        return canvas;
    }

    private static UIElement Space(double height = 0, double width = 0) => new Border { Width = width, Height = height };

    private SolidColorBrush Solid(string hex)
    {
        var color = (Color)ColorConverter.ConvertFromString(hex);
        var brush = new SolidColorBrush(color);
        brush.Freeze();
        return brush;
    }

    private SolidColorBrush WithOpacity(string hex, double opacity)
    {
        var color = (Color)ColorConverter.ConvertFromString(hex);
        color.A = (byte)Math.Round(255 * opacity);
        var brush = new SolidColorBrush(color);
        brush.Freeze();
        return brush;
    }

    private static string SectionTitle(Section section) => section switch
    {
        Section.Today => "Today",
        Section.Timer => "Timer",
        Section.Mood => "Mood",
        Section.Habits => "Habits",
        Section.Rewards => "Rewards",
        Section.Progress => "Progress",
        Section.Appearance => "Appearance",
        _ => "MyLifePal"
    };

    private enum Section
    {
        Today,
        Timer,
        Mood,
        Habits,
        Rewards,
        Progress,
        Appearance
    }
}
