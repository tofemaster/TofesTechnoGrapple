package technograpple.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface ItemRenderLayerAccessor {
    @Invoker("applyTransform")
    void technograpple$applyTransform(PoseStack.Pose pose);
}
