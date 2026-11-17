package com.practicehud;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * XMCore client interface.
 *
 * Everything is custom drawn in immediate mode: each frame the screen lays itself out,
 * draws, and registers a list of click regions ("hits") while doing so. Clicks are then
 * resolved against that list, so what you see is always exactly what you can click.
 * The sidebar and the content area both scroll, so nothing can overlap at any GUI scale.
 */
public class ConfigScreen extends Screen {
    private static final String SETTINGS = "Settings";
    private static final String THEMES = "Themes";
    private static final String PROFILES = "Profiles";
    private static final String[] GENERAL = {SETTINGS, THEMES, PROFILES};

    // Palette slots (see THEME_COLORS).
    private static final int P_OVERLAY = 0, P_SIDEBAR = 1, P_MAIN = 2, P_CARD = 3, P_ACCENT = 4, P_TEXT = 5, P_MUTED = 6;

    private static final String[] THEME_NAMES = {"Midnight", "Violet", "Ocean", "Crimson", "Light", "Halloween", "Christmas", "Sakura", "Emerald", "Sunset", "Cyberpunk", "Arctic", "Rose Gold", "Obsidian", "Lava", "Forest", "Cotton Candy"};
    private static final int[][] THEME_COLORS = {
            // Classic
            {0xF20A0D20, 0xFF101329, 0xFF0D1023, 0xFF171A31, 0xFF7965F5, 0xFFF1F1FA, 0xFF858BA8},
            {0xF20D0A1C, 0xFF17102B, 0xFF100B20, 0xFF211638, 0xFFB36BFF, 0xFFF7EEFF, 0xFFA994BE},
            {0xF2081520, 0xFF0D202B, 0xFF091923, 0xFF112B38, 0xFF39C6E8, 0xFFEAFBFF, 0xFF82AAB8},
            {0xF21B0A12, 0xFF28101B, 0xFF190B14, 0xFF321522, 0xFFFF5C83, 0xFFFFF0F4, 0xFFB88B9A},
            {0xF2D9DCE8, 0xFFE9EAF2, 0xFFF5F6FB, 0xFFFFFFFF, 0xFF6554D9, 0xFF202238, 0xFF70758D},
            // Seasonal
            {0xF21A0A16, 0xFF24102B, 0xFF140B1B, 0xFF30162E, 0xFFFF7A24, 0xFFFFE7C2, 0xFFB68A9B}, // Halloween
            {0xF20A1715, 0xFF102820, 0xFF091A16, 0xFF18372A, 0xFFE34B58, 0xFFFFF3DD, 0xFF9FC6AD}, // Christmas
            // Color stories
            {0xF21B101C, 0xFF291827, 0xFF170D19, 0xFF382034, 0xFFFF91B8, 0xFFFFF0F6, 0xFFC99BAD}, // Sakura
            {0xF20A1915, 0xFF102B22, 0xFF091B16, 0xFF17382B, 0xFF48D597, 0xFFE8FFF4, 0xFF86B7A0}, // Emerald
            {0xF21D1110, 0xFF2B1917, 0xFF1A0E0C, 0xFF3A211D, 0xFFFF986B, 0xFFFFF0E7, 0xFFC59B8D}, // Sunset
            {0xF2100B20, 0xFF1B1035, 0xFF10091F, 0xFF281744, 0xFF00E5FF, 0xFFF0F8FF, 0xFF9A8BC4}, // Cyberpunk
            {0xF20C1924, 0xFF142B3B, 0xFF0A1A28, 0xFF1A3547, 0xFF8EDBFF, 0xFFF1FAFF, 0xFF91B4C8}, // Arctic
            {0xF21C101B, 0xFF2C1927, 0xFF1B0E18, 0xFF3A2332, 0xFFE8B77E, 0xFFFFF2E8, 0xFFC09AAA}, // Rose Gold
            {0xF20B0D12, 0xFF171A22, 0xFF0D1017, 0xFF202630, 0xFFB7C4D8, 0xFFF4F7FC, 0xFF8793A5}, // Obsidian
            {0xF21B0B08, 0xFF2B120D, 0xFF190A08, 0xFF3A1910, 0xFFFF6038, 0xFFFFE9D9, 0xFFC58E7D}, // Lava
            {0xF20C1710, 0xFF17271B, 0xFF0B1710, 0xFF1D3322, 0xFFA3D977, 0xFFF0FFE8, 0xFF9DB68D}, // Forest
            {0xF21B1220, 0xFF291B32, 0xFF180F20, 0xFF382548, 0xFFB99BFF, 0xFFFFF1FF, 0xFFC0A9D0}  // Cotton Candy
    };

    // ---- Persistent UI state (survives reopening the screen) ----
    private static String currentTab = Modules.CAT_VISUAL;
    private static String searchText = "";
    private static String selectedModuleId;
    private static boolean gridView;

    // ---- Per-instance state ----
    private boolean searchFocused;
    private boolean halloweenPasswordPrompt;
    private String halloweenPassword = "";
    private boolean halloweenPasswordError;
    private boolean draggingScroll;
    private final long[] profileFlash = new long[3];
    private final Map<String, Integer> counts = new HashMap<>();

    // Palette (smoothly follows the selected theme).
    private final int[] pal = new int[7];
    private boolean palReady;
    private int cSide, cMain, cCard, cCardHover, cAccent, cText, cMuted, cBorder, cTrackOff;

    // Animation.
    private long lastNanos;
    private float dt = 0.016f;
    private float openT;
    private float tabT = 1f;
    private float alpha = 1f;
    private final Map<String, Float> anims = new HashMap<>();
    private final Map<String, String> fitCache = new HashMap<>();

    // Scrolling.
    private float navScroll, navScrollTarget, navMax;
    private float scroll, scrollTarget, scrollMax;
    private float pillY = -1f;
    private float savedListScroll;
    private int thumbH = 20;

    // Layout (recomputed every frame).
    private int px, py, pw, ph, sw;
    private int cx, cw, cy;
    private int vpT, vpB;
    private int mx, my;
    private int clipT, clipB;

    private record Hit(int x, int y, int w, int h, Runnable action) {}
    private final List<Hit> hits = new ArrayList<>();

    private static final int ROW_H = 38;
    private static final int ROW_GAP = 6;

    // ---- Tiny pixel icons ('#' = filled) ----
    private static final Map<String, String[]> ICONS = new HashMap<>();
    private static final String[] ICON_CLOSE = {"#.....#", ".#...#.", "..#.#..", "...#...", "..#.#..", ".#...#.", "#.....#"};
    private static final String[] ICON_CHEV_R = {"#...", ".#..", "..#.", "...#", "..#.", ".#..", "#..."};
    private static final String[] ICON_CHEV_L = {"...#", "..#.", ".#..", "#...", ".#..", "..#.", "...#"};
    private static final String[] ICON_SEARCH = {".###...", "#...#..", "#...#..", "#...#..", ".####..", ".....#.", "......#"};
    private static final String[] ICON_LIST = {"#######", ".......", "#######", ".......", "#######"};
    private static final String[] ICON_GRID = {"###.###", "###.###", ".......", "###.###", "###.###"};
    private static final String[] ICON_CHECK = {"......#", ".....#.", "#...#..", ".#.#...", "..#...."};

