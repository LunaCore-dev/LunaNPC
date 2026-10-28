package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.client.widget.SimpleSelectionList;
import net.lunacore.luna_npc.api.NpcCombatAttack;
import net.lunacore.luna_npc.api.NpcCombatAttackKind;
import net.lunacore.luna_npc.api.NpcCombatPlacement;
import net.lunacore.luna_npc.npc.NpcValidation;

// The named attack library a boss's USE_ATTACK actions reference. Names must be unique (a colliding edit is
// auto-suffixed rather than blocked) and the editor caps the count; the rules keep their string reference,
// so renaming an attack is the one thing that can orphan a USE_ATTACK — the same soft-warning contract as
// the runtime attackByName lookup.
public class AttackLibraryScreen extends LunaScreen {

    private static final int PANEL_WIDTH = 264;
    private static final int LIST_TOP = 54;
    private static final int LIST_HEIGHT = 150;
    private static final int ROW_HEIGHT = 18;
    private static final int PANEL_HEIGHT = LIST_TOP + LIST_HEIGHT + 62;

    private final Screen parent;
    private final Consumer<List<NpcCombatAttack>> onDone;
    private final List<NpcCombatAttack> attacks;
    private int selected = -1;

    private SimpleSelectionList list;
    private Button addButton;
    private Button editButton;
    private Button removeButton;

    public AttackLibraryScreen(Screen parent, List<NpcCombatAttack> current,
                               Consumer<List<NpcCombatAttack>> onDone) {
        super(Component.translatable("gui.luna_npc.combat.library.title"));
        this.parent = parent;
        this.onDone = onDone;
        this.attacks = new ArrayList<>(current);
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

        list = addRenderableWidget(new SimpleSelectionList(fieldWidth, LIST_HEIGHT, top + LIST_TOP, ROW_HEIGHT));
        list.setX(contentX);

        int bw = (fieldWidth - 4) / 3;
        int rowY = top + LIST_TOP + LIST_HEIGHT + 4;
        addButton = addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.combat.library.add"),
                button -> addAttack()).bounds(contentX, rowY, bw, 18).build());
        editButton = addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.combat.library.edit"),
                button -> editAttack()).bounds(contentX + bw + 2, rowY, bw, 18).build());
        removeButton = addRenderableWidget(Button.builder(
                Component.translatable("gui.luna_npc.combat.library.remove"), button -> removeAttack())
                .bounds(contentX + 2 * (bw + 2), rowY, bw, 18).build());

        int buttonWidth = (fieldWidth - 6) / 2;
        int doneY = top + PANEL_HEIGHT - 26;
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(contentX, doneY, buttonWidth, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, button -> save())
                .bounds(contentX + buttonWidth + 6, doneY, buttonWidth, 20).build());

        rebuild();
    }

    private void addAttack() {
        if (attacks.size() >= NpcValidation.MAX_COMBAT_ATTACKS_GUI) {
            return;
        }
        NpcCombatAttack fresh = new NpcCombatAttack(uniqueName("attack", -1), NpcCombatAttackKind.MELEE,
                5.0F, 3.0F, 20.0F, "minecraft:arrow", false, false, 1, 0.0F, List.of(),
                NpcCombatPlacement.atTarget());
        Minecraft.getInstance().setScreenAndShow(new EditCombatAttackScreen(this, fresh, updated -> {
            attacks.add(withUniqueName(updated, -1));
            selected = attacks.size() - 1;
        }));
    }

    private void editAttack() {
        if (selected < 0 || selected >= attacks.size()) {
            return;
        }
        int index = selected;
        Minecraft.getInstance().setScreenAndShow(new EditCombatAttackScreen(this, attacks.get(index),
                updated -> attacks.set(index, withUniqueName(updated, index))));
    }

    private void removeAttack() {
        if (selected < 0 || selected >= attacks.size()) {
            return;
        }
        attacks.remove(selected);
        selected = -1;
        rebuild();
    }

    private NpcCombatAttack withUniqueName(NpcCombatAttack attack, int excludeIndex) {
        String unique = uniqueName(attack.name().isBlank() ? "attack" : attack.name(), excludeIndex);
        return unique.equals(attack.name()) ? attack : new NpcCombatAttack(unique, attack.kind(),
                attack.damage(), attack.range(), attack.speedBps(), attack.projectileItemId(), attack.homing(),
                attack.curve(), attack.projectileCount(), attack.spreadDegrees(), attack.effects(),
                attack.placement());
    }

    private String uniqueName(String base, int excludeIndex) {
        String candidate = base;
        int suffix = 2;
        while (taken(candidate, excludeIndex)) {
            candidate = base + suffix++;
        }
        return candidate;
    }

    private boolean taken(String candidate, int excludeIndex) {
        for (int i = 0; i < attacks.size(); i++) {
            if (i != excludeIndex && attacks.get(i).name().equals(candidate)) {
                return true;
            }
        }
        return false;
    }

    private void rebuild() {
        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        SimpleSelectionList.Entry chosen = null;
        for (int i = 0; i < attacks.size(); i++) {
            NpcCombatAttack attack = attacks.get(i);
            SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(String.valueOf(i),
                    entryLabel(attack), value -> selected = Integer.parseInt(value));
            entries.add(entry);
            if (i == selected) {
                chosen = entry;
            }
        }
        if (chosen == null) {
            selected = -1;
        }
        list.fill(entries, chosen);
        addButton.active = attacks.size() < NpcValidation.MAX_COMBAT_ATTACKS_GUI;
    }

    private static Component entryLabel(NpcCombatAttack attack) {
        return Component.literal(attack.name() + "  ").append(CombatLabels.attackKind(attack.kind()));
    }

    private void save() {
        onDone.accept(List.copyOf(attacks));
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        titledPanel(graphics, panelX(), panelY(), PANEL_WIDTH, PANEL_HEIGHT, this.title);
        graphics.outline(panelX() + 12, panelY() + LIST_TOP, PANEL_WIDTH - 24, LIST_HEIGHT, PANEL_BORDER);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        editButton.active = selected >= 0;
        removeButton.active = selected >= 0;
        graphics.text(this.font, Component.translatable("gui.luna_npc.combat.library.count", attacks.size()),
                panelX() + 12, panelY() + 42, SECTION_COLOR);
    }
}
