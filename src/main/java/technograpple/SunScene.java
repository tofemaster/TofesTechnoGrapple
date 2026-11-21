package technograpple;

import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

record SunScene(UUID player, int elapsed, boolean reversing) implements CustomPacketPayload {
    static final Type<SunScene> TYPE = new Type<>(Identifier.fromNamespaceAndPath(TechnoGrapple.ID, "sun_scene"));
    static final StreamCodec<RegistryFriendlyByteBuf, SunScene> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SunScene::player,
            ByteBufCodecs.VAR_INT, SunScene::elapsed,
            ByteBufCodecs.BOOL, SunScene::reversing,
            SunScene::new);

    @Override
    public Type<SunScene> type() {
        return TYPE;
    }
}