    static {
        ICONS.put(Modules.CAT_CRYSTAL, new String[]{"...##...", "..####..", ".######.", "########", ".######.", "..####..", "...##..."});
        ICONS.put(Modules.CAT_COMBAT, new String[]{"#......#", ".#....#.", "..#..#..", "...##...", "..#..#..", ".#....#.", "#......#"});
        ICONS.put(Modules.CAT_MACE, new String[]{".#####..", ".#####..", ".#####..", "..###...", "...#....", "...#....", "...#...."});
        ICONS.put(Modules.CAT_CART, new String[]{"........", "#......#", "########", "#......#", "#......#", "########", ".##..##."});
        ICONS.put(Modules.CAT_UTIL, new String[]{"......##", ".....###", "....##.#", "...##...", "..##....", ".##.....", "##......"});
        ICONS.put(Modules.CAT_UHC, new String[]{".##..##.", "########", "########", "########", ".######.", "..####..", "...##..."});
        ICONS.put(Modules.CAT_VISUAL, new String[]{"..####..", ".#....#.", "#..##..#", "#..##..#", ".#....#.", "..####.."});
        ICONS.put(Modules.CAT_DONUT, new String[]{"..####..", ".######.", "###..###", "###..###", ".######.", "..####.."});
        ICONS.put(Modules.CAT_AMBIENCE, new String[]{"...#...", "...#...", "..###..", "#######", "..###..", "...#...", "...#..."});
        ICONS.put(SETTINGS, new String[]{"#######", "..##...", "..##...", "#######", "....##.", "....##.", "#######"});
        ICONS.put(THEMES, new String[]{"...#...", "..###..", ".#####.", "#######", "#######", ".#####.", "..###.."});
        ICONS.put(PROFILES, new String[]{"..###..", ".#####.", "#######", ".......", "#######", ".......", "#######"});
    }

    public ConfigScreen() {
        super(Text.literal("XMCore"));
        for (String category : Modules.CATEGORIES) {
            int n = 0;
            for (Module m : Modules.ALL) if (m.category.equals(category)) n++;
            counts.put(category, n);
        }
    }

    @Override
    protected void init() {
        // Intentionally no vanilla widgets: the complete interface is custom drawn.
    }

    // =====================================================================
    //  Small helpers
    // =====================================================================

    private static float clamp01(float v) { return v < 0f ? 0f : Math.min(v, 1f); }

    private static float easeOut(float t) {
        float u = 1f - clamp01(t);
        return 1f - u * u * u;
    }

    private static int withA(int c, int a) { return (Math.max(0, Math.min(255, a)) << 24) | (c & 0xFFFFFF); }

    private static int mix(int a, int b, float t) {
        t = clamp01(t);
        int out = 0;
        for (int s = 0; s <= 24; s += 8) {
            int ca = (a >>> s) & 255, cb = (b >>> s) & 255;
            out |= (Math.round(ca + (cb - ca) * t) & 255) << s;
        }
        return out;
    }

    private static int stepColor(int cur, int tgt, float k) {
        if (cur == tgt) return cur;
        int out = 0;
        boolean close = true;
        for (int s = 0; s <= 24; s += 8) {
            int c = (cur >>> s) & 255, t = (tgt >>> s) & 255;
            int n = Math.round(c + (t - c) * k);
            if (n == c && c != t) n += Integer.signum(t - c);
            if (Math.abs(t - c) > 3) close = false;
            out |= (n & 255) << s;
        }
        return close ? tgt : out;
    }

    /** Multiplies a colour's alpha by the current fade. */
    private int fade(int c) {
        if (alpha >= 0.999f) return c;
        return withA(c, Math.round((c >>> 24) * alpha));
    }

    /** Eases a named value toward a target (frame-rate independent). */
    private float anim(String key, float target, float speed) {
        float v = anims.getOrDefault(key, target);
        v += (target - v) * (1f - (float) Math.exp(-dt * speed));
        if (Math.abs(target - v) < 0.004f) v = target;
        anims.put(key, v);
        return v;
    }

    private boolean hover(int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h && my >= clipT && my < clipB;
    }

    private static boolean inside(double x, double y, int rx, int ry, int rw, int rh) {
        return x >= rx && x < rx + rw && y >= ry && y < ry + rh;
    }

    private void addHit(int x, int y, int w, int h, Runnable action) {
        int y1 = Math.max(y, clipT), y2 = Math.min(y + h, clipB);
        if (y2 <= y1 || w <= 0) return;
        hits.add(new Hit(x, y1, w, y2 - y1, action));
    }

    private String fit(String s, int maxW) {
        if (maxW <= 4) return "";
        String key = maxW + "|" + s;
        String cached = fitCache.get(key);
        if (cached != null) return cached;
        String out = s;
        if (textRenderer.getWidth(s) > maxW) {
            int dots = textRenderer.getWidth("..");
            int end = s.length();
            while (end > 0 && textRenderer.getWidth(s.substring(0, end)) + dots > maxW) end--;
            out = s.substring(0, end).stripTrailing() + "..";
        }
        if (fitCache.size() > 600) fitCache.clear();
        fitCache.put(key, out);
        return out;
    }

    // ---- Drawing primitives (all respect the current fade) ----

    private void fillRect(DrawContext ctx, int x, int y, int w, int h, int color) {
        color = fade(color);
        if ((color >>> 24) == 0 || w <= 0 || h <= 0) return;
        ctx.fill(x, y, x + w, y + h, color);
    }

    private void rrect(DrawContext ctx, int x, int y, int w, int h, int r, int color) {
        color = fade(color);
        if ((color >>> 24) == 0 || w <= 0 || h <= 0) return;
        r = Math.min(r, Math.min(w, h) / 2);
        if (r <= 0) { ctx.fill(x, y, x + w, y + h, color); return; }
        ctx.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            ctx.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            ctx.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }

    private void rrectBorder(DrawContext ctx, int x, int y, int w, int h, int r, int fill, int border) {
        rrect(ctx, x, y, w, h, r, border);
        rrect(ctx, x + 1, y + 1, w - 2, h - 2, Math.max(0, r - 1), fill);
    }

    private void text(DrawContext ctx, String s, int x, int y, int color, boolean shadow) {
        color = fade(color);
        if ((color >>> 24) < 8 || s.isEmpty()) return;
        ctx.drawText(textRenderer, s, x, y, color, shadow);
    }

    private void textCentered(DrawContext ctx, String s, int centerX, int y, int color, boolean shadow) {
        text(ctx, s, centerX - textRenderer.getWidth(s) / 2, y, color, shadow);
    }

    private void icon(DrawContext ctx, String[] bmp, int x, int y, int color) {
        if (bmp == null) return;
        color = fade(color);
        if ((color >>> 24) < 8) return;
        for (int r = 0; r < bmp.length; r++) {
            String row = bmp[r];
            int c = 0;
            while (c < row.length()) {
                if (row.charAt(c) != '#') { c++; continue; }
                int start = c;
                while (c < row.length() && row.charAt(c) == '#') c++;
                ctx.fill(x + start, y + r, x + c, y + r + 1, color);
            }
        }
    }

