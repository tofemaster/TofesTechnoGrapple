package technograpple;

import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;

record GlideControl(UUID player, boolean raised) implements CustomPacketPayload {
    static final Type<GlideControl> TYPE = new Type<>(Identifier.fromNamespaceAndPath(TechnoGrapple.ID, "glide_control"));
    static final StreamCodec<RegistryFriendlyByteBuf, GlideControl> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, GlideControl::player,
            ByteBufCodecs.BOOL, GlideControl::raised,
            GlideControl::new);

    @Override
    public Type<GlideControl> type() {
        return TYPE;
    }

    static void receive(GlideControl input, net.neoforged.neoforge.network.handling.IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && player.isFallFlying()
                && TechnoGrapple.isGauntlet(player.getMainHandItem())) {
            if (input.raised()) Grapple.raiseGlidingArm(player);
            else player.stopFallFlying();
        }
    }
}
