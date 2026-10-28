package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.api.NpcAttackEffect;
import net.lunacore.luna_npc.api.NpcCombatAction;
import net.lunacore.luna_npc.api.NpcCombatActionType;
import net.lunacore.luna_npc.api.NpcCombatPlacement;
import net.lunacore.luna_npc.api.NpcNavMode;
import net.lunacore.luna_npc.api.NpcTargetSelector;
import net.lunacore.luna_npc.api.NpcValueSource;
import net.lunacore.luna_npc.npc.NpcValidation;

// Edits one action. The action type is a tagged union, so only the fields that type actually consumes are
// shown (see CombatActionExecutor): a target selector, a placement, a dynamic amount, duration/delay ticks,
// one context-labelled string argument (attack name, entity id, particle, sound, nav mode or free text),
// an effect list and a boolean flag. Changing the type relays out the panel to the new field set.
public class CombatActionScreen extends LunaScreen {

    private static final int PANEL_WIDTH = 252;
    private static final int PANEL_HEIGHT = 216;
    private static final int LABEL_W = 80;

    private enum ArgKind { NONE, TEXT, ENTITY, PARTICLE, SOUND, NAV, ATTACK }

    private final LunaScreen parent;
    private final Consumer<NpcCombatAction> onDone;
    private final List<String> attackNames;

    private NpcCombatActionType type;
    private NpcTargetSelector target;
    private NpcCombatPlacement placement;
    private NpcValueSource amount;
    private int durationTicks;
    private int delayTicks;
    private String stringArg;
    private final List<NpcAttackEffect> effects;
    private boolean flag;

    private final List<AbstractWidget> dynamic = new ArrayList<>();
    private final List<LabelDraw> labels = new ArrayList<>();

    private record LabelDraw(String key, int y) {
    }

