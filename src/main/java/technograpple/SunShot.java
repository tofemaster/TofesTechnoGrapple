package technograpple;

import java.util.UUID;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

record SunShot(UUID player, Vec3 origin, Vec3 direction, int lockedSlot) implements CustomPacketPayload {
    static final double RANGE = 500;
    static final double SPEED = 6;
    static final Type<SunShot> TYPE = new Type<>(Identifier.fromNamespaceAndPath(TechnoGrapple.ID, "sun_shot"));
    static final StreamCodec<RegistryFriendlyByteBuf, SunShot> CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, SunShot::player, Vec3.STREAM_CODEC, SunShot::origin,
            Vec3.STREAM_CODEC, SunShot::direction,
            ByteBufCodecs.VAR_INT, SunShot::lockedSlot, SunShot::new);

    static boolean matches(ServerPlayer player, Vec3 origin, Vec3 direction, SunView view) {
        var level = player.level();
        boolean falling = !player.onGround() && (player.fallDistance > 0 || player.getDeltaMovement().y < 0);
        if (player.isFallFlying() || falling || player.isSwimming() || player.isInWater()
                || level.dimensionType().skybox() != DimensionType.Skybox.OVERWORLD || level.isRaining()
                || direction.y <= 0) return false;
        Vec3 sun = sunDirection(level, origin, view);
        if (sun.y <= 0 || direction.dot(sun) < Math.cos(Math.toRadians(2))) return false;
        double toSky = Math.max(0, level.getMaxY() + 1.0 - origin.y) / direction.y;
        // Require a clear path all the way above build height within the test shot's range.
        if (toSky > RANGE) return false;
        return level.clip(new ClipContext(origin, origin.add(direction.scale(toSky)),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.ANY, player)).getType() == HitResult.Type.MISS;
    }

    static Vec3 sunDirection(ServerLevel level, Vec3 origin, SunView view) {
        double angle = Math.toRadians(level.environmentAttributes().getValue(EnvironmentAttributes.SUN_ANGLE, origin, null));
        if (view.bslProcedural()) {
            // BSL remaps Iris's normalized sunAngle before drawing its procedural disk.
            double phase = fraction((Math.toDegrees(angle) + 90) / 360 - 0.033333333);
            double linear = phase < 0.433333333 ? phase * 1.15384615385 : phase * 0.882352941176 + 0.117647058824;
            double half = linear > 0.5 ? 1 : 0;
            double f = fraction(linear * 2);
            double blend = half == 0 ? 0.3 : -0.1;
            double timeAngle = (f * (1 - blend) + f * f * (3 - 2 * f) * blend + half) * 0.5;
            double cycle = fraction(timeAngle - 0.25);
            angle = (cycle + ((1 - Math.cos(cycle * Math.PI)) * 0.5 - cycle) / 3) * Math.TAU;
        }
        double tilt = Math.toRadians(view.pathRotation());
        double elevation = Math.cos(angle);
        return new Vec3(-Math.sin(angle), elevation * Math.cos(tilt), -elevation * Math.sin(tilt));
    }

    private static double fraction(double value) {
        return value - Math.floor(value);
    }

    Vec3 tip(double ticks) {
        return origin.add(direction.scale(Math.min(RANGE, SPEED * ticks)));
    }

    @Override
    public Type<SunShot> type() {
        return TYPE;
    }
}
