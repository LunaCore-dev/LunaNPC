package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// One quest goal. KILL_MOB: targetId is an entity type id, requiredWeaponId optionally restricts the
// kill to one weapon item ("" = any). COLLECT_ITEMS: targetId is an item id consumed at turn-in, and
// requiredWeaponId is unused (""). KILL_PLAYERS: targetId empty = any player. ENTER_*: targetId is
// the biome/structure/dimension id; the constructor forces amount to 1 (first entry completes).
public record NpcQuestObjective(NpcObjectiveType type, String targetId, int amount,
                                String requiredWeaponId) {

    public NpcQuestObjective {
        if (type != null && type.isEnterObjective()) {
            amount = 1;
        }
    }

    public static final Codec<NpcQuestObjective> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            NpcObjectiveType.CODEC.fieldOf("type").forGetter(NpcQuestObjective::type),
            Codec.STRING.fieldOf("target").forGetter(NpcQuestObjective::targetId),
            Codec.INT.optionalFieldOf("amount", 1).forGetter(NpcQuestObjective::amount),
            Codec.STRING.optionalFieldOf("weapon", "").forGetter(NpcQuestObjective::requiredWeaponId)
    ).apply(instance, NpcQuestObjective::new));

    public static final StreamCodec<ByteBuf, NpcQuestObjective> STREAM_CODEC = StreamCodec.composite(
            NpcObjectiveType.STREAM_CODEC, NpcQuestObjective::type,
            ByteBufCodecs.STRING_UTF8, NpcQuestObjective::targetId,
            ByteBufCodecs.VAR_INT, NpcQuestObjective::amount,
            ByteBufCodecs.STRING_UTF8, NpcQuestObjective::requiredWeaponId,
            NpcQuestObjective::new);

    public boolean isValid() {
        if (type == null || targetId == null || amount < 1 || requiredWeaponId == null) {
            return false;
        }
        return type == NpcObjectiveType.KILL_PLAYERS || !targetId.isBlank();
    }
}
