package com.practicehud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/** Premium-style module browser and visual settings screen. */
public class ConfigScreen extends Screen {
    private static final String SETTINGS_TAB = "Settings";
    private static String currentTab = Modules.CATEGORIES.get(0);
    private static final String[] SHAPES = {"Cross", "Dot", "Circle", "Cross + Dot"};
    private static final int BG = 0xF20A0F18;
    private static final int PANEL = 0xFF151F2D;
    private static final int MUTED = 0xFF9AA8BA;
    private static final int ACCENT = 0xFF55D6FF;

    public ConfigScreen() {
        super(Text.literal("Practice HUD"));
    }

    @Override
    protected void init() {
        clearChildren();
        List<String> tabs = new ArrayList<>(Modules.CATEGORIES);
        tabs.add(SETTINGS_TAB);
        int gap = 5;
        int tabW = Math.min(112, (width - 32 - gap * (tabs.size() - 1)) / tabs.size());
        int totalW = tabs.size() * tabW + (tabs.size() - 1) * gap;
        int tx = (width - totalW) / 2;
        for (String tab : tabs) {
            final String selected = tab;
            addDrawableChild(ButtonWidget.builder(Text.literal(tab.toUpperCase()), btn -> {
                currentTab = selected;
                clearAndInit();
            }).dimensions(tx, 45, tabW, 23).tooltip(Tooltip.of(Text.literal("Open " + tab))).build());
            tx += tabW + gap;
        }

        if (SETTINGS_TAB.equals(currentTab)) initSettings();
        else initModules();

        addDrawableChild(ButtonWidget.builder(Text.literal("CLOSE"), btn -> close())
                .dimensions(width - 112, height - 34, 96, 22).build());
    }

    private void initModules() {
        List<Module> visible = Modules.ALL.stream().filter(m -> m.category.equals(currentTab)).toList();
        int columns = width >= 700 ? 2 : 1;
        int gap = 10;
        int cardW = Math.min(310, (width - 48 - gap * (columns - 1)) / columns);
        int startX = (width - (cardW * columns + gap * (columns - 1))) / 2;
        int startY = 88;
        int rowH = 58;
        for (int i = 0; i < visible.size(); i++) {
            Module module = visible.get(i);
            int col = i % columns;
            int row = i / columns;
            int x = startX + col * (cardW + gap);
            int y = startY + row * rowH;
            addDrawableChild(ButtonWidget.builder(Text.literal(module.isEnabled() ? "ON" : "OFF"), btn -> {
                module.toggle();
                btn.setMessage(Text.literal(module.isEnabled() ? "ON" : "OFF"));
            }).dimensions(x + cardW - 66, y + 16, 56, 22)
                    .tooltip(Tooltip.of(Text.literal(module.description))).build());
        }
    }

