package technograpple;

import net.minecraft.network.FriendlyByteBuf;
import java.util.UUID;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

record DartAmmo(UUID player, int availableTypes) {
    private static final String ROPE_SELECTED = "technograpple_rope_selected";
    private static final String TANDEM_SELECTED = "technograpple_tandem_selected";
    static final int WHIP = 1;
    static final int GRIP = 2;
    static final int TANDEM = WHIP | GRIP;
    void encode(FriendlyByteBuf buffer) {
        buffer.writeUUID(player);
        buffer.writeVarInt(availableTypes);
    }

    static DartAmmo decode(FriendlyByteBuf buffer) {
        return new DartAmmo(buffer.readUUID(), buffer.readVarInt());
    }

    static boolean isDart(ItemStack stack) {
        return stack.is(TechnoGrapple.DART.get()) || stack.is(TechnoGrapple.ROPE_DART.get());
    }

    static int reloadMode(Player player) {
        ItemStack offhand = player.getOffhandItem();
        if (!isDart(offhand)) return 0;
        int current = mode(player.getMainHandItem());
        int offhandType = typeMask(offhand.is(TechnoGrapple.ROPE_DART.get()));
        if (current == offhandType) return 0;
        if (!player.isShiftKeyDown()) return offhandType;
        return current == TANDEM ? 0 : TANDEM;
    }

    static int mode(ItemStack tool) {
        var tag = tool.getTag();
        if (tag == null) return WHIP;
        if (tag.getBoolean(TANDEM_SELECTED)) return TANDEM;
        return typeMask(tag.getBoolean(ROPE_SELECTED));
    }

    static boolean firesGrip(Player player) {
        int loaded = mode(player.getMainHandItem());
        return loaded == GRIP || (loaded == TANDEM && player.isShiftKeyDown());
    }

    static boolean gripOnRail(int mode, int rail) {
        return mode == GRIP || (mode == TANDEM && rail == tandemRail(true));
    }

    static int tandemRail(boolean grip) {
        return grip ? RailState.PORT : RailState.STARBOARD;
    }

    static int typeMask(boolean rope) {
        return rope ? GRIP : WHIP;
    }

    static int availableTypes(Player player) {
        return (find(player, false).isEmpty() ? 0 : WHIP) | (find(player, true).isEmpty() ? 0 : GRIP);
    }

    static ItemStack find(Player player, boolean rope) {
        for (ItemStack stack : player.getInventory().items) {
            if (stack.is((rope ? TechnoGrapple.ROPE_DART : TechnoGrapple.DART).get())) return stack;
        }
        ItemStack offhand = player.getOffhandItem();
        return offhand.is((rope ? TechnoGrapple.ROPE_DART : TechnoGrapple.DART).get()) ? offhand : ItemStack.EMPTY;
    }

    static boolean reload(Player player, int mode) {
        ItemStack offhand = player.getOffhandItem();
        var slots = player.getInventory().items;
        int remaining = offhand.getCount();
        for (ItemStack slot : slots) {
            if (slot.isEmpty()) remaining -= offhand.getMaxStackSize();
            else if (ItemStack.isSameItemSameTags(slot, offhand)) {
                remaining -= Math.max(0, slot.getMaxStackSize() - slot.getCount());
            }
            if (remaining <= 0) break;
        }
        if (remaining > 0) return false;

        ItemStack moved = offhand.copy();
        for (int i = 0; i < slots.size() && !moved.isEmpty(); i++) {
            ItemStack slot = slots.get(i);
            if (slot.isEmpty()) slots.set(i, moved.split(moved.getMaxStackSize()));
            else if (ItemStack.isSameItemSameTags(slot, moved)) {
                int count = Math.min(moved.getCount(), Math.max(0, slot.getMaxStackSize() - slot.getCount()));
                slot.grow(count);
                moved.shrink(count);
            }
        }
        player.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
        var tag = player.getMainHandItem().getOrCreateTag();
        if (mode == GRIP) tag.putBoolean(ROPE_SELECTED, true);
        else tag.remove(ROPE_SELECTED);
        if (mode == TANDEM) tag.putBoolean(TANDEM_SELECTED, true);
        else tag.remove(TANDEM_SELECTED);
        player.getInventory().setChanged();
        return true;
    }
}
