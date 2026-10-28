package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// A named spawn zone in one dimension: a geometric shape, plus how its height is used (ignoreHeight
// collapses it to a full-height column) and where inside it NPCs may appear (surface/anywhere/caves).
// Created in-world with the Zone Wand and referenced by NPCs as a spawn area. Replaces NpcCube; old
// cubes migrate in as Box-shaped zones.
public record NpcZone(String id, String name, String dimension, ZoneShape shape,
                      boolean ignoreHeight, SurfaceMode surfaceMode) {

    public static final Codec<NpcZone> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(NpcZone::id),
            Codec.STRING.fieldOf("name").forGetter(NpcZone::name),
            Codec.STRING.fieldOf("dimension").forGetter(NpcZone::dimension),
            ZoneShape.CODEC.fieldOf("shape").forGetter(NpcZone::shape),
            Codec.BOOL.optionalFieldOf("ignore_height", false).forGetter(NpcZone::ignoreHeight),
            SurfaceMode.CODEC.optionalFieldOf("surface_mode", SurfaceMode.ANYWHERE).forGetter(NpcZone::surfaceMode)
    ).apply(instance, NpcZone::new));

    public static final StreamCodec<ByteBuf, NpcZone> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, NpcZone::id,
            ByteBufCodecs.STRING_UTF8, NpcZone::name,
            ByteBufCodecs.STRING_UTF8, NpcZone::dimension,
            ZoneShape.STREAM_CODEC, NpcZone::shape,
            ByteBufCodecs.BOOL, NpcZone::ignoreHeight,
            SurfaceMode.STREAM_CODEC, NpcZone::surfaceMode,
            NpcZone::new);

    public boolean contains(String dimensionId, BlockPos pos) {
        return dimension.equals(dimensionId) && shape.contains(pos, ignoreHeight);
    }

    public NpcZone withName(String newName) {
        return new NpcZone(id, newName, dimension, shape, ignoreHeight, surfaceMode);
    }

    // Soft advisories about this zone (e.g. a flat shape that can't spawn anything). Empty when fine.
    public java.util.List<net.lunacore.luna_npc.api.NpcWarning> warnings() {
        return net.lunacore.luna_npc.api.NpcWarnings.of(this);
    }
}
