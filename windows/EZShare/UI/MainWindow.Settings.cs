using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using EZShare.Core;

namespace EZShare.UI
{
    public partial class MainWindow
    {
        private TextBox _rootBox;
        private TextBox _portBox;
        private TextBox _codeBox;
        private CheckBox _requireCodeCheck;
        private CheckBox _allowUploadCheck;
        private CheckBox _allowDeleteCheck;

        private UIElement BuildSettingsPage()
        {
            var root = new StackPanel();

            // 共享目录
            var dirCard = SettingCard("共享目录", "局域网内其他设备可以访问这个文件夹");
            var dirPanel = new DockPanel { Margin = new Thickness(0, 4, 0, 0), LastChildFill = true };
            _rootBox = new TextBox {
                Text = _model.Settings.RootPath, FontSize = 12, Height = 30,
                Padding = new Thickness(8, 5, 8, 5), BorderBrush = Theme.LineBrush, BorderThickness = new Thickness(1),
                VerticalContentAlignment = VerticalAlignment.Center, IsReadOnly = true
            };
            var chooseBtn = new Button { Content = "选择文件夹", Style = (Style)FindResource("GhostButton"), Margin = new Thickness(8, 0, 0, 0) };
            chooseBtn.Click += ChooseFolder_Click;
            DockPanel.SetDock(chooseBtn, Dock.Right);
            dirPanel.Children.Add(chooseBtn);
            dirPanel.Children.Add(_rootBox);
            SetCardContent(dirCard, dirPanel);
            root.Children.Add(dirCard);

            // 端口
            var portCard = SettingCard("服务端口", "端口被占用时会自动尝试下一个可用端口");
            _portBox = new TextBox {
                Text = _model.Settings.Port.ToString(), FontSize = 12, Width = 120, Height = 30,
                HorizontalAlignment = HorizontalAlignment.Left, Padding = new Thickness(8, 5, 8, 5),
                BorderBrush = Theme.LineBrush, BorderThickness = new Thickness(1),
                VerticalContentAlignment = VerticalAlignment.Center
            };
            SetCardContent(portCard, _portBox);
            root.Children.Add(portCard);

            // 访问码
            var codeCard = SettingCard("访问保护", "开启后，其他设备加入或访问网页需要输入访问码");
            var codePanel = new StackPanel();
            _requireCodeCheck = new CheckBox {
                Content = "需要访问码", IsChecked = _model.Settings.RequireCode,
                FontSize = 12.5, Foreground = Theme.InkBrush, Margin = new Thickness(0, 0, 0, 10)
            };
            codePanel.Children.Add(_requireCodeCheck);
            var codeRow = new DockPanel { LastChildFill = true };
            _codeBox = new TextBox {
                Text = _model.Settings.AccessCode, FontSize = 13, Height = 30, Width = 160,
                HorizontalAlignment = HorizontalAlignment.Left, Padding = new Thickness(8, 5, 8, 5),
                BorderBrush = Theme.LineBrush, BorderThickness = new Thickness(1),
                FontFamily = new FontFamily("Consolas"), VerticalContentAlignment = VerticalAlignment.Center
            };
            var regenBtn = new Button { Content = "换一个", Style = (Style)FindResource("GhostButton"), Margin = new Thickness(8, 0, 0, 0) };
            regenBtn.Click += (s, e) => { _codeBox.Text = _model.RegenerateCode(); ShowToast("已生成新访问码"); };
            DockPanel.SetDock(regenBtn, Dock.Right);
            var codeRowLeft = new DockPanel { LastChildFill = false };
            codeRowLeft.Children.Add(_codeBox);
            DockPanel.SetDock(regenBtn, Dock.Right);
            codeRowLeft.Children.Add(regenBtn);
            codePanel.Children.Add(codeRowLeft);
            SetCardContent(codeCard, codePanel);
            root.Children.Add(codeCard);

            // 权限开关
            var permCard = SettingCard("共享权限", "控制其他设备能否上传、删除内容");
            var permPanel = new StackPanel();
            _allowUploadCheck = new CheckBox {
                Content = "允许上传文件与发送文本", IsChecked = _model.Settings.AllowUpload,
                FontSize = 12.5, Foreground = Theme.InkBrush, Margin = new Thickness(0, 2, 0, 8)
            };
            _allowDeleteCheck = new CheckBox {
                Content = "允许删除文件与文本", IsChecked = _model.Settings.AllowDelete,
                FontSize = 12.5, Foreground = Theme.InkBrush
            };
            permPanel.Children.Add(_allowUploadCheck);
            permPanel.Children.Add(_allowDeleteCheck);
            SetCardContent(permCard, permPanel);
            root.Children.Add(permCard);

            // 保存
            var saveBtn = new Button {
                Content = "保存设置", Style = (Style)FindResource("PrimaryButton"),
                HorizontalAlignment = HorizontalAlignment.Left, Margin = new Thickness(0, 4, 0, 0),
                Padding = new Thickness(20, 9, 20, 9)
            };
            saveBtn.Click += (s, e) =>
            {
                int port;
                if (!int.TryParse(_portBox.Text, out port) || port < 1024 || port > 65535)
                { ShowToast("端口需在 1024–65535 之间"); return; }
                _model.UpdateSettings(
                    _rootBox.Text, port,
                    _allowUploadCheck.IsChecked == true,
                    _allowDeleteCheck.IsChecked == true,
                    _requireCodeCheck.IsChecked == true,
                    _codeBox.Text);
                ShowToast("设置已保存");
            };
            root.Children.Add(saveBtn);

            var note = new TextBlock {
                Text = "修改端口或访问保护后，需要重启本机服务才能生效。",
                FontSize = 10.5, Foreground = Theme.MutedBrush, Margin = new Thickness(0, 8, 0, 0)
            };
            root.Children.Add(note);

            return WrapInScroll(root);
        }

        private Border SettingCard(string title, string subtitle)
        {
            var card = Theme.Card(16);
            card.Margin = new Thickness(0, 0, 0, 10);
            card.Padding = new Thickness(14, 12, 14, 12);
            var sp = new StackPanel();
            sp.Children.Add(new TextBlock {
                Text = title, FontSize = 13, FontWeight = FontWeights.SemiBold, Foreground = Theme.InkBrush
            });
            sp.Children.Add(new TextBlock {
                Text = subtitle, FontSize = 10.5, Foreground = Theme.MutedBrush, Margin = new Thickness(0, 3, 0, 8)
            });
            // 内容占位容器：调用方把控件放入此容器，标题始终保留。
            var content = new StackPanel();
            sp.Children.Add(content);
            card.Child = sp;
            card.Tag = content;
            return card;
        }

        private static void SetCardContent(Border card, UIElement content)
        {
            var host = card.Tag as StackPanel;
            if (host != null) host.Children.Add(content);
        }

        private void ChooseFolder_Click(object sender, RoutedEventArgs e)
        {
            if (_folderDialog == null)
                _folderDialog = new System.Windows.Forms.FolderBrowserDialog { Description = "选择要共享的文件夹" };
            if (!string.IsNullOrEmpty(_rootBox.Text)) _folderDialog.SelectedPath = _rootBox.Text;
            if (_folderDialog.ShowDialog() == System.Windows.Forms.DialogResult.OK)
                _rootBox.Text = _folderDialog.SelectedPath;
        }
    }
}
