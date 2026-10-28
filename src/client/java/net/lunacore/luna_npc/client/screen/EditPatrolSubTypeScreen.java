package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.api.NpcPatrolMode;
import net.lunacore.luna_npc.api.NpcPatrolSpeed;
import net.lunacore.luna_npc.api.NpcPatrolSubType;
import net.lunacore.luna_npc.api.NpcRoute;
import net.lunacore.luna_npc.client.ClientRoutes;
import net.lunacore.luna_npc.client.widget.LabeledSlider;
import net.lunacore.luna_npc.client.widget.SimpleSelectionList;
import net.lunacore.luna_npc.npc.NpcValidation;

// One weighted patrol variant: its weight, the saved route it walks (picked from the Route Creator's
// routes), the visiting order, and whether it keeps the route's walk/run speeds or always walks / runs.
// Hands the rebuilt variant back to the Jobs tab on close.
public class EditPatrolSubTypeScreen extends LunaScreen {

    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 236;
    private static final int ROUTE_TITLE_OFFSET = 88;
    private static final int LIST_TOP_OFFSET = 100;
    private static final int LIST_HEIGHT = 98;

    private final LunaScreen parent;
    private final Consumer<NpcPatrolSubType> onApply;
    private int weight;
    private String routeId;
    private NpcPatrolMode mode;
    private NpcPatrolSpeed speed;

    private Button modeButton;
    private Button speedButton;
    private SimpleSelectionList routeList;

    public EditPatrolSubTypeScreen(LunaScreen parent, NpcPatrolSubType subType, Consumer<NpcPatrolSubType> onApply) {
        super(Component.translatable("gui.luna_npc.edit_patrol.title"));
        this.parent = parent;
        this.onApply = onApply;
        this.weight = subType.weightPercent();
        this.routeId = subType.routeId();
        this.mode = subType.mode();
        this.speed = subType.speed();
    }

    private int panelX() {
        return this.width / 2 - PANEL_WIDTH / 2;
    }

    private int panelY() {
        return this.height / 2 - PANEL_HEIGHT / 2;
    }

    @Override
    protected void init() {
        int contentX = panelX() + 12;
        int top = panelY();
        int width = PANEL_WIDTH - 24;
        LabeledSlider weightSlider = new LabeledSlider(contentX, top + 16, width, 18,
                Component.translatable("gui.luna_npc.edit_subtype.weight"), 0,
                NpcValidation.JOB_WEIGHT_TOTAL_PERCENT, weight,
                value -> Math.round(value) + "%", value -> weight = Math.round(value));
        weightSlider.setTooltip(Tooltip.create(Component.translatable("gui.luna_npc.edit_subtype.weight.tooltip")));
        addRenderableWidget(weightSlider);

        modeButton = addRenderableWidget(Button.builder(modeLabel(), button -> {
            NpcPatrolMode[] modes = NpcPatrolMode.values();
            mode = modes[(mode.ordinal() + 1) % modes.length];
            modeButton.setMessage(modeLabel());
            modeButton.setTooltip(modeTooltip());
        }).bounds(contentX, top + 40, width, 20).tooltip(modeTooltip()).build());

        speedButton = addRenderableWidget(Button.builder(speedLabel(), button -> {
            NpcPatrolSpeed[] speeds = NpcPatrolSpeed.values();
            speed = speeds[(speed.ordinal() + 1) % speeds.length];
            speedButton.setMessage(speedLabel());
        }).bounds(contentX, top + 62, width, 20)
                .tooltip(Tooltip.create(Component.translatable("gui.luna_npc.edit_patrol.speed.tooltip"))).build());

        routeList = addRenderableWidget(new SimpleSelectionList(width, LIST_HEIGHT, top + LIST_TOP_OFFSET));
        routeList.setX(contentX);
        rebuildList();

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> onClose())
                .bounds(this.width / 2 - 50, top + PANEL_HEIGHT - 28, 100, 20).build());
    }

    private void rebuildList() {
        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        SimpleSelectionList.Entry selected = null;
        for (NpcRoute route : ClientRoutes.all()) {
            Component label = Component.literal(route.name() + "  ").append(Component.translatable(
                    "gui.luna_npc.route.point_count", route.size()).withStyle(ChatFormatting.GRAY));
            SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(route.id(), label, id -> routeId = id);
            entries.add(entry);
            if (route.id().equals(routeId)) {
                selected = entry;
            }
        }
        routeList.fill(entries, selected);
    }

    private Component modeLabel() {
        return Component.translatable("gui.luna_npc.edit_patrol.mode").append(": ")
                .append(Component.translatable("gui.luna_npc.edit_patrol.mode." + mode.getSerializedName()));
    }

    private Tooltip modeTooltip() {
        return Tooltip.create(Component.translatable("gui.luna_npc.edit_patrol.mode." + mode.getSerializedName() + ".tooltip"));
    }

    private Component speedLabel() {
        return Component.translatable("gui.luna_npc.edit_patrol.speed").append(": ")
                .append(Component.translatable("gui.luna_npc.edit_patrol.speed." + speed.getSerializedName()));
    }

    @Override
    public void onClose() {
        onApply.accept(new NpcPatrolSubType(weight, routeId, mode, speed));
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        titledPanel(graphics, panelX(), panelY(), PANEL_WIDTH, PANEL_HEIGHT, this.title);
        graphics.outline(panelX() + 11, panelY() + LIST_TOP_OFFSET - 1, PANEL_WIDTH - 22, LIST_HEIGHT + 2, PANEL_BORDER);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int contentX = panelX() + 12;
        int top = panelY();
        graphics.text(this.font, Component.translatable("gui.luna_npc.edit_patrol.route"),
                contentX, top + ROUTE_TITLE_OFFSET, SECTION_COLOR);
        if (ClientRoutes.all().isEmpty()) {
            graphics.textWithWordWrap(this.font, Component.translatable("gui.luna_npc.edit_patrol.no_routes"),
                    contentX + 4, top + LIST_TOP_OFFSET + 6, PANEL_WIDTH - 32, LABEL_COLOR);
        } else if (routeId.isBlank() || ClientRoutes.byId(routeId) == null) {
            Component hint = Component.translatable("gui.luna_npc.edit_patrol.pick_route");
            graphics.text(this.font, hint, panelX() + PANEL_WIDTH - 12 - this.font.width(hint),
                    top + ROUTE_TITLE_OFFSET, 0xFFE33B3B);
        }
    }
}
