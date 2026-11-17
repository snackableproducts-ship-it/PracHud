package com.practicehud;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/** Array-style menu populated from the live module registry. */
public final class ArrayGuiScreen extends Screen {
    private static final int PANEL = 0xD9101118;
    private static final int HEADER = 0xEE171B24;
    private static final int BORDER = 0x553A4658;
    private static final int TEXT = 0xFFE7EAF0;
    private static final int MUTED = 0xFF8992A1;
    private static final int ON = 0xFF67B8E8;
    private static final int OFF = 0xFF454B56;
    private static final int ROW_H = 18, HEADER_H = 23, TOP = 15, GAP = 7;

    // Mirrors the complete live module registry; no separate visibility list.
    private record Group(String title, int tint, List<Module> modules) {}
    private record Hit(int x, int y, int w, int h, Runnable action) {}
    private record HeaderHit(String category, int x, int y, int w, int h) {}
    private final List<Hit> hits = new ArrayList<>();
    private final List<HeaderHit> headers = new ArrayList<>();
    private int scroll;
    private String draggingCategory;
    private int dragMouseX, dragMouseY;

    public ArrayGuiScreen() { super(Text.literal("XMCore Array")); }

    private int columnsForWidth() {
        if (width >= 1500) return 6;
        if (width >= 1180) return 5;
        if (width >= 900) return 4;
        if (width >= 620) return 3;
        return 2;
    }

    private List<Group> groups() {
        List<Group> result = new ArrayList<>();
        int[] accents = {0xFF63C6E8, 0xFFB48BEE, 0xFF8ACB83, 0xFFE5C27A, 0xFFE7A56D,
                         0xFF7EA9F5, 0xFF69C7AE, 0xFFD78EAA, 0xFF9AA8C7};
        int tint = 0;
        for (String category : Modules.CATEGORIES) {
            List<Module> entries = new ArrayList<>();
            for (Module module : Modules.ALL) {
                if (module.category.equals(category)) entries.add(module);
            }
            if (!entries.isEmpty()) result.add(new Group(category.toUpperCase(), accents[tint++ % accents.length], entries));
        }
        return result;
    }

