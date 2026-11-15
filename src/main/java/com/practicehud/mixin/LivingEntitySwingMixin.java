package com.practicehud.mixin;

import com.practicehud.Config;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Changes only the local player's rendered hand-swing timeline. */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySwingMixin {
    @Shadow public int handSwingTicks;
    @Shadow public boolean handSwinging;
    @Shadow public float handSwingProgress;
    @Shadow public float lastHandSwingProgress;
    @Shadow private int getHandSwingDuration();

    @Unique
    private double practicehud$swingAccumulator;

    @Inject(method = "tickHandSwing", at = @At("HEAD"), cancellable = true)
    private void practicehud$adjustVisualSwingSpeed(CallbackInfo ci) {
        if (MinecraftClient.getInstance().player != (Object) this) return;

        double speed = Math.max(0.1, Math.min(5.0, Config.get().swingSpeed));
        if (Math.abs(speed - 1.0) < 0.0001) {
            practicehud$swingAccumulator = 0.0;
            return;
        }

        ci.cancel();
        lastHandSwingProgress = handSwingProgress;
        if (!handSwinging) {
            handSwingTicks = 0;
            practicehud$swingAccumulator = 0.0;
            handSwingProgress = 0.0f;
            return;
        }

        practicehud$swingAccumulator += speed;
        int advance = (int) practicehud$swingAccumulator;
        practicehud$swingAccumulator -= advance;
        handSwingTicks += advance;

        int duration = Math.max(1, getHandSwingDuration());
        if (handSwingTicks >= duration) {
            handSwingTicks = 0;
            handSwinging = false;
            practicehud$swingAccumulator = 0.0;
        }
        handSwingProgress = handSwingTicks / (float) duration;
    }
}
