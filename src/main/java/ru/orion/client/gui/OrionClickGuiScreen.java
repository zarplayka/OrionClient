package ru.orion.client.gui;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import ru.orion.client.OrionClient;
import ru.orion.client.config.OrionConfig;
import ru.orion.client.config.OrionConfigManager;
import ru.orion.client.effect.OrionHitSound;
import ru.orion.client.effect.OrionTargetEsp;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public final class OrionClickGuiScreen extends Screen {
    private static final Component TITLE = Component.literal(OrionClient.VERSION_TEXT);
    private static final Identifier GUI_TEXTURE = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "click_gui_dynamic");

    // Макет из Figma (окно 770x850, коэффициент 0.588 -> gui-юниты 453x501).
    private static final int GUI_WIDTH = 453;
    private static final int GUI_HEIGHT = 501;
    private static final int HEADER_HEIGHT = 33; // 56px в макете
    private static final int WINDOW_RADIUS = 11;
    private static final int SIDEBAR_WIDTH = 126;
    private static final int SIDEBAR_ITEM_X = 8;
    private static final int SIDEBAR_ITEM_Y = 47;
    private static final int SIDEBAR_ITEM_WIDTH = 110;
    private static final int SIDEBAR_ITEM_HEIGHT = 34;
    private static final int SIDEBAR_ITEM_STEP = 38;
    private static final int CONTENT_X = 140;
    private static final int MODULE_X = CONTENT_X;
    private static final int MODULE_Y = 84;
    private static final int MODULE_WIDTH = 301;
    private static final int MODULE_HEIGHT = 52;
    private static final int MODULE_STEP = 59; // 52 карточка + 7 отступ (12px макета)
    private static final int ICON_TEXT_GAP = 7; // 12px макета
    private static final int TOGGLE_WIDTH = 31;
    private static final int TOGGLE_HEIGHT = 19;
    private static final int TOGGLE_ANIMATION_MS = 180;
    private static final int HOVER_ANIMATION_MS = 160;
    private static final int MAX_TEXTURE_SCALE = 6;
    private static final float ORION_TARGET_SCALE = 1.3F;
    private static final float ORION2_TARGET_SCALE = 0.8F;
    private static final float ORION3_TARGET_SCALE = 1.15F;
    private static final String ORION3_LOGO_RESOURCE = "/assets/orion-client/textures/gui/orion3_logo.png";
    private static final int ORION3_LOGO_AREA_HEIGHT = 62;
    private static final int SCREEN_FIT_MARGIN = 12;
    private static final int GEAR_SIZE = 25;
    private static final int BACK_BUTTON_Y = 45;
    private static final int ARROW_SIZE = 18;
    private static final int CHOICE_VALUE_WIDTH = 64;
    private static final int SLIDER_TRACK_WIDTH = 80;
    private static final int SWATCH_SIZE = 14;
    private static final int SWATCH_STEP = 18;

    // Геометрия, зависящая от темы: классические темы делят один макет 453x501,
    // ORION3 — компактное окно 210x300 из своей Figma (фрейм 513x741, множитель ~0.41).
    private record Layout(
            int guiW, int guiH, int windowRadius, int sidebarW,
            int sideX, int sideY, int sideW, int sideH, int sideStep,
            int moduleX, int moduleY, int moduleW, int moduleH, int moduleStep,
            int toggleW, int toggleH, int gearSize, int backY, int arrowSize,
            int choiceValueW, int sliderTrackW, int swatchSize, int swatchStep
    ) {
    }

    private static final Layout CLASSIC_LAYOUT = new Layout(
            GUI_WIDTH, GUI_HEIGHT, WINDOW_RADIUS, SIDEBAR_WIDTH,
            SIDEBAR_ITEM_X, SIDEBAR_ITEM_Y, SIDEBAR_ITEM_WIDTH, SIDEBAR_ITEM_HEIGHT, SIDEBAR_ITEM_STEP,
            MODULE_X, MODULE_Y, MODULE_WIDTH, MODULE_HEIGHT, MODULE_STEP,
            TOGGLE_WIDTH, TOGGLE_HEIGHT, GEAR_SIZE, BACK_BUTTON_Y, ARROW_SIZE,
            CHOICE_VALUE_WIDTH, SLIDER_TRACK_WIDTH, SWATCH_SIZE, SWATCH_STEP
    );

    private static final Layout ORION3_LAYOUT = new Layout(
            210, 300, 9, 60,
            4, 74, 52, 22, 26,
            68, 50, 134, 34, 39,
            24, 13, 16, 16, 12,
            42, 48, 8, 10
    );

    private static final String[] COLOR_PRESETS = {
            "#F59E0B", "#FF7C00", "#EF4444", "#22C55E", "#3B82F6", "#64D9FF", "#A855F7", "#F5F5FF"
    };
    private static final String[] FOG_COLOR_PRESETS = {
            "#14111F", "#1E1F24", "#EF4444", "#22C55E", "#3B82F6", "#64D9FF", "#A855F7", "#F5F5FF"
    };

    // Палитры тем. ORION — из первой Figma (янтарная), PRIZRAK — из второй (тёмная, красно-оранжевая).
    private record Palette(
            boolean classic,
            Color accent, Color accentDark,
            Color bg, Color sidebar, Color surface, Color surfaceHover,
            Color border, Color text, Color muted, Color inset, Color placeholder
    ) {
    }

    private static final Palette ORION_PALETTE = new Palette(
            true,
            new Color(0xF5, 0x9E, 0x0B), new Color(0xD9, 0x77, 0x06),
            new Color(0x1E, 0x1F, 0x24), new Color(0x19, 0x1A, 0x1F),
            new Color(0x23, 0x26, 0x2D), new Color(0x2C, 0x30, 0x38),
            new Color(0x3A, 0x40, 0x49), new Color(0xE5, 0xE7, 0xEB),
            new Color(0x9C, 0xA3, 0xAF), new Color(0x17, 0x18, 0x1D),
            new Color(0x6F, 0x77, 0x82)
    );

    private static final Palette PRIZRAK_PALETTE = new Palette(
            false,
            new Color(0xFF, 0x6A, 0x00), new Color(0xC5, 0x55, 0x07),
            new Color(0x05, 0x05, 0x05), new Color(0x0B, 0x0B, 0x0D),
            new Color(0x17, 0x17, 0x1B), new Color(0x20, 0x20, 0x27),
            new Color(0x2A, 0x2A, 0x31), new Color(0xEC, 0xEC, 0xEE),
            new Color(0x8A, 0x8A, 0x92), new Color(0x15, 0x15, 0x18),
            new Color(0x6A, 0x6A, 0x72)
    );

    // ORION3 — из третьей Figma (Untitled.fig): сплошной чёрный фон, логотип в сайдбаре,
    // крупный поиск сверху, карточки без рамок и контурные тумблеры.
    private static final Palette ORION3_PALETTE = new Palette(
            false,
            new Color(0xFF, 0x6A, 0x00), new Color(0xC5, 0x55, 0x07),
            new Color(0x0B, 0x0B, 0x0D), new Color(0x0B, 0x0B, 0x0D),
            new Color(0x17, 0x17, 0x1B), new Color(0x20, 0x20, 0x27),
            new Color(0x2A, 0x2A, 0x31), new Color(0xEC, 0xEC, 0xEE),
            new Color(0x8A, 0x8A, 0x92), new Color(0x15, 0x15, 0x18),
            new Color(0x6A, 0x6A, 0x72)
    );

    private static final String[] THEME_OPTIONS = {"Orion", "Orion2", "Orion3"};

    private static int lastSelectedCategory;

    private final OrionConfigManager configManager;
    private int selectedCategory;
    private int hoveredCategory = -1;
    private int hoveredModule = -1;
    private ModuleRow settingsFor;
    private Setting[] settingsRows = new Setting[0];
    private final Setting[] globalSettings;
    private int draggingSlider = -1;
    private int editingText = -1;
    // Раскрытая палитра цвета: индекс ColorSetting и текущее HSV-состояние пикера.
    private int expandedColor = -1;
    private float pickerHue;
    private float pickerSat;
    private float pickerVal;
    private boolean draggingSV;
    private boolean draggingHue;
    private String searchQuery = "";
    private boolean orion3;
    private Layout L = CLASSIC_LAYOUT;
    private float sc = 1.0F; // множитель внутренних отступов/шрифтов карточек (ORION3 мельче)
    private Palette pal = ORION_PALETTE;
    private Color PRIMARY = ORION_PALETTE.accent;
    private Color PRIMARY_DARK = ORION_PALETTE.accentDark;
    private Color BG = ORION_PALETTE.bg;
    private Color SIDEBAR_BG = ORION_PALETTE.sidebar;
    private Color SURFACE = ORION_PALETTE.surface;
    private Color SURFACE_HOVER = ORION_PALETTE.surfaceHover;
    private Color BORDER = ORION_PALETTE.border;
    private Color TEXT = ORION_PALETTE.text;
    private Color MUTED = ORION_PALETTE.muted;
    private Color SEARCH_BG = ORION_PALETTE.inset;
    private Color PLACEHOLDER = ORION_PALETTE.placeholder;
    private DynamicTexture dynamicTexture;
    private int textureWidth;
    private int textureHeight;
    private List<Integer> lastFingerprint;
    private long lastFrameNanos;
    private final float[] toggleAnim = new float[Action.VALUES.length];
    private final float[] categoryHoverAnim = new float[Category.VALUES.length];
    private final float[] moduleHoverAnim = new float[8];

    public OrionClickGuiScreen(OrionConfigManager configManager) {
        super(TITLE);
        this.configManager = configManager;
        applyPalette();
        OrionConfig config = configManager.getConfig();
        globalSettings = new Setting[]{
                new ChoiceSetting("Тема", THEME_OPTIONS, config::getClickGuiTheme, configManager::setClickGuiTheme)
        };
        selectedCategory = Math.max(0, Math.min(Category.VALUES.length - 1, lastSelectedCategory));
        for (Action action : Action.VALUES) {
            toggleAnim[action.ordinal()] = isActive(action) ? 1.0F : 0.0F;
        }
    }

    /** Opens the ClickGUI already focused on the MediaPlayer module settings (used by the HUD menu chip). */
    public static OrionClickGuiScreen withMediaPlayerSettings(OrionConfigManager configManager) {
        OrionClickGuiScreen screen = new OrionClickGuiScreen(configManager);
        for (ModuleRow row : ModuleRow.MISC) {
            if (row.action == Action.MEDIA_PLAYER) {
                screen.selectedCategory = Category.MISC.ordinal();
                lastSelectedCategory = screen.selectedCategory;
                screen.openSettings(row);
                break;
            }
        }
        return screen;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void added() {
        super.added();
        Minecraft.getInstance().onTextInputFocusChange(this, true);
    }

    @Override
    public void removed() {
        Minecraft.getInstance().onTextInputFocusChange(this, false);
        configManager.save();
        Minecraft.getInstance().getTextureManager().release(GUI_TEXTURE);
        dynamicTexture = null;
        textureWidth = 0;
        textureHeight = 0;
        lastFingerprint = null;
        super.removed();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        context.fill(0, 0, width, height, 0x66000000);
        minecraft.gui.hud.extractDeferredSubtitles();
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        super.extractRenderState(context, mouseX, mouseY, deltaTicks);

        updateAnimations();
        updateHover(toLocalX(mouseX), toLocalY(mouseY));
        updateTextureIfNeeded();

        int x = guiX();
        int y = guiY();
        context.blit(GUI_TEXTURE, x, y, x + guiDisplayWidth(), y + guiDisplayHeight(), 0.0F, 1.0F, 0.0F, 1.0F);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseClicked(event, doubleClick);
        }

        int localX = toLocalX(event.x());
        int localY = toLocalY(event.y());

        int category = categoryAt(localX, localY);
        if (category >= 0) {
            selectedCategory = category;
            lastSelectedCategory = category;
            searchQuery = "";
            closeSettings();
            Arrays.fill(moduleHoverAnim, 0.0F);
            lastFingerprint = null;
            return true;
        }

        if (settingsFor != null) {
            if (settingsClick(localX, localY)) {
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        if (Category.VALUES[selectedCategory] == Category.SETTINGS) {
            if (settingsClick(localX, localY, globalSettings)) {
                return true;
            }
            return super.mouseClicked(event, doubleClick);
        }

        int module = moduleAt(localX, localY);
        ModuleRow[] rows = rowsForSelectedCategory();
        if (module >= 0 && module < rows.length) {
            ModuleRow row = rows[module];
            int rowY = L.moduleY + module * L.moduleStep;
            if (hasSettings(row.action) && isInside(localX, localY, gearX(), rowY + gearOffsetY(), L.gearSize, L.gearSize)) {
                openSettings(row);
                return true;
            }
            toggle(row.action);
            return true;
        }

        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseDragged(event, dragX, dragY);
        }
        Setting[] rows = editableRows();
        if ((draggingSV || draggingHue) && expandedColor >= 0 && expandedColor < rows.length
                && rows[expandedColor] instanceof ColorSetting color) {
            if (draggingSV) {
                applySvPick(color, toLocalX(event.x()), toLocalY(event.y()));
            } else {
                applyHuePick(color, toLocalY(event.y()));
            }
            return true;
        }
        if (draggingSlider >= 0 && draggingSlider < settingsRows.length
                && settingsRows[draggingSlider] instanceof SliderSetting slider) {
            applySlider(slider, toLocalX(event.x()));
            return true;
        }
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return super.mouseReleased(event);
        }
        if (draggingSlider >= 0 || draggingSV || draggingHue) {
            draggingSlider = -1;
            draggingSV = false;
            draggingHue = false;
            configManager.save();
            return true;
        }
        return super.mouseReleased(event);
    }

    /** Строки настроек, доступные для взаимодействия прямо сейчас. */
    private Setting[] editableRows() {
        if (settingsFor != null) {
            return settingsRows;
        }
        if (searchQuery.isBlank() && Category.VALUES[selectedCategory] == Category.SETTINGS) {
            return globalSettings;
        }
        return new Setting[0];
    }

    private TextSetting editingTextSetting() {
        Setting[] rows = editableRows();
        if (editingText >= 0 && editingText < rows.length && rows[editingText] instanceof TextSetting text) {
            return text;
        }
        return null;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        TextSetting editing = editingTextSetting();
        if (editing != null) {
            if (event.isAllowedChatCharacter()) {
                String current = editing.get.get();
                if (current.length() < editing.maxLength()) {
                    editing.set.accept(current + event.codepointAsString());
                    lastFingerprint = null;
                }
            }
            return true;
        }
        if (event.isAllowedChatCharacter() && searchQuery.length() < 24) {
            closeSettings();
            searchQuery += event.codepointAsString();
            Arrays.fill(moduleHoverAnim, 0.0F);
            lastFingerprint = null;
            return true;
        }
        return super.charTyped(event);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        TextSetting editing = editingTextSetting();
        if (editing != null) {
            if (event.isSelectAll()) {
                editing.set.accept("");
                lastFingerprint = null;
                return true;
            }
            if (event.isCopy() || event.isCut()) {
                minecraft.keyboardHandler.setClipboard(editing.get.get());
                if (event.isCut()) {
                    editing.set.accept("");
                    lastFingerprint = null;
                }
                return true;
            }
            if (event.isPaste()) {
                String current = editing.get.get();
                String clipboard = minecraft.keyboardHandler.getClipboard().codePoints()
                        .filter(codepoint -> codepoint >= 32 && codepoint != 127)
                        .collect(StringBuilder::new, StringBuilder::appendCodePoint, StringBuilder::append)
                        .toString();
                int available = Math.max(0, editing.maxLength() - current.length());
                int end = Math.min(available, clipboard.length());
                if (end > 0 && end < clipboard.length()
                        && Character.isHighSurrogate(clipboard.charAt(end - 1))
                        && Character.isLowSurrogate(clipboard.charAt(end))) {
                    end--;
                }
                if (end > 0) {
                    editing.set.accept(current + clipboard.substring(0, end));
                    lastFingerprint = null;
                }
                return true;
            }
            if (event.key() == InputConstants.KEY_BACKSPACE) {
                String current = editing.get.get();
                if (!current.isEmpty()) {
                    editing.set.accept(current.substring(0, current.length() - 1));
                    lastFingerprint = null;
                }
                return true;
            }
            if (event.key() == InputConstants.KEY_RETURN || event.key() == InputConstants.KEY_NUMPADENTER
                    || event.key() == InputConstants.KEY_ESCAPE) {
                editingText = -1;
                lastFingerprint = null;
                return true;
            }
        }
        if (event.key() == InputConstants.KEY_BACKSPACE && !searchQuery.isEmpty()) {
            searchQuery = searchQuery.substring(0, searchQuery.length() - 1);
            Arrays.fill(moduleHoverAnim, 0.0F);
            lastFingerprint = null;
            return true;
        }
        if (event.key() == InputConstants.KEY_ESCAPE && !searchQuery.isEmpty()) {
            searchQuery = "";
            Arrays.fill(moduleHoverAnim, 0.0F);
            lastFingerprint = null;
            return true;
        }
        return super.keyPressed(event);
    }

    private void openSettings(ModuleRow row) {
        settingsFor = row;
        settingsRows = buildSettings(row.action);
        draggingSlider = -1;
        expandedColor = -1;
        draggingSV = false;
        draggingHue = false;
        lastFingerprint = null;
    }

    private void closeSettings() {
        expandedColor = -1;
        draggingSV = false;
        draggingHue = false;
        if (settingsFor != null) {
            settingsFor = null;
            settingsRows = new Setting[0];
            draggingSlider = -1;
            editingText = -1;
            lastFingerprint = null;
        }
    }

    private boolean settingsClick(int localX, int localY) {
        if (isInside(localX, localY, L.moduleX, L.backY, L.gearSize, L.gearSize)) {
            closeSettings();
            return true;
        }

        return settingsClick(localX, localY, settingsRows);
    }

    private boolean settingsClick(int localX, int localY, Setting[] rows) {
        editingText = -1;

        if (expandedColor >= 0 && expandedColor < rows.length
                && rows[expandedColor] instanceof ColorSetting expanded
                && pickerClick(expanded, localX, localY, expandedPanelY())) {
            return true;
        }

        for (int i = 0; i < rows.length; i++) {
            int y = settingRowY(i);
            Setting setting = rows[i];
            if (setting instanceof ChoiceSetting choice) {
                if (isInside(localX, localY, choiceLeftArrowX(), y + arrowOffsetY(), L.arrowSize, L.arrowSize)) {
                    cycleChoice(choice, -1);
                    return true;
                }
                if (isInside(localX, localY, choiceRightArrowX(), y + arrowOffsetY(), L.arrowSize, L.arrowSize)) {
                    cycleChoice(choice, 1);
                    return true;
                }
            } else if (setting instanceof SliderSetting slider) {
                if (isInside(localX, localY, sliderTrackX() - 4, y + L.moduleH / 2 - 8, L.sliderTrackW + 8, 16)) {
                    draggingSlider = i;
                    applySlider(slider, localX);
                    return true;
                }
            } else if (setting instanceof ColorSetting color) {
                if (isInside(localX, localY, L.moduleX, y, L.moduleW, L.moduleH)) {
                    toggleColorExpand(i, color);
                    return true;
                }
            } else if (setting instanceof ToggleSetting toggle) {
                if (isInside(localX, localY, L.moduleX, y, L.moduleW, L.moduleH)) {
                    toggle.set.accept(!toggle.get.get());
                    lastFingerprint = null;
                    return true;
                }
            } else if (setting instanceof TextSetting) {
                if (isInside(localX, localY, textBoxX(), y + arrowOffsetY(), textBoxW(), L.arrowSize)) {
                    editingText = i;
                    lastFingerprint = null;
                    return true;
                }
            } else if (setting instanceof ButtonSetting button) {
                if (isInside(localX, localY, textBoxX(), y + arrowOffsetY(), textBoxW(), L.arrowSize)) {
                    button.onClick.run();
                    lastFingerprint = null;
                    return true;
                }
            }
        }
        return false;
    }

    private void cycleChoice(ChoiceSetting choice, int direction) {
        int count = choice.options.length;
        int next = ((choice.get.getAsInt() + direction) % count + count) % count;
        choice.set.accept(next);
        lastFingerprint = null;
    }

    private void applySlider(SliderSetting slider, float localX) {
        float t = (localX - sliderTrackX()) / L.sliderTrackW;
        t = Math.max(0.0F, Math.min(1.0F, t));
        float value = slider.min + (slider.max - slider.min) * t;
        slider.set.accept(slider.integer ? Math.round(value) : value);
        lastFingerprint = null;
    }

    private void toggleColorExpand(int index, ColorSetting color) {
        if (expandedColor == index) {
            expandedColor = -1;
            configManager.save();
        } else {
            expandedColor = index;
            syncPickerFromHex(color.get.get());
        }
        draggingSV = false;
        draggingHue = false;
        lastFingerprint = null;
    }

    private void syncPickerFromHex(String hex) {
        Color current = parseColor(hex);
        float[] hsb = Color.RGBtoHSB(current.getRed(), current.getGreen(), current.getBlue(), null);
        pickerHue = hsb[0];
        pickerSat = hsb[1];
        pickerVal = hsb[2];
    }

    private boolean pickerClick(ColorSetting color, int localX, int localY, int panelY) {
        PickerLayout p = pickerLayout(panelY);
        if (isInside(localX, localY, p.svX() - 2, p.svY() - 2, p.svS() + 4, p.svS() + 4)) {
            draggingSV = true;
            applySvPick(color, localX, localY);
            return true;
        }
        if (isInside(localX, localY, p.hueX() - 3, p.svY() - 2, p.hueW() + 6, p.svS() + 4)) {
            draggingHue = true;
            applyHuePick(color, localY);
            return true;
        }
        for (int s = 0; s < color.presets.length; s++) {
            int dx = p.rightX() + (s % 4) * p.dotStep();
            int dy = p.dotsY() + (s / 4) * p.dotStep();
            if (isInside(localX, localY, dx, dy, p.dotSize(), p.dotSize())) {
                color.set.accept(color.presets[s]);
                syncPickerFromHex(color.presets[s]);
                configManager.save();
                lastFingerprint = null;
                return true;
            }
        }
        // Клик по фону панели не должен проваливаться в строки под ней.
        return isInside(localX, localY, L.moduleX, panelY, L.moduleW, pickerPanelHeight());
    }

    private void applySvPick(ColorSetting color, int localX, int localY) {
        PickerLayout p = pickerLayout(expandedPanelY());
        pickerSat = clamp01((localX - p.svX()) / (float) p.svS());
        pickerVal = 1.0F - clamp01((localY - p.svY()) / (float) p.svS());
        commitPicker(color);
    }

    private void applyHuePick(ColorSetting color, int localY) {
        PickerLayout p = pickerLayout(expandedPanelY());
        pickerHue = clamp01((localY - p.svY()) / (float) p.svS());
        commitPicker(color);
    }

    private void commitPicker(ColorSetting color) {
        Color current = Color.getHSBColor(pickerHue, pickerSat, pickerVal);
        color.set.accept(String.format(Locale.ROOT, "#%02X%02X%02X",
                current.getRed(), current.getGreen(), current.getBlue()));
        lastFingerprint = null;
    }

    private static float clamp01(float value) {
        return Math.max(0.0F, Math.min(1.0F, value));
    }

    /** Y строки настроек с учётом раскрытой палитры: строки ниже неё сдвигаются вниз. */
    private int settingRowY(int index) {
        int y = L.moduleY + index * L.moduleStep;
        if (expandedColor >= 0 && index > expandedColor) {
            y += pickerPanelHeight() + (L.moduleStep - L.moduleH);
        }
        return y;
    }

    private int expandedPanelY() {
        return settingRowY(expandedColor) + L.moduleH + (L.moduleStep - L.moduleH);
    }

    /** Геометрия раскрытой палитры: SV-квадрат, полоса тона и правая колонка с превью и пресетами. */
    private record PickerLayout(int svX, int svY, int svS, int hueX, int hueW,
                                int rightX, int previewW, int previewH,
                                int dotSize, int dotStep, int dotsY) {
    }

    private PickerLayout pickerLayout(int panelY) {
        int pad = pickerPad();
        int svS = svSize();
        int svX = L.moduleX + pad;
        int svY = panelY + pad;
        int hueX = svX + svS + Math.round(8 * sc);
        int hueW = Math.round(10 * sc);
        int rightX = hueX + hueW + Math.round(12 * sc);
        int previewW = Math.round(34 * sc);
        int previewH = Math.round(14 * sc);
        int dotSize = Math.round(12 * sc);
        int dotStep = Math.round(16 * sc);
        int dotsY = svY + svS - dotStep - dotSize;
        return new PickerLayout(svX, svY, svS, hueX, hueW, rightX, previewW, previewH, dotSize, dotStep, dotsY);
    }

    private int pickerPad() {
        return Math.round(10 * sc);
    }

    private int svSize() {
        return Math.round(66 * sc);
    }

    private int pickerPanelHeight() {
        return svSize() + 2 * pickerPad();
    }

    private boolean hasSettings(Action action) {
        return switch (action) {
            case TARGET_ESP, JUMP_CIRCLE, ASPECT, HUD, WORLD_PARTICLES, WORLD_CHANGER, MEDIA_PLAYER, PROTECT_NAME,
                 FAKE_PREFIX,
                 HIT_BOX, HIT_COLOR, HIT_SOUND, CROSSHAIR, BETTER_NEAR, RAINBOW -> true;
            default -> false;
        };
    }

    private Setting[] buildSettings(Action action) {
        OrionConfig config = configManager.getConfig();
        return switch (action) {
            case TARGET_ESP -> new Setting[]{
                    new ChoiceSetting("Текстура", OrionTargetEsp.TEXTURES,
                            config::getTargetEspTexture, configManager::setTargetEspTexture),
                    new ColorSetting("Цвет цепи", COLOR_PRESETS,
                            config::getTargetEspChainColor, config::setTargetEspChainColor)
            };
            case JUMP_CIRCLE -> new Setting[]{
                    new ColorSetting("Цвет кольца", COLOR_PRESETS,
                            config::getJumpCircleColor, config::setJumpCircleColor),
                    new SliderSetting("Размер", 0.25F, 3.0F,
                            config::getJumpCircleSize, config::setJumpCircleSize, "x")
            };
            case ASPECT -> new Setting[]{
                    new SliderSetting("Растяжение", 0.05F, 2.0F,
                            config::getAspectStretch, config::setAspectStretch, "x")
            };
            case HUD -> new Setting[]{
                    new ToggleSetting("Стеклянный скорборд",
                            config::isScoreboardGlassEnabled, configManager::setScoreboardGlassEnabled),
                    new SliderSetting("Масштаб", 0.5F, 4.0F,
                            config::getHudScale, config::setHudScale, "x")
            };
            case WORLD_PARTICLES -> new Setting[]{
                    new ColorSetting("Цвет частиц", COLOR_PRESETS,
                            config::getWorldParticlesColor, config::setWorldParticlesColor),
                    new SliderSetting("Количество", 1.0F, 2500.0F,
                            () -> (float) config.getWorldParticlesCount(),
                            value -> config.setWorldParticlesCount(Math.round(value)),
                            "", true)
            };
            case WORLD_CHANGER -> new Setting[]{
                    new ColorSetting("Цвет неба", COLOR_PRESETS,
                            config::getWorldChangerSkyColor, config::setWorldChangerSkyColor),
                    new ColorSetting("Цвет тумана", FOG_COLOR_PRESETS,
                            config::getWorldChangerFogColor, config::setWorldChangerFogColor)
            };
            case PROTECT_NAME -> new Setting[]{
                    new TextSetting("Ник", config::getProtectName, configManager::setProtectName, 16)
            };
            case FAKE_PREFIX -> new Setting[]{
                    new TextSetting("Префикс / JSON", config::getFakePrefix, configManager::setFakePrefix, 2048),
                    new TextSetting("Доп. ники через запятую", config::getFakePrefixAdditionalNames,
                            configManager::setFakePrefixAdditionalNames, 512)
            };
            case HIT_BOX -> new Setting[]{
                    new ColorSetting("Цвет хитбоксов", COLOR_PRESETS,
                            config::getHitBoxColor, config::setHitBoxColor)
            };
            case HIT_COLOR -> new Setting[]{
                    new ColorSetting("Цвет заливки", COLOR_PRESETS,
                            config::getHitColorColor, config::setHitColorColor),
                    new SliderSetting("Прозрачность", 5.0F, 100.0F,
                            () -> (float) config.getHitColorOpacity(),
                            value -> config.setHitColorOpacity(Math.round(value)),
                            "%", true)
            };
            case HIT_SOUND -> new Setting[]{
                    new ChoiceSetting("Звук", OrionHitSound.SOUND_NAMES,
                            config::getHitSoundIndex, configManager::setHitSoundIndex),
                    new SliderSetting("Громкость", 0.0F, 100.0F,
                            () -> (float) config.getHitSoundVolume(),
                            value -> config.setHitSoundVolume(Math.round(value)),
                            "%", true),
                    new ButtonSetting("Проверка", "Тест",
                            () -> OrionClient.getInstance().getHitSound().preview()),
                    new ButtonSetting("Свои звуки (.wav)", "Папка",
                            () -> OrionClient.getInstance().getHitSound().openCustomFolder())
            };
            case CROSSHAIR -> new Setting[]{
                    new ColorSetting("Цвет прицела", COLOR_PRESETS,
                            config::getCrosshairColor, config::setCrosshairColor),
                    new ButtonSetting("Прицел", "Редактор", () -> {
                        Minecraft client = Minecraft.getInstance();
                        client.gui.setScreen(new OrionCrosshairEditorScreen(configManager, this));
                    })
            };
            case BETTER_NEAR -> new Setting[]{
                    new SliderSetting("Радиус", 10.0F, 200.0F,
                            () -> (float) config.getBetterNearRadius(),
                            value -> config.setBetterNearRadius(Math.round(value)),
                            "м", true)
            };
            case RAINBOW -> new Setting[]{
                    new SliderSetting("Скорость", 1.0F, 10.0F,
                            () -> (float) config.getRainbowSpeed(),
                            value -> config.setRainbowSpeed(Math.round(value)),
                            "x", true)
            };
            case MEDIA_PLAYER -> new Setting[]{
                    new ChoiceSetting("Позиция", new String[]{"TOP_LEFT", "TOP_RIGHT"},
                            () -> "TOP_RIGHT".equals(config.getMediaPlayerCorner()) ? 1 : 0,
                            index -> configManager.setMediaPlayerCorner(index == 1 ? "TOP_RIGHT" : "TOP_LEFT")),
                    new ChoiceSetting("Анимация", new String[]{"Вкл", "Выкл"},
                            () -> config.isMediaPlayerAnimated() ? 0 : 1,
                            index -> configManager.setMediaPlayerAnimated(index == 0))
            };
            default -> new Setting[0];
        };
    }

    private int textureScale() {
        int scale = Minecraft.getInstance().getWindow().getGuiScale();
        return Math.max(1, Math.min(MAX_TEXTURE_SCALE, scale));
    }

    private void updateTextureIfNeeded() {
        applyPalette();
        int scale = textureScale();
        List<Integer> fingerprint = fingerprint(scale);
        if (fingerprint.equals(lastFingerprint)) {
            return;
        }

        int imageWidth = guiDisplayWidth() * scale;
        int imageHeight = guiDisplayHeight() * scale;
        BufferedImage image = renderImage(scale);

        if (dynamicTexture == null || textureWidth != imageWidth || textureHeight != imageHeight) {
            Minecraft.getInstance().getTextureManager().release(GUI_TEXTURE);
            NativeImage nativeImage = new NativeImage(imageWidth, imageHeight, true);
            copyPixels(image, nativeImage);
            dynamicTexture = new DynamicTexture(() -> "orion_click_gui", nativeImage);
            Minecraft.getInstance().getTextureManager().register(GUI_TEXTURE, dynamicTexture);
            textureWidth = imageWidth;
            textureHeight = imageHeight;
        } else {
            NativeImage pixels = dynamicTexture.getPixels();
            if (pixels == null || pixels.isClosed()) {
                lastFingerprint = null;
                return;
            }
            copyPixels(image, pixels);
            dynamicTexture.upload();
        }
        lastFingerprint = fingerprint;
    }

    private static void copyPixels(BufferedImage source, NativeImage target) {
        int w = source.getWidth();
        int h = source.getHeight();
        int[] argb = source.getRGB(0, 0, w, h, null, 0, w);
        for (int y = 0; y < h; y++) {
            int row = y * w;
            for (int x = 0; x < w; x++) {
                target.setPixel(x, y, argb[row + x]);
            }
        }
    }

    private BufferedImage renderImage(int scale) {
        int imageWidth = guiDisplayWidth() * scale;
        int imageHeight = guiDisplayHeight() * scale;
        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        setupGraphics(g);
        // Fractional content scale keeps texture pixels 1:1 with physical pixels at any theme size.
        g.scale(imageWidth / (double) L.guiW, imageHeight / (double) L.guiH);

        Shape window = new RoundRectangle2D.Float(0, 0, L.guiW, L.guiH, L.windowRadius * 2, L.windowRadius * 2);
        drawWindow(g, window);
        drawHeader(g, window);
        drawSidebar(g);
        drawContent(g);

        g.dispose();
        return image;
    }

    private void drawWindow(Graphics2D g, Shape window) {
        if (orion3) {
            g.setColor(BG);
            g.fill(window);

            Shape oldClip = g.getClip();
            g.setClip(window);
            g.setColor(withAlpha(BORDER, 140));
            g.setStroke(new BasicStroke(1.0F));
            g.draw(new Line2D.Float(L.sidebarW, 0, L.sidebarW, L.guiH));
            g.draw(new Line2D.Float(0, ORION3_LOGO_AREA_HEIGHT, L.sidebarW, ORION3_LOGO_AREA_HEIGHT));
            g.setClip(oldClip);

            g.setColor(withAlpha(BORDER, 170));
            g.setStroke(new BasicStroke(1.0F));
            g.draw(new RoundRectangle2D.Float(0.5F, 0.5F, L.guiW - 1, L.guiH - 1, L.windowRadius * 2 - 1, L.windowRadius * 2 - 1));
            return;
        }

        g.setPaint(new GradientPaint(0, HEADER_HEIGHT, lighten(BG, 5), 0, GUI_HEIGHT, darken(BG, 6)));
        g.fill(window);

        Shape oldClip = g.getClip();
        g.setClip(window);
        g.setColor(SIDEBAR_BG);
        g.fillRect(0, HEADER_HEIGHT, SIDEBAR_WIDTH, GUI_HEIGHT - HEADER_HEIGHT);
        g.setColor(withAlpha(BORDER, 110));
        g.setStroke(new BasicStroke(1.0F));
        g.draw(new Line2D.Float(SIDEBAR_WIDTH, HEADER_HEIGHT, SIDEBAR_WIDTH, GUI_HEIGHT));
        g.setClip(oldClip);

        g.setColor(withAlpha(BORDER, 170));
        g.setStroke(new BasicStroke(1.0F));
        g.draw(new RoundRectangle2D.Float(0.5F, 0.5F, GUI_WIDTH - 1, GUI_HEIGHT - 1, WINDOW_RADIUS * 2 - 1, WINDOW_RADIUS * 2 - 1));
    }

    private void drawHeader(Graphics2D g, Shape window) {
        if (orion3) {
            drawOrion3Brand(g);
            return;
        }

        Shape oldClip = g.getClip();
        g.setClip(window);
        g.setPaint(new GradientPaint(0, 0, PRIMARY, GUI_WIDTH, HEADER_HEIGHT, PRIMARY_DARK));
        g.fillRect(0, 0, GUI_WIDTH, HEADER_HEIGHT);
        g.setColor(new Color(255, 255, 255, 36));
        g.draw(new Line2D.Float(1, 0.8F, GUI_WIDTH - 1, 0.8F));
        g.setClip(oldClip);

        drawLogo(g, 19, 16.5F, 9.5F, Color.WHITE, 1.7F);
        drawText(g, "OrionClient", 35, 21.5F, headingFont(13.0F), Color.WHITE, true);

        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.5F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(GUI_WIDTH - 53, 16.5F, GUI_WIDTH - 44, 16.5F));
        g.draw(new Line2D.Float(GUI_WIDTH - 26.5F, 12, GUI_WIDTH - 17.5F, 21));
        g.draw(new Line2D.Float(GUI_WIDTH - 17.5F, 12, GUI_WIDTH - 26.5F, 21));
    }

    /** Логотип из Figma в верхней части сайдбара (тема ORION3); при отсутствии текстуры — векторный запасной вариант. */
    private void drawOrion3Brand(Graphics2D g) {
        BufferedImage logo = orion3Logo();
        if (logo != null) {
            int size = Math.min(L.sidebarW - 8, ORION3_LOGO_AREA_HEIGHT - 8);
            int x = (L.sidebarW - size) / 2;
            int y = (ORION3_LOGO_AREA_HEIGHT - size) / 2;
            g.drawImage(logo, x, y, size, size, null);
            return;
        }
        float cx = L.sidebarW / 2.0F;
        drawLogo(g, cx, 24, 10, PRIMARY, 1.8F);
        Font brand = headingFont(7.5F);
        float orionWidth = (float) g.getFontMetrics(brand).getStringBounds("Orion ", g).getWidth();
        float clientWidth = (float) g.getFontMetrics(brand).getStringBounds("Client", g).getWidth();
        float start = cx - (orionWidth + clientWidth) / 2.0F;
        drawText(g, "Orion ", start, 48, brand, PRIMARY, true);
        drawText(g, "Client", start + orionWidth, 48, brand, TEXT, true);
    }

    private void drawSidebar(Graphics2D g) {
        for (int i = 0; i < Category.VALUES.length; i++) {
            Category category = Category.VALUES[i];
            int y = L.sideY + i * L.sideStep;
            boolean active = i == selectedCategory;
            float hover = categoryHoverAnim[i];

            if (orion3) {
                // В макете ORION3 пункты без подложки: активный целиком акцентный, остальные светлеют по ховеру.
                if (hover > 0.01F && !active) {
                    g.setColor(withAlpha(SURFACE, Math.round(120 * hover)));
                    g.fill(new RoundRectangle2D.Float(L.sideX, y, L.sideW, L.sideH, 6, 6));
                }
                Color color = active ? PRIMARY : mix(TEXT, Color.WHITE, hover * 0.4F);
                drawIcon(g, category.icon, 14, y + L.sideH / 2.0F, color, 1.3F, 0.45F);
                drawText(g, category.title, 24, y + L.sideH / 2.0F + 2.5F, bodyFont(7.0F), color, true);
            } else {
                if (active) {
                    g.setColor(SURFACE);
                    g.fill(new RoundRectangle2D.Float(L.sideX, y, L.sideW, L.sideH, 6, 6));
                } else if (hover > 0.01F) {
                    g.setColor(withAlpha(SURFACE, Math.round(140 * hover)));
                    g.fill(new RoundRectangle2D.Float(L.sideX, y, L.sideW, L.sideH, 6, 6));
                }

                Color color = active ? PRIMARY : mix(TEXT, Color.WHITE, hover * 0.4F);
                drawIcon(g, category.icon, 27, y + 17, color, 1.6F, 0.75F);
                drawText(g, category.title, 27 + 12 + ICON_TEXT_GAP, y + 21, bodyFont(10.0F), color, true);
            }
        }

        if (orion3) {
            drawText(g, "v1.0.0", 8, L.guiH - 8, bodyFont(5.5F), MUTED, false);
        } else {
            drawText(g, "v1.0.0", 14, L.guiH - 12, bodyFont(7.0F), MUTED, false);
        }
    }

    private void drawContent(Graphics2D g) {
        if (settingsFor != null) {
            drawSettings(g);
            return;
        }

        if (Category.VALUES[selectedCategory] == Category.SETTINGS) {
            drawGlobalSettings(g);
            return;
        }

        boolean searching = !searchQuery.isBlank();
        if (!orion3) {
            String title = searching ? "Поиск" : Category.VALUES[selectedCategory].title;
            drawText(g, title, CONTENT_X, 62, headingFont(12.5F), PRIMARY, true);
        }
        drawSearch(g);

        ModuleRow[] rows = rowsForSelectedCategory();
        if (rows.length == 0) {
            String empty = searching ? "Ничего не найдено" : "В этой категории пока пусто";
            drawText(g, empty, L.moduleX, L.moduleY + 18 * sc, bodyFont(8.5F * sc), MUTED, false);
            return;
        }
        int visible = Math.min(rows.length, maxVisibleRows());
        for (int i = 0; i < visible; i++) {
            float hover = i < moduleHoverAnim.length ? moduleHoverAnim[i] : 0.0F;
            drawModule(g, rows[i], L.moduleY + i * L.moduleStep, hover);
        }
        if (rows.length > visible) {
            drawText(g, "+ ещё " + (rows.length - visible), L.moduleX, L.moduleY + visible * L.moduleStep + 12 * sc, bodyFont(8.0F * sc), MUTED, false);
        }
    }

    private int maxVisibleRows() {
        return (L.guiH - L.moduleY - 8) / L.moduleStep;
    }

    private int gearOffsetY() {
        return (L.moduleH - L.gearSize) / 2;
    }

    private int arrowOffsetY() {
        return (L.moduleH - L.arrowSize) / 2;
    }

    /** Тумблер: в ORION3 прижат к правому краю карточки, в классике левее шестерёнки. */
    private int toggleX() {
        return orion3 ? L.moduleX + L.moduleW - 8 - L.toggleW : L.moduleX + L.moduleW - 85;
    }

    private void drawGlobalSettings(Graphics2D g) {
        if (orion3) {
            drawText(g, "Settings", L.moduleX, 28, headingFont(9.0F), PRIMARY, true);
            drawText(g, "Настройки клиента", L.moduleX, 40, bodyFont(6.0F), MUTED, false);
        } else {
            drawText(g, "Settings", CONTENT_X, 62, headingFont(12.5F), PRIMARY, true);
            drawText(g, "Настройки клиента", CONTENT_X, 73, bodyFont(7.0F), MUTED, false);
        }
        drawSettingRows(g, globalSettings);
    }

    private void drawSettingRows(Graphics2D g, Setting[] rows) {
        for (int i = 0; i < rows.length; i++) {
            int y = settingRowY(i);
            drawSettingRow(g, rows[i], y);
            if (i == expandedColor && rows[i] instanceof ColorSetting color) {
                drawColorPicker(g, color, y + L.moduleH + (L.moduleStep - L.moduleH));
            }
        }
    }

    private void drawSettings(Graphics2D g) {
        g.setColor(SEARCH_BG);
        g.fill(new RoundRectangle2D.Float(L.moduleX, L.backY, L.gearSize, L.gearSize, 5 * sc, 5 * sc));
        g.setColor(withAlpha(BORDER, 150));
        g.setStroke(new BasicStroke(1.0F));
        g.draw(new RoundRectangle2D.Float(L.moduleX + 0.5F, L.backY + 0.5F, L.gearSize - 1, L.gearSize - 1, 5 * sc, 5 * sc));
        float acx = L.moduleX + L.gearSize / 2.0F;
        float acy = L.backY + L.gearSize / 2.0F;
        g.setColor(TEXT);
        g.setStroke(new BasicStroke(Math.max(1.1F, 1.5F * sc), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(acx + 4 * sc, acy, acx - 4 * sc, acy));
        g.draw(new Line2D.Float(acx - 4 * sc, acy, acx - 0.5F * sc, acy - 3.5F * sc));
        g.draw(new Line2D.Float(acx - 4 * sc, acy, acx - 0.5F * sc, acy + 3.5F * sc));

        if (orion3) {
            drawText(g, settingsFor.title, L.moduleX + L.gearSize + 6, L.backY + L.gearSize - 4, headingFont(9.0F), PRIMARY, true);
            drawText(g, "Настройки", L.moduleX + L.gearSize + 6, L.backY + L.gearSize + 6, bodyFont(6.0F), MUTED, false);
        } else {
            drawText(g, settingsFor.title, CONTENT_X + GEAR_SIZE + 9, 62, headingFont(12.5F), PRIMARY, true);
            drawText(g, "Настройки", CONTENT_X + GEAR_SIZE + 9, 73, bodyFont(7.0F), MUTED, false);
        }

        drawSettingRows(g, settingsRows);
    }

    private void drawSettingRow(Graphics2D g, Setting setting, int y) {
        int radius = orion3 ? 8 : 6;
        g.setColor(SURFACE);
        g.fill(new RoundRectangle2D.Float(L.moduleX, y, L.moduleW, L.moduleH, radius, radius));
        if (!orion3) {
            g.setColor(withAlpha(BORDER, 150));
            g.setStroke(new BasicStroke(1.0F));
            g.draw(new RoundRectangle2D.Float(L.moduleX + 0.5F, y + 0.5F, L.moduleW - 1, L.moduleH - 1, 6, 6));
        }

        drawText(g, setting.label(), L.moduleX + 14 * sc, y + L.moduleH / 2.0F + 3.5F * sc, bodyFont(9.5F * sc), TEXT, true);

        if (setting instanceof ChoiceSetting choice) {
            drawArrowBox(g, choiceLeftArrowX(), y + arrowOffsetY(), true);
            drawArrowBox(g, choiceRightArrowX(), y + arrowOffsetY(), false);
            String value = choice.options[Math.floorMod(choice.get.getAsInt(), choice.options.length)];
            float centerX = choiceLeftArrowX() + L.arrowSize + L.choiceValueW / 2.0F;
            drawCenteredText(g, value, centerX, y + L.moduleH / 2.0F + 3.0F * sc, bodyFont(8.5F * sc), PRIMARY);
        } else if (setting instanceof SliderSetting slider) {
            float t = (slider.get.get() - slider.min) / (slider.max - slider.min);
            t = Math.max(0.0F, Math.min(1.0F, t));
            float tx = sliderTrackX();
            float ty = y + L.moduleH / 2.0F;
            float trackH = Math.max(2.5F, 4 * sc);
            float knobR = 5 * sc;
            g.setColor(withAlpha(BORDER, 220));
            g.fill(new RoundRectangle2D.Float(tx, ty - trackH / 2, L.sliderTrackW, trackH, trackH, trackH));
            g.setPaint(new GradientPaint(tx, ty, PRIMARY_DARK, tx + L.sliderTrackW, ty, PRIMARY));
            g.fill(new RoundRectangle2D.Float(tx, ty - trackH / 2, L.sliderTrackW * t, trackH, trackH, trackH));
            float kx = tx + L.sliderTrackW * t;
            g.setColor(new Color(0, 0, 0, 60));
            g.fill(new Ellipse2D.Float(kx - knobR, ty - knobR + 0.8F, knobR * 2, knobR * 2));
            g.setColor(Color.WHITE);
            g.fill(new Ellipse2D.Float(kx - knobR, ty - knobR, knobR * 2, knobR * 2));
            String value = slider.integer
                    ? String.format(Locale.ROOT, "%d%s", Math.round(slider.get.get()), slider.suffix)
                    : String.format(Locale.ROOT, "%.2f%s", slider.get.get(), slider.suffix);
            drawText(g, value, tx + L.sliderTrackW + 8 * sc, ty + 3.0F * sc, bodyFont(8.0F * sc), MUTED, false);
        } else if (setting instanceof ColorSetting color) {
            Setting[] rows = editableRows();
            boolean expanded = expandedColor >= 0 && expandedColor < rows.length && rows[expandedColor] == setting;
            Color current = parseColor(color.get.get());
            float chipW = 30 * sc;
            float chipH = Math.max(10 * sc, L.swatchSize);
            float chipX = L.moduleX + L.moduleW - Math.round(14 * sc) - chipW;
            float chipY = y + (L.moduleH - chipH) / 2.0F;
            float chipRadius = Math.max(2.0F, 4 * sc);
            g.setColor(current);
            g.fill(new RoundRectangle2D.Float(chipX, chipY, chipW, chipH, chipRadius, chipRadius));
            g.setColor(expanded ? Color.WHITE : withAlpha(BORDER, 180));
            g.setStroke(new BasicStroke(expanded ? 1.6F : 1.0F));
            g.draw(new RoundRectangle2D.Float(chipX + 0.5F, chipY + 0.5F, chipW - 1, chipH - 1, chipRadius, chipRadius));

            String hex = color.get.get().toUpperCase(Locale.ROOT);
            Font hexFont = bodyFont(8.0F * sc);
            float hexWidth = (float) g.getFontMetrics(hexFont).getStringBounds(hex, g).getWidth();
            drawText(g, hex, chipX - 8 * sc - hexWidth, y + L.moduleH / 2.0F + 3.0F * sc, hexFont, MUTED, false);
        } else if (setting instanceof ToggleSetting toggle) {
            drawToggle(g, toggleX(), y + (L.moduleH - L.toggleH) / 2.0F,
                    toggle.get.get() ? 1.0F : 0.0F);
        } else if (setting instanceof TextSetting text) {
            boolean editing = editingTextSetting() == setting;
            float bx = textBoxX();
            float by = y + arrowOffsetY();
            float bw = textBoxW();
            float cornerRadius = 5 * sc;
            g.setColor(SEARCH_BG);
            g.fill(new RoundRectangle2D.Float(bx, by, bw, L.arrowSize, cornerRadius, cornerRadius));
            g.setColor(editing ? PRIMARY : withAlpha(BORDER, 150));
            g.setStroke(new BasicStroke(editing ? 1.4F : 1.0F));
            g.draw(new RoundRectangle2D.Float(bx + 0.5F, by + 0.5F, bw - 1, L.arrowSize - 1, cornerRadius, cornerRadius));

            String value = text.get.get();
            Font valueFont = bodyFont(8.5F * sc);
            float baseline = by + L.arrowSize / 2.0F + 3.0F * sc;
            String displayedValue = ellipsize(g, valueFont, value, bw - 12 * sc);
            drawText(g, displayedValue, bx + 6 * sc, baseline, valueFont, TEXT, false);
            if (editing && (System.currentTimeMillis() / 500L) % 2L == 0L) {
                float valueWidth = textWidth(g, valueFont, displayedValue);
                g.setColor(PRIMARY);
                g.setStroke(new BasicStroke(1.0F));
                float caretX = bx + 7 * sc + valueWidth;
                g.draw(new Line2D.Float(caretX, by + 3 * sc, caretX, by + L.arrowSize - 3 * sc));
            }
        } else if (setting instanceof ButtonSetting button) {
            float bx = textBoxX();
            float by = y + arrowOffsetY();
            float bw = textBoxW();
            float cornerRadius = 5 * sc;
            g.setColor(SEARCH_BG);
            g.fill(new RoundRectangle2D.Float(bx, by, bw, L.arrowSize, cornerRadius, cornerRadius));
            g.setColor(withAlpha(PRIMARY, 190));
            g.setStroke(new BasicStroke(1.2F));
            g.draw(new RoundRectangle2D.Float(bx + 0.5F, by + 0.5F, bw - 1, L.arrowSize - 1, cornerRadius, cornerRadius));
            drawCenteredText(g, button.buttonText, bx + bw / 2.0F,
                    by + L.arrowSize / 2.0F + 3.0F * sc, bodyFont(8.5F * sc), PRIMARY);
        }
    }

    /** Раскрытая палитра: SV-квадрат, вертикальная полоса тона, превью с hex и быстрые пресеты. */
    private void drawColorPicker(Graphics2D g, ColorSetting color, int panelY) {
        int radius = orion3 ? 8 : 6;
        int panelH = pickerPanelHeight();
        g.setColor(SEARCH_BG);
        g.fill(new RoundRectangle2D.Float(L.moduleX, panelY, L.moduleW, panelH, radius, radius));
        if (!orion3) {
            g.setColor(withAlpha(BORDER, 150));
            g.setStroke(new BasicStroke(1.0F));
            g.draw(new RoundRectangle2D.Float(L.moduleX + 0.5F, panelY + 0.5F, L.moduleW - 1, panelH - 1, radius, radius));
        }

        PickerLayout p = pickerLayout(panelY);

        // SV-квадрат: слева направо — насыщенность, сверху вниз — яркость.
        g.setPaint(new GradientPaint(p.svX(), p.svY(), Color.WHITE,
                p.svX() + p.svS(), p.svY(), Color.getHSBColor(pickerHue, 1.0F, 1.0F)));
        g.fill(new Rectangle2D.Float(p.svX(), p.svY(), p.svS(), p.svS()));
        g.setPaint(new GradientPaint(p.svX(), p.svY(), new Color(0, 0, 0, 0),
                p.svX(), p.svY() + p.svS(), Color.BLACK));
        g.fill(new Rectangle2D.Float(p.svX(), p.svY(), p.svS(), p.svS()));
        g.setColor(withAlpha(BORDER, 180));
        g.setStroke(new BasicStroke(1.0F));
        g.draw(new Rectangle2D.Float(p.svX() - 0.5F, p.svY() - 0.5F, p.svS() + 1, p.svS() + 1));

        float knobX = p.svX() + pickerSat * p.svS();
        float knobY = p.svY() + (1.0F - pickerVal) * p.svS();
        float knobR = Math.max(2.5F, 3.5F * sc);
        g.setColor(new Color(0, 0, 0, 120));
        g.setStroke(new BasicStroke(2.5F));
        g.draw(new Ellipse2D.Float(knobX - knobR, knobY - knobR, knobR * 2, knobR * 2));
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(1.4F));
        g.draw(new Ellipse2D.Float(knobX - knobR, knobY - knobR, knobR * 2, knobR * 2));

        // Полоса тона.
        for (int i = 0; i < p.svS(); i++) {
            g.setColor(Color.getHSBColor(i / (float) p.svS(), 1.0F, 1.0F));
            g.fill(new Rectangle2D.Float(p.hueX(), p.svY() + i, p.hueW(), 1.25F));
        }
        g.setColor(withAlpha(BORDER, 180));
        g.setStroke(new BasicStroke(1.0F));
        g.draw(new Rectangle2D.Float(p.hueX() - 0.5F, p.svY() - 0.5F, p.hueW() + 1, p.svS() + 1));
        float hueY = p.svY() + pickerHue * p.svS();
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke(Math.max(1.5F, 2.0F * sc)));
        g.draw(new Line2D.Float(p.hueX() - 1.5F, hueY, p.hueX() + p.hueW() + 1.5F, hueY));

        // Превью текущего цвета и hex.
        Color current = Color.getHSBColor(pickerHue, pickerSat, pickerVal);
        float previewRadius = Math.max(2.0F, 4 * sc);
        g.setColor(current);
        g.fill(new RoundRectangle2D.Float(p.rightX(), p.svY(), p.previewW(), p.previewH(), previewRadius, previewRadius));
        g.setColor(withAlpha(BORDER, 180));
        g.setStroke(new BasicStroke(1.0F));
        g.draw(new RoundRectangle2D.Float(p.rightX() + 0.5F, p.svY() + 0.5F,
                p.previewW() - 1, p.previewH() - 1, previewRadius, previewRadius));
        String hex = String.format(Locale.ROOT, "#%02X%02X%02X",
                current.getRed(), current.getGreen(), current.getBlue());
        drawText(g, hex, p.rightX() + p.previewW() + 7 * sc,
                p.svY() + p.previewH() / 2.0F + 3.0F * sc, bodyFont(8.5F * sc), TEXT, false);

        // Быстрые пресеты.
        float dotRadius = Math.max(2.0F, 3 * sc);
        for (int s = 0; s < color.presets.length; s++) {
            float dx = p.rightX() + (s % 4) * p.dotStep();
            float dy = p.dotsY() + (s / 4) * p.dotStep();
            g.setColor(parseColor(color.presets[s]));
            g.fill(new RoundRectangle2D.Float(dx, dy, p.dotSize(), p.dotSize(), dotRadius, dotRadius));
            g.setColor(withAlpha(BORDER, 180));
            g.setStroke(new BasicStroke(1.0F));
            g.draw(new RoundRectangle2D.Float(dx + 0.5F, dy + 0.5F, p.dotSize() - 1, p.dotSize() - 1, dotRadius, dotRadius));
        }
    }

    private int textBoxX() {
        return choiceLeftArrowX();
    }

    private int textBoxW() {
        return L.arrowSize * 2 + L.choiceValueW;
    }

    private void drawArrowBox(Graphics2D g, float x, float y, boolean left) {
        g.setColor(SEARCH_BG);
        g.fill(new RoundRectangle2D.Float(x, y, L.arrowSize, L.arrowSize, 5 * sc, 5 * sc));
        g.setColor(withAlpha(BORDER, 150));
        g.setStroke(new BasicStroke(1.0F));
        g.draw(new RoundRectangle2D.Float(x + 0.5F, y + 0.5F, L.arrowSize - 1, L.arrowSize - 1, 5 * sc, 5 * sc));
        float cx = x + L.arrowSize / 2.0F;
        float cy = y + L.arrowSize / 2.0F;
        float d = (left ? 1.8F : -1.8F) * sc;
        g.setColor(TEXT);
        g.setStroke(new BasicStroke(Math.max(1.1F, 1.5F * sc), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Line2D.Float(cx + d, cy - 3.5F * sc, cx - d, cy));
        g.draw(new Line2D.Float(cx - d, cy, cx + d, cy + 3.5F * sc));
    }

    private static void drawCenteredText(Graphics2D g, String text, float centerX, float baseline, Font font, Color color) {
        g.setFont(font);
        float width = (float) g.getFontMetrics(font).getStringBounds(text, g).getWidth();
        drawText(g, text, centerX - width / 2.0F, baseline, font, color, false);
    }

    private static Color parseColor(String hex) {
        try {
            return new Color(Integer.parseInt(hex.substring(1), 16));
        } catch (RuntimeException exception) {
            return ORION_PALETTE.accent;
        }
    }

    private int gearX() {
        return orion3 ? toggleX() - 6 - L.gearSize : L.moduleX + L.moduleW - 38;
    }

    private int choiceRightArrowX() {
        return L.moduleX + L.moduleW - Math.round(14 * sc) - L.arrowSize;
    }

    private int choiceLeftArrowX() {
        return choiceRightArrowX() - L.choiceValueW - L.arrowSize;
    }

    private int sliderTrackX() {
        return L.moduleX + L.moduleW - Math.round(14 * sc) - Math.round(38 * sc) - L.sliderTrackW;
    }

    private void drawSearch(Graphics2D g) {
        if (orion3) {
            drawOrion3Search(g);
            return;
        }
        float x = 303;
        float y = 44;
        float w = 138;
        float h = 27;
        g.setColor(SEARCH_BG);
        g.fill(new RoundRectangle2D.Float(x, y, w, h, 6, 6));
        g.setColor(withAlpha(BORDER, 130));
        g.setStroke(new BasicStroke(1.0F));
        g.draw(new RoundRectangle2D.Float(x + 0.5F, y + 0.5F, w - 1, h - 1, 6, 6));
        if (searchQuery.isEmpty()) {
            drawText(g, "Поиск...", x + 10, y + 17.5F, bodyFont(8.2F), PLACEHOLDER, false);
        } else {
            Font queryFont = bodyFont(8.2F);
            drawText(g, searchQuery, x + 10, y + 17.5F, queryFont, TEXT, false);
            if ((System.currentTimeMillis() / 500L) % 2L == 0L) {
                float queryWidth = (float) g.getFontMetrics(queryFont).getStringBounds(searchQuery, g).getWidth();
                g.setColor(PRIMARY);
                g.setStroke(new BasicStroke(1.0F));
                g.draw(new Line2D.Float(x + 11.5F + queryWidth, y + 8, x + 11.5F + queryWidth, y + h - 8));
            }
        }
        g.setColor(withAlpha(BORDER, 100));
        g.draw(new Line2D.Float(x + w - 27, y + 6, x + w - 27, y + h - 6));
        g.setStroke(new BasicStroke(1.5F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(MUTED);
        g.draw(new Ellipse2D.Float(x + w - 19.5F, y + 8, 8, 8));
        g.draw(new Line2D.Float(x + w - 12.5F, y + 15.5F, x + w - 8.5F, y + 19.5F));
    }

    /** Крупная строка поиска из макета ORION3: по центру лупа и надпись SEARCH в разрядку. */
    private void drawOrion3Search(Graphics2D g) {
        float x = L.moduleX;
        float y = 16;
        float w = L.moduleW;
        float h = 24;
        g.setColor(SEARCH_BG);
        g.fill(new RoundRectangle2D.Float(x, y, w, h, 7, 7));
        g.setColor(withAlpha(BORDER, 150));
        g.setStroke(new BasicStroke(1.0F));
        g.draw(new RoundRectangle2D.Float(x + 0.5F, y + 0.5F, w - 1, h - 1, 7, 7));

        float centerY = y + h / 2.0F;
        if (searchQuery.isEmpty()) {
            String placeholder = spaced("SEARCH");
            Font font = headingFont(7.0F);
            float textWidth = (float) g.getFontMetrics(font).getStringBounds(placeholder, g).getWidth();
            float iconGap = 7;
            float iconRadius = 3.6F;
            float blockWidth = iconRadius * 2 + 3 + iconGap + textWidth;
            float iconX = x + (w - blockWidth) / 2.0F;
            drawMagnifier(g, iconX, centerY, iconRadius, PLACEHOLDER);
            drawText(g, placeholder, iconX + iconRadius * 2 + 3 + iconGap, centerY + 2.6F, font, PLACEHOLDER, false);
        } else {
            drawMagnifier(g, x + 9, centerY, 3.6F, MUTED);
            Font queryFont = bodyFont(6.5F);
            float textX = x + 22;
            drawText(g, searchQuery, textX, centerY + 2.4F, queryFont, TEXT, false);
            if ((System.currentTimeMillis() / 500L) % 2L == 0L) {
                float queryWidth = (float) g.getFontMetrics(queryFont).getStringBounds(searchQuery, g).getWidth();
                g.setColor(PRIMARY);
                g.setStroke(new BasicStroke(1.0F));
                g.draw(new Line2D.Float(textX + queryWidth + 1.5F, y + 7, textX + queryWidth + 1.5F, y + h - 7));
            }
        }
    }

    private static void drawMagnifier(Graphics2D g, float x, float centerY, float radius, Color color) {
        g.setColor(color);
        g.setStroke(new BasicStroke(Math.max(1.1F, radius * 0.29F), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        float offset = radius * 0.27F;
        g.draw(new Ellipse2D.Float(x, centerY - radius - offset, radius * 2, radius * 2));
        float hx = x + radius + radius * 0.72F;
        float hy = centerY - offset + radius * 0.72F;
        g.draw(new Line2D.Float(hx, hy, hx + radius * 0.62F, hy + radius * 0.62F));
    }

    private static String spaced(String text) {
        StringBuilder builder = new StringBuilder(text.length() * 2);
        for (int i = 0; i < text.length(); i++) {
            builder.append(text.charAt(i));
            if (i < text.length() - 1) {
                builder.append(' ');
            }
        }
        return builder.toString();
    }

    private void drawModule(Graphics2D g, ModuleRow row, int y, float hover) {
        boolean active = isActive(row.action);
        float animation = toggleAnim[row.action.ordinal()];

        int radius = orion3 ? 10 : 6;
        g.setColor(mix(SURFACE, SURFACE_HOVER, hover));
        g.fill(new RoundRectangle2D.Float(L.moduleX, y, L.moduleW, L.moduleH, radius, radius));

        if (orion3) {
            // Карточки в макете без рамок; при ховере проступает едва заметный контур.
            if (hover > 0.01F) {
                g.setColor(withAlpha(BORDER, Math.round(120 * hover)));
                g.setStroke(new BasicStroke(1.0F));
                g.draw(new RoundRectangle2D.Float(L.moduleX + 0.5F, y + 0.5F, L.moduleW - 1, L.moduleH - 1, radius, radius));
            }
            // Компактная карточка: только иконка и название по центру, как пустые карточки макета.
            Color iconColor = mix(TEXT, PRIMARY, animation);
            drawIcon(g, row.icon, L.moduleX + 15, y + L.moduleH / 2.0F, iconColor, 1.4F, 0.55F);
            drawText(g, row.title, L.moduleX + 28, y + L.moduleH / 2.0F + 2.5F, headingFont(7.2F), TEXT, true);
        } else {
            if (active) {
                g.setColor(PRIMARY);
                g.setStroke(new BasicStroke(1.3F));
            } else {
                g.setColor(withAlpha(BORDER, Math.round(150 + 60 * hover)));
                g.setStroke(new BasicStroke(1.0F));
            }
            g.draw(new RoundRectangle2D.Float(L.moduleX + 0.5F, y + 0.5F, L.moduleW - 1, L.moduleH - 1, radius, radius));

            Color iconColor = mix(TEXT, PRIMARY, animation);
            drawIcon(g, row.icon, L.moduleX + 23, y + 26, iconColor, 1.8F, 1.0F);
            int textX = L.moduleX + 23 + 12 + ICON_TEXT_GAP;
            drawText(g, row.title, textX, y + 22.5F, headingFont(10.8F), TEXT, true);
            // Описание всегда нейтрально-серое: акцентный оранжевый на тёмном фоне при таком кегле нечитаем.
            drawText(g, row.description, textX, y + 36.5F, bodyFont(8.2F), mix(MUTED, TEXT, animation * 0.35F), false);
        }

        drawToggle(g, toggleX(), y + (L.moduleH - L.toggleH) / 2.0F, animation);
        if (hasSettings(row.action)) {
            drawGearButton(g, gearX(), y + gearOffsetY(), hover);
        }
    }

    private void drawToggle(Graphics2D g, float x, float y, float animation) {
        float eased = easeInOutCubic(animation);
        if (orion3) {
            // Контурный тумблер из макета: тёмная пилюля, обводка и круглый бегунок
            // перекрашиваются из серого в акцентный.
            g.setColor(SEARCH_BG);
            g.fill(new RoundRectangle2D.Float(x, y, L.toggleW, L.toggleH, L.toggleH, L.toggleH));
            Color rim = mix(new Color(0xD8, 0xD8, 0xDE), PRIMARY, eased);
            g.setColor(rim);
            g.setStroke(new BasicStroke(1.4F));
            g.draw(new RoundRectangle2D.Float(x + 0.7F, y + 0.7F, L.toggleW - 1.4F, L.toggleH - 1.4F, L.toggleH - 1.4F, L.toggleH - 1.4F));

            float radius = (L.toggleH - 6) / 2.0F + 0.5F;
            float inset = 3.2F;
            float cx = x + inset + radius + (L.toggleW - radius * 2 - inset * 2) * eased;
            float cy = y + L.toggleH / 2.0F;
            g.setColor(rim);
            g.fill(new Ellipse2D.Float(cx - radius, cy - radius, radius * 2, radius * 2));
            return;
        }
        g.setPaint(new GradientPaint(x, y, mix(BORDER, PRIMARY, eased), x + L.toggleW, y + L.toggleH, mix(darken(BORDER, 8), PRIMARY_DARK, eased)));
        g.fill(new RoundRectangle2D.Float(x, y, L.toggleW, L.toggleH, L.toggleH, L.toggleH));
        g.setColor(new Color(255, 255, 255, Math.round(20 + 20 * eased)));
        g.setStroke(new BasicStroke(1.0F));
        g.draw(new RoundRectangle2D.Float(x + 0.5F, y + 0.5F, L.toggleW - 1, L.toggleH - 1, L.toggleH - 1, L.toggleH - 1));

        float radius = 6.5F;
        float cx = x + 3 + radius + (L.toggleW - radius * 2 - 6) * eased;
        float cy = y + L.toggleH / 2.0F;
        g.setColor(new Color(0, 0, 0, 60));
        g.fill(new Ellipse2D.Float(cx - radius, cy - radius + 0.8F, radius * 2, radius * 2));
        g.setColor(mix(TEXT, Color.WHITE, eased));
        g.fill(new Ellipse2D.Float(cx - radius, cy - radius, radius * 2, radius * 2));
    }

    private void drawGearButton(Graphics2D g, float x, float y, float hover) {
        float size = L.gearSize;
        float k = size / 25.0F;
        g.setColor(mix(SEARCH_BG, lighten(SEARCH_BG, 12), hover));
        g.fill(new RoundRectangle2D.Float(x, y, size, size, 5 * k, 5 * k));
        g.setColor(withAlpha(BORDER, 150));
        g.setStroke(new BasicStroke(1.0F));
        g.draw(new RoundRectangle2D.Float(x + 0.5F, y + 0.5F, size - 1, size - 1, 5 * k, 5 * k));

        float cx = x + size / 2;
        float cy = y + size / 2;
        g.setColor(mix(MUTED, TEXT, hover));
        g.setStroke(new BasicStroke(Math.max(1.1F, 1.5F * k), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(new Ellipse2D.Float(cx - 3.2F * k, cy - 3.2F * k, 6.4F * k, 6.4F * k));
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI / 4 * i;
            float sin = (float) Math.sin(angle);
            float cos = (float) Math.cos(angle);
            g.draw(new Line2D.Float(cx + cos * 5.2F * k, cy + sin * 5.2F * k, cx + cos * 7.4F * k, cy + sin * 7.4F * k));
        }
    }

    private void drawIcon(Graphics2D g, Icon icon, float cx, float cy, Color color, float stroke, float size) {
        Graphics2D g2 = (Graphics2D) g.create();
        g2.translate(cx, cy);
        g2.scale(size, size);
        g2.setColor(color);
        g2.setStroke(new BasicStroke(stroke / size, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        drawIconShape(g2, icon);
        g2.dispose();
    }

    private static void drawIconShape(Graphics2D g, Icon icon) {
        switch (icon) {
            case CROSSHAIR -> drawCrosshair(g, 11);
            case PLAYER -> {
                g.draw(new Ellipse2D.Float(-5, -11, 10, 10));
                Path2D body = new Path2D.Float();
                body.moveTo(-12, 12);
                body.curveTo(-9, 3, 9, 3, 12, 12);
                g.draw(body);
            }
            case MOVEMENT -> {
                g.draw(new Line2D.Float(-11, 11, 11, -11));
                g.draw(new Line2D.Float(-10, -10, 10, 10));
                g.draw(new Line2D.Float(3, -13, 11, -5));
            }
            case EYE -> {
                Path2D path = new Path2D.Float();
                path.moveTo(-13, 0);
                path.curveTo(-6, -9, 6, -9, 13, 0);
                path.curveTo(6, 9, -6, 9, -13, 0);
                g.draw(path);
                g.fill(new Ellipse2D.Float(-4, -4, 8, 8));
            }
            case CUBE -> {
                Path2D path = new Path2D.Float();
                path.moveTo(0, -13);
                path.lineTo(11, -7);
                path.lineTo(11, 7);
                path.lineTo(0, 13);
                path.lineTo(-11, 7);
                path.lineTo(-11, -7);
                path.closePath();
                g.draw(path);
                g.draw(new Line2D.Float(0, 0, 0, 13));
                g.draw(new Line2D.Float(0, 0, 11, -7));
                g.draw(new Line2D.Float(0, 0, -11, -7));
            }
            case GRID -> {
                for (int row = 0; row < 2; row++) {
                    for (int col = 0; col < 2; col++) {
                        g.draw(new RoundRectangle2D.Float(-11 + col * 13, -11 + row * 13, 8, 8, 2, 2));
                    }
                }
            }
            case FOLDER -> {
                Path2D path = new Path2D.Float();
                path.moveTo(-12, -8);
                path.lineTo(-3, -8);
                path.lineTo(1, -4);
                path.lineTo(12, -4);
                path.lineTo(12, 9);
                path.lineTo(-12, 9);
                path.closePath();
                g.draw(path);
            }
            case SETTINGS -> {
                g.draw(new Ellipse2D.Float(-6, -6, 12, 12));
                for (int i = 0; i < 8; i++) {
                    double angle = Math.PI / 4 * i;
                    float sin = (float) Math.sin(angle);
                    float cos = (float) Math.cos(angle);
                    g.draw(new Line2D.Float(cos * 9, sin * 9, cos * 13, sin * 13));
                }
            }
            case HAT -> {
                Path2D cone = new Path2D.Float();
                cone.moveTo(0, -13);
                cone.lineTo(10, 6);
                cone.lineTo(-10, 6);
                cone.closePath();
                g.draw(cone);
                g.draw(new Line2D.Float(-13, 10, 13, 10));
            }
            case RINGS -> {
                g.draw(new Ellipse2D.Float(-12, -2, 24, 11));
                g.draw(new Ellipse2D.Float(-6, 0.5F, 12, 6));
                g.draw(new Line2D.Float(0, -12, 0, -6));
            }
            case EXPAND -> {
                g.draw(new Line2D.Float(-10, 10, -3, 3));
                g.draw(new Line2D.Float(-11, 4, -11, 11));
                g.draw(new Line2D.Float(-4, 11, -11, 11));
                g.draw(new Line2D.Float(10, -10, 3, -3));
                g.draw(new Line2D.Float(11, -4, 11, -11));
                g.draw(new Line2D.Float(4, -11, 11, -11));
            }
            case MUSIC -> {
                g.draw(new Line2D.Float(-3, 7, -3, -11));
                g.draw(new Line2D.Float(-3, -11, 10, -8));
                g.draw(new Line2D.Float(10, -8, 10, 5));
                g.fill(new Ellipse2D.Float(-9.5F, 4, 7, 6));
                g.fill(new Ellipse2D.Float(3.5F, 2, 7, 6));
            }
            case MONITOR -> {
                g.draw(new RoundRectangle2D.Float(-12, -10, 24, 16, 4, 4));
                g.draw(new Line2D.Float(-5, 11, 5, 11));
                g.draw(new Line2D.Float(0, 6, 0, 11));
            }
            case SUN -> {
                g.fill(new Ellipse2D.Float(-4.5F, -4.5F, 9, 9));
                for (int i = 0; i < 8; i++) {
                    double angle = Math.PI / 4 * i;
                    float sin = (float) Math.sin(angle);
                    float cos = (float) Math.cos(angle);
                    g.draw(new Line2D.Float(cos * 8, sin * 8, cos * 12, sin * 12));
                }
            }
            case CHAT -> {
                Path2D bubble = new Path2D.Float();
                bubble.moveTo(-12, -9);
                bubble.lineTo(12, -9);
                bubble.lineTo(12, 6);
                bubble.lineTo(-2, 6);
                bubble.lineTo(-8, 12);
                bubble.lineTo(-8, 6);
                bubble.lineTo(-12, 6);
                bubble.closePath();
                g.draw(bubble);
                g.fill(new Ellipse2D.Float(-6.5F, -3.5F, 4, 4));
                g.fill(new Ellipse2D.Float(2.5F, -3.5F, 4, 4));
            }
        }
    }

    private static void drawCrosshair(Graphics2D g, float radius) {
        g.draw(new Ellipse2D.Float(-radius / 2, -radius / 2, radius, radius));
        g.draw(new Line2D.Float(0, -radius - 5, 0, -radius / 2));
        g.draw(new Line2D.Float(0, radius / 2, 0, radius + 5));
        g.draw(new Line2D.Float(-radius - 5, 0, -radius / 2, 0));
        g.draw(new Line2D.Float(radius / 2, 0, radius + 5, 0));
    }

    private static BufferedImage orion3LogoImage;
    private static boolean orion3LogoLoadAttempted;

    private static BufferedImage orion3Logo() {
        if (!orion3LogoLoadAttempted) {
            orion3LogoLoadAttempted = true;
            try (InputStream in = OrionClickGuiScreen.class.getResourceAsStream(ORION3_LOGO_RESOURCE)) {
                if (in != null) {
                    orion3LogoImage = ImageIO.read(in);
                }
            } catch (IOException ignored) {
            }
        }
        return orion3LogoImage;
    }

    private static void drawLogo(Graphics2D g, float x, float y, float size, Color color, float stroke) {
        g.setColor(color);
        g.setStroke(new BasicStroke(stroke, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        float arm = size * 0.28F;
        Path2D path = new Path2D.Float();
        path.moveTo(x, y - size);
        path.lineTo(x + arm, y - arm);
        path.lineTo(x + size, y);
        path.lineTo(x + arm, y + arm);
        path.lineTo(x, y + size);
        path.lineTo(x - arm, y + arm);
        path.lineTo(x - size, y);
        path.lineTo(x - arm, y - arm);
        path.closePath();
        g.draw(path);
        g.fill(new Ellipse2D.Float(x - 1.5F, y - 1.5F, 3, 3));
    }

    private void updateHover(int localX, int localY) {
        hoveredCategory = categoryAt(localX, localY);
        hoveredModule = settingsFor == null ? moduleAt(localX, localY) : -1;
    }

    private int categoryAt(int localX, int localY) {
        for (int i = 0; i < Category.VALUES.length; i++) {
            int y = L.sideY + i * L.sideStep;
            if (isInside(localX, localY, L.sideX, y, L.sideW, L.sideH)) {
                return i;
            }
        }
        return -1;
    }

    private int moduleAt(int localX, int localY) {
        ModuleRow[] rows = rowsForSelectedCategory();
        int visible = Math.min(rows.length, maxVisibleRows());
        for (int i = 0; i < visible; i++) {
            int y = L.moduleY + i * L.moduleStep;
            if (isInside(localX, localY, L.moduleX, y, L.moduleW, L.moduleH)) {
                return i;
            }
        }
        return -1;
    }

    private void toggle(Action action) {
        OrionConfig config = configManager.getConfig();
        switch (action) {
            case TARGET_ESP -> configManager.setTargetEspEnabled(!config.isTargetEspEnabled());
            case CHINA_HEAD -> configManager.setChinaHeadEnabled(!config.isChinaHeadEnabled());
            case JUMP_CIRCLE -> configManager.setJumpCircleEnabled(!config.isJumpCircleEnabled());
            case ASPECT -> configManager.setAspectEnabled(!config.isAspectEnabled());
            case MEDIA_PLAYER -> configManager.setMediaPlayerEnabled(!config.isMediaPlayerEnabled());
            case HUD -> configManager.setHudEnabled(!config.isHudEnabled());
            case DISCORD -> {
                configManager.setDiscordRpcEnabled(!config.isDiscordRpcEnabled());
                OrionClient.getInstance().getDiscordPresenceService().applyConfigNow();
            }
            case FULL_BRIGHT -> configManager.setFullBrightEnabled(!config.isFullBrightEnabled());
            case WORLD_PARTICLES -> configManager.setWorldParticlesEnabled(!config.isWorldParticlesEnabled());
            case NO_WEATHER -> configManager.setNoWeatherEnabled(!config.isNoWeatherEnabled());
            case NO_FIRE -> configManager.setNoFireEnabled(!config.isNoFireEnabled());
            case WORLD_CHANGER -> configManager.setWorldChangerEnabled(!config.isWorldChangerEnabled());
            case HEALTH_TAGS -> configManager.setHealthTagsEnabled(!config.isHealthTagsEnabled());
            case PROTECT_NAME -> configManager.setProtectNameEnabled(!config.isProtectNameEnabled());
            case FAKE_PREFIX -> configManager.setFakePrefixEnabled(!config.isFakePrefixEnabled());
            case HIT_BOX -> configManager.setHitBoxEnabled(!config.isHitBoxEnabled());
            case HIT_COLOR -> configManager.setHitColorEnabled(!config.isHitColorEnabled());
            case HIT_SOUND -> configManager.setHitSoundEnabled(!config.isHitSoundEnabled());
            case CROSSHAIR -> configManager.setCrosshairEnabled(!config.isCrosshairEnabled());
            case BETTER_NEAR -> configManager.setBetterNearEnabled(!config.isBetterNearEnabled());
            case RAINBOW -> configManager.setRainbowEnabled(!config.isRainbowEnabled());
        }
        lastFingerprint = null;
    }

    private boolean isActive(Action action) {
        OrionConfig config = configManager.getConfig();
        return switch (action) {
            case TARGET_ESP -> config.isTargetEspEnabled();
            case CHINA_HEAD -> config.isChinaHeadEnabled();
            case JUMP_CIRCLE -> config.isJumpCircleEnabled();
            case ASPECT -> config.isAspectEnabled();
            case MEDIA_PLAYER -> config.isMediaPlayerEnabled();
            case HUD -> config.isHudEnabled();
            case DISCORD -> config.isDiscordRpcEnabled();
            case FULL_BRIGHT -> config.isFullBrightEnabled();
            case WORLD_PARTICLES -> config.isWorldParticlesEnabled();
            case NO_WEATHER -> config.isNoWeatherEnabled();
            case NO_FIRE -> config.isNoFireEnabled();
            case WORLD_CHANGER -> config.isWorldChangerEnabled();
            case HEALTH_TAGS -> config.isHealthTagsEnabled();
            case PROTECT_NAME -> config.isProtectNameEnabled();
            case FAKE_PREFIX -> config.isFakePrefixEnabled();
            case HIT_BOX -> config.isHitBoxEnabled();
            case HIT_COLOR -> config.isHitColorEnabled();
            case HIT_SOUND -> config.isHitSoundEnabled();
            case CROSSHAIR -> config.isCrosshairEnabled();
            case BETTER_NEAR -> config.isBetterNearEnabled();
            case RAINBOW -> config.isRainbowEnabled();
        };
    }

    private ModuleRow[] rowsForSelectedCategory() {
        if (!searchQuery.isBlank()) {
            String query = searchQuery.toLowerCase(Locale.ROOT).trim();
            return Arrays.stream(ModuleRow.ALL)
                    .filter(row -> row.title.toLowerCase(Locale.ROOT).contains(query)
                            || row.description.toLowerCase(Locale.ROOT).contains(query))
                    .toArray(ModuleRow[]::new);
        }
        return switch (Category.VALUES[selectedCategory]) {
            case COMBAT -> ModuleRow.COMBAT;
            case PLAYER -> ModuleRow.PLAYER;
            case MOVEMENT -> ModuleRow.MOVEMENT;
            case RENDER -> ModuleRow.RENDER;
            case WORLD -> ModuleRow.WORLD;
            case MISC -> ModuleRow.MISC;
            case SETTINGS -> ModuleRow.SETTINGS;
        };
    }

    private void updateAnimations() {
        long now = System.nanoTime();
        float dt = lastFrameNanos == 0L ? 0.0F : (now - lastFrameNanos) / 1_000_000_000.0F;
        lastFrameNanos = now;
        float toggleStep = Math.min(1.0F, dt * 1000.0F / TOGGLE_ANIMATION_MS);
        float hoverStep = Math.min(1.0F, dt * 1000.0F / HOVER_ANIMATION_MS);

        for (Action action : Action.VALUES) {
            int i = action.ordinal();
            toggleAnim[i] = approach(toggleAnim[i], isActive(action) ? 1.0F : 0.0F, toggleStep);
        }
        for (int i = 0; i < categoryHoverAnim.length; i++) {
            categoryHoverAnim[i] = approach(categoryHoverAnim[i], i == hoveredCategory ? 1.0F : 0.0F, hoverStep);
        }
        for (int i = 0; i < moduleHoverAnim.length; i++) {
            moduleHoverAnim[i] = approach(moduleHoverAnim[i], i == hoveredModule ? 1.0F : 0.0F, hoverStep);
        }
    }

    private List<Integer> fingerprint(int scale) {
        List<Integer> values = new ArrayList<>(32);
        values.add(scale);
        values.add(guiDisplayWidth());
        values.add(guiDisplayHeight());
        values.add(configManager.getConfig().getClickGuiTheme());
        values.add(selectedCategory);
        values.add(hoveredCategory);
        values.add(hoveredModule);
        for (Action action : Action.VALUES) {
            values.add(isActive(action) ? 1 : 0);
            values.add(Math.round(toggleAnim[action.ordinal()] * 255));
        }
        for (float hover : categoryHoverAnim) {
            values.add(Math.round(hover * 255));
        }
        for (float hover : moduleHoverAnim) {
            values.add(Math.round(hover * 255));
        }
        values.add(settingsFor == null ? -1 : settingsFor.action.ordinal());
        values.add(searchQuery.hashCode());
        values.add(searchQuery.isEmpty() ? 0 : (int) ((System.currentTimeMillis() / 500L) % 2L));
        for (Setting setting : settingsRows) {
            if (setting instanceof ChoiceSetting choice) {
                values.add(choice.get.getAsInt());
            } else if (setting instanceof SliderSetting slider) {
                values.add(Math.round(slider.get.get() * 1000));
            } else if (setting instanceof ColorSetting color) {
                values.add(color.get.get().hashCode());
            } else if (setting instanceof TextSetting text) {
                values.add(text.get.get().hashCode());
            } else if (setting instanceof ToggleSetting toggle) {
                values.add(toggle.get.get() ? 1 : 0);
            }
        }
        values.add(editingText);
        values.add(editingText >= 0 ? (int) ((System.currentTimeMillis() / 500L) % 2L) : -1);
        values.add(expandedColor);
        values.add(Math.round(pickerHue * 1000.0F));
        values.add(Math.round(pickerSat * 1000.0F));
        values.add(Math.round(pickerVal * 1000.0F));
        return values;
    }

    private void applyPalette() {
        int theme = configManager.getConfig().getClickGuiTheme();
        orion3 = theme == 2;
        L = orion3 ? ORION3_LAYOUT : CLASSIC_LAYOUT;
        sc = orion3 ? 0.65F : 1.0F;
        pal = switch (theme) {
            case 1 -> PRIZRAK_PALETTE;
            case 2 -> ORION3_PALETTE;
            default -> ORION_PALETTE;
        };
        PRIMARY = pal.accent;
        PRIMARY_DARK = pal.accentDark;
        BG = pal.bg;
        SIDEBAR_BG = pal.sidebar;
        SURFACE = pal.surface;
        SURFACE_HOVER = pal.surfaceHover;
        BORDER = pal.border;
        TEXT = pal.text;
        MUTED = pal.muted;
        SEARCH_BG = pal.inset;
        PLACEHOLDER = pal.placeholder;
    }

    private int guiX() {
        return Math.max(0, (width - guiDisplayWidth()) / 2);
    }

    private int guiY() {
        return Math.max(0, (height - guiDisplayHeight()) / 2);
    }

    private float displayFactor() {
        float factor = switch (configManager.getConfig().getClickGuiTheme()) {
            case 1 -> ORION2_TARGET_SCALE;
            case 2 -> ORION3_TARGET_SCALE;
            default -> ORION_TARGET_SCALE;
        };
        if (width > 0 && height > 0) {
            // Не выпускаем окно за экран (при крупном gui scale места меньше).
            factor = Math.min(factor, (width - SCREEN_FIT_MARGIN) / (float) L.guiW);
            factor = Math.min(factor, (height - SCREEN_FIT_MARGIN) / (float) L.guiH);
        }
        return Math.max(0.4F, factor);
    }

    private int guiDisplayWidth() {
        return Math.max(1, Math.round(L.guiW * displayFactor()));
    }

    private int guiDisplayHeight() {
        return Math.max(1, Math.round(L.guiH * displayFactor()));
    }

    private int toLocalX(double screenX) {
        return (int) Math.floor((screenX - guiX()) * L.guiW / (double) guiDisplayWidth());
    }

    private int toLocalY(double screenY) {
        return (int) Math.floor((screenY - guiY()) * L.guiH / (double) guiDisplayHeight());
    }

    private static void setupGraphics(Graphics2D g) {
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
    }

    private static Font headingFont(float size) {
        Font font = pickFont(size, Font.PLAIN, "Inter SemiBold", "Inter Semi Bold", "Segoe UI Semibold");
        if (font != null) {
            return font;
        }
        font = pickFont(size, Font.BOLD, "Inter", "SF Pro Display", "Segoe UI");
        return font != null ? font : new Font(Font.SANS_SERIF, Font.BOLD, Math.round(size)).deriveFont(Font.BOLD, size);
    }

    private static Font bodyFont(float size) {
        Font font = pickFont(size, Font.PLAIN, "Inter", "SF Pro Display", "Segoe UI");
        return font != null ? font : new Font(Font.SANS_SERIF, Font.PLAIN, Math.round(size)).deriveFont(Font.PLAIN, size);
    }

    private static Font pickFont(float size, int style, String... families) {
        for (String family : families) {
            Font font = new Font(family, style, Math.round(size));
            if (font.getFamily().equalsIgnoreCase(family) && font.canDisplay('Я') && font.canDisplay('ё')) {
                return font.deriveFont(style, size);
            }
        }
        return null;
    }

    private static void drawText(Graphics2D g, String text, float x, float baseline, Font font, Color color, boolean shadow) {
        g.setFont(font);
        if (shadow) {
            g.setColor(new Color(0, 0, 0, 90));
            g.drawString(text, x + 0.7F, baseline + 0.9F);
        }
        g.setColor(color);
        g.drawString(text, x, baseline);
    }

    private static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha);
    }

    private static Color lighten(Color color, int amount) {
        return new Color(Math.min(255, color.getRed() + amount), Math.min(255, color.getGreen() + amount),
                Math.min(255, color.getBlue() + amount), color.getAlpha());
    }

    private static Color darken(Color color, int amount) {
        return new Color(Math.max(0, color.getRed() - amount), Math.max(0, color.getGreen() - amount),
                Math.max(0, color.getBlue() - amount), color.getAlpha());
    }

    private static Color mix(Color from, Color to, float t) {
        t = Math.max(0.0F, Math.min(1.0F, t));
        return new Color(Math.round(from.getRed() + (to.getRed() - from.getRed()) * t),
                Math.round(from.getGreen() + (to.getGreen() - from.getGreen()) * t),
                Math.round(from.getBlue() + (to.getBlue() - from.getBlue()) * t),
                Math.round(from.getAlpha() + (to.getAlpha() - from.getAlpha()) * t));
    }

    private static float approach(float current, float target, float step) {
        return current < target ? Math.min(target, current + step) : Math.max(target, current - step);
    }

    private static float easeInOutCubic(float value) {
        if (value < 0.5F) {
            return 4.0F * value * value * value;
        }
        float f = -2.0F * value + 2.0F;
        return 1.0F - f * f * f / 2.0F;
    }

    private static boolean isInside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private static String ellipsize(Graphics2D g, Font font, String text, float maxWidth) {
        if (textWidth(g, font, text) <= maxWidth) {
            return text;
        }

        String ellipsis = "…";
        int length = text.length();
        while (length > 0 && textWidth(g, font, text.substring(0, length) + ellipsis) > maxWidth) {
            length--;
        }
        return text.substring(0, length) + ellipsis;
    }

    private static float textWidth(Graphics2D g, Font font, String text) {
        Font measuring = font.deriveFont(font.getSize2D() * 16.0F);
        return (float) g.getFontMetrics(measuring).getStringBounds(text, g).getWidth() / 16.0F;
    }

    private enum Category {
        COMBAT("Combat", Icon.CROSSHAIR),
        PLAYER("Player", Icon.PLAYER),
        MOVEMENT("Movement", Icon.MOVEMENT),
        RENDER("Render", Icon.EYE),
        WORLD("World", Icon.CUBE),
        MISC("Misc", Icon.GRID),
        SETTINGS("Settings", Icon.SETTINGS);

        private static final Category[] VALUES = values();
        private final String title;
        private final Icon icon;

        Category(String title, Icon icon) {
            this.title = title;
            this.icon = icon;
        }
    }

    private enum Icon {
        CROSSHAIR, PLAYER, MOVEMENT, EYE, CUBE, GRID, FOLDER, SETTINGS, HAT, RINGS, EXPAND, MUSIC, MONITOR, SUN, CHAT
    }

    private sealed interface Setting permits ChoiceSetting, SliderSetting, ColorSetting, ToggleSetting, TextSetting,
            ButtonSetting {
        String label();
    }

    private record ButtonSetting(String label, String buttonText, Runnable onClick) implements Setting {
    }

    private record TextSetting(String label, Supplier<String> get, Consumer<String> set, int maxLength)
            implements Setting {
    }

    private record ChoiceSetting(String label, String[] options, IntSupplier get, IntConsumer set) implements Setting {
    }

    private record SliderSetting(String label, float min, float max, Supplier<Float> get, Consumer<Float> set,
                                 String suffix, boolean integer) implements Setting {
        private SliderSetting(String label, float min, float max, Supplier<Float> get, Consumer<Float> set,
                              String suffix) {
            this(label, min, max, get, set, suffix, false);
        }
    }

    private record ColorSetting(String label, String[] presets, Supplier<String> get,
                                Consumer<String> set) implements Setting {
    }

    private record ToggleSetting(String label, Supplier<Boolean> get, Consumer<Boolean> set) implements Setting {
    }

    private enum Action {
        TARGET_ESP, CHINA_HEAD, JUMP_CIRCLE, ASPECT, MEDIA_PLAYER, HUD, DISCORD, FULL_BRIGHT,
        WORLD_PARTICLES, NO_WEATHER, NO_FIRE, WORLD_CHANGER, HEALTH_TAGS, PROTECT_NAME, FAKE_PREFIX,
        HIT_BOX, HIT_COLOR, HIT_SOUND, CROSSHAIR, BETTER_NEAR, RAINBOW;

        private static final Action[] VALUES = values();
    }

    private record ModuleRow(String title, String description, Icon icon, Action action) {
        private static final ModuleRow[] COMBAT = {
                new ModuleRow("TargetEsp", "Эффект на цели под прицелом", Icon.CROSSHAIR, Action.TARGET_ESP),
                new ModuleRow("HitBox", "Перекрашивает хитбоксы (F3+B)", Icon.CUBE, Action.HIT_BOX),
                new ModuleRow("HitColor", "Заливка хитбокса при ударе", Icon.CROSSHAIR, Action.HIT_COLOR),
                new ModuleRow("HitSound", "Звук при ударе по цели", Icon.MUSIC, Action.HIT_SOUND),
                new ModuleRow("BetterNear", "Игроки рядом: ник, дистанция, стрелка", Icon.PLAYER, Action.BETTER_NEAR)
        };
        private static final ModuleRow[] PLAYER = {
                new ModuleRow("ChinaHead", "Рисует шляпу над головой", Icon.HAT, Action.CHINA_HEAD),
                new ModuleRow("Aspect", "Растягивает обзор (stretch)", Icon.EXPAND, Action.ASPECT),
                new ModuleRow("ProtectName", "Прячет ваш ник за псевдонимом", Icon.PLAYER, Action.PROTECT_NAME),
                new ModuleRow("FakePrefix", "Заменяет ваш донат-префикс", Icon.PLAYER, Action.FAKE_PREFIX)
        };
        private static final ModuleRow[] MOVEMENT = {
                new ModuleRow("JumpCircle", "Кольцо на земле при прыжке", Icon.RINGS, Action.JUMP_CIRCLE)
        };
        private static final ModuleRow[] RENDER = {
                new ModuleRow("RainbowColor", "Все цвета переливаются радугой", Icon.SUN, Action.RAINBOW),
                new ModuleRow("Crosshair", "Свой прицел: рисуется в редакторе", Icon.CROSSHAIR, Action.CROSSHAIR),
                new ModuleRow("FullBright", "Повышает яркость мира", Icon.SUN, Action.FULL_BRIGHT),
                new ModuleRow("HUD", "Отображает интерфейс клиента", Icon.MONITOR, Action.HUD),
                new ModuleRow("Health Indicator", "Показывает сердечки игроков", Icon.PLAYER, Action.HEALTH_TAGS),
                new ModuleRow("NoFire", "Убирает огонь с экрана полностью", Icon.EYE, Action.NO_FIRE)
        };
        private static final ModuleRow[] WORLD = {
                new ModuleRow("NoWeather", "Отключает дождь и снег", Icon.CUBE, Action.NO_WEATHER),
                new ModuleRow("WorldChanger", "Меняет цвет неба и тумана", Icon.EYE, Action.WORLD_CHANGER),
                new ModuleRow("WorldParticles", "Добавляет частицы в мире", Icon.GRID, Action.WORLD_PARTICLES)
        };
        private static final ModuleRow[] MISC = {
                new ModuleRow("Discord", "Показывает статус в Discord", Icon.CHAT, Action.DISCORD),
                new ModuleRow("MediaPlayer", "Виджет проигрываемой музыки", Icon.MUSIC, Action.MEDIA_PLAYER)
        };
        private static final ModuleRow[] SETTINGS = {};

        private static final ModuleRow[] ALL;

        static {
            List<ModuleRow> all = new ArrayList<>();
            for (ModuleRow[] rows : new ModuleRow[][]{COMBAT, PLAYER, MOVEMENT, RENDER, WORLD, MISC}) {
                all.addAll(Arrays.asList(rows));
            }
            ALL = all.toArray(ModuleRow[]::new);
        }
    }
}