    private void drawSwitch(DrawContext ctx, int x, int y, int w, int h, float t) {
        rrect(ctx, x, y, w, h, h / 2, mix(cTrackOff, cAccent, t));
        int k = h - 4;
        int kx = x + 2 + Math.round((w - h) * t);
        rrect(ctx, kx, y + 2, k, k, k / 2, mix(0xFFB4BBCF, 0xFFFFFFFF, t));
    }

    // =====================================================================
    //  State helpers
    // =====================================================================

    private void selectTab(String tab) {
        currentTab = tab;
        selectedModuleId = null;
        searchText = "";
        searchFocused = false;
        scroll = scrollTarget = 0f;
        tabT = 0f;
    }

    private void openModule(Module m) {
        savedListScroll = scrollTarget;
        currentTab = m.category;
        selectedModuleId = m.id;
        searchText = "";
        searchFocused = false;
        scroll = scrollTarget = 0f;
        tabT = 0f;
    }

    private void closeDetail() {
        selectedModuleId = null;
        scroll = scrollTarget = savedListScroll;
        tabT = 0f;
    }

    private boolean isCategory() { return Modules.CATEGORIES.contains(currentTab); }

    private List<Module> filteredModules() {
        String query = searchText.toLowerCase(Locale.ROOT).trim();
        List<Module> out = new ArrayList<>();
        for (Module m : Modules.ALL) {
            if (query.isEmpty()) {
                if (m.category.equals(currentTab)) out.add(m);
            } else if (m.name.toLowerCase(Locale.ROOT).contains(query)
                    || m.description.toLowerCase(Locale.ROOT).contains(query)) {
                out.add(m);
            }
        }
        return out;
    }

    private void updatePalette() {
        int[] target = THEME_COLORS[Math.max(0, Math.min(THEME_COLORS.length - 1, Config.get().themeId))];
        if (!palReady) { System.arraycopy(target, 0, pal, 0, 7); palReady = true; }
        float k = 1f - (float) Math.exp(-dt * 9f);
        for (int i = 0; i < 7; i++) pal[i] = stepColor(pal[i], target[i], k);
        cSide = pal[P_SIDEBAR] | 0xFF000000;
        cMain = pal[P_MAIN] | 0xFF000000;
        cCard = pal[P_CARD] | 0xFF000000;
        cAccent = pal[P_ACCENT] | 0xFF000000;
        cText = pal[P_TEXT] | 0xFF000000;
        cMuted = pal[P_MUTED] | 0xFF000000;
        cBorder = mix(cMain, cText, 0.10f);
        cCardHover = mix(cCard, cAccent, 0.13f);
        cTrackOff = mix(cCard, cMuted, 0.40f);
    }

    // =====================================================================
    //  Render
    // =====================================================================

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        dt = lastNanos == 0 ? 0.016f : Math.min(0.05f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;
        mx = mouseX;
        my = mouseY;
        openT = Math.min(1f, openT + dt / 0.24f);
        tabT = Math.min(1f, tabT + dt / 0.22f);
        updatePalette();
        hits.clear();

        float open = easeOut(openT);

        // Vanilla blurred world backdrop, then a theme tint on top.
        super.render(ctx, mouseX, mouseY, delta);
        alpha = open;
        clipT = 0;
        clipB = height;
        fillRect(ctx, 0, 0, width, height, withA(pal[P_OVERLAY], 0x66));

        pw = Math.min(820, width - 24);
        ph = Math.min(500, height - 24);
        sw = Math.max(110, Math.min(164, pw / 4));
        px = (width - pw) / 2;
        py = (height - ph) / 2 + Math.round((1f - open) * 12f);

        // Soft shadow + window.
        for (int i = 4; i >= 1; i--) rrect(ctx, px - i, py - i, pw + i * 2, ph + i * 2, 8 + i, 0x16000000);
        rrectBorder(ctx, px, py, pw, ph, 8, cMain, cBorder);

        drawSidebar(ctx);
        drawMain(ctx, open);

        // Christmas cosmetic: a gently bobbing Santa hat beside the XMCore brand.
        if (Config.get().themeId == 6) drawSantaHat(ctx, px + 32, py + 5, 1.35f);
        if (halloweenPasswordPrompt) drawHalloweenPasswordPrompt(ctx);

        // Close button.
        clipT = 0;
        clipB = height;
        alpha = open;
        int bx = px + pw - 26, by = py + 10;
        float ch = anim("close", hover(bx, by, 16, 16) ? 1f : 0f, 16f);
        if (ch > 0.03f) rrect(ctx, bx, by, 16, 16, 4, mix(cMain, 0xFFE5484D, 0.75f * ch));
        icon(ctx, ICON_CLOSE, bx + 4, by + 4, mix(cMuted, 0xFFFFFFFF, ch));
        addHit(bx, by, 16, 16, this::close);
    }

    private void drawSantaHat(DrawContext ctx, int x, int y, float scale) {
        int bob = Math.round((float)Math.sin(System.nanoTime() / 260_000_000.0) * 1.5f);
        int red = 0xFFE53945, white = 0xFFF7F4EF;
        int u = Math.max(1, Math.round(scale));
        rrect(ctx, x + 2*u, y + 7*u + bob, 13*u, 4*u, 2*u, white);
        rrect(ctx, x + 5*u, y + 4*u + bob, 8*u, 4*u, 2*u, red);
        rrect(ctx, x + 8*u, y + 1*u + bob, 6*u, 4*u, 2*u, red);
        rrect(ctx, x + 12*u, y - 1*u + bob, 4*u, 4*u, 2*u, red);
        rrect(ctx, x + 14*u, y - 2*u + bob, 4*u, 4*u, 2*u, white);
    }

    private void drawHalloweenPasswordPrompt(DrawContext ctx) {
        fillRect(ctx, 0, 0, width, height, 0x99000000);
        int w = Math.min(310, width - 32), h = 126;
        int x = (width - w) / 2, y = (height - h) / 2;
        rrectBorder(ctx, x, y, w, h, 8, cCard, cAccent);
        textCentered(ctx, "HALLOWEEN THEME LOCKED", width / 2, y + 15, cText, true);
        textCentered(ctx, "Enter the access password", width / 2, y + 36, cMuted, false);
        rrectBorder(ctx, x + 18, y + 57, w - 36, 25, 5, cMain, halloweenPasswordError ? 0xFFFF5555 : cBorder);
        String shown = "•".repeat(halloweenPassword.length());
        text(ctx, shown.isEmpty() ? "Type password..." : shown, x + 27, y + 66,
                halloweenPasswordError ? 0xFFFF6666 : cText, false);
        textCentered(ctx, halloweenPasswordError ? "Incorrect password" : "ENTER to unlock  •  ESC to cancel",
                width / 2, y + 96, halloweenPasswordError ? 0xFFFF6666 : cMuted, false);
    }

    private void selectTheme(int index) {
        if (index == 5) {
            halloweenPasswordPrompt = true;
            halloweenPassword = "";
            halloweenPasswordError = false;
            searchFocused = false;
            return;
        }
        Config.get().themeId = index;
        Config.save();
    }

