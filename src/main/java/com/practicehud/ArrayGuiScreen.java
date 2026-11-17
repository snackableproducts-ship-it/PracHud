package com.practicehud;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Compact, multi-column module list inspired by classic Minecraft array GUIs. */
public final class ArrayGuiScreen extends Screen {
    private static final int PANEL = 0xD9101118;
    private static final int HEADER = 0xF01B202B;
    private static final int TEXT = 0xFFE9EDF5;
    private static final int MUTED = 0xFF858D9D;
    private static final int ACCENT = 0xFF55D6FF;
    private static final int ENABLED = 0xFF55B9FF;
    private static final int DISABLED = 0xFF505765;

    private record Group(String title, List<String> ids) {}
    private record Hit(int x, int y, int w, int h, Runnable action) {}

    private static final List<Group> GROUPS = List.of(
        new Group("HUD", List.of("reticle", "fps_counter", "coordinates", "armor_status",
            "potion_hud", "keystrokes", "held_item", "world_time", "fps_graph",
            "session_stats", "storage_display", "animation_settings", "crosshair_settings")),
        new Group("SCREEN FX", List.of("no_fire_overlay", "no_nausea_overlay",
            "no_portal_overlay", "no_vignette")),
        new Group("AMBIENCE", List.of("ambient_particles", "ambient_audio")),
        new Group("DONUTSMP", List.of("donut_balance", "donut_earnings",
            "donut_top_earners", "donut_goal_alert", "donut_auction", "donut_stream_info"))
    );

    private final List<Hit> hits = new ArrayList<>();
    private int scroll;

    public ArrayGuiScreen() {
        super(Text.literal("XMCore Array GUI"));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        hits.clear();

        int margin = 20, gap = 10, panelW = Math.min(210,
            Math.max(150, (width - margin * 2 - gap * (GROUPS.size() - 1)) / GROUPS.size()));
        int totalW = panelW * GROUPS.size() + gap * (GROUPS.size() - 1);
        int startX = Math.max(margin, (width - totalW) / 2);
        int top = 24, headerH = 27, rowH = 23;

        ctx.fill(0, 0, width, height, 0x33000000);
        for (int col = 0; col < GROUPS.size(); col++) {
            Group group = GROUPS.get(col);
            int x = startX + col * (panelW + gap);
            int y = top - scroll;
            int panelH = headerH + group.ids().size() * rowH + 5;

            ctx.fill(x, y, x + panelW, y + panelH, PANEL);
            ctx.fill(x, y, x + panelW, y + headerH, HEADER);
            ctx.fill(x, y, x + 3, y + headerH, ACCENT);
            ctx.drawText(textRenderer, group.title(), x + 10, y + 9, ACCENT, false);
            int enabledCount = 0;
            for (String id : group.ids()) {
                Module module = Modules.byId(id);
                if (module != null && module.isEnabled()) enabledCount++;
            }
            String count = enabledCount + "/" + group.ids().size();
            ctx.drawText(textRenderer, count, x + panelW - textRenderer.getWidth(count) - 9,
                y + 9, MUTED, false);

            for (int i = 0; i < group.ids().size(); i++) {
                String id = group.ids().get(i);
                Module module = Modules.byId(id);
                if (module == null) continue;
                int ry = y + headerH + i * rowH;
                if (ry + rowH < 0 || ry > height - 30) continue;
                boolean on = module.isEnabled();
                boolean hovered = mouseX >= x + 3 && mouseX < x + panelW - 3
                    && mouseY >= ry && mouseY < ry + rowH;
                if (hovered) ctx.fill(x + 3, ry, x + panelW - 3, ry + rowH, 0x55434D60);
                String label = module.name.toUpperCase();
                ctx.drawText(textRenderer, label, x + 9, ry + 8, on ? TEXT : MUTED, false);

                int sx = x + panelW - 31, sy = ry + 6;
                ctx.fill(sx, sy, sx + 22, sy + 11, on ? ENABLED : DISABLED);
                int knob = on ? sx + 13 : sx + 2;
                ctx.fill(knob, sy + 2, knob + 7, sy + 9, 0xFFF4F6FA);

                hits.add(new Hit(x + 3, ry, panelW - 6, rowH, () -> module.toggle()));
            }
        }

        String footer = "XMCORE ARRAY  •  CLICK HERE TO RETURN TO XMCORE GUI";
        int fx = (width - textRenderer.getWidth(footer)) / 2;
        int fy = height - 18;
        ctx.drawText(textRenderer, footer, fx, fy, MUTED, false);
        hits.add(new Hit(fx - 8, fy - 5, textRenderer.getWidth(footer) + 16, 18, () -> {
            Config.get().guiStyle = 0;
            Config.save();
            if (client != null) client.setScreen(new ConfigScreen());
        }));
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
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int rows = 0;
        for (Group group : GROUPS) rows = Math.max(rows, group.ids().size());
        int contentHeight = 27 + rows * 23 + 5;
        int maxScroll = Math.max(0, contentHeight - height + 55);
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.round(verticalAmount * 28)));
        return true;
    }
}
