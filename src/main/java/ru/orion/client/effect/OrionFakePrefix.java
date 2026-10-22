package ru.orion.client.effect;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.scores.PlayerTeam;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** Клиентская подмена префикса локального игрока с поддержкой JSON-компонентов. */
public final class OrionFakePrefix {
    private static String cachedSource;
    private static Component cachedComponent = Component.empty();
    private static String cachedPlayerName;
    private static String cachedDetectedPrefix;

    private OrionFakePrefix() {
    }

    public static String replace(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }

        Replacement replacement = replacement(text);
        if (replacement == null || !text.contains(replacement.original())) {
            return text;
        }
        return replacePlainText(text, replacement);
    }

    public static FormattedText replace(FormattedText text) {
        if (text == null) {
            return null;
        }

        String plainText = text.getString();
        Replacement replacement = replacement(plainText);
        if (replacement == null || !plainText.contains(replacement.original())) {
            return text;
        }

        StyledCodepoints source = decode(text);
        int[] prefix = replacement.original().codePoints().toArray();
        List<int[]> playerNames = codepointNames(replacement.playerNames());
        boolean prefixOnlyComponent = isStatusLine(plainText) || isPrefixOnlyComponent(plainText, replacement.original());
        boolean fakeEndsWithWhitespace = endsWithWhitespace(replacement.fake().getString());
        List<FormattedText> parts = new ArrayList<>();
        StringBuilder unchanged = new StringBuilder();
        Style unchangedStyle = null;

        for (int index = 0; index < source.codepoints().size();) {
            Match match = matchAt(source.codepoints(), prefix, playerNames, index, prefixOnlyComponent,
                    fakeEndsWithWhitespace);
            if (match != null) {
                flush(parts, unchanged, unchangedStyle);
                unchangedStyle = null;
                parts.add(replacement.fake());
                index += match.consumedCodepoints();
                continue;
            }

            Style style = source.styles().get(index);
            if (unchangedStyle != null && !Objects.equals(unchangedStyle, style)) {
                flush(parts, unchanged, unchangedStyle);
                unchangedStyle = null;
            }
            if (unchangedStyle == null) {
                unchangedStyle = style;
            }
            unchanged.appendCodePoint(source.codepoints().getInt(index));
            index++;
        }
        flush(parts, unchanged, unchangedStyle);
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
        if (sequence == null) {
            return null;
        }

        StyledCodepoints source = decode(sequence);
        Replacement replacement = replacement(source.plainText());
        if (replacement == null || !source.plainText().contains(replacement.original())) {
            return sequence;
        }

        StyledCodepoints fake = decode(replacement.fake().getVisualOrderText());
        int[] prefix = replacement.original().codePoints().toArray();
        List<int[]> playerNames = codepointNames(replacement.playerNames());
        boolean prefixOnlyComponent = isStatusLine(source.plainText())
                || isPrefixOnlyComponent(source.plainText(), replacement.original());
        boolean fakeEndsWithWhitespace = endsWithWhitespace(replacement.fake().getString());
        IntArrayList outputCodepoints = new IntArrayList(source.codepoints().size());
        List<Style> outputStyles = new ArrayList<>(source.styles().size());

        for (int index = 0; index < source.codepoints().size();) {
            Match match = matchAt(source.codepoints(), prefix, playerNames, index, prefixOnlyComponent,
                    fakeEndsWithWhitespace);
            if (match != null) {
                outputCodepoints.addAll(fake.codepoints());
                outputStyles.addAll(fake.styles());
                index += match.consumedCodepoints();
            } else {
                outputCodepoints.add(source.codepoints().getInt(index));
                outputStyles.add(source.styles().get(index));
                index++;
            }
        }

        int count = outputCodepoints.size();
        return sink -> {
            for (int index = 0; index < count; index++) {
                if (!sink.accept(index, outputStyles.get(index), outputCodepoints.getInt(index))) {
                    return false;
                }
            }
            return true;
        };
    }

    /** Чат позволяет точно отделить последний донатный компонент от voice-иконки перед ним. */
    public static FormattedCharSequence replaceChat(FormattedCharSequence sequence) {
        if (sequence == null) {
            return null;
        }

        String text = decode(sequence).plainText();
        String playerName = playerName();
        String detected = null;
        for (String name : configuredPlayerNames()) {
            detected = detectPrefixBeforePlayer(text, name);
            if (detected != null) {
                break;
            }
        }
        if (detected != null && isPartOfTeamPrefix(detected)) {
            if (!playerName.equals(cachedPlayerName)) {
                cachedPlayerName = playerName;
            }
            cachedDetectedPrefix = detected;
        }
        return replace(sequence);
    }

    private static String replacePlainText(String text, Replacement replacement) {
        String original = replacement.original();
        List<String> playerNames = replacement.playerNames();
        String fake = replacement.fake().getString();
        boolean prefixOnlyComponent = isStatusLine(text) || isPrefixOnlyComponent(text, original);
        boolean fakeEndsWithWhitespace = endsWithWhitespace(fake);
        StringBuilder result = new StringBuilder(text.length() - original.length() + fake.length());
        int cursor = 0;

        while (cursor < text.length()) {
            int prefixIndex = text.indexOf(original, cursor);
            if (prefixIndex < 0) {
                result.append(text, cursor, text.length());
                break;
            }

            int afterPrefix = prefixIndex + original.length();
            int nameIndex = afterPrefix;
            while (nameIndex < text.length() && Character.isWhitespace(text.charAt(nameIndex))) {
                nameIndex++;
            }
            boolean belongsToPlayer = matchesAnyName(text, nameIndex, playerNames);
            if (!belongsToPlayer && !prefixOnlyComponent) {
                result.append(text, cursor, afterPrefix);
                cursor = afterPrefix;
                continue;
            }

            result.append(text, cursor, prefixIndex).append(fake);
            cursor = belongsToPlayer && fakeEndsWithWhitespace ? nameIndex : afterPrefix;
        }
        return result.toString();
    }

    private static Replacement replacement(String observedText) {
        OrionConfig config = OrionClient.getConfigOrNull();
        if (config == null || !config.isFakePrefixEnabled()) {
            return null;
        }

        String playerName = playerName();
        List<String> playerNames = configuredPlayerNames();
        if (playerName.isBlank() || playerNames.isEmpty()) {
            return null;
        }
        DetectedPrefix detected = detectConfiguredPlayerPrefix(observedText, playerNames);
        String original = detected == null ? currentPrefix(observedText, playerName) : detected.prefix();
        if (original == null || original.isBlank()) {
            return null;
        }

        Component fake = parse(config.getFakePrefix());
        // Одинаковый символ может отображать совершенно разные bitmap-глифы в разных JSON-шрифтах.
        // Поэтому сравнивать исходный и поддельный префиксы только по getString() нельзя.
        if (fake.getString().isEmpty()) {
            return null;
        }
        return new Replacement(original, fake, playerNames);
    }

    private static DetectedPrefix detectConfiguredPlayerPrefix(String text, List<String> playerNames) {
        for (String playerName : playerNames) {
            String prefix = detectPrefixBeforePlayer(text, playerName);
            if (prefix != null && isPartOfPlayerTeamPrefix(playerName, prefix)) {
                return new DetectedPrefix(prefix, playerName);
            }
        }
        return null;
    }

    private static boolean isPartOfPlayerTeamPrefix(String playerName, String candidate) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.getConnection() == null) {
            return false;
        }
        PlayerInfo info = client.getConnection().getPlayerInfoIgnoreCase(playerName);
        PlayerTeam team = info == null ? null : info.getTeam();
        return team != null && team.getPlayerPrefix().getString().contains(candidate);
    }

    private static String currentPrefix(String observedText, String playerName) {
        if (!playerName.equals(cachedPlayerName)) {
            cachedPlayerName = playerName;
            cachedDetectedPrefix = null;
        }

        if (cachedDetectedPrefix != null
                && (containsPrefixForPlayer(observedText, cachedDetectedPrefix, playerName)
                || isStatusLine(observedText) && observedText.contains(cachedDetectedPrefix))) {
            return cachedDetectedPrefix;
        }

        String detected = detectStatusPrefix(observedText);
        if (detected != null) {
            cachedDetectedPrefix = detected;
            return detected;
        }

        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) {
            return cachedDetectedPrefix;
        }

        PlayerTeam team = client.player.getTeam();
        if (team != null) {
            String teamPrefix = prefixWithoutVoiceChat(team.getPlayerPrefix());
            if (teamPrefix != null) {
                cachedDetectedPrefix = teamPrefix;
                return teamPrefix;
            }
        }

        detected = detectPrefixBeforePlayer(observedText, playerName);
        if (detected != null) {
            cachedDetectedPrefix = detected;
            return detected;
        }
        return cachedDetectedPrefix;
    }

    private static String prefixWithoutVoiceChat(Component component) {
        StringBuilder result = new StringBuilder();
        component.visit((style, part) -> {
            if (!isVoiceChatFont(style.getFont())) {
                result.append(part);
            }
            return Optional.empty();
        }, Style.EMPTY);
        return normalizePrefix(result.toString());
    }

    private static boolean isVoiceChatFont(FontDescription font) {
        if (!(font instanceof FontDescription.Resource resource)) {
            return false;
        }
        String id = resource.id().toString().toLowerCase(Locale.ROOT);
        return id.contains("voicechat") || id.contains("voice_chat") || id.contains("simple_voice");
    }

    private static boolean isPartOfTeamPrefix(String candidate) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null || client.player.getTeam() == null) {
            return true;
        }
        return client.player.getTeam().getPlayerPrefix().getString().contains(candidate);
    }

    private static String detectStatusPrefix(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        int labelIndex = lower.indexOf("статус");
        if (labelIndex < 0) {
            labelIndex = lower.indexOf("status");
        }
        if (labelIndex < 0) {
            return null;
        }
        int colonIndex = text.indexOf(':', labelIndex);
        return colonIndex < 0 ? null : normalizePrefix(text.substring(colonIndex + 1));
    }

    private static String detectPrefixBeforePlayer(String text, String playerName) {
        if (text == null || text.isBlank()) {
            return null;
        }
        int nameIndex = indexOfIgnoreCase(text, playerName);
        if (nameIndex <= 0) {
            return null;
        }

        int end = nameIndex;
        while (end > 0 && Character.isWhitespace(text.charAt(end - 1))) {
            end--;
        }
        int start = end;
        while (start > 0 && !Character.isWhitespace(text.charAt(start - 1))) {
            start--;
        }

        String candidate = normalizePrefix(text.substring(start, end));
        if (candidate == null || candidate.endsWith(":") || candidate.endsWith("：")) {
            return null;
        }
        return candidate;
    }

    private static boolean containsPrefixForPlayer(String text, String prefix, String playerName) {
        if (text == null || prefix == null) {
            return false;
        }
        int prefixIndex = text.indexOf(prefix);
        while (prefixIndex >= 0) {
            int nameIndex = prefixIndex + prefix.length();
            while (nameIndex < text.length() && Character.isWhitespace(text.charAt(nameIndex))) {
                nameIndex++;
            }
            if (text.regionMatches(true, nameIndex, playerName, 0, playerName.length())) {
                return true;
            }
            prefixIndex = text.indexOf(prefix, prefixIndex + prefix.length());
        }
        return false;
    }

    private static int indexOfIgnoreCase(String text, String target) {
        int lastStart = text.length() - target.length();
        for (int index = 0; index <= lastStart; index++) {
            if (text.regionMatches(true, index, target, 0, target.length())) {
                return index;
            }
        }
        return -1;
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null) {
            return null;
        }
        String normalized = prefix.strip();
        return normalized.isEmpty() ? null : normalized;
    }

    private static String playerName() {
        Minecraft client = Minecraft.getInstance();
        return client == null || client.player == null ? "" : client.player.getScoreboardName();
    }

    private static List<String> configuredPlayerNames() {
        List<String> names = new ArrayList<>();
        String ownName = playerName();
        if (!ownName.isBlank()) {
            names.add(ownName);
        }

        OrionConfig config = OrionClient.getConfigOrNull();
        String configured = config == null ? "" : config.getFakePrefixAdditionalNames();
        for (String candidate : configured.split("[,;\\s]+")) {
            if (!candidate.matches("[A-Za-z0-9_]{1,16}")) {
                continue;
            }
            boolean duplicate = names.stream().anyMatch(existing -> existing.equalsIgnoreCase(candidate));
            if (!duplicate) {
                names.add(candidate);
            }
        }
        return List.copyOf(names);
    }

    /** Порядок закреплённых игроков в TAB: локальный игрок, затем дополнительные ники. */
    public static List<String> prioritizedPlayerNames() {
        OrionConfig config = OrionClient.getConfigOrNull();
        return config == null || !config.isFakePrefixEnabled() ? List.of() : configuredPlayerNames();
    }

    public static Component configuredPrefixComponent() {
        OrionConfig config = OrionClient.getConfigOrNull();
        return config == null || !config.isFakePrefixEnabled() ? Component.empty() : parse(config.getFakePrefix());
    }

    private static boolean matchesAnyName(String text, int index, List<String> playerNames) {
        for (String playerName : playerNames) {
            if (text.regionMatches(true, index, playerName, 0, playerName.length())) {
                return true;
            }
        }
        return false;
    }

    private static List<int[]> codepointNames(List<String> playerNames) {
        return playerNames.stream().map(name -> name.codePoints().toArray()).toList();
    }

    private static Component parse(String source) {
        String safeSource = source == null ? "" : source;
        if (safeSource.equals(cachedSource)) {
            return cachedComponent;
        }

        Component parsed = null;
        try {
            parsed = ComponentSerialization.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(safeSource))
                    .result()
                    .orElse(null);
        } catch (RuntimeException ignored) {
            // Обычный текст и незавершённый JSON во время ввода отображаются буквально.
        }
        cachedSource = safeSource;
        cachedComponent = parsed == null ? Component.literal(safeSource) : parsed;
        return cachedComponent;
    }

    private static StyledCodepoints decode(FormattedText text) {
        IntArrayList codepoints = new IntArrayList();
        List<Style> styles = new ArrayList<>();
        StringBuilder plainText = new StringBuilder();
        text.visit((style, part) -> {
            part.codePoints().forEach(codepoint -> {
                codepoints.add(codepoint);
                styles.add(style);
                plainText.appendCodePoint(codepoint);
            });
            return Optional.empty();
        }, Style.EMPTY);
        return new StyledCodepoints(codepoints, styles, plainText.toString());
    }

    private static StyledCodepoints decode(FormattedCharSequence sequence) {
        IntArrayList codepoints = new IntArrayList();
        List<Style> styles = new ArrayList<>();
        StringBuilder plainText = new StringBuilder();
        sequence.accept((index, style, codepoint) -> {
            codepoints.add(codepoint);
            styles.add(style);
            plainText.appendCodePoint(codepoint);
            return true;
        });
        return new StyledCodepoints(codepoints, styles, plainText.toString());
    }

    private static Match matchAt(IntArrayList source, int[] prefix, List<int[]> playerNames, int index,
                                 boolean prefixOnlyComponent, boolean fakeEndsWithWhitespace) {
        if (!matchesAt(source, prefix, index)) {
            return null;
        }

        int afterPrefix = index + prefix.length;
        int nameIndex = afterPrefix;
        while (nameIndex < source.size() && Character.isWhitespace(source.getInt(nameIndex))) {
            nameIndex++;
        }
        if (matchesAnyName(source, playerNames, nameIndex)) {
            int consumedWhitespace = fakeEndsWithWhitespace ? nameIndex - afterPrefix : 0;
            return new Match(prefix.length + consumedWhitespace);
        }
        return prefixOnlyComponent ? new Match(prefix.length) : null;
    }

    private static boolean matchesAnyName(IntArrayList source, List<int[]> playerNames, int index) {
        for (int[] playerName : playerNames) {
            if (matchesAtIgnoreCase(source, playerName, index)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAt(IntArrayList source, int[] target, int index) {
        if (target.length == 0 || index < 0 || index + target.length > source.size()) {
            return false;
        }
        for (int offset = 0; offset < target.length; offset++) {
            if (source.getInt(index + offset) != target[offset]) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesAtIgnoreCase(IntArrayList source, int[] target, int index) {
        if (target.length == 0 || index < 0 || index + target.length > source.size()) {
            return false;
        }
        for (int offset = 0; offset < target.length; offset++) {
            if (Character.toLowerCase(source.getInt(index + offset)) != Character.toLowerCase(target[offset])) {
                return false;
            }
        }
        return true;
    }

    private static boolean endsWithWhitespace(String text) {
        return !text.isEmpty() && Character.isWhitespace(text.codePointBefore(text.length()));
    }

    private static boolean isStatusLine(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        return (lower.contains("статус") || lower.contains("status")) && text.indexOf(':') >= 0;
    }

    private static boolean isPrefixOnlyComponent(String text, String prefix) {
        return text != null && prefix != null && text.strip().equals(prefix);
    }

    private static void flush(List<FormattedText> parts, StringBuilder text, Style style) {
        if (!text.isEmpty() && style != null) {
            parts.add(FormattedText.of(text.toString(), style));
            text.setLength(0);
        }
    }

    private record Replacement(String original, Component fake, List<String> playerNames) {
    }

    private record DetectedPrefix(String prefix, String playerName) {
    }

    private record Match(int consumedCodepoints) {
    }

    private record StyledCodepoints(IntArrayList codepoints, List<Style> styles, String plainText) {
    }
}
