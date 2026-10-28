package net.lunacore.luna_npc.client.screen;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.lunacore.luna_npc.api.NpcBodyguardMode;
import net.lunacore.luna_npc.api.NpcTradeStack;
import net.lunacore.luna_npc.client.util.ClientRegistries;
import net.lunacore.luna_npc.job.QuestEvents;
import net.lunacore.luna_npc.network.BodyguardActionPayload;
import net.lunacore.luna_npc.network.BodyguardMenuPayload;
import net.lunacore.luna_npc.npc.NpcEquipment;

// A bodyguard's right-click menu. For a stranger it is the hire offer — the price as item slots and how
// long the hire lasts — with a Hire button; for its hirer it is the orders card: Follow, Stay here,
// Go home and Dismiss, with the current order and the hire time left.
public class BodyguardScreen extends LunaScreen {

    private static final int PANEL_WIDTH = 220;
    private static final int HIRE_HEIGHT = 96;
    private static final int ORDERS_HEIGHT = 116;
    private static final int SLOT = 18;

    private final BodyguardMenuPayload menu;

    public BodyguardScreen(BodyguardMenuPayload menu) {
        super(Component.translatable(menu.hirer() ? "gui.luna_npc.bodyguard.orders_title"
                : "gui.luna_npc.bodyguard.hire_title", menu.npcName()));
        this.menu = menu;
    }

    private int panelHeight() {
        return menu.hirer() ? ORDERS_HEIGHT : HIRE_HEIGHT;
    }

    private int panelX() {
        return this.width / 2 - PANEL_WIDTH / 2;
    }

    private int panelY() {
        return this.height / 2 - panelHeight() / 2;
    }

    @Override
    protected void init() {
        int x = panelX() + 10;
        int width = PANEL_WIDTH - 20;
        if (!menu.hirer()) {
            addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.bodyguard.hire"),
                    button -> send(BodyguardActionPayload.Action.HIRE))
                    .bounds(this.width / 2 - 102, panelY() + panelHeight() + 6, 100, 20).build());
            addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                    .bounds(this.width / 2 + 2, panelY() + panelHeight() + 6, 100, 20).build());
            return;
        }
        int half = (width - 4) / 2;
        int row = panelY() + 46;
        addOrder(x, row, half, NpcBodyguardMode.FOLLOW, BodyguardActionPayload.Action.FOLLOW, "gui.luna_npc.bodyguard.follow");
        addOrder(x + half + 4, row, half, NpcBodyguardMode.STAY, BodyguardActionPayload.Action.STAY, "gui.luna_npc.bodyguard.stay");
        addOrder(x, row + 24, half, NpcBodyguardMode.HOME, BodyguardActionPayload.Action.HOME, "gui.luna_npc.bodyguard.home");
        addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.bodyguard.dismiss"),
                button -> send(BodyguardActionPayload.Action.DISMISS)).bounds(x + half + 4, row + 24, half, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(this.width / 2 - 50, panelY() + panelHeight() + 6, 100, 20).build());
    }

    // The current order's button is pressed in (disabled), like the tab bar.
    private void addOrder(int x, int y, int width, NpcBodyguardMode mode, BodyguardActionPayload.Action action,
                          String key) {
        Button button = addRenderableWidget(Button.builder(Component.translatable(key), pressed -> send(action))
                .bounds(x, y, width, 20).build());
        button.active = menu.mode() != mode;
    }

    private void send(BodyguardActionPayload.Action action) {
        ClientPlayNetworking.send(new BodyguardActionPayload(menu.npcEntityId(), action));
        onClose();
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        titledPanel(graphics, panelX(), panelY(), PANEL_WIDTH, panelHeight(), this.title);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int x = panelX() + 10;
        int y = panelY() + 22;
        if (menu.hirer()) {
            graphics.text(this.font, Component.translatable("gui.luna_npc.bodyguard.current_order",
                    Component.translatable("gui.luna_npc.bodyguard." + menu.mode().getSerializedName())), x, y, LABEL_COLOR);
            graphics.text(this.font, timeLeftLine(), x, y + 12, LABEL_COLOR);
            return;
        }
        graphics.text(this.font, Component.translatable("gui.luna_npc.bodyguard.price"), x, y + 5, LABEL_COLOR);
        if (menu.cost().isEmpty()) {
            graphics.text(this.font, Component.translatable("gui.luna_npc.bodyguard.free"), x + 50, y + 5, SECTION_COLOR);
        }
        for (int index = 0; index < menu.cost().size(); index++) {
            drawStack(graphics, menu.cost().get(index), x + 50 + index * (SLOT + 2), y, mouseX, mouseY);
        }
        graphics.text(this.font, menu.hireSeconds() > 0
                ? Component.translatable("gui.luna_npc.bodyguard.duration", QuestEvents.formatDuration(menu.hireSeconds()))
                : Component.translatable("gui.luna_npc.bodyguard.duration_forever"), x, y + 30, LABEL_COLOR);
        graphics.textWithWordWrap(this.font, Component.translatable("gui.luna_npc.bodyguard.hire_hint"),
                x, y + 44, PANEL_WIDTH - 20, LABEL_COLOR);
    }

    private Component timeLeftLine() {
        return menu.remainingSeconds() < 0
                ? Component.translatable("gui.luna_npc.bodyguard.duration_forever")
                : Component.translatable("gui.luna_npc.bodyguard.time_left", QuestEvents.formatDuration(menu.remainingSeconds()));
    }

    private void drawStack(GuiGraphicsExtractor graphics, NpcTradeStack stack, int x, int y, int mouseX, int mouseY) {
        graphics.fill(x, y, x + SLOT, y + SLOT, 0xFF2A2A34);
        graphics.outline(x, y, SLOT, SLOT, PANEL_BORDER);
        ItemStack rendered = NpcEquipment.resolve(stack.itemId(), stack.enchants(), ClientRegistries.lookup());
        if (rendered.isEmpty()) {
            return;
        }
        rendered.setCount(stack.count());
        graphics.item(rendered, x + 1, y + 1);
        graphics.itemDecorations(this.font, rendered, x + 1, y + 1, stack.count() > 1 ? String.valueOf(stack.count()) : null);
        if (mouseX >= x && mouseX < x + SLOT && mouseY >= y && mouseY < y + SLOT) {
            graphics.setTooltipForNextFrame(this.font, rendered.getHoverName(), mouseX, mouseY);
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(null);
    }
}
