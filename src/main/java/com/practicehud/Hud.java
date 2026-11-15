package com.practicehud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;

/** Shared renderer for compact, modern HUD cards. */
public final class Hud {
    public static final int WHITE = 0xFFF4F6FF;
    public static final int GRAY = 0xFF9AA3B8;
    public static final int GREEN = 0xFF72E0B0;
    public static final int YELLOW = 0xFFFFC66D;
    public static final int RED = 0xFFFF718A;
    public static final int AQUA = 0xFF73D9F5;
    public static final int GOLD = 0xFFFFC66D;

    public record Row(String text, int color, float frac) {
        public static Row of(String text, int color) { return new Row(text, color, -1f); }
        public static Row bar(String text, float frac, int color) {
            return new Row(text, color, Math.max(0f, Math.min(1f, frac)));
        }
    }

    private static int cursorY = 4;
    private Hud() {}
    public static void begin() { cursorY = 4; }

    public static void panel(DrawContext ctx, String title, int accent, Row... rows) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int w = Math.max(132, tr.getWidth(title) + 34);
        int h = 32;
        for (Row r : rows) {
            w = Math.max(w, tr.getWidth(r.text()) + 28);
            h += r.frac() >= 0 ? 21 : 17;
        }
        w = Math.min(w, Math.max(132, ctx.getScaledWindowWidth() - 16));

        Config c = Config.get();
        int[] custom = c.hudPositions.get(title);
        int x = custom == null ? c.hudX : custom[0];
        int y = custom == null ? c.hudY + cursorY - 4 : custom[1];
        x = Math.max(0, Math.min(ctx.getScaledWindowWidth() - w, x));
        y = Math.max(0, Math.min(ctx.getScaledWindowHeight() - h, y));

        // Layered shadow and glass-like dark surface.
        ctx.fill(x + 3, y + 4, x + w + 3, y + h + 4, 0x42000000);
        ctx.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0x3010182B);
        ctx.fill(x, y, x + w, y + h, 0xF20C111F);
        ctx.fill(x, y + 8, x + 2, y + h - 8, accent);
        ctx.fill(x + 10, y + 9, x + 15, y + 14, accent);
        ctx.drawTextWithShadow(tr, title, x + 22, y + 7, WHITE);
        ctx.fill(x + 10, y + 25, x + w - 10, y + 26, 0x66414A66);

        int ry = y + 32;
        for (int i = 0; i < rows.length; i++) {
            Row r = rows[i];
            ctx.drawTextWithShadow(tr, r.text(), x + 12, ry, r.color());
            if (r.frac() >= 0) {
                int bw = w - 24;
                ctx.fill(x + 12, ry + 13, x + 12 + bw, ry + 16, 0xFF293247);
                int fill = Math.round(bw * r.frac());
                if (fill > 0) ctx.fill(x + 12, ry + 13, x + 12 + fill, ry + 16, r.color());
                ry += 21;
            } else {
                ry += 17;
            }
            if (i < rows.length - 1) ctx.fill(x + 12, ry - 3, x + w - 12, ry - 2, 0x222F3A55);
        }
        if (custom == null) cursorY += h + 8;
    }

    public static void centered(DrawContext ctx, String text, int cx, int y, int color) {
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        ctx.drawTextWithShadow(tr, text, cx - tr.getWidth(text) / 2, y, color);
    }

    public static String time(int ticks) {
        int s = ticks / 20;
        return String.format("%d:%02d", s / 60, s % 60);
    }
}
