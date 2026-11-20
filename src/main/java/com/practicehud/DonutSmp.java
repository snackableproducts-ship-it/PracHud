package com.practicehud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DonutSMP stream statistics.
 * Tracks only the server's virtual in-game balance; it never handles real money.
 */
public final class DonutSmp {
    // "balance: 1,234" / "current balance is $1.5m" / "you have $2m" (a bare "you have 3 ..." is not a balance).
    private static final Pattern BALANCE = Pattern.compile(
            "(?i)(?:(?:current\\s+)?balance\\s*(?:is|of|:|=)?\\s*\\$?|you\\s+have\\s+\\$)\\s*([0-9][0-9,]*(?:\\.[0-9]+)?\\s*[kmbt]?)\\b");
    private static final Pattern[] INCOMING_PATTERNS = {
            Pattern.compile("(?i)([A-Za-z0-9_]{3,16})\\s+(?:paid|payed|sent|gave)\\s+(?:you\\s+)?\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?\\s*[kmbt]?)"),
            Pattern.compile("(?i)(?:you\\s+)?received\\s+\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?\\s*[kmbt]?)\\s+from\\s+([A-Za-z0-9_]{3,16})"),
            Pattern.compile("(?i)payment\\s+received\\s+from\\s+([A-Za-z0-9_]{3,16})[^0-9$]*\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?\\s*[kmbt]?)")
    };
    private static final Pattern BID_EVENT = Pattern.compile(
            "(?i)(?:\\[?auction\\]?\\s*)?([A-Za-z0-9_]{3,16})\\s+(?:placed\\s+)?(?:a\\s+)?bid(?:\\s+of)?\\s*[:=]?\\s*\\$?([0-9][0-9,]*(?:\\.[0-9]+)?\\s*[kmbt]?)");
    private static final Pattern OUTGOING = Pattern.compile(
            "(?i)you\\s+(?:paid|payed|sent|spent)\\s+[A-Za-z0-9_]{3,16}[^0-9$]*\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?\\s*[kmbt]?)");
    private static final Pattern WORTH_VALUE = Pattern.compile(
            "(?i)(?:worth|value|price)[^0-9$]*\\$?([0-9][0-9,]*(?:\\.[0-9]+)?\\s*[kmbt]?)");

    private DonutSmp() {}

    // ---- Session-only state (not persisted) ----
    private static final long SESSION_START = System.currentTimeMillis();
    private static double sessionEarned = 0.0;
    private static final Deque<String> BID_LOG = new ArrayDeque<>();
    private static String lastResult = "";
    private static long lastResultUntil = 0L;
    private static double shownBalance = Double.NaN;
    private static long lastFrameNanos = 0L;
    private static long lastBidAt = 0L;
    private static long lastExtendAt = 0L;
    private static final java.util.Map<String, net.minecraft.item.ItemStack> ICONS = new java.util.HashMap<>();
    private static boolean dirty = false;
    private static int flushTicks = 0;

    /** Chat lines can arrive in bursts; write the config at most about once a second. */
    private static void markDirty() { dirty = true; }

