package com.practicehud;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;
import java.util.Map;

/** Select and drag individual HUD cards; positions persist in the mod config. */
public final class HudEditorScreen extends Screen {
    private static final List<String> WIDGETS = List.of(
            "Performance", "Coordinates", "Armor Status", "Potion Effects", "Keystrokes",
            "Held Item", "World Time", "Totems", "Respawn Anchor", "Movement", "Guard Timing",
            "Shield", "Cooldown", "Combat", "Critical Hit", "Mace", "Wind Burst Trainer",
            "Vertical Speed", "Elytra", "Minecarts", "Rail Supplies", "Throw Landing"
    );
    private static int selectedIndex = 0;
    private boolean dragging;
    private double dragOffsetX;
    private double dragOffsetY;

    public HudEditorScreen() { super(Text.literal("HUD Studio")); }

    private String selected() { return WIDGETS.get(Math.max(0, Math.min(WIDGETS.size() - 1, selectedIndex))); }

    @Override
    protected void init() {
        clearChildren();
        addDrawableChild(ButtonWidget.builder(Text.literal("‹"), b -> {
            selectedIndex = (selectedIndex + WIDGETS.size() - 1) % WIDGETS.size();
            clearAndInit();
        }).dimensions(16, 58, 28, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("›"), b -> {
            selectedIndex = (selectedIndex + 1) % WIDGETS.size();
            clearAndInit();
        }).dimensions(48, 58, 28, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset position"), b -> {
            Config.get().hudPositions.remove(selected());
            Config.save();
        }).dimensions(16, 88, 112, 22).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(width - 86, 12, 70, 22).build());
    }

    private int[] position() {
        Config c = Config.get();
        int[] saved = c.hudPositions.get(selected());
        if (saved != null) return saved;
        return new int[]{c.hudX, c.hudY + selectedIndex * 8};
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xE9080C16);
        ctx.fill(0, 0, width, 42, 0xFF11182A);
        ctx.drawTextWithShadow(textRenderer, "HUD STUDIO", 16, 14, Config.get().accentColor);
        ctx.drawText(textRenderer, "Select a widget, then drag its preview anywhere.", 16, 48, 0xFFB8C2D8, false);
        ctx.drawTextWithShadow(textRenderer, selected(), 84, 63, 0xFFF2F5FF);
        ctx.drawText(textRenderer, (selectedIndex + 1) + " / " + WIDGETS.size(), 84, 77, 0xFF8994AD, false);
        ctx.drawText(textRenderer, "Each widget saves its own position.", 16, 120, 0xFF8994AD, false);

        int[] pos = position();
        int x = pos[0], y = pos[1];
        int cardW = 190, cardH = 66;
        x = Math.max(0, Math.min(width - cardW, x));
        y = Math.max(0, Math.min(height - cardH, y));
        ctx.fill(x + 3, y + 3, x + cardW + 3, y + cardH + 3, 0x60000000);
        ctx.fill(x, y, x + cardW, y + cardH, 0xF0101524);
        ctx.fill(x, y, x + 3, y + cardH, Config.get().accentColor);
        ctx.fill(x + 12, y + 12, x + 16, y + 16, Config.get().accentColor);
        ctx.drawTextWithShadow(textRenderer, selected(), x + 22, y + 9, 0xFFF4F6FF);
        ctx.fill(x + 12, y + 29, x + cardW - 12, y + 30, 0x553D4964);
        ctx.drawText(textRenderer, "Preview • drag to move", x + 12, y + 40, 0xFF9AA5BB, false);
        ctx.drawCenteredTextWithShadow(textRenderer, "Changes are saved automatically", width / 2, height - 24, 0xFF8994AD);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        int[] pos = position();
        if (click.button() == 0 && click.x() >= pos[0] && click.x() <= pos[0] + 190
                && click.y() >= pos[1] && click.y() <= pos[1] + 66) {
            dragging = true;
            dragOffsetX = click.x() - pos[0];
            dragOffsetY = click.y() - pos[1];
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (dragging && click.button() == 0) {
            Config c = Config.get();
            c.hudPositions.put(selected(), new int[]{
                    Math.max(0, Math.min(width - 190, (int) (click.x() - dragOffsetX))),
                    Math.max(0, Math.min(height - 66, (int) (click.y() - dragOffsetY)))
            });
            Config.save();
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (click.button() == 0) dragging = false;
        return super.mouseReleased(click);
    }

    @Override
    public void close() {
        Config.save();
        if (client != null) client.setScreen(new ConfigScreen());
    }

    @Override
    public boolean shouldPause() { return false; }
}
