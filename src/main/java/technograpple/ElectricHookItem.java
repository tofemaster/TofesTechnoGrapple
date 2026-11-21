package technograpple;

import com.faktocraft.common.energy.impl.ItemEnergyWrapper;
import com.faktocraft.common.energy.interfaces.IEnergy;
import com.faktocraft.common.enums.EnergyTier;
import com.faktocraft.common.enums.EnergyType;
import com.faktocraft.common.enums.EnumLang;
import com.faktocraft.common.interfaces.item.IElectricItem;
import com.faktocraft.common.util.TextComponentUtil;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;

final class ElectricHookItem extends TechnoGrapple.HookItem implements IElectricItem {
    private static final int CAPACITY = 80_000;
    private static final int LAUNCH_COST = 320;

    static TechnoGrapple.HookItem create(Properties properties) {
        return new ElectricHookItem(properties);
    }

    private ElectricHookItem(Properties properties) {
        super(properties);
    }

    @Override
    public EnergyTier getEnergyTier() {
        return EnergyTier.MEDIUM;
    }

    @Override
    public EnergyType getEnergyType() {
        return EnergyType.RECEIVE;
    }

    @Override
    public IEnergy getEnergy(ItemStack stack) {
        return new ItemEnergyWrapper(stack, CAPACITY, getEnergyType(), getEnergyTier());
    }

    @Override
    boolean hasLaunchPower(ItemStack stack) {
        return getEnergy(stack).energyStored() >= LAUNCH_COST;
    }

    @Override
    ItemStack creativeSample(boolean depleted) {
        ItemStack stack = new ItemStack(this);
        getEnergy(stack).setEnergy(depleted ? 0 : CAPACITY);
        return stack;
    }

    @Override
    void spendLaunchPower(ItemStack stack) {
        getEnergy(stack).consumeEnergy(LAUNCH_COST, false);
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(13.0F * getEnergy(stack).energyStored() / CAPACITY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return 0xFF0000;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
            Consumer<Component> tooltip, TooltipFlag flag) {
        EnergyTier tier = getEnergyTier();
        tooltip.accept(EnumLang.POWER_TIER.getTranslationComponent(
                tier.getLang().getTranslationComponent().withStyle(tier.getColor()))
                .withStyle(ChatFormatting.GRAY)
                .append(Component.literal(" (" + TextComponentUtil.getFormattedLong(tier.getBasicTransfer()) + " IE/t)")
                        .withStyle(ChatFormatting.DARK_GRAY)));
        tooltip.accept(EnumLang.STORED.getTranslationComponent(TextComponentUtil.build(
                energyText(getEnergy(stack).energyStored()),
                Component.literal(" / ").withStyle(ChatFormatting.GRAY),
                energyText(CAPACITY))).withStyle(ChatFormatting.GRAY));
        super.appendHoverText(stack, context, display, tooltip, flag);
    }

    private MutableComponent energyText(int amount) {
        return EnumLang.POWER.getTranslationComponent(TextComponentUtil.getFormattedEnergyUnit(amount))
                .withStyle(getEnergyTier().getColor());
    }
}
