package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// A DIALOG NPC's one fixed conversation tree. Nodes reference each other by id (a flat list), so the
// tree nests without limit and choices may loop or converge. There is no weighting and no per-spawn
// roll — the story is deterministic; branching comes only from the player's answers. The two toggles
// are snapshotted into per-player progress when a conversation starts, deciding whether that progress
// survives the NPC's or the player's death.
public record NpcDialog(String dialogId, String name, String startNodeId, List<NpcDialogNode> nodes,
                        boolean storySurvivesNpcDeath, boolean storySurvivesPlayerDeath) {

    public NpcDialog {
        nodes = List.copyOf(nodes);
    }

    private static final StreamCodec<ByteBuf, List<NpcDialogNode>> NODE_LIST =
            NpcDialogNode.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final Codec<NpcDialog> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("id", "").forGetter(NpcDialog::dialogId),
            Codec.STRING.optionalFieldOf("name", "").forGetter(NpcDialog::name),
            Codec.STRING.optionalFieldOf("start", "").forGetter(NpcDialog::startNodeId),
            NpcDialogNode.CODEC.listOf().optionalFieldOf("nodes", List.of()).forGetter(NpcDialog::nodes),
            Codec.BOOL.optionalFieldOf("survive_npc_death", false)
                    .forGetter(NpcDialog::storySurvivesNpcDeath),
            Codec.BOOL.optionalFieldOf("survive_player_death", true)
                    .forGetter(NpcDialog::storySurvivesPlayerDeath)
    ).apply(instance, NpcDialog::new));

    public static final StreamCodec<ByteBuf, NpcDialog> STREAM_CODEC = StreamCodec.of(
            (buffer, dialog) -> {
                ByteBufCodecs.STRING_UTF8.encode(buffer, dialog.dialogId());
                ByteBufCodecs.STRING_UTF8.encode(buffer, dialog.name());
                ByteBufCodecs.STRING_UTF8.encode(buffer, dialog.startNodeId());
                NODE_LIST.encode(buffer, dialog.nodes());
                ByteBufCodecs.BOOL.encode(buffer, dialog.storySurvivesNpcDeath());
                ByteBufCodecs.BOOL.encode(buffer, dialog.storySurvivesPlayerDeath());
            },
            buffer -> new NpcDialog(
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    NODE_LIST.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer)));

    public static NpcDialog defaults() {
        return new NpcDialog("", "", "", List.of(), false, true);
    }

    public NpcDialogNode nodeById(String id) {
        for (NpcDialogNode node : nodes) {
            if (node.nodeId().equals(id)) {
                return node;
            }
        }
        return null;
    }

    // A dialog is interactable only once it has a start node that exists — an empty dialog is a valid
    // but inert placeholder.
    public boolean hasStart() {
        return !startNodeId.isBlank() && nodeById(startNodeId) != null;
    }

    public boolean isValid() {
        if (dialogId == null || name == null || startNodeId == null || nodes == null) {
            return false;
        }
        Set<String> ids = new HashSet<>();
        for (NpcDialogNode node : nodes) {
            if (!node.isValid() || !ids.add(node.nodeId())) {
                return false;
            }
        }
        if (nodes.isEmpty()) {
            return true;
        }
        if (!ids.contains(startNodeId)) {
            return false;
        }
        for (NpcDialogNode node : nodes) {
            for (NpcDialogChoice choice : node.choices()) {
                if (!choice.targetNodeId().isBlank() && !ids.contains(choice.targetNodeId())) {
                    return false;
                }
            }
        }
        return true;
    }
}