    private void submitHalloweenPassword() {
        if ("h123h".equals(halloweenPassword)) {
            Config.get().themeId = 5;
            Config.save();
            halloweenPasswordPrompt = false;
        } else {
            halloweenPassword = "";
            halloweenPasswordError = true;
        }
    }

    // ---------------------------------------------------------------------
    //  Sidebar
    // ---------------------------------------------------------------------

    private void drawSidebar(DrawContext ctx) {
        alpha = easeOut(openT);
        rrect(ctx, px + 1, py + 1, sw - 1, ph - 2, 7, cSide);
        fillRect(ctx, px + sw - 8, py + 1, 8, ph - 2, cSide);
        fillRect(ctx, px + sw, py + 1, 1, ph - 2, cBorder);

        // Brand.
        rrect(ctx, px + 14, py + 12, 24, 24, 6, cAccent);
        rrect(ctx, px + 16, py + 14, 20, 20, 5, mix(cAccent, 0xFFFFFFFF, 0.16f));
        textCentered(ctx, "X", px + 26, py + 20, 0xFFFFFFFF, true);
        text(ctx, "XMCore", px + 44, py + 13, cText, true);
        text(ctx, fit("CLIENT FRAMEWORK", sw - 44 - 8), px + 44, py + 25, cMuted, false);
        fillRect(ctx, px + 12, py + 44, sw - 24, 1, cBorder);

        // Footer.
        int footerY = py + ph - 26;
        fillRect(ctx, px + 12, footerY, sw - 24, 1, cBorder);
        text(ctx, "MalekXMine", px + 14, footerY + 10, cMuted, false);
        String ver = "v1.0.0";
        text(ctx, ver, px + sw - 12 - textRenderer.getWidth(ver), footerY + 10, mix(cMuted, cMain, 0.35f), false);

        // Navigation (scrolls if the window is small).
        int navT = py + 48, navB = footerY - 1;
        int viewH = navB - navT;
        int pitch = Math.max(18, Math.min(24, (viewH - 52) / 12));
        int itemH = pitch - 2;

        int yy = 6;
        int labelModules = yy;
        yy += 16;
        int[] catY = new int[Modules.CATEGORIES.size()];
        for (int i = 0; i < catY.length; i++) { catY[i] = yy; yy += pitch; }
        yy += 8;
        int labelGeneral = yy;
        yy += 16;
        int[] genY = new int[GENERAL.length];
        for (int i = 0; i < genY.length; i++) { genY[i] = yy; yy += pitch; }
        yy += 6;

        navMax = Math.max(0, yy - viewH);
        navScrollTarget = Math.max(0f, Math.min(navMax, navScrollTarget));
        navScroll += (navScrollTarget - navScroll) * (1f - (float) Math.exp(-dt * 18f));
        if (Math.abs(navScrollTarget - navScroll) < 0.2f) navScroll = navScrollTarget;
        int scrollPx = Math.round(navScroll);

        // Where is the active highlight?
        int activeY = -1;
        for (int i = 0; i < catY.length; i++) if (Modules.CATEGORIES.get(i).equals(currentTab)) activeY = catY[i];
        for (int i = 0; i < genY.length; i++) if (GENERAL[i].equals(currentTab)) activeY = genY[i];
        if (activeY >= 0) {
            if (pillY < 0f) pillY = activeY;
            pillY += (activeY - pillY) * (1f - (float) Math.exp(-dt * 16f));
            if (Math.abs(activeY - pillY) < 0.3f) pillY = activeY;
        }

        clipT = navT;
        clipB = navB;
        ctx.enableScissor(px + 1, navT, px + sw, navB);

        text(ctx, "MODULES", px + 16, navT + labelModules - scrollPx, cMuted, false);
        text(ctx, "GENERAL", px + 16, navT + labelGeneral - scrollPx, cMuted, false);

        if (activeY >= 0) {
            int pyy = navT + Math.round(pillY) - scrollPx;
            rrect(ctx, px + 8, pyy, sw - 17, itemH, 5, mix(cSide, cAccent, 0.20f));
            rrect(ctx, px + 8, pyy + 5, 2, itemH - 10, 1, cAccent);
        }

        for (int i = 0; i < catY.length; i++) {
            String name = Modules.CATEGORIES.get(i);
            navItem(ctx, name, counts.getOrDefault(name, 0), name.equals(currentTab), navT + catY[i] - scrollPx, itemH);
        }
        for (int i = 0; i < genY.length; i++) {
            navItem(ctx, GENERAL[i], 0, GENERAL[i].equals(currentTab), navT + genY[i] - scrollPx, itemH);
        }
        ctx.disableScissor();

        // Soft scroll hints when content continues above/below.
        if (navMax > 0) {
            if (navScroll > 1f) fillRect(ctx, px + 1, navT, sw - 1, 6, withA(cSide, 0xB0));
            if (navScroll < navMax - 1f) fillRect(ctx, px + 1, navB - 6, sw - 1, 6, withA(cSide, 0xB0));
        }
    }

    private void navItem(DrawContext ctx, String name, int count, boolean active, int y0, int itemH) {
        int x0 = px + 8, w0 = sw - 17;
        if (y0 + itemH < clipT || y0 > clipB) return;
        float h = anim("nav:" + name, hover(x0, y0, w0, itemH) ? 1f : 0f, 16f);
        if (!active && h > 0.03f) rrect(ctx, x0, y0, w0, itemH, 5, mix(cSide, cCard, 0.9f * h));
        int ic = active ? cAccent : mix(cMuted, cText, h);
        icon(ctx, ICONS.get(name), px + 17, y0 + (itemH - 7) / 2, ic);
        int right = count > 0 ? 24 : 8;
        text(ctx, fit(name, w0 - 23 - right), px + 31, y0 + (itemH - 8) / 2, active ? cText : mix(cMuted, cText, h * 0.9f), false);
        if (count > 0) {
            String s = Integer.toString(count);
            text(ctx, s, x0 + w0 - 8 - textRenderer.getWidth(s), y0 + (itemH - 8) / 2, active ? mix(cMuted, cText, 0.4f) : cMuted, false);
        }
        addHit(x0, y0, w0, itemH, () -> selectTab(name));
    }

    // ---------------------------------------------------------------------
    //  Main area
    // ---------------------------------------------------------------------

    private void heading(DrawContext ctx, String italic, String rest, int x, int y, int maxW) {
        Text t1 = Text.literal(italic).formatted(Formatting.ITALIC);
        int w = textRenderer.getWidth(t1);
        int col = fade(mix(cAccent, cText, 0.18f));
        if ((col >>> 24) >= 8) ctx.drawText(textRenderer, t1, x, y, col, true);
        if (w + textRenderer.getWidth(rest) <= maxW) text(ctx, rest, x + w, y, cText, true);
    }

