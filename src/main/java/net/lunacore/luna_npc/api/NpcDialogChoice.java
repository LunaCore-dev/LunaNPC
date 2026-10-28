package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.npc.NpcValidation;

// One answer button on a node. targetNodeId is the node this choice routes to (blank = end the
// conversation); several choices may share a target, so five answers can converge on one next step. A
// choice shows only when every condition passes; hideWhenLocked chooses whether a failed condition
// hides the button or greys it.
public record NpcDialogChoice(String text, String targetNodeId,
                              List<NpcDialogCondition> conditions, boolean hideWhenLocked) {

    public NpcDialogChoice {
        conditions = List.copyOf(conditions);
    }

    private static final StreamCodec<ByteBuf, List<NpcDialogCondition>> CONDITION_LIST =
            NpcDialogCondition.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final Codec<NpcDialogChoice> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("text").forGetter(NpcDialogChoice::text),
            Codec.STRING.optionalFieldOf("target", "").forGetter(NpcDialogChoice::targetNodeId),
            NpcDialogCondition.CODEC.listOf().optionalFieldOf("conditions", List.of())
                    .forGetter(NpcDialogChoice::conditions),
            Codec.BOOL.optionalFieldOf("hide_locked", true).forGetter(NpcDialogChoice::hideWhenLocked)
    ).apply(instance, NpcDialogChoice::new));

    public static final StreamCodec<ByteBuf, NpcDialogChoice> STREAM_CODEC = StreamCodec.of(
            (buffer, choice) -> {
                ByteBufCodecs.STRING_UTF8.encode(buffer, choice.text());
                ByteBufCodecs.STRING_UTF8.encode(buffer, choice.targetNodeId());
                CONDITION_LIST.encode(buffer, choice.conditions());
                ByteBufCodecs.BOOL.encode(buffer, choice.hideWhenLocked());
            },
            buffer -> new NpcDialogChoice(
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    CONDITION_LIST.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer)));

    public boolean isValid() {
        if (text == null || text.isBlank() || text.length() > NpcValidation.MAX_DIALOG_CHOICE_LENGTH
                || targetNodeId == null) {
            return false;
        }
        for (NpcDialogCondition condition : conditions) {
            if (!condition.isValid()) {
                return false;
            }
        }
        return true;
    }
}
