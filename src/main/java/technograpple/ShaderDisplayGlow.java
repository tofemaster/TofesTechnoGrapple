package technograpple;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

record ShaderDisplayGlow() implements ConditionalItemModelProperty {
    static final MapCodec<ShaderDisplayGlow> CODEC = MapCodec.unit(new ShaderDisplayGlow());

    @Override
    public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner, int seed,
                       ItemDisplayContext context) {
        return IrisSunCompatibility.shadersActive();
    }

    @Override
    public MapCodec<ShaderDisplayGlow> type() {
        return CODEC;
    }
}
