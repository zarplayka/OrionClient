# Orion Client — руководство для агентов

Fabric-мод (client-only) для Minecraft **26.3**, Java **25**, fabric-loader 0.19.5, fabric-api 0.164.0+26.3.
Код: `src/main/java/ru/orion/client/…`, ресурсы: `src/main/resources/…`, миксины: `orion-client.mixins.json`.
Папка `sources/` — чужой референс-клиент (пакет `tech.relevantfree`), **его не редактируем**, только подсматриваем идеи.

Пакеты:

| Пакет | Что там |
|---|---|
| `ru.orion.client` | `OrionClient` — входная точка, регистрирует все модули, `getConfigOrNull()` для миксинов |
| `config` | `OrionConfig` (POJO + `sanitize()`), `OrionConfigManager` (json в `config/orion-client.json`, сеттеры сразу `save()`) |
| `hud` | `OrionHudRenderer` — watermark-HUD (две «пилюли») |
| `gui` | `OrionClickGuiScreen` — ClickGUI на весь экран |
| `effect` | мировые модули: TargetESP, JumpCircle, WorldParticles, NameProtect и т.д. |
| `mixin` | миксины (регистрировать в `orion-client.mixins.json`, секция `client`) |

Маппинги — mojmap-подобные, но **не доверяй памяти по именам классов**: тут `Identifier` (не `ResourceLocation`), `RenderTypes`, `GuiGraphicsExtractor`, `CharacterEvent`/`KeyEvent`/`MouseButtonEvent`. Всегда проверяй реальные сигнатуры через `javap` по jar из loom-cache (см. «Сборка» внизу).

---

## Как устроен интерфейс (ГЛАВНОЕ)

**Мы не используем виджеты Minecraft и не рисуем примитивами GuiGraphics.** Весь UI (HUD, ClickGUI, медиаплеер) рисуется **Java2D** (`java.awt.Graphics2D`) в `BufferedImage` в нативном разрешении, загружается в `DynamicTexture` и выводится на экран **одним blit'ом**. Это даёт настоящее сглаживание, дробные размеры шрифтов, градиенты и ровные скругления, которых нет в ванильном рендере.

### Канонический конвейер (так сделаны OrionHudRenderer и OrionClickGuiScreen)

```java
// 1. Дизайн ведётся в «юнитах»: 1 юнит = 1 gui-пиксель при масштабе 1.
//    Размер текстуры = юниты × displayScale × guiScale (guiScale ограничиваем!).
private static final int MAX_TEXTURE_SCALE = 6;
int scale = Math.max(1, Math.min(MAX_TEXTURE_SCALE, client.getWindow().getGuiScale()));

// 2. Fingerprint: пересобираем картинку ТОЛЬКО когда изменились входные данные.
//    Никогда не рисуй BufferedImage каждый кадр — это убьёт FPS.
List<Object> fingerprint = List.of(scale, username, fps, accentColor, ...);
if (!fingerprint.equals(lastFingerprint)) { updateTexture(...); lastFingerprint = fingerprint; }
// Мигающие элементы (курсор) вносят в fingerprint фазу: (System.currentTimeMillis() / 500L) % 2L.

// 3. Замер макета «пробным» контекстом ДО создания настоящей картинки:
BufferedImage probe = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
Graphics2D pg = probe.createGraphics();
pg.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
float w = width(pg, font, text);   // считаем ширины, из них — размер холста
pg.dispose();

// 4. Настоящий холст: пиксели = юниты × scale, затем g.scale() переводит рисование в юниты.
BufferedImage image = new BufferedImage(designW * scale, designH * scale, BufferedImage.TYPE_INT_ARGB);
Graphics2D g = image.createGraphics();
g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_ON);
g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
g.setRenderingHint(RenderingHints.KEY_RENDERING,         RenderingHints.VALUE_RENDER_QUALITY);
g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL,    RenderingHints.VALUE_STROKE_PURE);
g.scale(imageWidth / designWidth, imageHeight / designHeight);
// ... рисуем в юнитах ...
g.dispose();

// 5. Загрузка в текстуру. Если размер НЕ изменился — переиспользуем DynamicTexture:
//    копируем пиксели в существующий NativeImage и dynamicTexture.upload().
//    Если изменился — release() старой, создаём новую NativeImage + DynamicTexture, register().
//    (см. uploadTexture()/copyPixels() в OrionHudRenderer — копируй этот код, он правильный)

// 6. Вывод: одна команда, UV всегда 0..1.
context.blit(TEXTURE_ID, x, y, x + guiWidth, y + guiHeight, 0.0F, 1.0F, 0.0F, 1.0F);
```

