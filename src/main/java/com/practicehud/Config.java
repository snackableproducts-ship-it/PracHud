package com.practicehud;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Persistent settings, stored as JSON in the Fabric config directory. */
public final class Config {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static Config instance = new Config();

    public Map<String, Boolean> modules = new LinkedHashMap<>();

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
    public double hudScale = 1.0;
    public int accentColor = 0xFF55D6FF;

    // Target tracking ring
    public int ringRadius = 40;
    public int ringColor = 0xFF55FFFF;

    // Quick hotbar binds: which hotbar slot (0-8) each Quick Slot key selects
    public int[] quickSlots = {0, 1, 2, 3};

    public static Config get() {
        return instance;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("practicehud.json");
    }

    public static void load() {
        try {
            Path p = path();
            if (Files.exists(p)) {
                Config c = GSON.fromJson(Files.readString(p), Config.class);
                if (c != null) {
                    if (c.modules == null) c.modules = new LinkedHashMap<>();
                    if (c.quickSlots == null || c.quickSlots.length != 4) c.quickSlots = new int[]{0, 1, 2, 3};
                    if (c.swingSpeedTicks < 1 || c.swingSpeedTicks > 10) c.swingSpeedTicks = 3;
                    c.hudScale = Math.max(0.75, Math.min(1.5, c.hudScale));
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
