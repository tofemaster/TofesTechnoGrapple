package technograpple.mixin;

import java.lang.reflect.Method;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.fml.ModList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import technograpple.GauntletElbow;
import technograpple.GlideArmView;

@Mixin(value = AvatarRenderer.class, priority = 900)
abstract class GauntletFirstPersonGlideMixin {
    @Unique private static Method technograpple$showLeft;
    @Unique private static Method technograpple$showRight;
    @Unique private static Method technograpple$armOffset;
    @Unique private static Method technograpple$isCameraEntity;
    @Unique private static boolean technograpple$resolved;

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("RETURN"))
    private void technograpple$showRaisedArm(Avatar avatar, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        var view = (GlideArmView) state;
        view.technograpple$setArmOnly(false);
        var client = Minecraft.getInstance();
        if (!state.isFallFlying || avatar != client.player || avatar != client.getCameraEntity()
                || !client.options.getCameraType().isFirstPerson() || !GauntletElbow.isAiming(state)
                || !ModList.get().isLoaded("firstperson")) return;
        try {
            if (!technograpple$resolved) {
                technograpple$resolved = true;
                var access = Class.forName("dev.tr7zw.firstperson.access.LivingEntityRenderStateAccess");
                technograpple$showLeft = access.getMethod("setHideLeftArm", boolean.class);
                technograpple$showRight = access.getMethod("setHideRightArm", boolean.class);
                technograpple$armOffset = access.getMethod("setArmOffset", float.class);
                technograpple$isCameraEntity = access.getMethod("isCameraEntity");
            }
            if (technograpple$isCameraEntity != null && Boolean.TRUE.equals(technograpple$isCameraEntity.invoke(state))) {
                Method show = state.mainArm == HumanoidArm.LEFT ? technograpple$showLeft : technograpple$showRight;
                show.invoke(state, false);
                technograpple$armOffset.invoke(state, 0F);
                view.technograpple$setArmOnly(true);
            }
        } catch (ReflectiveOperationException | LinkageError unavailable) {
            technograpple$isCameraEntity = null;
        }
    }
}
