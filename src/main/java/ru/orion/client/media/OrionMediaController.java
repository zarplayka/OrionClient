package ru.orion.client.media;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfigManager;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Читает и управляет медиасессией Windows через PowerShell
 * Обложки и громкость обслуживают готовые помощники из JAR — компиляции на компьютере игрока нет
 */
public final class OrionMediaController {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrionClient.MOD_ID);
    private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    private static final long POLL_INTERVAL_MS = 3_000L;
    private static final String DELIMITER = "|||";

    private static final String SESSION_SELECTION_SCRIPT = """
              function GetPlayingSession($manager){
                $current=$manager.GetCurrentSession()
                try {
                  if($current -and $current.GetPlaybackInfo().PlaybackStatus.ToString() -eq 'Playing'){ return $current }
                } catch { }
                try {
                  foreach($candidate in @($manager.GetSessions())){
                    try {
                      if($candidate.GetPlaybackInfo().PlaybackStatus.ToString() -eq 'Playing'){ return $candidate }
                    } catch { }
                  }
                } catch { }
                return $current
              }
            """;

    // Controls the current media session directly (reliable; also enables seeking). %ACTION% is one call.
    private static final String CONTROL_SCRIPT = """
            [Console]::OutputEncoding=[System.Text.Encoding]::UTF8
            $ErrorActionPreference='Stop'
            try {
              Add-Type -AssemblyName System.Runtime.WindowsRuntime | Out-Null
              $ext=[System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' }
              $asTask=$ext[0]
              function Await($op,$t){ $m=$asTask.MakeGenericMethod($t); $task=$m.Invoke($null,@($op)); $task.Wait(-1)|Out-Null; $task.Result }
              [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]|Out-Null
              $mgr=Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
              %SESSION_SELECTION%
              $s=GetPlayingSession $mgr
              if($s){ %ACTION% }
            } catch { }
            """.replace("%SESSION_SELECTION%", SESSION_SELECTION_SCRIPT);

    private static final String HELPERS_RESOURCE = "/assets/orion-client/media/";

    private static final String COVER_PATH =
            (System.getProperty("java.io.tmpdir") + "/orion_cover.png").replace('\\', '/');

    // PowerShell snippet that asks the Windows GlobalSystemMediaTransportControls for the current track.
    private static final String NOW_PLAYING_SCRIPT = """
            [Console]::OutputEncoding=[System.Text.Encoding]::UTF8
            $ErrorActionPreference='Stop'
            try {
              Add-Type -AssemblyName System.Runtime.WindowsRuntime | Out-Null
              $ext=[System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object { $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1' }
              $asTask=$ext[0]
              function Await($op,$t){ $m=$asTask.MakeGenericMethod($t); $task=$m.Invoke($null,@($op)); $task.Wait(-1)|Out-Null; $task.Result }
              [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager,Windows.Media.Control,ContentType=WindowsRuntime]|Out-Null
              $mgr=Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
              %SESSION_SELECTION%
              $s=GetPlayingSession $mgr
              if($s){ $p=Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties]); $pb=$s.GetPlaybackInfo(); $st=$pb.PlaybackStatus.ToString(); $tl=$s.GetTimelineProperties(); $now=[DateTimeOffset]::UtcNow; $posMs=$tl.Position.TotalMilliseconds; if($st -eq 'Playing' -and $tl.LastUpdatedTime.Year -gt 2000){ $d=($now - $tl.LastUpdatedTime).TotalMilliseconds; if($d -gt 0){ $posMs += $d } }; $durMs=$tl.EndTime.TotalMilliseconds; if($durMs -gt 0 -and $posMs -gt $durMs){ $posMs=$durMs }; $posMs=[math]::Round($posMs); $durMs=[math]::Round($durMs); $cap=$now.ToUnixTimeMilliseconds(); $sh=''; if($pb.IsShuffleActive -ne $null){ $sh=$pb.IsShuffleActive.ToString() }; $rm=''; if($pb.AutoRepeatMode -ne $null){ $rm=$pb.AutoRepeatMode.ToString() }; $trackTitle=[string]$p.Title; if([string]::IsNullOrWhiteSpace($trackTitle)){ $trackTitle=[string]$p.AlbumTitle }; $trackArtist=[string]$p.Artist; if([string]::IsNullOrWhiteSpace($trackArtist)){ $trackArtist=[string]$p.AlbumArtist }; Write-Output ($trackTitle + '|||' + $trackArtist + '|||' + $st + '|||' + $posMs + '|||' + $durMs + '|||' + $cap + '|||' + $sh + '|||' + $rm) }
            } catch { }
            """.replace("%SESSION_SELECTION%", SESSION_SELECTION_SCRIPT);

    private final OrionConfigManager configManager;

    private volatile String title = "";
    private volatile String artist = "";
    private volatile boolean playing;
    private volatile boolean active;
    private volatile boolean running;
    private volatile long positionMs;
    private volatile long durationMs;
    private volatile long capturedAtMs;
    private volatile int coverGeneration;
    private String lastFetchedTitle = "";
    private String lastPolledTitle = "";
    private long lastRawPositionMs = -1L;

    private volatile boolean shuffleActive;
    private volatile int repeatMode; // 0 = None, 1 = List, 2 = Track
    private volatile float volume = 0.7F;
    private volatile long lastVolumeSendMs;

    private volatile boolean helperReady;
    private boolean helperChecked;
    private String helperExePath;

    private volatile boolean volumeHelperReady;
    private boolean volumeHelperChecked;
    private String volumeHelperExePath;
    private Path helperDirectory;

    private Thread pollThread;

    public OrionMediaController(OrionConfigManager configManager) {
        this.configManager = configManager;
    }

    public void register() {
        running = true;
        pollThread = new Thread(this::pollLoop, "Orion Client Media Poll");
        pollThread.setDaemon(true);
        pollThread.start();

        ClientLifecycleEvents.CLIENT_STOPPING.register(client -> running = false);
    }

    public String getTitle() {
        return title;
    }

    public String getArtist() {
        return artist;
    }

    public boolean isPlaying() {
        return playing;
    }

    public boolean isActive() {
        return active;
    }

    /** Current playback position in seconds, interpolated from the capture timestamp while playing. */
    public double getCurrentSeconds() {
        long current = positionMs;
        if (playing && capturedAtMs != 0L) {
            current += System.currentTimeMillis() - capturedAtMs;
        }
        if (durationMs > 0 && current > durationMs) {
            current = durationMs;
        }
        return Math.max(0L, current) / 1000.0;
    }

    public double getDurationSeconds() {
        return durationMs / 1000.0;
    }

    public void playPause() {
        runControl("Await ($s.TryTogglePlayPauseAsync()) ([bool]) | Out-Null");
        playing = !playing; // optimistic; the next poll corrects it
    }

    public void next() {
        runControl("Await ($s.TrySkipNextAsync()) ([bool]) | Out-Null");
    }

    public void previous() {
        runControl("Await ($s.TrySkipPreviousAsync()) ([bool]) | Out-Null");
    }

    public boolean isShuffleActive() {
        return shuffleActive;
    }

    /** 0 = off, 1 = repeat all, 2 = repeat one. */
    public int getRepeatMode() {
        return repeatMode;
    }

    public float getVolume() {
        return volume;
    }

    /** Toggles shuffle; reads the real state inside the script to avoid stale flips. */
    public void toggleShuffle() {
        shuffleActive = !shuffleActive; // optimistic; the next poll corrects it
        runControl("$pb=$s.GetPlaybackInfo(); $cur=$pb.IsShuffleActive; if($cur -eq $null){$cur=$false}; "
                + "Await ($s.TryChangeShuffleActiveAsync(-not $cur)) ([bool]) | Out-Null");
    }

    /** Cycles repeat: off -> all -> one -> off. */
    public void cycleRepeat() {
        repeatMode = (repeatMode + 1) % 3; // optimistic; the next poll corrects it
        runControl("$pb=$s.GetPlaybackInfo(); $cur=$pb.AutoRepeatMode; "
                + "$next=[Windows.Media.MediaPlaybackAutoRepeatMode]::List; "
                + "if($cur -eq [Windows.Media.MediaPlaybackAutoRepeatMode]::List){$next=[Windows.Media.MediaPlaybackAutoRepeatMode]::Track} "
                + "elseif($cur -eq [Windows.Media.MediaPlaybackAutoRepeatMode]::Track){$next=[Windows.Media.MediaPlaybackAutoRepeatMode]::None}; "
                + "Await ($s.TryChangeAutoRepeatModeAsync($next)) ([bool]) | Out-Null");
    }

    /**
     * Sets the Windows master volume (0..1). The UI value updates immediately; the actual helper
     * call is throttled so slider drags do not spawn a process per frame.
     */
    public void setVolume(float value, boolean force) {
        float clamped = Math.max(0.0F, Math.min(1.0F, value));
        volume = clamped;

        long now = System.currentTimeMillis();
        if (!force && now - lastVolumeSendMs < 120L) {
            return;
        }
        lastVolumeSendMs = now;

        if (!WINDOWS) {
            return;
        }

        Thread thread = new Thread(() -> {
            ensureVolumeHelper();
            if (!volumeHelperReady || volumeHelperExePath == null) {
                return;
            }
            try {
                Process process = new ProcessBuilder(volumeHelperExePath,
                        String.format(Locale.ROOT, "%.4f", clamped)).start();
                if (!process.waitFor(3, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (Exception exception) {
                // volume control unavailable; slider stays optimistic
            }
        }, "Orion Client Volume Set");
        thread.setDaemon(true);
        thread.start();
    }

    private void pollVolume() {
        ensureVolumeHelper();
        if (!volumeHelperReady || volumeHelperExePath == null) {
            return;
        }

        // A fresh user drag wins over the polled value for a moment.
        if (System.currentTimeMillis() - lastVolumeSendMs < 1_500L) {
            return;
        }

        try {
            Process process = new ProcessBuilder(volumeHelperExePath).start();
            if (!process.waitFor(3, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return;
            }
            String line = process.exitValue() == 0 ? readFirstLine(process) : null;
            if (line != null) {
                float parsed = Float.parseFloat(line.trim());
                if (parsed >= 0.0F && parsed <= 1.0F) {
                    volume = parsed;
                }
            }
        } catch (Exception exception) {
            // keep the last known value
        }
    }

    /** Распаковывает готовый помощник громкости один раз */
    private synchronized void ensureVolumeHelper() {
        if (volumeHelperChecked) {
            return;
        }
        volumeHelperChecked = true;

        try {
            volumeHelperExePath = extractHelper("OrionVolume.exe").toString();
            volumeHelperReady = true;
        } catch (IOException exception) {
            LOGGER.warn("Orion Client could not extract the bundled volume helper; volume control unavailable.", exception);
        }
    }

    /** Seeks the current track to the given second (if the app supports position changes). */
    public void seek(double seconds) {
        double target = Math.max(0.0, seconds);
        long ticks = (long) (target * 10_000_000L); // 100-ns units
        runControl("Await ($s.TryChangePlaybackPositionAsync(" + ticks + ")) ([bool]) | Out-Null");
        positionMs = (long) (target * 1000.0);
        capturedAtMs = System.currentTimeMillis();
    }

    /**
     * Runs a single media-session command via PowerShell off-thread (launching PowerShell is slow).
     * Controls the current session directly, which is far more reliable than synthetic media keys.
     */
    private void runControl(String action) {
        if (!WINDOWS) {
            return;
        }

        String script = CONTROL_SCRIPT.replace("%ACTION%", action);
        Thread thread = new Thread(() -> {
            try {
                Process process = new ProcessBuilder(
                        "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", script).start();
                if (!process.waitFor(5, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (Exception exception) {
                LOGGER.warn("Orion Client media control failed.", exception);
            }
        }, "Orion Client Media Control");
        thread.setDaemon(true);
        thread.start();
    }

    private void pollLoop() {
        while (running) {
            try {
                if (WINDOWS && configManager.getConfig().isMediaPlayerEnabled()) {
                    poll();
                } else {
                    clear();
                }
            } catch (Exception exception) {
                clear();
            }

            sleepQuietly();
        }
    }

    private void poll() throws Exception {
        ProcessBuilder builder = new ProcessBuilder(
                "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-Command", NOW_PLAYING_SCRIPT);
        builder.redirectErrorStream(false);

        Process process = builder.start();
        if (!process.waitFor(4, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            clear();
            return;
        }

        String line = readFirstLine(process);

        if (line == null || line.isBlank()) {
            clear();
            return;
        }

        String[] parts = line.split(java.util.regex.Pattern.quote(DELIMITER), -1);
        String newTitle = parts.length > 0 ? parts[0].trim() : "";
        String newArtist = parts.length > 1 ? parts[1].trim() : "";
        boolean newPlaying = parts.length > 2 && "Playing".equalsIgnoreCase(parts[2].trim());
        long newPositionMs = parts.length > 3 ? parseLong(parts[3]) : 0L;
        long newDurationMs = parts.length > 4 ? parseLong(parts[4]) : 0L;
        long newCapturedAtMs = parts.length > 5 ? parseLong(parts[5]) : System.currentTimeMillis();

        // Некоторые плееры не обновляют TimelineProperties: каждый опрос приносит ту же сырую позицию
        // (обычно 0), и она перетирала уже наэкстраполированное время — счётчик прыгал к 0:00.
        // Если трек тот же, статус "играет" и сырая позиция не сдвинулась — базу времени не трогаем.
        boolean staleTimeline = newPlaying && playing
                && newTitle.equals(lastPolledTitle)
                && newPositionMs == lastRawPositionMs;

        title = newTitle;
        artist = newArtist;
        playing = newPlaying;
        durationMs = newDurationMs;
        if (!staleTimeline) {
            positionMs = newPositionMs;
            capturedAtMs = newCapturedAtMs;
        }
        lastPolledTitle = newTitle;
        lastRawPositionMs = newPositionMs;
        active = newPlaying || !newTitle.isEmpty() || !newArtist.isEmpty();

        if (parts.length > 6) {
            shuffleActive = "True".equalsIgnoreCase(parts[6].trim());
        }
        if (parts.length > 7) {
            String mode = parts[7].trim();
            repeatMode = "List".equalsIgnoreCase(mode) ? 1 : "Track".equalsIgnoreCase(mode) ? 2 : 0;
        }

        pollVolume();

        if (active && !newTitle.equals(lastFetchedTitle)) {
            lastFetchedTitle = newTitle;
            fetchCover();
        }
    }

    /** Получает обложку через готовый помощник и обновляет поколение после успешной записи */
    private void fetchCover() {
        if (!WINDOWS) {
            return;
        }

        ensureHelper();
        if (!helperReady || helperExePath == null) {
            return;
        }

        try {
            Path temp = Path.of(COVER_PATH + ".tmp");
            Files.deleteIfExists(temp);

            Process process = new ProcessBuilder(helperExePath, temp.toString())
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
            if (!process.waitFor(6, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return;
            }
            boolean ok = process.exitValue() == 0
                    && Files.isRegularFile(temp) && Files.size(temp) > 0;

            if (ok) {
                Files.move(temp, Path.of(COVER_PATH), StandardCopyOption.REPLACE_EXISTING);
                coverGeneration++;
            } else {
                Files.deleteIfExists(temp);
            }
        } catch (Exception exception) {
            // keep the placeholder note
        }
    }

    /** Распаковывает готовый помощник обложек один раз */
    private synchronized void ensureHelper() {
        if (helperChecked) {
            return;
        }
        helperChecked = true;

        try {
            helperExePath = extractHelper("OrionThumbnail.exe").toString();
            helperReady = true;
        } catch (IOException exception) {
            LOGGER.warn("Orion Client could not extract the bundled cover-art helper; album art unavailable.", exception);
        }
    }

    private synchronized Path extractHelper(String name) throws IOException {
        try (InputStream in = OrionMediaController.class.getResourceAsStream(HELPERS_RESOURCE + name)) {
            if (in == null) {
                throw new IOException("Missing bundled media helper: " + name);
            }
            // Новый каталог на запуск: старые exe из общего временного каталога не используются
            if (helperDirectory == null) {
                helperDirectory = Files.createTempDirectory("orion-media-");
                helperDirectory.toFile().deleteOnExit();
            }
            Path executable = helperDirectory.resolve(name);
            executable.toFile().deleteOnExit();
            Files.copy(in, executable);
            return executable;
        }
    }

    public int getCoverGeneration() {
        return coverGeneration;
    }

    public String getCoverPath() {
        return COVER_PATH;
    }

    private static long parseLong(String value) {
        try {
            return Math.max(0L, Math.round(Double.parseDouble(value.trim())));
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }

    private static String readFirstLine(Process process) throws Exception {
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.isBlank()) {
                    return line;
                }
            }
        }

        return null;
    }

    private void clear() {
        title = "";
        artist = "";
        playing = false;
        active = false;
        positionMs = 0L;
        durationMs = 0L;
        capturedAtMs = 0L;
        lastFetchedTitle = "";
        lastPolledTitle = "";
        lastRawPositionMs = -1L;
    }

    private void sleepQuietly() {
        try {
            Thread.sleep(POLL_INTERVAL_MS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}
