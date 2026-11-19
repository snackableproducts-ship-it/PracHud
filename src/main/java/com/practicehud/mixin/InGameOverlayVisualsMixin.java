package com.practicehud.mixin;

import com.practicehud.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.effect.StatusEffects;
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

    private static boolean xmcore$lowFirePushed;

    /** Low Fire: shifts the overlay down; hides it entirely while Fire Resistance is active. */
    @Inject(method = "renderFireOverlay", at = @At("HEAD"), cancellable = true)
    private static void xmcore$lowFire(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                       Sprite sprite, CallbackInfo ci) {
        if (!Modules.enabled("low_fire") || Modules.enabled("no_fire_overlay")) return;
        var player = MinecraftClient.getInstance().player;
        if (player != null && player.hasStatusEffect(StatusEffects.FIRE_RESISTANCE)) {
            ci.cancel();
            return;
        }
        matrices.push();
        matrices.translate(0.0f, -0.35f, 0.0f);
        xmcore$lowFirePushed = true;
    }

    @Inject(method = "renderFireOverlay", at = @At("RETURN"))
    private static void xmcore$lowFireEnd(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                          Sprite sprite, CallbackInfo ci) {
        if (xmcore$lowFirePushed) {
            matrices.pop();
            xmcore$lowFirePushed = false;
        }
    }
}
