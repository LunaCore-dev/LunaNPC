package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.api.NpcAttackEffect;
import net.lunacore.luna_npc.api.NpcCombatAttack;
import net.lunacore.luna_npc.api.NpcCombatAttackKind;
import net.lunacore.luna_npc.api.NpcCombatPlacement;
import net.lunacore.luna_npc.npc.NpcValidation;

// Edits one library attack: its name, kind, damage/range/speed, the projectile item and its homing/curve
// and multishot/spread tuning, the effects it carries and the placement its shots or blasts spawn from.
public class EditCombatAttackScreen extends LunaScreen {

    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 212;
    private static final int COL_GAP = 10;
    private static final int NUM_LABEL_W = 44;
    private static final int Y_NAME = 24;
    private static final int Y_KIND = 48;
    private static final int Y_DMG = 70;
    private static final int Y_SPD = 92;
    private static final int Y_SPREAD = 114;
    private static final int Y_CURVE = 136;
    private static final int Y_PLACEMENT = 158;

    private final LunaScreen parent;
    private final Consumer<NpcCombatAttack> onDone;

    private String name;
    private NpcCombatAttackKind kind;
    private float damage;
    private float range;
    private float speedBps;
    private String projectileItemId;
    private boolean homing;
    private boolean curve;
    private int projectileCount;
    private float spreadDegrees;
    private final List<NpcAttackEffect> effects;
    private NpcCombatPlacement placement;

    private Button itemButton;

    public EditCombatAttackScreen(LunaScreen parent, NpcCombatAttack current,
                                  Consumer<NpcCombatAttack> onDone) {
        super(Component.translatable("gui.luna_npc.combat.attack.title"));
        this.parent = parent;
        this.onDone = onDone;
        this.name = current.name();
        this.kind = current.kind();
        this.damage = current.damage();
        this.range = current.range();
        this.speedBps = current.speedBps();
        this.projectileItemId = current.projectileItemId();
        this.homing = current.homing();
        this.curve = current.curve();
        this.projectileCount = current.projectileCount();
        this.spreadDegrees = current.spreadDegrees();
        this.effects = new ArrayList<>(current.effects());
        this.placement = current.placement();
    }

    private int panelX() {
        return this.width / 2 - PANEL_WIDTH / 2;
    }

    private int panelY() {
        return this.height / 2 - PANEL_HEIGHT / 2;
    }

    private int col1X() {
        return panelX() + 12;
    }

    private int col2X() {
        return col1X() + colW() + COL_GAP;
    }

    private int colW() {
        return (PANEL_WIDTH - 24 - COL_GAP) / 2;
    }

