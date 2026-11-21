package technograpple.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import technograpple.GauntletClient;
import technograpple.TechnoGrapple;

@Pseudo
@Mixin(targets = "dev.tr7zw.firstperson.LogicHandler", remap = false)
abstract class FirstPersonGlideActivationMixin {
    @ModifyExpressionValue(method = "lambda$registerDefaultHandlers$0()Z", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;isFallFlying()Z"), require = 0)
    private boolean technograpple$renderBodyWithRaisedGauntlet(boolean gliding) {
        if (!gliding) return false;
        var client = Minecraft.getInstance();
        var player = client.player;
        return player == null || client.getCameraEntity() != player
                || !TechnoGrapple.isGauntlet(player.getMainHandItem())
                || !GauntletClient.glideArmRaised(player.getId());
    }
}
