package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.client.util.FuzzySearch;
import net.lunacore.luna_npc.client.widget.SimpleSelectionList;

// Searchable particle list for the impact visuals, following the sound picker layout. Only
// data-free particle types are offered (anything needing colors or block states cannot spawn
// from a bare id); clearing the field and selecting means "no particles".
public class ParticlePickerScreen extends LunaScreen {

    private static final int LIST_TOP = 54;

    private final Screen parent;
    private final Consumer<String> onSelected;
    private final List<String> allParticleIds;

    private SimpleSelectionList particleList;
    private EditBox pathField;
    private String path;
    private boolean suppressFilterUpdate;

    public ParticlePickerScreen(Screen parent, String currentParticleId, Consumer<String> onSelected) {
        super(Component.translatable("gui.luna_npc.particle_picker.title"));
        this.parent = parent;
        this.onSelected = onSelected;
        this.path = currentParticleId;
        this.allParticleIds = BuiltInRegistries.PARTICLE_TYPE.stream()
                .filter(type -> type instanceof SimpleParticleType)
                .map(type -> String.valueOf(BuiltInRegistries.PARTICLE_TYPE.getKey(type)))
                .sorted()
                .toList();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;

        pathField = addRenderableWidget(new EditBox(this.font, centerX - 110, 28, 220, 20, this.title));
        pathField.setMaxLength(200);
        pathField.setHint(Component.translatable("gui.luna_npc.particle_picker.none_hint"));
        pathField.setValue(path);
        pathField.setResponder(value -> {
            path = value;
            if (!suppressFilterUpdate) {
                rebuildList();
            }
        });

        particleList = addRenderableWidget(new SimpleSelectionList(220, this.height - LIST_TOP - 40, LIST_TOP));
        particleList.setX(centerX - 110);

        addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.sound_picker.select"), button -> {
            onSelected.accept(path.trim());
            onClose();
        }).bounds(centerX - 102, this.height - 28, 100, 20).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(centerX + 2, this.height - 28, 100, 20).build());

        rebuildList();
    }

    private void rebuildList() {
        record Scored(SimpleSelectionList.Entry entry, int score) {
        }
        List<Scored> scored = new ArrayList<>();
        SimpleSelectionList.Entry selected = null;
        for (String particleId : allParticleIds) {
            int rank = FuzzySearch.score(path, particleId);
            if (rank < 0) {
                continue;
            }
            SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(particleId,
                    Component.literal(particleId), this::selectParticle);
            scored.add(new Scored(entry, rank));
            if (particleId.equals(path)) {
                selected = entry;
            }
        }
        scored.sort((a, b) -> Integer.compare(b.score(), a.score()));
        List<SimpleSelectionList.Entry> entries = new ArrayList<>(scored.size());
        for (Scored s : scored) {
            entries.add(s.entry());
        }
        particleList.fill(entries, selected);
    }

    private void selectParticle(String particleId) {
        path = particleId;
        suppressFilterUpdate = true;
        pathField.setValue(particleId);
        suppressFilterUpdate = false;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        if (particleList == null) {
            return;
        }
        int top = pathField.getY() - 5;
        int bottom = particleList.getY() + particleList.getHeight() + 5;
        panel(graphics, particleList.getX() - 5, top, particleList.getWidth() + 10, bottom - top);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        screenTitle(graphics);
        if (particleList.children().isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("gui.luna_npc.npc_list.empty"),
                    this.width / 2, LIST_TOP + 40, LABEL_COLOR);
        }
    }
}