    private void drawMain(DrawContext ctx, float open) {
        int mainX = px + sw + 1;
        int mainR = px + pw - 1;
        cx = mainX + 20;
        cw = mainR - mainX - 20 - 18;
        vpT = py + 62;
        vpB = py + ph - 8;
        boolean modulesView = isCategory() && selectedModuleId == null;
        boolean searching = !searchText.trim().isEmpty();

        // ---- Header ----
        alpha = open;
        clipT = 0;
        clipB = height;
        String italic, rest, sub;
        Module detail = selectedModuleId != null ? Modules.byId(selectedModuleId) : null;
        if (detail != null) {
            italic = detail.name; rest = ""; sub = detail.category;
        } else if (isCategory()) {
            List<Module> list = filteredModules();
            long on = list.stream().filter(Module::isEnabled).count();
            italic = searching ? "Search" : currentTab;
            rest = searching ? " Results" : " Modules";
            sub = list.size() + " modules  ·  " + on + " enabled";
        } else if (currentTab.equals(SETTINGS)) {
            italic = "Client"; rest = " Settings"; sub = "Fine-tune how XMCore looks and feels";
        } else if (currentTab.equals(THEMES)) {
            italic = "Theme"; rest = " Studio"; sub = "Make the interface feel like yours";
        } else {
            italic = "HUD"; rest = " Profiles"; sub = "Switch between ready-made setups";
        }

        int headRight = px + pw - 36;
        int searchW = Math.max(110, Math.min(190, cw / 3));
        int titleMax = modulesView ? headRight - searchW - 16 - cx : headRight - cx;
        heading(ctx, italic, rest, cx, py + 16, titleMax);
        text(ctx, fit(sub, titleMax), cx, py + 30, cMuted, false);

        if (modulesView) {
            drawSearchBox(ctx, headRight - searchW, py + 12, searchW, 20);
            // View toggles.
            int tx = headRight - 38, ty = py + 38;
            viewToggle(ctx, "view-list", tx, ty, ICON_LIST, !gridView, () -> { gridView = false; scrollTarget = 0; });
            viewToggle(ctx, "view-grid", tx + 20, ty, ICON_GRID, gridView, () -> { gridView = true; scrollTarget = 0; });
        }
        fillRect(ctx, mainX, py + 60, mainR - mainX, 1, cBorder);

        // ---- Scrolling content ----
        scrollTarget = Math.max(0f, Math.min(scrollMax, scrollTarget));
        scroll += (scrollTarget - scroll) * (1f - (float) Math.exp(-dt * 16f));
        if (Math.abs(scrollTarget - scroll) < 0.2f) scroll = scrollTarget;

        float tabE = easeOut(tabT);
        int slide = Math.round((1f - tabE) * 10f);
        alpha = open * tabE;
        clipT = vpT;
        clipB = vpB;
        int top0 = vpT + 8 - Math.round(scroll);
        cy = top0 + slide;

        ctx.enableScissor(mainX, vpT, mainR, vpB);
        if (isCategory()) {
            if (detail != null) drawDetail(ctx, detail);
            else {
                if (selectedModuleId != null) selectedModuleId = null;
                drawModuleList(ctx, searching);
            }
        } else if (currentTab.equals(SETTINGS)) drawSettings(ctx);
        else if (currentTab.equals(THEMES)) drawThemes(ctx);
        else drawProfiles(ctx);
        ctx.disableScissor();

        int contentH = (cy - slide) - top0;
        scrollMax = Math.max(0, contentH + 16 - (vpB - vpT));

        // ---- Scrollbar ----
        alpha = open;
        if (scrollMax > 0.5f) {
            int vpH = vpB - vpT;
            thumbH = Math.max(22, Math.round(vpH * (vpH / (vpH + scrollMax))));
            int ty = vpT + Math.round((vpH - thumbH) * (scroll / scrollMax));
            boolean over = draggingScroll || inside(mx, my, px + pw - 14, vpT, 12, vpH);
            float sh = anim("scrollbar", over ? 1f : 0f, 14f);
            fillRect(ctx, px + pw - 10, vpT, 3, vpH, mix(cMain, cCard, 0.6f));
            rrect(ctx, px + pw - 10, ty, 3, thumbH, 1, mix(mix(cMuted, cMain, 0.45f), cAccent, sh));
        }
    }

    private void viewToggle(DrawContext ctx, String key, int x, int y, String[] bmp, boolean active, Runnable action) {
        float h = anim(key, hover(x, y, 18, 16) ? 1f : 0f, 16f);
        int bg = active ? mix(cCard, cAccent, 0.28f) : mix(cMain, cCard, 0.5f + 0.5f * h);
        rrectBorder(ctx, x, y, 18, 16, 4, bg, active ? mix(cBorder, cAccent, 0.6f) : cBorder);
        icon(ctx, bmp, x + 5, y + (16 - bmp.length) / 2, active ? cText : mix(cMuted, cText, h));
        addHit(x, y, 18, 16, action);
    }

    private void drawSearchBox(DrawContext ctx, int x, int y, int w, int h) {
        boolean hov = hover(x, y, w, h);
        float f = anim("search-focus", searchFocused ? 1f : 0f, 14f);
        float hv = anim("search-hover", hov ? 1f : 0f, 14f);
        rrectBorder(ctx, x, y, w, h, 5, mix(cCard, cCardHover, Math.max(f * 0.6f, hv * 0.4f)),
                mix(cBorder, cAccent, Math.max(f, hv * 0.4f)));
        icon(ctx, ICON_SEARCH, x + 7, y + 6, mix(cMuted, cAccent, f));
        int tx = x + 20, avail = w - 28;
        if (searchText.isEmpty()) {
            text(ctx, "Search modules", tx, y + 6, cMuted, false);
        } else {
            String shown = searchText;
            while (shown.length() > 1 && textRenderer.getWidth(shown) > avail) shown = shown.substring(1);
            text(ctx, shown, tx, y + 6, cText, false);
            tx += textRenderer.getWidth(shown);
        }
        if (searchFocused && (System.currentTimeMillis() / 500L) % 2L == 0L) fillRect(ctx, tx + 1, y + 5, 1, 10, cAccent);
        addHit(x, y, w, h, () -> searchFocused = true);
    }

    // ---------------------------------------------------------------------
    //  Modules list / grid
    // ---------------------------------------------------------------------

    private void drawModuleList(DrawContext ctx, boolean searching) {
        List<Module> mods = filteredModules();
        int cols = gridView ? (cw >= 430 ? 2 : 1) : 1;
        int gap = gridView ? 8 : ROW_GAP;
        int itemH = gridView ? 56 : 42;
        int colW = (cw - gap * (cols - 1)) / cols;

        for (int i = 0; i < mods.size(); i++) {
            int x0 = cx + (i % cols) * (colW + gap);
            int y0 = cy + (i / cols) * (itemH + gap);
            if (y0 + itemH < vpT || y0 > vpB) continue;
            drawModuleItem(ctx, mods.get(i), x0, y0, colW, itemH, searching);
        }
        int rows = (mods.size() + cols - 1) / cols;
        cy += rows * (itemH + gap);

        if (mods.isEmpty()) {
            textCentered(ctx, "No modules match your search", cx + cw / 2, cy + 24, cMuted, false);
            textCentered(ctx, "Try a different word", cx + cw / 2, cy + 38, mix(cMuted, cMain, 0.4f), false);
            cy += 60;
        }
    }

