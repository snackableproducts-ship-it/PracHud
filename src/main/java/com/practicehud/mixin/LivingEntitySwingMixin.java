package com.practicehud.mixin;

import com.practicehud.Config;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Changes only the local player's first-person arm swing animation duration. */
@Mixin(LivingEntity.class)
public abstract class LivingEntitySwingMixin {
    @Inject(method = "getHandSwingDuration", at = @At("RETURN"), cancellable = true)
    private void practicehud$changeLocalSwingDuration(CallbackInfoReturnable<Integer> cir) {
        if ((Object) this instanceof ClientPlayerEntity
                && Config.get().modules.getOrDefault("swing_speed", false)) {
            int duration = Config.get().swingSpeedTicks;
            cir.setReturnValue(Math.max(1, Math.min(10, duration)));
        }
    }
}
