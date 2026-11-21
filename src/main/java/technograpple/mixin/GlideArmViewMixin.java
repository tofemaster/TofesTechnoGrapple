package technograpple.mixin;

import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import technograpple.GlideArmView;

@Mixin(AvatarRenderState.class)
abstract class GlideArmViewMixin implements GlideArmView {
    @Unique private boolean technograpple$armOnly;

    @Override
    public boolean technograpple$armOnly() {
        return technograpple$armOnly;
    }

    @Override
    public void technograpple$setArmOnly(boolean armOnly) {
        technograpple$armOnly = armOnly;
    }
}
