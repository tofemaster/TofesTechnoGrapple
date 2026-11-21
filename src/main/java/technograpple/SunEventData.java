package technograpple;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

final class SunEventData extends SavedData {
    private static final long MOON_VISIBLE = Long.MIN_VALUE;
    private record Committed(SunWave wave, Identifier dimension, long seed, int nextTick) {
        private static final Codec<Committed> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                SunWave.SAVE_CODEC.fieldOf("wave").forGetter(Committed::wave),
                Identifier.CODEC.fieldOf("dimension").forGetter(Committed::dimension),
                Codec.LONG.fieldOf("seed").forGetter(Committed::seed),
                Codec.intRange(0, SunRelease.WHITEOUT_END - SunRelease.CUTOFF)
                        .fieldOf("next_tick").forGetter(Committed::nextTick)
        ).apply(instance, Committed::new));
    }

    private static final Codec<SunEventData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.listOf().optionalFieldOf("spent", List.of()).forGetter(data -> List.copyOf(data.spent)),
            Committed.CODEC.optionalFieldOf("active").forGetter(data -> Optional.ofNullable(data.committed)),
            Codec.LONG.optionalFieldOf("moon_hidden_until", MOON_VISIBLE).forGetter(data -> data.moonHiddenUntil)
    ).apply(instance, SunEventData::new));
    private static final SavedDataType<SunEventData> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(TechnoGrapple.ID, "sun_events"),
            () -> new SunEventData(List.of(), Optional.empty(), MOON_VISIBLE), CODEC);

    private final Set<UUID> spent;
    private Committed committed;
    private SunWaveDamage damage;
    private SunScene preview;
    private long previewAt;
    private long moonHiddenUntil;

    private SunEventData(List<UUID> spent, Optional<Committed> committed, long moonHiddenUntil) {
        this.spent = new HashSet<>(spent);
        this.committed = committed.orElse(null);
        this.moonHiddenUntil = moonHiddenUntil;
    }

    private static SunEventData get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    static boolean mayStart(ServerPlayer player) {
        if (!TechnoGrappleConfig.hctsEnabled()) return false;
        var server = player.level().getServer();
        var data = get(server);
        return data.committed == null
                && data.previewScene(server.overworld().getGameTime()) == null
                && (server.getDefaultGameType() == GameType.CREATIVE || !data.spent.contains(player.getUUID()));
    }

    static boolean blocksSleep(ServerPlayer player) {
        if (Grapple.sunShotActive(player)) return true;
        var server = player.level().getServer();
        var data = get(server);
        if (data.beforeSunReset()) return data.committed.wave().player().equals(player.getUUID());
        var preview = data.previewScene(server.overworld().getGameTime());
        return preview != null && preview.player().equals(player.getUUID());
    }

    static boolean blocksSleepSkip(MinecraftServer server) {
        var data = get(server);
        return data.beforeSunReset() || data.previewScene(server.overworld().getGameTime()) != null
                || Grapple.hasActiveSunShot(server);
    }

    private boolean beforeSunReset() {
        return committed != null && committed.nextTick() + SunRelease.CUTOFF <= SunRelease.END;
    }

    static void finishSleep(MinecraftServer server) {
        get(server).restoreMoon(server);
    }

    private void hideMoonAtMidnight(ServerLevel overworld) {
        var server = overworld.getServer();
        overworld.dimensionType().defaultClock().ifPresent(clock -> {
            var clocks = server.clockManager();
            long time = clocks.getTotalTicks(clock);
            long midnight = time + Math.floorMod(18_000L - time, 24_000L);
            moonHiddenUntil = midnight + 12_000L;
            clocks.setTotalTicks(clock, midnight);
            setDirty();
            broadcastNight(server, true);
        });
    }

    private void restoreMoon(MinecraftServer server) {
        if (moonHiddenUntil == MOON_VISIBLE) return;
        moonHiddenUntil = MOON_VISIBLE;
        setDirty();
        broadcastNight(server, false);
    }

    private static void broadcastNight(MinecraftServer server, boolean active) {
        var packet = new SunNight(active);
        for (var player : server.getPlayerList().getPlayers()) PacketDistributor.sendToPlayer(player, packet);
    }

    static void attach(ServerLevel level, UUID player) {
        var server = level.getServer();
        var data = get(server);
        data.preview = new SunScene(player, 0, false);
        data.previewAt = server.overworld().getGameTime();
        broadcast(server, data.preview);
    }

    static void release(ServerLevel level, SunWave wave, SunRelease release) {
        var server = level.getServer();
        var data = get(server);
        if (data.preview == null || !data.preview.player().equals(release.player())) return;
        if (release.reverses()) {
            data.preview = new SunScene(release.player(), release.elapsed(), true);
            data.previewAt = server.overworld().getGameTime();
            broadcast(server, data.preview);
            return;
        }
        data.preview = null;
        if (server.getDefaultGameType() != GameType.CREATIVE) data.spent.add(release.player());
        data.committed = new Committed(wave, level.dimension().identifier(), level.getRandom().nextLong(), 0);
        data.damage = null;
        data.setDirty();
        broadcast(server, new SunScene(release.player(), SunRelease.CUTOFF, false));
        PacketDistributor.sendToPlayersInDimension(level, wave);
    }

    private static void broadcast(MinecraftServer server, SunScene scene) {
        for (var player : server.getPlayerList().getPlayers()) PacketDistributor.sendToPlayer(player, scene);
    }

    private SunScene previewScene(long now) {
        if (preview == null) return null;
        int elapsed = (int) (now - previewAt);
        int age = preview.elapsed() + (preview.reversing() ? -elapsed : elapsed);
        if (preview.reversing() && age <= 0) {
            preview = null;
            return null;
        }
        return new SunScene(preview.player(), age, preview.reversing());
    }

    static void sync(ServerPlayer player) {
        var server = player.level().getServer();
        var data = get(server);
        PacketDistributor.sendToPlayer(player, new SunNight(data.moonHiddenUntil != MOON_VISIBLE));
        if (data.committed == null) {
            var scene = data.previewScene(server.overworld().getGameTime());
            if (scene != null) PacketDistributor.sendToPlayer(player, scene);
            return;
        }
        var event = data.committed;
        PacketDistributor.sendToPlayer(player, new SunScene(event.wave().player(), SunRelease.CUTOFF + event.nextTick(), false));
        if (event.nextTick() <= SunWave.LAST_TICK && event.dimension().equals(player.level().dimension().identifier())) {
            PacketDistributor.sendToPlayer(player, event.wave().withAge(event.nextTick()));
        }
    }

    static void tick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level != level.getServer().overworld()) return;
        var server = level.getServer();
        var data = get(server);
        if (data.moonHiddenUntil != MOON_VISIBLE) {
            level.dimensionType().defaultClock().ifPresent(clock -> {
                if (server.clockManager().getTotalTicks(clock) >= data.moonHiddenUntil) {
                    data.restoreMoon(server);
                }
            });
        }
        var committed = data.committed;
        if (committed == null || (!server.isDedicatedServer() && server.getPlayerList().getPlayers().isEmpty())) return;
        int age = committed.nextTick();
        if (age + SunRelease.CUTOFF == SunRelease.END) data.hideMoonAtMidnight(level);
        if (age <= SunWave.LAST_TICK) {
            var target = server.getLevel(ResourceKey.create(Registries.DIMENSION, committed.dimension()));
            if (target != null) {
                if (data.damage == null) {
                    data.damage = new SunWaveDamage(committed.wave(), RandomSource.create(committed.seed()));
                }
                data.damage.advance(target, age);
            }
        } else {
            data.damage = null;
        }
        data.committed = age + SunRelease.CUTOFF >= SunRelease.WHITEOUT_END ? null
                : new Committed(committed.wave(), committed.dimension(), committed.seed(), age + 1);
        data.setDirty();
    }
}
