using System.Windows;

namespace MyLifePal.Windows;

public static class Program
{
    [STAThread]
    public static void Main()
    {
        var store = new LifeStore();
        using var notifier = new WindowsNotifier();
        var app = new Application
        {
            ShutdownMode = ShutdownMode.OnMainWindowClose
        };

        var window = new MainWindow(store, notifier);
        app.Run(window);
    }
}
