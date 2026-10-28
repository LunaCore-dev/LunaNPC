package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.npc.NpcValidation;

// One step of a conversation: a line the NPC speaks, the actions it runs on entry, and the answer
// choices it offers (empty = a terminal node the player just closes). interactSound plays when the node
// is shown, triggerSound when its actions fire. graphX/graphY are the node's saved position on the
// editor canvas — runtime ignores them.
public record NpcDialogNode(String nodeId, String speakerLine, List<NpcDialogChoice> choices,
                            List<NpcDialogAction> actions, String interactSound, String triggerSound,
                            int graphX, int graphY) {

    public NpcDialogNode {
        choices = List.copyOf(choices);
        actions = List.copyOf(actions);
    }

    private static final StreamCodec<ByteBuf, List<NpcDialogChoice>> CHOICE_LIST =
            NpcDialogChoice.STREAM_CODEC.apply(ByteBufCodecs.list());
    private static final StreamCodec<ByteBuf, List<NpcDialogAction>> ACTION_LIST =
            NpcDialogAction.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final Codec<NpcDialogNode> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(NpcDialogNode::nodeId),
            Codec.STRING.optionalFieldOf("line", "").forGetter(NpcDialogNode::speakerLine),
            NpcDialogChoice.CODEC.listOf().optionalFieldOf("choices", List.of())
                    .forGetter(NpcDialogNode::choices),
            NpcDialogAction.CODEC.listOf().optionalFieldOf("actions", List.of())
                    .forGetter(NpcDialogNode::actions),
            Codec.STRING.optionalFieldOf("interact_sound", "").forGetter(NpcDialogNode::interactSound),
            Codec.STRING.optionalFieldOf("trigger_sound", "").forGetter(NpcDialogNode::triggerSound),
            Codec.INT.optionalFieldOf("x", 0).forGetter(NpcDialogNode::graphX),
            Codec.INT.optionalFieldOf("y", 0).forGetter(NpcDialogNode::graphY)
    ).apply(instance, NpcDialogNode::new));

    public static final StreamCodec<ByteBuf, NpcDialogNode> STREAM_CODEC = StreamCodec.of(
            (buffer, node) -> {
                ByteBufCodecs.STRING_UTF8.encode(buffer, node.nodeId());
                ByteBufCodecs.STRING_UTF8.encode(buffer, node.speakerLine());
                CHOICE_LIST.encode(buffer, node.choices());
                ACTION_LIST.encode(buffer, node.actions());
                ByteBufCodecs.STRING_UTF8.encode(buffer, node.interactSound());
                ByteBufCodecs.STRING_UTF8.encode(buffer, node.triggerSound());
                ByteBufCodecs.VAR_INT.encode(buffer, node.graphX());
                ByteBufCodecs.VAR_INT.encode(buffer, node.graphY());
            },
            buffer -> new NpcDialogNode(
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    CHOICE_LIST.decode(buffer),
                    ACTION_LIST.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer)));

    public boolean isValid() {
        if (nodeId == null || nodeId.isBlank() || speakerLine == null
                || speakerLine.length() > NpcValidation.MAX_DIALOG_LINE_LENGTH
                || !isValidSoundId(interactSound) || !isValidSoundId(triggerSound)) {
            return false;
        }
        for (NpcDialogChoice choice : choices) {
            if (!choice.isValid()) {
                return false;
            }
        }
        for (NpcDialogAction action : actions) {
            if (!action.isValid()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isValidSoundId(String soundId) {
        return soundId != null && (soundId.isEmpty() || Identifier.tryParse(soundId) != null);
    }
}
