package com.practicehud.mixin;

import com.practicehud.Modules;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.Items;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Fast XP: removes the right-click repeat delay while holding an XP bottle. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientFastXpMixin {
    @Shadow private int itemUseCooldown;

    @Inject(method = "doItemUse", at = @At("HEAD"), require = 0)
    private void xmcore$fastXp(CallbackInfo ci) {
        MinecraftClient mc = (MinecraftClient) (Object) this;
        if (mc.player == null || !Modules.enabled("fast_xp")) return;
        if (mc.player.getMainHandStack().isOf(Items.EXPERIENCE_BOTTLE)
                || mc.player.getOffHandStack().isOf(Items.EXPERIENCE_BOTTLE)) {
            itemUseCooldown = 0;
        }
    }
}
