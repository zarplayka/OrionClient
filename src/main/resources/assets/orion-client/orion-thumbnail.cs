using System;
using System.IO;
using System.Threading;
using Windows.Foundation;
using Windows.Media.Control;
using Windows.Storage.Streams;

// Сохраняет обложку медиасессии Windows в args[0]
// Собирается заранее через native/media/build.ps1, без компиляции на компьютере игрока
public static class OrionThumbnail
{
    public static int Main(string[] args)
    {
        if (args.Length != 1)
        {
            return 4;
        }

        try
        {
            var manager = Await(GlobalSystemMediaTransportControlsSessionManager.RequestAsync());

            var session = manager.GetCurrentSession();
            if (session == null)
            {
                return 2;
            }

            var properties = Await(session.TryGetMediaPropertiesAsync());

            if (properties.Thumbnail == null)
            {
                return 3;
            }

            using (var stream = Await(properties.Thumbnail.OpenReadAsync()))
            using (var reader = new DataReader(stream))
            using (var output = File.Create(args[0]))
            {
                uint count;
                while ((count = Await<uint>(reader.LoadAsync(64 * 1024))) > 0)
                {
                    var bytes = new byte[count];
                    reader.ReadBytes(bytes);
                    output.Write(bytes, 0, bytes.Length);
                }
            }

            return 0;
        }
        catch
        {
            return 1;
        }
    }

    private static T Await<T>(IAsyncOperation<T> operation)
    {
        try
        {
            while (operation.Status == AsyncStatus.Started)
            {
                Thread.Sleep(10);
            }
            return operation.GetResults();
        }
        finally
        {
            operation.Close();
        }
    }
}
