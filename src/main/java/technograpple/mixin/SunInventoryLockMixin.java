package technograpple.mixin;

import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import technograpple.TechnoGrapple;

@Mixin(Inventory.class)
abstract class SunInventoryLockMixin {
    @Shadow @Final public Player player;

    @Inject(method = "swapPaint", at = @At("HEAD"), cancellable = true)
    private void technograpple$lockScroll(double amount, CallbackInfo result) {
        if (TechnoGrapple.sunLocksHeldItem(player)) result.cancel();
    }

    @Inject(method = "pickSlot", at = @At("HEAD"), cancellable = true)
    private void technograpple$lockPick(int slot, CallbackInfo result) {
        if (TechnoGrapple.sunLocksHeldItem(player)) result.cancel();
    }

    @Inject(method = "setPickedItem", at = @At("HEAD"), cancellable = true)
    private void technograpple$lockCreativePick(ItemStack stack, CallbackInfo result) {
        if (TechnoGrapple.sunLocksHeldItem(player)) result.cancel();
    }

    @Inject(method = "removeFromSelected", at = @At("HEAD"), cancellable = true)
    private void technograpple$lockDrop(boolean all, CallbackInfoReturnable<ItemStack> result) {
        if (TechnoGrapple.sunLocksHeldItem(player)) result.setReturnValue(ItemStack.EMPTY);
    }
}
