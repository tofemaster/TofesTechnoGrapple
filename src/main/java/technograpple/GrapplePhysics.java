package technograpple;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

final class GrapplePhysics {
    static final double RANGE = 85;
    static final double MAX_TETHER_DISTANCE = RANGE + 16;
    static final double MIN_ROPE = 3.6;
    private static final double FULL_CATCH_STRENGTH_DISTANCE = 64;

    record Catch(double restRadius, double targetSpeed, int duration) {
        static Catch forDistance(double distance) {
            double scale = Math.sqrt(Mth.clamp((distance - MIN_ROPE)
                    / (FULL_CATCH_STRENGTH_DISTANCE - MIN_ROPE), 0, 1));
            return new Catch(Math.max(0.35, distance * 0.10), 0.90 + 1.10 * scale,
                    (int) Math.round(18 + 27 * scale));
        }

        Vec3 impulse(Vec3 inward, double distance, Vec3 velocity) {
            double extension = distance - restRadius;
            if (extension <= 0.05) return Vec3.ZERO;
            double speed = velocity.dot(inward);
            double demand = Math.min(extension * 0.08 - speed * 0.12, targetSpeed - speed);
            return inward.scale(Math.max(0, Math.min(0.24, demand) - 0.004));
        }
    }

    static boolean shouldRelease(Vec3 previous, Vec3 position, Vec3 velocity, Vec3 anchor,
                                 int age, double releaseDistance, int deadline) {
        double nearest = Math.min(segmentDistance(previous, position, anchor),
                segmentDistance(position, position.add(velocity), anchor));
        return nearest <= 1.25 || age > deadline || (age >= 4 && nearest <= releaseDistance + 0.01);
    }

    private static double segmentDistance(Vec3 start, Vec3 end, Vec3 anchor) {
        Vec3 delta = end.subtract(start);
        double lengthSquared = delta.lengthSqr();
        double t = lengthSquared > 0.000001
                ? Mth.clamp(anchor.subtract(start).dot(delta) / lengthSquared, 0, 1) : 0;
        return start.add(delta.scale(t)).distanceTo(anchor);
    }

    static Vec3 burst(Vec3 inward, Vec3 velocity) {
        Vec3 direction = inward.add(0, 0.38, 0).normalize();
        return direction.scale(Mth.clamp(1.90 - velocity.dot(direction), 0, 0.92));
    }

    static Vec3 ropeTension(Vec3 outward, double distance, Vec3 velocity, double length) {
        // The rope acts only when the endpoint would exceed the paid-out length.
        double stretch = distance - length;
        double radial = velocity.dot(outward);
        double correction = Math.max(stretch + radial, stretch * 0.2);
        return outward.scale(-Mth.clamp(correction, 0, 1.2));
    }

    static Vec3 tension(Vec3 outward, double distance, Vec3 velocity, double rope, double gravity) {
        if (distance < 0.0001) return Vec3.ZERO;
        double radial = velocity.dot(outward);
        double tangent = velocity.subtract(outward.scale(radial)).length();
        Vec3 predicted = velocity.add(0, -gravity, 0);
        double predictedRadial = predicted.dot(outward);
        double tangentSquared = predicted.subtract(outward.scale(predictedRadial)).lengthSqr();

        double stretch = distance - rope - 0.06;
        double soft = stretch > 0
                ? Math.max(0, Math.min(0.22, stretch * 0.16 + predictedRadial * 0.25) - 0.004) : 0;
        if (tangent <= 0.18 && Math.abs(radial) <= 0.16) {
            soft = Math.min(soft, Math.max(0, predictedRadial));
        }
        double radius = rope + Math.min(1.25, rope * 0.10);
        double radialLimit = Math.sqrt(Math.max(0, radius * radius - tangentSquared)) - distance;
        double hard = Mth.clamp(predictedRadial - radialLimit, 0, 3);
        return outward.scale(-Math.max(soft, hard));
    }

    static Vec3 manualRelease(Vec3 velocity, Vec3 forward) {
        double target = Math.min(1.30, Math.max(0, velocity.y) + 0.80);
        double upwardImpulse = Mth.clamp(target - velocity.y, 0, 1.20);
        return forward.scale(upwardImpulse * 0.75).add(0, upwardImpulse, 0);
    }
}
