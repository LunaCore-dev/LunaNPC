package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.npc.NpcDefaults;
import net.lunacore.luna_npc.npc.NpcValidation;

// What an attack does to the world where it lands: optionally carve a rough crater of the given
// radius into the terrain, and optionally shower the area with a particle for a few seconds.
// Both halves are independent building blocks — particles without destruction and destruction
// without particles are equally valid, and the radius drives whichever of the two is active.
public record NpcImpactSettings(boolean destroyBlocks, int radius, String particleId,
                                float particleSeconds) {

    public static final Codec<NpcImpactSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("destroy_blocks", false).forGetter(NpcImpactSettings::destroyBlocks),
            Codec.INT.optionalFieldOf("radius", NpcDefaults.IMPACT_RADIUS).forGetter(NpcImpactSettings::radius),
            Codec.STRING.optionalFieldOf("particle", NpcDefaults.IMPACT_PARTICLE)
                    .forGetter(NpcImpactSettings::particleId),
            Codec.FLOAT.optionalFieldOf("particle_seconds", NpcDefaults.IMPACT_PARTICLE_SECONDS)
                    .forGetter(NpcImpactSettings::particleSeconds)
    ).apply(instance, NpcImpactSettings::new));

    public static final StreamCodec<ByteBuf, NpcImpactSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, NpcImpactSettings::destroyBlocks,
            ByteBufCodecs.VAR_INT, NpcImpactSettings::radius,
            ByteBufCodecs.STRING_UTF8, NpcImpactSettings::particleId,
            ByteBufCodecs.FLOAT, NpcImpactSettings::particleSeconds,
            NpcImpactSettings::new);

    public static NpcImpactSettings defaults() {
        return new NpcImpactSettings(false, NpcDefaults.IMPACT_RADIUS, NpcDefaults.IMPACT_PARTICLE,
                NpcDefaults.IMPACT_PARTICLE_SECONDS);
    }

    // Developer convenience: each returns a new record with a single field swapped, immutability kept.
    public NpcImpactSettings withDestroyBlocks(boolean destroyBlocks) {
        return new NpcImpactSettings(destroyBlocks, radius, particleId, particleSeconds);
    }

    public NpcImpactSettings withRadius(int radius) {
        return new NpcImpactSettings(destroyBlocks, radius, particleId, particleSeconds);
    }

    public NpcImpactSettings withParticle(String particleId) {
        return new NpcImpactSettings(destroyBlocks, radius, particleId, particleSeconds);
    }

    public NpcImpactSettings withParticleSeconds(float particleSeconds) {
        return new NpcImpactSettings(destroyBlocks, radius, particleId, particleSeconds);
    }

    public boolean hasParticles() {
        return !particleId.isEmpty() && particleSeconds > 0.0F;
    }

    public boolean isValid() {
        return radius >= NpcValidation.MIN_IMPACT_RADIUS && radius <= NpcValidation.MAX_IMPACT_RADIUS
                && particleSeconds >= 0.0F && particleSeconds <= NpcValidation.MAX_IMPACT_PARTICLE_SECONDS
                && isKnownParticleOrEmpty(particleId);
    }

    // Only data-free particle types can be spawned from a bare id; anything needing extra data
    // (dust colors, block states) is rejected here and never offered by the picker.
    public static boolean isKnownParticleOrEmpty(String particleId) {
        if (particleId.isEmpty()) {
            return true;
        }
        Identifier parsed = Identifier.tryParse(particleId);
        if (parsed == null) {
            return false;
        }
        return BuiltInRegistries.PARTICLE_TYPE.getOptional(parsed)
                .filter(type -> type instanceof SimpleParticleType)
                .isPresent();
    }
}
