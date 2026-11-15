package com.practicehud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.text.Text;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DonutSMP stream statistics.
 * Tracks only the server's virtual in-game balance; it never handles real money.
 */
public final class DonutSmp {
    private static final Pattern BALANCE = Pattern.compile(
            "(?i)(?:balance|you have|current balance)[^0-9$]*\\$?([0-9][0-9,]*(?:\\.[0-9]+)?\\s*[kmbt]?)");
    private static final Pattern INCOMING = Pattern.compile(
            "(?i)([A-Za-z0-9_]{3,16})\\s+(?:paid|payed|sent|gave)\\s+(?:you\\s+)?\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?\\s*[kmbt]?)");
    private static final Pattern OUTGOING = Pattern.compile(
            "(?i)you\\s+(?:paid|payed|sent|spent)\\s+[A-Za-z0-9_]{3,16}[^0-9$]*\\$?\\s*([0-9][0-9,]*(?:\\.[0-9]+)?\\s*[kmbt]?)");

    private DonutSmp() {}

    public static void onChat(Text message) {
        if (!Modules.enabled("donutsmp") || message == null) return;
        String line = message.getString();
        Config c = Config.get();

        Matcher balance = BALANCE.matcher(line);
        if (balance.find()) {
            double value = amount(balance.group(1));
            if (Double.isFinite(value)) {
                c.donutCurrentBalance = value;
                if (!c.donutBalanceCaptured) {
                    c.donutStartingBalance = value;
                    c.donutBalanceCaptured = true;
                }
                Config.save();
            }
            return;
        }

        Matcher incoming = INCOMING.matcher(line);
        if (incoming.find()) {
            String player = incoming.group(1);
            double value = amount(incoming.group(2));
            if (Double.isFinite(value) && value > 0) {
                c.donutTotalEarned += value;
                c.donutCurrentBalance += value;
                c.donutPlayerEarnings.merge(player, value, Double::sum);
                if (c.donutAuctionActive && value >= c.donutMinimumBid && value > c.donutHighestBid) {
                    c.donutHighestBid = value;
                    c.donutHighestBidder = player;
                    c.donutBidCount++;
                    if (c.donutAntiSnipe) {
                        long remaining = c.donutAuctionEndAt - System.currentTimeMillis();
                        if (remaining > 0 && remaining <= 10_000L) {
                            c.donutAuctionEndAt += Math.max(0, c.donutAntiSnipeSeconds) * 1000L;
                        }
                    }
                } else if (c.donutAuctionActive && value >= c.donutMinimumBid) {
                    c.donutBidCount++;
                }
                checkGoal(c);
                Config.save();
            }
            return;
        }

        Matcher outgoing = OUTGOING.matcher(line);
        if (outgoing.find()) {
            double value = amount(outgoing.group(1));
            if (Double.isFinite(value) && value > 0) {
                c.donutCurrentBalance = Math.max(0, c.donutCurrentBalance - value);
                Config.save();
            }
        }
    }

    public static void toggleAuction() {
        Config c = Config.get();
        if (!Modules.enabled("donutsmp")) {
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
        Config.save();
        notifyLocal("§d[XMCore] §fDonutSMP auction started.");
    }

    private static void finishAuction() {
        Config c = Config.get();
        if (!c.donutAuctionActive) return;
        c.donutAuctionActive = false;
        Config.save();
        if (c.donutHighestBidder.isBlank()) {
            notifyLocal("§d[XMCore] §fAuction ended with no tracked bids.");
        } else {
            notifyLocal("§d[XMCore] §fAuction winner: §d" + c.donutHighestBidder
                    + " §fwith §a$" + money(c.donutHighestBid) + " §f(" + c.donutBidCount + " bids).");
        }
    }

    public static void tick() {
        Config c = Config.get();
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

    public static void render(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity player) {
        Config c = Config.get();
        double progress = c.donutGoalAmount <= 0 ? 0 : Math.min(1.0, c.donutTotalEarned / c.donutGoalAmount);
        Hud.panel(ctx, "DonutSMP Balance", Hud.AQUA,
                Hud.Row.of("Balance: $" + money(c.donutCurrentBalance), Hud.WHITE),
                Hud.Row.of("Starting: $" + money(c.donutStartingBalance), Hud.GRAY),
                Hud.Row.of("Earned: $" + money(c.donutTotalEarned), Hud.GREEN),
                Hud.Row.bar("Goal: $" + money(c.donutGoalAmount), (float) progress, Hud.AQUA));

        var top = c.donutPlayerEarnings.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(5)
                .toList();
        if (top.isEmpty()) {
            Hud.panel(ctx, "DonutSMP Earnings", Hud.GOLD,
                    Hud.Row.of("Waiting for payment messages", Hud.GRAY));
        } else {
            Hud.Row[] rows = new Hud.Row[top.size()];
            for (int i = 0; i < top.size(); i++) {
                Map.Entry<String, Double> entry = top.get(i);
                rows[i] = Hud.Row.of((i + 1) + ". " + entry.getKey() + "  $" + money(entry.getValue()), Hud.WHITE);
            }
            Hud.panel(ctx, "DonutSMP Earnings", Hud.GOLD, rows);
        }

        if (c.donutAuctionActive) {
            long left = Math.max(0L, c.donutAuctionEndAt - System.currentTimeMillis());
            Hud.panel(ctx, "DonutSMP Live Auction", Hud.GOLD,
                    Hud.Row.of(String.format("Time left: %d:%02d", left / 60_000L, (left / 1000L) % 60L), Hud.YELLOW),
                    Hud.Row.of("Highest: " + (c.donutHighestBidder.isBlank() ? "No bids" : c.donutHighestBidder), Hud.WHITE),
                    Hud.Row.of("Bid: $" + money(c.donutHighestBid), Hud.GREEN),
                    Hud.Row.of("Bids: " + c.donutBidCount + "  Min: $" + money(c.donutMinimumBid), Hud.GRAY));
        }

        Hud.panel(ctx, "DonutSMP Stream Info", Config.get().accentColor,
                Hud.Row.of("Player: " + player.getName().getString(), Hud.WHITE),
                Hud.Row.of("Server: DonutSMP", Hud.GRAY),
                Hud.Row.of("Virtual balance tracker", Hud.AQUA));
    }
}
