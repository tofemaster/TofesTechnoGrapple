package technograpple;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import javax.annotation.Nullable;

public record GauntletElbow(ModelPart arm, @Nullable ModelPart sleeve, Quaternionf forearmRotation) {
    // Model units from the shoulder to the back edge of the third-person cuff.
    private static final float PIVOT = 4.0F;
    private static final float SEAM_OVERLAP = 0.75F;
    private static final Map<UUID, ArmGesture> GESTURES = new HashMap<>();
    public static final ThreadLocal<GauntletElbow> RENDERING = new ThreadLocal<>();

    public static boolean isAiming(Player player) {
        return !player.isSpectator() && !player.isVisuallySwimming()
                && (!player.isFallFlying() || GauntletClient.glideArmRaised(player.getId()))
                && TechnoGrapple.isGauntlet(player.getMainHandItem())
                && (player.isFallFlying() || !player.swinging || player.swingingArm != net.minecraft.world.InteractionHand.MAIN_HAND);
    }

    static void clearGestures() {
        GESTURES.clear();
    }

    static void removeGesture(UUID player) {
        GESTURES.remove(player);
    }

    private record ArmGesture(long startedAt, @Nullable GauntletClient.ReleaseWhip whip, boolean dryFire) {
        float lift(long now) {
            if (whip != null) return whip.finished(now) ? 0 : 50 * Mth.DEG_TO_RAD * whip.whipAmount(now);
            float elapsed = (now - startedAt) / 1_000_000F;
            if (dryFire) return 2.5F * Mth.DEG_TO_RAD * Mth.sin(Mth.PI * net.minecraft.util.Mth.clamp(elapsed / 100, 0, 1));
            float rise = net.minecraft.util.Mth.clamp(elapsed / 40, 0, 1);
            float recovery = net.minecraft.util.Mth.clamp((elapsed - 40) / 110, 0, 1);
            return 8.5F * Mth.DEG_TO_RAD * rise * (1 - recovery * recovery * (3 - 2 * recovery));
        }
    }

    static void startGesture(UUID player, boolean whip, long now) {
        GESTURES.put(player, new ArmGesture(now, whip ? new GauntletClient.ReleaseWhip(0, now) : null, false));
    }

    static void startDryFire(UUID player, long now) {
        GESTURES.put(player, new ArmGesture(now, null, true));
    }

    public record Aim(float pitch, float yaw) {
    }

    public static Aim aim(HumanoidModel<?> model, Player player) {
        float pitch = player.isFallFlying() ? player.getXRot() * Mth.DEG_TO_RAD : model.head.xRot;
        float yaw = model.head.yRot;
        long now = System.nanoTime();
        ArmGesture gesture = GESTURES.get(player.getUUID());
        float lift = (gesture == null ? 0 : gesture.lift(now))
                + 12 * Mth.DEG_TO_RAD * (GauntletClient.sunJolt(player.getUUID()) + 0.25F * SunEffects.tremor(player.getUUID()));
        var reload = GauntletClient.reloadMotion(player);
        if (reload != null && reload.ammo.hasOutgoing()) {
            float t = reload.progress();
            lift += 12 * Mth.DEG_TO_RAD * Math.max(0, 1 - Math.abs(t - 0.11F) / 0.11F);
            lift += 8 * Mth.DEG_TO_RAD * Math.max(0, 1 - Math.abs(t - AmmoReload.EJECT_AT) / 0.10F);
        }
        return new Aim(Math.max(-Mth.HALF_PI, pitch - lift), yaw);
    }

