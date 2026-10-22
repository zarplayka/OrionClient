package ru.orion.client.effect;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ProtectName: подменяет реальный ник игрока на псевдоним из настроек во всём
 * отрисовываемом тексте. Работает через перехват Font.prepareText (см. FontMixin),
 * поэтому покрывает таб, скорборд, чат, неймтеги, заголовки — любой текст,
 * который проходит через шрифтовой рендер.
 */
public final class OrionNameProtect {
    private OrionNameProtect() {
    }

    /** Модуль активен: включён в конфиге, псевдоним задан и отличается от реального ника. */
    public static boolean isActive() {
        OrionConfig config = OrionClient.getConfigOrNull();
        if (config == null || !config.isProtectNameEnabled()) {
            return false;
        }

        String fake = config.getProtectName();
        String real = realName();
        return fake != null && !fake.isBlank()
                && real != null && real.length() >= 3
                && !fake.equals(real);
    }

    public static String replace(String text) {
        if (text == null || text.isEmpty() || !isActive()) {
            return text;
        }

        String real = realName();
        if (!text.contains(real)) {
            return text;
        }

        return text.replace(real, fakeName());
    }

    /**
     * Подмена в FormattedText: этим типом Font.width(...) меряет текст при вёрстке
     * (ширина скорборда, таба и т.п.) — без неё псевдоним вылезал за границы панели.
     */
    public static FormattedText replace(FormattedText text) {
        if (text == null || !isActive()) {
            return text;
        }

        String real = realName();
        if (!text.getString().contains(real)) {
            return text;
        }

        String fake = fakeName();
        List<FormattedText> parts = new ArrayList<>();
        text.visit((style, part) -> {
            parts.add(FormattedText.of(part.replace(real, fake), style));
            return Optional.empty();
        }, Style.EMPTY);
        return FormattedText.composite(parts);
    }

    public static Component replace(Component component) {
        if (component == null) {
            return null;
        }
        FormattedText replaced = replace((FormattedText) component);
        if (replaced == component) {
            return component;
        }

        MutableComponent result = Component.empty();
        replaced.visit((style, part) -> {
            result.append(Component.literal(part).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    public static FormattedCharSequence replace(FormattedCharSequence sequence) {
        if (sequence == null || !isActive()) {
            return sequence;
        }

        String real = realName();

        // Быстрый проход: если ника в тексте нет — возвращаем последовательность как есть.
        StringBuilder plainBuilder = new StringBuilder();
        sequence.accept((index, style, codepoint) -> {
            plainBuilder.appendCodePoint(codepoint);
            return true;
        });
        String plain = plainBuilder.toString();
        if (!plain.contains(real)) {
            return sequence;
        }

        // Полное декодирование со стилями посимвольно.
        IntArrayList codepoints = new IntArrayList(plain.length());
        List<Style> styles = new ArrayList<>(plain.length());
        sequence.accept((index, style, codepoint) -> {
            codepoints.add(codepoint);
            styles.add(style);
            return true;
        });

        String fake = fakeName();
        int[] fakeCodepoints = fake.codePoints().toArray();
        int realCodepointCount = real.codePointCount(0, real.length());
        IntArrayList outCodepoints = new IntArrayList(codepoints.size());
        List<Style> outStyles = new ArrayList<>(styles.size());

        int charPos = 0;
        int cpIndex = 0;
        while (cpIndex < codepoints.size()) {
            if (plain.startsWith(real, charPos)) {
                // Псевдоним наследует стиль первого символа реального ника.
                Style style = styles.get(cpIndex);
                for (int codepoint : fakeCodepoints) {
                    outCodepoints.add(codepoint);
                    outStyles.add(style);
                }
                cpIndex += realCodepointCount;
                charPos += real.length();
            } else {
                int codepoint = codepoints.getInt(cpIndex);
                outCodepoints.add(codepoint);
                outStyles.add(styles.get(cpIndex));
                cpIndex++;
                charPos += Character.charCount(codepoint);
            }
        }

        int count = outCodepoints.size();
        return sink -> {
            for (int i = 0; i < count; i++) {
                if (!sink.accept(i, outStyles.get(i), outCodepoints.getInt(i))) {
                    return false;
                }
            }
            return true;
        };
    }

    private static String fakeName() {
        OrionConfig config = OrionClient.getConfigOrNull();
        return config == null ? "" : config.getProtectName();
    }

    private static String realName() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getUser() == null) {
            return null;
        }

        return client.getUser().getName();
    }
}
