package com.practicehud;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

/** Simple drag-and-drop editor for the shared HUD panel anchor. */
public final class HudEditorScreen extends Screen {
    private boolean dragging;
    private double dragOffsetX;
    private double dragOffsetY;

    public HudEditorScreen() {
        super(Text.literal("HUD Editor"));
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xE90A0E16);
        ctx.fill(0, 0, width, 34, 0xFF111A28);
        ctx.drawTextWithShadow(textRenderer, "HUD EDITOR", 16, 12, 0xFF55D6FF);
        ctx.drawTextWithShadow(textRenderer, "Drag the preview panel to reposition your HUD", 16, 48, 0xFFCBD5E1);

        Config c = Config.get();
        int x = c.hudX;
        int y = c.hudY;
        int w = 164;
        int h = 64;
        ctx.fill(x, y, x + w, y + h, 0xE51B2636);
        ctx.fill(x, y, x + 3, y + h, c.accentColor);
        ctx.drawTextWithShadow(textRenderer, "PRACTICE HUD", x + 10, y + 8, c.accentColor);
        ctx.drawTextWithShadow(textRenderer, "FPS: 240", x + 10, y + 25, 0xFFFFFFFF);
        ctx.drawTextWithShadow(textRenderer, "X: 120  Y: 64  Z: -38", x + 10, y + 40, 0xFFCBD5E1);

        ctx.drawCenteredTextWithShadow(textRenderer, "Right Shift > Settings > HUD Editor", width / 2, height - 28, 0xFF9AA8BA);
        super.render(ctx, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && mouseX >= Config.get().hudX && mouseX <= Config.get().hudX + 164
                && mouseY >= Config.get().hudY && mouseY <= Config.get().hudY + 64) {
            dragging = true;
            dragOffsetX = mouseX - Config.get().hudX;
            dragOffsetY = mouseY - Config.get().hudY;
            return true;
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging && button == 0) {
            Config c = Config.get();
            c.hudX = Math.max(0, Math.min(width - 164, (int) (mouseX - dragOffsetX)));
            c.hudY = Math.max(0, Math.min(height - 64, (int) (mouseY - dragOffsetY)));
            Config.save();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) dragging = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public void close() {
        Config.save();
        if (client != null) client.setScreen(new ConfigScreen());
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
