package technograpple;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

record SunView(float pathRotation, boolean bslProcedural, boolean roundSun) implements CustomPacketPayload {
    static final Type<SunView> TYPE = new Type<>(Identifier.fromNamespaceAndPath(TechnoGrapple.ID, "sun_view"));
    static final StreamCodec<RegistryFriendlyByteBuf, SunView> CODEC =
            StreamCodec.composite(ByteBufCodecs.FLOAT, SunView::pathRotation,
                    ByteBufCodecs.BOOL, SunView::bslProcedural,
                    ByteBufCodecs.BOOL, SunView::roundSun, SunView::new);

    static void receive(SunView view, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player && Float.isFinite(view.pathRotation())
                && Math.abs(view.pathRotation()) <= 85 && TechnoGrapple.isGauntlet(player.getMainHandItem())) {
            Grapple.setSunView(player, view);
        }
    }

    @Override
    public Type<SunView> type() {
        return TYPE;
    }
}
