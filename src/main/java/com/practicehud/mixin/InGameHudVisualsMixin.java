package com.practicehud.mixin;

import com.practicehud.Modules;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional client-side screen overlay visibility settings. */
@Mixin(InGameHud.class)
public abstract class InGameHudVisualsMixin {
    @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
    private void xmcore$hideVignette(DrawContext context, Entity entity, CallbackInfo ci) {
        if (Modules.enabled("no_vignette")) ci.cancel();
    }

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void xmcore$hidePortal(DrawContext context, float nauseaStrength, CallbackInfo ci) {
        if (Modules.enabled("no_portal_overlay")) ci.cancel();
    }

    @Inject(method = "renderNauseaOverlay", at = @At("HEAD"), cancellable = true)
    private void xmcore$hideNausea(DrawContext context, float nauseaStrength, CallbackInfo ci) {
        if (Modules.enabled("no_nausea_overlay")) ci.cancel();
    }
}
