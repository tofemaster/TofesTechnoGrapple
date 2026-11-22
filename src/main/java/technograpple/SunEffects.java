package technograpple;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import net.minecraft.client.Minecraft;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

public final class SunEffects {
    private static final Map<UUID, SunEffects> ACTIVE = new HashMap<>();
    private static final float PEAK_SCALE = 9.6F;
    private static final float PHOTON_PEAK_SCALE = PEAK_SCALE * 1.5F;
    private static final int EXPOSURE_START = 100;
    private static final double PEAK_EXPOSURE_ALPHA = 0.22;
    private static SunScene scene;
    private static long sceneTick;
    private static boolean moonHidden;

    static void setMoonHidden(boolean active) {
        moonHidden = active;
    }

    public static boolean moonHidden() {
        var level = Minecraft.getInstance().level;
        return moonHidden && level != null && level.dimension() == Level.OVERWORLD;
    }

    static void syncScene(SunScene update) {
        scene = update;
        sceneTick = now();
    }

    private static double sceneAge(double tick) {
        if (scene == null) return -1;
        double elapsed = tick - sceneTick;
        return scene.elapsed() + (scene.reversing() ? -elapsed : elapsed);
    }
    private final long start;
    private SunRelease release;
    private long releaseTick;
    private long nextFlicker;
    private int display = RailState.CANNOT_FIRE;
    private final long[] nextPump = {0, 0};
    private final long[] lastPump = {-100, -100};

    private SunEffects(long start) {
        this.start = start;
    }

    private static long now() {
        var level = Minecraft.getInstance().level;
        return level == null ? 0 : level.getGameTime();
    }

    static void start(UUID player) {
        ACTIVE.put(player, new SunEffects(now()));
    }

    static void release(SunRelease release) {
        var effect = ACTIVE.get(release.player());
        if (effect == null) return;
        effect.release = release;
        effect.releaseTick = now();
    }

    static void remove(UUID player) {
        ACTIVE.remove(player);
    }

    static void clear() {
        ACTIVE.clear();
        scene = null;
        moonHidden = false;
    }

    private boolean cooling(long tick) {
        return release != null && tick - releaseTick < SunRelease.COOLDOWN;
    }

    static boolean cooling(UUID player) {
        var effect = ACTIVE.get(player);
        return effect != null && effect.cooling(now());
    }

    private double elapsed(double tick) {
        if (release == null) return tick - start;
        double sinceRelease = tick - releaseTick;
        return release.elapsed() + (release.reverses() ? -sinceRelease : sinceRelease);
    }

    public static float scale() {
        var minecraft = Minecraft.getInstance();
        if (minecraft.player == null) return 1;
        return scaleAt(sceneAge(renderTick()));
    }

    static float photonScale() {
        return scaleAt(sceneAge(renderTick()), PHOTON_PEAK_SCALE);
    }

    static int sceneWashAlpha() {
        double age = sceneAge(renderTick());
        double progress = age <= SunRelease.PEAK
                ? (age - SunRelease.CUTOFF) / (SunRelease.PEAK - SunRelease.CUTOFF)
                : (SunRelease.WHITEOUT_END - age) / (SunRelease.WHITEOUT_END - SunRelease.END);
        double whiteout = smoothFade(progress);
        double exposure = age <= SunRelease.PEAK
                ? PEAK_EXPOSURE_ALPHA * smoothFade((age - EXPOSURE_START) / (SunRelease.CUTOFF - EXPOSURE_START))
                : 0;
        return (int) Math.round(255 * (whiteout + exposure * (1 - whiteout)));
    }

    private static double smoothFade(double progress) {
        double fade = Mth.clamp(progress, 0, 1);
        return fade * fade * (3 - 2 * fade);
    }

    private static double renderTick() {
        return now() + Minecraft.getInstance().getFrameTime();
    }

    private static float scaleAt(double age) {
        return scaleAt(age, PEAK_SCALE);
    }