    private void drawModuleItem(DrawContext ctx, Module m, int x0, int y0, int w, int h, boolean searching) {
        boolean hov = hover(x0, y0, w, h);
        float hv = anim("row:" + m.id, hov ? 1f : 0f, 16f);
        float on = anim("tog:" + m.id, m.isEnabled() ? 1f : 0f, 14f);

        rrectBorder(ctx, x0, y0, w, h, 5, mix(cCard, cCardHover, hv), mix(cBorder, cAccent, Math.max(hv * 0.45f, on * 0.18f)));
        if (on > 0.02f) rrect(ctx, x0 + 1, y0 + 10, 2, h - 20, 1, withA(cAccent, Math.round(255 * on)));

        int swW = 28, swH = 14;
        int swX = x0 + w - 14 - swW, swY = y0 + (h - swH) / 2;
        int textMax = w - 14 - swW - 14 - (gridView ? 8 : 38);

        int nameY = y0 + (gridView ? 11 : 10);
        text(ctx, fit(m.name, textMax), x0 + 14, nameY, cText, false);
        if (searching) {
            int nw = textRenderer.getWidth(fit(m.name, textMax));
            text(ctx, fit(m.category.toUpperCase(Locale.ROOT), Math.max(0, textMax - nw - 10)), x0 + 14 + nw + 8, nameY, mix(cMuted, cAccent, 0.5f), false);
        }
        text(ctx, fit(m.description, textMax), x0 + 14, nameY + 13, cMuted, false);

        if (!gridView) {
            icon(ctx, ICON_CHEV_R, swX - 22, y0 + (h - 7) / 2, mix(mix(cMuted, cMain, 0.3f), cText, hv));
        }
        drawSwitch(ctx, swX, swY, swW, swH, on);

        addHit(x0, y0, w, h, () -> openModule(m));
        addHit(swX - 6, y0 + 4, swW + 12, h - 8, m::toggle);
    }

    // ---------------------------------------------------------------------
    //  Module detail
    // ---------------------------------------------------------------------

    private void drawDetail(DrawContext ctx, Module m) {
        // Back button.
        int bw = 64, bh = 20;
        float bh0 = anim("back", hover(cx, cy, bw, bh) ? 1f : 0f, 16f);
        rrectBorder(ctx, cx, cy, bw, bh, 5, mix(cCard, cCardHover, bh0), mix(cBorder, cAccent, bh0 * 0.5f));
        icon(ctx, ICON_CHEV_L, cx + 10, cy + 6, mix(cMuted, cText, bh0));
        text(ctx, "Back", cx + 22, cy + 6, mix(cMuted, cText, 0.4f + 0.6f * bh0), false);
        addHit(cx, cy, bw, bh, this::closeDetail);
        cy += bh + 12;

        // Hero card.
        float on = anim("tog:" + m.id, m.isEnabled() ? 1f : 0f, 14f);
        int hh = 70;
        rrectBorder(ctx, cx, cy, cw, hh, 6, cCard, mix(cBorder, cAccent, on * 0.35f));
        rrect(ctx, cx + 1, cy + 12, 2, hh - 24, 1, withA(cAccent, Math.round(255 * on)));
        int swW = 38, swH = 20;
        int swX = cx + cw - 18 - swW, swY = cy + (hh - swH) / 2;
        int tMax = cw - 16 - swW - 36;
        text(ctx, fit(m.name, tMax), cx + 16, cy + 14, cText, true);
        text(ctx, fit(m.description, tMax), cx + 16, cy + 29, cMuted, false);
        text(ctx, m.category.toUpperCase(Locale.ROOT), cx + 16, cy + 49, mix(cMuted, cAccent, 0.55f), false);
        drawSwitch(ctx, swX, swY, swW, swH, on);
        addHit(swX - 8, swY - 8, swW + 16, swH + 16, m::toggle);
        cy += hh + 18;

        section(ctx, "MODULE SETTINGS", "Controls for this feature");
        rowSwitch(ctx, "m-state", "Module state", "Turn this feature on or off", m.isEnabled(), m::toggle);
        rowChip(ctx, "m-hud", "HUD position", "Move this module's widget in HUD Studio", "Open HUD Studio", this::openStudio);
        text(ctx, fit("More module-specific options will appear here as they are added.", cw), cx + 2, cy + 6, mix(cMuted, cMain, 0.25f), false);
        cy += 24;
    }

    private void openStudio() {
        Config.save();
        if (client != null) client.setScreen(new HudEditorScreen());
    }

    // ---------------------------------------------------------------------
    //  Settings rows
    // ---------------------------------------------------------------------

    private void section(DrawContext ctx, String title, String sub) {
        text(ctx, title, cx, cy, cAccent, false);
        int w = textRenderer.getWidth(title);
        text(ctx, fit(sub, cw - w - 24), cx + w + 10, cy, cMuted, false);
        fillRect(ctx, cx, cy + 13, cw, 1, cBorder);
        cy += 22;
    }

    private void rowShell(DrawContext ctx, String key, String name, String hint, boolean clickable) {
        boolean hov = clickable && hover(cx, cy, cw, ROW_H);
        float h = anim("srow:" + key, hov ? 1f : 0f, 16f);
        rrectBorder(ctx, cx, cy, cw, ROW_H, 5, mix(cCard, cCardHover, h), mix(cBorder, cAccent, h * 0.45f));
        text(ctx, fit(name, cw - 170), cx + 14, cy + 8, cText, false);
        text(ctx, fit(hint, cw - 170), cx + 14, cy + 21, cMuted, false);
    }

    private void rowSwitch(DrawContext ctx, String key, String name, String hint, boolean value, Runnable toggle) {
        rowShell(ctx, key, name, hint, true);
        float t = anim("sw:" + key, value ? 1f : 0f, 14f);
        drawSwitch(ctx, cx + cw - 14 - 28, cy + (ROW_H - 14) / 2, 28, 14, t);
        addHit(cx, cy, cw, ROW_H, () -> { toggle.run(); Config.save(); });
        cy += ROW_H + ROW_GAP;
    }

    private void rowChip(DrawContext ctx, String key, String name, String hint, String value, Runnable action) {
        rowShell(ctx, key, name, hint, true);
        int w = Math.max(56, textRenderer.getWidth(value) + 22);
        int x = cx + cw - 12 - w, y = cy + (ROW_H - 20) / 2;
        float h = anim("chip:" + key, hover(x, y, w, 20) ? 1f : 0f, 16f);
        rrect(ctx, x, y, w, 20, 5, mix(mix(cCard, cAccent, 0.22f), cAccent, h * 0.30f));
        textCentered(ctx, value, x + w / 2, y + 6, mix(mix(cAccent, cText, 0.35f), cText, h * 0.5f), false);
        addHit(cx, cy, cw, ROW_H, () -> { action.run(); Config.save(); });
        cy += ROW_H + ROW_GAP;
    }

