using System;
using System.IO;
using System.Linq;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Input;
using EZShare.Core;
using EZShare.Server;

namespace EZShare.UI
{
    public partial class MainWindow
    {
        private UIElement BuildFilesPage()
        {
            var root = new StackPanel();

            // 顶部操作栏
            var toolbar = new DockPanel { Margin = new Thickness(0, 0, 0, 12), LastChildFill = false };
            var title = new TextBlock {
                Text = _model.BrowseDisplayName, FontSize = 16, FontWeight = FontWeights.SemiBold,
                Foreground = Theme.InkBrush, VerticalAlignment = VerticalAlignment.Center
            };
            DockPanel.SetDock(title, Dock.Left);
            toolbar.Children.Add(title);

            var actions = new StackPanel { Orientation = Orientation.Horizontal, VerticalAlignment = VerticalAlignment.Center };

            var upButton = MakeIconTextButton("↑", "返回上级");
            upButton.Click += (s, e) => { if (!string.IsNullOrEmpty(_model.BrowsePath)) { _model.GoUp(); } };
            actions.Children.Add(upButton);

            var refreshButton = MakeIconTextButton("↻", "刷新");
            refreshButton.Click += (s, e) => { Theme.Spin((Button)s); _model.RefreshContent(); };
            actions.Children.Add(refreshButton);

            var addButton = MakeIconTextButton("＋", "添加文件");
            addButton.Click += AddFiles_Click;
            actions.Children.Add(addButton);

            var mkdirButton = MakeIconTextButton("▣", "新建文件夹");
            mkdirButton.Click += Mkdir_Click;
            actions.Children.Add(mkdirButton);

            var qrButton = MakeIconTextButton("▦", "二维码");
            qrButton.Click += (s, e) => ShowQr();
            actions.Children.Add(qrButton);

            DockPanel.SetDock(actions, Dock.Right);
            toolbar.Children.Add(actions);
            root.Children.Add(toolbar);

            if (!string.IsNullOrEmpty(_model.BrowsePath))
            {
                var crumb = new TextBlock {
                    Text = "共享根 / " + _model.BrowsePath.Replace("/", " / "),
                    FontSize = 11, Foreground = Theme.MutedBrush, Margin = new Thickness(0, 0, 0, 10)
                };
                root.Children.Add(crumb);
            }

            // 文件列表卡片
            var card = Theme.Card(16);
            _fileList = new WrapPanel { Margin = new Thickness(10) };

            if (_model.FileEntries.Count == 0)
            {
                var empty = new StackPanel { HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(0, 60, 0, 60) };
                empty.Children.Add(new TextBlock {
                    Text = "▢", FontSize = 40, Foreground = Theme.LineBrush,
                    HorizontalAlignment = HorizontalAlignment.Center
                });
                empty.Children.Add(new TextBlock {
                    Text = "这个目录还是空的", FontSize = 13, Foreground = Theme.MutedBrush,
                    HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(0, 8, 0, 0)
                });
                empty.Children.Add(new TextBlock {
                    Text = "点击「添加文件」把文件放进共享目录",
                    FontSize = 11, Foreground = Theme.MutedBrush,
                    HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(0, 4, 0, 0)
                });
                _fileList.Children.Add(empty);
            }
            else
            {
                for (int i = 0; i < _model.FileEntries.Count; i++)
                    _fileList.Children.Add(BuildFileTile(_model.FileEntries[i], i));
            }
            card.Child = _fileList;
            root.Children.Add(card);

            // 启动/停止服务提示
            if (!_model.IsRunning && !_model.IsJoined)
            {
                var hint = new Border {
                    Background = new SolidColorBrush(Color.FromRgb(0xF2, 0xF2, 0xF2)),
                    CornerRadius = new CornerRadius(10), Padding = new Thickness(12, 9, 12, 9),
                    Margin = new Thickness(0, 12, 0, 0)
                };
                var hintPanel = new DockPanel();
                var startBtn = new Button { Content = "启动本机服务", Style = (Style)FindResource("PrimaryButton"), Padding = new Thickness(12, 6, 12, 6) };
                startBtn.Click += (s, e) => _model.Start();
                DockPanel.SetDock(startBtn, Dock.Right);
                hintPanel.Children.Add(startBtn);
                hintPanel.Children.Add(new TextBlock {
                    Text = "本机服务未启动，启动后局域网内其他设备可以发现并加入。",
                    FontSize = 11.5, Foreground = new SolidColorBrush(ColorFromHex(0x808080)), VerticalAlignment = VerticalAlignment.Center
                });
                hint.Child = hintPanel;
                root.Children.Add(hint);
            }

            return WrapInScroll(root);
        }

