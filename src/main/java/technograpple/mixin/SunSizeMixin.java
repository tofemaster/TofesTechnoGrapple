package technograpple.mixin;

import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import technograpple.SunEffects;
import technograpple.IrisSunCompatibility;

@Mixin(SkyRenderer.class)
abstract class SunSizeMixin {
    @ModifyArg(method = "renderSun", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/SkyRenderer;applyCelestialBodyTransform(Lcom/mojang/blaze3d/vertex/PoseStack;FF)Lorg/joml/Matrix4f;"),
            index = 2)
    private float technograpple$scaleSun(float size) {
        return size * SunEffects.scale();
    }

    @ModifyArg(method = "renderMoon", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/client/renderer/SkyRenderer;applyCelestialBodyTransform(Lcom/mojang/blaze3d/vertex/PoseStack;FF)Lorg/joml/Matrix4f;"),
            index = 2)
    private float technograpple$hideMoon(float size) {
        return SunEffects.moonHidden() && !IrisSunCompatibility.keepsShaderMoon() ? 0 : size;
    }
}
