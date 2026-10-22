package ru.orion.client.effect;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import java.awt.Desktop;
import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

/**
 * HitSound: проигрывает звук при ударе по сущности. Пять встроенных синтезированных
 * звуков (стерео) + режим «Свои»: любые .wav из config/orion-client/hitsounds/
 * (если файлов несколько — играется случайный). Громкость 0–100%.
 */
public final class OrionHitSound {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrionClient.MOD_ID);

    /** Порядок совпадает с индексом hitSoundIndex в конфиге. */
    public static final String[] SOUND_NAMES = {"Bell", "Pop", "Laser", "Punch", "Retro", "Стон", "Свои (папка)"};
    public static final int CUSTOM_INDEX = 6;

    private static final String[] BUILTIN_FILES = {"bell.wav", "pop.wav", "laser.wav", "punch.wav", "retro.wav", "moan.wav"};
    private static final String RESOURCE_DIR = "/assets/orion-client/sounds/hitsounds/";
    private static final long MIN_INTERVAL_MS = 40L; // защита от дублей за один свинг
    private static final int CACHE_LIMIT = 16;

    private final OrionConfigManager configManager;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "orion-hitsound");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, byte[]> cache = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private volatile long lastPlayMs;

    public OrionHitSound(OrionConfigManager configManager) {
        this.configManager = configManager;
    }

    public void register() {
        try {
            Files.createDirectories(customDir());
        } catch (IOException exception) {
            LOGGER.warn("HitSound: не удалось создать папку своих звуков.", exception);
        }
    }

    /** Папка пользовательских звуков: config/orion-client/hitsounds. */
    public Path customDir() {
        return FabricLoader.getInstance().getConfigDir().resolve("orion-client").resolve("hitsounds");
    }

    /** Вызывается из MultiPlayerGameModeMixin при атаке. */
    public void onHit(Entity target) {
        OrionConfig config = configManager.getConfig();
        if (!config.isHitSoundEnabled()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastPlayMs < MIN_INTERVAL_MS) {
            return;
        }
        lastPlayMs = now;
        play(config.getHitSoundIndex(), config.getHitSoundVolume());
    }

    /** Кнопка «Тест» в ClickGUI — без дебаунса. */
    public void preview() {
        OrionConfig config = configManager.getConfig();
        play(config.getHitSoundIndex(), config.getHitSoundVolume());
    }

    /** Открывает папку своих звуков в проводнике. */
    public void openCustomFolder() {
        executor.execute(() -> {
            try {
                Files.createDirectories(customDir());
                Desktop.getDesktop().open(customDir().toFile());
            } catch (Exception exception) {
                LOGGER.warn("HitSound: не удалось открыть папку {}.", customDir(), exception);
            }
        });
    }

    private void play(int index, int volumePercent) {
        if (volumePercent <= 0) {
            return;
        }
        executor.execute(() -> {
            try {
                byte[] data = resolveBytes(index);
                if (data != null) {
                    playClip(data, volumePercent);
                }
            } catch (Exception exception) {
                LOGGER.warn("HitSound: не удалось проиграть звук.", exception);
            }
        });
    }

    private byte[] resolveBytes(int index) throws IOException {
        if (index == CUSTOM_INDEX) {
            byte[] custom = resolveCustomBytes();
            if (custom != null) {
                return custom;
            }
            // Папка пуста — падаем на первый встроенный, чтобы модуль не молчал.
            return builtinBytes(0);
        }
        return builtinBytes(Math.max(0, Math.min(BUILTIN_FILES.length - 1, index)));
    }

    private byte[] builtinBytes(int index) throws IOException {
        String name = BUILTIN_FILES[index];
        byte[] cached = cache.get(name);
        if (cached != null) {
            return cached;
        }
        try (InputStream stream = OrionHitSound.class.getResourceAsStream(RESOURCE_DIR + name)) {
            if (stream == null) {
                LOGGER.warn("HitSound: встроенный звук {} не найден в ресурсах.", name);
                return null;
            }
            byte[] data = stream.readAllBytes();
            cache.put(name, data);
            return data;
        }
    }

    private byte[] resolveCustomBytes() throws IOException {
        Path dir = customDir();
        if (!Files.isDirectory(dir)) {
            return null;
        }
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(dir)) {
            stream.filter(path -> {
                String lower = path.getFileName().toString().toLowerCase(Locale.ROOT);
                return lower.endsWith(".wav") || lower.endsWith(".aiff") || lower.endsWith(".au");
            }).forEach(files::add);
        }
        if (files.isEmpty()) {
            return null;
        }

        Path pick = files.get(random.nextInt(files.size()));
        String key = pick + "|" + Files.size(pick) + "|" + Files.getLastModifiedTime(pick).toMillis();
        byte[] cached = cache.get(key);
        if (cached != null) {
            return cached;
        }
        byte[] data = Files.readAllBytes(pick);
        if (cache.size() >= CACHE_LIMIT) {
            cache.clear();
        }
        cache.put(key, data);
        return data;
    }

    private static void playClip(byte[] data, int volumePercent) throws Exception {
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(
                new BufferedInputStream(new ByteArrayInputStream(data)))) {
            Clip clip = AudioSystem.getClip();
            clip.open(stream);
            if (!applyVolume(clip, volumePercent)) {
                // Без доступного регулятора нельзя безопасно воспроизвести звук тише 100%.
                clip.close();
                return;
            }
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    clip.close();
                }
            });
            clip.start();
        }
    }

    private static boolean applyVolume(Clip clip, int volumePercent) {
        float normalized = Math.max(0.0F, Math.min(100.0F, volumePercent)) / 100.0F;
        // Квадратичная шкала даёт заметно более тихие значения в нижней части слайдера:
        // 2% превращается примерно в -68 dB вместо прежних -34 dB.
        float volume = normalized * normalized;

        if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            FloatControl gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            float db = volume <= 0.0F
                    ? gain.getMinimum()
                    : (float) (20.0 * Math.log10(volume));
            gain.setValue(clamp(db, gain.getMinimum(), gain.getMaximum()));
            return true;
        }

        if (clip.isControlSupported(FloatControl.Type.VOLUME)) {
            FloatControl linearVolume = (FloatControl) clip.getControl(FloatControl.Type.VOLUME);
            float value = linearVolume.getMinimum()
                    + volume * (linearVolume.getMaximum() - linearVolume.getMinimum());
            linearVolume.setValue(clamp(value, linearVolume.getMinimum(), linearVolume.getMaximum()));
            return true;
        }

        return volume >= 1.0F;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
