package technograpple;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;

record SunView(float pathRotation, boolean bslProcedural, boolean roundSun) {
    void encode(FriendlyByteBuf buffer) {
        buffer.writeFloat(pathRotation);
        buffer.writeBoolean(bslProcedural);
        buffer.writeBoolean(roundSun);
    }

    static SunView decode(FriendlyByteBuf buffer) {
        return new SunView(buffer.readFloat(), buffer.readBoolean(), buffer.readBoolean());
    }

    static void receive(SunView view, ServerPlayer player) {
        if (player != null && Float.isFinite(view.pathRotation())
                && Math.abs(view.pathRotation()) <= 85 && TechnoGrapple.isGauntlet(player.getMainHandItem())) {
            Grapple.setSunView(player, view);
        }
    }
}