    public static void onChat(Text message) {
        if (message == null) return;
        String line = message.getString();
        Config c = Config.get();

        if (c.donutWorthPending && System.currentTimeMillis() <= c.donutWorthPendingUntil) {
            Matcher worth = WORTH_VALUE.matcher(line);
            if (worth.find()) {
                double value = amount(worth.group(1));
                if (Double.isFinite(value)) {
                    c.donutAuctionItemWorth = value;
                    c.donutAuctionItemWorthKnown = true;
                    c.donutWorthPending = false;
                    markDirty();
                    notifyLocal("§d[XMCore] §fServer worth: §a$" + money(value) + " §fper item.");
                }
                return;
            }
        }

        if (!(Modules.enabled("donut_balance") || Modules.enabled("donut_earnings")
                || Modules.enabled("donut_top_earners") || Modules.enabled("donut_goal_alert")
                || Modules.enabled("donut_auction"))) return;

        Matcher balance = BALANCE.matcher(line);
        if (balance.find()) {
            double value = amount(balance.group(1));
            if (Double.isFinite(value)) {
                c.donutCurrentBalance = value;
                if (!c.donutBalanceCaptured) {
                    c.donutStartingBalance = value;
                    c.donutBalanceCaptured = true;
                }
                markDirty();
            }
            return;
        }

        Matcher bid = BID_EVENT.matcher(line);
        if (bid.find()) {
            double value = amount(bid.group(2));
            if (Modules.enabled("donut_auction") && c.donutAuctionActive && Double.isFinite(value) && value >= c.donutMinimumBid) {
                recordBid(c, bid.group(1), value);
                markDirty();
            }
            return;
        }

        // Pay-to-bid: while an auction is running, money sent to you counts as a bid under your minimum.
        if (Modules.enabled("donut_auction") && c.donutAuctionActive) {
            for (int i = 0; i < INCOMING_PATTERNS.length; i++) {
                Matcher pay = INCOMING_PATTERNS[i].matcher(line);
                if (!pay.find()) continue;
                String payer = i == 1 ? pay.group(2) : pay.group(1);
                double value = amount(i == 1 ? pay.group(1) : pay.group(2));
                if (Double.isFinite(value) && value > 0) {
                    if (value >= c.donutMinimumBid) {
                        recordBid(c, payer, value);
                    } else {
                        pushLog(payer + " - $" + money(value) + " (under min)");
                    }
                    markDirty();
                }
                break; // keep going so the earnings tracker still sees the payment
            }
        }

        if (Modules.enabled("donut_earnings") && c.donutLiveAutoTrack) {
            for (int i = 0; i < INCOMING_PATTERNS.length; i++) {
                Matcher incoming = INCOMING_PATTERNS[i].matcher(line);
                if (!incoming.find()) continue;
                String player = i == 1 ? incoming.group(2) : incoming.group(1);
                String rawAmount = i == 1 ? incoming.group(1) : incoming.group(2);
                double value = amount(rawAmount);
                if (Double.isFinite(value) && value > 0) {
                    c.donutTotalEarned += value;
                    sessionEarned += value;
                    c.donutCurrentBalance += value;
                    c.donutLastPayment = value;
                    c.donutLastPayer = player;
                    c.donutPaymentCount++;
                    c.donutPlayerEarnings.merge(player, value, Double::sum);
                    checkGoal(c);
                    markDirty();
                }
                return;
            }
        }

        Matcher outgoing = OUTGOING.matcher(line);
        if (outgoing.find()) {
            double value = amount(outgoing.group(1));
            if (Double.isFinite(value) && value > 0) {
                c.donutCurrentBalance = Math.max(0, c.donutCurrentBalance - value);
                markDirty();
            }
        }
    }

    private static void pushLog(String line) {
        BID_LOG.addFirst(line);
        while (BID_LOG.size() > 5) BID_LOG.removeLast();
    }

    private static void recordBid(Config c, String bidder, double value) {
        c.donutBidCount++;
        lastBidAt = System.currentTimeMillis();
        pushLog(bidder + " - $" + money(value));
        if (value > c.donutHighestBid) {
            c.donutHighestBid = value;
            c.donutHighestBidder = bidder;
            if (c.donutAntiSnipe) {
                long remaining = c.donutAuctionEndAt - System.currentTimeMillis();
                if (remaining > 0 && remaining <= 10_000L) {
                    c.donutAuctionEndAt += Math.max(0, c.donutAntiSnipeSeconds) * 1000L;
                    lastExtendAt = System.currentTimeMillis();
                }
            }
        }
    }

