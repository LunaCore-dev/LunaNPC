package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// One NPC's use of a named zone as a spawn area: which zone, the spawn weight, and the maximum number
// of this NPC allowed alive in the zone at once (0 = unlimited). Pending respawns targeting the zone
// count toward the max.
public record NpcZoneSpawn(String zoneId, int chance, int maxCount) {

    public static final Codec<NpcZoneSpawn> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("zone").forGetter(NpcZoneSpawn::zoneId),
            Codec.INT.optionalFieldOf("weight", 1).forGetter(NpcZoneSpawn::chance),
            Codec.INT.optionalFieldOf("max_count", 0).forGetter(NpcZoneSpawn::maxCount)
    ).apply(instance, NpcZoneSpawn::new));

    public static final StreamCodec<ByteBuf, NpcZoneSpawn> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, NpcZoneSpawn::zoneId,
            ByteBufCodecs.VAR_INT, NpcZoneSpawn::chance,
            ByteBufCodecs.VAR_INT, NpcZoneSpawn::maxCount,
            NpcZoneSpawn::new);

    public NpcZoneSpawn withChance(int chance) {
        return new NpcZoneSpawn(zoneId, chance, maxCount);
    }

    public NpcZoneSpawn withMaxCount(int maxCount) {
        return new NpcZoneSpawn(zoneId, chance, maxCount);
    }

    public boolean isValid() {
        return zoneId != null && !zoneId.isBlank() && chance >= 0 && maxCount >= 0;
    }
}
