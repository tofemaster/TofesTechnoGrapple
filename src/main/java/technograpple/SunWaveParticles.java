package technograpple;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.Heightmap;

final class SunWaveParticles {
    private static final int VIEW_RADIUS = 96;
    private static final int SPACING = 4;
    private static final List<SunWaveParticles> ACTIVE = new ArrayList<>();
    private final SunWave wave;
    private final ClientLevel level;
    private final long start;

    private SunWaveParticles(SunWave wave, ClientLevel level) {
        this.wave = wave;
        this.level = level;
        this.start = level.getGameTime() - wave.age();
    }

    static void start(SunWave wave) {
        var level = Minecraft.getInstance().level;
        if (level != null) {
            ACTIVE.removeIf(front -> front.wave.player().equals(wave.player()));
            ACTIVE.add(new SunWaveParticles(wave, level));
        }
    }

    static void clear() {
        ACTIVE.clear();
    }

    static void tick() {
        var minecraft = Minecraft.getInstance();
        var camera = minecraft.getCameraEntity();
        ACTIVE.removeIf(front -> front.level != minecraft.level
                || front.level.getGameTime() - front.start > SunWave.LAST_TICK);
        if (camera == null) return;
        for (var front : ACTIVE) front.draw(camera.blockPosition());
    }

    private void draw(BlockPos viewer) {
        int age = (int) (level.getGameTime() - start);
        int minX = Math.max(wave.minX(), viewer.getX() - VIEW_RADIUS);
        int maxX = Math.min(wave.minX() + SunWave.WIDTH - 1, viewer.getX() + VIEW_RADIUS);
        int minZ = Math.max(wave.minZ(), viewer.getZ() - VIEW_RADIUS);
        int maxZ = Math.min(wave.minZ() + SunWave.WIDTH - 1, viewer.getZ() + VIEW_RADIUS);
        var random = level.getRandom();
        int offsetX = random.nextInt(SPACING);
        int offsetZ = random.nextInt(SPACING);
        var pos = new BlockPos.MutableBlockPos();
        for (int x = minX + offsetX; x <= maxX; x += SPACING) {
            for (int z = minZ + offsetZ; z <= maxZ; z += SPACING) {
                if (!wave.contains(x, z)) continue;
                int arrival = wave.arrival(x, z);
                if (arrival < age || arrival > age + 2) continue;
                pos.set(x, 0, z);
                if (!level.hasChunkAt(pos)) continue;
                int ground = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                double fx = x + random.nextDouble();
                double fz = z + random.nextDouble();
                for (int layer = 0; layer < 3; layer++) {
                    double y = ground + 0.3 + 1.6 * layer + random.nextDouble();
                    level.addAlwaysVisibleParticle(ParticleTypes.FLAME, true, fx, y, fz, 0, 0.12, 0);
                }
                if (random.nextBoolean()) {
                    level.addAlwaysVisibleParticle(ParticleTypes.LARGE_SMOKE, true, fx, ground + 3, fz, 0, 0.18, 0);
                }
            }
        }
    }
}
