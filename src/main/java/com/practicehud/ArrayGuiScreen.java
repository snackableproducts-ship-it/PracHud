package com.practicehud;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Compact classic-style array GUI. Uses short, independently scrollable-looking
 * category columns and the existing module registry/config rather than duplicate state.
 */
public final class ArrayGuiScreen extends Screen {
    private static final int PANEL = 0xD9101118;
    private static final int HEADER = 0xEE171B24;
    private static final int BORDER = 0x553A4658;
    private static final int TEXT = 0xFFE7EAF0;
    private static final int MUTED = 0xFF8992A1;
    private static final int ACCENT = 0xFF6AD7E8;
    private static final int ON = 0xFF67B8E8;
    private static final int OFF = 0xFF454B56;
    private static final int ROW_H = 18;
    private static final int HEADER_H = 23;
    private static final int TOP = 15;
    private static final int GAP = 7;

    private record Group(String title, String icon, int tint, List<String> ids) {}
    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private static final List<Group> GROUPS = List.of(
        new Group("HUD", "▰", 0xFF63C6E8, List.of(
            "fps_counter", "coordinates", "armor_status", "potion_hud", "keystrokes",
            "held_item", "world_time", "fps_graph", "session_stats", "storage_display"
        )),
        new Group("SCREEN FX", "◈", 0xFFB48BEE, List.of(
            "no_fire_overlay", "no_nausea_overlay", "no_portal_overlay", "no_vignette"
        )),
        new Group("RENDER", "◉", 0xFF8ACB83, List.of(
            "reticle", "animation_settings", "crosshair_settings"
        )),
        new Group("AMBIENCE", "✧", 0xFFE5C27A, List.of(
            "ambient_particles", "ambient_audio"
        )),
        new Group("DONUTSMP", "●", 0xFFE7A56D, List.of(
            "donut_balance", "donut_earnings", "donut_top_earners",
            "donut_goal_alert", "donut_auction", "donut_stream_info"
        )),
        new Group("CLIENT", "✦", 0xFF7EA9F5, List.of())
    );

    private final List<Hit> hits = new ArrayList<>();
    private int scroll;

    public ArrayGuiScreen() {
        super(Text.literal("XMCore Array"));
    }

    private int columnsForWidth() {
        if (width >= 1320) return 6;
        if (width >= 990) return 4;
        if (width >= 620) return 3;
        return 2;
    }

    private String fit(String value, int maxWidth) {
        if (textRenderer.getWidth(value) <= maxWidth) return value;
        String result = value;
        while (!result.isEmpty() && textRenderer.getWidth(result + "…") > maxWidth) {
            result = result.substring(0, result.length() - 1);
        }
        return result.isEmpty() ? "" : result + "…";
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        hits.clear();

        int margin = 12;
        int columns = columnsForWidth();
        int panelW = Math.max(126, Math.min(204,
            (width - margin * 2 - GAP * (columns - 1)) / columns));
        int totalW = columns * panelW + (columns - 1) * GAP;
        int startX = Math.max(8, (width - totalW) / 2);
        int bottom = height - 22;
        int maxRows = 0;
        for (Group group : GROUPS) maxRows = Math.max(maxRows, group.ids().size());
        int contentHeight = HEADER_H + maxRows * ROW_H + 8;
        int maxScroll = Math.max(0, contentHeight - (bottom - TOP));
        scroll = Math.min(scroll, maxScroll);

        // Slight background dim only; the world remains visible behind the panels.
        ctx.fill(0, 0, width, height, 0x26000000);

        for (int i = 0; i < GROUPS.size(); i++) {
            Group group = GROUPS.get(i);
            int col = i % columns;
            int row = i / columns;
            int x = startX + col * (panelW + GAP);
            int y = TOP + row * (contentHeight + GAP) - scroll;
            int rowCount = group.ids().size() + (group.title().equals("CLIENT") ? 1 : 0);
            int panelH = HEADER_H + rowCount * ROW_H + 4;

            if (y + panelH < TOP || y > bottom) continue;
            ctx.fill(x, y, x + panelW, y + panelH, PANEL);
            ctx.fill(x, y, x + panelW, y + HEADER_H, HEADER);
            ctx.fill(x, y, x + 2, y + HEADER_H, group.tint());
            ctx.fill(x, y, x + panelW, y + 1, BORDER);
            ctx.drawText(textRenderer, group.icon(), x + 7, y + 7, group.tint(), false);
            ctx.drawText(textRenderer, group.title(), x + 19, y + 7, TEXT, false);

            int active = 0;
            for (String id : group.ids()) {
                Module m = Modules.byId(id);
                if (m != null && m.isEnabled()) active++;
            }
            String count = active + "/" + group.ids().size();
            if (!group.ids().isEmpty()) {
                ctx.drawText(textRenderer, count,
                    x + panelW - textRenderer.getWidth(count) - 7, y + 7, MUTED, false);
            }

            for (int n = 0; n < rowCount; n++) {
                int ry = y + HEADER_H + n * ROW_H;
                if (ry + ROW_H < TOP || ry > bottom) continue;
                String label;
                boolean enabled = false;
                Runnable action;

                if (group.title().equals("CLIENT")) {
                    label = "GUI STYLE";
                    enabled = true;
                    action = () -> {
                        Config.get().guiStyle = 0;
                        Config.save();
                        if (client != null) client.setScreen(new ConfigScreen());
                    };
                } else {
                    String id = group.ids().get(n);
                    Module module = Modules.byId(id);
                    if (module == null) continue;
                    label = module.name.toUpperCase();
                    enabled = module.isEnabled();
                    Module target = module;
                    action = () -> {
                        target.toggle();
                        Config.save();
                    };
                }

                boolean hover = mouseX >= x + 2 && mouseX < x + panelW - 2
                    && mouseY >= ry && mouseY < ry + ROW_H;
                if (hover) ctx.fill(x + 2, ry, x + panelW - 2, ry + ROW_H, 0x443F4B5D);
                ctx.drawText(textRenderer, fit(label, panelW - 45), x + 7, ry + 6,
                    enabled ? TEXT : MUTED, false);

                int sx = x + panelW - 25;
                int sy = ry + 4;
                ctx.fill(sx, sy, sx + 17, sy + 10, enabled ? ON : OFF);
                int knobX = enabled ? sx + 9 : sx + 1;
                ctx.fill(knobX, sy + 1, knobX + 7, sy + 9, 0xFFF4F6F8);
                hits.add(new Hit(x + 2, ry, panelW - 4, ROW_H, action));
            }
        }

        String footer = "XMCORE  /  RIGHT SHIFT TO CLOSE";
        ctx.drawText(textRenderer, footer, 8, height - 12, 0xAAE3E8F0, false);
        String style = "CLASSIC ARRAY";
        ctx.drawText(textRenderer, style, width - textRenderer.getWidth(style) - 8,
            height - 12, ACCENT, false);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (click.x() >= hit.x() && click.x() < hit.x() + hit.w()
                && click.y() >= hit.y() && click.y() < hit.y() + hit.h()) {
                hit.action().run();
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        int columns = columnsForWidth();
        int rows = (GROUPS.size() + columns - 1) / columns;
        int maxRows = 0;
        for (Group group : GROUPS) maxRows = Math.max(maxRows, group.ids().size());
        int contentHeight = HEADER_H + maxRows * ROW_H + 8;
        int maxScroll = Math.max(0, rows * (contentHeight + GAP) - (height - TOP - 22));
        scroll = Math.max(0, Math.min(maxScroll,
            scroll - (int) Math.round(verticalAmount * 24)));
        return true;
    }
}
