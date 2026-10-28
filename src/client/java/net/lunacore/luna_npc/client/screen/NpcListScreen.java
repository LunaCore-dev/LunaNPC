package net.lunacore.luna_npc.client.screen;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.lunacore.luna_npc.api.NpcEditAccess;
import net.lunacore.luna_npc.client.widget.SimpleSelectionList;
import net.lunacore.luna_npc.network.NpcListPayload;
import net.lunacore.luna_npc.network.NpcSummary;
import net.lunacore.luna_npc.network.SpawnNpcPayload;
import net.lunacore.luna_npc.network.ToggleFavoritePayload;

public class NpcListScreen extends LunaScreen {

    private static final int LIST_TOP = 28;
    private static final int CATEGORY_LIST_WIDTH = 110;
    private static final int NPC_LIST_WIDTH = 180;

    // Real categories are lowercase a-z0-9, so ids starting with $ can never collide with them.
    private static final String CATEGORY_FAVORITES = "$favorites";
    private static final String CATEGORY_ALL = "$all";
    private static final Identifier LOCK_SPRITE = Identifier.withDefaultNamespace("container/cartography_table/locked");
    private static final int LOCKED_COLOR = 0xFF808080;
    // Only the API may edit these: they show a lock in both lists and cannot be opened in the editor.
    private static final SimpleSelectionList.IconRenderer LOCK_ICON = (graphics, x, yMiddle) ->
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, LOCK_SPRITE, x + 1, yMiddle - 5, 7, 10);
    private static final SimpleSelectionList.IconRenderer NO_ICON = (graphics, x, yMiddle) -> { };

    private final boolean forSpawn;
    private final BlockPos clickedPos;
    private final List<NpcSummary> npcs;

    private SimpleSelectionList categoryList;
    private SimpleSelectionList npcList;
    private Button favoriteButton;
    private Button actionButton;
    private String selectedCategory = CATEGORY_ALL;
    private long selectedLunaId = -1;

    public NpcListScreen(NpcListPayload payload) {
        super(Component.translatable(payload.forSpawn()
                ? "gui.luna_npc.spawn_npc.title" : "gui.luna_npc.edit_npc.title"));
        this.forSpawn = payload.forSpawn();
        this.clickedPos = payload.clickedPos();
        this.npcs = new ArrayList<>(payload.npcs());
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        // The Edit list keeps a line free under its panel for the locked-NPC hint.
        int listHeight = this.height - LIST_TOP - (forSpawn ? 64 : 78);
        int npcListX = forSpawn ? centerX - 64 : centerX - NPC_LIST_WIDTH / 2;

        if (forSpawn) {
            categoryList = addRenderableWidget(new SimpleSelectionList(CATEGORY_LIST_WIDTH, listHeight, LIST_TOP));
            categoryList.setX(centerX - 180);
        }
        npcList = addRenderableWidget(new SimpleSelectionList(NPC_LIST_WIDTH, listHeight, LIST_TOP));
        npcList.setX(npcListX);

        int buttonsY = this.height - 54;
        if (forSpawn) {
            favoriteButton = addRenderableWidget(Button.builder(
                            Component.translatable("gui.luna_npc.npc_list.favorite"), button -> toggleFavorite())
                    .bounds(npcListX, buttonsY, 88, 20).build());
            actionButton = addRenderableWidget(Button.builder(
                            Component.translatable("gui.luna_npc.npc_list.spawn"), button -> spawnSelected())
                    .bounds(npcListX + 92, buttonsY, 88, 20).build());
        } else {
            actionButton = addRenderableWidget(Button.builder(
                            Component.translatable("gui.luna_npc.npc_list.edit"), button -> editSelected())
                    .bounds(npcListX, buttonsY, NPC_LIST_WIDTH, 20).build());
        }
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> onClose())
                .bounds(centerX - 50, this.height - 28, 100, 20).build());

        if (forSpawn) {
            rebuildCategoryList();
        }
        rebuildNpcList();
    }

    private List<String> distinctCategories() {
        TreeSet<String> categories = new TreeSet<>();
        for (NpcSummary npc : npcs) {
            if (!npc.category().isEmpty()) {
                categories.add(npc.category());
            }
        }
        return List.copyOf(categories);
    }

    private void rebuildCategoryList() {
        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        entries.add(new SimpleSelectionList.Entry(CATEGORY_FAVORITES,
                Component.literal("★ ").withStyle(ChatFormatting.GOLD)
                        .append(Component.translatable("gui.luna_npc.npc_list.favorites")
                                .withStyle(ChatFormatting.GOLD)),
                this::selectCategory));
        entries.add(new SimpleSelectionList.Entry(CATEGORY_ALL,
                Component.translatable("gui.luna_npc.npc_list.all"), this::selectCategory));
        for (String category : distinctCategories()) {
            entries.add(new SimpleSelectionList.Entry(category, Component.literal(category), this::selectCategory));
        }
        SimpleSelectionList.Entry selected = entries.stream()
                .filter(entry -> entry.id.equals(selectedCategory)).findFirst().orElse(entries.get(1));
        categoryList.fill(entries, selected);
    }

    private void rebuildNpcList() {
        List<SimpleSelectionList.Entry> entries = new ArrayList<>();
        SimpleSelectionList.Entry selected = null;
        boolean anyLocked = npcs.stream().anyMatch(NpcListScreen::isLocked);
        for (NpcSummary npc : npcs) {
            if (!isInSelectedCategory(npc)) {
                continue;
            }
            Component label = Component.literal(npc.name())
                    .append(Component.literal(" (" + npc.lunaId() + ")").withStyle(ChatFormatting.GRAY))
                    .append(npc.favorite()
                            ? Component.literal(" ★").withStyle(ChatFormatting.GOLD) : Component.empty());
            boolean locked = isLocked(npc);
            SimpleSelectionList.Entry entry = new SimpleSelectionList.Entry(String.valueOf(npc.lunaId()), label,
                    this::selectNpc, anyLocked ? (locked ? LOCK_ICON : NO_ICON) : null,
                    locked && !forSpawn ? LOCKED_COLOR : 0xFFFFFFFF);
            entries.add(entry);
            if (npc.lunaId() == selectedLunaId) {
                selected = entry;
            }
        }
        if (selected == null) {
            selectedLunaId = -1;
        }
        npcList.fill(entries, selected);
        updateButtonState();
    }

    private static boolean isLocked(NpcSummary npc) {
        return !npc.editAccess().guiCanEdit();
    }

    private boolean isInSelectedCategory(NpcSummary npc) {
        if (!forSpawn || CATEGORY_ALL.equals(selectedCategory)) {
            return true;
        }
        if (CATEGORY_FAVORITES.equals(selectedCategory)) {
            return npc.favorite();
        }
        return selectedCategory.equals(npc.category());
    }

    private void selectCategory(String categoryId) {
        if (!categoryId.equals(selectedCategory)) {
            selectedCategory = categoryId;
            rebuildNpcList();
        }
    }

    private void selectNpc(String lunaIdText) {
        selectedLunaId = Long.parseLong(lunaIdText);
        updateButtonState();
    }

    private void updateButtonState() {
        NpcSummary npc = selectedNpc();
        boolean hasSelection = npc != null;
        actionButton.active = hasSelection && (forSpawn || !isLocked(npc));
        if (favoriteButton != null) {
            favoriteButton.active = hasSelection;
        }
    }

    private NpcSummary selectedNpc() {
        return npcs.stream().filter(npc -> npc.lunaId() == selectedLunaId).findFirst().orElse(null);
    }

    private void toggleFavorite() {
        NpcSummary npc = selectedNpc();
        if (npc == null) {
            return;
        }
        boolean newFavorite = !npc.favorite();
        ClientPlayNetworking.send(new ToggleFavoritePayload(npc.lunaId(), newFavorite));
        npcs.replaceAll(entry -> entry.lunaId() == npc.lunaId() ? entry.withFavorite(newFavorite) : entry);
        rebuildNpcList();
    }

    private void spawnSelected() {
        NpcSummary npc = selectedNpc();
        if (npc != null) {
            ClientPlayNetworking.send(new SpawnNpcPayload(npc.lunaId(), clickedPos));
            onClose();
        }
    }

    private void editSelected() {
        NpcSummary npc = selectedNpc();
        if (npc != null && !isLocked(npc)) {
            Minecraft.getInstance().setScreenAndShow(new CreateNpcScreen(npc));
        }
    }

    @Override
    protected void extractPanels(GuiGraphicsExtractor graphics) {
        if (npcList == null) {
            return;
        }
        if (categoryList != null) {
            listPanel(graphics, categoryList);
        }
        listPanel(graphics, npcList);
    }

    private void listPanel(GuiGraphicsExtractor graphics, SimpleSelectionList list) {
        panel(graphics, list.getX() - 5, list.getY() - 5, list.getWidth() + 10, list.getHeight() + 10);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        int centerX = this.width / 2;
        graphics.centeredText(this.font, this.title, centerX, 10, 0xFFFFFFFF);
        if (npcs.isEmpty()) {
            graphics.centeredText(this.font, Component.translatable("gui.luna_npc.npc_list.empty"),
                    centerX, LIST_TOP + 40, 0xFFA0A0A0);
        }
        NpcSummary npc = selectedNpc();
        if (!forSpawn && npc != null && isLocked(npc)) {
            graphics.centeredText(this.font, Component.translatable("gui.luna_npc.npc_list.locked"),
                    centerX, this.height - 67, 0xFFA0A0A0);
        }
    }
}
