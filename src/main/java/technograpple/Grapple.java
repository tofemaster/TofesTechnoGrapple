package technograpple;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import technograpple.mixin.DisplayRenderSettingsInvoker;

final class Grapple {
    private static final int ATTACHMENT_LIMIT_TICKS = 30;
    private static final Map<UUID, ItemStack> GLIDE_READY = new HashMap<>();
    private static final Map<UUID, Grapple> ACTIVE = new HashMap<>();
    private static final Map<UUID, RailLoad> RAIL_LOADS = new HashMap<>();
    private static final Map<UUID, Integer> AMMO_STATUS = new HashMap<>();
    private static final Map<UUID, Long> RELOAD_UNTIL = new HashMap<>();
    private static final Map<UUID, Long> TANDEM_WHIP_REFIRE_AT = new HashMap<>();
    private static final Map<UUID, Long> SUN_COOLDOWNS = new HashMap<>();
    private static final Map<UUID, Long> SUN_BUSY_UNTIL = new HashMap<>();
    private static final Map<UUID, SunAim> SUN_AIMS = new HashMap<>();

    private record SunAim(SunView view, int tick, ItemStack tool) {}

    static void setSunView(ServerPlayer player, SunView view) {
        SUN_AIMS.put(player.getUUID(), new SunAim(view, player.tickCount, player.getMainHandItem()));
    }
    private static final Identifier HOOK_MODEL = Identifier.fromNamespaceAndPath(TechnoGrapple.ID, "electronic_hook");
    private static final Identifier ROPE_HOOK_MODEL = Identifier.fromNamespaceAndPath(TechnoGrapple.ID, "electronic_rope_hook");
    private static final Set<EntityType<?>> DAMAGE_ONLY = Set.of(EntityTypes.ENDER_DRAGON, EntityTypes.WITHER,
            EntityTypes.WARDEN, EntityTypes.RAVAGER, EntityTypes.ELDER_GUARDIAN, EntityTypes.IRON_GOLEM,
            EntityTypes.GHAST, EntityTypes.HAPPY_GHAST, EntityTypes.SNIFFER);

    private enum Phase { FIRING, ATTACHED, PULLING_MOB, SUN_LOCKED }

    private record RailLoad(ServerLevel level, int slot, ItemStack tool, int emptyRail, long refireAt) {
        boolean isEquipped(ServerPlayer player) {
            return player.isAlive() && level == player.level()
                    && slot == player.getInventory().getSelectedSlot() && tool == player.getMainHandItem();
        }
    }

    private static final class DartDisplay extends Display.ItemDisplay {
        private int discardAt = Integer.MAX_VALUE;

        DartDisplay(ServerLevel level) {
            super(EntityTypes.ITEM_DISPLAY, level);
        }

        @Override
        public void tick() {
            super.tick();
            if (tickCount >= discardAt) discard();
        }

        @Override
        public boolean shouldBeSaved() {
            return false;
        }

        void lingerFor(CableRetraction retraction) {
            // Let the endpoint stay visible through the client's recoil and display interpolation.
            discardAt = tickCount + Math.ceilDiv((int) retraction.durationNanos(), 50_000_000) + 2;
        }
    }

    private final ServerLevel level;
    private final int slot;
    private final Vec3 direction;
    private final int rail;
    private final boolean unlimitedAttachment;
    private final boolean rope;
    private final long firedAt = System.nanoTime();
    private DartDisplay deployedHook;
    private RailState publishedRail;
    private Phase phase = Phase.FIRING;
    private int age;
    private Vec3 tip;
    private double rangeLeft = GrapplePhysics.RANGE;
    private SunShot sunShot;
    private SunView sunView;
    private long sunAttachedAt;
    private SunWave sunWave;
    private int sunReleasePresses;
    private BlockHitResult anchor;
    private BlockState anchorState;
    private LivingEntity mob;
    private Vec3 mobOffset;
    private double bestMobDistance = Double.POSITIVE_INFINITY;
    private int stallTicks;
    private double ropeLength;
    private boolean pulling;
    private double releaseDistance;
    private GrapplePhysics.Catch elasticCatch;
    private Vec3 previousAttachment;
    private int useTicks;
    private boolean held;
    private final boolean tandem;
    private boolean suppressLaunchSneak;

