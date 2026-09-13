using System;
using System.Diagnostics;
using System.IO;
using System.Runtime.InteropServices;
using System.Text;
using System.Threading;

internal static class HtaIconLauncher
{
    private delegate bool EnumWindowsCallback(IntPtr window, IntPtr parameter);

    private const uint ImageIcon = 1;
    private const uint LoadFromFile = 0x0010;
    private const uint WmSetIcon = 0x0080;
    private const int IconSmall = 0;
    private const int IconBig = 1;
    private const int IconSmall2 = 2;
    private const ushort VariantString = 31;
    private static readonly Guid AppUserModelFormatId = new Guid("9F4C2855-9F79-4B39-A8D0-E1D42DE1D5F3");
    private static readonly Guid PropertyStoreInterfaceId = new Guid("886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99");

    [StructLayout(LayoutKind.Sequential, Pack = 4)]
    private struct PropertyKey
    {
        public Guid FormatId;
        public uint PropertyId;

        public PropertyKey(Guid formatId, uint propertyId)
        {
            FormatId = formatId;
            PropertyId = propertyId;
        }
    }

    [StructLayout(LayoutKind.Explicit)]
    private struct PropertyVariant
    {
        [FieldOffset(0)] public ushort VariantType;
        [FieldOffset(8)] public IntPtr PointerValue;

        public static PropertyVariant FromString(string value)
        {
            PropertyVariant result = new PropertyVariant();
            result.VariantType = VariantString;
            result.PointerValue = Marshal.StringToCoTaskMemUni(value);
            return result;
        }
    }

    [ComImport]
    [Guid("886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99")]
    [InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    private interface IPropertyStore
    {
        uint GetCount();
        void GetAt(uint index, out PropertyKey key);
        void GetValue(ref PropertyKey key, out PropertyVariant value);
        void SetValue(ref PropertyKey key, ref PropertyVariant value);
        void Commit();
    }

    [DllImport("user32.dll", CharSet = CharSet.Unicode, SetLastError = true)]
    private static extern IntPtr LoadImage(IntPtr instance, string name, uint type, int width, int height, uint load);

    [DllImport("user32.dll", CharSet = CharSet.Auto)]
    private static extern IntPtr SendMessage(IntPtr window, uint message, IntPtr wParam, IntPtr lParam);

    [DllImport("user32.dll")]
    private static extern bool EnumWindows(EnumWindowsCallback callback, IntPtr parameter);

    [DllImport("user32.dll")]
    private static extern uint GetWindowThreadProcessId(IntPtr window, out uint processId);

    [DllImport("user32.dll")]
    private static extern bool IsWindowVisible(IntPtr window);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetWindowTextLength(IntPtr window);

    [DllImport("user32.dll", CharSet = CharSet.Unicode)]
    private static extern int GetWindowText(IntPtr window, StringBuilder text, int count);

    [DllImport("user32.dll", SetLastError = true)]
    private static extern bool DestroyIcon(IntPtr icon);

    [DllImport("shell32.dll")]
    private static extern int SHGetPropertyStoreForWindow(IntPtr window, ref Guid interfaceId,
        [MarshalAs(UnmanagedType.Interface)] out IPropertyStore propertyStore);

    [DllImport("shell32.dll", CharSet = CharSet.Unicode)]
    private static extern int SHGetPropertyStoreFromParsingName(string path, IntPtr bindContext, uint flags,
        ref Guid interfaceId, [MarshalAs(UnmanagedType.Interface)] out IPropertyStore propertyStore);

    [DllImport("ole32.dll")]
    private static extern int PropVariantClear(ref PropertyVariant variant);

    private static IntPtr FindVisibleWindow(int processId)
    {
        IntPtr result = IntPtr.Zero;
        EnumWindows(delegate(IntPtr window, IntPtr parameter)
        {
            uint ownerProcessId;
            GetWindowThreadProcessId(window, out ownerProcessId);
            if (ownerProcessId == processId && IsWindowVisible(window) && GetWindowTextLength(window) > 0)
            {
                result = window;
                return false;
            }
            return true;
        }, IntPtr.Zero);
        return result;
    }

    private static IntPtr FindVisibleWindow(string expectedTitle)
    {
        IntPtr result = IntPtr.Zero;
        EnumWindows(delegate(IntPtr window, IntPtr parameter)
        {
            if (!IsWindowVisible(window) || GetWindowTextLength(window) <= 0)
            {
                return true;
            }
            StringBuilder title = new StringBuilder(512);
            GetWindowText(window, title, title.Capacity);
            if (string.Equals(title.ToString(), expectedTitle, StringComparison.Ordinal))
            {
                result = window;
                return false;
            }
            return true;
        }, IntPtr.Zero);
        return result;
    }

    private static void SetWindowStringProperty(IPropertyStore store, uint propertyId, string value)
    {
        PropertyKey key = new PropertyKey(AppUserModelFormatId, propertyId);
        PropertyVariant variant = PropertyVariant.FromString(value);
        try
        {
            store.SetValue(ref key, ref variant);
        }
        finally
        {
            PropVariantClear(ref variant);
        }
    }

    private static void SetTaskbarIdentity(IntPtr window, string htaPath, string iconPath)
    {
        IPropertyStore store;
        Guid interfaceId = PropertyStoreInterfaceId;
        if (SHGetPropertyStoreForWindow(window, ref interfaceId, out store) != 0 || store == null)
        {
            return;
        }
        try
        {
            try
            {
                bool isAdmin = string.Equals(Path.GetFileName(htaPath), "admin_data_menu.hta", StringComparison.OrdinalIgnoreCase);
                string appId = isAdmin ? "NRO.AdminData" : "NRO.ServerDashboard.V2";
                string launcherPath = Process.GetCurrentProcess().MainModule.FileName;
                string relaunchCommand = "\"" + launcherPath + "\" \"" + htaPath + "\" \"" + iconPath + "\"";
                SetWindowStringProperty(store, 5, appId);
                SetWindowStringProperty(store, 2, relaunchCommand);
                SetWindowStringProperty(store, 3, iconPath + ",0");
                store.Commit();
            }
            catch
            {
                // WM_SETICON below still provides the correct titlebar icon on older Windows versions.
            }
        }
        finally
        {
            Marshal.ReleaseComObject(store);
        }
    }

    private static void SetShortcutIdentity(string shortcutPath, string appId)
    {
        if (!File.Exists(shortcutPath))
        {
            return;
        }
        IPropertyStore store;
        Guid interfaceId = PropertyStoreInterfaceId;
        if (SHGetPropertyStoreFromParsingName(shortcutPath, IntPtr.Zero, 2, ref interfaceId, out store) != 0 || store == null)
        {
            return;
        }
        try
        {
            SetWindowStringProperty(store, 5, appId);
            store.Commit();
        }
        catch
        {
        }
        finally
        {
            Marshal.ReleaseComObject(store);
        }
    }

    [STAThread]
    private static int Main(string[] args)
    {
        if (args.Length < 2)
        {
            return 2;
        }

        string htaPath = Path.GetFullPath(args[0]);
        string iconPath = Path.GetFullPath(args[1]);
        if (!File.Exists(htaPath) || !File.Exists(iconPath))
        {
            return 3;
        }

        bool isAdminLauncher = string.Equals(Path.GetFileName(htaPath), "admin_data_menu.hta", StringComparison.OrdinalIgnoreCase);
        string shortcutName = isAdminLauncher ? "NRO Admin Data.lnk" : "NRO Server Dashboard.lnk";
        string appUserModelId = isAdminLauncher ? "NRO.AdminData" : "NRO.ServerDashboard.V2";
        string projectRoot = Path.GetDirectoryName(htaPath);
        SetShortcutIdentity(Path.Combine(projectRoot, shortcutName), appUserModelId);
        SetShortcutIdentity(Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.DesktopDirectory), shortcutName), appUserModelId);
        if (args.Length > 2 && string.Equals(args[2], "--prepare", StringComparison.OrdinalIgnoreCase))
        {
            return 0;
        }
        if (args.Length > 2 && string.Equals(args[2], "--attach", StringComparison.OrdinalIgnoreCase))
        {
            string expectedTitle = isAdminLauncher ? "NRO Admin Data" : "NRO Server Dashboard";
            IntPtr existingWindow = FindVisibleWindow(expectedTitle);
            if (existingWindow == IntPtr.Zero)
            {
                return 5;
            }
            SetTaskbarIdentity(existingWindow, htaPath, iconPath);
            return 0;
        }