    private void rowStepper(DrawContext ctx, String key, String name, String hint, String value, Runnable minus, Runnable plus) {
        rowShell(ctx, key, name, hint, false);
        int by = cy + (ROW_H - 18) / 2;
        int plusX = cx + cw - 12 - 18, valX = plusX - 56, minusX = valX - 18;
        stepButton(ctx, key + "-", minusX, by, false);
        stepButton(ctx, key + "+", plusX, by, true);
        textCentered(ctx, value, valX + 28, by + 5, mix(cAccent, cText, 0.25f), false);
        addHit(minusX, by, 18, 18, () -> { minus.run(); Config.save(); });
        addHit(plusX, by, 18, 18, () -> { plus.run(); Config.save(); });
        cy += ROW_H + ROW_GAP;
    }

    private void stepButton(DrawContext ctx, String key, int x, int y, boolean plus) {
        float h = anim("step:" + key, hover(x, y, 18, 18) ? 1f : 0f, 18f);
        rrectBorder(ctx, x, y, 18, 18, 5, mix(mix(cCard, cMain, 0.3f), cCardHover, h), mix(cBorder, cAccent, h * 0.6f));
        int c = mix(cMuted, cAccent, h);
        fillRect(ctx, x + 5, y + 8, 8, 2, c);
        if (plus) fillRect(ctx, x + 8, y + 5, 2, 8, c);
    }

    private void drawSettings(DrawContext ctx) {
        Config c = Config.get();
        String[] shapes = {"Cross", "Dot", "Circle", "Cross + Dot"};

        section(ctx, "INTERFACE", "Choose your module layout");
        rowChip(ctx, "gui-style", "GUI style", "Switch between XMCore and classic array",
                c.guiStyle == 1 ? "Classic Array" : "XMCore",
                () -> {
                    c.guiStyle = c.guiStyle == 1 ? 0 : 1;
                    if (client != null) client.setScreen(c.guiStyle == 1 ? new ArrayGuiScreen() : this);
                });

        section(ctx, "CROSSHAIR STUDIO", "Shape, size and behaviour");
        rowChip(ctx, "shape", "Crosshair shape", "Click to change",
                shapes[Math.max(0, Math.min(3, c.crosshairShape))], () -> c.crosshairShape = (c.crosshairShape + 1) % 4);
        rowStepper(ctx, "size", "Crosshair size", "Arm length in pixels", c.crosshairSize + " px",
                () -> c.crosshairSize = Math.max(1, c.crosshairSize - 1), () -> c.crosshairSize = Math.min(24, c.crosshairSize + 1));
        rowStepper(ctx, "gap", "Crosshair gap", "Space around the centre", c.crosshairGap + " px",
                () -> c.crosshairGap = Math.max(0, c.crosshairGap - 1), () -> c.crosshairGap = Math.min(12, c.crosshairGap + 1));
        rowStepper(ctx, "thick", "Crosshair thickness", "Line width in pixels", c.crosshairThickness + " px",
                () -> c.crosshairThickness = Math.max(1, c.crosshairThickness - 1), () -> c.crosshairThickness = Math.min(5, c.crosshairThickness + 1));

        cy += 8;
        section(ctx, "ANIMATION", "Local first-person swing animation");
        rowStepper(ctx, "swing", "Swing speed", "Lower is faster", c.swingSpeedTicks + " ticks",
                () -> c.swingSpeedTicks = Math.max(1, c.swingSpeedTicks - 1), () -> c.swingSpeedTicks = Math.min(20, c.swingSpeedTicks + 1));
        String preset = c.swingSpeedTicks == 20 ? "Slow" : c.swingSpeedTicks == 3 ? "Prestige" : c.swingSpeedTicks == 6 ? "Vanilla" : "Custom";
        rowChip(ctx, "preset", "Swing preset", "Click to cycle", preset,
                () -> c.swingSpeedTicks = c.swingSpeedTicks == 20 ? 6 : c.swingSpeedTicks == 6 ? 3 : 20);

        cy += 8;
        section(ctx, "HUD CUSTOMISATION", "Position and colour");
        rowSwitch(ctx, "dyn", "Dynamic crosshair", "Expands while moving", c.crosshairDynamic, () -> c.crosshairDynamic = !c.crosshairDynamic);
        rowSwitch(ctx, "outline", "Crosshair outline", "Dark edge for contrast", c.crosshairOutline, () -> c.crosshairOutline = !c.crosshairOutline);
        rowChip(ctx, "studio", "HUD Studio", "Drag individual widgets", "Open", this::openStudio);

        cy += 8;
        section(ctx, "DONUTSMP LIVE TRACKER", "Automatic tracking and HUD display");
        rowSwitch(ctx, "d-auto", "Auto track payments", "Detect incoming payments", c.donutLiveAutoTrack, () -> c.donutLiveAutoTrack = !c.donutLiveAutoTrack);
        rowSwitch(ctx, "d-total", "Show total earned", "Running total on the HUD", c.donutLiveShowTotal, () -> c.donutLiveShowTotal = !c.donutLiveShowTotal);
        rowSwitch(ctx, "d-last", "Show last payment", "Most recent payment amount", c.donutLiveShowLastPayment, () -> c.donutLiveShowLastPayment = !c.donutLiveShowLastPayment);
        rowSwitch(ctx, "d-payer", "Show payer name", "Who sent the last payment", c.donutLiveShowPayer, () -> c.donutLiveShowPayer = !c.donutLiveShowPayer);
        rowSwitch(ctx, "d-count", "Show payment count", "Number of payments received", c.donutLiveShowPaymentCount, () -> c.donutLiveShowPaymentCount = !c.donutLiveShowPaymentCount);
    }

    // ---------------------------------------------------------------------
    //  Themes
    // ---------------------------------------------------------------------

