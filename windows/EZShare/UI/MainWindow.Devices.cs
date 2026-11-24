using System;
using System.Threading.Tasks;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using EZShare.Core;

namespace EZShare.UI
{
    public partial class MainWindow
    {
        private UIElement BuildDevicesPage()
        {
            var root = new StackPanel();

            var header = new DockPanel { Margin = new Thickness(0, 0, 0, 12), LastChildFill = false };
            header.Children.Add(new TextBlock {
                Text = "局域网设备", FontSize = 16, FontWeight = FontWeights.SemiBold,
                Foreground = Theme.InkBrush, VerticalAlignment = VerticalAlignment.Center
            });
            var refreshBtn = new Button { Content = "↻ 刷新", Style = (Style)FindResource("GhostButton") };
            refreshBtn.Click += (s, e) => { Theme.Spin((Button)s); _model.RefreshPeers(); ShowToast("正在发现设备…"); };
            DockPanel.SetDock(refreshBtn, Dock.Right);
            header.Children.Add(refreshBtn);
            root.Children.Add(header);

            root.Children.Add(new TextBlock {
                Text = "启动后会自动发现一次，也可手动刷新。发现其他设备后可以加入其共享。",
                FontSize = 11, Foreground = Theme.MutedBrush, Margin = new Thickness(0, 0, 0, 12)
            });

            _deviceList = new StackPanel();

            if (_model.Peers.Count == 0)
            {
                var empty = Theme.Card(12);
                var sp = new StackPanel { Margin = new Thickness(0, 44, 0, 44), HorizontalAlignment = HorizontalAlignment.Center };
                sp.Children.Add(new TextBlock {
                    Text = "◎", FontSize = 40, Foreground = Theme.LineBrush, HorizontalAlignment = HorizontalAlignment.Center
                });
                sp.Children.Add(new TextBlock {
                    Text = "没有发现其他设备", FontSize = 13, Foreground = Theme.MutedBrush,
                    HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(0, 8, 0, 0)
                });
                sp.Children.Add(new TextBlock {
                    Text = "确保其他设备已启动共享，且处于同一局域网",
                    FontSize = 11, Foreground = Theme.MutedBrush,
                    HorizontalAlignment = HorizontalAlignment.Center, Margin = new Thickness(0, 4, 0, 0)
                });
                empty.Child = sp;
                _deviceList.Children.Add(empty);
            }
            else
            {
                for (int i = 0; i < _model.Peers.Count; i++)
                    _deviceList.Children.Add(BuildPeerTile(_model.Peers[i], i));
            }
            root.Children.Add(_deviceList);

            if (_model.IsJoined)
            {
                var leaveBar = new Border {
                    Background = new SolidColorBrush(Color.FromRgb(0xF2, 0xF2, 0xF2)),
                    CornerRadius = new CornerRadius(10), Padding = new Thickness(12, 10, 12, 10),
                    Margin = new Thickness(0, 14, 0, 0)
                };
                var dock = new DockPanel();
                var leaveBtn = new Button { Content = "退出共享", Style = (Style)FindResource("GhostButton") };
                leaveBtn.Click += (s, e) => { _model.LeaveRemote(); ShowToast("已退出远端共享"); };
                DockPanel.SetDock(leaveBtn, Dock.Right);
                dock.Children.Add(leaveBtn);
                dock.Children.Add(new TextBlock {
                    Text = "当前已加入 " + (_model.RemoteInfo?.Name ?? "远端共享"),
                    FontSize = 12, Foreground = Theme.BlueBrush, VerticalAlignment = VerticalAlignment.Center
                });
                leaveBar.Child = dock;
                root.Children.Add(leaveBar);
            }

            return WrapInScroll(root);
        }

        private Border BuildPeerTile(DiscoveredPeer peer, int index)
        {
            var card = Theme.Card(16);
            card.Margin = new Thickness(0, 0, 0, 8);
            card.Padding = new Thickness(14, 12, 14, 12);
            var dock = new DockPanel { LastChildFill = false };

            var joinBtn = new Button { Content = peer.RequiresCode ? "加入（需访问码）" : "加入", Style = (Style)FindResource("PrimaryButton") };
            joinBtn.Click += async (s, e) =>
            {
                string code = "";
                if (peer.RequiresCode)
                {
                    var dialog = new InputDialog("加入共享", "访问码：", "") { Owner = this };
                    if (dialog.ShowDialog() != true) return;
                    code = dialog.Value;
                }
                var btn = (Button)s;
                btn.IsEnabled = false;
                bool ok = await _model.JoinAsync(peer, code);
                Dispatcher.Invoke(() =>
                {
                    btn.IsEnabled = true;
                    ShowToast(ok ? "已加入共享" : (_model.LastError ?? "加入失败"));
                    if (ok) _tab = 0;
                });
            };
            DockPanel.SetDock(joinBtn, Dock.Right);
            dock.Children.Add(joinBtn);

            var info = new StackPanel();
            info.Children.Add(new TextBlock {
                Text = peer.Name, FontSize = 13, FontWeight = FontWeights.SemiBold, Foreground = Theme.InkBrush
            });
            info.Children.Add(new TextBlock {
                Text = peer.Host + ":" + peer.Port + (peer.RequiresCode ? " · 需要访问码" : ""),
                FontSize = 10.5, Foreground = Theme.MutedBrush, Margin = new Thickness(0, 3, 0, 0)
            });
            dock.Children.Add(info);
            card.Child = dock;
            Theme.FadeIn(card, index);
            return card;
        }
    }
}
