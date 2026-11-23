using System;
using System.Windows;
using EZShare.UI;

namespace EZShare
{
    public partial class App : Application
    {
        private AppModel _model;

        protected override void OnStartup(StartupEventArgs e)
        {
            base.OnStartup(e);
            ShutdownMode = ShutdownMode.OnMainWindowClose;
            _model = new AppModel();
            var window = new MainWindow(_model);
            MainWindow = window;
            window.Show();
        }

        protected override void OnExit(ExitEventArgs e)
        {
            try { _model?.Dispose(); } catch { }
            base.OnExit(e);
        }
    }
}