    private void drawThemes(DrawContext ctx) {
        int cols = cw >= 560 ? 3 : 2;
        int gap = 10, cardH = 98;
        int cardW = (cw - gap * (cols - 1)) / cols;
        int current = Config.get().themeId;

        for (int i = 0; i < THEME_NAMES.length; i++) {
            int x0 = cx + (i % cols) * (cardW + gap);
            int y0 = cy + (i / cols) * (cardH + gap);
            if (y0 + cardH < vpT || y0 > vpB) continue;
            final int index = i;
            boolean active = current == i;
            float h = anim("theme:" + i, hover(x0, y0, cardW, cardH) ? 1f : 0f, 16f);
            float a = anim("theme-active:" + i, active ? 1f : 0f, 12f);
            int[] t = THEME_COLORS[i];

            rrectBorder(ctx, x0, y0, cardW, cardH, 6, mix(cCard, cCardHover, h), mix(mix(cBorder, cAccent, h * 0.5f), cAccent, a));

            // Miniature window preview in that theme's colours.
            int pvx = x0 + 8, pvy = y0 + 8, pvw = cardW - 16, pvh = 52;
            int sbw = Math.max(16, pvw * 28 / 100);
            rrect(ctx, pvx, pvy, pvw, pvh, 4, t[P_MAIN] | 0xFF000000);
            rrect(ctx, pvx + 2, pvy + 2, sbw, pvh - 4, 3, t[P_SIDEBAR] | 0xFF000000);
            for (int k = 0; k < 3; k++) {
                int col = k == 0 ? t[P_ACCENT] : mix(t[P_SIDEBAR] | 0xFF000000, t[P_MUTED] | 0xFF000000, 0.5f);
                rrect(ctx, pvx + 7, pvy + 9 + k * 9, sbw - 10, 4, 2, col | 0xFF000000);
            }
            for (int k = 0; k < 2; k++) {
                int ry = pvy + 6 + k * 22;
                rrect(ctx, pvx + sbw + 6, ry, pvw - sbw - 10, 18, 3, t[P_CARD] | 0xFF000000);
                rrect(ctx, pvx + pvw - 22, ry + 6, 12, 6, 3, t[P_ACCENT] | 0xFF000000);
                rrect(ctx, pvx + sbw + 11, ry + 5, Math.max(8, (pvw - sbw) / 4), 3, 1, t[P_TEXT] | 0xFF000000);
                rrect(ctx, pvx + sbw + 11, ry + 11, Math.max(6, (pvw - sbw) / 6), 2, 1, t[P_MUTED] | 0xFF000000);
            }

            text(ctx, THEME_NAMES[i], x0 + 12, y0 + 72, cText, false);
            String label = active ? "ACTIVE" : "SELECT";
            int lw = textRenderer.getWidth(label);
            int lc = active ? cAccent : mix(cMuted, cText, h);
            text(ctx, label, x0 + cardW - 12 - lw, y0 + 72, lc, false);
            if (active) icon(ctx, ICON_CHECK, x0 + cardW - 12 - lw - 11, y0 + 73, cAccent);

            addHit(x0, y0, cardW, cardH, () -> selectTheme(index));
        }
        int rows = (THEME_NAMES.length + cols - 1) / cols;
        cy += rows * (cardH + gap);
    }

    // ---------------------------------------------------------------------
    //  Profiles
    // ---------------------------------------------------------------------

    private void drawProfiles(DrawContext ctx) {
        String[] names = {"PvP", "Clean", "Builder"};
        String[] keys = {"pvp", "clean", "builder"};
        String[] desc = {"Combat HUD, crosshair, keystrokes and performance",
                "Minimal FPS, coordinates and keystrokes",
                "Coordinates, world time, held item and FPS"};
        long now = System.currentTimeMillis();
        for (int i = 0; i < names.length; i++) {
            final int index = i;
            int h0 = 62;
            boolean hov = hover(cx, cy, cw, h0);
            float h = anim("prof:" + i, hov ? 1f : 0f, 16f);
            rrectBorder(ctx, cx, cy, cw, h0, 6, mix(cCard, cCardHover, h), mix(cBorder, cAccent, h * 0.5f));
            rrect(ctx, cx + 1, cy + 12, 2, h0 - 24, 1, cAccent);
            boolean flashed = now < profileFlash[i];
            String label = flashed ? "Applied" : "Apply";
            int w = Math.max(64, textRenderer.getWidth(label) + 26);
            int bx = cx + cw - 14 - w, by = cy + (h0 - 22) / 2;
            text(ctx, names[i], cx + 18, cy + 15, cText, true);
            text(ctx, fit(desc[i], bx - cx - 36), cx + 18, cy + 33, cMuted, false);
            float bh = anim("prof-btn:" + i, hover(bx, by, w, 22) ? 1f : 0f, 16f);
            rrect(ctx, bx, by, w, 22, 5, flashed ? mix(cAccent, cMain, 0.15f) : mix(mix(cCard, cAccent, 0.25f), cAccent, bh * 0.35f));
            textCentered(ctx, label, bx + w / 2, by + 7, flashed ? 0xFFFFFFFF : mix(mix(cAccent, cText, 0.35f), cText, bh * 0.5f), false);
            addHit(cx, cy, cw, h0, () -> { applyProfile(keys[index]); profileFlash[index] = System.currentTimeMillis() + 1400L; });
            cy += h0 + 10;
        }
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

    // =====================================================================
    //  Input
    // =====================================================================

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        double x = click.x(), y = click.y();

        // Scrollbar drag.
        if (scrollMax > 0.5f && inside(x, y, px + pw - 14, vpT, 12, vpB - vpT)) {
            draggingScroll = true;
            setScrollFromMouse(y);
            return true;
        }

        searchFocused = false;
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (inside(x, y, hit.x(), hit.y(), hit.w(), hit.h())) {
                hit.action().run();
                return true;
            }
        }
        return true;
    }

    private void setScrollFromMouse(double mouseY) {
        int vpH = vpB - vpT;
        float range = Math.max(1f, vpH - thumbH);
        float f = (float) ((mouseY - vpT - thumbH / 2.0) / range);
        scrollTarget = Math.max(0f, Math.min(scrollMax, f * scrollMax));
        scroll = scrollTarget;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (draggingScroll) {
            setScrollFromMouse(click.y());
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (draggingScroll) {
            draggingScroll = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (inside(mouseX, mouseY, px, py + 48, sw, ph - 75)) {
            navScrollTarget = Math.max(0f, Math.min(navMax, navScrollTarget - (float) verticalAmount * 28f));
            return true;
        }
        if (inside(mouseX, mouseY, px + sw, py + 58, pw - sw, ph - 58)) {
            scrollTarget = Math.max(0f, Math.min(scrollMax, scrollTarget - (float) verticalAmount * 34f));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean charTyped(CharInput input) {
        int codePoint = input.codepoint();
        if (halloweenPasswordPrompt && !Character.isISOControl(codePoint) && halloweenPassword.length() < 24) {
            halloweenPassword += new String(Character.toChars(codePoint));
            halloweenPasswordError = false;
            return true;
        }
        if (searchFocused && isCategory() && selectedModuleId == null && !Character.isISOControl(codePoint)) {
            if (searchText.length() < 40) searchText += new String(Character.toChars(codePoint));
            scroll = scrollTarget = 0f;
            return true;
        }
        return super.charTyped(input);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (halloweenPasswordPrompt) {
            int key = input.key();
            if (key == GLFW.GLFW_KEY_ESCAPE) {
                halloweenPasswordPrompt = false;
                halloweenPassword = "";
                return true;
            }
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!halloweenPassword.isEmpty()) halloweenPassword = halloweenPassword.substring(0, halloweenPassword.length() - 1);
                halloweenPasswordError = false;
                return true;
            }
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                submitHalloweenPassword();
                return true;
            }
            return true;
        }
        if (input.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            Config.get().guiStyle = 1;
            Config.save();
            if (client != null) client.setScreen(new ArrayGuiScreen());
            return true;
        }
        if (searchFocused && isCategory()) {
            int key = input.key();
            if (key == GLFW.GLFW_KEY_BACKSPACE) {
                if (!searchText.isEmpty()) {
                    searchText = searchText.substring(0, searchText.length() - 1);
                    scroll = scrollTarget = 0f;
                }
                return true;
            }
            if (key == GLFW.GLFW_KEY_ESCAPE || key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                searchFocused = false;
                return true;
            }
        }
        return super.keyPressed(input);
    }

    @Override
    public void close() {
        Config.save();
        super.close();
    }

    @Override
    public boolean shouldPause() { return false; }
}
