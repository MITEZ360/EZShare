using System;
using System.Collections.Generic;
using System.IO;
using System.Linq;

namespace EZShare.Core
{
    public enum FileCategory { Folder, Image, Video, Audio, Archive, Code, Document, File }

    public static class MimeTypes
    {
        private static readonly Dictionary<string, string> Map = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase)
        {
            {".html","text/html; charset=utf-8"}, {".htm","text/html; charset=utf-8"},
            {".css","text/css; charset=utf-8"}, {".js","application/javascript; charset=utf-8"},
            {".json","application/json; charset=utf-8"}, {".xml","application/xml; charset=utf-8"},
            {".txt","text/plain; charset=utf-8"}, {".md","text/plain; charset=utf-8"},
            {".csv","text/csv; charset=utf-8"}, {".log","text/plain; charset=utf-8"},
            {".pdf","application/pdf"}, {".png","image/png"}, {".jpg","image/jpeg"},
            {".jpeg","image/jpeg"}, {".gif","image/gif"}, {".webp","image/webp"},
            {".svg","image/svg+xml"}, {".ico","image/x-icon"}, {".bmp","image/bmp"},
            {".mp4","video/mp4"}, {".mov","video/quicktime"}, {".mkv","video/x-matroska"},
            {".avi","video/x-msvideo"}, {".webm","video/webm"},
            {".mp3","audio/mpeg"}, {".m4a","audio/mp4"}, {".wav","audio/wav"},
            {".flac","audio/flac"}, {".aac","audio/aac"},
            {".zip","application/zip"}, {".rar","application/vnd.rar"},
            {".7z","application/x-7z-compressed"}, {".tar","application/x-tar"},
            {".gz","application/gzip"},
            {".swift","text/plain; charset=utf-8"}, {".kt","text/plain; charset=utf-8"},
            {".java","text/plain; charset=utf-8"}, {".py","text/plain; charset=utf-8"},
            {".go","text/plain; charset=utf-8"}, {".rs","text/plain; charset=utf-8"},
            {".c","text/plain; charset=utf-8"}, {".cpp","text/plain; charset=utf-8"},
            {".cs","text/plain; charset=utf-8"},
            {".ts","text/plain; charset=utf-8"}, {".sh","text/plain; charset=utf-8"},
            {".doc","application/msword"}, {".docx","application/vnd.openxmlformats-officedocument.wordprocessingml.document"},
            {".xls","application/vnd.ms-excel"}, {".xlsx","application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"},
            {".ppt","application/vnd.ms-powerpoint"}, {".pptx","application/vnd.openxmlformats-officedocument.presentationml.presentation"},
            {".exe","application/octet-stream"}, {".msi","application/octet-stream"}
        };

        public static string Of(string name)
        {
            string ext = Path.GetExtension(name ?? "");
            return Map.TryGetValue(ext, out var value) ? value : "application/octet-stream";
        }

        public static FileCategory Category(string name)
        {
            string ext = (Path.GetExtension(name ?? "") ?? "").ToLowerInvariant();
            if (".png .jpg .jpeg .gif .webp .svg .ico .bmp .heic".Split(' ').Contains(ext)) return FileCategory.Image;
            if (".mp4 .mov .mkv .avi .webm .m4v".Split(' ').Contains(ext)) return FileCategory.Video;
            if (".mp3 .m4a .wav .flac .aac .ogg".Split(' ').Contains(ext)) return FileCategory.Audio;
            if (".zip .rar .7z .tar .gz".Split(' ').Contains(ext)) return FileCategory.Archive;
            if (".swift .kt .java .py .go .rs .c .cpp .cs .js .ts .sh .json .css .html".Split(' ').Contains(ext)) return FileCategory.Code;
            if (".pdf .txt .md .doc .docx .xls .xlsx .ppt .pptx .csv".Split(' ').Contains(ext)) return FileCategory.Document;
            return FileCategory.File;
        }

        public static string CategoryLabel(FileCategory category)
        {
            switch (category)
            {
                case FileCategory.Folder: return "folder";
                case FileCategory.Image: return "image";
                case FileCategory.Video: return "video";
                case FileCategory.Audio: return "audio";
                case FileCategory.Archive: return "archive";
                case FileCategory.Code: return "code";
                case FileCategory.Document: return "document";
                default: return "file";
            }
        }
    }
}