Критично для резкости: **пиксельный размер картинки = размеру blit'а × guiScale, точно**. Тогда тексель ложится 1:1 в пиксель экрана и ничего не мылится. Если размеры разъедутся хоть на пиксель — весь элемент станет мутным.

Обязательная уборка: у экранов в `removed()` — `configManager.save()` и `textureManager.release(TEXTURE_ID)`.

### Готовый шаблон нового HUD-элемента

Новый оверлей делай копией этого скелета (это выжимка из `OrionHudRenderer` — там всё то же с реальным содержимым):

```java
public final class OrionFooHud {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "hud_foo"); // уникальный id!
    private static final int MAX_TEXTURE_SCALE = 6;

    private final OrionConfigManager configManager;
    private DynamicTexture dynamicTexture;
    private int textureWidth, textureHeight;   // пиксели
    private int guiWidth, guiHeight;           // gui-пиксели для blit
    private List<Object> lastFingerprint;

    public void register() {
        HudElementRegistry.addLast(Identifier.fromNamespaceAndPath(OrionClient.MOD_ID, "foo"), this::render);
    }

    private void render(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        if (!configManager.getConfig().isFooEnabled()) return;
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.player == null) return;

        int scale = Math.max(1, Math.min(MAX_TEXTURE_SCALE, client.getWindow().getGuiScale()));
        List<Object> fp = List.of(scale, /* все данные, влияющие на картинку */);
        if (!fp.equals(lastFingerprint)) { updateTexture(scale /*, данные */); lastFingerprint = fp; }
        if (dynamicTexture == null) return;

        int x = ..., y = ...;
        context.blit(TEXTURE, x, y, x + guiWidth, y + guiHeight, 0.0F, 1.0F, 0.0F, 1.0F);
    }

    // updateTexture: probe-замер -> BufferedImage(designW*scale, designH*scale) -> hints -> g.scale -> рисование -> uploadTexture(image)
    // uploadTexture / copyPixels: скопируй ДОСЛОВНО из OrionHudRenderer (release/register vs повторный upload)
}
```

Регистрация — одна строка в `OrionClient.onInitializeClient()` (поле + `register()`), по образцу соседей.

---

## Правила Java2D

**Скругления.** Только `RoundRectangle2D.Float(x, y, w, h, arcW, arcH)`. `arc` — это ДИАМЕТР дуги, не радиус:
- обычная карточка: `arc = 8–12` юнитов;
- **полная «пилюля»: `arcW = arcH = высоте прямоугольника`** — так сделаны пилюли HUD;
- **вложенные скругления**: внутренний arc = внешний arc − 2×зазор (иначе внутренний элемент «торчит углами» в скруглении родителя);
- рамка поверх заливки: `draw()` со сдвигом на полпикселя внутрь, иначе рамка мылится:
  ```java
  g.setColor(SURFACE);
  g.fill(new RoundRectangle2D.Float(x, y, w, h, 10, 10));
  g.setColor(withAlpha(BORDER, 150));
  g.setStroke(new BasicStroke(1.0F));
  g.draw(new RoundRectangle2D.Float(x + 0.5F, y + 0.5F, w - 1, h - 1, 10, 10));
  ```
