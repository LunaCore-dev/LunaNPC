package net.lunacore.luna_npc.client.screen;

import java.util.Locale;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.api.NpcSkin;
import net.lunacore.luna_npc.api.NpcSkinArms;
import net.lunacore.luna_npc.client.ClientSkins;
import net.lunacore.luna_npc.network.EditSkinPayload;
import net.lunacore.luna_npc.network.SkinResultPayload;

// The model picker's Skin Settings popup for one downloaded skin: its name and arm width, saved through the
// server for everyone. A refused name keeps the popup open with the reason in red.
public class EditSkinScreen extends LunaScreen {

    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 140;
    private static final int STATUS_ERROR = 0xFFFF5555;

    private final Screen parent;
    private final NpcSkin skin;
    private String name;
    private NpcSkinArms arms;
    private Component status = Component.empty();
    private int panelX;
    private int panelY;

    public EditSkinScreen(Screen parent, NpcSkin skin) {
        super(Component.translatable("gui.luna_npc.edit_skin.title"));
        this.parent = parent;
        this.skin = skin;
        this.name = skin.name();
        this.arms = skin.arms();
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - PANEL_HEIGHT) / 2;
        int inner = PANEL_WIDTH - 40;
        EditBox nameField = addRenderableWidget(new EditBox(this.font, panelX + 20, panelY + 36, inner, 20,
                Component.translatable("gui.luna_npc.edit_skin.name")));
        nameField.setMaxLength(64);
        nameField.setValue(name);
        nameField.setResponder(value -> {
            String cleaned = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "");
            if (!cleaned.equals(value)) {
                nameField.setValue(cleaned);
                return;
            }
            name = cleaned;
        });
        setInitialFocus(nameField);
        addRenderableWidget(CycleButton.<NpcSkinArms>builder(value -> Component.translatable(
                        "gui.luna_npc.download_skin.arms." + (value == NpcSkinArms.SLIM ? 2 : 1)), arms)
                .withValues(NpcSkinArms.values())
                .create(panelX + 20, panelY + 64, inner, 20, Component.translatable("gui.luna_npc.download_skin.arms"),
                        (button, value) -> arms = value));
        int buttonWidth = (PANEL_WIDTH - 30) / 2;
        int buttonsY = panelY + PANEL_HEIGHT - 26;
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> save())
                .bounds(panelX + 10, buttonsY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(panelX + PANEL_WIDTH - 10 - buttonWidth, buttonsY, buttonWidth, 20).build());
        ClientSkins.setResultListener(this::onResult);
    }

    private void save() {
        if (name.equals(skin.name()) && arms == skin.arms()) {
            onClose();
            return;
        }
        ClientPlayNetworking.send(new EditSkinPayload(skin.id(), name, arms, false));
    }

    private void onResult(SkinResultPayload result) {
        if (result.skinId().isEmpty()) {
            status = Component.translatable(result.failureKey());
            return;
        }
        onClose();
    }

    @Override
    public void onClose() {
        ClientSkins.setResultListener(null);
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        titledPanel(graphics, panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, this.title);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        graphics.text(this.font, Component.translatable("gui.luna_npc.edit_skin.name"), panelX + 20, panelY + 24,
                LABEL_COLOR);
        graphics.textWithWordWrap(this.font, status, panelX + 20, panelY + 90, PANEL_WIDTH - 40, STATUS_ERROR);
    }
}