    private String fit(String value, int maxWidth) {
        if (textRenderer.getWidth(value) <= maxWidth) return value;
        String result = value;
        while (!result.isEmpty() && textRenderer.getWidth(result + "…") > maxWidth)
            result = result.substring(0, result.length() - 1);
        return result.isEmpty() ? "" : result + "…";
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        hits.clear();
        headers.clear();
        List<Group> groups = groups();
        int columns = columnsForWidth();
        int margin = 12;
        int panelW = Math.max(126, Math.min(210,
            (width - margin * 2 - GAP * (columns - 1)) / columns));
        int totalW = panelW * columns + GAP * (columns - 1);
        int startX = Math.max(8, (width - totalW) / 2);
        int bottom = height - 22;

        int rowCount = (groups.size() + columns - 1) / columns;
        int[] rowHeights = new int[rowCount];
        for (int i = 0; i < groups.size(); i++) {
            int panelH = HEADER_H + groups.get(i).modules().size() * ROW_H + 4;
            rowHeights[i / columns] = Math.max(rowHeights[i / columns], panelH);
        }
        int[] rowY = new int[rowCount];
        int contentHeight = 0;
        for (int r = 0; r < rowCount; r++) {
            rowY[r] = TOP + contentHeight;
            contentHeight += rowHeights[r] + GAP;
        }
        int maxScroll = Math.max(0, contentHeight - (bottom - TOP));
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        ctx.fill(0, 0, width, height, 0x26000000);
        for (int i = 0; i < groups.size(); i++) {
            Group group = groups.get(i);
            int col = i % columns, row = i / columns;
            int[] offset = Config.get().arrayGuiOffsets.getOrDefault(group.title(), new int[]{0, 0});
            int x = startX + col * (panelW + GAP) + offset[0];
            int y = rowY[row] - scroll + offset[1];
            int panelH = HEADER_H + group.modules().size() * ROW_H + 4;
            if (y + panelH < TOP || y > bottom) continue;

            ctx.fill(x, y, x + panelW, y + panelH, PANEL);
            ctx.fill(x, y, x + panelW, y + HEADER_H, HEADER);
            ctx.fill(x, y, x + 2, y + HEADER_H, group.tint());
            ctx.fill(x, y, x + panelW, y + 1, BORDER);
            ctx.drawText(textRenderer, group.title(), x + 8, y + 7, group.tint(), false);
            headers.add(new HeaderHit(group.title(), x, y, panelW, HEADER_H));

            int active = 0;
            for (Module module : group.modules()) if (module.isEnabled()) active++;
            String count = active + "/" + group.modules().size();
            ctx.drawText(textRenderer, count, x + panelW - textRenderer.getWidth(count) - 7,
                y + 7, MUTED, false);

            for (int n = 0; n < group.modules().size(); n++) {
                Module module = group.modules().get(n);
                int ry = y + HEADER_H + n * ROW_H;
                if (ry + ROW_H < TOP || ry > bottom) continue;
                boolean enabled = module.isEnabled();
                boolean hover = mouseX >= x + 2 && mouseX < x + panelW - 2
                    && mouseY >= ry && mouseY < ry + ROW_H;
                if (hover) ctx.fill(x + 2, ry, x + panelW - 2, ry + ROW_H, 0x443F4B5D);
                ctx.drawText(textRenderer, fit(module.name.toUpperCase(), panelW - 43),
                    x + 7, ry + 6, enabled ? TEXT : MUTED, false);
                int sx = x + panelW - 24, sy = ry + 4;
                ctx.fill(sx, sy, sx + 17, sy + 10, enabled ? ON : OFF);
                int knobX = enabled ? sx + 9 : sx + 1;
                ctx.fill(knobX, sy + 1, knobX + 7, sy + 9, 0xFFF4F6F8);
                hits.add(new Hit(x + 2, ry, panelW - 4, ROW_H, () -> {
                    module.toggle();
                    Config.save();
                }));
            }
        }

        if (Config.get().themeId == 6) {
            int bob = Math.round((float)Math.sin(System.nanoTime() / 260_000_000.0) * 1.5f);
            int hx = width / 2 - 7, hy = 3 + bob;
            ctx.fill(hx + 2, hy + 10, hx + 20, hy + 15, 0xFFF7F4EF);
            ctx.fill(hx + 5, hy + 6, hx + 14, hy + 11, 0xFFE53945);
            ctx.fill(hx + 9, hy + 3, hx + 17, hy + 7, 0xFFE53945);
            ctx.fill(hx + 14, hy + 1, hx + 20, hy + 5, 0xFFE53945);
            ctx.fill(hx + 18, hy, hx + 22, hy + 4, 0xFFF7F4EF);
        }

        String footer = "XMCORE  /  RIGHT SHIFT TO CLOSE";
        ctx.drawText(textRenderer, footer, 8, height - 12, 0xAAE3E8F0, false);
        String label = "CLASSIC ARRAY  •  " + Modules.ALL.size() + " REGISTERED";
        ctx.drawText(textRenderer, label, width - textRenderer.getWidth(label) - 8,
            height - 12, 0xFF6AD7E8, false);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (input.key() == GLFW.GLFW_KEY_RIGHT_SHIFT) {
            Config.get().guiStyle = 0;
            Config.save();
            if (client != null) client.setScreen(new ConfigScreen());
            return true;
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (draggingCategory != null && click.button() == 0) {
            int dx = (int)Math.round(click.x()) - dragMouseX;
            int dy = (int)Math.round(click.y()) - dragMouseY;
            int[] old = Config.get().arrayGuiOffsets.getOrDefault(draggingCategory, new int[]{0, 0});
            Config.get().arrayGuiOffsets.put(draggingCategory, new int[]{old[0] + dx, old[1] + dy});
            dragMouseX = (int)Math.round(click.x());
            dragMouseY = (int)Math.round(click.y());
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (draggingCategory != null) {
            draggingCategory = null;
            Config.save();
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        for (int i = headers.size() - 1; i >= 0; i--) {
            HeaderHit header = headers.get(i);
            if (click.x() >= header.x() && click.x() < header.x() + header.w()
                && click.y() >= header.y() && click.y() < header.y() + header.h()) {
                draggingCategory = header.category();
                dragMouseX = (int)Math.round(click.x());
                dragMouseY = (int)Math.round(click.y());
                return true;
            }
        }
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
        List<Group> groups = groups();
        int columns = columnsForWidth();
        int rows = (groups.size() + columns - 1) / columns;
        int[] rowHeights = new int[rows];
        for (int i = 0; i < groups.size(); i++) {
            int panelHeight = HEADER_H + groups.get(i).modules().size() * ROW_H + 4;
            rowHeights[i / columns] = Math.max(rowHeights[i / columns], panelHeight);
        }
        int contentHeight = 0;
        for (int rowHeight : rowHeights) contentHeight += rowHeight + GAP;
        int maxScroll = Math.max(0, contentHeight - (height - TOP - 22));
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int)Math.round(verticalAmount * 24)));
        return true;
    }
}