- скругление «съедает» горизонтальный отступ: текст у края пилюли ставь с запасом ≥ 2 юнита сверх обычного паддинга.

**Шрифты.** Системные шрифты через `pickFont` (есть в OrionHudRenderer/ClickGUI): пробуем Inter → Segoe UI, обязательно проверяем `font.canDisplay('Я')` и `canDisplay('ё')` — интерфейс русскоязычный. Размер задаём `deriveFont(style, floatSize)` (дробные размеры работают). Не создавай `new Font(...)` в горячем цикле — шрифты собираются один раз на `updateTexture`.

**Замер ширины текста — известные грабли.** `FontMetrics.getStringBounds` на мелком кегле (6–10pt) **занижает** ширину из-за хинтинга, а рисуем-то мы в увеличенном масштабе — текст вылезает за подложку. Правильный замер — на 16-кратном кегле с делением:
```java
private static float width(Graphics2D g, Font font, String text) {
    Font measuring = font.deriveFont(font.getSize2D() * 16.0F);
    return (float) g.getFontMetrics(measuring).getStringBounds(text, g).getWidth() / 16.0F;
}
```

**Вертикальное центрирование текста** — по базовой линии, не по bounding box:
```java
float baseline = centerY + (metrics.getAscent() - metrics.getDescent()) / 2.0F;
```

**Тень текста** (см. `drawText` в ClickGUI): чёрный `alpha≈90` со сдвигом `+0.7, +0.9`, потом основной цвет.

**Иконки** — только вектором (`Path2D`, `Line2D`, `Ellipse2D`) со `BasicStroke(…, CAP_ROUND, JOIN_ROUND)`, никаких растровых картинок для мелкой графики.

**Инварианты макета.** Ширины блоков считаются из реальных ширин текста, а не хардкодом. Если элементы стоят рядами — явно фиксируй инвариант (пример из HUD: верхняя пилюля всегда `max(своя ширина, ширина нижнего ряда)`, чтобы нижний ряд не торчал за шапку). Проверяй макет с длинными никами/числами.

### Чёткость пикселей

- Линия толщиной 1 по целым координатам рисуется **между** пикселями и мылится. Правило: нечётная толщина → координаты `n + 0.5`, чётная → целые.
- `VALUE_STROKE_PURE` обязателен, иначе Java2D «прищёлкивает» штрихи к сетке и ломает дробную геометрию.
- Всё рисуем float-фигурами (`Line2D.Float`, `RoundRectangle2D.Float`…), никаких `g.drawRect/fillRect` с int.
- Два полупрозрачных шейпа внахлёст дают тёмный шов (альфа складывается). Если нужна единая полупрозрачная форма из кусков — объедини их в `Area` и залей один раз:
  ```java
  Area blob = new Area(new RoundRectangle2D.Float(...));
  blob.add(new Area(new Ellipse2D.Float(...)));      // объединение
  blob.subtract(new Area(new Ellipse2D.Float(...))); // вырез (дырка)
  g.fill(blob);
  ```

### Кулинарная книга Java2D (готовые приёмы)

**Вертикальный/горизонтальный градиент:**
```java
g.setPaint(new GradientPaint(x, y, topColor, x, y + h, bottomColor));
g.fill(shape);
g.setPaint(null); // не забываем сбрасывать? нет: просто setColor дальше перекроет
```
Многостоповый: `new LinearGradientPaint(x1, y1, x2, y2, new float[]{0F, 0.5F, 1F}, new Color[]{a, b, c})`.

**Свечение (glow) без шейдеров** — несколько заливок с растущим размером и падающей альфой:
```java
for (int i = 3; i >= 1; i--) {
    g.setColor(withAlpha(accent, 18 * (4 - i)));
    g.fill(new RoundRectangle2D.Float(x - i, y - i, w + i * 2, h + i * 2, arc + i * 2, arc + i * 2));
}
g.setColor(accent);
g.fill(new RoundRectangle2D.Float(x, y, w, h, arc, arc));
```
Мягкая тень под карточкой — то же самое чёрным с альфой 20–40 и сдвигом вниз на 1–2 юнита.