    public static GauntletElbow of(HumanoidModel<?> model, Player player) {
        HumanoidArm side = player.getMainArm();
        ModelPart arm = (side == HumanoidArm.LEFT ? model.leftArm : model.rightArm);
        ModelPart sleeve = model instanceof PlayerModel<?> skin
                ? (side == HumanoidArm.LEFT ? skin.leftSleeve : skin.rightSleeve) : null;
        Aim target = aim(model, player);
        float twist = -Mth.HALF_PI * upwardAim(target.pitch());
        var aim = new Quaternionf().rotationYXZ(target.yaw(), target.pitch() - Mth.HALF_PI, 0)
                .rotateY(twist).rotateLocalX(glideBodyPitch(player));
        var relative = new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot).invert().mul(aim);
        return new GauntletElbow(arm, sleeve, relative);
    }

    public static float glideBodyPitch(Player player) {
        // The entity renderer flips model Y, reversing the body tilt in model space.
        return player.isFallFlying() ? Mth.clamp(player.getFallFlyingTicks() * player.getFallFlyingTicks() / 100F, 0F, 1F) * (-Mth.HALF_PI - player.getXRot() * Mth.DEG_TO_RAD) : 0;
    }

    public static float upwardAim(float headPitch) {
        float amount = Mth.clamp(-headPitch / Mth.HALF_PI, 0, 1);
        return amount * amount * (3 - 2 * amount);
    }

    public void transformForearm(PoseStack pose) {
        pose.translate(0, PIVOT / 16, 0);
        pose.mulPose(forearmRotation);
        pose.translate(0, -PIVOT / 16, 0);
    }

    public static void poseArm(HumanoidModel<?> model, Player player) {
        var arm = player.getMainArm() == HumanoidArm.LEFT ? model.leftArm : model.rightArm;
        Aim aim = aim(model, player);
        float upward = upwardAim(aim.pitch());
        float outward = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        arm.xRot = aim.pitch() * 0.5F - Mth.HALF_PI * 0.5F - 78 * Mth.DEG_TO_RAD * upward;
        arm.yRot = aim.yaw() + outward * Mth.HALF_PI * upward;
        arm.zRot = 0;
        if (player.isFallFlying()) {
            var shoulder = arm.getInitialPose();
            arm.setPos(shoulder.x - outward * 0.8F, shoulder.y - 0.7F, shoulder.z);
            var rotation = new Quaternionf().rotationX(glideBodyPitch(player))
                    .rotateYXZ(arm.yRot + outward * 12 * Mth.DEG_TO_RAD, arm.xRot, 0);
            var angles = rotation.getEulerAnglesZYX(new org.joml.Vector3f());
            arm.setRotation(angles.x, angles.y, angles.z);
        }
    }

    private record Face(ModelPart.Vertex[] vertices, Vector3f normal) {}

    public record Mesh(List<Face> upper, List<Face> lower) {
        public static Mesh split(List<ModelPart.Cube> cubes) {
            // Vanilla skin, sleeve, and armor cuboids all cross the elbow plane.
            if (cubes.stream().anyMatch(cube -> cube.polygons.length != 6
                    || cube.minY >= PIVOT - SEAM_OVERLAP || cube.maxY <= PIVOT + SEAM_OVERLAP)) {
                return new Mesh(cubes.stream().flatMap(cube -> Arrays.stream(cube.polygons))
                        .map(face -> new Face(face.vertices, face.normal)).toList(), List.of());
            }
            return new Mesh(clip(cubes, true), clip(cubes, false));
        }

        public void render(GauntletElbow elbow, PoseStack.Pose pose, VertexConsumer buffer,
                           int light, int overlay, float red, float green, float blue, float alpha) {
            renderFaces(upper, pose, buffer, light, overlay, red, green, blue, alpha);
            if (lower.isEmpty()) return;
            PoseStack forearm = new PoseStack();
            forearm.last().pose().set(pose.pose());
            forearm.last().normal().set(pose.normal());
            elbow.transformForearm(forearm);
            renderFaces(lower, forearm.last(), buffer, light, overlay, red, green, blue, alpha);
        }

        private static void renderFaces(List<Face> faces, PoseStack.Pose pose, VertexConsumer buffer,
                                        int light, int overlay, float red, float green, float blue, float alpha) {
            // Render the final polygons directly. Sodium can cache Cube geometry at construction.
            var normal = new Vector3f();
            var position = new Vector3f();
            for (var face : faces) {
                pose.normal().transform(face.normal(), normal);
                for (var vertex : face.vertices()) {
                    pose.pose().transformPosition(new Vector3f(vertex.pos).div(16), position);
                    buffer.vertex(position.x, position.y, position.z).color(red, green, blue, alpha)
                            .uv(vertex.u, vertex.v).overlayCoords(overlay).uv2(light)
                            .normal(normal.x, normal.y, normal.z).endVertex();
                }
            }
        }

        private static List<Face> clip(List<ModelPart.Cube> cubes, boolean upper) {
            float cut = PIVOT + (upper ? SEAM_OVERLAP : -SEAM_OVERLAP);
            var faces = new ArrayList<Face>(cubes.size() * 6);
            for (var cube : cubes) {
                for (var face : cube.polygons) {
                    var vertices = face.vertices.clone();
                    for (int i = 0; i < vertices.length; i++) {
                        var vertex = vertices[i];
                        if (upper ? vertex.pos.y > cut : vertex.pos.y < cut) {
                            vertices[i] = clipVertex(face, vertex, cut);
                        }
                    }
                    faces.add(new Face(vertices, face.normal));
                }
            }
            return faces;
        }

        private static ModelPart.Vertex clipVertex(ModelPart.Polygon face, ModelPart.Vertex vertex, float cut) {
            // Vertical edges interpolate their UVs. End caps keep their original mapping.
            for (var opposite : face.vertices) {
                if (opposite.pos.x == vertex.pos.x && opposite.pos.z == vertex.pos.z && opposite.pos.y != vertex.pos.y) {
                    float fraction = (cut - vertex.pos.y) / (opposite.pos.y - vertex.pos.y);
                    return new ModelPart.Vertex(vertex.pos.x, cut, vertex.pos.z,
                            Mth.lerp(fraction, vertex.u, opposite.u), Mth.lerp(fraction, vertex.v, opposite.v));
                }
            }
            return new ModelPart.Vertex(vertex.pos.x, cut, vertex.pos.z, vertex.u, vertex.v);
        }
    }
}
