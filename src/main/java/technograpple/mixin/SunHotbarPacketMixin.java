package technograpple.mixin;

import net.minecraft.network.protocol.game.ClientboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import technograpple.TechnoGrapple;

@Mixin(ServerGamePacketListenerImpl.class)
abstract class SunHotbarPacketMixin {
    @Shadow public ServerPlayer player;

    @Inject(method = "handleSetCarriedItem", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/player/Inventory;selected:I",
            opcode = org.objectweb.asm.Opcodes.GETFIELD), cancellable = true)
    private void technograpple$lockHotbar(ServerboundSetCarriedItemPacket packet, CallbackInfo ci) {
        if (TechnoGrapple.sunLocksHeldItem(player)) {
            player.connection.send(new ClientboundSetCarriedItemPacket(player.getInventory().selected));
            ci.cancel();
        }
    }
}
