using System;
using System.Windows;
using EZShare.UI;

namespace EZShare
{
    public partial class App : Application
    {
        private AppModel _model;
        private TrayIcon _tray;

        protected override void OnStartup(StartupEventArgs e)
        {
            base.OnStartup(e);
            // 关闭主窗口不退出程序，服务保持运行，由托盘图标接管。
            ShutdownMode = ShutdownMode.OnExplicitShutdown;
            _model = new AppModel();
            var window = new MainWindow(_model);
            MainWindow = window;
            window.Closing += (_, args) =>
            {
                if (_tray == null) return;
                args.Cancel = true;
                window.Hide();
            };
            _tray = new TrayIcon(window);
            window.Show();
        }

        protected override void OnExit(ExitEventArgs e)
        {
            try { _tray?.Dispose(); } catch { }
            try { _model?.Dispose(); } catch { }
            base.OnExit(e);
        }
    }
}
