using System;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Input;
using System.Windows.Media;
using EZShare.Core;

namespace EZShare.UI
{
    public partial class MainWindow
    {
        private UIElement BuildTextsPage()
        {
            var root = new StackPanel();
            bool canWrite = _model.IsJoined ? (_model.RemoteInfo?.AllowUpload ?? false) : _model.Settings.AllowUpload;

            if (canWrite)
            {
                var composer = Theme.Card(16);
                var inner = new StackPanel { Margin = new Thickness(12) };
                _textInput = new TextBox {
                    Height = 92, AcceptsReturn = true, TextWrapping = TextWrapping.Wrap,
                    FontSize = 12.5, Padding = new Thickness(10), BorderThickness = new Thickness(1),
                    BorderBrush = Theme.LineBrush, VerticalScrollBarVisibility = ScrollBarVisibility.Auto
                };
                _textInput.KeyDown += (s, e) =>
                {
                    if (e.Key == Key.Enter && (Keyboard.Modifiers & ModifierKeys.Control) == ModifierKeys.Control)
                    { SendText(); e.Handled = true; }
                };
                inner.Children.Add(_textInput);
                inner.Children.Add(new TextBlock {
                    Text = "Ctrl + Enter 快速发送 · 局域网内任意设备可查看与复制",
                    FontSize = 10.5, Foreground = Theme.MutedBrush, Margin = new Thickness(0, 6, 0, 0)
                });
                var foot = new DockPanel { Margin = new Thickness(0, 10, 0, 0), LastChildFill = false };
                var sendBtn = new Button { Content = "发送到共享", Style = (Style)FindResource("PrimaryButton") };
                sendBtn.Click += (s, e) => SendText();
                DockPanel.SetDock(sendBtn, Dock.Right);
                foot.Children.Add(sendBtn);
                inner.Children.Add(foot);
                composer.Child = inner;
                root.Children.Add(composer);
            }

            var listCard = Theme.Card(16);
            listCard.Margin = new Thickness(0, 12, 0, 0);
            _textList = new StackPanel { Margin = new Thickness(6) };

            var header = new DockPanel { Margin = new Thickness(8, 6, 8, 10), LastChildFill = false };
            header.Children.Add(new TextBlock {
                Text = _model.Snippets.Count + " 条文本", FontSize = 12, FontWeight = FontWeights.SemiBold, Foreground = Theme.MutedBrush
            });
            var refreshBtn = new Button { Content = "↻ 刷新", Style = (Style)FindResource("RoundButton"), FontSize = 11.5, Padding = new Thickness(8, 4, 8, 4) };
            refreshBtn.Click += (s, e) => { Theme.Spin((Button)s); _model.RefreshContent(); };
            DockPanel.SetDock(refreshBtn, Dock.Right);
            header.Children.Add(refreshBtn);
            _textList.Children.Add(header);

            if (_model.Snippets.Count == 0)
            {
                _textList.Children.Add(new TextBlock {
                    Text = "还没有共享的文本", FontSize = 13, Foreground = Theme.MutedBrush,
                    HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(0, 50, 0, 50)
                });
            }
            else
            {
                for (int i = 0; i < _model.Snippets.Count; i++)
                    _textList.Children.Add(BuildTextTile(_model.Snippets[i], i));
            }
            listCard.Child = _textList;
            root.Children.Add(listCard);

            return WrapInScroll(root);
        }

        private Border BuildTextTile(TextSnippet snippet, int index)
        {
            var tile = new Border {
                Background = new SolidColorBrush(Color.FromRgb(0xF2, 0xF2, 0xF2)),
                CornerRadius = new CornerRadius(10), Margin = new Thickness(6, 4, 6, 4), Padding = new Thickness(11)
            };
            var sp = new StackPanel();
            var head = new DockPanel { LastChildFill = false };
            head.Children.Add(new TextBlock {
                Text = snippet.CreatedAt.ToLocalTime().ToString("yyyy-MM-dd HH:mm"),
                FontSize = 10.5, Foreground = Theme.MutedBrush, VerticalAlignment = VerticalAlignment.Center
            });
            var actions = new StackPanel { Orientation = Orientation.Horizontal };
            var copyBtn = new Button { Content = "复制", Style = (Style)FindResource("RoundButton"), FontSize = 11, Padding = new Thickness(7, 3, 7, 3) };
            copyBtn.Click += (s, e) => { _model.Copy(snippet.Content ?? ""); ShowToast("已复制"); };
            actions.Children.Add(copyBtn);
            bool canDelete = _model.IsJoined ? (_model.RemoteInfo?.AllowDelete ?? false) : _model.Settings.AllowDelete;
            if (canDelete)
            {
                var delBtn = new Button { Content = "删除", Style = (Style)FindResource("RoundButton"), FontSize = 11, Padding = new Thickness(7, 3, 7, 3), Foreground = Theme.RedBrush };
                delBtn.Click += (s, e) =>
                {
                    bool ok = _model.DeleteText(snippet);
                    ShowToast(ok ? "已删除" : "删除失败");
                };
                actions.Children.Add(delBtn);
            }
            DockPanel.SetDock(actions, Dock.Right);
            head.Children.Add(actions);
            sp.Children.Add(head);
            sp.Children.Add(new TextBlock {
                Text = Preview(snippet.Content), FontSize = 12, Foreground = Theme.InkBrush,
                TextWrapping = TextWrapping.Wrap, Margin = new Thickness(0, 7, 0, 0),
                MaxHeight = 120, TextTrimming = TextTrimming.CharacterEllipsis
            });
            tile.Child = sp;
            Theme.FadeIn(tile, index);
            return tile;
        }

        private static string Preview(string content)
        {
            if (string.IsNullOrEmpty(content)) return "";
            return content.Length > 2000 ? content.Substring(0, 2000) + "\n…" : content;
        }

        private void SendText()
        {
            if (_textInput == null) return;
            string text = _textInput.Text;
            if (string.IsNullOrWhiteSpace(text)) { ShowToast("请输入文本"); return; }
            bool ok = _model.SendText(text);
            if (ok) { _textInput.Text = ""; ShowToast("已发送到共享"); }
            else ShowToast("发送失败");
        }
    }
}
