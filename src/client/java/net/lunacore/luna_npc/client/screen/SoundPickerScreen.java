package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.lunacore.luna_npc.client.util.FuzzySearch;
import net.lunacore.luna_npc.client.widget.SimpleSelectionList;

// Searchable scrollable sound list; typing filters, clicking an entry previews it, the path field
// accepts any id and the play button tests whether it exists before playing.
public class SoundPickerScreen extends LunaScreen {

    private static final int LIST_TOP = 54;

    private final Screen parent;
    private final Consumer<String> onSelected;
    private final List<String> allSoundIds;

    private SimpleSelectionList soundList;
    private EditBox pathField;
    private String path;
    private boolean suppressFilterUpdate;

    public SoundPickerScreen(Screen parent, String currentSoundId, Consumer<String> onSelected) {
        super(Component.translatable("gui.luna_npc.sound_picker.title"));
        this.parent = parent;
        this.onSelected = onSelected;
        this.path = currentSoundId;
        this.allSoundIds = Minecraft.getInstance().getSoundManager().getAvailableSounds().stream()
                .map(Identifier::toString)
                .sorted()
                .toList();
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;

        pathField = addRenderableWidget(new EditBox(this.font, centerX - 110, 28, 196, 20, this.title));
        pathField.setMaxLength(200);
        pathField.setValue(path);
        pathField.setResponder(value -> {
            path = value;
            if (!suppressFilterUpdate) {
                rebuildList();
            }
        });
        addRenderableWidget(Button.builder(Component.literal("▶"), button -> playSound(path))
                .bounds(centerX + 90, 28, 20, 20).build());

        soundList = addRenderableWidget(new SimpleSelectionList(220, this.height - LIST_TOP - 40, LIST_TOP));
        soundList.setX(centerX - 110);

        addRenderableWidget(Button.builder(Component.translatable("gui.luna_npc.sound_picker.select"), button -> {
            onSelected.accept(path);
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
        for (String soundId : allSoundIds) {
            int rank = FuzzySearch.score(path, soundId);
            if (rank < 0) {
                continue;
            }
            SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(soundId,
                    Component.literal(soundId), this::selectSound);
            scored.add(new Scored(entry, rank));
            if (soundId.equals(path)) {
                selected = entry;
            }
        }
        scored.sort((a, b) -> Integer.compare(b.score(), a.score()));
        List<SimpleSelectionList.Entry> entries = new ArrayList<>(scored.size());
        for (Scored s : scored) {
            entries.add(s.entry());
        }
        soundList.fill(entries, selected);
    }

    private void selectSound(String soundId) {
        if (soundId.equals(path)) {
            return;
        }
        path = soundId;
        suppressFilterUpdate = true;
        pathField.setValue(soundId);
        suppressFilterUpdate = false;
        playSound(soundId);
    }

    private void playSound(String soundId) {
        Identifier parsed = Identifier.tryParse(soundId.trim());
        if (parsed == null) {
            return;
        }
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        if (soundManager.getSoundEvent(parsed) != null) {
            soundManager.play(SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(parsed), 1.0F));
        }
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreenAndShow(parent);
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        if (soundList == null) {
            return;
        }
        int top = pathField.getY() - 5;
        int bottom = soundList.getY() + soundList.getHeight() + 5;
        panel(graphics, soundList.getX() - 5, top, soundList.getWidth() + 10, bottom - top);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        screenTitle(graphics);
        if (soundList.children().isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("gui.luna_npc.npc_list.empty"),
                    this.width / 2, LIST_TOP + 40, LABEL_COLOR);
        }
    }
}
