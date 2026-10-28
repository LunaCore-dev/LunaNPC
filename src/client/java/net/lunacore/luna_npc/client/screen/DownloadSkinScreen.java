package net.lunacore.luna_npc.client.screen;

import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.client.ClientSkins;
import net.lunacore.luna_npc.network.DownloadSkinPayload;
import net.lunacore.luna_npc.network.SkinResultPayload;

// The model picker's Download Skin popup: a player name or skin URL and the arm width, downloaded through
// the server. It stays open while the server works (grey status) and on an error (red status, so the input
// can be fixed); a finished download closes it and hands the new skin id back.
public class DownloadSkinScreen extends LunaScreen {

    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 180;
    private static final int STATUS_INFO = 0xFFA0A0A0;
    private static final int STATUS_ERROR = 0xFFFF5555;

    private final Screen parent;
    private final Consumer<String> onDownloaded;
    private EditBox inputField;
    private Button downloadButton;
    private String input = "";
    // 0 = from the profile (classic for a URL), 1 = classic, 2 = slim, as DownloadSkinPayload sends it.
    private int arms;
    private boolean waiting;
    private Component status = Component.empty();
    private int statusColor = STATUS_INFO;
    private int panelX;
    private int panelY;

    public DownloadSkinScreen(Screen parent, Consumer<String> onDownloaded) {
        super(Component.translatable("gui.luna_npc.download_skin.title"));
        this.parent = parent;
        this.onDownloaded = onDownloaded;
    }

    @Override
    protected void init() {
        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - PANEL_HEIGHT) / 2;
        int inner = PANEL_WIDTH - 40;
        inputField = addRenderableWidget(new EditBox(this.font, panelX + 20, panelY + 36, inner, 20,
                Component.translatable("gui.luna_npc.download_skin.input")));
        inputField.setMaxLength(2048);
        inputField.setHint(Component.translatable("gui.luna_npc.download_skin.input.hint"));
        inputField.setValue(input);
        inputField.setResponder(value -> {
            input = value;
            updateButton();
        });
        setInitialFocus(inputField);
        addRenderableWidget(CycleButton.<Integer>builder(value -> Component.translatable(
                        "gui.luna_npc.download_skin.arms." + value), arms)
                .withValues(0, 1, 2)
                .withTooltip(value -> Tooltip.create(Component.translatable("gui.luna_npc.download_skin.arms.tooltip")))
                .create(panelX + 20, panelY + 66, inner, 20, Component.translatable("gui.luna_npc.download_skin.arms"),
                        (button, value) -> arms = value));
        int buttonWidth = (PANEL_WIDTH - 30) / 2;
        int buttonsY = panelY + PANEL_HEIGHT - 26;
        downloadButton = addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.download_skin.download"),
                        button -> download())
                .bounds(panelX + 10, buttonsY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(panelX + PANEL_WIDTH - 10 - buttonWidth, buttonsY, buttonWidth, 20).build());
        ClientSkins.setResultListener(this::onResult);
        updateButton();
    }

    private void updateButton() {
        if (downloadButton != null) {
            downloadButton.active = !waiting && !input.isBlank();
        }
    }

    private void download() {
        waiting = true;
        status = Component.translatable("gui.luna_npc.download_skin.downloading");
        statusColor = STATUS_INFO;
        updateButton();
        ClientPlayNetworking.send(new DownloadSkinPayload(input.strip(), arms));
    }

    private void onResult(SkinResultPayload result) {
        waiting = false;
        if (result.skinId().isEmpty()) {
            status = Component.translatable(result.failureKey());
            statusColor = STATUS_ERROR;
            updateButton();
            return;
        }
        ClientSkins.setResultListener(null);
        onDownloaded.accept(result.skinId());
        Minecraft.getInstance().setScreenAndShow(parent);
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
        int inner = PANEL_WIDTH - 40;
        graphics.text(this.font, Component.translatable("gui.luna_npc.download_skin.input"), panelX + 20,
                panelY + 24, LABEL_COLOR);
        graphics.textWithWordWrap(this.font, Component.translatable("gui.luna_npc.download_skin.hint"),
                panelX + 20, panelY + 92, inner, LABEL_COLOR);
        graphics.textWithWordWrap(this.font, status, panelX + 20, panelY + PANEL_HEIGHT - 48, inner, statusColor);
    }
}
