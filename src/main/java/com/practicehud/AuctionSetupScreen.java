package com.practicehud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.registry.Registries;

import java.util.Locale;

/** DonutSMP auction setup: choose an inventory item, check its server worth and configure tracking. */
public final class AuctionSetupScreen extends Screen {
    private static final int PANEL = 0xF0121726;
    private static final int CARD = 0xFF1B2335;
    private static final int HOVER = 0xFF29344D;
    private static final int ACCENT = 0xFFAA8BFF;
    private int selectedSlot = -1;
    private int durationSeconds = 60;
    private double minimumBid = 0.0;
    private TextFieldWidget worthField;

    public AuctionSetupScreen() {
        super(Text.literal("DonutSMP Auction Setup"));
    }

    private int left() { return width / 2 - 220; }
    private int top() { return height / 2 - 145; }
    private ItemStack selectedStack() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || selectedSlot < 0 || selectedSlot >= 36) return ItemStack.EMPTY;
        return mc.player.getInventory().getStack(selectedSlot);
    }
    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
    private void button(DrawContext ctx, int mx, int my, int x, int y, int w, int h, String label, boolean active) {
        ctx.fill(x, y, x + w, y + h, inside(mx, my, x, y, w, h) ? HOVER : CARD);
        ctx.drawCenteredTextWithShadow(textRenderer, label, x + w / 2, y + (h - 8) / 2, active ? ACCENT : 0xFFE8ECF8);
    }
    private static String money(double value) {
        if (value >= 1_000_000_000_000.0) return String.format(Locale.US, "%.2fT", value / 1_000_000_000_000.0);
        if (value >= 1_000_000_000.0) return String.format(Locale.US, "%.2fB", value / 1_000_000_000.0);
        if (value >= 1_000_000.0) return String.format(Locale.US, "%.2fM", value / 1_000_000.0);
        if (value >= 1_000.0) return String.format(Locale.US, "%.2fK", value / 1_000.0);
        return String.format(Locale.US, "%.0f", value);
    }

    @Override
    protected void init() {
        Config c = Config.get();
        worthField = new TextFieldWidget(textRenderer, left() + 235, top() + 158, 170, 22, Text.literal("Item worth"));
        worthField.setPlaceholder(Text.literal("Enter worth, e.g. 1.5m"));
        worthField.setText(c.donutAuctionItemWorthKnown ? Double.toString(c.donutAuctionItemWorth) : "");
        addSelectableChild(worthField);
    }

    private static double parseWorth(String raw) {
        String value = raw.toLowerCase(Locale.ROOT).replace(",", "").replace("$", "").trim();
        if (value.isEmpty()) return Double.NaN;
        double multiplier = 1.0;
        char last = value.charAt(value.length() - 1);
        if (Character.isLetter(last)) {
            multiplier = switch (last) {
                case 'k' -> 1_000.0;
                case 'm' -> 1_000_000.0;
                case 'b' -> 1_000_000_000.0;
                case 't' -> 1_000_000_000_000.0;
                default -> Double.NaN;
            };
            value = value.substring(0, value.length() - 1);
        }
        try { return Double.parseDouble(value) * multiplier; }
        catch (NumberFormatException ignored) { return Double.NaN; }
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB9000000);
        int x = left(), y = top();
        ctx.fill(x, y, x + 440, y + 290, PANEL);
        ctx.fill(x, y, x + 440, y + 2, ACCENT);
        ctx.drawTextWithShadow(textRenderer, "DONUTSMP AUCTION", x + 18, y + 14, ACCENT);
        ctx.drawText(textRenderer, "Choose an item from your inventory", x + 18, y + 32, 0xFF9AA5BB, false);

        int gx = x + 18, gy = y + 62;
        for (int slot = 0; slot < 36; slot++) {
            int col = slot % 9, row = slot / 9;
            int sx = gx + col * 22, sy = gy + row * 22;
            ItemStack stack = client != null && client.player != null
                    ? client.player.getInventory().getStack(slot) : ItemStack.EMPTY;
            boolean selected = selectedSlot == slot;
            ctx.fill(sx, sy, sx + 20, sy + 20, selected ? 0xFF44356D
                    : inside(mouseX, mouseY, sx, sy, 20, 20) ? HOVER : CARD);
            if (!stack.isEmpty()) {
                ctx.drawItem(stack, sx + 2, sy + 2);
                if (stack.getCount() > 1) ctx.drawTextWithShadow(textRenderer, Integer.toString(stack.getCount()), sx + 11, sy + 11, 0xFFFFFFFF);
            }
        }

        int px = x + 235;
        ctx.drawTextWithShadow(textRenderer, "SELECTED ITEM", px, y + 62, 0xFFB9C2D8);
        ItemStack chosen = selectedStack();
        if (chosen.isEmpty()) {
            ctx.drawText(textRenderer, "Click an inventory item", px, y + 82, 0xFF8994AD, false);
        } else {
            ctx.drawItem(chosen, px, y + 80);
            ctx.drawTextWithShadow(textRenderer, chosen.getName().getString(), px + 22, y + 84, 0xFFF2F4FC);
            ctx.drawText(textRenderer, "Amount: " + chosen.getCount(), px + 22, y + 98, 0xFF9AA5BB, false);
        }

        Config c = Config.get();
        String worth = c.donutWorthPending ? "Checking..."
                : c.donutAuctionItemWorthKnown && c.donutAuctionItemId.equals(selectedItemId())
                ? "$" + money(c.donutAuctionItemWorth) + " each"
                : "Not checked";
        ctx.drawText(textRenderer, "Server /worth: " + worth, px, y + 125, 0xFF72E0B0, false);
        ctx.drawText(textRenderer, "This is server base worth, not market price.", px, y + 140, 0xFF8994AD, false);
        worthField.setX(px); worthField.setY(y + 158); worthField.setWidth(170);
        ctx.drawText(textRenderer, "Enter worth manually (e.g. 1.5m)", px, y + 183, 0xFF8994AD, false);

        ctx.drawText(textRenderer, "Auction timer", px, y + 194, 0xFFB9C2D8, false);
        button(ctx, mouseX, mouseY, px, y + 211, 28, 22, "−", true);
        ctx.drawCenteredTextWithShadow(textRenderer, durationSeconds + " sec", px + 83, y + 218, 0xFFF2F4FC);
        button(ctx, mouseX, mouseY, px + 140, y + 211, 28, 22, "+", true);

        ctx.drawText(textRenderer, "Minimum tracked bid: $" + money(minimumBid), x + 18, y + 178, 0xFFB9C2D8, false);
        button(ctx, mouseX, mouseY, x + 18, y + 196, 52, 22, "− 1K", true);
        button(ctx, mouseX, mouseY, x + 76, y + 196, 52, 22, "+ 1K", true);

        button(ctx, mouseX, mouseY, x + 18, y + 244, 190, 28, "Cancel", false);
        button(ctx, mouseX, mouseY, x + 220, y + 244, 202, 28, "Start auction tracker", !chosen.isEmpty());
        ctx.drawText(textRenderer, "This tracks the auction locally; it does not list the item for you.", x + 18, y + 226, 0xFF8994AD, false);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private String selectedItemId() {
        ItemStack stack = selectedStack();
        if (stack.isEmpty()) return "";
        return Registries.ITEM.getId(stack.getItem()).getPath();
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() != 0) return super.mouseClicked(click, doubled);
        double mx = click.x(), my = click.y();
        int x = left(), y = top(), gx = x + 18, gy = y + 62;
        for (int slot = 0; slot < 36; slot++) {
            int sx = gx + (slot % 9) * 22, sy = gy + (slot / 9) * 22;
            if (inside(mx, my, sx, sy, 20, 20)) {
                if (client != null && client.player != null
                        && !client.player.getInventory().getStack(slot).isEmpty()) {
                    selectedSlot = slot;
                    Config.get().donutAuctionItemWorthKnown = false;
                    Config.get().donutWorthPending = false;
                    Config.save();
                }
                return true;
            }
        }

        int px = x + 235;
        if (inside(mx, my, px, y + 158, 170, 25)) {
            ItemStack stack = selectedStack();
            if (!stack.isEmpty()) DonutSmp.requestWorth(stack);
            return true;
        }
        if (inside(mx, my, px, y + 211, 28, 22)) {
            durationSeconds = Math.max(5, durationSeconds - 15);
            return true;
        }
        if (inside(mx, my, px + 140, y + 211, 28, 22)) {
            durationSeconds = Math.min(3600, durationSeconds + 15);
            return true;
        }
        if (inside(mx, my, x + 18, y + 196, 52, 22)) {
            minimumBid = Math.max(0, minimumBid - 1_000);
            return true;
        }
        if (inside(mx, my, x + 76, y + 196, 52, 22)) {
            minimumBid += 1_000;
            return true;
        }
        if (inside(mx, my, x + 18, y + 244, 190, 28)) {
            close();
            return true;
        }
        if (inside(mx, my, x + 220, y + 244, 202, 28)) {
            ItemStack stack = selectedStack();
            if (!stack.isEmpty()) {
                Config.get().donutAuctionDurationSeconds = durationSeconds;
                Config.get().donutMinimumBid = minimumBid;
                DonutSmp.startAuction(stack);
                close();
            }
            return true;
        }
        return true;
    }

    @Override
    public void close() {
        Config.save();
        super.close();
    }

    @Override
    public boolean shouldPause() { return false; }
}