**Круговой прогресс / дуги:**
```java
g.setStroke(new BasicStroke(3F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
g.setColor(withAlpha(BORDER, 120));
g.draw(new Arc2D.Float(cx - r, cy - r, r * 2, r * 2, 0, 360, Arc2D.OPEN));      // подложка
g.setColor(accent);
g.draw(new Arc2D.Float(cx - r, cy - r, r * 2, r * 2, 90, -progress * 360, Arc2D.OPEN)); // 90 = старт сверху, минус = по часовой
```

**Прогресс-бар со скруглением** — не рисуй «заполненную часть» вторым раундректом (при малом прогрессе арки ломаются). Правильно — клип:
```java
Shape oldClip = g.getClip();
g.setColor(track); g.fill(new RoundRectangle2D.Float(x, y, w, h, h, h));
g.clip(new RoundRectangle2D.Float(x, y, w, h, h, h));  // клип по ПОЛНОЙ форме
g.setColor(accent); g.fill(new Rectangle2D.Float(x, y, w * progress, h));
g.setClip(oldClip); // ОБЯЗАТЕЛЬНО вернуть
```
Тот же приём — для любого контента, который не должен вылезать за карточку (обрезка списка, бегущая строка).

**Поворот/трансформация — только с сохранением состояния:**
```java
AffineTransform old = g.getTransform();
g.rotate(Math.toRadians(angle), cx, cy); // поворот вокруг точки
// ... рисуем ...
g.setTransform(old);
```
То же с `g.getStroke()`/`g.getComposite()`/`g.getClip()` — всё, что меняешь «временно», возвращай. Утёкшее состояние — классическая причина «почему всё съехало ниже по коду».

**Общая прозрачность группы** (затухание целого блока одним числом):
```java
Composite oldComposite = g.getComposite();
g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha)); // 0..1
// ... блок ...
g.setComposite(oldComposite);
```
Это правильный способ «fade in/out» — не надо прокидывать альфу в каждый цвет.

**Пунктир:**
```java
g.setStroke(new BasicStroke(1.2F, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
        10F, new float[]{3F, 4F}, 0F)); // штрих 3, пробел 4
```

**Разрядка (letter-spacing):**
```java
Map<TextAttribute, Object> attrs = new HashMap<>();
attrs.put(TextAttribute.TRACKING, 0.2F); // +20% кегля между буквами
Font spaced = font.deriveFont(attrs);
```

**Обрезка длинного текста с «…»** — обязательна везде, где строка приходит извне (ник, название трека):
```java
private static String ellipsize(Graphics2D g, Font font, String text, float maxWidth) {
    if (width(g, font, text) <= maxWidth) return text;
    String ellipsis = "…";
    int len = text.length();
    while (len > 0 && width(g, font, text.substring(0, len) + ellipsis) > maxWidth) len--;
    return text.substring(0, len) + ellipsis;
}
```

**Текст по градиенту / контурный текст** — через векторный контур:
```java
GlyphVector gv = font.createGlyphVector(g.getFontRenderContext(), text);
Shape outline = gv.getOutline(x, baseline);
g.setPaint(new GradientPaint(...)); g.fill(outline);          // градиентная заливка
g.setStroke(new BasicStroke(0.8F)); g.setColor(dark); g.draw(outline); // обводка
```

**Центрирование текста по ширине:** `x = centerX - width(g, font, text) / 2` (замер — только хелпером с ×16!).

**Скруглённый треугольник/стрелка/звезда** — `Path2D.Float` с `quadTo` (вогнутые грани) — примеры: звезда в `OrionHudRenderer.drawStar`, стрелки в `OrionClickGuiScreen.drawArrowBox`.

### Анимации

