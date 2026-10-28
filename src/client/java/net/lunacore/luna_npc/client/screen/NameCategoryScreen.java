package net.lunacore.luna_npc.client.screen;

import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.npc.NpcValidation;

// In-game name prompt for a new custom model category, so importing never drops the player out to a
// native OS dialog. One centred card with a single text field and a confirm/cancel pair; confirm hands
// the raw text back to the caller and returns to the model picker.
public class NameCategoryScreen extends LunaScreen {

    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 96;
    private static final Component PROMPT = Component.translatable("gui.luna_npc.model_picker.name_prompt");

    private final Screen parent;
    private final String defaultName;
    private final Consumer<String> onConfirm;
    private EditBox nameField;
    private int panelX;
    private int panelY;

    public NameCategoryScreen(Screen parent, String defaultName, Consumer<String> onConfirm) {
        super(Component.translatable("gui.luna_npc.model_picker.name_title"));
        this.parent = parent;
        this.defaultName = defaultName;
        this.onConfirm = onConfirm;
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - PANEL_HEIGHT) / 2;
        nameField = addRenderableWidget(new EditBox(this.font, panelX + 20, panelY + 34, PANEL_WIDTH - 40, 20,
                Component.empty()));
        nameField.setMaxLength(NpcValidation.MAX_MODEL_CATEGORY_LENGTH);
        nameField.setValue(defaultName);
        setInitialFocus(nameField);

        int buttonsY = panelY + PANEL_HEIGHT - 26;
        int buttonWidth = (PANEL_WIDTH - 30) / 2;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> confirm())
                .bounds(panelX + 10, buttonsY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(panelX + PANEL_WIDTH - 10 - buttonWidth, buttonsY, buttonWidth, 20).build());
    }

    private void confirm() {
        String value = nameField.getValue().trim();
        if (!value.isEmpty()) {
            onConfirm.accept(value);
        }
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        titledPanel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, this.title);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(this.font, PROMPT, panelX + 20, panelY + 22, LABEL_COLOR);
    }
}
