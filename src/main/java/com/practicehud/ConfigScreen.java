package com.practicehud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Locale;

/** Prestige-style module browser with selectable colour themes. */
public class ConfigScreen extends Screen {
    private static final String SETTINGS_TAB = "Settings";
    private static final String THEMES_TAB = "Themes";
    private static String currentTab = Modules.CAT_VISUAL;
    private static String searchText = "";

    // [background, sidebar, content, card, accent, primary text, muted text]
    private static final int[][] THEMES = {
            {0xF20A0D20, 0xFF101329, 0xFF0D1023, 0xFF171A31, 0xFF7965F5, 0xFFF1F1FA, 0xFF858BA8}, // Midnight
            {0xF20D0A1C, 0xFF17102B, 0xFF100B20, 0xFF211638, 0xFFB36BFF, 0xFFF7EEFF, 0xFFA994BE}, // Violet
            {0xF2081520, 0xFF0D202B, 0xFF091923, 0xFF112B38, 0xFF39C6E8, 0xFFEAFBFF, 0xFF82AAB8}, // Ocean
            {0xF21B0A12, 0xFF28101B, 0xFF190B14, 0xFF321522, 0xFFFF5C83, 0xFFFFF0F4, 0xFFB88B9A}, // Crimson
            {0xF2D9DCE8, 0xFFE9EAF2, 0xFFF5F6FB, 0xFFFFFFFF, 0xFF6554D9, 0xFF202238, 0xFF70758D}  // Light
    };
    private static final String[] THEME_NAMES = {"Midnight", "Violet", "Ocean", "Crimson", "Light"};

    private TextFieldWidget searchField;

    public ConfigScreen() {
        super(Text.literal("Practice HUD"));
    }

    private int[] palette() {
        int id = Math.max(0, Math.min(THEMES.length - 1, Config.get().themeId));
        return THEMES[id];
    }