    @Override
    protected void init() {
        int top = panelY();
        int inner = PANEL_WIDTH - 24;

        EditBox nameField = addRenderableWidget(new EditBox(this.font, col1X() + NUM_LABEL_W, top + Y_NAME,
                inner - NUM_LABEL_W, 20, Component.translatable("gui.luna_npc.combat.attack.name")));
        nameField.setMaxLength(NpcValidation.MAX_NAME_LENGTH);
        nameField.setValue(name);
        nameField.setResponder(text -> name = text);

        addRenderableWidget(CycleButton.<NpcCombatAttackKind>builder(CombatLabels::attackKind, kind)
                .withValues(NpcCombatAttackKind.values())
                .create(col1X(), top + Y_KIND, colW(), 20,
                        Component.translatable("gui.luna_npc.combat.attack.kind"),
                        (button, selected) -> kind = selected));
        itemButton = addRenderableWidget(Button.builder(itemLabel(), button -> pickItem())
                .bounds(col2X(), top + Y_KIND, colW(), 20).build());

        addNumeric(col1X(), top + Y_DMG, damage, v -> damage = Math.max(0.0F, v));
        addNumeric(col2X(), top + Y_DMG, range, v -> range = Math.max(0.0F, v));
        addNumeric(col1X(), top + Y_SPD, speedBps, v -> speedBps = Math.max(0.0F, v));
        addNumeric(col2X(), top + Y_SPD, projectileCount, v -> projectileCount = Math.max(1, Math.round(v)));
        addNumeric(col1X(), top + Y_SPREAD, spreadDegrees, v -> spreadDegrees = Math.max(0.0F, v));

        addRenderableWidget(CycleButton.booleanBuilder(CommonComponents.GUI_YES, CommonComponents.GUI_NO, homing)
                .create(col2X(), top + Y_SPREAD, colW(), 20,
                        Component.translatable("gui.luna_npc.combat.attack.homing"),
                        (button, value) -> homing = value));
        addRenderableWidget(CycleButton.booleanBuilder(CommonComponents.GUI_YES, CommonComponents.GUI_NO, curve)
                .create(col1X(), top + Y_CURVE, colW(), 20,
                        Component.translatable("gui.luna_npc.combat.attack.curve"),
                        (button, value) -> curve = value));
        addRenderableWidget(Button.builder(
                        Component.translatable("gui.luna_npc.combat.attack.effects", effects.size()),
                        button -> openEffects())
                .bounds(col2X(), top + Y_CURVE, colW(), 20).build());
        addRenderableWidget(Button.builder(placementLabel(), button -> openPlacement())
                .bounds(col1X(), top + Y_PLACEMENT, inner, 20).build());

        int buttonWidth = (inner - 6) / 2;
        int buttonY = top + PANEL_HEIGHT - 26;
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL,
                        button -> Minecraft.getInstance().setScreenAndShow(parent))
                .bounds(col1X(), buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> save())
                .bounds(col1X() + buttonWidth + 6, buttonY, buttonWidth, 20).build());
    }

    private void addNumeric(int x, int y, float initial, Consumer<Float> onValue) {
        EditBox field = addRenderableWidget(new EditBox(this.font, x + NUM_LABEL_W, y, colW() - NUM_LABEL_W,
                20, Component.empty()));
        field.setValue(CombatText.number(initial));
        field.setResponder(text -> CombatText.parse(text).ifPresent(onValue));
    }

    private Component itemLabel() {
        String id = projectileItemId.isBlank() ? "—" : projectileItemId.replaceFirst(".*:", "");
        return Component.translatable("gui.luna_npc.combat.attack.item").copy().append(": " + id);
    }

    private Component placementLabel() {
        return Component.translatable("gui.luna_npc.combat.attack.placement").copy().append(": ")
                .append(CombatLabels.placementSummary(placement));
    }

    private void pickItem() {
        Minecraft.getInstance().setScreenAndShow(new ItemPickerScreen(this, null,
                value -> projectileItemId = value));
    }

    private void openEffects() {
        Minecraft.getInstance().setScreenAndShow(new TargetEffectScreen(this,
                Component.translatable("gui.luna_npc.combat.attack.title"), effects,
                NpcValidation.ENCHANT_EFFECTS));
    }

    private void openPlacement() {
        Minecraft.getInstance().setScreenAndShow(new CombatPlacementScreen(this, placement,
                updated -> placement = updated));
    }

    private void save() {
        onDone.accept(new NpcCombatAttack(name, kind, damage, range, speedBps, projectileItemId, homing,
                curve, projectileCount, spreadDegrees, List.copyOf(effects), placement));
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        titledPanel(graphics, panelX(), panelY(), PANEL_WIDTH, PANEL_HEIGHT, this.title);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        itemButton.setMessage(itemLabel());
        int top = panelY();
        label(graphics, "gui.luna_npc.combat.attack.name", col1X(), top + Y_NAME);
        label(graphics, "gui.luna_npc.combat.attack.damage", col1X(), top + Y_DMG);
        label(graphics, "gui.luna_npc.combat.attack.range", col2X(), top + Y_DMG);
        label(graphics, "gui.luna_npc.combat.attack.speed", col1X(), top + Y_SPD);
        label(graphics, "gui.luna_npc.combat.attack.count", col2X(), top + Y_SPD);
        label(graphics, "gui.luna_npc.combat.attack.spread", col1X(), top + Y_SPREAD);
    }

    private void label(GuiGraphicsExtractor graphics, String key, int x, int y) {
        graphics.text(this.font, Component.translatable(key), x, y + 6, LABEL_COLOR);
    }
}
