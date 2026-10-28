package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.npc.NpcValidation;

// One weighted farming variant: how far the farmer works crops around its home (radius), how far it
// searches for a reachable chest to deposit into (chestRadius — the chest is discovered at runtime,
// never configured), how many item pieces its buffer holds before depositing (capacity), how long
// without a mature crop before it deposits what it holds (idleDepositSeconds), and which crop blocks
// it harvests (empty = every crop block in the registry, modded ones included). Weights across a
// job's sub-type list must total exactly 100.
public record NpcFarmSubType(int weightPercent, int radius, int chestRadius,
                             int capacity, int idleDepositSeconds,
                             List<String> cropBlockIds) {

    public NpcFarmSubType {
        cropBlockIds = List.copyOf(cropBlockIds);
    }

    private static final StreamCodec<ByteBuf, List<String>> CROP_LIST =
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list());

    public static final Codec<NpcFarmSubType> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.optionalFieldOf("weight", 0).forGetter(NpcFarmSubType::weightPercent),
            Codec.INT.optionalFieldOf("radius", 16).forGetter(NpcFarmSubType::radius),
            Codec.INT.optionalFieldOf("chest_radius", 8).forGetter(NpcFarmSubType::chestRadius),
            Codec.INT.optionalFieldOf("capacity", 32).forGetter(NpcFarmSubType::capacity),
            Codec.INT.optionalFieldOf("idle_deposit_seconds", 15).forGetter(NpcFarmSubType::idleDepositSeconds),
            Codec.STRING.listOf().optionalFieldOf("crops", List.of()).forGetter(NpcFarmSubType::cropBlockIds)
    ).apply(instance, NpcFarmSubType::new));

    public static final StreamCodec<ByteBuf, NpcFarmSubType> STREAM_CODEC = StreamCodec.of(
            (buffer, subType) -> {
                ByteBufCodecs.VAR_INT.encode(buffer, subType.weightPercent());
                ByteBufCodecs.VAR_INT.encode(buffer, subType.radius());
                ByteBufCodecs.VAR_INT.encode(buffer, subType.chestRadius());
                ByteBufCodecs.VAR_INT.encode(buffer, subType.capacity());
                ByteBufCodecs.VAR_INT.encode(buffer, subType.idleDepositSeconds());
                CROP_LIST.encode(buffer, subType.cropBlockIds());
            },
            buffer -> new NpcFarmSubType(
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    CROP_LIST.decode(buffer)));

    public boolean isValid() {
        if (weightPercent < 0 || weightPercent > NpcValidation.JOB_WEIGHT_TOTAL_PERCENT) {
            return false;
        }
        if (radius < NpcValidation.MIN_WANDER_RADIUS || radius > NpcValidation.MAX_WANDER_RADIUS
                || chestRadius < 1 || chestRadius > NpcValidation.MAX_FARM_CHEST_RADIUS) {
            return false;
        }
        if (capacity < 0 || capacity > NpcValidation.MAX_FARM_CAPACITY
                || idleDepositSeconds < 0 || idleDepositSeconds > NpcValidation.MAX_FARM_IDLE_SECONDS) {
            return false;
        }
        for (String cropId : cropBlockIds) {
            if (cropId == null || cropId.isBlank()) {
                return false;
            }
        }
        return true;
    }
}