    private Grapple(ServerPlayer player, int rail, boolean rope, boolean tandem) {
        level = player.level();
        slot = player.getInventory().getSelectedSlot();
        this.rail = rail;
        this.rope = rope;
        this.tandem = tandem;
        suppressLaunchSneak = tandem && rope;
        unlimitedAttachment = rope || player.getMainHandItem().is(TechnoGrapple.CREATIVE_HOOK.get());
        direction = player.getLookAngle();
        tip = player.getEyePosition();
        previousAttachment = attachment(player);
    }

    static void reload(ServerPlayer player, int mode) {
        int outgoing = DartAmmo.mode(player.getMainHandItem()) & DartAmmo.availableTypes(player);
        if (player.isSpectator() || !DartAmmo.reload(player, mode)) return;
        if (ACTIVE.containsKey(player.getUUID())) finish(player, false);
        if (RAIL_LOADS.remove(player.getUUID()) != null) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                    RailState.idle(player.getUUID(), RailState.DOCKED, false));
        }
        var reload = new AmmoReload(player.getUUID(), outgoing, mode & DartAmmo.availableTypes(player));
        player.containerMenu.broadcastChanges();
        syncAmmo(player);
        RELOAD_UNTIL.put(player.getUUID(), System.nanoTime() + reload.duration());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, reload);
    }

    static boolean isReloading(ServerPlayer player) {
        Long until = RELOAD_UNTIL.get(player.getUUID());
        if (until == null) return false;
        if (System.nanoTime() < until) return true;
        RELOAD_UNTIL.remove(player.getUUID());
        return false;
    }

    static void press(ServerPlayer player) {
        SunAim aim = SUN_AIMS.remove(player.getUUID());
        if (!canGrapple(player)) return;
        if (player.isFallFlying() && !glideReady(player)) return;
        resetRailsIfUnequipped(player);
        UUID id = player.getUUID();
        Grapple current = ACTIVE.get(id);
        if (current != null && current.isValid(player)) {
            if (current.rope && current.phase != Phase.FIRING) {
                finish(player, false);
                return;
            }
            if (current.phase == Phase.ATTACHED) {
                Vec3 velocity = player.getKnownMovement();
                Vec3 forward = Vec3.directionFromRotation(0, player.getYRot());
                applyImpulse(player, velocity, GrapplePhysics.manualRelease(velocity, forward));
                finish(player, true);
                float whipPitch = 0.95F + player.getRandom().nextFloat() * 0.10F;
                player.level().playSound(null, player,
                        TechnoGrapple.WHIP_PULL.get(), SoundSource.PLAYERS, 0.70F, whipPitch);
                return;
            }
            if (current.phase == Phase.PULLING_MOB) {
                finish(player, false);
                return;
            }
        }
        ItemStack tool = player.getMainHandItem();
        boolean tandem = DartAmmo.mode(tool) == DartAmmo.TANDEM;
        boolean rope = DartAmmo.firesGrip(player);
        ItemStack dart = DartAmmo.find(player, rope);
        if (dart.isEmpty() || !TechnoGrapple.canLaunch(tool)) {
            cannotFire(player);
            return;
        }
        if (!tool.is(TechnoGrapple.CREATIVE_HOOK.get())) {
            ((TechnoGrapple.HookItem) tool.getItem()).spendLaunchPower(tool);
            dart.shrink(1);
            player.getInventory().setChanged();
            syncAmmo(player);
        }
        if (current != null) current.discardHook();
        int rail;
        if (tandem) rail = DartAmmo.tandemRail(rope);
        else rail = emptyRail(player) == RailState.PORT ? RailState.STARBOARD : RailState.PORT;
        Grapple shot = new Grapple(player, rail, rope, tandem);
        SunView sunView = aim != null && aim.tool() == tool && player.tickCount - aim.tick() <= 4
                ? aim.view() : new SunView(0, false, false);
        RAIL_LOADS.put(id, new RailLoad(shot.level, shot.slot, tool, rail, 0));
        ACTIVE.put(id, shot);
        if (!rope && SUN_BUSY_UNTIL.getOrDefault(id, 0L) <= levelTime(player)
                && SunShot.matches(player, shot.tip, shot.direction, sunView) && SunEventData.mayStart(player)
                && !hasActiveSunShot(shot.level.getServer())) {
            int lockedSlot = tool.is(TechnoGrapple.HOOK.get()) ? shot.slot : -1;
            shot.sunShot = new SunShot(id, shot.tip, shot.direction, lockedSlot);
            shot.sunView = sunView;
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, shot.sunShot);
        } else {
            shot.placeHookDisplay(shot.visualOrigin(player), shot.direction);
        }
        shot.publishVisualState(player);
        float launchPitch = 0.93F + player.getRandom().nextFloat() * 0.14F;
        player.level().playSound(null, player,
                TechnoGrapple.HOOK_LAUNCH.get(), SoundSource.PLAYERS, 0.75F, launchPitch);
    }

    static void tick(ServerPlayer player) {
        UUID id = player.getUUID();
        long time = levelTime(player);
        SUN_COOLDOWNS.computeIfPresent(id, (key, until) -> until <= time ? null : until);
        SUN_BUSY_UNTIL.computeIfPresent(id, (key, until) -> until <= time ? null : until);
        long now = System.nanoTime();
        TANDEM_WHIP_REFIRE_AT.computeIfPresent(id, (key, until) -> until <= now ? null : until);
        if (!player.isFallFlying() || !glideReady(player)) {
            if (GLIDE_READY.remove(player.getUUID()) != null) {
                PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new GlideControl(player.getUUID(), false));
            }
        }
        syncAmmo(player);
        resetRailsIfUnequipped(player);
        Grapple grapple = ACTIVE.get(id);
        if (grapple != null && (!grapple.isValid(player) || !grapple.update(player))) {
            finish(player, false);
        }
    }

    static void release(ServerPlayer player) {
        Grapple grapple = ACTIVE.get(player.getUUID());
        if (grapple != null && grapple.stopHolding()) finish(player, false);
    }

    private static void finish(ServerPlayer player, boolean whipPull) {
        UUID id = player.getUUID();
        Grapple grapple = ACTIVE.remove(id);
        if (grapple != null) {
            if (grapple.phase == Phase.SUN_LOCKED) grapple.endSunAttachment(player);
            if (grapple.sunShot == null && grapple.phase != Phase.FIRING
                    && emptyRail(player) != RailState.DOCKED && grapple.isValid(player)) {
                grapple.retractCable(player, true, false);
            }
            grapple.discardHook();
            if (grapple.tandem) {
                RAIL_LOADS.computeIfPresent(id, (key, load) ->
                        new RailLoad(load.level(), load.slot(), load.tool(), RailState.DOCKED, load.refireAt()));
            }
        }
        boolean keepTandemRails = grapple != null && grapple.tandem && RAIL_LOADS.containsKey(id);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player,
                RailState.idle(id, emptyRail(player), whipPull, keepTandemRails));
    }

    private static int emptyRail(ServerPlayer player) {
        RailLoad load = RAIL_LOADS.get(player.getUUID());
        return load != null && load.isEquipped(player) ? load.emptyRail() : RailState.DOCKED;
    }

    private static void cannotFire(ServerPlayer player) {
        player.level().playSound(null, player, TechnoGrapple.CANNOT_FIRE.get(), SoundSource.PLAYERS, 0.52F, 1.0F);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, RailState.cannotFire(player.getUUID()));
    }

    static void pressSunRelease(ServerPlayer player) {
        Grapple grapple = ACTIVE.get(player.getUUID());
        if (grapple == null || grapple.phase != Phase.SUN_LOCKED || !sunShotActive(player)) return;
        cannotFire(player);
        if (++grapple.sunReleasePresses == 25) finish(player, false);
    }

    private static long levelTime(ServerPlayer player) {
        return player.level().getGameTime();
    }

    private void endSunAttachment(ServerPlayer player) {
        long time = level.getGameTime();
        int elapsed = (int) Math.clamp(time - sunAttachedAt, 0, SunRelease.CUTOFF);
        var release = new SunRelease(player.getUUID(), elapsed);
        if (release.reverses()) {
            SUN_BUSY_UNTIL.put(player.getUUID(), time + Math.max(elapsed, SunRelease.COOLDOWN));
        }
        SunEventData.release(level, sunWave, release);
        SUN_COOLDOWNS.put(player.getUUID(), time + SunRelease.COOLDOWN);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, release);
    }

    static boolean sunCoolingDown(ServerPlayer player) {
        return SUN_COOLDOWNS.getOrDefault(player.getUUID(), 0L) > levelTime(player);
    }

    static boolean sunShotActive(ServerPlayer player) {
        Grapple grapple = ACTIVE.get(player.getUUID());
        RailLoad load = RAIL_LOADS.get(player.getUUID());
        return grapple != null && grapple.sunShot != null && load != null && load.isEquipped(player);
    }

    static boolean gripSurfaceAttached(ServerPlayer player) {
        Grapple grapple = ACTIVE.get(player.getUUID());
        return grapple != null && grapple.gripSurfaceAttached() && grapple.isValid(player);
    }

    private boolean gripSurfaceAttached() {
        return rope && phase == Phase.ATTACHED;
    }

    static boolean hasActiveSunShot(net.minecraft.server.MinecraftServer server) {
        return ACTIVE.values().stream().anyMatch(grapple -> grapple.sunShot != null
                && grapple.level.getServer() == server);
    }

    static boolean blocksLaunch(ServerPlayer player) {
        Grapple grapple = ACTIVE.get(player.getUUID());
        if (grapple != null && grapple.isValid(player)) {
            // A click on an attached dart releases it instead of starting another shot.
            if (grapple.phase != Phase.FIRING) return false;
            if (emptyRail(player) != RailState.DOCKED) return true;
        }
        long now = System.nanoTime();
        Long whipRefireAt = TANDEM_WHIP_REFIRE_AT.get(player.getUUID());
        if (whipRefireAt != null && now < whipRefireAt
                && DartAmmo.mode(player.getMainHandItem()) == DartAmmo.TANDEM && !DartAmmo.firesGrip(player)) return true;
        RailLoad load = RAIL_LOADS.get(player.getUUID());
        return load != null && load.refireAt() != 0 && load.isEquipped(player)
                && now < load.refireAt();
    }

    private static void resetRailsIfUnequipped(ServerPlayer player) {
        UUID id = player.getUUID();
        RailLoad load = RAIL_LOADS.get(id);
        if (load != null && !load.isEquipped(player)) {
            RAIL_LOADS.remove(id);
            finish(player, false);
        }
    }

    static void clear(ServerPlayer player) {
        UUID id = player.getUUID();
        AMMO_STATUS.remove(id);
        RELOAD_UNTIL.remove(id);
        TANDEM_WHIP_REFIRE_AT.remove(id);
        SUN_COOLDOWNS.remove(id);
        SUN_BUSY_UNTIL.remove(id);
        SUN_AIMS.remove(id);
        GLIDE_READY.remove(id);
        Grapple grapple = ACTIVE.remove(id);
        RailLoad load = RAIL_LOADS.remove(id);
        if (grapple != null) {
            if (grapple.phase == Phase.SUN_LOCKED) grapple.endSunAttachment(player);
            grapple.discardHook();
        }
        if (load != null || grapple != null) {
            PacketDistributor.sendToPlayersTrackingEntity(player, RailState.idle(id, RailState.DOCKED, false));
        }
    }

    private RailState visualState(ServerPlayer player) {
        int hookId = deployedHook == null || deployedHook.isRemoved() ? -1 : deployedHook.getId();
        int display = phase == Phase.FIRING ? RailState.TRAVELLING : RailState.SURFACE_ATTACHED;
        if (phase == Phase.SUN_LOCKED) display = RailState.SUN_LOCKED;
        boolean surfaceTaut = phase == Phase.ATTACHED && isTaut(player);
        boolean taut = surfaceTaut || (phase == Phase.PULLING_MOB && (!rope || isTaut(player)));
        // The sun lock has no attachment countdown. Negative gauge values identify rope.
        int cells = phase == Phase.FIRING || phase == Phase.SUN_LOCKED ? 0
                : rope ? (ropeLength >= GrapplePhysics.RANGE * 0.9 ? 1 : ropeLength >= GrapplePhysics.RANGE * 0.6 ? 2 : 3)
                : unlimitedAttachment ? 3 : Math.ceilDiv(ATTACHMENT_LIMIT_TICKS - age, 10);
        return new RailState(player.getUUID(), rail, hookId, rail, display, phase == Phase.FIRING, taut || sunShot != null,
                !rope && (surfaceTaut || pulling), false, rope ? -cells - 1 : cells, tandem, gripSurfaceAttached());
    }

    private boolean isTaut(ServerPlayer player) {
        return attachment(player).distanceTo(tip) >= ropeLength - 0.18;
    }

    static boolean isPulling(ServerPlayer player) {
        Grapple grapple = ACTIVE.get(player.getUUID());
        return grapple != null && !grapple.rope && grapple.phase == Phase.ATTACHED && grapple.isValid(player)
                && (grapple.pulling || grapple.isTaut(player));
    }

    private void publishVisualState(ServerPlayer player) {
        RailState state = visualState(player);
        if (!state.equals(publishedRail)) {
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, state);
            publishedRail = state;
        }
    }

    static void startTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer observer && event.getTarget() instanceof ServerPlayer player) {
            Grapple grapple = ACTIVE.get(player.getUUID());
            int emptyRail = emptyRail(player);
            RailState state = grapple != null && emptyRail != RailState.DOCKED && grapple.isValid(player)
                    ? grapple.visualState(player) : RailState.idle(player.getUUID(), emptyRail, false);
            PacketDistributor.sendToPlayer(observer, state);
            if (player.isFallFlying() && glideReady(player)) {
                PacketDistributor.sendToPlayer(observer, new GlideControl(player.getUUID(), true));
            }
            if (holdsGauntlet(player)) {
                PacketDistributor.sendToPlayer(observer, new DartAmmo(player.getUUID(), DartAmmo.availableTypes(player)));
            }
        }
    }

    private static void syncAmmo(ServerPlayer player) {
        UUID id = player.getUUID();
        if (!holdsGauntlet(player)) {
            AMMO_STATUS.remove(id);
            return;
        }
        int available = DartAmmo.availableTypes(player);
        Integer previous = AMMO_STATUS.put(id, available);
        if (previous == null || previous != available) {
            PacketDistributor.sendToPlayersTrackingEntity(player, new DartAmmo(id, available));
        }
    }

    private static boolean holdsGauntlet(ServerPlayer player) {
        return TechnoGrapple.isGauntlet(player.getMainHandItem()) || TechnoGrapple.isGauntlet(player.getOffhandItem());
    }

    private static boolean canGrapple(ServerPlayer player) {
        return player.isAlive() && !player.isSpectator() && !player.isPassenger()
                && !player.getAbilities().flying;
    }

    private static boolean glideReady(ServerPlayer player) {
        return GLIDE_READY.get(player.getUUID()) == player.getMainHandItem();
    }

    static void raiseGlidingArm(ServerPlayer player) {
        if (glideReady(player)) return;
        GLIDE_READY.put(player.getUUID(), player.getMainHandItem());
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, new GlideControl(player.getUUID(), true));
    }

    private boolean isValid(ServerPlayer player) {
        return level == player.level() && canGrapple(player) && (!player.isFallFlying() || phase != Phase.ATTACHED)
                && player.getInventory().getSelectedSlot() == slot
                && TechnoGrapple.isGauntlet(player.getMainHandItem());
    }

    private boolean update(ServerPlayer player) {
        if (phase == Phase.SUN_LOCKED) return level.getGameTime() - sunAttachedAt < SunRelease.CUTOFF;
        age++;
        if (sunShot != null) {
            tip = sunShot.tip(age);
            if (age * SunShot.SPEED >= SunShot.RANGE) {
                if (!SunEventData.mayStart(player)) return false;
                phase = Phase.SUN_LOCKED;
                sunAttachedAt = level.getGameTime();
                var center = player.blockPosition();
                sunWave = new SunWave(player.getUUID(), center.getX(), center.getZ(),
                        (float) SunShot.sunDirection(level, player.getEyePosition(), sunView).x, sunView.roundSun());
                SUN_BUSY_UNTIL.put(player.getUUID(), sunAttachedAt + SunRelease.WHITEOUT_END);
                SunEventData.attach(level, player.getUUID());
                publishVisualState(player);
            }
            return true;
        }
        if (phase != Phase.FIRING && !unlimitedAttachment && age >= ATTACHMENT_LIMIT_TICKS) return false;
        boolean useDown = player.isUsingItem() && player.getUsedItemHand() == InteractionHand.MAIN_HAND;
        if (!player.getLastClientInput().shift()) suppressLaunchSneak = false;
        if (useDown && ++useTicks >= 4) held = true;
        if (!useDown && stopHolding()) return false;

        boolean wasFlying = phase == Phase.FIRING;
        boolean active = switch (phase) {
            case FIRING -> fly(player);
            case ATTACHED -> attachTick(player, useDown);
            case PULLING_MOB -> pullMob(player);
            case SUN_LOCKED -> true;
        };
        previousAttachment = attachment(player);
        if (active || wasFlying) {
            Vec3 displayPosition = tip.subtract(direction.scale(1.71 / 16));
            placeHookDisplay(displayPosition, direction);
            if (wasFlying && !active) {
                retractCable(player, !active, !active && rangeLeft <= 0);
            }
        }
        if (active) publishVisualState(player);
        return active;
    }

    private void retractCable(ServerPlayer player, boolean spent, boolean miss) {
        if (miss) {
            long refireAt = TechnoGrapple.pumpReturnAt(firedAt, System.nanoTime()) + TechnoGrapple.PUMP_RECOVERY;
            RAIL_LOADS.computeIfPresent(player.getUUID(), (id, load) ->
                    new RailLoad(load.level(), load.slot(), load.tool(), load.emptyRail(), refireAt));
        }
        CableRetraction retraction = new CableRetraction(player.getUUID(), rail, visualOrigin(player),
                tip.subtract(direction.scale((1.71 + 1.36) / 16)), miss, phase == Phase.ATTACHED, rope);
        PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, retraction);
        if (spent && deployedHook != null) {
            deployedHook.lingerFor(retraction);
            deployedHook = null;
        }
    }

    private boolean stopHolding() {
        boolean detach = (!rope || tandem) && held && (phase == Phase.ATTACHED || phase == Phase.PULLING_MOB);
        held = false;
        useTicks = 0;
        return detach;
    }

    private boolean fly(ServerPlayer player) {
        double travel = Math.min(6, rangeLeft);
        Vec3 end = tip.add(direction.scale(travel));
        if (!level.hasChunkAt(net.minecraft.core.BlockPos.containing(end))) {
            rangeLeft = 0;
            return false;
        }

        BlockHitResult block = level.clip(new ClipContext(tip, end,
                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
        Vec3 stop = block.getType() == HitResult.Type.BLOCK ? block.getLocation() : end;
        EntityHitResult entity = ProjectileUtil.getEntityHitResult(player, tip, stop,
                new AABB(tip, stop).inflate(1),
                target -> target instanceof LivingEntity && !(target instanceof Player)
                        && !(target instanceof ArmorStand) && target.isAlive() && target.isPickable(),
                tip.distanceToSqr(stop));
        if (entity != null && entity.getEntity() instanceof LivingEntity target) {
            startTandemWhipCooldown(player);
            tip = entity.getLocation();
            if ((rope || target.hurtServer(level, player.damageSources().playerAttack(player), 3))
                    && target.isAlive() && !DAMAGE_ONLY.contains(target.getType())) {
                mob = target;
                mobOffset = tip.subtract(target.position());
                phase = Phase.PULLING_MOB;
                age = 0;
                if (rope) ropeLength = initialRopeLength(player);
            } else {
                return false;
            }
        } else if (block.getType() == HitResult.Type.BLOCK) {
            startTandemWhipCooldown(player);
            anchor = block;
            anchorState = level.getBlockState(block.getBlockPos());
            tip = stop;
            phase = Phase.ATTACHED;
            if (player.isFallFlying()) player.stopFallFlying();
            age = 0;
            double distance = attachment(player).distanceTo(tip);
            ropeLength = rope ? initialRopeLength(player)
                    : Mth.clamp(distance, GrapplePhysics.MIN_ROPE, GrapplePhysics.RANGE);
            releaseDistance = Math.min(distance * 0.25, 8);
            if (!rope) elasticCatch = GrapplePhysics.Catch.forDistance(distance);
        } else {
            tip = end;
            rangeLeft -= travel;
            if (rangeLeft <= 0) return false;
        }
        return true;
    }

    private void startTandemWhipCooldown(ServerPlayer player) {
        if (!tandem || rope) return;
        TANDEM_WHIP_REFIRE_AT.put(player.getUUID(), System.nanoTime() + TandemWhipCooldown.DURATION_NANOS);
        PacketDistributor.sendToPlayer(player, TandemWhipCooldown.INSTANCE);
    }

    private boolean attachTick(ServerPlayer player, boolean useDown) {
        Vec3 position = attachment(player);
        Vec3 toward = tip.subtract(position);
        double distance = toward.length();
        if (distance > GrapplePhysics.MAX_TETHER_DISTANCE || !level.hasChunkAt(anchor.getBlockPos())
                || level.getBlockState(anchor.getBlockPos()) != anchorState) {
            return false;
        }
        Vec3 velocity = player.getKnownMovement();
        Input input = player.getLastClientInput();
        if (rope) {
            adjustRopeLength(input);
            Vec3 tension = GrapplePhysics.ropeTension(toward.normalize().scale(-1), distance, velocity, ropeLength);
            applyImpulse(player, velocity, tension);
            return true;
        }
        if (!useDown && GrapplePhysics.shouldRelease(previousAttachment, position, velocity, tip,
                age, releaseDistance, elasticCatch == null ? 0 : elasticCatch.duration())) {
            return false;
        }

        Vec3 inward = toward.normalize();
        Vec3 control = Vec3.ZERO;
        if (elasticCatch != null && age <= elasticCatch.duration()) {
            control = age == 1 ? GrapplePhysics.burst(inward, velocity)
                    : elasticCatch.impulse(inward, distance, velocity);
        }
        Vec3 steering = steer(player, input, inward, velocity.add(control));
        Vec3 controlledVelocity = velocity.add(control).add(steering);
        double gravity = !player.onGround() && controlledVelocity.lengthSqr() <= 0.04
                && movementInput(input).equals(Vec3.ZERO) ? 0.08 : 0;
        Vec3 tension = GrapplePhysics.tension(inward.scale(-1), distance, controlledVelocity, ropeLength, gravity);
        pulling = control.add(tension).dot(inward) > 0.000001;
        applyImpulse(player, velocity, control.add(steering).add(tension));
        return true;
    }

    private static Vec3 movementInput(Input input) {
        return new Vec3((input.left() ? 1 : 0) - (input.right() ? 1 : 0), 0,
                (input.forward() ? 1 : 0) - (input.backward() ? 1 : 0));
    }

    private static Vec3 steer(ServerPlayer player, Input input, Vec3 inward, Vec3 velocity) {
        if (input.jump()) return Vec3.ZERO;
        Vec3 movement = movementInput(input);
        if (movement.lengthSqr() == 0) return Vec3.ZERO;
        double lateralShare = Math.abs(movement.x) / (Math.abs(movement.x) + Math.abs(movement.z));
        double maxImpulse = 0.045 * (1 + 1.25 * lateralShare);
        Vec3 intent = movement.normalize().yRot(-player.getYRot() * Mth.DEG_TO_RAD);
        double outward = Math.max(0, -intent.dot(inward));
        Vec3 direction = intent.add(inward.scale(outward * 0.70)).normalize();
        return direction.scale(Mth.clamp(0.45 - velocity.dot(direction), 0, maxImpulse));
    }

    private boolean pullMob(ServerPlayer player) {
        if (mob == null || !mob.isAlive() || mob.level() != level || mob.isPassenger()) {
            return false;
        }
        tip = mob.position().add(mobOffset);
        if (rope) {
            Vec3 outward = tip.subtract(attachment(player));
            double distance = outward.length();
            if (distance > GrapplePhysics.MAX_TETHER_DISTANCE) return false;
            adjustRopeLength(player.getLastClientInput());
            Vec3 relativeVelocity = mob.getDeltaMovement().subtract(player.getKnownMovement());
            Vec3 tension = GrapplePhysics.ropeTension(outward.normalize(), distance, relativeVelocity, ropeLength);
            if (tension.lengthSqr() > 1.0E-8) mob.push(tension);
            return true;
        }
        Vec3 toward = attachment(player).subtract(mob.position().add(0, mob.getBbHeight() * 0.5, 0));
        double distance = toward.length();
        Vec3 inward = toward.normalize();
        double speed = mob.getDeltaMovement().dot(inward);
        if (distance < bestMobDistance - 0.03) {
            stallTicks = 0;
            bestMobDistance = distance;
        } else {
            stallTicks++;
        }
        if (distance <= 3.1 || distance > GrapplePhysics.MAX_TETHER_DISTANCE || stallTicks >= 30) {
            if (distance <= 3.1 && speed > 0) mob.push(inward.scale(-speed));
            return false;
        }
        double desiredSpeed = Math.min(0.88, distance - 3);
        mob.push(inward.scale(Mth.clamp(desiredSpeed - speed, -0.288, 0.288)));
        return true;
    }

    private double initialRopeLength(ServerPlayer player) {
        return Mth.clamp(attachment(player).distanceTo(tip), 0.35, GrapplePhysics.RANGE);
    }

    private void adjustRopeLength(Input input) {
        double adjustment = input.shift() && !suppressLaunchSneak ? 0.25 : input.jump() ? -0.25 : 0;
        ropeLength = Mth.clamp(ropeLength + adjustment, 0.35, GrapplePhysics.RANGE);
    }

    private void placeHookDisplay(Vec3 position, Vec3 heading) {
        // Item displays turn their item model 180 degrees around Y.
        float yaw = Mth.wrapDegrees(180 + (float) (Math.atan2(-heading.x, heading.z) * Mth.RAD_TO_DEG));
        float pitch = (float) (Math.atan2(heading.y, heading.horizontalDistance()) * Mth.RAD_TO_DEG);
        if (deployedHook == null || deployedHook.isRemoved()) {
            DartDisplay visual = new DartDisplay(level);
            ItemStack stack = new ItemStack(TechnoGrapple.HOOK.get());
            stack.set(DataComponents.ITEM_MODEL, rope ? ROPE_HOOK_MODEL : HOOK_MODEL);
            visual.getSlot(0).set(stack);
            var displaySettings = (DisplayRenderSettingsInvoker) (Display) visual;
            displaySettings.technograpple$setPosRotInterpolationDuration(1);
            displaySettings.technograpple$setViewRange(2);
            visual.snapTo(position, yaw, pitch);
            if (level.addFreshEntity(visual)) deployedHook = visual;
        } else {
            deployedHook.setPos(position);
            deployedHook.setYRot(yaw);
            deployedHook.setXRot(pitch);
        }
    }

    private void discardHook() {
        if (deployedHook != null) deployedHook.discard();
    }

    private Vec3 visualOrigin(ServerPlayer player) {
        Vec3 side = Vec3.directionFromRotation(0, player.getYRot() + 90);
        double handSide = player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        double railOffset = rail == RailState.PORT ? -0.1266 : 0.1266;
        return player.getEyePosition().add(side.scale(handSide * (0.32 + railOffset)))
                .add(player.getLookAngle().scale(0.30)).add(0, -0.29, 0);
    }

    private static Vec3 attachment(ServerPlayer player) {
        return player.position().add(0, 0.90, 0);
    }

    private static void applyImpulse(ServerPlayer player, Vec3 velocity, Vec3 impulse) {
        double magnitude = impulse.length();
        if (magnitude < 0.000001) return;
        Vec3 motion = velocity.add(impulse.scale(Math.min(1, 5 / magnitude)));
        player.setDeltaMovement(motion);
        player.connection.send(new ClientboundSetEntityMotionPacket(player.getId(), motion));
        // Give the movement validator time to receive the velocity change without extending vanilla fall protection.
        if (!player.isIgnoringFallDamageFromCurrentImpulse()) {
            player.applyPostImpulseGraceTime(3);
        }
    }
}
