package com.practicehud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayerEntity;

/** A toggleable HUD feature. */
public final class Module {
    @FunctionalInterface
    public interface Renderer {
        void render(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity player);
    }

    public final String id;
    public final String name;
    public final String category;
    public final String description;
    /** Restricted modules are held off while Competitive Safe Mode is active. */
    public final boolean restricted;
    private final Renderer renderer;

    public Module(String id, String name, String category, String description, Renderer renderer) {
        this(id, name, category, description, renderer, false);
    }

    public Module(String id, String name, String category, String description, Renderer renderer, boolean restricted) {
        this.id = id;
        this.name = name;
        this.category = category;
        this.description = description;
        this.renderer = renderer;
        this.restricted = restricted;
    }

    /** What the user has switched on, regardless of Safe Mode. */
    public boolean isEnabledRaw() {
        return Config.get().modules.getOrDefault(id, false);
    }

    /** Whether the module is actually running right now (respects Safe Mode). */
    public boolean isEnabled() {
        return isEnabledRaw() && !(restricted && Safe.active());
    }

    /** True when the user has this on but Safe Mode is holding it off. */
    public boolean isLocked() {
        return restricted && Safe.active();
    }

    public void toggle() {
        Config.get().modules.put(id, !isEnabledRaw());
        Config.save();
    }

    public void render(DrawContext ctx, MinecraftClient mc, ClientPlayerEntity player) {
        if (renderer == null) return;
        try {
            renderer.render(ctx, mc, player);
        } catch (Exception e) {
            // A buggy module must never crash the game; switch it off and log.
            PracticeHudClient.LOGGER.error("Module {} failed and was disabled", id, e);
            Config.get().modules.put(id, false);
        }
    }
}