        private Border BuildFileTile(ShareEntry entry, int index)
        {
            var tile = new Border {
                Width = 132, Height = 96, Margin = new Thickness(5),
                Background = new SolidColorBrush(Color.FromRgb(0xF2, 0xF2, 0xF2)),
                CornerRadius = new CornerRadius(10), Padding = new Thickness(9),
                Cursor = Cursors.Hand
            };
            var sp = new StackPanel();
            var color = entry.IsDirectory ? Theme.Teal : MimeColor(entry.Name);
            var glyph = new TextBlock {
                Text = entry.IsDirectory ? "▣" : FileGlyph(entry.Name),
                FontSize = 24, Foreground = new SolidColorBrush(color),
                HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(0, 2, 0, 8)
            };
            sp.Children.Add(glyph);
            var name = new TextBlock {
                Text = entry.Name, FontSize = 11.5, Foreground = Theme.InkBrush,
                HorizontalAlignment = HorizontalAlignment.Center, TextTrimming = TextTrimming.CharacterEllipsis,
                ToolTip = entry.Name
            };
            sp.Children.Add(name);
            var meta = new TextBlock {
                Text = entry.IsDirectory ? "文件夹" : WebUI.SizeText(entry.Size),
                FontSize = 10, Foreground = Theme.MutedBrush,
                HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(0, 3, 0, 0)
            };
            sp.Children.Add(meta);
            tile.Child = sp;

            tile.MouseLeftButtonUp += (s, e) => _model.OpenEntry(entry);
            tile.MouseRightButtonUp += (s, e) => ShowFileMenu(tile, entry);
            Theme.AttachPress(tile);
            Theme.FadeIn(tile, index);
            return tile;
        }

        private void ShowFileMenu(FrameworkElement anchor, ShareEntry entry)
        {
            var menu = new ContextMenu();
            var open = new MenuItem { Header = entry.IsDirectory ? "进入文件夹" : "在资源管理器中显示" };
            open.Click += (s, e) => _model.OpenEntry(entry);
            menu.Items.Add(open);
            if (!entry.IsDirectory && !_model.IsJoined)
            {
                var copyPath = new MenuItem { Header = "复制本地路径" };
                copyPath.Click += (s, e) => { var p = _model.LocalPath(entry); if (p != null) _model.Copy(p); };
                menu.Items.Add(copyPath);
            }
            var del = new MenuItem { Header = "删除", Foreground = Theme.RedBrush };
            del.Click += async (s, e) =>
            {
                if (MessageBox.Show("确定删除「" + entry.Name + "」吗？", "删除确认", MessageBoxButton.YesNo, MessageBoxImage.Question) == MessageBoxResult.Yes)
                {
                    bool ok = await _model.DeleteEntryAsync(entry);
                    ShowToast(ok ? "已删除" : "删除失败");
                }
            };
            menu.Items.Add(del);
            menu.PlacementTarget = anchor;
            menu.IsOpen = true;
        }

        private void AddFiles_Click(object sender, RoutedEventArgs e)
        {
            if (_fileDialog == null)
                _fileDialog = new System.Windows.Forms.OpenFileDialog { Multiselect = true, Title = "选择要添加到共享的文件" };
            if (_fileDialog.ShowDialog() == System.Windows.Forms.DialogResult.OK)
            {
                Task.Run(async () =>
                {
                    bool ok = await _model.AddFilesAsync(_fileDialog.FileNames);
                    Dispatcher.Invoke(() => ShowToast(ok ? "已添加到共享" : "添加失败"));
                });
            }
        }

        private void Mkdir_Click(object sender, RoutedEventArgs e)
        {
            var dialog = new InputDialog("新建文件夹", "文件夹名称：", "新建文件夹") { Owner = this };
            if (dialog.ShowDialog() == true && !string.IsNullOrWhiteSpace(dialog.Value))
            {
                Task.Run(async () =>
                {
                    bool ok = await _model.MkdirAsync(dialog.Value);
                    Dispatcher.Invoke(() => ShowToast(ok ? "已创建文件夹" : "创建失败（可能重名）"));
                });
            }
        }

        private void ShowQr()
        {
            var url = _model.PrimaryUrl;
            if (string.IsNullOrEmpty(url)) { ShowToast("请先启动本机服务"); return; }
            new QRWindow(url, _model.ShareName) { Owner = this }.ShowDialog();
        }

        private Button MakeIconTextButton(string glyph, string tooltip)
        {
            var btn = new Button {
                Content = glyph, ToolTip = tooltip, Width = 30, Height = 30,
                FontSize = 15, Style = (Style)FindResource("RoundButton")
            };
            Theme.AttachPress(btn);
            return btn;
        }

        private static Color MimeColor(string name)
        {
            switch (MimeTypes.Category(name))
            {
                case FileCategory.Image: return Color.FromRgb(0x80, 0x80, 0x80);
                case FileCategory.Video: return Color.FromRgb(0x80, 0x80, 0x80);
                case FileCategory.Audio: return Color.FromRgb(0x80, 0x80, 0x80);
                case FileCategory.Archive: return Color.FromRgb(0x80, 0x80, 0x80);
                case FileCategory.Code: return Color.FromRgb(0x80, 0x80, 0x80);
                case FileCategory.Document: return Color.FromRgb(0x80, 0x80, 0x80);
                default: return Theme.Teal;
            }
        }

        private static string FileGlyph(string name)
        {
            switch (MimeTypes.Category(name))
            {
                case FileCategory.Image: return "▧";
                case FileCategory.Video: return "▶";
                case FileCategory.Audio: return "♫";
                case FileCategory.Archive: return "▤";
                case FileCategory.Code: return "⌘";
                case FileCategory.Document: return "▤";
                default: return "▢";
            }
        }

        private static Color ColorFromHex(uint hex)
        {
            return Color.FromRgb((byte)(hex >> 16), (byte)(hex >> 8), (byte)hex);
        }
    }
}
