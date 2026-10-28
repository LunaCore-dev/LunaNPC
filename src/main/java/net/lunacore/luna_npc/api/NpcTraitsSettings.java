package net.lunacore.luna_npc.api;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.npc.NpcDefaults;
import net.lunacore.luna_npc.npc.NpcValidation;

// Innate NPC characteristics. The npcType decides how potions land: "alive" reacts like a
// player, "creature" reacts like a mob such as a zombie (inverted heal/harm). The avoid-light
// rules keep pathfinding out of too-bright (or, inverted, too-dark) cells and make the NPC flee
// when it stands in one; avoidWater/avoidLava keep pathfinding out of those fluids (lava on by
// default — turning it off is an intentionally extreme choice). canSwim only matters for
// non-water NPCs: off means it sinks and stops. emergencySeek (only meaningful with
// burnInDaylight/avoidLight/avoidNight) lets a threatened NPC drop everything and hunt for a
// safe spot immediately, bypassing its wander interval; off, it only relocates on its normal
// wander cadence. takeFallDamage (off by default) decides whether the NPC is hurt by falls at
// all — off keeps flyers that dip to the ground, or any NPC, from dying to phantom fall damage.
// projectileImmune makes every projectile pass through (no damage, effects, or knockback; only the
// explosion of an explosive shot still lands); fireImmune blocks all fire, lava, and magma-block
// damage and keeps the NPC from ever catching fire. chunkLoader keeps the chunks around the NPC loaded
// and ticking while it lives, so it keeps walking with no player near (see NpcChunkLoader).
public record NpcTraitsSettings(String npcType, boolean canDrown, boolean canSwim, boolean burnInDaylight,
                                boolean avoidWater, boolean avoidLava,
                                boolean avoidLight, int avoidLightLevel,
                                boolean avoidNight, int avoidNightLevel, boolean emergencySeek,
                                boolean canOpenDoors, boolean canBreakDoors, boolean takeFallDamage,
                                boolean projectileImmune, boolean fireImmune,
                                NpcReactionSettings reaction, NpcRegenSettings regen, boolean chunkLoader) {

    // True when any condition that the emergency seek reacts to is switched on for this NPC.
    public boolean hasAvoidanceCondition() {
        return burnInDaylight || avoidLight || avoidNight;
    }

    // True when the NPC may move through a closed wooden door — by opening it or breaking it —
    // so ground pathfinding treats such a door as a passable cell instead of a wall.
    public boolean canTraverseDoors() {
        return canOpenDoors || canBreakDoors;
    }

    public static final String TYPE_ALIVE = "alive";
    public static final String TYPE_CREATURE = "creature";

    // The record exceeds the 16 fields RecordCodecBuilder.group allows, so the codec is stitched from
    // two flat halves with mapPair — both write into the same object, so every field keeps its key.
    private record Head(String npcType, boolean canDrown, boolean canSwim, boolean burnInDaylight,
                        boolean avoidWater, boolean avoidLava, boolean avoidLight, int avoidLightLevel,
                        boolean avoidNight, int avoidNightLevel) {
    }

    private record Tail(boolean emergencySeek, boolean canOpenDoors, boolean canBreakDoors,
                        boolean takeFallDamage, boolean projectileImmune, boolean fireImmune,
                        NpcReactionSettings reaction, NpcRegenSettings regen, boolean chunkLoader) {
    }

    private static final MapCodec<Head> HEAD_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("npc_type", TYPE_ALIVE).forGetter(Head::npcType),
            Codec.BOOL.optionalFieldOf("can_drown", true).forGetter(Head::canDrown),
            Codec.BOOL.optionalFieldOf("can_swim", true).forGetter(Head::canSwim),
            Codec.BOOL.optionalFieldOf("burn_in_daylight", false).forGetter(Head::burnInDaylight),
            Codec.BOOL.optionalFieldOf("avoid_water", false).forGetter(Head::avoidWater),
            Codec.BOOL.optionalFieldOf("avoid_lava", true).forGetter(Head::avoidLava),
            Codec.BOOL.optionalFieldOf("avoid_light", false).forGetter(Head::avoidLight),
            Codec.INT.optionalFieldOf("avoid_light_level", NpcDefaults.AVOID_LIGHT_LEVEL).forGetter(Head::avoidLightLevel),
            Codec.BOOL.optionalFieldOf("avoid_night", false).forGetter(Head::avoidNight),
            Codec.INT.optionalFieldOf("avoid_night_level", NpcDefaults.AVOID_LIGHT_LEVEL).forGetter(Head::avoidNightLevel)
    ).apply(instance, Head::new));

    private static final MapCodec<Tail> TAIL_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.BOOL.optionalFieldOf("emergency_seek", NpcDefaults.EMERGENCY_SEEK).forGetter(Tail::emergencySeek),
            Codec.BOOL.optionalFieldOf("can_open_doors", false).forGetter(Tail::canOpenDoors),
            Codec.BOOL.optionalFieldOf("can_break_doors", false).forGetter(Tail::canBreakDoors),
            Codec.BOOL.optionalFieldOf("take_fall_damage", false).forGetter(Tail::takeFallDamage),
            Codec.BOOL.optionalFieldOf("projectile_immune", false).forGetter(Tail::projectileImmune),
            Codec.BOOL.optionalFieldOf("fire_immune", false).forGetter(Tail::fireImmune),
            NpcReactionSettings.CODEC.optionalFieldOf("reaction", NpcReactionSettings.defaults()).forGetter(Tail::reaction),
            NpcRegenSettings.CODEC.optionalFieldOf("regen", NpcRegenSettings.defaults()).forGetter(Tail::regen),
            Codec.BOOL.optionalFieldOf("chunk_loader", false).forGetter(Tail::chunkLoader)
    ).apply(instance, Tail::new));

    public static final Codec<NpcTraitsSettings> CODEC = Codec.mapPair(HEAD_CODEC, TAIL_CODEC).xmap(
            pair -> {
                Head h = pair.getFirst();
                Tail t = pair.getSecond();
                return new NpcTraitsSettings(h.npcType(), h.canDrown(), h.canSwim(), h.burnInDaylight(),
                        h.avoidWater(), h.avoidLava(), h.avoidLight(), h.avoidLightLevel(), h.avoidNight(),
                        h.avoidNightLevel(), t.emergencySeek(), t.canOpenDoors(), t.canBreakDoors(),
                        t.takeFallDamage(), t.projectileImmune(), t.fireImmune(), t.reaction(), t.regen(),
                        t.chunkLoader());
            },
            settings -> Pair.of(
                    new Head(settings.npcType(), settings.canDrown(), settings.canSwim(), settings.burnInDaylight(),
                            settings.avoidWater(), settings.avoidLava(), settings.avoidLight(),
                            settings.avoidLightLevel(), settings.avoidNight(), settings.avoidNightLevel()),
                    new Tail(settings.emergencySeek(), settings.canOpenDoors(), settings.canBreakDoors(),
                            settings.takeFallDamage(), settings.projectileImmune(), settings.fireImmune(),
                            settings.reaction(), settings.regen(), settings.chunkLoader()))
    ).codec();

    // Composite tops out at 12 pairs, so this record is encoded by hand in field order.
    public static final StreamCodec<ByteBuf, NpcTraitsSettings> STREAM_CODEC = StreamCodec.of(
            (buffer, settings) -> {
                ByteBufCodecs.STRING_UTF8.encode(buffer, settings.npcType());
                ByteBufCodecs.BOOL.encode(buffer, settings.canDrown());
                ByteBufCodecs.BOOL.encode(buffer, settings.canSwim());
                ByteBufCodecs.BOOL.encode(buffer, settings.burnInDaylight());
                ByteBufCodecs.BOOL.encode(buffer, settings.avoidWater());
                ByteBufCodecs.BOOL.encode(buffer, settings.avoidLava());
                ByteBufCodecs.BOOL.encode(buffer, settings.avoidLight());
                ByteBufCodecs.VAR_INT.encode(buffer, settings.avoidLightLevel());
                ByteBufCodecs.BOOL.encode(buffer, settings.avoidNight());
                ByteBufCodecs.VAR_INT.encode(buffer, settings.avoidNightLevel());
                ByteBufCodecs.BOOL.encode(buffer, settings.emergencySeek());
                ByteBufCodecs.BOOL.encode(buffer, settings.canOpenDoors());
                ByteBufCodecs.BOOL.encode(buffer, settings.canBreakDoors());
                ByteBufCodecs.BOOL.encode(buffer, settings.takeFallDamage());
                ByteBufCodecs.BOOL.encode(buffer, settings.projectileImmune());
                ByteBufCodecs.BOOL.encode(buffer, settings.fireImmune());
                NpcReactionSettings.STREAM_CODEC.encode(buffer, settings.reaction());
                NpcRegenSettings.STREAM_CODEC.encode(buffer, settings.regen());
                ByteBufCodecs.BOOL.encode(buffer, settings.chunkLoader());
            },
            buffer -> new NpcTraitsSettings(
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    NpcReactionSettings.STREAM_CODEC.decode(buffer),
                    NpcRegenSettings.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer)));

    public static NpcTraitsSettings defaults() {
        return new NpcTraitsSettings(TYPE_ALIVE, true, true, false, false, true,
                false, NpcDefaults.AVOID_LIGHT_LEVEL, false, NpcDefaults.AVOID_LIGHT_LEVEL,
                NpcDefaults.EMERGENCY_SEEK, false, false, false, false, false,
                NpcReactionSettings.defaults(), NpcRegenSettings.defaults(), false);
    }

    // Additive convenience copies for scripting/dev use: each returns a new record with a single
    // field changed via the canonical constructor, preserving immutability.
    public NpcTraitsSettings withNpcType(String npcType) {
        return new NpcTraitsSettings(npcType, canDrown, canSwim, burnInDaylight, avoidWater, avoidLava,
                avoidLight, avoidLightLevel, avoidNight, avoidNightLevel, emergencySeek, canOpenDoors, canBreakDoors, takeFallDamage, projectileImmune, fireImmune, reaction, regen, chunkLoader);
    }

    // Reacts to potions like a mob (inverted heal/harm).
    public NpcTraitsSettings asCreature() {
        return withNpcType(TYPE_CREATURE);
    }

    // Reacts to potions like a player.
    public NpcTraitsSettings asAlive() {
        return withNpcType(TYPE_ALIVE);
    }

    public NpcTraitsSettings withCanSwim(boolean canSwim) {
        return new NpcTraitsSettings(npcType, canDrown, canSwim, burnInDaylight, avoidWater, avoidLava,
                avoidLight, avoidLightLevel, avoidNight, avoidNightLevel, emergencySeek, canOpenDoors, canBreakDoors, takeFallDamage, projectileImmune, fireImmune, reaction, regen, chunkLoader);
    }

    public NpcTraitsSettings withAvoidLava(boolean avoidLava) {
        return new NpcTraitsSettings(npcType, canDrown, canSwim, burnInDaylight, avoidWater, avoidLava,
                avoidLight, avoidLightLevel, avoidNight, avoidNightLevel, emergencySeek, canOpenDoors, canBreakDoors, takeFallDamage, projectileImmune, fireImmune, reaction, regen, chunkLoader);
    }

    public NpcTraitsSettings withAvoidWater(boolean avoidWater) {
        return new NpcTraitsSettings(npcType, canDrown, canSwim, burnInDaylight, avoidWater, avoidLava,
                avoidLight, avoidLightLevel, avoidNight, avoidNightLevel, emergencySeek, canOpenDoors, canBreakDoors, takeFallDamage, projectileImmune, fireImmune, reaction, regen, chunkLoader);
    }

    public NpcTraitsSettings withBurnInDaylight(boolean burnInDaylight) {
        return new NpcTraitsSettings(npcType, canDrown, canSwim, burnInDaylight, avoidWater, avoidLava,
                avoidLight, avoidLightLevel, avoidNight, avoidNightLevel, emergencySeek, canOpenDoors, canBreakDoors, takeFallDamage, projectileImmune, fireImmune, reaction, regen, chunkLoader);
    }

    public NpcTraitsSettings withCanDrown(boolean canDrown) {
        return new NpcTraitsSettings(npcType, canDrown, canSwim, burnInDaylight, avoidWater, avoidLava,
                avoidLight, avoidLightLevel, avoidNight, avoidNightLevel, emergencySeek, canOpenDoors, canBreakDoors, takeFallDamage, projectileImmune, fireImmune, reaction, regen, chunkLoader);
    }

    public NpcTraitsSettings withAvoidLight(boolean avoidLight) {
        return new NpcTraitsSettings(npcType, canDrown, canSwim, burnInDaylight, avoidWater, avoidLava,
                avoidLight, avoidLightLevel, avoidNight, avoidNightLevel, emergencySeek, canOpenDoors, canBreakDoors, takeFallDamage, projectileImmune, fireImmune, reaction, regen, chunkLoader);
    }

    public NpcTraitsSettings withAvoidLightLevel(int avoidLightLevel) {
        return new NpcTraitsSettings(npcType, canDrown, canSwim, burnInDaylight, avoidWater, avoidLava,
                avoidLight, avoidLightLevel, avoidNight, avoidNightLevel, emergencySeek, canOpenDoors, canBreakDoors, takeFallDamage, projectileImmune, fireImmune, reaction, regen, chunkLoader);
    }

    public NpcTraitsSettings withAvoidNight(boolean avoidNight) {
        return new NpcTraitsSettings(npcType, canDrown, canSwim, burnInDaylight, avoidWater, avoidLava,
                avoidLight, avoidLightLevel, avoidNight, avoidNightLevel, emergencySeek, canOpenDoors, canBreakDoors, takeFallDamage, projectileImmune, fireImmune, reaction, regen, chunkLoader);
    }

    public NpcTraitsSettings withAvoidNightLevel(int avoidNightLevel) {
        return new NpcTraitsSettings(npcType, canDrown, canSwim, burnInDaylight, avoidWater, avoidLava,
                avoidLight, avoidLightLevel, avoidNight, avoidNightLevel, emergencySeek, canOpenDoors, canBreakDoors, takeFallDamage, projectileImmune, fireImmune, reaction, regen, chunkLoader);
    }

    public NpcTraitsSettings withEmergencySeek(boolean emergencySeek) {
        return new NpcTraitsSettings(npcType, canDrown, canSwim, burnInDaylight, avoidWater, avoidLava,
                avoidLight, avoidLightLevel, avoidNight, avoidNightLevel, emergencySeek, canOpenDoors, canBreakDoors, takeFallDamage, projectileImmune, fireImmune, reaction, regen, chunkLoader);
    }

    public NpcTraitsSettings withCanOpenDoors(boolean canOpenDoors) {
        return new NpcTraitsSettings(npcType, canDrown, canSwim, burnInDaylight, avoidWater, avoidLava,
                avoidLight, avoidLightLevel, avoidNight, avoidNightLevel, emergencySeek, canOpenDoors, canBreakDoors, takeFallDamage, projectileImmune, fireImmune, reaction, regen, chunkLoader);
    }

    public NpcTraitsSettings withCanBreakDoors(boolean canBreakDoors) {
        return new NpcTraitsSettings(npcType, canDrown, canSwim, burnInDaylight, avoidWater, avoidLava,
                avoidLight, avoidLightLevel, avoidNight, avoidNightLevel, emergencySeek, canOpenDoors, canBreakDoors, takeFallDamage, projectileImmune, fireImmune, reaction, regen, chunkLoader);
    }

    public NpcTraitsSettings withTakeFallDamage(boolean takeFallDamage) {
        return new NpcTraitsSettings(npcType, canDrown, canSwim, burnInDaylight, avoidWater, avoidLava,
                avoidLight, avoidLightLevel, avoidNight, avoidNightLevel, emergencySeek, canOpenDoors, canBreakDoors, takeFallDamage, projectileImmune, fireImmune, reaction, regen, chunkLoader);
    }

    public NpcTraitsSettings withProjectileImmune(boolean projectileImmune) {
        return new NpcTraitsSettings(npcType, canDrown, canSwim, burnInDaylight, avoidWater, avoidLava,
                avoidLight, avoidLightLevel, avoidNight, avoidNightLevel, emergencySeek, canOpenDoors, canBreakDoors, takeFallDamage, projectileImmune, fireImmune, reaction, regen, chunkLoader);
    }

    public NpcTraitsSettings withFireImmune(boolean fireImmune) {
        return new NpcTraitsSettings(npcType, canDrown, canSwim, burnInDaylight, avoidWater, avoidLava,
                avoidLight, avoidLightLevel, avoidNight, avoidNightLevel, emergencySeek, canOpenDoors, canBreakDoors, takeFallDamage, projectileImmune, fireImmune, reaction, regen, chunkLoader);
    }

    public NpcTraitsSettings withReaction(NpcReactionSettings reaction) {
        return new NpcTraitsSettings(npcType, canDrown, canSwim, burnInDaylight, avoidWater, avoidLava,
                avoidLight, avoidLightLevel, avoidNight, avoidNightLevel, emergencySeek, canOpenDoors, canBreakDoors, takeFallDamage, projectileImmune, fireImmune, reaction, regen, chunkLoader);
    }

    public NpcTraitsSettings withRegen(NpcRegenSettings regen) {
        return new NpcTraitsSettings(npcType, canDrown, canSwim, burnInDaylight, avoidWater, avoidLava,
                avoidLight, avoidLightLevel, avoidNight, avoidNightLevel, emergencySeek, canOpenDoors, canBreakDoors, takeFallDamage, projectileImmune, fireImmune, reaction, regen, chunkLoader);
    }

    public NpcTraitsSettings withChunkLoader(boolean chunkLoader) {
        return new NpcTraitsSettings(npcType, canDrown, canSwim, burnInDaylight, avoidWater, avoidLava,
                avoidLight, avoidLightLevel, avoidNight, avoidNightLevel, emergencySeek, canOpenDoors, canBreakDoors, takeFallDamage, projectileImmune, fireImmune, reaction, regen, chunkLoader);
    }

    public boolean isValid() {
        return NpcValidation.NPC_TYPES.contains(npcType)
                && avoidLightLevel >= NpcValidation.MIN_LIGHT_LEVEL
                && avoidLightLevel <= NpcValidation.MAX_LIGHT_LEVEL
                && avoidNightLevel >= NpcValidation.MIN_LIGHT_LEVEL
                && avoidNightLevel <= NpcValidation.MAX_LIGHT_LEVEL
                && reaction.isValid()
                && regen.isValid();
    }
}
