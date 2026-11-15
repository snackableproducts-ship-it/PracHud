package com.practicehud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Sidebar-based dark client GUI inspired by the supplied reference. */
public class ConfigScreen extends Screen {
    private static final String SETTINGS_TAB = "Settings";
    private static String currentTab = Modules.CAT_VISUAL;
    private static final String[] SHAPES = {"Cross", "Dot", "Circle", "Cross + Dot"};

    private static final int OVERLAY = 0xB8000008;
    private static final int FRAME = 0xF20A0D20;
    private static final int SIDEBAR = 0xFF101329;
    private static final int CONTENT = 0xFF0D1023;
    private static final int CARD = 0xFF171A31;
    private static final int CARD_HOVER = 0xFF202541;
    private static final int ACCENT = 0xFF7965F5;
    private static final int TEXT = 0xFFF1F1FA;
    private static final int MUTED = 0xFF858BA8;
    private static final int GREEN = 0xFF7DDBB0;

    private TextFieldWidget searchField;
    private String searchText = "";

    public ConfigScreen() {
        super(Text.literal("Practice HUD"));
    }

    @Override
    protected void init() {
        clearChildren();
        int panelW = Math.min(900, width - 36);
        int panelH = Math.min(540, height - 28);
        int left = (width - panelW) / 2;
        int top = (height - panelH) / 2;
        int sideW = Math.max(126, Math.min(158, panelW / 5));
        int mainX = left + sideW + 18;
        int mainW = panelW - sideW - 36;

        int navY = top + 78;
        for (String category : Modules.CATEGORIES) {
            final String selected = category;
            addDrawableChild(ButtonWidget.builder(Text.literal(category), b -> {
                currentTab = selected;
                searchText = "";
                clearAndInit();
            }).dimensions(left + 10, navY, sideW - 20, 25).build());
            navY += 31;
        }
        navY += 18;
        addDrawableChild(ButtonWidget.builder(Text.literal("Settings"), b -> {
            currentTab = SETTINGS_TAB;
            clearAndInit();
        }).dimensions(left + 10, navY, sideW - 20, 25).build());

        if (!SETTINGS_TAB.equals(currentTab)) {
            searchField = new TextFieldWidget(textRenderer, mainX + mainW - 205, top + 48,
                    195, 24, Text.literal("Search modules"));
            searchField.setPlaceholder(Text.literal("Search modules..."));
            searchField.setText(searchText);
            searchField.setChangedListener(value -> {
                searchText = value;
                rebuildModuleButtons();
            });
            addDrawableChild(searchField);
            buildModuleButtons(left, top, sideW, panelW, panelH);
        } else {
            initSettings(left, top, sideW, panelW, panelH);
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("×"), b -> close())
                .dimensions(left + panelW - 31, top + 10, 21, 21).build());
    }

    private void rebuildModuleButtons() {
        // Reinitialize so the filtered list and its toggle controls stay in sync.
        if (client != null) clearAndInit();
    }

    private List<Module> filteredModules() {
        String query = searchText.toLowerCase(Locale.ROOT).trim();
        return Modules.ALL.stream()
                .filter(m -> m.category.equals(currentTab))
                .filter(m -> query.isEmpty() || m.name.toLowerCase(Locale.ROOT).contains(query)
                        || m.description.toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    private void buildModuleButtons(int left, int top, int sideW, int panelW, int panelH) {
        int mainX = left + sideW + 18;
        int mainW = panelW - sideW - 36;
        int rowY = top + 112;
        int rowH = 52;
        for (Module module : filteredModules()) {
            int y = rowY;
            addDrawableChild(ButtonWidget.builder(Text.literal(module.isEnabled() ? "ON" : "OFF"), btn -> {
                module.toggle();
                btn.setMessage(Text.literal(module.isEnabled() ? "ON" : "OFF"));
            }).dimensions(mainX + mainW - 58, y + 14, 46, 23)
                    .tooltip(Tooltip.of(Text.literal(module.description))).build());
            rowY += rowH + 5;
            if (rowY > top + panelH - 45) break;
        }
    }

    private void initSettings(int left, int top, int sideW, int panelW, int panelH) {
        Config c = Config.get();
        int x = left + sideW + 28;
        int y = top + 106;
        int w = panelW - sideW - 56;
        addAction(x, y, w, "Crosshair shape  •  " + SHAPES[c.crosshairShape], () -> c.crosshairShape = (c.crosshairShape + 1) % SHAPES.length); y += 29;
        addAction(x, y, w, "Crosshair size  •  " + c.crosshairSize, () -> c.crosshairSize = c.crosshairSize >= 24 ? 1 : c.crosshairSize + 1); y += 29;
        addAction(x, y, w, "Crosshair gap  •  " + c.crosshairGap, () -> c.crosshairGap = c.crosshairGap >= 12 ? 0 : c.crosshairGap + 1); y += 29;
        addAction(x, y, w, "Crosshair thickness  •  " + c.crosshairThickness, () -> c.crosshairThickness = c.crosshairThickness >= 5 ? 1 : c.crosshairThickness + 1); y += 29;
        addAction(x, y, w, "Swing animation  •  " + c.swingSpeedTicks + " ticks", () -> c.swingSpeedTicks = c.swingSpeedTicks <= 1 ? 6 : c.swingSpeedTicks - 1); y += 29;
        addAction(x, y, w, "Dynamic crosshair  •  " + (c.crosshairDynamic ? "ON" : "OFF"), () -> c.crosshairDynamic = !c.crosshairDynamic); y += 29;
        addAction(x, y, w, "Crosshair outline  •  " + (c.crosshairOutline ? "ON" : "OFF"), () -> c.crosshairOutline = !c.crosshairOutline); y += 29;
        addAction(x, y, w, "Crosshair colour  •  cycle", () -> c.crosshairColor = c.crosshairColor == 0xFFFFFFFF ? 0xFF55D6FF : c.crosshairColor == 0xFF55D6FF ? 0xFFFFB86B : 0xFFFFFFFF); y += 29;
        addAction(x, y, w, "Target colour  •  cycle", () -> c.crosshairTargetColor = c.crosshairTargetColor == 0xFFFF3030 ? 0xFF55FF55 : c.crosshairTargetColor == 0xFF55FF55 ? 0xFFFFB86B : 0xFFFF3030); y += 29;
        addAction(x, y, w, "HUD accent colour  •  cycle", () -> c.accentColor = c.accentColor == 0xFF55D6FF ? 0xFFB78CFF : c.accentColor == 0xFFB78CFF ? 0xFFFFB86B : 0xFF55D6FF); y += 29;
        addAction(x, y, w, "Open draggable HUD editor", () -> {
            Config.save();
            if (client != null) client.setScreen(new HudEditorScreen());
        }); y += 29;
        addAction(x, y, w, "Reset HUD position", () -> { c.hudX = 4; c.hudY = 4; }); y += 29;
        addAction(x, y, w, "Tracking ring radius  •  " + c.ringRadius, () -> c.ringRadius = c.ringRadius >= 120 ? 10 : c.ringRadius + 5);
    }

    private void addAction(int x, int y, int w, String label, Runnable action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), btn -> {
            action.run();
            Config.save();
            clearAndInit();
        }).dimensions(x, y, w, 22).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, OVERLAY);
        int panelW = Math.min(900, width - 36);
        int panelH = Math.min(540, height - 28);
        int left = (width - panelW) / 2;
        int top = (height - panelH) / 2;
        int sideW = Math.max(126, Math.min(158, panelW / 5));
        int mainX = left + sideW;
        int mainW = panelW - sideW;

        ctx.fill(left, top, left + panelW, top + panelH, FRAME);
        ctx.fill(left + 2, top + 2, left + sideW, top + panelH - 2, SIDEBAR);
        ctx.fill(mainX, top + 2, left + panelW - 2, top + panelH - 2, CONTENT);

        ctx.fill(left + 15, top + 17, left + 21, top + 23, ACCENT);
        ctx.drawTextWithShadow(textRenderer, "PRACTICE HUD", left + 29, top + 16, TEXT);
        ctx.drawText(textRenderer, "CLIENT MENU", left + 15, top + 48, MUTED, false);
        ctx.drawText(textRenderer, "MODULES", left + 15, top + 66, MUTED, false);

        ctx.drawTextWithShadow(textRenderer, currentTab.equals(SETTINGS_TAB) ? "Settings" : currentTab,
                mainX + 18, top + 18, TEXT);
        ctx.drawText(textRenderer, "Manage your client features", mainX + 18, top + 33, MUTED, false);

        if (!SETTINGS_TAB.equals(currentTab)) {
            ctx.drawText(textRenderer, "MODULE LIST", mainX + 18, top + 82, MUTED, false);
            int rowY = top + 108;
            int rowH = 52;
            for (Module module : filteredModules()) {
                if (rowY > top + panelH - 42) break;
                int right = left + panelW - 14;
                ctx.fill(mainX + 10, rowY, right, rowY + 47, CARD);
                ctx.drawTextWithShadow(textRenderer, module.name, mainX + 22, rowY + 10, TEXT);
                String desc = module.description;
                int maxChars = Math.max(18, (mainW - 112) / 5);
                if (desc.length() > maxChars) desc = desc.substring(0, maxChars - 3) + "...";
                ctx.drawText(textRenderer, desc, mainX + 22, rowY + 27, MUTED, false);
                ctx.drawText(textRenderer, module.isEnabled() ? "ENABLED" : "DISABLED",
                        right - 91, rowY + 18, module.isEnabled() ? GREEN : MUTED, false);
                rowY += rowH;
            }
            if (filteredModules().isEmpty()) {
                ctx.drawText(textRenderer, "No modules found", mainX + 22, top + 128, MUTED, false);
            }
        } else {
            ctx.drawText(textRenderer, "CLIENT PREFERENCES", mainX + 18, top + 82, MUTED, false);
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
