package com.practicehud;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Locale;

/**
 * Custom XMCore client interface. All controls are drawn by the screen rather
 * than using vanilla ButtonWidget styling.
 */
public class ConfigScreen extends Screen {
    private static final String SETTINGS = "Settings";
    private static final String THEMES = "Themes";
    private static final String PROFILES = "Profiles";
    private static final int BG = 0xF5080912;
    private static final int PANEL = 0xFF101321;
    private static final int SIDEBAR = 0xFF0C0F1B;
    private static final int CARD = 0xFF171C2D;
    private static final int CARD_HOVER = 0xFF202740;
    private static final int TEXT = 0xFFF1F3FC;
    private static final int MUTED = 0xFF8992AA;
    private static final int ACCENT = 0xFF9A7BFF;
    private static final int ACCENT_DARK = 0xFF6049B8;

    private static String currentTab = Modules.CAT_VISUAL;
    private static String searchText = "";
    private int scroll;
    private boolean searchFocused;

    private static final String[] THEME_NAMES = {"Midnight", "Violet", "Ocean", "Crimson", "Light"};
    private static final int[][] THEME_COLORS = {
            {0xF20A0D20, 0xFF101329, 0xFF0D1023, 0xFF171A31, 0xFF7965F5, 0xFFF1F1FA, 0xFF858BA8},
            {0xF20D0A1C, 0xFF17102B, 0xFF100B20, 0xFF211638, 0xFFB36BFF, 0xFFF7EEFF, 0xFFA994BE},
            {0xF2081520, 0xFF0D202B, 0xFF091923, 0xFF112B38, 0xFF39C6E8, 0xFFEAFBFF, 0xFF82AAB8},
            {0xF21B0A12, 0xFF28101B, 0xFF190B14, 0xFF321522, 0xFFFF5C83, 0xFFFFF0F4, 0xFFB88B9A},
            {0xF2D9DCE8, 0xFFE9EAF2, 0xFFF5F6FB, 0xFFFFFFFF, 0xFF6554D9, 0xFF202238, 0xFF70758D}
    };

    public ConfigScreen() {
        super(Text.literal("XMCore"));
    }

    @Override
    protected void init() {
        // Intentionally no vanilla widgets: the complete interface is custom drawn.
    }

    private int[] palette() {
        return THEME_COLORS[Math.max(0, Math.min(THEME_COLORS.length - 1, Config.get().themeId))];
    }

