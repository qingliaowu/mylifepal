using System.Drawing;
using Forms = System.Windows.Forms;

namespace MyLifePal.Windows;

public sealed class WindowsNotifier : IDisposable
{
    private readonly Forms.NotifyIcon _notifyIcon;

    public WindowsNotifier()
    {
        _notifyIcon = new Forms.NotifyIcon
        {
            Icon = SystemIcons.Application,
            Text = "MyLifePal",
            Visible = true
        };
    }

    public void Show(string title, string body)
    {
        _notifyIcon.BalloonTipTitle = title;
        _notifyIcon.BalloonTipText = body;
        _notifyIcon.ShowBalloonTip(6000);
    }

    public void Dispose()
    {
        _notifyIcon.Visible = false;
        _notifyIcon.Dispose();
    }
}