    @Override
    protected void init() {
        clearChildren();
        int panelW = Math.min(980, width - 32);
        int panelH = Math.min(590, height - 24);
        int left = (width - panelW) / 2;
        int top = (height - panelH) / 2;
        int sideW = Math.max(150, Math.min(178, panelW / 5));
        int mainX = left + sideW + 14;
        int mainW = panelW - sideW - 28;

        int navY = top + 100;
        for (String category : Modules.CATEGORIES) {
            final String selected = category;
            addDrawableChild(ButtonWidget.builder(Text.literal(category), b -> {
                currentTab = selected;
                searchText = "";
                clearAndInit();
            }).dimensions(left + 12, navY, sideW - 24, 25).build());
            navY += 31;
        }

        int generalY = Math.max(navY + 14, top + panelH - 126);
        addDrawableChild(ButtonWidget.builder(Text.literal("Settings"), b -> {
            currentTab = SETTINGS_TAB;
            clearAndInit();
        }).dimensions(left + 12, generalY, sideW - 24, 24).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Themes"), b -> {
            currentTab = THEMES_TAB;
            clearAndInit();
        }).dimensions(left + 12, generalY + 29, sideW - 24, 24).build());

        if (!SETTINGS_TAB.equals(currentTab) && !THEMES_TAB.equals(currentTab)) {
            searchField = new TextFieldWidget(textRenderer, mainX + mainW - 205, top + 47,
                    194, 24, Text.literal("Search modules"));
            searchField.setPlaceholder(Text.literal("Search modules..."));
            searchField.setText(searchText);
            searchField.setChangedListener(value -> {
                searchText = value;
                clearAndInit();
            });
            addDrawableChild(searchField);
            buildModuleButtons(mainX, top, mainW, panelH);
        } else if (SETTINGS_TAB.equals(currentTab)) {
            initSettings(mainX, top, mainW, panelH);
        } else {
            initThemes(mainX, top, mainW);
        }

        addDrawableChild(ButtonWidget.builder(Text.literal("×"), b -> close())
                .dimensions(left + panelW - 31, top + 10, 21, 21).build());
    }

    private List<Module> filteredModules() {
        String query = searchText.toLowerCase(Locale.ROOT).trim();
        return Modules.ALL.stream()
                .filter(m -> m.category.equals(currentTab))
                .filter(m -> query.isEmpty() || m.name.toLowerCase(Locale.ROOT).contains(query)
                        || m.description.toLowerCase(Locale.ROOT).contains(query))
                .toList();
    }

    private void buildModuleButtons(int mainX, int top, int mainW, int panelH) {
        int y = top + 127;
        for (Module module : filteredModules()) {
            if (y > top + panelH - 48) break;
            int rowY = y;
            addDrawableChild(ButtonWidget.builder(Text.literal(module.isEnabled() ? "ON" : "OFF"), btn -> {
                module.toggle();
                btn.setMessage(Text.literal(module.isEnabled() ? "ON" : "OFF"));
            }).dimensions(mainX + mainW - 66, rowY + 16, 48, 24)
                    .tooltip(Tooltip.of(Text.literal(module.description))).build());
            y += 61;
        }
    }

    private void initSettings(int x, int top, int w, int panelH) {
        Config c = Config.get();
        int y = top + 110;
        int rowW = Math.min(w - 12, 690);
        addAction(x, y, rowW, "Crosshair shape  •  " + new String[]{"Cross", "Dot", "Circle", "Cross + Dot"}[Math.max(0, Math.min(3, c.crosshairShape))], () -> c.crosshairShape = (c.crosshairShape + 1) % 4); y += 32;
        addAction(x, y, rowW, "Crosshair size  •  " + c.crosshairSize, () -> c.crosshairSize = c.crosshairSize >= 24 ? 1 : c.crosshairSize + 1); y += 32;
        addAction(x, y, rowW, "Crosshair gap  •  " + c.crosshairGap, () -> c.crosshairGap = c.crosshairGap >= 12 ? 0 : c.crosshairGap + 1); y += 32;
        addAction(x, y, rowW, "Crosshair thickness  •  " + c.crosshairThickness, () -> c.crosshairThickness = c.crosshairThickness >= 5 ? 1 : c.crosshairThickness + 1); y += 32;
        addAction(x, y, rowW, "Swing Speed  •  " + c.swingSpeedTicks + " ticks (lower = faster)", () -> c.swingSpeedTicks = c.swingSpeedTicks <= 1 ? 6 : c.swingSpeedTicks - 1); y += 32;
        addAction(x, y, rowW, "Dynamic crosshair  •  " + (c.crosshairDynamic ? "ON" : "OFF"), () -> c.crosshairDynamic = !c.crosshairDynamic); y += 32;
        addAction(x, y, rowW, "Crosshair outline  •  " + (c.crosshairOutline ? "ON" : "OFF"), () -> c.crosshairOutline = !c.crosshairOutline); y += 32;
        addAction(x, y, rowW, "Crosshair colour  •  cycle", () -> c.crosshairColor = c.crosshairColor == 0xFFFFFFFF ? 0xFF55D6FF : c.crosshairColor == 0xFF55D6FF ? 0xFFFFB86B : 0xFFFFFFFF); y += 32;
        addAction(x, y, rowW, "HUD accent colour  •  cycle", () -> c.accentColor = c.accentColor == 0xFF55D6FF ? 0xFFB78CFF : c.accentColor == 0xFFB78CFF ? 0xFFFFB86B : 0xFF55D6FF); y += 32;
        addAction(x, y, rowW, "Open draggable HUD editor", () -> { Config.save(); if (client != null) client.setScreen(new HudEditorScreen()); }); y += 32;
        addAction(x, y, rowW, "Reset HUD position", () -> { c.hudX = 4; c.hudY = 4; });
    }

    private void initThemes(int x, int top, int w) {
        int y = top + 126;
        for (int i = 0; i < THEME_NAMES.length; i++) {
            final int id = i;
            String label = THEME_NAMES[i] + (Config.get().themeId == i ? "  •  ACTIVE" : "  •  Select theme");
            addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
                Config.get().themeId = id;
                Config.save();
                clearAndInit();
            }).dimensions(x + 8, y, Math.min(w - 20, 440), 34).build());
            y += 44;
        }
    }

    private void addAction(int x, int y, int w, String label, Runnable action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), btn -> {
            action.run();
            Config.save();
            clearAndInit();
        }).dimensions(x + 8, y, w, 26).build());
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int[] p = palette();
        int panelW = Math.min(980, width - 32);
        int panelH = Math.min(590, height - 24);
        int left = (width - panelW) / 2;
        int top = (height - panelH) / 2;
        int sideW = Math.max(150, Math.min(178, panelW / 5));
        int mainX = left + sideW;
        int mainW = panelW - sideW;

        ctx.fill(0, 0, width, height, p[0]);
        ctx.fill(left, top, left + panelW, top + panelH, p[1]);
        ctx.fill(mainX, top + 2, left + panelW - 2, top + panelH - 2, p[2]);
        ctx.fill(left + 12, top + 17, left + 19, top + 24, p[4]);
        ctx.drawTextWithShadow(textRenderer, "Prestige Client", left + 27, top + 15, p[5]);
        ctx.drawText(textRenderer, "PRACTICE HUD  •  CLIENT MENU", left + 15, top + 48, p[6], false);
        ctx.drawText(textRenderer, "MODULES", left + 15, top + 78, p[6], false);
        ctx.drawText(textRenderer, "GENERAL", left + 15, top + panelH - 137, p[6], false);

        String heading = currentTab.equals(SETTINGS_TAB) ? "Settings" : currentTab.equals(THEMES_TAB) ? "Themes" : currentTab + " Modules";
        ctx.drawTextWithShadow(textRenderer, heading, mainX + 18, top + 18, p[5]);
        String subtitle = currentTab.equals(THEMES_TAB) ? "Choose the look of your client" : "Manage and customise your client features";
        ctx.drawText(textRenderer, subtitle, mainX + 18, top + 34, p[6], false);

        if (!SETTINGS_TAB.equals(currentTab) && !THEMES_TAB.equals(currentTab)) {
            List<Module> list = filteredModules();
            long enabled = list.stream().filter(Module::isEnabled).count();
            ctx.drawText(textRenderer, list.size() + " modules  •  " + enabled + " enabled", mainX + 18, top + 83, p[6], false);
            int y = top + 116;
            for (Module module : list) {
                if (y > top + panelH - 48) break;
                int right = left + panelW - 13;
                ctx.fill(mainX + 8, y, right, y + 55, p[3]);
                ctx.drawTextWithShadow(textRenderer, module.name, mainX + 22, y + 10, p[5]);
                String desc = module.description;
                int max = Math.max(22, (mainW - 145) / 5);
                if (desc.length() > max) desc = desc.substring(0, max - 3) + "...";
                ctx.drawText(textRenderer, desc, mainX + 22, y + 29, p[6], false);
                ctx.drawText(textRenderer, "›", right - 88, y + 19, p[6], false);
                ctx.fill(right - 35, y + 21, right - 13, y + 34, module.isEnabled() ? p[4] : p[6]);
                y += 61;
            }
            if (list.isEmpty()) ctx.drawText(textRenderer, "No modules found", mainX + 22, top + 135, p[6], false);
        } else if (THEMES_TAB.equals(currentTab)) {
            ctx.drawText(textRenderer, "AVAILABLE THEMES", mainX + 18, top + 88, p[6], false);
            int y = top + 116;
            for (int i = 0; i < THEMES.length; i++) {
                int[] theme = THEMES[i];
                ctx.fill(mainX + 8, y, left + panelW - 13, y + 36, theme[3]);
                ctx.fill(mainX + 20, y + 10, mainX + 40, y + 26, theme[4]);
                ctx.drawText(textRenderer, THEME_NAMES[i], mainX + 51, y + 14, theme[5], false);
                if (Config.get().themeId == i) ctx.drawText(textRenderer, "ACTIVE", left + panelW - 75, y + 14, theme[4], false);
                y += 45;
            }
        } else {
            ctx.drawText(textRenderer, "CLIENT PREFERENCES", mainX + 18, top + 88, p[6], false);
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
