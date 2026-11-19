package com.practicehud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/** DonutSMP auction setup with searchable Minecraft item registry. */
public final class AuctionSetupScreen extends Screen {
    private static final int PANEL = 0xF0121726;
    private static final int CARD = 0xFF1B2335;
    private static final int HOVER = 0xFF29344D;
    private static final int ACCENT = 0xFFAA8BFF;
    private static final int[] DURATIONS = {30, 60, 120, 300};
    private static final double[] BID_STEPS = {1_000, 10_000, 100_000, 1_000_000};
    private static final String[] BID_LABELS = {"1K", "10K", "100K", "1M"};
    private TextFieldWidget searchField;
    private TextFieldWidget worthField;
    private final List<Item> results = new ArrayList<>();
    private ItemStack selectedItem = ItemStack.EMPTY;
    private int resultScroll = 0;
    private int durationSeconds = 60;
    private double minimumBid = 0.0;

    public AuctionSetupScreen() { super(Text.literal("DonutSMP Auction Setup")); }

    private int left() { return width / 2 - 280; }
    private int top() { return height / 2 - 160; }
    private boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
    private void button(DrawContext ctx, int mx, int my, int x, int y, int w, int h, String label) {
        ctx.fill(x, y, x + w, y + h, inside(mx, my, x, y, w, h) ? HOVER : CARD);
        ctx.drawCenteredTextWithShadow(textRenderer, label, x + w / 2, y + (h - 8) / 2, 0xFFE8ECF8);
    }
    private static String fmtDuration(int sec) {
        return sec >= 60 && sec % 60 == 0 ? (sec / 60) + " min" : sec + " sec";
    }
    /** When the /worth reply arrives, show it in the field for the selected item. */
    private void syncWorthField() {
        Config c = Config.get();
        if (c.donutAuctionItemWorthKnown && c.donutAuctionItemId.equals(selectedItemId())
                && !worthField.isFocused()) {
            worthField.setText(Double.toString(c.donutAuctionItemWorth));
        }
    }
    private void start() {
        if (selectedItem.isEmpty()) return;
        double enteredWorth = parseWorth(worthField.getText());
        Config c = Config.get();
        c.donutAuctionDurationSeconds = durationSeconds;
        c.donutMinimumBid = minimumBid;
        c.donutAuctionItemName = selectedItem.getName().getString();
        c.donutAuctionItemId = selectedItemId();
        if (Double.isFinite(enteredWorth) && enteredWorth >= 0) {
            c.donutAuctionItemWorth = enteredWorth;
            c.donutAuctionItemWorthKnown = true;
        } else c.donutAuctionItemWorthKnown = false;
        DonutSmp.startAuction(selectedItem);
        close();
    }
    private static String money(double value) {
        if (value >= 1_000_000_000_000.0) return String.format(Locale.US, "%.2fT", value / 1_000_000_000_000.0);
        if (value >= 1_000_000_000.0) return String.format(Locale.US, "%.2fB", value / 1_000_000_000.0);
        if (value >= 1_000_000.0) return String.format(Locale.US, "%.2fM", value / 1_000_000.0);
        if (value >= 1_000.0) return String.format(Locale.US, "%.2fK", value / 1_000.0);
        return String.format(Locale.US, "%.0f", value);
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
    private void refreshResults() {
        String query = searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        results.clear();
        if (query.isEmpty()) return;
        Registries.ITEM.stream()
                .filter(item -> item != net.minecraft.item.Items.AIR)
                .filter(item -> item.getName().getString().toLowerCase(Locale.ROOT).contains(query)
                        || Registries.ITEM.getId(item).getPath().replace('_', ' ').contains(query)
                        || Registries.ITEM.getId(item).toString().toLowerCase(Locale.ROOT).contains(query))
                .limit(200)
                .forEach(results::add);
        resultScroll = 0;
    }
    @Override
    protected void init() {
        Config c = Config.get();
        searchField = new TextFieldWidget(textRenderer, left() + 16, top() + 58, 250, 22, Text.literal("Search items"));
        searchField.setPlaceholder(Text.literal("Search all items..."));
        searchField.setChangedListener(value -> refreshResults());
        addDrawableChild(searchField);
        worthField = new TextFieldWidget(textRenderer, left() + 310, top() + 160, 235, 22, Text.literal("Item worth"));
        worthField.setPlaceholder(Text.literal("e.g. 1.5m"));
        durationSeconds = Math.max(5, Math.min(3600, c.donutAuctionDurationSeconds));
        minimumBid = c.donutMinimumBid;
        worthField.setText("");
        setInitialFocus(searchField);
        addDrawableChild(worthField);
    }
    private boolean shiftHeld() {
        long window = MinecraftClient.getInstance().getWindow().getHandle();
        return org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(window, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }
    private String selectedItemId() {
        return selectedItem.isEmpty() ? "" : Registries.ITEM.getId(selectedItem.getItem()).getPath();
    }
    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, width, height, 0xB9000000);
        int x = left(), y = top();
        ctx.fill(x, y, x + 560, y + 320, PANEL);
        ctx.fill(x, y, x + 560, y + 2, ACCENT);
        ctx.drawTextWithShadow(textRenderer, "DONUTSMP AUCTION", x + 16, y + 14, ACCENT);
        ctx.drawText(textRenderer, "Search for any Minecraft item", x + 16, y + 34, 0xFF9AA5BB, false);
        ctx.drawText(textRenderer, "ITEM SEARCH", x + 16, y + 46, 0xFFB9C2D8, false);
        ctx.drawText(textRenderer, "SEARCH RESULTS", x + 16, y + 86, 0xFFB9C2D8, false);
        for (int i = 0; i < 8; i++) {
            int index = resultScroll + i;
            int ry = y + 102 + i * 23;
            if (index >= results.size()) break;
            Item item = results.get(index);
            boolean chosen = !selectedItem.isEmpty() && selectedItem.isOf(item);
            ctx.fill(x + 16, ry, x + 266, ry + 21, chosen ? 0xFF44356D
                    : inside(mouseX, mouseY, x + 16, ry, 250, 21) ? HOVER : CARD);
            ctx.drawItem(new ItemStack(item), x + 19, ry + 2);
            ctx.drawText(textRenderer, item.getName().getString(), x + 42, ry + 7, 0xFFE8ECF8, false);
        }
        if (results.isEmpty()) ctx.drawText(textRenderer, "Type to search the item list", x + 20, y + 110, 0xFF8994AD, false);
        else ctx.drawText(textRenderer, "Showing " + (resultScroll + 1) + "-" + Math.min(resultScroll + 8, results.size()) + " of " + results.size(), x + 16, y + 291, 0xFF8994AD, false);

        int px = x + 310;
        ctx.drawTextWithShadow(textRenderer, "SELECTED ITEM", px, y + 58, 0xFFB9C2D8);
        if (selectedItem.isEmpty()) ctx.drawText(textRenderer, "Choose an item from search", px, y + 80, 0xFF8994AD, false);
        else {
            ctx.drawItem(selectedItem, px, y + 78);
            ctx.drawTextWithShadow(textRenderer, selectedItem.getName().getString(), px + 24, y + 84, 0xFFF2F4FC);
            ctx.drawText(textRenderer, Registries.ITEM.getId(selectedItem.getItem()).toString(), px + 24, y + 98, 0xFF8994AD, false);
        }
        Config c = Config.get();
        String worth = c.donutAuctionItemWorthKnown && c.donutAuctionItemId.equals(selectedItemId())
                ? "$" + money(c.donutAuctionItemWorth) + " each" : "Not set";
        ctx.drawText(textRenderer, "Entered worth: " + worth, px, y + 128, 0xFF72E0B0, false);
        ctx.drawText(textRenderer, "ITEM WORTH", px, y + 145, 0xFFB9C2D8, false);
        worthField.setX(px); worthField.setY(y + 160); worthField.setWidth(235);
        ctx.drawText(textRenderer, "Supports 250k, 1.5m, 2b", px, y + 185, 0xFF8994AD, false);
        ctx.drawText(textRenderer, "Auction timer", px, y + 204, 0xFFB9C2D8, false);
        button(ctx, mouseX, mouseY, px, y + 218, 28, 20, "-");
        ctx.drawCenteredTextWithShadow(textRenderer, fmtDuration(durationSeconds), px + 117, y + 224, 0xFFF2F4FC);
        button(ctx, mouseX, mouseY, px + 207, y + 218, 28, 20, "+");
        for (int i = 0; i < DURATIONS.length; i++) {
            int bx = px + 34 + i * 43;
            boolean on = durationSeconds == DURATIONS[i];
            ctx.fill(bx, y + 240, bx + 40, y + 254, on ? 0xFF44356D : inside(mouseX, mouseY, bx, y + 240, 40, 14) ? HOVER : CARD);
            ctx.drawCenteredTextWithShadow(textRenderer, fmtDuration(DURATIONS[i]), bx + 20, y + 243, 0xFFE8ECF8);
        }
        ctx.drawText(textRenderer, "Minimum bid: $" + money(minimumBid), px, y + 260, 0xFFB9C2D8, false);
        for (int i = 0; i < BID_STEPS.length; i++) {
            int bx = px + i * 59;
            button(ctx, mouseX, mouseY, bx, y + 272, 56, 16, (shiftHeld() ? "-" : "+") + BID_LABELS[i]);
        }
        ctx.drawText(textRenderer, "Hold Shift to subtract", px + 120, y + 260, 0xFF8994AD, false);
        boolean hasItem = !selectedItem.isEmpty();
        boolean checking = Config.get().donutWorthPending;
        ctx.fill(px + 150, y + 125, px + 235, y + 141, !hasItem ? 0xFF161C2B : inside(mouseX, mouseY, px + 150, y + 125, 85, 16) ? HOVER : CARD);
        ctx.drawCenteredTextWithShadow(textRenderer, checking ? "Checking..." : "Check worth", px + 192, y + 129,
                hasItem ? 0xFFE8ECF8 : 0xFF66708A);
        button(ctx, mouseX, mouseY, x + 16, y + 298, 100, 18, "Cancel");
        ctx.fill(x + 310, y + 298, x + 545, y + 316, hasItem ? 0xFF5B46A8 : 0xFF2A3047);
        ctx.drawCenteredTextWithShadow(textRenderer, hasItem ? "Start auction tracker" : "Select an item first",
                x + 427, y + 303, hasItem ? 0xFFFFFFFF : 0xFF7A849C);
        if (checking) syncWorthField();
        super.render(ctx, mouseX, mouseY, delta);
    }
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            double mx = click.x(), my = click.y();
            int x = left(), y = top(), px = x + 310;
            for (int i = 0; i < 8; i++) {
                int index = resultScroll + i, ry = y + 102 + i * 23;
                if (index < results.size() && inside(mx, my, x + 16, ry, 250, 21)) {
                    selectedItem = new ItemStack(results.get(index));
                    Config.get().donutAuctionItemWorthKnown = false;
                    worthField.setText("");
                    return true;
                }
            }
            if (inside(mx, my, x + 16, y + 298, 100, 18)) { close(); return true; }
            if (inside(mx, my, px, y + 218, 28, 20)) { durationSeconds = Math.max(5, durationSeconds - (shiftHeld() ? 5 : 15)); return true; }
            if (inside(mx, my, px + 207, y + 218, 28, 20)) { durationSeconds = Math.min(3600, durationSeconds + (shiftHeld() ? 5 : 15)); return true; }
            for (int i = 0; i < DURATIONS.length; i++) {
                if (inside(mx, my, px + 34 + i * 43, y + 240, 40, 14)) { durationSeconds = DURATIONS[i]; return true; }
            }
            for (int i = 0; i < BID_STEPS.length; i++) {
                if (inside(mx, my, px + i * 59, y + 272, 56, 16)) {
                    minimumBid = Math.max(0, minimumBid + (shiftHeld() ? -BID_STEPS[i] : BID_STEPS[i]));
                    return true;
                }
            }
            if (inside(mx, my, px + 150, y + 125, 85, 16)) {
                if (!selectedItem.isEmpty()) DonutSmp.requestWorth(selectedItem);
                return true;
            }
            if (inside(mx, my, x + 310, y + 298, 235, 18)) { start(); return true; }
        }
        return super.mouseClicked(click, doubled);
    }
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (inside(mouseX, mouseY, left() + 16, top() + 100, 250, 190)) {
            int max = Math.max(0, results.size() - 8);
            resultScroll = Math.max(0, Math.min(max, resultScroll - (int) Math.signum(verticalAmount)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyInput input) {
        if ((input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER)
                && !selectedItem.isEmpty() && !searchField.isFocused()) {
            start();
            return true;
        }
        return super.keyPressed(input);
    }
    @Override
    public void close() { Config.save(); super.close(); }
    @Override
    public boolean shouldPause() { return false; }
}
