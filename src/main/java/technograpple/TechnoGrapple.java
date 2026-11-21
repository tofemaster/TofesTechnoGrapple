package technograpple;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.UseEffects;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.AddPackFindersEvent;
import net.neoforged.neoforge.event.AnvilUpdateEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.level.SleepFinishedTimeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.PlayerSwitchHotbarSlotEvent;
import net.neoforged.neoforge.event.entity.living.LivingSwapItemsEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@Mod(TechnoGrapple.ID)
public final class TechnoGrapple {
    static final String ID = "tofestechnograpple";
    static final long PUMP_STROKE = 45_000_000;
    static final long PUMP_MIN_DWELL = 35_000_000;
    static final long PUMP_RECOVERY = 125_000_000;

    static long pumpReturnAt(long firedAt, long now) {
        return Math.max(now, firedAt + PUMP_STROKE + PUMP_MIN_DWELL);
    }
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ID);
    static final DeferredItem<HookItem> HOOK = ITEMS.registerItem("grappling_hook",
            properties -> ModList.get().isLoaded("faktocraft") ? ElectricHookItem.create(properties)
                    : new HookItem(properties.durability(HookItem.SHOT_LIFESPAN)));
    static final DeferredItem<HookItem> CREATIVE_HOOK = ITEMS.registerItem("creative_grappling_hook",
            properties -> new HookItem(properties.component(DataComponents.ITEM_MODEL,
                    Identifier.fromNamespaceAndPath(ID, "grappling_hook"))));
    static final DeferredItem<Item> DART = ITEMS.registerSimpleItem("dart", properties -> properties.stacksTo(64));
    static final DeferredItem<Item> ROPE_DART = ITEMS.registerSimpleItem("rope_dart", properties -> properties.stacksTo(64));
    static final DeferredHolder<SoundEvent, SoundEvent> RELOAD_EJECT = SOUNDS.register("reload_eject",
            () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(ID, "reload_eject")));
    static final DeferredHolder<SoundEvent, SoundEvent> RELOAD_INSERT = SOUNDS.register("reload_insert",
            () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(ID, "reload_insert")));
    static final DeferredHolder<SoundEvent, SoundEvent> HOOK_LAUNCH = SOUNDS.register("hook_launch",
            () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(ID, "hook_launch")));
    static final DeferredHolder<SoundEvent, SoundEvent> PUMP_RETURN = SOUNDS.register("pump_return",
            () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(ID, "pump_return")));
    static final DeferredHolder<SoundEvent, SoundEvent> WHIP_PULL = SOUNDS.register("whip_pull",
            () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(ID, "whip_pull")));
    static final DeferredHolder<SoundEvent, SoundEvent> CANNOT_FIRE = SOUNDS.register("cannot_fire",
            () -> SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath(ID, "cannot_fire")));

    public TechnoGrapple(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, TechnoGrappleConfig.SPEC);
        ITEMS.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener(RailState::register);
        modBus.addListener(TechnoGrapple::creativeTab);
        modBus.addListener(TechnoGrapple::addFaktocraftTextures);
        NeoForge.EVENT_BUS.addListener(TechnoGrapple::tick);
        NeoForge.EVENT_BUS.addListener(SunEventData::tick);
        NeoForge.EVENT_BUS.addListener(TechnoGrapple::login);
        NeoForge.EVENT_BUS.addListener(TechnoGrapple::changedDimension);
        NeoForge.EVENT_BUS.addListener(TechnoGrapple::logout);
        NeoForge.EVENT_BUS.addListener(TechnoGrapple::startSleep);
        NeoForge.EVENT_BUS.addListener(TechnoGrapple::sleepSkip);
        NeoForge.EVENT_BUS.addListener(TechnoGrapple::swapHands);
        NeoForge.EVENT_BUS.addListener(TechnoGrapple::switchHotbar);
        NeoForge.EVENT_BUS.addListener(Grapple::startTracking);
        NeoForge.EVENT_BUS.addListener(TechnoGrapple::interact);
        NeoForge.EVENT_BUS.addListener(TechnoGrapple::interactBlock);
        NeoForge.EVENT_BUS.addListener(TechnoGrapple::repair);
    }

    public static boolean isGauntlet(ItemStack stack) {
        return stack.getItem() instanceof HookItem;
    }

    static boolean canLaunch(ItemStack stack) {
        return stack.getItem() instanceof HookItem hook && hook.hasLaunchPower(stack);
    }

    public static boolean sunShotActive(Player player) {
        if (!isGauntlet(player.getMainHandItem())) return false;
        if (player instanceof ServerPlayer serverPlayer) return Grapple.sunShotActive(serverPlayer);
        if (!player.level().isClientSide()) return false;
        return RopeRenderer.hasSunShot(player.getUUID());
    }

    public static boolean blocksGlideStart(Player player) {
        if (sunShotActive(player)) return true;
        if (!isGauntlet(player.getMainHandItem())) return false;
        if (player instanceof ServerPlayer serverPlayer) return Grapple.gripSurfaceAttached(serverPlayer);
        if (!player.level().isClientSide()) return false;
        RailState rail = GauntletClient.deployedRail(player.getUUID());
        return rail != null && rail.gripSurfaceAttached();
    }

    public static boolean sunLocksHeldItem(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return player.getMainHandItem().is(HOOK.get()) && Grapple.sunShotActive(serverPlayer);
        }
        return player.level().isClientSide() && RopeRenderer.sunLocksHeldItem(player.getUUID());
    }

    private static void switchHotbar(PlayerSwitchHotbarSlotEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player && sunLocksHeldItem(player)) {
            event.setCanceled(true);
            player.connection.send(new ClientboundSetHeldSlotPacket(player.getInventory().getSelectedSlot()));
        }
    }

    private static void swapHands(LivingSwapItemsEvent.Hands event) {
        if (event.getEntity() instanceof Player player && sunLocksHeldItem(player)) event.setCanceled(true);
    }

    public static boolean shouldSlide(Player player) {
        if (!player.onGround() || !isGauntlet(player.getMainHandItem())) return false;
        if (player instanceof ServerPlayer serverPlayer) return Grapple.isPulling(serverPlayer);
        return player.level().isClientSide() && GauntletClient.isPulling(player);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.TOOLS_AND_UTILITIES)) {
            event.accept(HOOK.get().creativeSample(false));
            event.accept(HOOK.get().creativeSample(true));
            event.accept(CREATIVE_HOOK.get());
            event.accept(DART.get());
            event.accept(ROPE_DART.get());
        }
    }

    private static void addFaktocraftTextures(AddPackFindersEvent event) {
        if (!ModList.get().isLoaded("faktocraft")) return;
        event.addPackFinders(Identifier.fromNamespaceAndPath(ID, "resourcepacks/faktocraft"),
                PackType.CLIENT_RESOURCES, Component.literal("TechnoGrapple Faktocraft Textures"),
                PackSource.BUILT_IN, true, Pack.Position.TOP);
    }

    private static void tick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) Grapple.tick(player);
    }

    private static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SunEventData.sync(player);
    }

    private static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) SunEventData.sync(player);
    }

    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) Grapple.clear(player);
    }

    private static void startSleep(CanPlayerSleepEvent event) {
        if (SunEventData.blocksSleep(event.getEntity())) {
            event.setProblem(Player.BedSleepingProblem.OTHER_PROBLEM);
        }
    }

    private static void sleepSkip(SleepFinishedTimeEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (SunEventData.blocksSleepSkip(level.getServer())) event.setCanceled(true);
        else SunEventData.finishSleep(level.getServer());
    }

    private static void interact(PlayerInteractEvent.EntityInteract event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !isGauntlet(event.getItemStack())) return;
        event.setCancellationResult(HookItem.startUse(event.getEntity(), event.getHand()));
        event.setCanceled(true);
    }

    private static void interactBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND || !isGauntlet(event.getItemStack())) return;
        event.setCancellationResult(HookItem.startUse(event.getEntity(), event.getHand()));
        event.setCanceled(true);
    }

    private static void repair(AnvilUpdateEvent event) {
        ItemStack gauntlet = event.getLeft();
        if (ModList.get().isLoaded("faktocraft") || !gauntlet.is(HOOK.get()) || gauntlet.getDamageValue() == 0) return;

        ItemStack ingots = event.getRight();
        int shotsPerIngot;
        if (ingots.is(Items.IRON_INGOT)) {
            shotsPerIngot = 1;
        } else if (ingots.is(Items.COPPER_INGOT)) {
            shotsPerIngot = 10;
        } else if (ingots.is(Items.NETHERITE_INGOT)) {
            shotsPerIngot = 50;
        } else {
            return;
        }

        int damage = gauntlet.getDamageValue();
        int materialCost = Math.min(ingots.getCount(), Math.ceilDiv(damage, shotsPerIngot));
        int repairedDamage = Math.max(0, damage - materialCost * shotsPerIngot);
        gauntlet.setDamageValue(repairedDamage);

        // Price crossed durability bands so normal stack splits don't each round up.
        int maxDamage = gauntlet.getMaxDamage();
        long before = 10L * (maxDamage - damage) / maxDamage;
        long after = 10L * (maxDamage - repairedDamage) / maxDamage;
        int xpCost = Math.max(1, (int) (after - before));
        String name = event.getName();
        if (name != null) {
            if (StringUtil.isBlank(name) && gauntlet.has(DataComponents.CUSTOM_NAME)) {
                gauntlet.remove(DataComponents.CUSTOM_NAME);
                xpCost++;
            } else if (!StringUtil.isBlank(name) && !name.equals(gauntlet.getHoverName().getString())) {
                gauntlet.set(DataComponents.CUSTOM_NAME, Component.literal(name));
                xpCost++;
            }
        }

        event.setOutput(gauntlet);
        event.setMaterialCost(materialCost);
        event.setXpCost(xpCost);
    }

    static class HookItem extends Item {
        private static final int SHOT_LIFESPAN = 250;

        HookItem(Properties properties) {
            super(properties.stacksTo(1)
                    // Show the vanilla base melee stats without changing them.
                    .attributes(ItemAttributeModifiers.builder()
                            .add(Attributes.ATTACK_DAMAGE,
                                    new AttributeModifier(BASE_ATTACK_DAMAGE_ID, 0, AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            .add(Attributes.ATTACK_SPEED,
                                    new AttributeModifier(BASE_ATTACK_SPEED_ID, 0, AttributeModifier.Operation.ADD_VALUE),
                                    EquipmentSlotGroup.MAINHAND)
                            .build())
                    .component(DataComponents.USE_EFFECTS, new UseEffects(true, false, 1)));
        }

        boolean hasLaunchPower(ItemStack stack) {
            return !stack.isBroken();
        }

        ItemStack creativeSample(boolean depleted) {
            ItemStack stack = new ItemStack(this);
            if (depleted) stack.setDamageValue(stack.getMaxDamage());
            return stack;
        }

        void spendLaunchPower(ItemStack stack) {
            stack.setDamageValue(stack.getDamageValue() + 1);
        }

        @Override
        public InteractionResult use(Level level, Player player, InteractionHand hand) {
            return startUse(player, hand);
        }

        static InteractionResult startUse(Player player, InteractionHand hand) {
            if (hand != InteractionHand.MAIN_HAND) return InteractionResult.PASS;
            if (player instanceof ServerPlayer serverPlayer
                    && (Grapple.isReloading(serverPlayer) || Grapple.sunShotActive(serverPlayer)
                        || Grapple.sunCoolingDown(serverPlayer))) {
                return InteractionResult.FAIL;
            }
            int reloadMode = DartAmmo.reloadMode(player);
            if (reloadMode != 0) {
                if (player instanceof ServerPlayer serverPlayer) Grapple.reload(serverPlayer, reloadMode);
                return InteractionResult.CONSUME;
            }
            if (player instanceof ServerPlayer serverPlayer && Grapple.blocksLaunch(serverPlayer)) {
                return InteractionResult.FAIL;
            }
            if (!player.isUsingItem()) {
                player.startUsingItem(hand);
                if (player instanceof ServerPlayer serverPlayer) Grapple.press(serverPlayer);
            }
            return InteractionResult.CONSUME;
        }

        @Override
        public int getUseDuration(ItemStack stack, LivingEntity entity) {
            return 72_000;
        }

        @Override
        public boolean releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
            if (entity instanceof ServerPlayer player) Grapple.release(player);
            return false;
        }
    }
}
