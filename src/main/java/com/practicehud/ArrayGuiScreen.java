package com.practicehud;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Compact column ClickGUI (category panels side by side, collapsible, with a Search panel and
 * hover tooltips). Populated from the live module registry so new modules appear automatically.
 */
public final class ArrayGuiScreen extends Screen {
    private static final int PANEL = 0xB6101319;
    private static final int HEADER = 0xDD07090D;
    private static final int BORDER = 0x66364152;
    private static final int TEXT_ON = 0xFFFFFFFF;
    private static final int TEXT_OFF = 0xFFA3ABB9;
    private static final int OFF_TRACK = 0xFF2A303B;
    private static final int LOCK = 0xFFE59B4A;
    private static final float TEXT_SCALE = 0.8f;
    private static final int ROW_H = 11, HEADER_H = 15, TOP = 10, GAP = 5, PANEL_W = 108, FIELD_H = 14;
    private static final String SEARCH = "SEARCH";

    private record Group(String title, int tint, List<Module> modules) {}
    private record Hit(int x, int y, int w, int h, Runnable action) {}
    private record HeaderHit(String category, int x, int y, int w, int h) {}

    private static final Set<String> COLLAPSED = new HashSet<>();

    private final List<Hit> hits = new ArrayList<>();
    private final List<HeaderHit> headers = new ArrayList<>();
    private TextFieldWidget searchField;
    private int scroll;
    private String draggingCategory;
    private int dragMouseX, dragMouseY;
    private Module hovered;
    private int fieldX, fieldY, fieldW;

    public ArrayGuiScreen() { super(Text.literal("XMCore Array")); }

    @Override
    protected void init() {
        searchField = new TextFieldWidget(textRenderer, 0, 0, PANEL_W - 8, 12, Text.literal("Search"));
        searchField.setPlaceholder(Text.literal("Search..."));
        searchField.setMaxLength(32);
        addSelectableChild(searchField);
    }

    // ---------------------------------------------------------------- layout helpers

    private int columnsForWidth() {
        return Math.max(1, (width - 16 + GAP) / (PANEL_W + GAP));
    }

