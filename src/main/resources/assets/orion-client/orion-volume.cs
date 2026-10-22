using System;
using System.Globalization;
using System.Runtime.InteropServices;

// Master-volume helper for Orion Client.
// No args: prints the current master volume scalar ("0.734").
// One arg (0..1): sets the master volume scalar.
namespace OrionVolumeHelper
{
    [ComImport, Guid("BCDE0395-E52F-467C-8E3D-C4579291692E")]
    internal class MMDeviceEnumeratorComObject
    {
    }

    [Guid("A95664D2-9614-4F35-A746-DE8DB63617E6"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IMMDeviceEnumerator
    {
        int EnumAudioEndpoints(int dataFlow, int stateMask, out IntPtr devices);
        int GetDefaultAudioEndpoint(int dataFlow, int role, out IMMDevice device);
    }

    [Guid("D666063F-1587-4E43-81F1-B948E807363F"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IMMDevice
    {
        int Activate(ref Guid iid, int clsCtx, IntPtr activationParams,
            [MarshalAs(UnmanagedType.IUnknown)] out object activated);
    }

    // Vtable order matters; only the entries up to GetMasterVolumeLevelScalar are used.
    [Guid("5CDF2C82-841E-4546-9722-0CF74078229A"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
    internal interface IAudioEndpointVolume
    {
        int RegisterControlChangeNotify(IntPtr notify);
        int UnregisterControlChangeNotify(IntPtr notify);
        int GetChannelCount(out uint channelCount);
        int SetMasterVolumeLevel(float levelDb, ref Guid eventContext);
        int SetMasterVolumeLevelScalar(float level, ref Guid eventContext);
        int GetMasterVolumeLevel(out float levelDb);
        int GetMasterVolumeLevelScalar(out float level);
    }

    internal static class Program
    {
        private static int Main(string[] args)
        {
            try
            {
                var enumerator = (IMMDeviceEnumerator)new MMDeviceEnumeratorComObject();
                IMMDevice device;
                int hr = enumerator.GetDefaultAudioEndpoint(0 /* eRender */, 1 /* eMultimedia */, out device);
                if (hr != 0 || device == null)
                {
                    return 1;
                }

                Guid iid = typeof(IAudioEndpointVolume).GUID;
                object activated;
                hr = device.Activate(ref iid, 23 /* CLSCTX_ALL */, IntPtr.Zero, out activated);
                if (hr != 0 || activated == null)
                {
                    return 1;
                }

                var volume = (IAudioEndpointVolume)activated;
                if (args.Length > 0)
                {
                    float level;
                    if (!float.TryParse(args[0], NumberStyles.Float, CultureInfo.InvariantCulture, out level))
                    {
                        return 1;
                    }
                    level = Math.Max(0f, Math.Min(1f, level));
                    Guid context = Guid.Empty;
                    volume.SetMasterVolumeLevelScalar(level, ref context);
                }
                else
                {
                    float level;
                    volume.GetMasterVolumeLevelScalar(out level);
                    Console.WriteLine(level.ToString("0.####", CultureInfo.InvariantCulture));
                }

                return 0;
            }
            catch (Exception)
            {
                return 1;
            }
        }
    }
}
