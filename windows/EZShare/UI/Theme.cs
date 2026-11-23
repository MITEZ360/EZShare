using System;
using System.Windows;
using System.Windows.Controls;
using System.Windows.Media;
using System.Windows.Media.Animation;

namespace EZShare.UI
{
    /// <summary>固定亮色 teal/blue 品牌色板，与 Android 端一致。</summary>
    public static class Theme
    {
        public static readonly Color Teal = Color.FromRgb(0x0E, 0x9A, 0x8E);
        public static readonly Color TealDark = Color.FromRgb(0x0B, 0x7E, 0x74);
        public static readonly Color Blue = Color.FromRgb(0x2F, 0x6F, 0xED);
        public static readonly Color Background = Color.FromRgb(0xEE, 0xF1, 0xF5);
        public static readonly Color Card = Colors.White;
        public static readonly Color Ink = Color.FromRgb(0x16, 0x24, 0x2C);
        public static readonly Color Muted = Color.FromRgb(0x6B, 0x7A, 0x86);
        public static readonly Color Line = Color.FromRgb(0xE2, 0xE8, 0xEE);
        public static readonly Color Green = Color.FromRgb(0x10, 0x9A, 0x6A);
        public static readonly Color Amber = Color.FromRgb(0xD9, 0x8A, 0x10);
        public static readonly Color Red = Color.FromRgb(0xD1, 0x4B, 0x4B);

        public static SolidColorBrush Brush(Color color) { return new SolidColorBrush(color); }
        public static SolidColorBrush TealBrush { get { return Brush(Teal); } }
        public static SolidColorBrush BlueBrush { get { return Brush(Blue); } }
        public static SolidColorBrush InkBrush { get { return Brush(Ink); } }
        public static SolidColorBrush MutedBrush { get { return Brush(Muted); } }
        public static SolidColorBrush LineBrush { get { return Brush(Line); } }
        public static SolidColorBrush CardBrush { get { return Brush(Card); } }

        public static readonly TimeSpan Fast = TimeSpan.FromMilliseconds(150);
        public static readonly TimeSpan Normal = TimeSpan.FromMilliseconds(260);

        public static bool ReduceMotion
        {
            get
            {
                try { return SystemParameters.ClientAreaAnimation == false; }
                catch { return false; }
            }
        }

        /// <summary>点击时轻微缩放再回弹，给可点击区域克制的触感反馈。</summary>
        public static void AttachPress(Control control)
        {
            if (ReduceMotion) return;
            control.RenderTransformOrigin = new Point(0.5, 0.5);
            var group = new TransformGroup();
            var scale = new ScaleTransform(1, 1);
            group.Children.Add(scale);
            control.RenderTransform = group;
            control.PreviewMouseDown += (s, e) => AnimateScale(scale, 0.965, 90);
            control.PreviewMouseUp += (s, e) => AnimateScale(scale, 1.0, 180);
            control.PreviewMouseLeave += (s, e) => AnimateScale(scale, 1.0, 180);
        }

        private static void AnimateScale(ScaleTransform scale, double target, int ms)
        {
            var anim = new DoubleAnimation(target, new Duration(TimeSpan.FromMilliseconds(ms)))
            {
                EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
            };
            scale.BeginAnimation(ScaleTransform.ScaleXProperty, anim);
            scale.BeginAnimation(ScaleTransform.ScaleYProperty, anim);
        }

        /// <summary>刷新按钮点击后旋转一圈（620ms）。</summary>
        public static void Spin(Control control)
        {
            if (ReduceMotion) return;
            control.RenderTransformOrigin = new Point(0.5, 0.5);
            var existing = control.RenderTransform as RotateTransform;
            if (existing == null)
            {
                existing = new RotateTransform(0);
                control.RenderTransform = existing;
            }
            var anim = new DoubleAnimation(0, 360, new Duration(TimeSpan.FromMilliseconds(620)))
            {
                EasingFunction = new CubicEase { EasingMode = EasingMode.EaseInOut }
            };
            existing.BeginAnimation(RotateTransform.AngleProperty, anim);
        }

        /// <summary>列表项入场：轻微上浮 + 淡入。</summary>
        public static void FadeIn(UIElement element, int index)
        {
            if (ReduceMotion) return;
            element.Opacity = 0;
            var translate = new TranslateTransform(0, 6);
            element.RenderTransform = translate;
            int delay = Math.Min(index, 10) * 24;
            var fade = new DoubleAnimation(0, 1, new Duration(TimeSpan.FromMilliseconds(260)))
            {
                BeginTime = TimeSpan.FromMilliseconds(delay),
                EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
            };
            var move = new DoubleAnimation(6, 0, new Duration(TimeSpan.FromMilliseconds(260)))
            {
                BeginTime = TimeSpan.FromMilliseconds(delay),
                EasingFunction = new CubicEase { EasingMode = EasingMode.EaseOut }
            };
            element.BeginAnimation(UIElement.OpacityProperty, fade);
            translate.BeginAnimation(TranslateTransform.YProperty, move);
        }

        public static Border Card(double corner = 12)
        {
            return new Border
            {
                Background = CardBrush,
                CornerRadius = new CornerRadius(corner),
                BorderBrush = LineBrush,
                BorderThickness = new Thickness(1),
                SnapsToDevicePixels = true
            };
        }

    }
}
