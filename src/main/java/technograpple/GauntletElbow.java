package technograpple;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

public record GauntletElbow(ModelPart arm, @Nullable ModelPart sleeve, Quaternionf forearmRotation) {
    // Model units from the shoulder to the back edge of the third-person cuff.
    private static final float PIVOT = 4.0F;
    private static final float SEAM_OVERLAP = 0.75F;
    private static final Map<UUID, ArmGesture> GESTURES = new HashMap<>();
    public static final ThreadLocal<GauntletElbow> RENDERING = new ThreadLocal<>();

    public static boolean isAiming(HumanoidRenderState state) {
        return state instanceof AvatarRenderState avatar && !avatar.isSpectator
                && (!state.isFallFlying || GauntletClient.glideArmRaised(avatar.id)) && state.swimAmount <= 0
                && TechnoGrapple.isGauntlet(state.getMainHandItemStack())
                && (state.isFallFlying || !(state.attackTime > 0 && state.attackArm == state.mainArm));
    }

    static void clearGestures() {
        GESTURES.clear();
    }

    static void removeGesture(UUID player) {
        GESTURES.remove(player);
    }

    private record ArmGesture(long startedAt, GauntletClient.@Nullable ReleaseWhip whip, boolean dryFire) {
        float lift(long now) {
            if (whip != null) return whip.finished(now) ? 0 : 50 * Mth.DEG_TO_RAD * whip.whipAmount(now);
            float elapsed = (now - startedAt) / 1_000_000F;
            if (dryFire) return 2.5F * Mth.DEG_TO_RAD * Mth.sin(Mth.PI * Math.clamp(elapsed / 100, 0, 1));
            float rise = Math.clamp(elapsed / 40, 0, 1);
            float recovery = Math.clamp((elapsed - 40) / 110, 0, 1);
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

    public static Aim aim(HumanoidModel<?> model, AvatarRenderState state) {
        float pitch = state.isFallFlying ? state.xRot * Mth.DEG_TO_RAD : model.head.xRot;
        float yaw = model.head.yRot;
        var level = Minecraft.getInstance().level;
        if (level == null || !(level.getEntity(state.id) instanceof net.minecraft.world.entity.player.Player player)) {
            return new Aim(pitch, yaw);
        }
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

    public static GauntletElbow of(HumanoidModel<?> model, AvatarRenderState state) {
        HumanoidArm side = state.mainArm;
        ModelPart arm = model.getArm(side);
        ModelPart sleeve = model instanceof PlayerModel player
                ? (side == HumanoidArm.LEFT ? player.leftSleeve : player.rightSleeve) : null;
        Aim target = aim(model, state);
        float twist = -Mth.HALF_PI * upwardAim(target.pitch());
        var aim = new Quaternionf().rotationYXZ(target.yaw(), target.pitch() - Mth.HALF_PI, 0)
                .rotateY(twist).rotateLocalX(glideBodyPitch(state));
        var relative = new Quaternionf().rotationZYX(arm.zRot, arm.yRot, arm.xRot).invert().mul(aim);
        return new GauntletElbow(arm, sleeve, relative);
    }

    public static float glideBodyPitch(AvatarRenderState state) {
        // The entity renderer flips model Y, reversing the body tilt in model space.
        return state.isFallFlying ? state.fallFlyingScale() * (-Mth.HALF_PI - state.xRot * Mth.DEG_TO_RAD) : 0;
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

    public static void poseArm(HumanoidModel<?> model, AvatarRenderState state) {
        var arm = model.getArm(state.mainArm);
        Aim aim = aim(model, state);
        float upward = upwardAim(aim.pitch());
        float outward = state.mainArm == HumanoidArm.RIGHT ? 1 : -1;
        arm.xRot = aim.pitch() * 0.5F - Mth.HALF_PI * 0.5F - 78 * Mth.DEG_TO_RAD * upward;
        arm.yRot = aim.yaw() + outward * Mth.HALF_PI * upward;
        arm.zRot = 0;
        if (state.isFallFlying) {
            var shoulder = arm.getInitialPose();
            arm.setPos(shoulder.x() - outward * 0.8F, shoulder.y() - 0.7F, shoulder.z());
            var rotation = new Quaternionf().rotationX(glideBodyPitch(state))
                    .rotateYXZ(arm.yRot + outward * 12 * Mth.DEG_TO_RAD, arm.xRot, 0);
            if (((GlideArmView) state).technograpple$armOnly()) {
                // Lower the shoulder in frame while keeping the elbow near its original position.
                var elbow = rotation.transform(new org.joml.Vector3f(0, PIVOT, 0));
                rotation.rotateX(-55 * Mth.DEG_TO_RAD);
                elbow.sub(rotation.transform(new org.joml.Vector3f(0, PIVOT, 0)));
                elbow.x -= outward * 0.35F;
                arm.offsetPos(elbow);
            }
            var angles = rotation.getEulerAnglesZYX(new org.joml.Vector3f());
            arm.setRotation(angles.x, angles.y, angles.z);
        }
    }

    public record Mesh(List<ModelPart.Polygon> upper, List<ModelPart.Polygon> lower) {
        public static Mesh split(List<ModelPart.Cube> cubes) {
            // Vanilla skin, sleeve, and armor cuboids all cross the elbow plane.
            if (cubes.stream().anyMatch(cube -> cube.polygons.length != 6
                    || cube.minY >= PIVOT - SEAM_OVERLAP || cube.maxY <= PIVOT + SEAM_OVERLAP)) {
                return new Mesh(cubes.stream().flatMap(cube -> Arrays.stream(cube.polygons)).toList(), List.of());
            }
            return new Mesh(clip(cubes, true), clip(cubes, false));
        }

        public void render(GauntletElbow elbow, PoseStack.Pose pose, VertexConsumer buffer,
                           int light, int overlay, int color) {
            renderFaces(upper, pose, buffer, light, overlay, color);
            if (lower.isEmpty()) return;
            PoseStack forearm = new PoseStack();
            forearm.last().set(pose);
            elbow.transformForearm(forearm);
            renderFaces(lower, forearm.last(), buffer, light, overlay, color);
        }

        private static void renderFaces(List<ModelPart.Polygon> faces, PoseStack.Pose pose, VertexConsumer buffer,
                                        int light, int overlay, int color) {
            // Render the final polygons directly. Sodium can cache Cube geometry at construction.
            var normal = new Vector3f();
            var position = new Vector3f();
            for (var face : faces) {
                pose.transformNormal(face.normal(), normal);
                for (var vertex : face.vertices()) {
                    pose.pose().transformPosition(vertex.worldX(), vertex.worldY(), vertex.worldZ(), position);
                    buffer.addVertex(position.x(), position.y(), position.z(), color, vertex.u(), vertex.v(),
                            overlay, light, normal.x(), normal.y(), normal.z());
                }
            }
        }

        private static List<ModelPart.Polygon> clip(List<ModelPart.Cube> cubes, boolean upper) {
            float cut = PIVOT + (upper ? SEAM_OVERLAP : -SEAM_OVERLAP);
            var faces = new ArrayList<ModelPart.Polygon>(cubes.size() * 6);
            for (var cube : cubes) {
                for (var face : cube.polygons) {
                    var vertices = face.vertices().clone();
                    for (int i = 0; i < vertices.length; i++) {
                        var vertex = vertices[i];
                        if (upper ? vertex.y() > cut : vertex.y() < cut) {
                            vertices[i] = clipVertex(face, vertex, cut);
                        }
                    }
                    faces.add(new ModelPart.Polygon(vertices, face.normal()));
                }
            }
            return faces;
        }

        private static ModelPart.Vertex clipVertex(ModelPart.Polygon face, ModelPart.Vertex vertex, float cut) {
            // Vertical edges interpolate their UVs. End caps keep their original mapping.
            for (var opposite : face.vertices()) {
                if (opposite.x() == vertex.x() && opposite.z() == vertex.z() && opposite.y() != vertex.y()) {
                    float fraction = (cut - vertex.y()) / (opposite.y() - vertex.y());
                    return new ModelPart.Vertex(vertex.x(), cut, vertex.z(),
                            Mth.lerp(fraction, vertex.u(), opposite.u()), Mth.lerp(fraction, vertex.v(), opposite.v()));
                }
            }
            return new ModelPart.Vertex(vertex.x(), cut, vertex.z(), vertex.u(), vertex.v());
        }
    }
}
