package technograpple.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import technograpple.TechnoGrapple;

@Mixin(LivingEntity.class)
abstract class GauntletGroundFrictionMixin {
    @Redirect(method = "travel", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;setDeltaMovement(DDD)V", ordinal = 1),
            slice = @Slice(from = @At(value = "INVOKE", target = "Lnet/minecraft/world/entity/LivingEntity;shouldDiscardFriction()Z")))
    private void technograpple$preserveGroundSlide(LivingEntity entity, double x, double y, double z) {
        if (entity instanceof Player player && TechnoGrapple.shouldSlide(player)) {
            var movement = entity.getDeltaMovement();
            entity.setDeltaMovement(movement.x, y, movement.z);
        } else {
            entity.setDeltaMovement(x, y, z);
        }
    }
}
