package technograpple;

import java.util.List;

import com.google.gson.JsonObject;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import org.joml.Matrix4f;
import technograpple.GauntletModels.Models;
import technograpple.GauntletModels.Node;
import technograpple.GauntletModels.Part;

final class GauntletModelParts {
    private static final float PI = (float) Math.PI;
    private static final float DEG_TO_RAD = PI / 180F;
    private static final float DOOR_HINGE = 0.625F / 16;

    private GauntletModelParts() {}

    static Node rail(JsonObject definition, Models models) {
        var front = models.get(new ResourceLocation(definition.get("front").getAsString()));
        var dart = models.get(new ResourceLocation(definition.get("dart").getAsString()));
        boolean port = definition.get("port").getAsBoolean();
        int rail = port ? RailState.PORT : RailState.STARBOARD;
        var rope = models.item(port ? "rope_hook_docked_port" : "rope_hook_docked_starboard");
        var left = models.item("gauntlet_hazard_door_left");
        var right = models.item("gauntlet_hazard_door_right");
        var pivot = definition.getAsJsonArray("pivot");
        float x = pivot.get(0).getAsFloat() / 16;
        float y = pivot.get(1).getAsFloat() / 16;
        return (view, parts) -> {
            var pose = GauntletClient.railPose(view.stack(), view.holder(), rail);
            float opening = GauntletClient.hazardDoorPosition(view.stack(), view.holder(), rail);
            var housing = turn(x, y, pose.housingAngle());
            parts.add(new Part(front, housing));
            door(parts, left, housing, x, y, -DOOR_HINGE, opening);
            door(parts, right, housing, x, y, DOOR_HINGE, opening);
            boolean grip = DartAmmo.gripOnRail(DartAmmo.mode(view.stack()), rail);
            if (!GauntletClient.hasVisibleAmmo(DartAmmo.typeMask(grip), view.holder())) return;
            var loaded = grip ? rope : dart;
            if (pose.rolling()) {
                parts.add(new Part(loaded, turn(x, y, PI + pose.travelAngle())));
            } else {
                if (pose.topLoaded()) parts.add(new Part(loaded, null));
                parts.add(new Part(loaded, turn(x, y, PI)));
            }
        };
    }

    private static Matrix4f turn(float x, float y, float radians) {
        return new Matrix4f().translate(x, y, 0).rotateZ(radians).translate(-x, -y, 0);
    }

    private static void door(List<Part> parts, BakedModel model, Matrix4f housing, float x, float y,
                             float hinge, float opening) {
        float swing = -Math.copySign(80 * DEG_TO_RAD * opening, hinge);
        var transform = new Matrix4f(housing).translate(x + hinge, y, RopeRenderer.MOUTH_Z)
                .rotateY(swing).translate(-hinge, 0, 0);
        parts.add(new Part(model, transform));
    }

    static Node hatch(Models models) {
        var hatch = models.item("gauntlet_reload_hatch");
        var darts = models.item("reload_dart_pair");
        var rope = models.item("reload_rope_dart_pair");
        var tandem = models.item("reload_tandem_dart_pair");
        return (view, parts) -> {
            var reload = GauntletClient.reloadMotion(view.holder());
            float progress = reload == null ? 1 : reload.progress();
            float opening = ease(progress / AmmoReload.HATCH_OPEN_AT) * (1 - ease((progress - 0.64F) / 0.19F));
            var door = new Matrix4f().translate(0, 11.02F / 16, 9.34F / 16)
                    .rotateX(105 * DEG_TO_RAD * opening).translate(0, -11.02F / 16, -9.34F / 16);
            parts.add(new Part(hatch, door));
            if (reload == null || progress >= 0.84F) return;
            float side = view.context() == ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                    || view.context() == ItemDisplayContext.THIRD_PERSON_LEFT_HAND ? -1 : 1;
            int type;
            Matrix4f transform;
            if (progress < AmmoReload.INCOMING_START) {
                if (!reload.ammo.hasOutgoing()) return;
                type = reload.ammo.outgoingTypes();
                float eject = ease((progress - AmmoReload.EJECT_AT) / 0.20F);
                transform = new Matrix4f().translation(-side * 0.85F * eject, 0.55F * eject, -0.95F * eject);
            } else {
                type = reload.ammo.incomingTypes();
                float incoming = 1 - ease((progress - AmmoReload.INCOMING_START)
                        / (AmmoReload.INSERT_AT - AmmoReload.INCOMING_START));
                transform = new Matrix4f().translation(-side * 0.95F * incoming,
                        -0.42F * incoming + 0.17F * Mth.sin(PI * incoming), -1.2F * incoming);
            }
            var pair = switch (type) {
                case DartAmmo.GRIP -> rope;
                case DartAmmo.TANDEM -> tandem;
                default -> darts;
            };
            parts.add(new Part(pair, transform));
        };
    }

    private static float ease(float value) {
        float t = Mth.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }
}