- Время — только реальное: `System.nanoTime()` для dt, `System.currentTimeMillis()` для фаз. Никаких «+0.05 за кадр» — скорость не должна зависеть от FPS.
- Плавное приближение к цели (все ховеры/тумблеры ClickGUI): `value = approach(value, target, dt * 1000 / DURATION_MS)`; изинг для красоты: `smooth = 0.5 - 0.5 * cos(PI * t)`.
- Раз анимация меняет картинку — она **обязана попадать в fingerprint**, иначе кадр не перерисуется. Квантуй, чтобы не пересобирать текстуру чаще нужного: `Math.round(anim * 255)` для плавных, `(millis / 500) % 2` для мигания.
- Дорогая пересборка каждый кадр допустима только пока анимация реально идёт (ClickGUI так и живёт: в покое fingerprint стабилен и blit'ится готовая текстура).
- Дешёвая альтернатива для мировых эффектов: анимировать не текстуру, а трансформ (PoseStack) — см. `effect/*`.

---

## ClickGUI (`OrionClickGuiScreen`) — как расширять

Экран наследует `Screen`. Особенности жизненного цикла:
- Рисование — в `@Override extractRenderState(GuiGraphicsExtractor context, mouseX, mouseY, deltaTicks)`: сначала `super.…`, затем `updateAnimations()` → `updateHover()` → `updateTextureIfNeeded()` → `context.fill(...)` (затемнение) + `context.blit(...)`.
- Ввод: `mouseClicked(MouseButtonEvent event, boolean doubleClick)` (`event.x()`, `event.y()`, `event.button()`), `mouseDragged`, `mouseReleased`, `charTyped(CharacterEvent)` (`isAllowedChatCharacter()`, `codepointAsString()`), `keyPressed(KeyEvent)` (`event.key()` + константы `InputConstants`).
- Координаты мыши переводи в юниты макета через `toLocalX/ toLocalY` — весь хит-тест в юнитах. **Зона клика = зона отрисовки**: если рисуешь бокс в `drawSettingRow`, хит-тест в `settingsClick` должен использовать те же формулы координат (выноси в общие методы: `toggleX()`, `textBoxX()`, `swatchX(...)`).
- Drag-паттерн: индекс перетаскиваемого элемента в поле (`draggingSlider`), захват в `mouseClicked`, обновление в `mouseDragged`, сброс + `configManager.save()` в `mouseReleased`.
- Темы: `Palette` (record с цветами) + `Layout` (record с геометрией) + множитель `sc`; выбираются в `applyPalette()` по `config.getClickGuiTheme()`. Новые цвета — только в палитры, не литералами в коде рисования. **Любую новую фичу проверяй во всех трёх темах** («Orion», «Orion2», «Orion3» — у третьей другой Layout и sc=0.65, всё мельче).
- Анимации: массивы `float[]` + `approach()` в `updateAnimations()`; состояние анимации попадает в fingerprint округлённым (`Math.round(anim * 255)`).
- Списки длиннее окна: сейчас просто `maxVisibleRows()` (обрезка). Если понадобится скролл — clip по контентной области + смещение, колесо ловить в `mouseScrolled`.

Настройки модулей — sealed-типы `Setting` (`ChoiceSetting`, `SliderSetting`, `ColorSetting`, `ToggleSetting`, `TextSetting`). Чтобы добавить **новый модуль**:
1. Поля + геттеры/сеттеры + `sanitize()` в `OrionConfig`, сеттеры с `save()` в `OrionConfigManager`.
2. Значение в enum `Action`; `ModuleRow` в нужную категорию (`COMBAT`/`PLAYER`/…).
3. Ветки в `toggle(Action)` и `isActive(Action)`.
4. Если есть настройки: добавить в `hasSettings()` и вернуть массив из `buildSettings()`.
5. Рендер нового типа Setting — ветка в `drawSettingRow` + хит-тест в `settingsClick` + вклад в `fingerprint()` (иначе не перерисуется!).

Текстовый ввод уже реализован (`TextSetting` + `editingText`): клик по полю включает режим, `charTyped`/`keyPressed` перехватываются ДО обработчика поиска, Enter/Esc завершают. Новые поля ввода делай через `TextSetting`, не изобретай второй механизм.

---

## Антипаттерны (не делать)

| Нельзя | Почему / что вместо |
|---|---|
| Рисовать BufferedImage каждый кадр | FPS умрёт. Fingerprint + пересборка только при изменениях |
| Ванильный шрифт MC для нашего UI | Пиксельный, без дробных размеров. Только Java2D-текст в текстуре |
| `g.drawRect/fillRect/drawString(int, int)` | Ломает дробную геометрию. Только `*2D.Float`-фигуры и float-координаты |
| Забыть `g.dispose()` / `pg.dispose()` | Утечка нативных ресурсов |
| Новый `DynamicTexture` при каждом обновлении | Пересоздание — только при смене размера; иначе `copyPixels` + `upload()` |
| `register()` без `release()` старой текстуры | Утечка и конфликт id |
| Один `Identifier` на два элемента | Текстуры перезатирают друг друга. Id уникален на элемент |
| Хардкод ширины под конкретный текст | Ломается на другом нике/языке/числе. Замер + инварианты |
| Цвет литералом в коде рисования ClickGUI | Ломает темы. Только через `Palette` |
| Магические координаты внутри метода | Выноси в константы юнитов или `Layout` — иначе хит-тест разъедется с отрисовкой |
| Анимация «за кадр», а не за время | Скорость зависит от FPS. Только dt по nanoTime |
| Полупрозрачные фигуры внахлёст | Тёмные швы. Объединяй в `Area`, заливай один раз |
| Менять stroke/clip/transform/composite и не вернуть | Дальше по коду всё поедет. Сохраняй и восстанавливай |
| `Thread`/IO внутри рендера | Рендер-тред. Тяжёлое — заранее или лениво с кешем |
| Свой парсер цвета | Есть `parseColor` с fallback — переиспользуй |

## Чеклист перед сдачей UI-задачи

1. Компиляция прошла (см. «Сборка»).
2. Длинные значения: ник 16 символов, FPS 4 цифры, длинное название — ничего не вылезает и не наезжает (ellipsize/инварианты).
3. Пустые/краевые значения: пустая строка, 0, отрицательные.
4. Кириллица отображается (шрифтовый fallback работает).
5. GuiScale 1–6 и `hudScale` 0.5–4: элемент остаётся резким (пиксели = blit × scale) и влезает в экран.
6. ClickGUI: все 3 темы; открытие/закрытие настроек; поиск активен — твой ввод с ним не конфликтует.
7. Всё новое состояние, влияющее на картинку, добавлено в fingerprint.
8. `removed()`/выключение модуля освобождает текстуры и сохраняет конфиг.
9. Конфиг: новые поля в `sanitize()` с дефолтами при мусоре в json.

## Отладка

- Запустить игру из песочницы нельзя — проверяй компиляцию javac'ом и проси пользователя посмотреть в игре; по скриншоту сверяй с юнитами макета (скриншот-пиксели / guiScale / displayScale = юниты).
- Быстрый способ проверить чистый Java2D-рендер без Minecraft: временный `main()`, который рисует ту же картинку и сохраняет `ImageIO.write(image, "png", new File(...))` в scratchpad — так можно итерировать макет мгновенно. В прод-код такой main не коммитим.
- «Текст вылезает» → замер без ×16-хелпера. «Всё мутное» → размер текстуры ≠ blit × guiScale. «Не обновляется» → данные не в fingerprint. «Рамки жирные/мыльные» → нет 0.5-сдвига или STROKE_PURE. «Швы на стыках» → полупрозрачный overlap вместо Area.

---

## Мировой рендер (кратко)

- Подписка: `LevelRenderEvents.COLLECT_SUBMITS.register(this::render)`; геометрия через `context.submitNodeCollector().submitCustomGeometry(poseStack, renderType, (pose, vertexConsumer) -> …)`.
- Кастомные `RenderPipeline`/`RenderType` — в `OrionEspRenderTypes` (регистрируются через `RenderPipelinesAccessor.orion$register`). Там два depth-режима: `ALWAYS_PASS` (виден сквозь всё) и `LESS_THAN_OR_EQUAL` без записи глубины (прячется за моделями/блоками) — для эффектов на сущностях используем depth-tested.
- Полная яркость вершин: свет `0xF000F0`.

## Подмена текста (NameProtect)

Все пути отрисовки текста сходятся в `Font.prepareText(String|FormattedCharSequence, …)` — `FontMixin` подменяет там ник через `OrionNameProtect`. Если нужен новый глобальный текст-фильтр — расширяй `OrionNameProtect.replace(...)`, а не плодите новые миксины.

## Конвенции

- Комментарии и строки UI — на русском; имена — на английском, классы с префиксом `Orion`.
- Конфиг: поле + геттер/сеттер + правка `sanitize()` (значения по умолчанию при мусоре) + сеттер в менеджере с `save()`.
- Цвета в конфиге — строки `"#RRGGBB"`, валидируются регэкспом в `sanitize()`, парсятся с fallback.

## Карта примеров — где подсмотреть готовый приём

| Приём | Где |
|---|---|
| Пилюли, звезда с вогнутыми гранями, baseline-центрирование, ×16-замер | `hud/OrionHudRenderer` |
| Тумблер с анимацией, слайдер с градиентом, цветовые свотчи, стрелки выбора | `gui/OrionClickGuiScreen.drawSettingRow`, `drawToggle` |
| Поле ввода с курсором (и в поиске, и в TextSetting) | `OrionClickGuiScreen.drawSearch`, ветка `TextSetting` |
| Темизация (палитры/лейауты/sc) | `OrionClickGuiScreen.applyPalette` + records `Palette`/`Layout` |
| Ховер/тумблер-анимации через approach + fingerprint | `OrionClickGuiScreen.updateAnimations`, `fingerprint` |
| Кляксы/рваные края/дырки в фигурах через Area (add/subtract) | техника — раздел «Кулинарная книга» выше |
| Upload текстуры (release/register vs reuse) | `OrionHudRenderer.uploadTexture` |
| Камера-facing квады, кольца, кристаллы в мире | `effect/OrionTargetEsp` |
| Кастомные пайплайны/depth-state | `net.minecraft.client.renderer.rendertype.OrionEspRenderTypes` |

## Сборка и проверка

- У пользователя проект собирается из IDE/Gradle — **не ломай build.gradle**.
- В песочнице агента `gradlew` может падать с `Unable to establish loopback connection` — это известно. Компиляцию проверяй прямым `javac`:
  - Для сборки проекта используй `gradlew.bat build` с Gradle 9.6 и Java 25. Если Gradle недоступен, classpath: `.gradle/loom-cache/minecraftMaven/net/minecraft/minecraft-merged-*/26.3/*.jar` (Minecraft в named-маппингах), jar'ы из `~/.gradle/caches/modules-2/files-2.1/` (fabric-loader 0.19.5, sponge-mixin 0.17.3, joml 1.10.8, slf4j-api, gson, jspecify, fastutil, brigadier 1.2.9), `sources/zar.jar`, модули fabric-api, извлечённые из `META-INF/jars/` fat-jar fabric-api;
  - `javac -encoding UTF-8 --release 25 -proc:none -cp "<cp>" @sources.txt` по всем файлам `src/main/java`;
  - в Git Bash пути для javac конвертируй `/c/…` → `C:/…`, classpath склеивай через `;`.
- Сигнатуры классов Minecraft смотри там же: `javap -cp <merged.jar> net.minecraft.client.gui.Font` (а для логики инжекта — `javap -c`). Дескрипторы в аннотациях миксинов бери из вывода `javap` дословно.
