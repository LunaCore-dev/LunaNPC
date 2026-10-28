package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.npc.NpcValidation;

// Where an attack's spawn points land: a shape (ring, star, line to target, ...) laid out around a centre
// between minDistance and maxDistance. yMode picks the vertical anchor: 0=ground-snap, 1=centre-Y, 2=above.
public record NpcCombatPlacement(NpcPlacementCentre centre, NpcPlacementShape shape, float minDistance,
                                 float maxDistance, int count, int angleOffset, int yMode) {

    public static final Codec<NpcCombatPlacement> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            NpcPlacementCentre.CODEC.optionalFieldOf("centre", NpcPlacementCentre.SELF)
                    .forGetter(NpcCombatPlacement::centre),
            NpcPlacementShape.CODEC.optionalFieldOf("shape", NpcPlacementShape.AT_CENTRE)
                    .forGetter(NpcCombatPlacement::shape),
            Codec.FLOAT.optionalFieldOf("min_distance", 0.0F).forGetter(NpcCombatPlacement::minDistance),
            Codec.FLOAT.optionalFieldOf("max_distance", 0.0F).forGetter(NpcCombatPlacement::maxDistance),
            Codec.INT.optionalFieldOf("count", 1).forGetter(NpcCombatPlacement::count),
            Codec.INT.optionalFieldOf("angle_offset", 0).forGetter(NpcCombatPlacement::angleOffset),
            Codec.INT.optionalFieldOf("y_mode", 0).forGetter(NpcCombatPlacement::yMode)
    ).apply(instance, NpcCombatPlacement::new));

    public static final StreamCodec<ByteBuf, NpcCombatPlacement> STREAM_CODEC = StreamCodec.composite(
            NpcPlacementCentre.STREAM_CODEC, NpcCombatPlacement::centre,
            NpcPlacementShape.STREAM_CODEC, NpcCombatPlacement::shape,
            ByteBufCodecs.FLOAT, NpcCombatPlacement::minDistance,
            ByteBufCodecs.FLOAT, NpcCombatPlacement::maxDistance,
            ByteBufCodecs.VAR_INT, NpcCombatPlacement::count,
            ByteBufCodecs.VAR_INT, NpcCombatPlacement::angleOffset,
            ByteBufCodecs.VAR_INT, NpcCombatPlacement::yMode,
            NpcCombatPlacement::new);

    public static NpcCombatPlacement atSelf() {
        return new NpcCombatPlacement(NpcPlacementCentre.SELF, NpcPlacementShape.AT_CENTRE, 0.0F, 0.0F, 1, 0, 0);
    }

    public static NpcCombatPlacement atTarget() {
        return new NpcCombatPlacement(NpcPlacementCentre.TARGET, NpcPlacementShape.AT_CENTRE, 0.0F, 0.0F, 1, 0, 0);
    }

    public boolean isValid() {
        return centre != null && shape != null && minDistance >= 0.0F && maxDistance >= minDistance
                && maxDistance <= NpcValidation.MAX_PLACEMENT_DISTANCE && count >= 1
                && count <= NpcValidation.MAX_PLACEMENT_POINTS && yMode >= 0 && yMode <= 2;
    }
}
