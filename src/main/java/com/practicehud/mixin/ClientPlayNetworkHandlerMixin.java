package com.practicehud.mixin;

import com.practicehud.Combat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.EntityStatusS2CPacket;
import net.minecraft.network.packet.s2c.play.WorldTimeUpdateS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Read-only network hooks: totem pops (entity status 35) and the server-TPS estimate (interval
 * between world-time updates). Both are optional (require = 0): if a hook cannot attach on a given
 * game version the feature simply reports no data instead of stopping the client from starting.
 */
@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onEntityStatus", at = @At("HEAD"), require = 0)
    private void xmcore$entityStatus(EntityStatusS2CPacket packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        // Packet handlers run once on the network thread and again on the client thread; count once.
        if (mc == null || !mc.isOnThread() || mc.world == null) return;
        if (packet.getStatus() == 35) {
            Entity entity = packet.getEntity(mc.world);
            if (entity != null) Combat.onTotemPop(entity);
        }
    }

    @Inject(method = "onWorldTimeUpdate", at = @At("HEAD"), require = 0)
    private void xmcore$timeUpdate(WorldTimeUpdateS2CPacket packet, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || !mc.isOnThread()) return;
        Combat.onTimeUpdate();
    }
}
