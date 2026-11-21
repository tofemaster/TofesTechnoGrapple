package technograpple;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

record ReloadHatchModel(RailFrontModel.Part hatch, RailFrontModel.Part darts, RailFrontModel.Part ropeDarts,
                        RailFrontModel.Part tandemDarts,
                        Matrix4fc transform) implements ItemModel {
    private static final Identifier HATCH = model("gauntlet_reload_hatch");
    private static final Identifier DARTS = model("reload_dart_pair");
    private static final Identifier ROPE_DARTS = model("reload_rope_dart_pair");
    private static final Identifier TANDEM_DARTS = model("reload_tandem_dart_pair");

    private static Identifier model(String name) {
        return Identifier.fromNamespaceAndPath(TechnoGrapple.ID, "item/" + name);
    }

    @Override
    public void update(ItemStackRenderState output, ItemStack stack, ItemModelResolver resolver,
                       ItemDisplayContext context, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        var reload = GauntletClient.reloadMotion(owner);
        float t = reload == null ? 1 : reload.progress();
        float opening = ease(t / AmmoReload.HATCH_OPEN_AT) * (1 - ease((t - 0.64F) / 0.19F));
        boolean foil = stack.hasFoil();
        if (t < 1 || foil) output.setAnimated();

        // The forward lip is the hinge, so the rear edge lifts away from the deck.
        var door = new Matrix4f(transform).translate(0, 11.02F / 16, 9.34F / 16)
                .rotateX(105 * Mth.DEG_TO_RAD * opening).translate(0, -11.02F / 16, -9.34F / 16);
        RailFrontModel.addPart(output, hatch, context, door, foil);
        if (reload == null || t >= 0.84F) return;

        float side = context.leftHand() ? -1 : 1;
        if (t < AmmoReload.INCOMING_START) {
            if (!reload.ammo.hasOutgoing()) return;
            float eject = ease((t - AmmoReload.EJECT_AT) / 0.20F);
            var outgoing = new Matrix4f(transform).translate(-side * 0.85F * eject, 0.55F * eject, -0.95F * eject);
            RailFrontModel.addPart(output, dartPair(reload.ammo.outgoingTypes()), context, outgoing, foil);
        } else {
            float incoming = 1 - ease((t - AmmoReload.INCOMING_START)
                    / (AmmoReload.INSERT_AT - AmmoReload.INCOMING_START));
            var insertion = new Matrix4f(transform).translate(-side * 0.95F * incoming, -0.42F * incoming,
                    -1.2F * incoming).translate(0, 0.17F * Mth.sin(Mth.PI * incoming), 0);
            RailFrontModel.addPart(output, dartPair(reload.ammo.incomingTypes()), context, insertion, foil);
        }
    }

    private RailFrontModel.Part dartPair(int types) {
        return switch (types) {
            case DartAmmo.GRIP -> ropeDarts;
            case DartAmmo.TANDEM -> tandemDarts;
            default -> darts;
        };
    }

    private static float ease(float value) {
        float t = Mth.clamp(value, 0, 1);
        return t * t * (3 - 2 * t);
    }

    record Unbaked() implements ItemModel.Unbaked {
        static final MapCodec<Unbaked> CODEC = MapCodec.unit(new Unbaked());

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            resolver.markDependency(HATCH);
            resolver.markDependency(DARTS);
            resolver.markDependency(ROPE_DARTS);
            resolver.markDependency(TANDEM_DARTS);
        }

        @Override
        public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
            var baker = context.blockModelBaker();
            return new ReloadHatchModel(RailFrontModel.Part.bake(baker, HATCH),
                    RailFrontModel.Part.bake(baker, DARTS), RailFrontModel.Part.bake(baker, ROPE_DARTS),
                    RailFrontModel.Part.bake(baker, TANDEM_DARTS), transformation);
        }

        @Override
        public MapCodec<Unbaked> type() {
            return CODEC;
        }
    }
}