    public CombatActionScreen(LunaScreen parent, NpcCombatAction current, List<String> attackNames,
                              Consumer<NpcCombatAction> onDone) {
        super(Component.translatable("gui.luna_npc.combat.action.title"));
        this.parent = parent;
        this.onDone = onDone;
        this.attackNames = attackNames;
        this.type = current.type();
        this.target = current.target();
        this.placement = current.placement();
        this.amount = current.amount();
        this.durationTicks = current.durationTicks();
        this.delayTicks = current.delayTicks();
        this.stringArg = current.stringArg();
        this.effects = new ArrayList<>(current.effects());
        this.flag = current.flag();
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
        int fieldWidth = PANEL_WIDTH - 24;
        int top = panelY();

        addRenderableWidget(CycleButton.<NpcCombatActionType>builder(CombatLabels::action, type)
                .withValues(NpcCombatActionType.values())
                .create(contentX, top + 24, fieldWidth, 20,
                        Component.translatable("gui.luna_npc.combat.action.type"),
                        (button, selected) -> {
                            type = selected;
                            rebuildControls();
                        }));

        int buttonWidth = (fieldWidth - 6) / 2;
        int buttonY = top + PANEL_HEIGHT - 26;
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL,
                        button -> Minecraft.getInstance().setScreenAndShow(parent))
                .bounds(contentX, buttonY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> save())
                .bounds(contentX + buttonWidth + 6, buttonY, buttonWidth, 20).build());

        rebuildControls();
    }

    private void rebuildControls() {
        for (AbstractWidget widget : dynamic) {
            removeWidget(widget);
        }
        dynamic.clear();
        labels.clear();
        int contentX = panelX() + 12;
        int fieldWidth = PANEL_WIDTH - 24;
        int y = panelY() + 50;

        if (usesTarget()) {
            add(CycleButton.<NpcTargetSelector>builder(CombatLabels::target, target)
                    .withValues(NpcTargetSelector.values())
                    .create(contentX, y, fieldWidth, 20,
                            Component.translatable("gui.luna_npc.combat.action.target"),
                            (button, selected) -> target = selected));
            y += 22;
        }
        ArgKind kind = argKind();
        if (kind == ArgKind.ATTACK) {
            y = addAttackControl(contentX, y, fieldWidth);
        } else if (kind == ArgKind.NAV) {
            add(CycleButton.<NpcNavMode>builder(CombatLabels::navMode, navFromArg())
                    .withValues(NpcNavMode.values())
                    .create(contentX, y, fieldWidth, 20,
                            Component.translatable("gui.luna_npc.combat.action.nav_mode"),
                            (button, selected) -> stringArg = selected.getSerializedName()));
            y += 22;
        } else if (kind == ArgKind.PARTICLE || kind == ArgKind.SOUND) {
            add(Button.builder(pickLabel(kind), button -> openPicker(kind))
                    .bounds(contentX, y, fieldWidth, 20).build());
            y += 22;
        } else if (kind == ArgKind.TEXT || kind == ArgKind.ENTITY) {
            EditBox field = new EditBox(this.font, contentX + LABEL_W, y, fieldWidth - LABEL_W, 20,
                    Component.empty());
            field.setMaxLength(256);
            field.setValue(stringArg);
            field.setResponder(text -> stringArg = text);
            add(field);
            labels.add(new LabelDraw(stringArgLabelKey(), y));
            y += 22;
        }
        if (usesAmount()) {
            add(Button.builder(summaryLabel(amountLabelKey(), CombatLabels.valueSourceSummary(amount)),
                    button -> openAmount()).bounds(contentX, y, fieldWidth, 20).build());
            y += 22;
        }
        if (usesPlacement()) {
            add(Button.builder(summaryLabel("gui.luna_npc.combat.action.placement",
                            CombatLabels.placementSummary(placement)), button -> openPlacement())
                    .bounds(contentX, y, fieldWidth, 20).build());
            y += 22;
        }
        if (usesEffects()) {
            add(Button.builder(Component.translatable("gui.luna_npc.combat.action.effects", effects.size()),
                    button -> openEffects()).bounds(contentX, y, fieldWidth, 20).build());
            y += 22;
        }
        if (usesDuration()) {
            add(numeric(contentX + LABEL_W, y, fieldWidth - LABEL_W, durationTicks,
                    v -> durationTicks = Math.max(0, Math.round(v))));
            labels.add(new LabelDraw("gui.luna_npc.combat.action.duration", y));
            y += 22;
        }
        add(numeric(contentX + LABEL_W, y, fieldWidth - LABEL_W, delayTicks,
                v -> delayTicks = Math.max(0, Math.round(v))));
        labels.add(new LabelDraw("gui.luna_npc.combat.action.delay", y));
        y += 22;
        if (usesFlag()) {
            add(CycleButton.booleanBuilder(CommonComponents.GUI_YES, CommonComponents.GUI_NO, flag)
                    .create(contentX, y, fieldWidth, 20, Component.translatable(flagLabelKey()),
                            (button, value) -> flag = value));
        }
    }

    private int addAttackControl(int contentX, int y, int fieldWidth) {
        if (attackNames.isEmpty()) {
            Button none = Button.builder(Component.translatable("gui.luna_npc.combat.action.no_attacks"),
                    button -> {
                    }).bounds(contentX, y, fieldWidth, 20).build();
            none.active = false;
            add(none);
            return y + 22;
        }
        String initial = attackNames.contains(stringArg) ? stringArg : attackNames.get(0);
        stringArg = initial;
        add(CycleButton.<String>builder(Component::literal, initial)
                .withValues(attackNames)
                .create(contentX, y, fieldWidth, 20,
                        Component.translatable("gui.luna_npc.combat.action.attack"),
                        (button, selected) -> stringArg = selected));
        return y + 22;
    }

    private <T extends AbstractWidget> void add(T widget) {
        addRenderableWidget(widget);
        dynamic.add(widget);
    }

    private EditBox numeric(int x, int y, int width, float initial, Consumer<Float> onValue) {
        EditBox field = new EditBox(this.font, x, y, width, 20, Component.empty());
        field.setValue(CombatText.number(initial));
        field.setResponder(text -> CombatText.parse(text).ifPresent(onValue));
        return field;
    }

    private NpcNavMode navFromArg() {
        for (NpcNavMode mode : NpcNavMode.values()) {
            if (mode.getSerializedName().equals(stringArg)) {
                return mode;
            }
        }
        return NpcNavMode.GROUND;
    }

    private Component pickLabel(ArgKind kind) {
        String key = kind == ArgKind.SOUND
                ? "gui.luna_npc.combat.action.sound" : "gui.luna_npc.combat.action.particle";
        return summaryLabel(key, Component.literal(stringArg.isBlank() ? "—" : stringArg));
    }

    private Component summaryLabel(String key, Component value) {
        return Component.translatable(key).copy().append(": ").append(value);
    }

    private void openPicker(ArgKind kind) {
        if (kind == ArgKind.SOUND) {
            Minecraft.getInstance().setScreenAndShow(new SoundPickerScreen(this, stringArg,
                    value -> stringArg = value));
        } else {
            Minecraft.getInstance().setScreenAndShow(new ParticlePickerScreen(this, stringArg,
                    value -> stringArg = value));
        }
    }

    private void openAmount() {
        Minecraft.getInstance().setScreenAndShow(new CombatValueSourceScreen(this, amount,
                updated -> amount = updated));
    }

    private void openPlacement() {
        Minecraft.getInstance().setScreenAndShow(new CombatPlacementScreen(this, placement,
                updated -> placement = updated));
    }

    private void openEffects() {
        Minecraft.getInstance().setScreenAndShow(new TargetEffectScreen(this,
                CombatLabels.action(type), effects, NpcValidation.ENCHANT_EFFECTS));
    }

    private boolean usesTarget() {
        return switch (type) {
            case USE_ATTACK, APPLY_STATUS, CLEANSE, KNOCKBACK, PULL, SWITCH_AGGRO, TAUNT -> true;
            default -> false;
        };
    }

    private boolean usesPlacement() {
        return switch (type) {
            case EXPLOSION, TELEGRAPH, SUMMON, TELEPORT, MOVE_TO, PARTICLE, PLACE_HAZARD -> true;
            default -> false;
        };
    }

    private boolean usesAmount() {
        return switch (type) {
            case EXPLOSION, HEAL, SHIELD, GUARD, KNOCKBACK, PULL, SET_FLAG, INC_COUNTER, ENRAGE, PLACE_HAZARD,
                 SWITCH_NAV_MODE -> true;
            default -> false;
        };
    }

    private boolean usesDuration() {
        return switch (type) {
            case TELEGRAPH, SHIELD, INVULN, GUARD, UNTARGETABLE, SWITCH_NAV_MODE, START_TIMER, ENRAGE,
                 PLACE_HAZARD, WAIT -> true;
            default -> false;
        };
    }

    private boolean usesEffects() {
        return switch (type) {
            case EXPLOSION, BUFF_SELF, APPLY_STATUS, PLACE_HAZARD -> true;
            default -> false;
        };
    }

    private boolean usesFlag() {
        return type == NpcCombatActionType.EXPLOSION || type == NpcCombatActionType.SET_FLAG
                || type == NpcCombatActionType.SWITCH_NAV_MODE;
    }

    private ArgKind argKind() {
        return switch (type) {
            case USE_ATTACK -> ArgKind.ATTACK;
            case SUMMON -> ArgKind.ENTITY;
            case TELEGRAPH, PARTICLE -> ArgKind.PARTICLE;
            case SOUND -> ArgKind.SOUND;
            case SWITCH_NAV_MODE -> ArgKind.NAV;
            case SET_FLAG, CLEAR_FLAG, INC_COUNTER, START_TIMER, RUN_COMMAND, CUSTOM_EVENT, BOSS_BAR ->
                    ArgKind.TEXT;
            default -> ArgKind.NONE;
        };
    }

    private String stringArgLabelKey() {
        return switch (type) {
            case SUMMON -> "gui.luna_npc.combat.action.entity";
            case SET_FLAG, CLEAR_FLAG -> "gui.luna_npc.combat.action.flag_key";
            case INC_COUNTER -> "gui.luna_npc.combat.action.counter_key";
            case START_TIMER -> "gui.luna_npc.combat.action.timer_key";
            case RUN_COMMAND -> "gui.luna_npc.combat.action.command";
            case CUSTOM_EVENT -> "gui.luna_npc.combat.action.event_key";
            case BOSS_BAR -> "gui.luna_npc.combat.action.boss_bar_text";
            default -> "gui.luna_npc.combat.action.arg";
        };
    }

    private String flagLabelKey() {
        return switch (type) {
            case EXPLOSION -> "gui.luna_npc.combat.action.flag_terrain";
            case SWITCH_NAV_MODE -> "gui.luna_npc.combat.action.flag_fixed_height";
            default -> "gui.luna_npc.combat.action.flag_value";
        };
    }

    private String amountLabelKey() {
        return type == NpcCombatActionType.SWITCH_NAV_MODE
                ? "gui.luna_npc.combat.action.height" : "gui.luna_npc.combat.action.amount";
    }

    private void save() {
        onDone.accept(new NpcCombatAction(type, target, placement, amount, durationTicks, delayTicks,
                stringArg, List.copyOf(effects), flag));
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
        for (LabelDraw label : labels) {
            graphics.text(this.font, Component.translatable(label.key()), panelX() + 12, label.y() + 6,
                    LABEL_COLOR);
        }
        // Summoning a LunaNPC that persists, respawns or is unallied misbehaves as an add; the exact check
        // needs the registry, so in-game this is a reminder (the server logs the precise reason at spawn).
        if (type == NpcCombatActionType.SUMMON && stringArg.startsWith("luna:")) {
            graphics.textWithWordWrap(this.font,
                    Component.translatable("gui.luna_npc.combat.action.summon_warning"),
                    panelX() + 12, panelY() + PANEL_HEIGHT - 46, PANEL_WIDTH - 24, 0xFFE0A040);
        }
    }
}
