package technograpple;

import java.util.Arrays;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

final class SunWaveDamage {
    private static final int LAVA_SEEDS = 256;
    private static final ResourceKey<DamageType> CURIOSITY = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(TechnoGrapple.ID, "exceptional_curiosity"));
    private static final ResourceKey<DamageType> OTHER_CURIOSITY = ResourceKey.create(Registries.DAMAGE_TYPE,
            new ResourceLocation(TechnoGrapple.ID, "someone_elses_exceptional_curiosity"));
    private static final TagKey<Block> MELTABLE_STONE = TagKey.create(Registries.BLOCK,
            new ResourceLocation(TechnoGrapple.ID, "sun_meltable_stone"));
    private final SunWave wave;
    private final int[] first = new int[SunWave.LAST_TICK + 1];
    private final int[] next = new int[SunWave.WIDTH * SunWave.WIDTH];
    private final BitSet lavaSeeds = new BitSet(next.length);
    private Map<UUID, Integer> previousPlayerArrivals = Map.of();
    private final HashSet<UUID> hitPlayers = new HashSet<>();
    private Map<Integer, Integer> previousMobArrivals = Map.of();
    private final HashSet<Integer> ignitedMobs = new HashSet<>();

    SunWaveDamage(SunWave wave, RandomSource random) {
        this.wave = wave;
        Arrays.fill(first, -1);
        for (int selected = 0; selected < LAVA_SEEDS;) {
            int column = random.nextInt(next.length);
            if (lavaSeeds.get(column) || !wave.contains(x(column), z(column))) continue;
            lavaSeeds.set(column);
            selected++;
        }
        // Bucket each column once so the server tick only visits the advancing band.
        for (int column = 0; column < next.length; column++) {
            int x = x(column);
            int z = z(column);
            if (!wave.contains(x, z)) continue;
            int time = wave.arrival(x, z);
            next[column] = first[time];
            first[time] = column;
        }
    }

    private int x(int column) {
        return wave.minX() + column / SunWave.WIDTH;
    }

    private int z(int column) {
        return wave.minZ() + column % SunWave.WIDTH;
    }

    void advance(ServerLevel level, int age) {
        var pos = new BlockPos.MutableBlockPos();
        affectBand(level, pos, age);
        igniteMobs(level, age);

        var arrivals = new HashMap<UUID, Integer>();
        for (var player : level.players()) {
            UUID id = player.getUUID();
            if (!player.isAlive() || player.gameMode.getGameModeForPlayer() != GameType.SURVIVAL
                    || hitPlayers.contains(id)) continue;
            BlockPos eye = BlockPos.containing(player.getEyePosition());
            if (!wave.contains(eye.getX(), eye.getZ())) continue;
            int arrival = wave.arrival(eye.getX(), eye.getZ());
            boolean crossed = arrival == age || previousPlayerArrivals.getOrDefault(id, -1) >= age && arrival < age;
            if (crossed && level.canSeeSky(eye)) {
                var damage = id.equals(wave.player()) ? CURIOSITY : OTHER_CURIOSITY;
                var type = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(damage);
                hitPlayers.add(id);
                player.hurt(new DamageSource(type), Float.MAX_VALUE);
            } else if (arrival > age) {
                arrivals.put(id, arrival);
            }
        }
        previousPlayerArrivals = arrivals;
    }

    private void igniteMobs(ServerLevel level, int age) {
        var area = new AABB(wave.minX(), level.getMinBuildHeight(), wave.minZ(),
                wave.minX() + SunWave.WIDTH, level.getMaxBuildHeight() + 512, wave.minZ() + SunWave.WIDTH);
        var arrivals = new HashMap<Integer, Integer>();
        for (var mob : level.getEntitiesOfClass(Mob.class, area, Mob::isAlive)) {
            if (ignitedMobs.contains(mob.getId()) || mob.fireImmune()) continue;
            BlockPos eye = BlockPos.containing(mob.getEyePosition());
            if (!wave.contains(eye.getX(), eye.getZ())) continue;
            int arrival = wave.arrival(eye.getX(), eye.getZ());
            int previous = previousMobArrivals.getOrDefault(mob.getId(), -1);
            if ((arrival == age || previous >= age && arrival < age) && level.canSeeSky(eye)) {
                mob.setSecondsOnFire(5);
                ignitedMobs.add(mob.getId());
            } else if (arrival > age) {
                arrivals.put(mob.getId(), arrival);
            }
        }
        previousMobArrivals = arrivals;
    }

    private void affectBand(ServerLevel level, BlockPos.MutableBlockPos pos, int band) {
        var random = level.getRandom();
        for (int column = first[band]; column >= 0; column = next[column]) {
            int x = x(column);
            int z = z(column);
            // Keep the front on schedule without generating terrain during the event.
            var chunk = level.getChunkSource().getChunkNow(x >> 4, z >> 4);
            if (chunk == null) continue;
            pos.set(x, chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15), z);
            vaporizeFrozenSurface(level, pos);
            vaporizeWater(level, pos);
            pos.set(x, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, x & 15, z & 15), z);
            if (!level.isOutsideBuildHeight(pos) && level.getWorldBorder().isWithinBounds(pos)
                    && level.canSeeSky(pos.above())) {
                var surface = chunk.getBlockState(pos);
                if (lavaSeeds.get(column) && meltSurface(level, pos, true)) {
                    chainMelt(level, pos, 4 + random.nextInt(5), true);
                    continue;
                }
                if (surface.is(MELTABLE_STONE) && random.nextInt(10) < 3 && meltSurface(level, pos, false)) {
                    chainMelt(level, pos, 3 + random.nextInt(7), false);
                }
                if (canScorchToDirt(surface)) {
                    int grassY = pos.getY();
                    if (level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS)) {
                        spreadGrassScorch(level, pos, x, grassY, z);
                    }
                }
            }
            if (random.nextInt(10) >= 3) continue;
            int y = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15) + 1;
            pos.set(x, y, z);
            if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) continue;
            if (chunk.getBlockState(pos).isAir()) {
                var fire = BaseFireBlock.getState(level, pos);
                if (fire.canSurvive(level, pos)) level.setBlock(pos, fire, Block.UPDATE_CLIENTS);
            }
        }
    }

    private void vaporizeFrozenSurface(ServerLevel level, BlockPos.MutableBlockPos pos) {
        if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) return;
        var state = level.getBlockState(pos);
        if (!isFrozenSurface(state) || !level.canSeeSky(pos.above())) return;
        while (pos.getY() >= level.getMinBuildHeight() && isFrozenSurface(state)) {
            if (!level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL)) break;
            pos.move(Direction.DOWN);
            state = level.getBlockState(pos);
        }
    }

    private static boolean isFrozenSurface(BlockState state) {
        return state.is(Blocks.SNOW) || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.ICE) || state.is(Blocks.PACKED_ICE)
                || state.is(Blocks.BLUE_ICE) || state.is(Blocks.FROSTED_ICE);
    }

    private void vaporizeWater(ServerLevel level, BlockPos pos) {
        if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) return;
        var state = level.getBlockState(pos);
        if (!state.is(Blocks.WATER) || !level.canSeeSky(pos.above())) return;
        if (!isWaterSource(state)) {
            for (Direction direction : Direction.values()) {
                if (direction == Direction.DOWN) continue; // Included in the flowing column's two-block depth.
                var adjacent = pos.relative(direction);
                if (wave.contains(adjacent.getX(), adjacent.getZ())
                        && level.getChunkSource().getChunkNow(adjacent.getX() >> 4, adjacent.getZ() >> 4) != null
                        && isWaterSource(level.getBlockState(adjacent))) {
                    if (level.getWorldBorder().isWithinBounds(adjacent)) {
                        level.setBlockAndUpdate(adjacent, Blocks.AIR.defaultBlockState());
                    }
                }
            }
        }
        vaporizeWaterColumn(level, pos);
    }

    private void vaporizeWaterColumn(ServerLevel level, BlockPos top) {
        if (!level.getWorldBorder().isWithinBounds(top)) return;
        for (int depth = 0; depth < 2; depth++) {
            var pos = top.below(depth);
            if (level.isOutsideBuildHeight(pos) || !level.getBlockState(pos).is(Blocks.WATER)
                    || !level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState())) break;
        }
    }

    private static boolean isWaterSource(BlockState state) {
        return state.is(Blocks.WATER) && state.getFluidState().isSource();
    }

    private void chainMelt(ServerLevel level, BlockPos origin, int size, boolean lava) {
        if (size <= 1) return;
        var random = level.getRandom();
        var next = new BlockPos.MutableBlockPos();
        int x = origin.getX();
        int y = origin.getY();
        int z = origin.getZ();
        for (int count = 1; count < size; count++) {
            int firstDirection = random.nextInt(4);
            boolean extended = false;
            for (int offset = 0; offset < 4; offset++) {
                Direction direction = Direction.from2DDataValue((firstDirection + offset) % 4);
                int nearX = x + direction.getStepX();
                int nearZ = z + direction.getStepZ();
                if (!wave.contains(nearX, nearZ)) continue;
                var chunk = level.getChunkSource().getChunkNow(nearX >> 4, nearZ >> 4);
                if (chunk == null) continue;
                int nearY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, nearX & 15, nearZ & 15);
                if (Math.abs(nearY - y) > 1) continue;
                next.set(nearX, nearY, nearZ);
                if (lava && (nearY != y || (hasExtraLavaNeighbor(level, next.above())
                        && random.nextInt(10) != 0))) continue;
                if (!meltSurface(level, next, lava)) continue;
                x = nearX;
                y = nearY;
                z = nearZ;
                extended = true;
                break;
            }
            if (!extended) return;
        }
    }

    private boolean hasExtraLavaNeighbor(ServerLevel level, BlockPos source) {
        int neighbors = 0;
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            var neighbor = source.relative(direction);
            var chunk = level.getChunkSource().getChunkNow(neighbor.getX() >> 4, neighbor.getZ() >> 4);
            if (chunk == null) continue;
            var fluid = chunk.getBlockState(neighbor).getFluidState();
            if (fluid.is(Fluids.LAVA) && fluid.isSource() && ++neighbors > 1) {
                return true;
            }
        }
        return false;
    }

    private boolean meltSurface(ServerLevel level, BlockPos pos, boolean lava) {
        if (level.isOutsideBuildHeight(pos) || !level.getWorldBorder().isWithinBounds(pos)) return false;
        var surface = level.getBlockState(pos);
        boolean stone = surface.is(MELTABLE_STONE);
        if (!stone && (!lava || !(canScorchToDirt(surface) || surface.is(Blocks.DIRT)
                || surface.is(Blocks.SAND) || surface.is(Blocks.RED_SAND) || surface.is(Blocks.MAGMA_BLOCK)))) {
            return false;
        }
        var above = pos.above();
        if (!level.canSeeSky(above)
                || lava && (level.isOutsideBuildHeight(above) || !level.getBlockState(above).isAir())) return false;
        if (!surface.is(Blocks.MAGMA_BLOCK)
                && !level.setBlock(pos, Blocks.MAGMA_BLOCK.defaultBlockState(), Block.UPDATE_CLIENTS)) return false;
        if (lava) level.setBlockAndUpdate(above, Blocks.LAVA.defaultBlockState());
        return true;
    }

    private static boolean canScorchToDirt(BlockState surface) {
        return surface.is(Blocks.GRASS_BLOCK) || surface.is(Blocks.PODZOL);
    }

    private void spreadGrassScorch(ServerLevel level, BlockPos.MutableBlockPos pos, int x, int y, int z) {
        var random = level.getRandom();
        int converted = 0;
        for (int attempt = 0; attempt < 128 && converted < 32; attempt++) {
            int dx = random.nextInt(33) - 16;
            int dz = random.nextInt(33) - 16;
            int nearX = x + dx;
            int nearZ = z + dz;
            if (dx * dx + dz * dz > 16 * 16 || !wave.contains(nearX, nearZ)) continue;
            var chunk = level.getChunkSource().getChunkNow(nearX >> 4, nearZ >> 4);
            if (chunk == null) continue;
            int nearY = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, nearX & 15, nearZ & 15);
            if (Math.abs(nearY - y) > 2) continue;
            pos.set(nearX, nearY, nearZ);
            if (canScorchToDirt(chunk.getBlockState(pos)) && level.getWorldBorder().isWithinBounds(pos)
                    && level.canSeeSky(pos.above())
                    && level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS)) converted++;
        }
    }
}
