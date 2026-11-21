package technograpple.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import technograpple.TechnoGrapple;

@Mixin(LivingEntity.class)
abstract class GauntletGroundFrictionMixin {
    @WrapOperation(method = "travelInAir", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(DDD)V", ordinal = 1))
    private void technograpple$preserveGroundSlide(LivingEntity entity, double x, double y, double z,
                                                      Operation<Void> original, @Local(ordinal = 1) Vec3 movement) {
        if (entity instanceof Player player && TechnoGrapple.shouldSlide(player)) {
            // Keep the horizontal velocity from the collision step and retain vanilla vertical drag.
            original.call(entity, movement.x, y, movement.z);
        } else {
            original.call(entity, x, y, z);
        }
    }
}
