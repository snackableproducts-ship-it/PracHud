package com.practicehud.mixin;

import com.practicehud.Modules;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional client-side first-person fire overlay visibility setting. */
@Mixin(InGameOverlayRenderer.class)
public abstract class InGameOverlayVisualsMixin {
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void xmcore$hideFire(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                         Sprite sprite, CallbackInfo ci) {
        if (Modules.enabled("no_fire_overlay")) ci.cancel();
    }
}