    private static float scaleAt(double age, float peakScale) {
        if (age > SunRelease.END) return 1;
        double growth = Mth.clamp(age / SunRelease.PEAK, 0, 1);
        return (float) (1 + (peakScale - 1) * growth * growth * growth);
    }

    static float cameraShake(float phase) {
        double age = sceneAge(renderTick());
        float startScale = scaleAt(SunRelease.CAMERA_SHAKE);
        float intensity = Mth.clamp((scaleAt(age) - startScale) / (scaleAt(SunRelease.CAMERA_SHAKE_PEAK) - startScale), 0, 1);
        if (age > SunRelease.CAMERA_SHAKE_PEAK) {
            intensity *= (float) (1 - (age - SunRelease.CAMERA_SHAKE_PEAK) / (SunRelease.END - SunRelease.CAMERA_SHAKE_PEAK));
        }
        if (intensity <= 0) return 0;
        float time = (float) age;
        return intensity * (0.65F * Mth.sin(time * 1.4F + phase) + 0.35F * Mth.sin(time * 2.2F - phase));
    }

    static int display(UUID player) {
        var effect = ACTIVE.get(player);
        return effect != null && (effect.cooling(now())
                || effect.release == null && now() - effect.start >= SunRelease.SHAKE) ? effect.display : -1;
    }

    static float tremor(UUID player) {
        var effect = ACTIVE.get(player);
        if (effect == null || effect.release != null) return 0;
        var partial = Minecraft.getInstance().getFrameTime();
        float age = now() - effect.start + partial;
        float intensity = Mth.clamp((age - SunRelease.SHAKE) / 160, 0, 1);
        return intensity * (0.60F * Mth.sin(age * 1.3F) + 0.40F * Mth.sin(age * 2.1F));
    }

    static int pump(UUID player, int side) {
        var effect = ACTIVE.get(player);
        if (effect == null || effect.release != null) return 0;
        double age = now() - effect.lastPump[side]
                + Minecraft.getInstance().getFrameTime();
        return (int) Math.round(6 * Math.max(0, 1 - Math.abs(age - 2) / (age < 2 ? 2 : 4)));
    }

    static void tick() {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        long tick = level.getGameTime();
        double ageOfScene = sceneAge(tick);
        if (ageOfScene < 0 || ageOfScene > SunRelease.WHITEOUT_END) scene = null;
        ACTIVE.entrySet().removeIf(entry -> {
            var effect = entry.getValue();
            if (effect.release != null && effect.release.reverses()) {
                return !effect.cooling(tick) && effect.elapsed(tick) <= 0;
            }
            return effect.elapsed(tick) > SunRelease.WHITEOUT_END + 1;
        });
        var random = ThreadLocalRandom.current();
        for (var entry : ACTIVE.entrySet()) {
            var effect = entry.getValue();
            long age = tick - effect.start;
            if ((effect.cooling(tick) || effect.release == null && age >= SunRelease.SHAKE)
                    && tick >= effect.nextFlicker) {
                effect.display = effect.display == RailState.CANNOT_FIRE ? RailState.TRAVELLING : RailState.CANNOT_FIRE;
                effect.nextFlicker = tick + random.nextInt(1, 6);
            }
            var player = level.getPlayerByUUID(entry.getKey());
            if (effect.release != null || age < SunRelease.PUMPS || player == null
                    || !TechnoGrapple.isGauntlet(player.getMainHandItem())) continue;
            for (int side = 0; side < 2; side++) {
                if (effect.nextPump[side] == 0) effect.nextPump[side] = tick + side * 4;
                if (tick < effect.nextPump[side]) continue;
                effect.lastPump[side] = tick;
                effect.nextPump[side] = tick + random.nextInt(9, 19);
                level.playLocalSound(player.getX(), player.getY(), player.getZ(), TechnoGrapple.WHIP_PULL.get(), SoundSource.PLAYERS,
                        0.35F, random.nextFloat(0.88F, 1.12F), false);
            }
        }
    }
}
