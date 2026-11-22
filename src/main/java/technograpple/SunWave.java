package technograpple;

import net.minecraft.network.FriendlyByteBuf;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.UUIDUtil;
import net.minecraft.util.Mth;

record SunWave(UUID player, int centerX, int centerZ, float sunX, boolean round, int age) {
    static final int HALF_WIDTH = 256;
    static final int WIDTH = HALF_WIDTH * 2;
    static final int CENTER_TICK = 150;
    static final int LAST_TICK = CENTER_TICK + 30;
    static final Codec<SunWave> SAVE_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUIDUtil.CODEC.fieldOf("player").forGetter(SunWave::player),
            Codec.INT.fieldOf("x").forGetter(SunWave::centerX),
            Codec.INT.fieldOf("z").forGetter(SunWave::centerZ),
            Codec.FLOAT.fieldOf("sun_x").forGetter(SunWave::sunX),
            Codec.BOOL.optionalFieldOf("round", false).forGetter(SunWave::round)
    ).apply(instance, SunWave::new));

    SunWave(UUID player, int centerX, int centerZ, float sunX, boolean round) {
        this(player, centerX, centerZ, sunX, round, 0);
    }

    SunWave withAge(int age) {
        return new SunWave(player, centerX, centerZ, sunX, round, age);
    }

    void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(player);
        buffer.writeInt(centerX);
        buffer.writeInt(centerZ);
        buffer.writeFloat(sunX);
        buffer.writeBoolean(round);
        buffer.writeVarInt(age);
    }

    static SunWave decode(FriendlyByteBuf buffer) {
        return new SunWave(buffer.readUUID(), buffer.readInt(), buffer.readInt(), buffer.readFloat(), buffer.readBoolean(), buffer.readVarInt());
    }

    int minX() { return centerX - HALF_WIDTH; }
    int minZ() { return centerZ - HALF_WIDTH; }

    boolean contains(int x, int z) {
        if (x < minX() || x >= minX() + WIDTH || z < minZ() || z >= minZ() + WIDTH) return false;
        return !round || radiusSquared(x, z) <= 1;
    }

    int arrival(int x, int z) {
        // Near noon, blend the directional sweep into the footprint perimeter closing inward.
        double dx = (x - centerX) / (double) HALF_WIDTH;
        double dz = (z - centerZ) / (double) HALF_WIDTH;
        double radius = round ? Math.sqrt(radiusSquared(x, z)) : Math.max(Math.abs(dx), Math.abs(dz));
        double inward = CENTER_TICK * (1 - radius);
        double towardSun = Math.copySign(1, sunX) * dx;
        double sweep = CENTER_TICK - towardSun * (towardSun >= 0 ? CENTER_TICK : LAST_TICK - CENTER_TICK);
        double directionWeight = Mth.clamp(Math.abs(sunX) / 0.30, 0, 1);
        return (int) Math.round(Mth.lerp(directionWeight, inward, sweep));
    }

    private double radiusSquared(int x, int z) {
        double dx = (x - centerX + 0.5) / HALF_WIDTH;
        double dz = (z - centerZ + 0.5) / HALF_WIDTH;
        return dx * dx + dz * dz;
    }
}