    private List<Module> searchResults() {
        String q = searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        List<Module> out = new ArrayList<>();
        if (q.isEmpty()) return out;
        for (Module m : Modules.ALL) {
            if (m.name.toLowerCase(Locale.ROOT).contains(q) || m.description.toLowerCase(Locale.ROOT).contains(q)
                    || m.category.toLowerCase(Locale.ROOT).contains(q)) {
                out.add(m);
                if (out.size() >= 16) break;
            }
        }
        return out;
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
            if (!entries.isEmpty()) result.add(new Group(category.toUpperCase(Locale.ROOT), accents[tint++ % accents.length], entries));
        }
        result.add(new Group(SEARCH, 0xFFE8EAF0, searchResults()));
        return result;
    }

    private int panelHeight(Group g) {
        if (COLLAPSED.contains(g.title())) return HEADER_H;
        if (g.title().equals(SEARCH)) {
            int rows = Math.max(1, g.modules().size());
            return HEADER_H + FIELD_H + 4 + rows * ROW_H + 3;
        }
        return HEADER_H + g.modules().size() * ROW_H + 3;
    }

    private int[] rowHeights(List<Group> groups, int columns) {
        int rows = (groups.size() + columns - 1) / columns;
        int[] heights = new int[rows];
        for (int i = 0; i < groups.size(); i++)
            heights[i / columns] = Math.max(heights[i / columns], panelHeight(groups.get(i)));
        return heights;
    }

    // ---------------------------------------------------------------- text helpers

    private int sw(String s) { return Math.round(textRenderer.getWidth(s) * TEXT_SCALE); }

    private void small(DrawContext ctx, String text, int x, int y, int color) {
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate((float) x, (float) y);
        m.scale(TEXT_SCALE, TEXT_SCALE);
        ctx.drawText(textRenderer, text, 0, 0, color, false);
        m.popMatrix();
    }

    private String fit(String value, int maxWidth) {
        if (sw(value) <= maxWidth) return value;
        String result = value;
        while (!result.isEmpty() && sw(result + "..") > maxWidth) result = result.substring(0, result.length() - 1);
        return result + "..";
    }

    private List<String> wrap(String text, int maxWidth) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String test = cur.length() == 0 ? word : cur + " " + word;
            if (sw(test) > maxWidth && cur.length() > 0) {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else cur = new StringBuilder(test);
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    // ---------------------------------------------------------------- rendering

    private void drawRow(DrawContext ctx, Module module, int x, int ry, int panelW, int mouseX, int mouseY, int accent) {
        boolean raw = module.isEnabledRaw();
        boolean locked = module.isLocked();
        boolean on = raw && !locked;
        boolean hover = mouseX >= x + 1 && mouseX < x + panelW - 1 && mouseY >= ry && mouseY < ry + ROW_H;
        if (hover) {
            ctx.fill(x + 1, ry, x + panelW - 1, ry + ROW_H, 0x30FFFFFF);
            hovered = module;
        }
        int textMax = panelW - 34;
        small(ctx, fit(module.name.toUpperCase(Locale.ROOT), textMax), x + 6, ry + 2, on ? TEXT_ON : TEXT_OFF);
        // Toggle pill.
        int px = x + panelW - 19, py = ry + 3;
        int track = locked ? (raw ? 0xFF8A5A24 : OFF_TRACK) : on ? accent : OFF_TRACK;
        ctx.fill(px + 1, py, px + 12, py + 5, track);
        ctx.fill(px, py + 1, px + 13, py + 4, track);
        int kx = raw ? px + 7 : px + 1;
        ctx.fill(kx, py, kx + 5, py + 5, 0xFFF4F6F8);
        if (locked) {
            String l = "L";
            small(ctx, l, px - 7, ry + 2, LOCK);
        }
        hits.add(new Hit(x + 1, ry, panelW - 2, ROW_H, () -> { module.toggle(); Config.save(); }));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        super.render(ctx, mouseX, mouseY, delta);
        hits.clear();
        headers.clear();
        hovered = null;
        List<Group> groups = groups();
        int columns = Math.min(columnsForWidth(), groups.size());
        int panelW = PANEL_W;
        int totalW = panelW * columns + GAP * (columns - 1);
        int startX = Math.max(8, (width - totalW) / 2);
        int bottom = height - 16;
        int accent = Config.get().accentColor | 0xFF000000;

        int[] rowHeights = rowHeights(groups, columns);
        int[] rowY = new int[rowHeights.length];
        int contentHeight = 0;
        for (int r = 0; r < rowHeights.length; r++) {
            rowY[r] = TOP + contentHeight;
            contentHeight += rowHeights[r] + GAP;
        }
        int maxScroll = Math.max(0, contentHeight - (bottom - TOP));
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        ctx.fill(0, 0, width, height, 0x30000000);
        boolean fieldDrawn = false;
        for (int i = 0; i < groups.size(); i++) {
            Group group = groups.get(i);
            int col = i % columns, row = i / columns;
            int[] offset = Config.get().arrayGuiOffsets.getOrDefault(group.title(), new int[]{0, 0});
            int x = startX + col * (panelW + GAP) + offset[0];
            int y = rowY[row] - scroll + offset[1];
            int panelH = panelHeight(group);
            boolean collapsed = COLLAPSED.contains(group.title());
            boolean isSearch = group.title().equals(SEARCH);

            if (y + panelH >= 0 && y <= height) {
                ctx.fill(x - 1, y - 1, x + panelW + 1, y + panelH + 1, BORDER);
                ctx.fill(x, y, x + panelW, y + panelH, PANEL);
                ctx.fill(x, y, x + panelW, y + HEADER_H, HEADER);
                // Category icon: tinted square with a darker core.
                ctx.fill(x + 5, y + 4, x + 12, y + 11, group.tint());
                ctx.fill(x + 7, y + 6, x + 10, y + 9, 0xFF0B0D12);
                String title = group.title();
                small(ctx, title, x + 16, y + 4, TEXT_ON);
                small(ctx, title, x + 17, y + 4, TEXT_ON);
                String toggle = collapsed ? "+" : "-";
                small(ctx, toggle, x + panelW - 10, y + 4, 0xFFC8CEDA);
                headers.add(new HeaderHit(group.title(), x, y, panelW, HEADER_H));

                if (!collapsed) {
                    int ry = y + HEADER_H;
                    if (isSearch) {
                        fieldX = x + 4; fieldY = ry + 2; fieldW = panelW - 8;
                        searchField.setX(fieldX);
                        searchField.setY(fieldY);
                        searchField.setWidth(fieldW);
                        ry += FIELD_H + 4;
                        fieldDrawn = true;
                        if (group.modules().isEmpty()) {
                            small(ctx, searchField.getText().isBlank() ? "TYPE TO SEARCH" : "NO RESULTS", x + 6, ry + 2, TEXT_OFF);
                        }
                    }
                    for (Module module : group.modules()) {
                        if (ry + ROW_H >= 0 && ry <= height) drawRow(ctx, module, x, ry, panelW, mouseX, mouseY, accent);
                        ry += ROW_H;
                    }
                }
            }
        }
        if (fieldDrawn) searchField.render(ctx, mouseX, mouseY, delta);

        if (Config.get().themeId == 6) {
            int bob = Math.round((float)Math.sin(System.nanoTime() / 260_000_000.0) * 1.5f);
            int hx = width / 2 - 7, hy = 1 + bob;
            ctx.fill(hx + 2, hy + 10, hx + 20, hy + 15, 0xFFF7F4EF);
            ctx.fill(hx + 5, hy + 6, hx + 14, hy + 11, 0xFFE53945);
            ctx.fill(hx + 9, hy + 3, hx + 17, hy + 7, 0xFFE53945);
            ctx.fill(hx + 14, hy + 1, hx + 20, hy + 5, 0xFFE53945);
            ctx.fill(hx + 18, hy, hx + 22, hy + 4, 0xFFF7F4EF);
        }

        small(ctx, "XMCORE  /  RIGHT SHIFT SWITCHES GUI  /  DRAG HEADERS  /  - COLLAPSES", 8, height - 11, 0xAAE3E8F0);
        String label = Modules.ALL.size() + " MODULES";
        small(ctx, label, width - sw(label) - 8, height - 11, accent);

        if (hovered != null) drawTooltip(ctx, hovered, mouseX, mouseY);
    }

    private void drawTooltip(DrawContext ctx, Module m, int mouseX, int mouseY) {
        String desc = m.description.toUpperCase(Locale.ROOT);
        if (m.isLocked()) desc += "  [HELD OFF BY SAFE MODE]";
        List<String> lines = wrap(desc, 190);
        int w = 0;
        for (String l : lines) w = Math.max(w, sw(l));
        w += 10;
        int h = lines.size() * 9 + 6;
        int x = mouseX + 10, y = mouseY + 8;
        if (x + w > width - 4) x = mouseX - w - 6;
        if (y + h > height - 4) y = height - h - 4;
        ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xFF3A4660);
        ctx.fill(x, y, x + w, y + h, 0xF0090B10);
        int ly = y + 4;
        for (String l : lines) {
            small(ctx, l, x + 5, ly, 0xFFF0F2F8);
            ly += 9;
        }
    }

    // ---------------------------------------------------------------- input

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
        double mx = click.x(), my = click.y();
        // Search box: let the text field take focus/caret.
        if (mx >= fieldX && mx < fieldX + fieldW && my >= fieldY && my < fieldY + 12 && searchField != null
                && !COLLAPSED.contains(SEARCH)) {
            return super.mouseClicked(click, doubled);
        }
        if (searchField != null) searchField.setFocused(false);
        if (click.button() != 0) return true;
        for (int i = headers.size() - 1; i >= 0; i--) {
            HeaderHit header = headers.get(i);
            if (mx >= header.x() && mx < header.x() + header.w() && my >= header.y() && my < header.y() + header.h()) {
                if (mx >= header.x() + header.w() - 16) {
                    if (!COLLAPSED.remove(header.category())) COLLAPSED.add(header.category());
                } else {
                    draggingCategory = header.category();
                    dragMouseX = (int)Math.round(mx);
                    dragMouseY = (int)Math.round(my);
                }
                return true;
            }
        }
        for (int i = hits.size() - 1; i >= 0; i--) {
            Hit hit = hits.get(i);
            if (mx >= hit.x() && mx < hit.x() + hit.w() && my >= hit.y() && my < hit.y() + hit.h()) {
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
        int columns = Math.min(columnsForWidth(), groups.size());
        int contentHeight = 0;
        for (int h : rowHeights(groups, columns)) contentHeight += h + GAP;
        int maxScroll = Math.max(0, contentHeight - (height - 16 - TOP));
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int)Math.round(verticalAmount * 20)));
        return true;
    }
}
