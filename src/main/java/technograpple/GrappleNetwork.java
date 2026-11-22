package technograpple;

import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

final class GrappleNetwork {
    private static final String PROTOCOL = "1";
    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(TechnoGrapple.ID, "main"), () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static int nextId;

    private GrappleNetwork() {}

    static void register() {
        clientbound(RailState.class, RailState::encode, RailState::decode, packet -> GauntletClient.receive(packet));
        clientbound(CableRetraction.class, CableRetraction::encode, CableRetraction::decode, packet -> GauntletClient.receive(packet));
        clientbound(SunNight.class, SunNight::encode, SunNight::decode, packet -> GauntletClient.receive(packet));
        clientbound(SunWave.class, SunWave::encode, SunWave::decode, packet -> GauntletClient.receive(packet));
        clientbound(SunScene.class, SunScene::encode, SunScene::decode, packet -> GauntletClient.receive(packet));
        clientbound(SunShot.class, SunShot::encode, SunShot::decode, packet -> GauntletClient.receive(packet));
        clientbound(SunRelease.class, SunRelease::encode, SunRelease::decode, packet -> GauntletClient.receive(packet));
        clientbound(AmmoReload.class, AmmoReload::encode, AmmoReload::decode, packet -> GauntletClient.receive(packet));
        clientbound(TandemWhipCooldown.class, TandemWhipCooldown::encode, TandemWhipCooldown::decode, packet -> GauntletClient.receive(packet));
        clientbound(DartAmmo.class, DartAmmo::encode, DartAmmo::decode, packet -> GauntletClient.receive(packet));
        clientbound(GlideStatus.class, GlideStatus::encode, GlideStatus::decode,
                packet -> GauntletClient.receive(new GlideControl(packet.player(), packet.raised())));
        serverbound(GlideControl.class, GlideControl::encode, GlideControl::decode, GlideControl::receive);
        serverbound(SunView.class, SunView::encode, SunView::decode, SunView::receive);
        serverbound(SunReleasePress.class, SunReleasePress::encode, SunReleasePress::decode, SunReleasePress::receive);
        serverbound(MotionInput.class, MotionInput::encode, MotionInput::decode, Grapple::updateInput);
    }

    private static <T> void clientbound(Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder,
                                         Function<FriendlyByteBuf, T> decoder, Consumer<T> handler) {
        CHANNEL.registerMessage(nextId++, type, encoder, decoder, (packet, supplied) -> {
            var context = supplied.get();
            context.enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> handler.accept(packet)));
            context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    private static <T> void serverbound(Class<T> type, BiConsumer<T, FriendlyByteBuf> encoder,
                                         Function<FriendlyByteBuf, T> decoder, BiConsumer<T, ServerPlayer> handler) {
        CHANNEL.registerMessage(nextId++, type, encoder, decoder, (packet, supplied) -> {
            var context = supplied.get();
            ServerPlayer sender = context.getSender();
            if (sender != null) context.enqueueWork(() -> handler.accept(packet, sender));
            context.setPacketHandled(true);
        }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    static void sendToServer(Object packet) {
        CHANNEL.sendToServer(packet);
    }

    private static Object clientbound(Object packet) {
        return packet instanceof GlideControl glide ? new GlideStatus(glide.player(), glide.raised()) : packet;
    }

    static void sendToPlayer(ServerPlayer player, Object packet) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), clientbound(packet));
    }

    static void sendToPlayersTrackingEntity(Entity entity, Object packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> entity), clientbound(packet));
    }

    static void sendToPlayersTrackingEntityAndSelf(Entity entity, Object packet) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> entity), clientbound(packet));
    }

    static void sendToPlayersInDimension(ServerLevel level, Object packet) {
        CHANNEL.send(PacketDistributor.DIMENSION.with(level::dimension), clientbound(packet));
    }

    static void writeVec3(FriendlyByteBuf buffer, Vec3 value) {
        buffer.writeDouble(value.x);
        buffer.writeDouble(value.y);
        buffer.writeDouble(value.z);
    }

    static Vec3 readVec3(FriendlyByteBuf buffer) {
        return new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    private record GlideStatus(java.util.UUID player, boolean raised) {
        void encode(FriendlyByteBuf buffer) {
            buffer.writeUUID(player);
            buffer.writeBoolean(raised);
        }

        static GlideStatus decode(FriendlyByteBuf buffer) {
            return new GlideStatus(buffer.readUUID(), buffer.readBoolean());
        }
    }
}
