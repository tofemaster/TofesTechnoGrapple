package technograpple;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.QuadTransformers;
import net.minecraftforge.client.model.geometry.IGeometryLoader;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.resource.PathPackResources;
import org.joml.Matrix4f;

@Mod.EventBusSubscriber(modid = TechnoGrapple.ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class GauntletModels {
    private static final String[] ITEMS = {"grappling_hook", "creative_grappling_hook", "dart", "rope_dart"};

    private GauntletModels() {}

    @SubscribeEvent
    public static void registerLoaders(ModelEvent.RegisterGeometryLoaders event) {
        IGeometryLoader<GauntletCuboids> loader = GauntletCuboids::read;
        event.register("cuboids", loader);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent event) {
        GauntletClient.registerConfigScreen(event);
    }

    @SubscribeEvent
    public static void optionalTextures(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES || !ModList.get().isLoaded("faktocraft")) return;
        var root = ModList.get().getModFileById(TechnoGrapple.ID).getFile().findResource("resourcepacks", "faktocraft");
        var pack = Pack.readMetaAndCreate("tofestechnograpple_faktocraft",
                Component.literal("TechnoGrapple Faktocraft Textures"), true,
                name -> new PathPackResources(name, true, root), PackType.CLIENT_RESOURCES, Pack.Position.TOP,
                PackSource.BUILT_IN);
        if (pack != null) event.addRepositorySource(source -> source.accept(pack));
    }

    @SubscribeEvent
    public static void registerParts(ModelEvent.RegisterAdditional event) {
        for (String item : ITEMS) read(item, id -> {
            event.register(id);
            return null;
        });
    }

    @SubscribeEvent
    public static void wrapModels(ModelEvent.ModifyBakingResult event) {
        var models = event.getModels();
        for (String item : ITEMS) {
            var key = new ModelResourceLocation(new ResourceLocation(TechnoGrapple.ID, item), "inventory");
            var base = models.get(key);
            if (base != null) {
                Node layout = read(item, id -> {
                    var part = models.get(id);
                    if (part == null) throw new IllegalStateException("TechnoGrapple model did not bake: " + id);
                    return part;
                });
                models.put(key, new ContextModel(base, layout, ItemStack.EMPTY, null));
            }
        }
    }

    private static Node read(String item, Models models) {
        String path = "/assets/" + TechnoGrapple.ID + "/items/" + item + ".json";
        try (var stream = GauntletModels.class.getResourceAsStream(path)) {
            if (stream == null) throw new IllegalStateException("Missing TechnoGrapple item layout " + path);
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            return parse(json.getAsJsonObject("model"), models);
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
    }

    private static Node parse(JsonObject node, Models models) {
        return switch (node.get("type").getAsString()) {
            case "minecraft:model" -> {
                var part = models.get(new ResourceLocation(node.get("model").getAsString()));
                yield (view, parts) -> parts.add(new Part(part, null));
            }
            case "minecraft:composite" -> {
                var children = new ArrayList<Node>();
                for (var child : node.getAsJsonArray("models")) children.add(parse(child.getAsJsonObject(), models));
                yield (view, parts) -> children.forEach(child -> child.add(view, parts));
            }
            case "minecraft:condition" -> {
                String property = node.get("property").getAsString();
                var yes = parse(node.getAsJsonObject("on_true"), models);
                var no = parse(node.getAsJsonObject("on_false"), models);
                yield (view, parts) -> {
                    boolean condition = switch (property) {
                        case "tofestechnograpple:vanilla_like" -> TechnoGrappleConfig.vanillaLikeEnabled();
                        case "tofestechnograpple:shader_display_glow" -> IrisSunCompatibility.shadersActive();
                        default -> throw new IllegalArgumentException("Unknown gauntlet model condition: " + property);
                    };
                    (condition ? yes : no).add(view, parts);
                };
            }
            case "minecraft:select" -> {
                var fallback = parse(node.getAsJsonObject("fallback"), models);
                var cases = new ArrayList<DisplayCase>();
                for (var value : node.getAsJsonArray("cases")) {
                    var entry = value.getAsJsonObject();
                    var names = new HashSet<String>();
                    var when = entry.get("when");
                    if (when.isJsonArray()) when.getAsJsonArray().forEach(name -> names.add(name.getAsString()));
                    else names.add(when.getAsString());
                    cases.add(new DisplayCase(names, parse(entry.getAsJsonObject("model"), models)));
                }
                yield (view, parts) -> {
                    for (var entry : cases) {
                        if (entry.names.contains(view.context.getSerializedName())) {
                            entry.model.add(view, parts);
                            return;
                        }
                    }
                    fallback.add(view, parts);
                };
            }
            case "minecraft:range_dispatch" -> {
                var fallback = parse(node.getAsJsonObject("fallback"), models);
                var threshold = new ArrayList<Range>();
                for (var value : node.getAsJsonArray("entries")) {
                    var entry = value.getAsJsonObject();
                    threshold.add(new Range(entry.get("threshold").getAsFloat(), parse(entry.getAsJsonObject("model"), models)));
                }
                threshold.sort(Comparator.comparingDouble(Range::limit).reversed());
                var property = switch (node.get("property").getAsString()) {
                    case "tofestechnograpple:hook_display" -> GauntletClient.VisualProperty.DISPLAY;
                    case "tofestechnograpple:attachment_cells" -> GauntletClient.VisualProperty.ATTACHMENT_CELLS;
                    case "tofestechnograpple:pump_frame_port" -> GauntletClient.VisualProperty.PORT_PUMP;
                    case "tofestechnograpple:pump_frame_starboard" -> GauntletClient.VisualProperty.STARBOARD_PUMP;
                    case "tofestechnograpple:vanilla_like_frame" -> GauntletClient.VisualProperty.SPRITE_FRAME;
                    default -> throw new IllegalArgumentException("Unknown gauntlet render property: " + node.get("property"));
                };
                yield (view, parts) -> {
                    float amount = property.get(view.stack, view.holder);
                    for (var entry : threshold) {
                        if (amount >= entry.limit) {
                            entry.model.add(view, parts);
                            return;
                        }
                    }
                    fallback.add(view, parts);
                };
            }
            case "tofestechnograpple:rotating_rail" -> GauntletModelParts.rail(node, models);
            case "tofestechnograpple:reload_hatch" -> GauntletModelParts.hatch(models);
            default -> throw new IllegalArgumentException("Unknown gauntlet model: " + node.get("type"));
        };
    }

    @FunctionalInterface
    interface Models {
        BakedModel get(ResourceLocation id);

        default BakedModel item(String name) {
            return get(new ResourceLocation(TechnoGrapple.ID, "item/" + name));
        }
    }

    interface Node {
        void add(View view, List<Part> parts);
    }

    record View(ItemStack stack, LivingEntity holder, ItemDisplayContext context) {}
    record Part(BakedModel model, Matrix4f matrix) {}
    private record DisplayCase(Set<String> names, Node model) {}
    private record Range(float limit, Node model) {}

    private static final class ContextModel extends BakedModelWrapper<BakedModel> {
        private final Node root;
        private final ItemStack stack;
        private final LivingEntity holder;
        private final ItemOverrides overrides = new ItemOverrides() {
            @Override
            public BakedModel resolve(BakedModel model, ItemStack item, ClientLevel level, LivingEntity owner, int seed) {
                return new ContextModel(originalModel, root, item, owner);
            }
        };

        ContextModel(BakedModel base, Node root, ItemStack stack, LivingEntity holder) {
            super(base);
            this.root = root;
            this.stack = stack;
            this.holder = holder;
        }

        @Override
        public ItemOverrides getOverrides() {
            return overrides;
        }

        @Override
        public BakedModel applyTransform(ItemDisplayContext context, PoseStack pose, boolean left) {
            var parts = new ArrayList<Part>();
            root.add(new View(stack, holder, context), parts);
            if (parts.isEmpty()) return originalModel.applyTransform(context, pose, left);
            var base = parts.get(0).model;
            base.applyTransform(context, pose, left);
            return parts.size() == 1 ? base : new LayeredModel(base, List.copyOf(parts));
        }
    }

    private static final class LayeredModel extends BakedModelWrapper<BakedModel> {
        private final List<Part> parts;

        LayeredModel(BakedModel base, List<Part> parts) {
            super(base);
            this.parts = parts;
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random) {
            var quads = new ArrayList<BakedQuad>();
            for (var part : parts) {
                var layer = part.model.getQuads(state, side, random);
                if (part.matrix != null) layer = QuadTransformers.applying(new Transformation(part.matrix)).process(layer);
                quads.addAll(layer);
            }
            return quads;
        }

        @Override
        public List<BakedQuad> getQuads(BlockState state, Direction side, RandomSource random,
                                        net.minecraftforge.client.model.data.ModelData data,
                                        net.minecraft.client.renderer.RenderType layer) {
            return getQuads(state, side, random);
        }

        @Override
        public List<BakedModel> getRenderPasses(ItemStack stack, boolean fabulous) {
            return List.of(this);
        }
    }
}
