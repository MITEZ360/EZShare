using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;

namespace EZShare.UI
{
    public sealed class InputDialog : Window
    {
        private readonly TextBox _box;
        public string Value { get { return _box.Text; } }

        public InputDialog(string title, string label, string initial)
        {
            Title = title;
            Width = 320; Height = 170;
            ResizeMode = ResizeMode.NoResize;
            WindowStartupLocation = WindowStartupLocation.CenterOwner;
            Background = Theme.CardBrush;

            var root = new StackPanel { Margin = new Thickness(20) };
            root.Children.Add(new TextBlock {
                Text = title, FontSize = 15, FontWeight = FontWeights.SemiBold,
                Foreground = Theme.InkBrush, Margin = new Thickness(0, 0, 0, 12)
            });
            root.Children.Add(new TextBlock {
                Text = label, FontSize = 12, Foreground = Theme.MutedBrush, Margin = new Thickness(0, 0, 0, 6)
            });
            _box = new TextBox {
                Text = initial ?? "", FontSize = 13, Height = 32, Padding = new Thickness(8, 6, 8, 6),
                BorderBrush = Theme.LineBrush, BorderThickness = new Thickness(1),
                VerticalContentAlignment = VerticalAlignment.Center
            };
            root.Children.Add(_box);

            var buttons = new DockPanel { Margin = new Thickness(0, 16, 0, 0), LastChildFill = false };
            var ok = new Button { Content = "确定", Style = (Style)Application.Current.FindResource("PrimaryButton"), Width = 70, IsDefault = true };
            ok.Click += (s, e) => { DialogResult = true; Close(); };
            var cancel = new Button { Content = "取消", Style = (Style)Application.Current.FindResource("GhostButton"), Width = 70, Margin = new Thickness(0, 0, 8, 0), IsCancel = true };
            DockPanel.SetDock(ok, Dock.Right);
            buttons.Children.Add(ok);
            buttons.Children.Add(cancel);
            root.Children.Add(buttons);

            Content = root;
            Loaded += (s, e) => { _box.Focus(); _box.SelectAll(); };
        }
    }
}
