package technograpple.mixin;

import net.minecraft.client.renderer.LevelRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import technograpple.IrisSunCompatibility;
import technograpple.SunEffects;

@Mixin(LevelRenderer.class)
abstract class SunSizeMixin {
    @ModifyConstant(method = "renderSky", constant = @Constant(floatValue = 30F))
    private float technograpple$scaleSun(float size) {
        return size * SunEffects.scale();
    }

    @ModifyConstant(method = "renderSky", constant = @Constant(floatValue = 20F))
    private float technograpple$hideMoon(float size) {
        return SunEffects.moonHidden() && !IrisSunCompatibility.keepsShaderMoon() ? 0 : size;
    }
}
