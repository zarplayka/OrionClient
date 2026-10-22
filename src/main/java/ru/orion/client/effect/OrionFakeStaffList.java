package ru.orion.client.effect;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** Добавляет настроенные ники в их группу сообщения «Администрация сервера». */
public final class OrionFakeStaffList {
    private static final int ACCENT_COLOR = 0xE1BB4B;
    private static final int NAME_COLOR = 0xFFFFFF;
    private static final FontDescription SYMBOLS_FONT = new FontDescription.Resource(
            Identifier.fromNamespaceAndPath("minecraft", "symbols")
    );
    private static final Map<String, Integer> GROUP_ORDER = Map.of(
            "helper", 0,
            "shelper", 1,
            "moder", 2,
            "smoder", 3,
            "admin", 4
    );

    private OrionFakeStaffList() {
    }

    public static Component addConfiguredPlayers(Component message) {
        if (message == null || !message.getString().contains("Администрация сервера:")) {
            return message;
        }

        List<String> configuredNames = OrionFakePrefix.prioritizedPlayerNames().stream()
                .filter(name -> !containsName(message.getString(), name))
                .toList();
        if (configuredNames.isEmpty()) {
            return message;
        }

        Component prefix = OrionFakePrefix.configuredPrefixComponent();
        String targetFont = groupFont(prefix);
        if (targetFont == null || !targetFont.startsWith("custom:groups/")) {
            return message;
        }

        List<Component> lines = splitLines(message);
        int insertionIndex = insertionIndex(lines, targetFont);
        boolean hasStaffAfter = hasStaffLine(lines, insertionIndex);
        if (!hasStaffAfter) {
            int previousStaffLine = previousStaffLine(lines, insertionIndex);
            if (previousStaffLine >= 0) {
                lines.set(previousStaffLine, replaceFinalConnector(lines.get(previousStaffLine)));
            }
        }

        List<Component> additions = new ArrayList<>(configuredNames.size());
        for (int index = 0; index < configuredNames.size(); index++) {
            boolean lastOverall = !hasStaffAfter && index == configuredNames.size() - 1;
            additions.add(staffLine(lastOverall ? "Y" : "Z", prefix, configuredNames.get(index)));
        }
        lines.addAll(insertionIndex, additions);
        return joinLines(lines);
    }

    private static int insertionIndex(List<Component> lines, String targetFont) {
        int lastExact = -1;
        int lastStaff = -1;
        int firstHigher = -1;
        int targetOrder = groupOrder(targetFont);

        for (int index = 0; index < lines.size(); index++) {
            String lineFont = groupFont(lines.get(index));
            if (lineFont == null) {
                continue;
            }
            lastStaff = index;
            if (lineFont.equals(targetFont)) {
                lastExact = index;
            } else if (firstHigher < 0 && groupOrder(lineFont) > targetOrder) {
                firstHigher = index;
            }
        }
        if (lastExact >= 0) {
            return lastExact + 1;
        }
        if (firstHigher >= 0) {
            return firstHigher;
        }
        return lastStaff >= 0 ? lastStaff + 1 : Math.max(0, lines.size() - 1);
    }

    private static int groupOrder(String font) {
        int slash = font.lastIndexOf('/');
        String group = slash < 0 ? font : font.substring(slash + 1);
        return GROUP_ORDER.getOrDefault(group, Integer.MAX_VALUE - 1);
    }

    private static boolean hasStaffLine(List<Component> lines, int fromIndex) {
        for (int index = fromIndex; index < lines.size(); index++) {
            if (groupFont(lines.get(index)) != null) {
                return true;
            }
        }
        return false;
    }

    private static int previousStaffLine(List<Component> lines, int fromIndex) {
        for (int index = Math.min(fromIndex, lines.size()) - 1; index >= 0; index--) {
            if (groupFont(lines.get(index)) != null) {
                return index;
            }
        }
        return -1;
    }

    private static Component staffLine(String connector, Component prefix, String playerName) {
        return Component.empty()
                .append(Component.literal(connector).withStyle(style -> style
                        .withColor(ACCENT_COLOR)
                        .withFont(SYMBOLS_FONT)))
                .append(" ")
                .append(prefix.copy())
                .append(" ")
                .append(Component.literal(playerName).withStyle(style -> style.withColor(NAME_COLOR)));
    }

    private static Component replaceFinalConnector(Component line) {
        MutableComponent result = Component.empty();
        line.visit((style, part) -> {
            String text = isSymbolsFont(style) ? part.replace('Y', 'Z') : part;
            result.append(Component.literal(text).setStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return result;
    }

    private static List<Component> splitLines(Component component) {
        List<Component> lines = new ArrayList<>();
        MutableComponent[] current = {Component.empty()};
        component.visit((style, part) -> {
            part.codePoints().forEach(codepoint -> {
                if (codepoint == '\n') {
                    lines.add(current[0]);
                    current[0] = Component.empty();
                } else {
                    current[0].append(Component.literal(new String(Character.toChars(codepoint))).setStyle(style));
                }
            });
            return Optional.empty();
        }, Style.EMPTY);
        lines.add(current[0]);
        return lines;
    }

    private static Component joinLines(List<Component> lines) {
        MutableComponent result = Component.empty();
        for (int index = 0; index < lines.size(); index++) {
            if (index > 0) {
                result.append("\n");
            }
            result.append(lines.get(index));
        }
        return result;
    }

    private static String groupFont(Component component) {
        return component.visit((style, part) -> {
            if (style.getFont() instanceof FontDescription.Resource resource) {
                String id = resource.id().toString().toLowerCase(Locale.ROOT);
                if (id.startsWith("custom:groups/")) {
                    return Optional.of(id);
                }
            }
            return Optional.empty();
        }, Style.EMPTY).orElse(null);
    }

    private static boolean isSymbolsFont(Style style) {
        return style.getFont() instanceof FontDescription.Resource resource
                && resource.id().toString().equals("minecraft:symbols");
    }

    private static boolean containsName(String text, String playerName) {
        return Pattern.compile("(?i)(?<![A-Za-z0-9_])" + Pattern.quote(playerName) + "(?![A-Za-z0-9_])")
                .matcher(text)
                .find();
    }
}
