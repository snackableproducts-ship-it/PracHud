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
        int w = Math.max(112, tr.getWidth(title) + 22);
        int h = 23;
        for (Row r : rows) {
            w = Math.max(w, tr.getWidth(r.text()) + 18);
            h += r.frac() >= 0 ? 17 : 12;
        }
        w = Math.min(w, Math.max(112, ctx.getScaledWindowWidth() - 12));

        Config c = Config.get();
        int[] custom = c.hudPositions.get(title);
        int x = custom == null ? c.hudX : custom[0];
        int y = custom == null ? c.hudY + cursorY - 4 : custom[1];
        x = Math.max(0, Math.min(ctx.getScaledWindowWidth() - w, x));
        y = Math.max(0, Math.min(ctx.getScaledWindowHeight() - h, y));

        // Layered card, slim accent rail, header divider.
        ctx.fill(x + 2, y + 2, x + w + 2, y + h + 2, 0x50000000);
        ctx.fill(x, y, x + w, y + h, 0xE9101422);
        ctx.fill(x, y, x + 2, y + h, accent);
        ctx.fill(x + 8, y + 8, x + 12, y + 12, accent);
        ctx.drawTextWithShadow(tr, title, x + 17, y + 5, WHITE);
        ctx.fill(x + 8, y + 19, x + w - 8, y + 20, 0x553D4964);

        int ry = y + 25;
        for (Row r : rows) {
            ctx.drawTextWithShadow(tr, r.text(), x + 9, ry, r.color());
            if (r.frac() >= 0) {
                int bw = w - 18;
                ctx.fill(x + 9, ry + 11, x + 9 + bw, ry + 14, 0xFF30384A);
                int fill = Math.round(bw * r.frac());
                if (fill > 0) ctx.fill(x + 9, ry + 11, x + 9 + fill, ry + 14, r.color());
                ry += 17;
            } else {
                ry += 12;
            }
        }
        if (custom == null) cursorY += h + 5;
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
