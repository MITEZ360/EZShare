using System;
using System.Drawing;
using System.IO;
using System.Reflection;
using System.Windows.Forms;

namespace EZShare
{
    /// <summary>
    /// 系统托盘图标：关闭主窗口后程序继续运行，双击图标恢复窗口。
    /// </summary>
    internal sealed class TrayIcon : IDisposable
    {
        private readonly NotifyIcon _notify;
        private readonly MainWindow _window;

        private static Icon LoadIcon()
        {
            var assembly = Assembly.GetExecutingAssembly();
            using (var stream = assembly.GetManifestResourceStream("EZShare.Assets.AppIcon.ico"))
            {
                if (stream != null)
                {
                    using (var copy = new MemoryStream())
                    {
                        stream.CopyTo(copy);
                        return new Icon(new MemoryStream(copy.ToArray()));
                    }
                }
            }
            return SystemIcons.Application;
        }

        public TrayIcon(MainWindow window)
        {
            _window = window;
            _notify = new NotifyIcon
            {
                Icon = LoadIcon(),
                Text = "EZShare · 局域网共享",
                Visible = true
            };
            _notify.DoubleClick += delegate { Restore(); };

            var menu = new ContextMenuStrip();
            menu.Items.Add("显示 / 隐藏 EZShare", null, delegate { Toggle(); });
            menu.Items.Add(new ToolStripSeparator());
            menu.Items.Add("退出 EZShare", null, delegate
            {
                _notify.Visible = false;
                window.Dispatcher.Invoke(() => System.Windows.Application.Current.Shutdown());
            });
            _notify.ContextMenuStrip = menu;
        }

        public void Toggle()
        {
            if (_window.IsVisible) _window.Hide();
            else Restore();
        }

        private void Restore()
        {
            _window.Show();
            _window.WindowState = System.Windows.WindowState.Normal;
            _window.Activate();
        }

        public void Dispose()
        {
            _notify.Visible = false;
            _notify.Dispose();
        }
    }
}
