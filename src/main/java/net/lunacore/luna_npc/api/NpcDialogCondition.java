package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// One requirement on an answer choice. stringArg is the quest id / item id / flag key; intArg is the
// item count for HAS_ITEM (else unused); negate inverts the test. A choice is offered only when all its
// conditions pass — this is how two players reach different answers from the same node.
public record NpcDialogCondition(NpcDialogConditionType type, String stringArg, int intArg,
                                 boolean negate) {

    public static final Codec<NpcDialogCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            NpcDialogConditionType.CODEC.fieldOf("type").forGetter(NpcDialogCondition::type),
            Codec.STRING.optionalFieldOf("arg", "").forGetter(NpcDialogCondition::stringArg),
            Codec.INT.optionalFieldOf("count", 1).forGetter(NpcDialogCondition::intArg),
            Codec.BOOL.optionalFieldOf("negate", false).forGetter(NpcDialogCondition::negate)
    ).apply(instance, NpcDialogCondition::new));

    public static final StreamCodec<ByteBuf, NpcDialogCondition> STREAM_CODEC = StreamCodec.composite(
            NpcDialogConditionType.STREAM_CODEC, NpcDialogCondition::type,
            ByteBufCodecs.STRING_UTF8, NpcDialogCondition::stringArg,
            ByteBufCodecs.VAR_INT, NpcDialogCondition::intArg,
            ByteBufCodecs.BOOL, NpcDialogCondition::negate,
            NpcDialogCondition::new);

    public boolean isValid() {
        return type != null && stringArg != null && !stringArg.isBlank() && intArg >= 0;
    }
}
