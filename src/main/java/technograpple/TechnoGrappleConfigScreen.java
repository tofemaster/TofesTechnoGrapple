package technograpple;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.fml.ModList;

final class TechnoGrappleConfigScreen extends Screen {
    private static final Identifier ICON = Identifier.fromNamespaceAndPath(
            TechnoGrapple.ID, "textures/gui/config_icon.png");
    private static final int ICON_TEXTURE_SIZE = 128;
    private static final int ICON_SIZE = 40;
    private static final int TITLE_GAP = 6;
    private static final int CREDITS_TOP = 54;
    private static final int CREDIT_LINE_HEIGHT = 11;
    private final Screen parent;
    private List<FormattedCharSequence> creditLines = List.of();

    TechnoGrappleConfigScreen(Screen parent) {
        super(Component.translatable("tofestechnograpple.configuration.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        var container = ModList.get().getModContainerById(TechnoGrapple.ID).orElseThrow();
        var metadata = container.getModInfo().getConfig();
        int textWidth = Math.max(1, width - 24);
        var lines = new ArrayList<FormattedCharSequence>();
        metadata.<String>getConfigElement("authors").ifPresent(authors ->
                lines.addAll(font.split(Component.translatable("fml.menu.mods.info.authors", authors), textWidth)));
        metadata.<String>getConfigElement("credits").ifPresent(credits ->
                lines.addAll(font.split(Component.translatable("fml.menu.mods.info.credits", credits), textWidth)));
        creditLines = lines;

        int left = width / 2 - 100;
        int top = Math.max(CREDITS_TOP + creditLines.size() * CREDIT_LINE_HEIGHT + 8, height / 2 - 20);
        addRenderableWidget(CycleButton.onOffBuilder(TechnoGrappleConfig.hctsEnabled())
                .withTooltip(value -> Tooltip.create(Component.translatable("tofestechnograpple.configuration.hcts.tooltip")))
                .create(left, top, 200, 20,
                        Component.translatable("tofestechnograpple.configuration.hcts"),
                        (button, value) -> TechnoGrappleConfig.setHctsEnabled(value)));
        addRenderableWidget(CycleButton.onOffBuilder(TechnoGrappleConfig.vanillaLikeEnabled())
                .withTooltip(value -> Tooltip.create(Component.translatable("tofestechnograpple.configuration.vanilla_like.tooltip")))
                .create(left, top + 24, 200, 20,
                        Component.translatable("tofestechnograpple.configuration.vanilla_like"),
                        (button, value) -> TechnoGrappleConfig.setVanillaLikeEnabled(value)));
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(left, height - 32, 200, 20)
                .build());
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int left = (width - ICON_SIZE - TITLE_GAP - font.width(title)) / 2;
        graphics.blit(RenderPipelines.GUI_TEXTURED, ICON, left, 8, 0, 0, ICON_SIZE, ICON_SIZE,
                ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE, ICON_TEXTURE_SIZE);
        graphics.text(font, title, left + ICON_SIZE + TITLE_GAP, 24, 0xFFFFFFFF);
        for (int i = 0; i < creditLines.size(); i++) {
            graphics.centeredText(font, creditLines.get(i), width / 2, CREDITS_TOP + i * CREDIT_LINE_HEIGHT, 0xFFD0D0D0);
        }
    }

    @Override
    public void onClose() {
        if (minecraft != null) minecraft.setScreen(parent);
    }
}
