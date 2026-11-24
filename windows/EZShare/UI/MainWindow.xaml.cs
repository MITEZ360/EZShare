using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using System.Windows.Media.Animation;
using EZShare.Core;

namespace EZShare.UI
{
    public partial class MainWindow : Window
    {
        private readonly AppModel _model;
        private int _tab = 0;
        private System.Windows.Forms.OpenFileDialog _fileDialog;
        private System.Windows.Forms.FolderBrowserDialog _folderDialog;
        private TextBox _textInput;
        private WrapPanel _fileList;
        private StackPanel _textList;
        private StackPanel _deviceList;

        public MainWindow(AppModel model)
        {
            InitializeComponent();
            _model = model;
            _model.OnChanged = Refresh;
            Refresh();
        }

        private void Tab_Click(object sender, RoutedEventArgs e)
        {
            int next = int.Parse(((Button)sender).Tag.ToString());
            if (next == _tab) return;
            _tab = next;
            TabFiles.Style = (Style)FindResource(next == 0 ? "TabActive" : "TabStyle");
            TabTexts.Style = (Style)FindResource(next == 1 ? "TabActive" : "TabStyle");
            TabDevices.Style = (Style)FindResource(next == 2 ? "TabActive" : "TabStyle");
            TabSettings.Style = (Style)FindResource(next == 3 ? "TabActive" : "TabStyle");
            RenderPage();
        }

        private void Refresh()
        {
            Dispatcher.Invoke(() =>
            {
                ShareNameText.Text = _model.IsJoined ? (_model.RemoteInfo?.Name ?? "远端") : _model.ShareName;
                StatusText.Text = _model.IsJoined ? "已连接" : (_model.IsRunning ? "共享中" : (_model.IsStarting ? "启动中" : "未启动"));
                var (dot, bg, fg) = StatusPalette();
                StatusDot.Fill = new SolidColorBrush(dot);
                StatusPill.Background = new SolidColorBrush(bg);
                StatusText.Foreground = new SolidColorBrush(fg);
                StatusText.FontWeight = FontWeights.SemiBold;
                PortText.Text = _model.IsJoined ? "远端共享" : "端口 " + _model.DisplayPort;
                FooterText.Text = _model.IsJoined
                    ? "已加入 " + (_model.RemoteInfo?.Name ?? "远端共享") + " · 本地服务已暂停"
                    : "局域网直连 · 不经云端";
                RenderPage();
            });
        }

        private (Color dot, Color bg, Color fg) StatusPalette()
        {
            if (_model.IsJoined) return (Theme.Blue, Color.FromRgb(0xF2, 0xF2, 0xF2), Theme.Blue);
            if (_model.IsRunning) return (Theme.Green, Color.FromRgb(0xE6, 0xF8, 0xF3), Theme.Green);
            if (_model.IsStarting) return (Theme.Amber, Color.FromRgb(0xFB, 0xF3, 0xE0), Theme.Amber);
            return (Theme.Muted, Color.FromRgb(0xF2, 0xF2, 0xF2), Theme.Muted);
        }

        private void RenderPage()
        {
            switch (_tab)
            {
                case 0: PageHost.Content = BuildFilesPage(); break;
                case 1: PageHost.Content = BuildTextsPage(); break;
                case 2: PageHost.Content = BuildDevicesPage(); break;
                case 3: PageHost.Content = BuildSettingsPage(); break;
            }
        }

        private void ShowToast(string message)
        {
            ToastText.Text = message;
            Toast.Opacity = 0;
            var show = new DoubleAnimation(1, new Duration(TimeSpan.FromMilliseconds(150)));
            var hold = new DoubleAnimation(1, new Duration(TimeSpan.FromMilliseconds(1800)))
            {
                BeginTime = TimeSpan.FromMilliseconds(250)
            };
            var hide = new DoubleAnimation(0, new Duration(TimeSpan.FromMilliseconds(250)))
            {
                BeginTime = TimeSpan.FromMilliseconds(2100)
            };
            Toast.BeginAnimation(OpacityProperty, show);
            var storyboard = new Storyboard();
            hold.BeginTime = TimeSpan.FromMilliseconds(180);
            Storyboard.SetTarget(hold, Toast);
            Storyboard.SetTargetProperty(hold, new PropertyPath(OpacityProperty));
            storyboard.Children.Add(hold);
            storyboard.Children.Add(hide);
            Storyboard.SetTarget(hide, Toast);
            Storyboard.SetTargetProperty(hide, new PropertyPath(OpacityProperty));
            storyboard.Begin();
        }

        private ScrollViewer WrapInScroll(UIElement content)
        {
            return new ScrollViewer {
                VerticalScrollBarVisibility = ScrollBarVisibility.Auto,
                HorizontalScrollBarVisibility = ScrollBarVisibility.Disabled,
                Content = content
            };
        }

        private TextBlock SectionTitle(string text)
        {
            return new TextBlock {
                Text = text, FontSize = 13, FontWeight = FontWeights.SemiBold,
                Foreground = Theme.InkBrush, Margin = new Thickness(0, 0, 0, 10)
            };
        }
    }
}
