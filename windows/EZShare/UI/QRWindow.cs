using System;
using System.IO;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media.Imaging;
using QRCoder;

namespace EZShare.UI
{
    public sealed class QRWindow : Window
    {
        public QRWindow(string url, string title)
        {
            Title = "扫码访问 · " + (title ?? "EZShare");
            Width = 300; Height = 360;
            ResizeMode = ResizeMode.NoResize;
            WindowStartupLocation = WindowStartupLocation.CenterOwner;
            Background = Theme.CardBrush;

            var root = new StackPanel { Margin = new Thickness(20) };
            root.Children.Add(new TextBlock {
                Text = "手机扫码访问", FontSize = 16, FontWeight = FontWeights.SemiBold,
                Foreground = Theme.InkBrush, HorizontalAlignment = HorizontalAlignment.Center,
                Margin = new Thickness(0, 0, 0, 6)
            });
            root.Children.Add(new TextBlock {
                Text = url ?? "", FontSize = 10.5, Foreground = Theme.MutedBrush,
                TextWrapping = TextWrapping.Wrap, HorizontalAlignment = HorizontalAlignment.Center,
                Margin = new Thickness(0, 0, 0, 14)
            });

            var image = new Image { Width = 220, Height = 220, HorizontalAlignment = HorizontalAlignment.Center };
            try
            {
                using (var generator = new QRCodeGenerator())
                using (var data = generator.CreateQrCode(url ?? "", QRCodeGenerator.ECCLevel.Q))
                {
                    var png = new PngByteQRCode(data);
                    byte[] bytes = png.GetQRCode(8, false);
                    var bitmap = new BitmapImage();
                    using (var ms = new MemoryStream(bytes))
                    {
                        bitmap.BeginInit();
                        bitmap.CacheOption = BitmapCacheOption.OnLoad;
                        bitmap.StreamSource = ms;
                        bitmap.EndInit();
                    }
                    bitmap.Freeze();
                    image.Source = bitmap;
                }
            }
            catch { }
            root.Children.Add(image);
            Content = root;
        }
    }
}
