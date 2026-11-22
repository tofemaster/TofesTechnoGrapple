package technograpple;

import net.minecraft.network.FriendlyByteBuf;
import java.util.UUID;

import net.minecraft.server.level.ServerPlayer;

record GlideControl(UUID player, boolean raised) {
    void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(player);
        buffer.writeBoolean(raised);
    }

    static GlideControl decode(FriendlyByteBuf buffer) {
        return new GlideControl(buffer.readUUID(), buffer.readBoolean());
    }

    static void receive(GlideControl input, ServerPlayer player) {
        if (player != null && player.isFallFlying()
                && TechnoGrapple.isGauntlet(player.getMainHandItem())) {
            if (input.raised()) Grapple.raiseGlidingArm(player);
            else player.stopFallFlying();
        }
    }
}