        Process process = Process.Start(new ProcessStartInfo
        {
            FileName = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.System), "mshta.exe"),
            Arguments = "\"" + htaPath + "\"",
            WorkingDirectory = Path.GetDirectoryName(htaPath),
            UseShellExecute = false,
            CreateNoWindow = true
        });
        if (process == null)
        {
            return 4;
        }

        IntPtr window = IntPtr.Zero;
        for (int attempt = 0; attempt < 100 && !process.HasExited; attempt++)
        {
            process.Refresh();
            window = FindVisibleWindow(process.Id);
            if (window != IntPtr.Zero)
            {
                break;
            }
            Thread.Sleep(50);
        }

        IntPtr smallIcon = IntPtr.Zero;
        IntPtr bigIcon = IntPtr.Zero;
        if (window != IntPtr.Zero)
        {
            SetTaskbarIdentity(window, htaPath, iconPath);
            smallIcon = LoadImage(IntPtr.Zero, iconPath, ImageIcon, 16, 16, LoadFromFile);
            bigIcon = LoadImage(IntPtr.Zero, iconPath, ImageIcon, 32, 32, LoadFromFile);
            if (smallIcon != IntPtr.Zero)
            {
                SendMessage(window, WmSetIcon, new IntPtr(IconSmall), smallIcon);
                SendMessage(window, WmSetIcon, new IntPtr(IconSmall2), smallIcon);
            }
            if (bigIcon != IntPtr.Zero)
            {
                SendMessage(window, WmSetIcon, new IntPtr(IconBig), bigIcon);
            }
        }

        process.WaitForExit();
        if (smallIcon != IntPtr.Zero) DestroyIcon(smallIcon);
        if (bigIcon != IntPtr.Zero) DestroyIcon(bigIcon);
        return process.ExitCode;
    }
}