    private int left() { return Math.max(12, (width - Math.min(1120, width - 24)) / 2); }
    private int top() { return Math.max(12, (height - Math.min(700, height - 24)) / 2); }
    private int panelW() { return Math.min(1120, width - 24); }
    private int panelH() { return Math.min(700, height - 24); }
    private int sideW() { return 202; }
    private int mainX() { return left() + sideW(); }
    private int mainW() { return panelW() - sideW(); }
    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    private List<Module> filteredModules() {
        String query = searchText.toLowerCase(Locale.ROOT).trim();
        return Modules.ALL.stream()
                .filter(m -> m.category.equals(currentTab))
                .filter(m -> query.isEmpty() || m.name.toLowerCase(Locale.ROOT).contains(query)
                        || m.description.toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    private void selectTab(String tab) {
        currentTab = tab;
        searchText = "";
        searchFocused = false;
        scroll = 0;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int[] p = palette();
        int x = left(), y = top(), w = panelW(), h = panelH();
        int sx = x + sideW(), contentW = mainW();

        ctx.fill(0, 0, width, height, BG);
        // Soft backdrop accents and the main window.
        ctx.fill(x - 5, y - 5, x + w + 5, y + h + 5, 0x28000000);
        ctx.fill(x, y, x + w, y + h, p[2]);
        ctx.fill(x, y, sx, y + h, SIDEBAR);
        ctx.fill(sx, y, sx + 1, y + h, 0xFF252A3B);

        // Brand block.
        ctx.fill(x + 20, y + 20, x + 54, y + 54, ACCENT_DARK);
        ctx.fill(x + 23, y + 23, x + 51, y + 51, ACCENT);
        ctx.drawCenteredTextWithShadow(textRenderer, "X", x + 37, y + 33, 0xFFFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "XMCORE", x + 65, y + 24, TEXT);
        ctx.drawText(textRenderer, "CLIENT FRAMEWORK", x + 65, y + 39, MUTED, false);
        ctx.fill(x + 18, y + 70, sx - 18, y + 71, 0xFF252A3B);

        ctx.drawText(textRenderer, "WORKSPACE", x + 20, y + 86, MUTED, false);
        int navY = y + 108;
        for (String category : Modules.CATEGORIES) {
            boolean active = category.equals(currentTab);
            boolean hover = inside(mouseX, mouseY, x + 12, navY, sideW() - 24, 31);
            drawNav(ctx, x + 12, navY, sideW() - 24, 31, category, active, hover);
            navY += 36;
        }

        int generalY = y + h - 150;
        ctx.fill(x + 18, generalY - 13, sx - 18, generalY - 12, 0xFF252A3B);
        ctx.drawText(textRenderer, "PREFERENCES", x + 20, generalY, MUTED, false);
        String[] general = {SETTINGS, THEMES, PROFILES};
        for (int i = 0; i < general.length; i++) {
            int gy = generalY + 20 + i * 34;
            boolean active = currentTab.equals(general[i]);
            drawNav(ctx, x + 12, gy, sideW() - 24, 29, general[i], active,
                    inside(mouseX, mouseY, x + 12, gy, sideW() - 24, 29));
        }

        // Header.
        String heading = currentTab.equals(SETTINGS) ? "Settings"
                : currentTab.equals(THEMES) ? "Appearance"
                : currentTab.equals(PROFILES) ? "HUD Profiles" : currentTab;
        String subtitle = currentTab.equals(SETTINGS) ? "Fine-tune how XMCore looks and feels"
                : currentTab.equals(THEMES) ? "Make the interface feel like yours"
                : currentTab.equals(PROFILES) ? "Switch between ready-made setups"
                : "Manage your client modules and visual features";
        ctx.drawTextWithShadow(textRenderer, heading, sx + 28, y + 24, TEXT);
        ctx.drawText(textRenderer, subtitle, sx + 28, y + 42, MUTED, false);
        ctx.drawText(textRenderer, "MalekXMine", x + 20, y + h - 20, MUTED, false);
        ctx.drawText(textRenderer, "v1.0  •  FABRIC", sx + contentW - 112, y + 27, MUTED, false);

        if (isCategory()) renderModules(ctx, mouseX, mouseY, sx, y, contentW, h);
        else if (currentTab.equals(SETTINGS)) renderSettings(ctx, mouseX, mouseY, sx, y, contentW);
        else if (currentTab.equals(THEMES)) renderThemes(ctx, mouseX, mouseY, sx, y, contentW);
        else renderProfiles(ctx, mouseX, mouseY, sx, y, contentW);

        // Close affordance.
        boolean closeHover = inside(mouseX, mouseY, x + w - 38, y + 12, 26, 24);
        ctx.fill(x + w - 38, y + 12, x + w - 12, y + 36, closeHover ? 0xFF573044 : 0xFF252A3B);
        ctx.drawCenteredTextWithShadow(textRenderer, "×", x + w - 25, y + 18, closeHover ? 0xFFFFA5B5 : TEXT);
    }

    private boolean isCategory() {
        return Modules.CATEGORIES.contains(currentTab);
    }

    private void drawNav(DrawContext ctx, int x, int y, int w, int h, String label, boolean active, boolean hover) {
        if (active) {
            ctx.fill(x, y, x + w, y + h, 0xFF292342);
            ctx.fill(x, y + 5, x + 3, y + h - 5, ACCENT);
        } else if (hover) {
            ctx.fill(x, y, x + w, y + h, 0xFF171D2C);
        }
        ctx.fill(x + 12, y + 10, x + 18, y + 16, active ? ACCENT : MUTED);
        ctx.drawText(textRenderer, label, x + 28, y + 10, active ? TEXT : 0xFFB5BDD0, false);
        if (active) ctx.fill(x + w - 17, y + 12, x + w - 11, y + 18, ACCENT);
    }

    private void renderModules(DrawContext ctx, int mx, int my, int sx, int y, int w, int h) {
        int searchX = sx + w - 238;
        int searchY = y + 66;
        boolean searchHover = inside(mx, my, searchX, searchY, 210, 30);
        ctx.fill(searchX, searchY, searchX + 210, searchY + 30, searchFocused ? 0xFF25213B : CARD);
        ctx.drawText(textRenderer, searchText.isEmpty() ? "⌕  Search modules..." : searchText,
                searchX + 11, searchY + 10, searchText.isEmpty() ? MUTED : TEXT, false);

        List<Module> modules = filteredModules();
        long enabled = modules.stream().filter(Module::isEnabled).count();
        ctx.drawText(textRenderer, modules.size() + " FEATURES", sx + 28, y + 76, MUTED, false);
        ctx.drawText(textRenderer, enabled + " ENABLED", sx + 128, y + 76, ACCENT, false);

        int gridY = y + 116;
        int gap = 12;
        int cardW = (w - 56 - gap) / 2;
        int cardH = 76;
        int rows = Math.max(1, (h - 144) / (cardH + gap));
        int start = scroll * 2;
        for (int i = start; i < Math.min(modules.size(), start + rows * 2); i++) {
            Module m = modules.get(i);
            int col = (i - start) % 2;
            int row = (i - start) / 2;
            int cx = sx + 28 + col * (cardW + gap);
            int cy = gridY + row * (cardH + gap);
            boolean hover = inside(mx, my, cx, cy, cardW, cardH);
            boolean on = m.isEnabled();
            ctx.fill(cx, cy, cx + cardW, cy + cardH, hover ? CARD_HOVER : CARD);
            ctx.fill(cx, cy, cx + 3, cy + cardH, on ? ACCENT : 0xFF343B50);
            ctx.fill(cx + 14, cy + 14, cx + 38, cy + 38, on ? 0xFF342A55 : 0xFF252B3B);
            ctx.drawCenteredTextWithShadow(textRenderer, shortIcon(m.name), cx + 26, cy + 22, on ? ACCENT : MUTED);
            ctx.drawTextWithShadow(textRenderer, m.name, cx + 48, cy + 13, TEXT);
            String desc = m.description;
            int max = Math.max(18, (cardW - 70) / 5);
            if (desc.length() > max) desc = desc.substring(0, max - 2) + "..";
            ctx.drawText(textRenderer, desc, cx + 48, cy + 30, MUTED, false);
            int tx = cx + cardW - 48, ty = cy + 28;
            ctx.fill(tx, ty, tx + 34, ty + 18, on ? ACCENT_DARK : 0xFF343B50);
            ctx.fill(on ? tx + 18 : tx + 2, ty + 2, on ? tx + 32 : tx + 16, ty + 16, on ? 0xFFFFFFFF : 0xFF929AAF);
            ctx.drawText(textRenderer, on ? "ON" : "OFF", cx + cardW - 48, cy + 54, on ? ACCENT : MUTED, false);
        }
        if (modules.isEmpty()) ctx.drawText(textRenderer, "No matching modules. Try another search.", sx + 30, gridY + 18, MUTED, false);
        int maxScroll = Math.max(0, (modules.size() - 1) / 2 - rows + 1);
        if (maxScroll > 0) {
            ctx.drawText(textRenderer, "SCROLL  •  " + (scroll + 1) + " / " + (maxScroll + 1),
                    sx + w - 145, y + h - 24, MUTED, false);
        }
    }

    private String shortIcon(String name) {
        if (name.isEmpty()) return "•";
        return name.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private void renderSettings(DrawContext ctx, int mx, int my, int sx, int y, int w) {
        Config c = Config.get();
        int x = sx + 28, yy = y + 82, rw = w - 56;
        drawSection(ctx, x, yy, "CROSSHAIR STUDIO", "Shape, size and behaviour"); yy += 42;
        drawSetting(ctx, mx, my, x, yy, rw, "Crosshair shape", new String[]{"Cross", "Dot", "Circle", "Cross + Dot"}[Math.max(0, Math.min(3, c.crosshairShape))], "Click to change"); yy += 39;
        drawSetting(ctx, mx, my, x, yy, rw, "Crosshair size", c.crosshairSize + " px", "Click to cycle"); yy += 39;
        drawSetting(ctx, mx, my, x, yy, rw, "Crosshair gap", c.crosshairGap + " px", "Click to cycle"); yy += 39;
        drawSetting(ctx, mx, my, x, yy, rw, "Crosshair thickness", c.crosshairThickness + " px", "Click to cycle"); yy += 39;
        drawSection(ctx, x, yy + 2, "ANIMATION", "Local first-person swing animation"); yy += 43;
        drawSetting(ctx, mx, my, x, yy, rw, "Swing speed", c.swingSpeedTicks + " ticks", "−   /   +"); yy += 39;
        drawSetting(ctx, mx, my, x, yy, rw, "Swing preset", c.swingSpeedTicks == 20 ? "Slow" : c.swingSpeedTicks == 3 ? "Prestige" : c.swingSpeedTicks == 6 ? "Vanilla" : "Custom", "Click to cycle"); yy += 39;
        drawSection(ctx, x, yy + 2, "HUD CUSTOMISATION", "Position and colour"); yy += 43;
        drawSetting(ctx, mx, my, x, yy, rw, "Dynamic crosshair", c.crosshairDynamic ? "Enabled" : "Disabled", "Click to toggle"); yy += 39;
        drawSetting(ctx, mx, my, x, yy, rw, "Crosshair outline", c.crosshairOutline ? "Enabled" : "Disabled", "Click to toggle"); yy += 39;
        drawSetting(ctx, mx, my, x, yy, rw, "Open HUD Studio", "Drag individual widgets", "Click to open");
    }

    private void drawSection(DrawContext ctx, int x, int y, String title, String sub) {
        ctx.drawTextWithShadow(textRenderer, title, x, y, ACCENT);
        ctx.drawText(textRenderer, sub, x + 180, y, MUTED, false);
    }

    private void drawSetting(DrawContext ctx, int mx, int my, int x, int y, int w, String name, String value, String hint) {
        boolean hover = inside(mx, my, x, y, w, 32);
        ctx.fill(x, y, x + w, y + 32, hover ? CARD_HOVER : CARD);
        ctx.drawText(textRenderer, name, x + 12, y + 11, TEXT, false);
        ctx.drawText(textRenderer, hint, x + w - 178, y + 11, MUTED, false);
        ctx.drawText(textRenderer, value, x + w - 90, y + 11, ACCENT, false);
    }

    private void renderThemes(DrawContext ctx, int mx, int my, int sx, int y, int w) {
        int x = sx + 28, yy = y + 100, cw = (w - 68) / 2;
        for (int i = 0; i < THEME_NAMES.length; i++) {
            int col = i % 2, row = i / 2;
            int cx = x + col * (cw + 12), cy = yy + row * 105;
            boolean active = Config.get().themeId == i;
            boolean hover = inside(mx, my, cx, cy, cw, 88);
            int[] t = THEME_COLORS[i];
            ctx.fill(cx, cy, cx + cw, cy + 88, hover ? CARD_HOVER : CARD);
            ctx.fill(cx + 12, cy + 12, cx + cw - 12, cy + 52, t[2]);
            ctx.fill(cx + 20, cy + 20, cx + 44, cy + 44, t[4]);
            ctx.fill(cx + 52, cy + 20, cx + 76, cy + 44, t[1]);
            ctx.fill(cx + 84, cy + 20, cx + 108, cy + 44, t[3]);
            ctx.drawTextWithShadow(textRenderer, THEME_NAMES[i], cx + 14, cy + 65, TEXT);
            ctx.drawText(textRenderer, active ? "ACTIVE" : "SELECT", cx + cw - 62, cy + 65, active ? ACCENT : MUTED, false);
            if (active) ctx.fill(cx, cy, cx + 3, cy + 88, ACCENT);
        }
    }

    private void renderProfiles(DrawContext ctx, int mx, int my, int sx, int y, int w) {
        String[] names = {"PvP", "Clean", "Builder"};
        String[] desc = {"Combat HUD, crosshair, keystrokes and performance", "Minimal FPS, coordinates and keystrokes", "Coordinates, world time, held item and FPS"};
        int x = sx + 28, yy = y + 104, cw = w - 56;
        for (int i = 0; i < names.length; i++) {
            int cy = yy + i * 94;
            boolean hover = inside(mx, my, x, cy, cw, 78);
            ctx.fill(x, cy, x + cw, cy + 78, hover ? CARD_HOVER : CARD);
            ctx.fill(x, cy, x + 3, cy + 78, ACCENT);
            ctx.drawTextWithShadow(textRenderer, names[i], x + 18, cy + 17, TEXT);
            ctx.drawText(textRenderer, desc[i], x + 18, cy + 37, MUTED, false);
            ctx.drawText(textRenderer, "APPLY  ›", x + cw - 76, cy + 29, ACCENT, false);
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x(), my = click.y();
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        int x = left(), y = top(), w = panelW(), h = panelH(), sx = x + sideW();
        if (inside(mx, my, x + w - 38, y + 12, 26, 24)) { close(); return true; }

        int navY = y + 108;
        for (String category : Modules.CATEGORIES) {
            if (inside(mx, my, x + 12, navY, sideW() - 24, 31)) { selectTab(category); return true; }
            navY += 36;
        }
        int generalY = y + h - 150;
        String[] general = {SETTINGS, THEMES, PROFILES};
        for (int i = 0; i < general.length; i++) {
            int gy = generalY + 20 + i * 34;
            if (inside(mx, my, x + 12, gy, sideW() - 24, 29)) { selectTab(general[i]); return true; }
        }

        if (isCategory()) {
            int searchX = sx + mainW() - 238, searchY = y + 66;
            if (inside(mx, my, searchX, searchY, 210, 30)) { searchFocused = true; return true; }
            searchFocused = false;
            List<Module> modules = filteredModules();
            int gridY = y + 116, gap = 12, cardW = (mainW() - 56 - gap) / 2, cardH = 76;
            int rows = Math.max(1, (h - 144) / (cardH + gap));
            int start = scroll * 2;
            for (int i = start; i < Math.min(modules.size(), start + rows * 2); i++) {
                int col = (i - start) % 2, row = (i - start) / 2;
                int cx = sx + 28 + col * (cardW + gap), cy = gridY + row * (cardH + gap);
                if (inside(mx, my, cx, cy, cardW, cardH)) {
                    modules.get(i).toggle();
                    Config.save();
                    return true;
                }
            }
            return true;
        }

        if (currentTab.equals(THEMES)) {
            int tx = sx + 28, ty = y + 100, cw = (mainW() - 68) / 2;
            for (int i = 0; i < THEME_NAMES.length; i++) {
                int cx = tx + (i % 2) * (cw + 12), cy = ty + (i / 2) * 105;
                if (inside(mx, my, cx, cy, cw, 88)) {
                    Config.get().themeId = i; Config.save(); return true;
                }
            }
        } else if (currentTab.equals(PROFILES)) {
            int px = sx + 28, py = y + 104, cw = mainW() - 56;
            for (int i = 0; i < 3; i++) if (inside(mx, my, px, py + i * 94, cw, 78)) {
                applyProfile(i == 0 ? "pvp" : i == 1 ? "clean" : "builder"); return true;
            }
        } else if (currentTab.equals(SETTINGS)) {
            handleSettingsClick(mx, my, sx, y, mainW());
            return true;
        }
        return true;
    }

    private void handleSettingsClick(double mx, double my, int sx, int y, int w) {
        Config c = Config.get();
        int x = sx + 28, yy = y + 82, rw = w - 56;
        if (inside(mx, my, x, yy + 42, rw, 32)) c.crosshairShape = (c.crosshairShape + 1) % 4;
        else if (inside(mx, my, x, yy + 81, rw, 32)) c.crosshairSize = c.crosshairSize >= 24 ? 1 : c.crosshairSize + 1;
        else if (inside(mx, my, x, yy + 120, rw, 32)) c.crosshairGap = c.crosshairGap >= 12 ? 0 : c.crosshairGap + 1;
        else if (inside(mx, my, x, yy + 159, rw, 32)) c.crosshairThickness = c.crosshairThickness >= 5 ? 1 : c.crosshairThickness + 1;
        else if (inside(mx, my, x, yy + 244, rw, 32)) c.swingSpeedTicks = Math.min(20, c.swingSpeedTicks + 1);
        else if (inside(mx, my, x, yy + 283, rw, 32)) c.swingSpeedTicks = c.swingSpeedTicks == 20 ? 6 : c.swingSpeedTicks == 6 ? 3 : 20;
        else if (inside(mx, my, x, yy + 365, rw, 32)) c.crosshairDynamic = !c.crosshairDynamic;
        else if (inside(mx, my, x, yy + 404, rw, 32)) c.crosshairOutline = !c.crosshairOutline;
        else if (inside(mx, my, x, yy + 443, rw, 32) && client != null) {
            Config.save(); client.setScreen(new HudEditorScreen()); return;
        }
        Config.save();
    }

    private void applyProfile(String profile) {
        Config c = Config.get();
        c.modules.replaceAll((id, enabled) -> false);
        if ("pvp".equals(profile)) {
            for (String id : List.of("reticle", "swing_speed", "guard_timing", "netherite_hud",
                    "keystrokes", "fps_counter", "coordinates", "armor_status")) c.modules.put(id, true);
        } else if ("clean".equals(profile)) {
            for (String id : List.of("fps_counter", "coordinates", "keystrokes")) c.modules.put(id, true);
        } else {
            for (String id : List.of("coordinates", "world_time", "held_item", "fps_counter")) c.modules.put(id, true);
        }
        Config.save();
    }

    @Override
    public boolean charTyped(CharInput input) {
        int codePoint = input.codepoint();
        if (searchFocused && isCategory() && !Character.isISOControl(codePoint)) {
            searchText += new String(Character.toChars(codePoint));
            scroll = 0;
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (searchFocused && isCategory()) {
            if (input.key() == GLFW.GLFW_KEY_BACKSPACE && !searchText.isEmpty()) {
                searchText = searchText.substring(0, searchText.length() - 1);
                scroll = 0;
                return true;
            }
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) { searchFocused = false; return true; }
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (isCategory()) {
            int rows = Math.max(1, (panelH() - 144) / 88);
            int max = Math.max(0, (filteredModules().size() - 1) / 2 - rows + 1);
            scroll = Math.max(0, Math.min(max, scroll + (verticalAmount < 0 ? 1 : -1)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public void close() {
        Config.save();
        super.close();
    }

    @Override
    public boolean shouldPause() { return false; }
}
