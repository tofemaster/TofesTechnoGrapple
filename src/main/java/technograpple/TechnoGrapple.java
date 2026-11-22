package technograpple;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.Level;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AnvilUpdateEvent;
import net.minecraftforge.event.BuildCreativeModeTabContentsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.level.SleepFinishedTimeEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.living.LivingSwapItemsEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerSleepInBedEvent;

@Mod(TechnoGrapple.ID)
public final class TechnoGrapple {
    static final String ID = "tofestechnograpple";
    static final long PUMP_STROKE = 45_000_000;
    static final long PUMP_MIN_DWELL = 35_000_000;
    static final long PUMP_RECOVERY = 125_000_000;

    static long pumpReturnAt(long firedAt, long now) {
        return Math.max(now, firedAt + PUMP_STROKE + PUMP_MIN_DWELL);
    }
    private static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ID);
    static final RegistryObject<HookItem> HOOK = ITEMS.register("grappling_hook",
            () -> ModList.get().isLoaded("faktocraft") ? ElectricHookItem.create(new Item.Properties().stacksTo(1))
                    : new HookItem(new Item.Properties().durability(HookItem.SHOT_LIFESPAN)));
    static final RegistryObject<HookItem> CREATIVE_HOOK = ITEMS.register("creative_grappling_hook",
            () -> new HookItem(new Item.Properties().stacksTo(1)));
    static final RegistryObject<Item> DART = ITEMS.register("dart", () -> new Item(new Item.Properties()));
    static final RegistryObject<Item> ROPE_DART = ITEMS.register("rope_dart", () -> new Item(new Item.Properties()));
    static final RegistryObject<Item> HOOK_DISPLAY = ITEMS.register("electronic_hook", () -> new Item(new Item.Properties()));
    static final RegistryObject<Item> ROPE_DISPLAY = ITEMS.register("electronic_rope_hook", () -> new Item(new Item.Properties()));
    static final RegistryObject<SoundEvent> RELOAD_EJECT = SOUNDS.register("reload_eject",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ID, "reload_eject")));
    static final RegistryObject<SoundEvent> RELOAD_INSERT = SOUNDS.register("reload_insert",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ID, "reload_insert")));
    static final RegistryObject<SoundEvent> HOOK_LAUNCH = SOUNDS.register("hook_launch",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ID, "hook_launch")));
    static final RegistryObject<SoundEvent> PUMP_RETURN = SOUNDS.register("pump_return",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ID, "pump_return")));
    static final RegistryObject<SoundEvent> WHIP_PULL = SOUNDS.register("whip_pull",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ID, "whip_pull")));
    static final RegistryObject<SoundEvent> CANNOT_FIRE = SOUNDS.register("cannot_fire",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(ID, "cannot_fire")));

    public TechnoGrapple(FMLJavaModLoadingContext context) {
        IEventBus modBus = context.getModEventBus();
        context.registerConfig(ModConfig.Type.COMMON, TechnoGrappleConfig.SPEC);
        ITEMS.register(modBus);
        SOUNDS.register(modBus);
        GrappleNetwork.register();
        modBus.addListener(TechnoGrapple::creativeTab);
        MinecraftForge.EVENT_BUS.addListener(TechnoGrapple::tick);
        MinecraftForge.EVENT_BUS.addListener(SunEventData::tick);
        MinecraftForge.EVENT_BUS.addListener(TechnoGrapple::login);
        MinecraftForge.EVENT_BUS.addListener(TechnoGrapple::changedDimension);
        MinecraftForge.EVENT_BUS.addListener(TechnoGrapple::logout);
        MinecraftForge.EVENT_BUS.addListener(TechnoGrapple::startSleep);
        MinecraftForge.EVENT_BUS.addListener(TechnoGrapple::sleepSkip);
        MinecraftForge.EVENT_BUS.addListener(TechnoGrapple::swapHands);
        MinecraftForge.EVENT_BUS.addListener(Grapple::startTracking);
        MinecraftForge.EVENT_BUS.addListener(TechnoGrapple::interact);
        MinecraftForge.EVENT_BUS.addListener(TechnoGrapple::interactBlock);
        MinecraftForge.EVENT_BUS.addListener(TechnoGrapple::repair);
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
        if (!player.level().isClientSide) return false;
        return RopeRenderer.hasSunShot(player.getUUID());
    }

    public static boolean blocksGlideStart(Player player) {
        if (sunShotActive(player)) return true;
        if (!isGauntlet(player.getMainHandItem())) return false;
        if (player instanceof ServerPlayer serverPlayer) return Grapple.gripSurfaceAttached(serverPlayer);
        if (!player.level().isClientSide) return false;
        RailState rail = GauntletClient.deployedRail(player.getUUID());
        return rail != null && rail.gripSurfaceAttached();
    }

    public static boolean sunLocksHeldItem(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            return player.getMainHandItem().is(HOOK.get()) && Grapple.sunShotActive(serverPlayer);
        }
        return player.level().isClientSide && RopeRenderer.sunLocksHeldItem(player.getUUID());
    }

    private static void swapHands(LivingSwapItemsEvent.Hands event) {
        if (event.getEntity() instanceof Player player && sunLocksHeldItem(player)) event.setCanceled(true);
    }

    public static boolean shouldSlide(Player player) {
        if (!player.onGround() || !isGauntlet(player.getMainHandItem())) return false;
        if (player instanceof ServerPlayer serverPlayer) return Grapple.isPulling(serverPlayer);
        return player.level().isClientSide && GauntletClient.isPulling(player);
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


    private static void tick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer player) Grapple.tick(player);
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

    private static void startSleep(PlayerSleepInBedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && SunEventData.blocksSleep(player)) {
            event.setResult(Player.BedSleepingProblem.OTHER_PROBLEM);
        }
    }

    private static void sleepSkip(SleepFinishedTimeEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (SunEventData.blocksSleepSkip(level.getServer())) event.setTimeAddition(level.getDayTime());
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
        ItemStack gauntlet = event.getLeft().copy();
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
        int materialCost = Math.min(ingots.getCount(), net.minecraft.util.Mth.positiveCeilDiv(damage, shotsPerIngot));
        int repairedDamage = Math.max(0, damage - materialCost * shotsPerIngot);
        gauntlet.setDamageValue(repairedDamage);

        // Price crossed durability bands so normal stack splits don't each round up.
        int maxDamage = gauntlet.getMaxDamage();
        long before = 10L * (maxDamage - damage) / maxDamage;
        long after = 10L * (maxDamage - repairedDamage) / maxDamage;
        int xpCost = Math.max(1, (int) (after - before));
        String name = event.getName();
        if (name != null) {
            if (name.isBlank() && gauntlet.hasCustomHoverName()) {
                gauntlet.resetHoverName();
                xpCost++;
            } else if (!name.isBlank() && !name.equals(gauntlet.getHoverName().getString())) {
                gauntlet.setHoverName(Component.literal(name));
                xpCost++;
            }
        }

        event.setOutput(gauntlet);
        event.setMaterialCost(materialCost);
        event.setCost(xpCost);
    }

    static class HookItem extends Item {
        private static final int SHOT_LIFESPAN = 250;

        HookItem(Properties properties) {
            super(properties);
        }

        boolean hasLaunchPower(ItemStack stack) {
            return !stack.isDamageableItem() || stack.getDamageValue() < stack.getMaxDamage();
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
        public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
            return new InteractionResultHolder<>(startUse(player, hand), player.getItemInHand(hand));
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
        public int getUseDuration(ItemStack stack) {
            return 72_000;
        }

        @Override
        public void releaseUsing(ItemStack stack, Level level, LivingEntity entity, int remaining) {
            if (entity instanceof ServerPlayer player) Grapple.release(player);
        }
    }
}