    public static void requestWorth(net.minecraft.item.ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() == null) return;
        Config c = Config.get();
        String itemId = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).getPath();
        c.donutAuctionItemId = itemId;
        c.donutAuctionItemName = stack.getName().getString();
        c.donutAuctionItemWorthKnown = false;
        c.donutWorthPending = true;
        c.donutWorthPendingUntil = System.currentTimeMillis() + 10_000L;
        markDirty();
        mc.getNetworkHandler().sendChatCommand("worth " + itemId);
        notifyLocal("§d[XMCore] §fChecking server worth for §d" + c.donutAuctionItemName + "§f...");
    }

    public static void startAuction(net.minecraft.item.ItemStack stack) {
        Config c = Config.get();
        if (!Modules.enabled("donut_auction")) {
            notifyLocal("§d[XMCore] §fEnable Auction Tracker in XMCore first.");
            return;
        }
        if (stack == null || stack.isEmpty()) return;
        if (c.donutAuctionActive) {
            notifyLocal("§d[XMCore] §fAn auction tracker is already running.");
            return;
        }
        c.donutAuctionItemName = stack.getName().getString();
        c.donutAuctionItemId = net.minecraft.registry.Registries.ITEM.getId(stack.getItem()).getPath();
        c.donutAuctionActive = true;
        c.donutAuctionDurationSeconds = Math.max(5, Math.min(3600, c.donutAuctionDurationSeconds));
        c.donutAuctionEndAt = System.currentTimeMillis() + c.donutAuctionDurationSeconds * 1000L;
        c.donutHighestBid = 0;
        c.donutHighestBidder = "";
        c.donutBidCount = 0;
        BID_LOG.clear();
        lastResultUntil = 0L;
        markDirty();
        notifyLocal("§d[XMCore] §fAuction tracker started for §d" + c.donutAuctionItemName + "§f.");
    }

    public static void toggleAuction() {
        Config c = Config.get();
        if (!Modules.enabled("donut_auction")) {
            MinecraftClient.getInstance().inGameHud.getChatHud().addMessage(
                    Text.literal("§d[XMCore] §fEnable the DonutSMP module first."));
            return;
        }
        if (c.donutAuctionActive) {
            finishAuction();
            return;
        }
        c.donutAuctionActive = true;
        c.donutAuctionDurationSeconds = Math.max(5, Math.min(3600, c.donutAuctionDurationSeconds));
        c.donutAuctionEndAt = System.currentTimeMillis() + c.donutAuctionDurationSeconds * 1000L;
        c.donutHighestBid = 0;
        c.donutHighestBidder = "";
        c.donutBidCount = 0;
        BID_LOG.clear();
        lastResultUntil = 0L;
        markDirty();
        notifyLocal("§d[XMCore] §fDonutSMP auction started.");
    }

    private static void finishAuction() {
        Config c = Config.get();
        if (!c.donutAuctionActive) return;
        c.donutAuctionActive = false;
        markDirty();
        lastResultUntil = System.currentTimeMillis() + 20_000L;
        lastResult = c.donutHighestBidder.isBlank() ? "No bids" : c.donutHighestBidder + " won at $" + money(c.donutHighestBid);
        if (c.donutHighestBidder.isBlank()) {
            notifyLocal("§d[XMCore] §fAuction ended with no tracked bids.");
        } else {
            notifyLocal("§d[XMCore] §fAuction winner: §d" + c.donutHighestBidder
                    + " §fwith §a$" + money(c.donutHighestBid) + " §f(" + c.donutBidCount + " bids).");
        }
    }

    public static void tick() {
        Config c = Config.get();
        if (dirty && ++flushTicks >= 20) {
            dirty = false;
            flushTicks = 0;
            Config.save();
        }
        if (c.donutWorthPending && System.currentTimeMillis() > c.donutWorthPendingUntil) {
            c.donutWorthPending = false;
            markDirty();
            notifyLocal("§d[XMCore] §fCould not read the server worth reply. Try checking again.");
        }
        if (c.donutAuctionActive && System.currentTimeMillis() >= c.donutAuctionEndAt) {
            finishAuction();
        }
    }

    private static void notifyLocal(String text) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null) mc.inGameHud.getChatHud().addMessage(Text.literal(text));
    }

    private static void checkGoal(Config c) {
        if (c.donutGoalAmount <= 0 || c.donutGoalNotified || c.donutTotalEarned < c.donutGoalAmount) return;
        c.donutGoalNotified = true;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.inGameHud != null) {
            mc.inGameHud.getChatHud().addMessage(Text.literal("§d[XMCore] §fDonutSMP earnings goal reached!"));
        }
    }

    private static double amount(String raw) {
        String value = raw.toLowerCase(Locale.ROOT).replace(",", "").replace(" ", "").replace("$", "");
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
        try {
            return Double.parseDouble(value) * multiplier;
        } catch (NumberFormatException ignored) {
            return Double.NaN;
        }
    }

    private static String money(double value) {
        if (value >= 1_000_000_000_000.0) return String.format(Locale.US, "%.2fT", value / 1_000_000_000_000.0);
        if (value >= 1_000_000_000.0) return String.format(Locale.US, "%.2fB", value / 1_000_000_000.0);
        if (value >= 1_000_000.0) return String.format(Locale.US, "%.2fM", value / 1_000_000.0);
        if (value >= 1_000.0) return String.format(Locale.US, "%.2fK", value / 1_000.0);
        return String.format(Locale.US, "%.0f", value);
    }

    private static String rate(double earned, long sinceMs) {
        if (sinceMs < 30_000L || earned <= 0) return "calculating...";
        return "$" + money(earned / (sinceMs / 3_600_000.0)) + "/hr";
    }

    /** Eased value so counters glide to new numbers instead of jumping. */
    private static double glide(double target) {
        long now = System.nanoTime();
        double dt = lastFrameNanos == 0 ? 0.016 : Math.min(0.1, (now - lastFrameNanos) / 1e9);
        lastFrameNanos = now;
        if (Double.isNaN(shownBalance) || Math.abs(target - shownBalance) < 0.5) shownBalance = target;
        else shownBalance += (target - shownBalance) * (1.0 - Math.exp(-dt * 9.0));
        return shownBalance;
    }

    public static void renderBalance(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity player) {
        Config c = Config.get();
        double shown = glide(c.donutCurrentBalance);
        double diff = c.donutCurrentBalance - c.donutStartingBalance;
        Hud.panel(ctx, "DonutSMP Balance", Hud.AQUA,
                Hud.Row.of("Balance: $" + money(shown), Hud.WHITE),
                Hud.Row.of("Session: " + (diff >= 0 ? "+$" : "-$") + money(Math.abs(diff)), diff >= 0 ? Hud.GREEN : Hud.RED),
                Hud.Row.of("Started: $" + money(c.donutStartingBalance), Hud.GRAY));
    }

    public static void renderEarnings(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity player) {
        Config c = Config.get();
        java.util.List<Hud.Row> rows = new java.util.ArrayList<>();
        if (c.donutLiveShowTotal)
            rows.add(Hud.Row.of("Total earned: $" + money(c.donutTotalEarned), Hud.WHITE));
        if (c.donutLiveShowLastPayment)
            rows.add(Hud.Row.of("Last payment: $" + money(c.donutLastPayment), Hud.GREEN));
        if (c.donutLiveShowPayer && !c.donutLastPayer.isBlank())
            rows.add(Hud.Row.of("From: " + c.donutLastPayer, Hud.GRAY));
        if (c.donutLiveShowPaymentCount) {
            rows.add(Hud.Row.of("Payments: " + c.donutPaymentCount, Hud.AQUA));
            if (c.donutPaymentCount > 0)
                rows.add(Hud.Row.of("Average: $" + money(c.donutTotalEarned / c.donutPaymentCount), Hud.GRAY));
        }
        rows.add(Hud.Row.of("Rate: " + rate(sessionEarned, System.currentTimeMillis() - SESSION_START), Hud.YELLOW));
        if (c.donutGoalAmount > 0) {
            float frac = (float) Math.min(1.0, c.donutTotalEarned / c.donutGoalAmount);
            rows.add(Hud.Row.bar("Goal: " + Math.round(frac * 100) + "% of $" + money(c.donutGoalAmount),
                    frac, frac >= 1f ? Hud.GREEN : Hud.AQUA));
        }
        Hud.panel(ctx, "DonutSMP Live Tracker", Hud.GREEN, rows.toArray(new Hud.Row[0]));
    }

    public static void renderTopEarners(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity player) {
        Config c = Config.get();
        var top = c.donutPlayerEarnings.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(5)
                .toList();
        if (top.isEmpty()) {
            Hud.panel(ctx, "DonutSMP Top Earners", Hud.GOLD,
                    Hud.Row.of("Waiting for payment messages", Hud.GRAY));
            return;
        }
        double best = Math.max(1.0, top.get(0).getValue());
        int[] medal = {Hud.GOLD, 0xFFD5DBE8, 0xFFE0A070, Hud.WHITE, Hud.WHITE};
        Hud.Row[] rows = new Hud.Row[top.size()];
        for (int i = 0; i < top.size(); i++) {
            Map.Entry<String, Double> entry = top.get(i);
            rows[i] = Hud.Row.bar((i + 1) + ". " + entry.getKey() + ": $" + money(entry.getValue()),
                    (float) (entry.getValue() / best), medal[i]);
        }
        Hud.panel(ctx, "DonutSMP Top Earners", Hud.GOLD, rows);
    }

    public static void renderAuction(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity player) {
        Config c = Config.get();
        long now = System.currentTimeMillis();
        if (!c.donutAuctionActive) {
            if (now < lastResultUntil) {
                Hud.panel(ctx, "DonutSMP Auction", Hud.GREEN,
                        Hud.Row.of("Auction ended", Hud.GREEN),
                        Hud.Row.of("Item: " + c.donutAuctionItemName, Hud.WHITE),
                        Hud.Row.of("Result: " + lastResult, Hud.GOLD),
                        Hud.Row.of("Bids: " + c.donutBidCount, Hud.GRAY));
                return;
            }
            Hud.panel(ctx, "DonutSMP Auction", Hud.GOLD,
                    Hud.Row.of("No auction is currently running", Hud.GRAY),
                    Hud.Row.of("Open the auction menu to choose an item", Hud.WHITE));
            return;
        }
        paintLiveAuction(ctx, c, now);
    }

    private static net.minecraft.item.ItemStack icon(String id) {
        return ICONS.computeIfAbsent(id, k -> {
            try {
                var item = net.minecraft.registry.Registries.ITEM.get(net.minecraft.util.Identifier.of("minecraft", k));
                return new net.minecraft.item.ItemStack(item);
            } catch (Exception e) {
                return net.minecraft.item.ItemStack.EMPTY;
            }
        });
    }

    private static int alpha(int rgb, float a) {
        return ((int) (Math.max(0f, Math.min(1f, a)) * 255) << 24) | (rgb & 0xFFFFFF);
    }

    private static void paintLiveAuction(DrawContext ctx, Config c, long now) {
        net.minecraft.client.font.TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        long left = Math.max(0L, c.donutAuctionEndAt - now);
        long total = Math.max(1L, c.donutAuctionDurationSeconds * 1000L);
        float frac = Math.min(1f, left / (float) total);
        boolean urgent = left <= 10_000L;
        boolean critical = left <= 5_000L;
        float pulse = (float) (0.5 + 0.5 * Math.sin(now / 140.0));
        int timeColor = critical ? Hud.RED : urgent ? Hud.YELLOW : Hud.GREEN;
        boolean hasWorth = c.donutAuctionItemWorthKnown && c.donutAuctionItemWorth > 0;
        boolean extended = now - lastExtendAt < 2500L;
        float bidFlash = 1f - Math.min(1f, (now - lastBidAt) / 700f);
        int n = BID_LOG.size();

        int w = 176;
        int h = Hud.titleHeight() + 24 + 26 + 8 + 30 + (hasWorth ? 15 : 0) + (n > 0 ? 3 + n * 10 : 0) + 14 + 6;

        Hud.custom(ctx, "DonutSMP Live Auction", Hud.GOLD, w, h, (g, x, y, cw, ch) -> {
            int pad = Hud.padding();
            int inner = cw - pad * 2;
            int cy = y + 3;

            // Item row: icon, name, worth.
            net.minecraft.item.ItemStack stack = icon(c.donutAuctionItemId);
            g.fill(x + pad, cy, x + pad + 20, cy + 20, 0xFF1A2236);
            if (!stack.isEmpty()) g.drawItem(stack, x + pad + 2, cy + 2);
            String name = c.donutAuctionItemName.isBlank() ? "No item selected" : c.donutAuctionItemName;
            while (name.length() > 3 && tr.getWidth(name) > inner - 26) name = name.substring(0, name.length() - 1);
            g.drawTextWithShadow(tr, name, x + pad + 26, cy + 1, Hud.WHITE);
            g.drawTextWithShadow(tr, hasWorth ? "Worth $" + money(c.donutAuctionItemWorth) : "Worth unknown",
                    x + pad + 26, cy + 11, hasWorth ? Hud.AQUA : Hud.GRAY);
            cy += 24;

            // Big countdown with a draining bar.
            String t = String.format("%d:%02d", left / 60_000L, (left / 1000L) % 60L);
            int tColor = critical ? (pulse > 0.5f ? Hud.RED : 0xFFFF9AAA) : timeColor;
            var m = g.getMatrices();
            m.pushMatrix();
            m.translate((float) (x + pad), (float) cy);
            m.scale(2f, 2f);
            g.drawTextWithShadow(tr, t, 0, 0, tColor);
            m.popMatrix();
            String tag = left == 0 ? "ENDING" : extended ? "+" + c.donutAntiSnipeSeconds + "s EXTENDED" : critical ? "LAST CALL" : urgent ? "ENDING SOON" : "";
            if (!tag.isEmpty()) g.drawTextWithShadow(tr, tag, x + cw - pad - tr.getWidth(tag), cy + 4, extended ? Hud.AQUA : timeColor);
            cy += 20;
            g.fill(x + pad, cy, x + cw - pad, cy + 4, 0xFF232C42);
            int fill = Math.round(inner * frac);
            if (fill > 0) g.fill(x + pad, cy, x + pad + fill, cy + 4, tColor);
            if (fill > 1) g.fill(x + pad + fill - 1, cy, x + pad + fill, cy + 4, 0xFFFFFFFF);
            cy += 8;

            // Highest bid block (flashes on a new bid).
            if (bidFlash > 0f) g.fill(x + pad - 3, cy - 1, x + cw - pad + 3, cy + 27, alpha(Hud.GREEN, bidFlash * 0.25f));
            boolean has = !c.donutHighestBidder.isBlank();
            g.drawTextWithShadow(tr, "TOP PAYMENT", x + pad, cy, Hud.GRAY);
            if (bidFlash > 0f) {
                String nb = "NEW PAYMENT";
                g.drawTextWithShadow(tr, nb, x + cw - pad - tr.getWidth(nb), cy, Hud.GREEN);
            } else {
                String cnt = c.donutBidCount + (c.donutBidCount == 1 ? " payment" : " payments");
                g.drawTextWithShadow(tr, cnt, x + cw - pad - tr.getWidth(cnt), cy, Hud.GRAY);
            }
            cy += 10;
            String amount = has ? "$" + money(c.donutHighestBid) : "Waiting for payments";
            m.pushMatrix();
            m.translate((float) (x + pad), (float) cy);
            m.scale(1.5f, 1.5f);
            g.drawTextWithShadow(tr, amount, 0, 0, has ? Hud.GREEN : Hud.GRAY);
            m.popMatrix();
            if (has) {
                String who = c.donutHighestBidder;
                g.drawTextWithShadow(tr, who, x + cw - pad - tr.getWidth(who), cy + 3, Hud.WHITE);
            } else {
                String min = "min $" + money(c.donutMinimumBid);
                g.drawTextWithShadow(tr, min, x + cw - pad - tr.getWidth(min), cy + 3, Hud.GRAY);
            }
            cy += 20;

            // Bid vs worth.
            if (hasWorth) {
                double pct = c.donutHighestBid / c.donutAuctionItemWorth;
                g.fill(x + pad, cy + 8, x + cw - pad, cy + 10, 0xFF232C42);
                int f = (int) Math.round(inner * Math.min(1.0, pct));
                int pc = pct >= 1.0 ? Hud.GREEN : pct >= 0.7 ? Hud.YELLOW : Hud.AQUA;
                if (f > 0) g.fill(x + pad, cy + 8, x + pad + f, cy + 10, pc);
                String ps = Math.round(pct * 100) + "% of worth";
                g.drawTextWithShadow(tr, ps, x + pad, cy - 1, pc);
                cy += 15;
            }

            // Recent bids.
            if (n > 0) {
                cy += 3;
                int i = 0;
                for (String line : BID_LOG) {
                    g.drawTextWithShadow(tr, line, x + pad, cy, i == 0 ? Hud.WHITE : Hud.GRAY);
                    cy += 10;
                    i++;
                }
            }
            cy += 3;
            g.fill(x + pad, cy - 2, x + cw - pad, cy - 1, 0x2EFFFFFF);
            String foot = (c.donutAntiSnipe ? "Anti-snipe on" : "Anti-snipe off")
                    + (c.donutMinimumBid > 0 ? "  -  min $" + money(c.donutMinimumBid) : "");
            g.drawTextWithShadow(tr, foot, x + pad, cy + 1, Hud.GRAY);

            // Urgent pulsing frame around the whole card.
            if (urgent) {
                int fc = alpha(timeColor, 0.25f + 0.55f * pulse);
                int top = y - Hud.titleHeight();
                g.fill(x, top, x + cw, top + 1, fc);
                g.fill(x, y + ch - 1, x + cw, y + ch, fc);
                g.fill(x, top, x + 1, y + ch, fc);
                g.fill(x + cw - 1, top, x + cw, y + ch, fc);
            }
        });
    }

    public static void renderStreamInfo(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity player) {
        long mins = (System.currentTimeMillis() - SESSION_START) / 60_000L;
        Hud.panel(ctx, "DonutSMP Stream Info", Config.get().accentColor,
                Hud.Row.of("Player: " + player.getName().getString(), Hud.WHITE),
                Hud.Row.of("Session: " + (mins / 60) + "h " + (mins % 60) + "m", Hud.GRAY),
                Hud.Row.of("Earned: $" + money(sessionEarned), Hud.GREEN));
    }
}
