package com.practicehud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;

/** Persistent settings, stored as JSON in the Fabric config directory. */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance = new Config();

    public Map<String, Boolean> modules = new LinkedHashMap<>();

    // DonutSMP stream statistics (virtual server currency only).
    public boolean donutBalanceCaptured = false;
    public double donutStartingBalance = 0.0;
    public double donutCurrentBalance = 0.0;
    public double donutTotalEarned = 0.0;
    public double donutGoalAmount = 1_000_000.0;
    public boolean donutGoalNotified = false;
    public Map<String, Double> donutPlayerEarnings = new LinkedHashMap<>();
    public boolean donutLiveShowTotal = true;
    public boolean donutLiveShowLastPayment = true;
    public boolean donutLiveShowPayer = true;
    public boolean donutLiveShowPaymentCount = true;
    public boolean donutLiveAutoTrack = true;
    public double donutLastPayment = 0.0;
    public String donutLastPayer = "";
    public long donutPaymentCount = 0L;
    public boolean donutAuctionActive = false;
    public long donutAuctionEndAt = 0L;
    public int donutAuctionDurationSeconds = 60;
    public double donutMinimumBid = 0.0;
    public double donutHighestBid = 0.0;
    public String donutHighestBidder = "";
    public int donutBidCount = 0;
    public boolean donutAntiSnipe = true;
    public int donutAntiSnipeSeconds = 10;
    public String donutAuctionItemName = "";
    public String donutAuctionItemId = "";
    public double donutAuctionItemWorth = 0.0;
    public boolean donutAuctionItemWorthKnown = false;
    public boolean donutWorthPending = false;
    public long donutWorthPendingUntil = 0L;

    // Crosshair designer
    public int crosshairShape = 0; // 0 cross, 1 dot, 2 circle, 3 cross + dot
    public int crosshairSize = 6;
    public int crosshairGap = 2;
    public int crosshairThickness = 1;
    public boolean crosshairDynamic = true;
    public int crosshairColor = 0xFFFFFFFF;
    public int crosshairTargetColor = 0xFFFF3030;
    public boolean crosshairOutline = false;
    public int crosshairOutlineColor = 0xFF101010;
    public boolean crosshairShowWhileAiming = true;

    // Swing animation duration in ticks. Lower values make the local arm animation faster.
    public int swingSpeedTicks = 3;

    // Shared HUD anchor and scale, editable in the HUD editor.
    public int hudX = 4;
    public int hudY = 4;
    // Per-widget HUD positions keyed by panel title, stored as [x, y].
    public Map<String, int[]> hudPositions = new HashMap<>();
    public double hudScale = 1.0;
    public int accentColor = 0xFF55D6FF;

    // 0 Midnight, 1 Purple, 2 Ocean, 3 Crimson, 4 Light
    public int themeId = 1;

    // 0 = XMCore panel GUI, 1 = classic multi-column array GUI.
    public int guiStyle = 0;
    // Per-category offsets for freely arranging Classic Array panels.
    public Map<String, int[]> arrayGuiOffsets = new LinkedHashMap<>();

    // Target tracking ring
    public int ringRadius = 40;
    public int ringColor = 0xFF55FFFF;

    // Quick hotbar binds: which hotbar slot (0-8) each Quick Slot key selects
    public int[] quickSlots = {0, 1, 2, 3};

    // ---- Competitive Safe Mode ----
    // Disables modules flagged as restricted (freelook, middle-click utilities, hitboxes...) while
    // keeping purely visual HUDs on. Turns itself on automatically on the listed servers.
    public boolean safeMode = false;
    public boolean autoSafeMode = true;
    public java.util.List<String> safeServers = new java.util.ArrayList<>(java.util.List.of("hypixel.net", "minemen.club"));

    // ---- Module presets (name -> module id -> enabled) ----
    public Map<String, Map<String, Boolean>> presets = new LinkedHashMap<>();

    // ---- HUD editor ----
    public Map<String, Float> hudScales = new HashMap<>();
    public Map<String, Integer> hudColors = new HashMap<>();
    public boolean hudSnap = true;
    public int hudGrid = 4;

    // ---- Combat utilities ----
    public boolean crosshairCooldownArc = true;
    public boolean crosshairShieldAlert = true;
    public boolean totemChatAlert = true;
    public boolean middlePearl = true;
    public boolean middleXp = true;
    public boolean middleFirework = true;
    /** Saved hotbar layout (registry ids); empty string means "anything". */
    public String[] hotbarPreset = new String[]{"", "", "", "", "", "", "", "", ""};

    public static Config get() {
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("xmcore.json");
    }

    public static void load() {
        try {
            Path p = path();
            Path legacy = FabricLoader.getInstance().getConfigDir().resolve("practicehud.json");
            if (!Files.exists(p) && Files.exists(legacy)) p = legacy;
            if (Files.exists(p)) {
                Config c = GSON.fromJson(Files.readString(p), Config.class);
                if (c != null) {
                    if (c.modules == null) c.modules = new LinkedHashMap<>();
                    if (c.donutPlayerEarnings == null) c.donutPlayerEarnings = new LinkedHashMap<>();
                    if (c.hudPositions == null) c.hudPositions = new HashMap<>();
                    if (c.arrayGuiOffsets == null) c.arrayGuiOffsets = new LinkedHashMap<>();
                    if (c.quickSlots == null || c.quickSlots.length != 4) c.quickSlots = new int[]{0, 1, 2, 3};
                    if (c.safeServers == null) c.safeServers = new java.util.ArrayList<>(java.util.List.of("hypixel.net", "minemen.club"));
                    if (c.presets == null) c.presets = new LinkedHashMap<>();
                    if (c.hudScales == null) c.hudScales = new HashMap<>();
                    if (c.hudColors == null) c.hudColors = new HashMap<>();
                    if (c.hotbarPreset == null || c.hotbarPreset.length != 9) c.hotbarPreset = new String[]{"", "", "", "", "", "", "", "", ""};
                    for (int i = 0; i < 9; i++) if (c.hotbarPreset[i] == null) c.hotbarPreset[i] = "";
                    c.hudGrid = Math.max(1, Math.min(16, c.hudGrid));
                    if (c.swingSpeedTicks < 1 || c.swingSpeedTicks > 20) c.swingSpeedTicks = 6;
                    c.hudScale = Math.max(0.75, Math.min(1.5, c.hudScale));
                    c.guiStyle = Math.max(0, Math.min(1, c.guiStyle));
                    instance = c;
                }
            }
        } catch (Exception e) {
            PracticeHudClient.LOGGER.warn("Could not read config, using defaults", e);
        }
    }

    public static void save() {
        try {
            Files.writeString(path(), GSON.toJson(instance));
        } catch (IOException e) {
            PracticeHudClient.LOGGER.warn("Could not save config", e);
        }
    }
}