    private void initSettings() {
        Config c = Config.get();
        int x = width / 2 - 160;
        int y = 88;
        int w = 320;
        addAction(x, y, w, "Crosshair shape  •  " + SHAPES[c.crosshairShape], () -> {
            c.crosshairShape = (c.crosshairShape + 1) % SHAPES.length;
        }); y += 30;
        addAction(x, y, w, "Crosshair size  •  " + c.crosshairSize, () -> c.crosshairSize = c.crosshairSize >= 24 ? 1 : c.crosshairSize + 1); y += 30;
        addAction(x, y, w, "Crosshair gap  •  " + c.crosshairGap, () -> c.crosshairGap = c.crosshairGap >= 12 ? 0 : c.crosshairGap + 1); y += 30;
        addAction(x, y, w, "Crosshair thickness  •  " + c.crosshairThickness, () -> c.crosshairThickness = c.crosshairThickness >= 5 ? 1 : c.crosshairThickness + 1); y += 30;
        addAction(x, y, w, "Dynamic crosshair  •  " + (c.crosshairDynamic ? "ON" : "OFF"), () -> c.crosshairDynamic = !c.crosshairDynamic); y += 30;
        addAction(x, y, w, "Crosshair outline  •  " + (c.crosshairOutline ? "ON" : "OFF"), () -> c.crosshairOutline = !c.crosshairOutline); y += 30;
        addAction(x, y, w, "Crosshair colour  •  cycle", () -> c.crosshairColor =
                c.crosshairColor == 0xFFFFFFFF ? 0xFF55D6FF : c.crosshairColor == 0xFF55D6FF ? 0xFFFFB86B : 0xFFFFFFFF); y += 30;
        addAction(x, y, w, "Target colour  •  cycle", () -> c.crosshairTargetColor =
                c.crosshairTargetColor == 0xFFFF3030 ? 0xFF55FF55 : c.crosshairTargetColor == 0xFF55FF55 ? 0xFFFFB86B : 0xFFFF3030); y += 30;
        addAction(x, y, w, "Swing animation speed  •  " + String.format("%.1fx", c.swingSpeed), () -> {
            c.swingSpeed = c.swingSpeed >= 5.0 ? 0.1 : Math.round((c.swingSpeed + 0.1) * 10.0) / 10.0;
        }); y += 30;
        addAction(x, y, w, "HUD accent colour  •  cycle", () -> {
            c.accentColor = c.accentColor == 0xFF55D6FF ? 0xFFB78CFF :
                    c.accentColor == 0xFFB78CFF ? 0xFFFFB86B : 0xFF55D6FF;
        }); y += 30;
        addAction(x, y, w, "Open draggable HUD editor", () -> {
            Config.save();
            if (client != null) client.setScreen(new HudEditorScreen());
        }); y += 30;
        addAction(x, y, w, "Reset HUD position", () -> { c.hudX = 4; c.hudY = 4; }); y += 30;
        addAction(x, y, w, "Tracking ring radius  •  " + c.ringRadius, () -> c.ringRadius = c.ringRadius >= 120 ? 10 : c.ringRadius + 5);
        y += 30;
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            addDrawableChild(ButtonWidget.builder(slotText(idx), btn -> {
                c.quickSlots[idx] = (c.quickSlots[idx] + 1) % 9;
                btn.setMessage(slotText(idx));
                Config.save();
            }).dimensions(x, y, w, 22)
                    .tooltip(Tooltip.of(Text.literal("Bind the key itself in Options > Controls > Practice HUD"))).build());
            y += 26;
        }
    }

    private void addAction(int x, int y, int w, String label, Runnable action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), btn -> {
            action.run();
            Config.save();
            clearAndInit();
        }).dimensions(x, y, w, 22).build());
    }

    private Text slotText(int idx) {
        return Text.literal("Quick Slot " + (char) ('A' + idx) + " selects hotbar slot " + (Config.get().quickSlots[idx] + 1));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, BG);
        ctx.fill(0, 0, width, 34, 0xFF101925);
        ctx.fill(0, 34, width, 36, ACCENT);
        ctx.drawTextWithShadow(textRenderer, "PRACTICE HUD", 18, 12, ACCENT);
        ctx.drawTextWithShadow(textRenderer, "CLIENT  /  " + currentTab.toUpperCase(), 18, 68, 0xFFFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "Right Shift", width - 100, 12, MUTED);

        if (!SETTINGS_TAB.equals(currentTab)) {
            List<Module> visible = Modules.ALL.stream().filter(m -> m.category.equals(currentTab)).toList();
            int columns = width >= 700 ? 2 : 1;
            int gap = 10;
            int cardW = Math.min(310, (width - 48 - gap * (columns - 1)) / columns);
            int startX = (width - (cardW * columns + gap * (columns - 1))) / 2;
            for (int i = 0; i < visible.size(); i++) {
                Module m = visible.get(i);
                int x = startX + (i % columns) * (cardW + gap);
                int y = 88 + (i / columns) * 58;
                ctx.fill(x, y, x + cardW, y + 48, PANEL);
                ctx.fill(x, y, x + 2, y + 48, m.isEnabled() ? ACCENT : 0xFF465366);
                ctx.drawTextWithShadow(textRenderer, m.name, x + 12, y + 9, 0xFFFFFFFF);
                ctx.drawText(textRenderer, m.description, x + 12, y + 25, MUTED, false);
            }
        } else {
            ctx.fill(width / 2 - 174, 82, width / 2 + 174, height - 42, PANEL);
        }
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        Config.save();
        super.close();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
