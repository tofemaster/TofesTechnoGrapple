package technograpple.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.renderer.SkyRenderer;
import org.joml.Matrix4f;
import org.joml.Matrix4fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import technograpple.SunEffects;
import technograpple.IrisSunCompatibility;

@Mixin(SkyRenderer.class)
abstract class SunSizeMixin {
    @WrapOperation(method = "renderSun", at = @At(value = "INVOKE", target =
            "Lorg/joml/Matrix4fStack;scale(FFF)Lorg/joml/Matrix4f;"))
    private Matrix4f technograpple$scaleSun(Matrix4fStack view, float x, float y, float z,
                                           Operation<Matrix4f> original) {
        float scale = SunEffects.scale();
        return original.call(view, x * scale, y, z * scale);
    }

    @WrapOperation(method = "renderMoon", at = @At(value = "INVOKE", target =
            "Lorg/joml/Matrix4fStack;scale(FFF)Lorg/joml/Matrix4f;"))
    private Matrix4f technograpple$hideMoon(Matrix4fStack view, float x, float y, float z,
                                           Operation<Matrix4f> original) {
        if (SunEffects.moonHidden() && !IrisSunCompatibility.keepsShaderMoon()) {
            return original.call(view, 0F, y, 0F);
        }
        return original.call(view, x, y, z);
    }
}
