package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.npc.NpcValidation;

// The BODYGUARD job. source picks who it protects: the closest protect target within range (TARGETS) or
// whoever hires it for the hire price (HIRE); dialogs, the command and the API can hire it in either
// mode. It then locks onto that one protectee and keeps within stayRadius of it (never on its spot),
// running to catch up and walking once within walkWithin. Whoever hurts the protectee is attacked — for
// defenseSeconds, or until dead when 0 — and with assistAttacks also whatever the protectee attacks;
// retreat drops a fight that drags it more than retreatDistance away; without friendlyFire the ones it
// guards (its protectee, and in TARGETS mode everyone matching its targets) can't hurt it. teleportDistance (0 = never) snaps
// it next to a protectee left that far behind or in another dimension; without teleporting it gives up
// once the protectee is beyond range. keepAfterDeath keeps the lock through a player's death and respawn
// (NPCs and mobs never come back). giveUp is what it does after letting go.
public record NpcBodyguardSettings(NpcBodyguardSource source, NpcProtectTargets protect, NpcBodyguardHire hire,
                                   int range, int stayRadius, int walkWithin, int teleportDistance,
                                   int defenseSeconds, boolean assistAttacks, boolean retreat,
                                   int retreatDistance, boolean friendlyFire, boolean keepAfterDeath,
                                   NpcGiveUpAction giveUp) {

    public static final Codec<NpcBodyguardSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            NpcBodyguardSource.CODEC.optionalFieldOf("source", NpcBodyguardSource.TARGETS)
                    .forGetter(NpcBodyguardSettings::source),
            NpcProtectTargets.CODEC.optionalFieldOf("protect", NpcProtectTargets.none())
                    .forGetter(NpcBodyguardSettings::protect),
            NpcBodyguardHire.CODEC.optionalFieldOf("hire", NpcBodyguardHire.defaults())
                    .forGetter(NpcBodyguardSettings::hire),
            Codec.INT.optionalFieldOf("range", 24).forGetter(NpcBodyguardSettings::range),
            Codec.INT.optionalFieldOf("stay_radius", 3).forGetter(NpcBodyguardSettings::stayRadius),
            Codec.INT.optionalFieldOf("walk_within", 5).forGetter(NpcBodyguardSettings::walkWithin),
            Codec.INT.optionalFieldOf("teleport_distance", 0).forGetter(NpcBodyguardSettings::teleportDistance),
            Codec.INT.optionalFieldOf("defense_seconds", 0).forGetter(NpcBodyguardSettings::defenseSeconds),
            Codec.BOOL.optionalFieldOf("assist_attacks", false).forGetter(NpcBodyguardSettings::assistAttacks),
            Codec.BOOL.optionalFieldOf("retreat", true).forGetter(NpcBodyguardSettings::retreat),
            Codec.INT.optionalFieldOf("retreat_distance", 16).forGetter(NpcBodyguardSettings::retreatDistance),
            Codec.BOOL.optionalFieldOf("friendly_fire", true).forGetter(NpcBodyguardSettings::friendlyFire),
            Codec.BOOL.optionalFieldOf("keep_after_death", true).forGetter(NpcBodyguardSettings::keepAfterDeath),
            NpcGiveUpAction.CODEC.optionalFieldOf("give_up", NpcGiveUpAction.STAY)
                    .forGetter(NpcBodyguardSettings::giveUp)
    ).apply(instance, NpcBodyguardSettings::new));

    // Composite tops out at 12 pairs, so this record is encoded by hand in field order.
    public static final StreamCodec<ByteBuf, NpcBodyguardSettings> STREAM_CODEC = StreamCodec.of(
            (buffer, settings) -> {
                NpcBodyguardSource.STREAM_CODEC.encode(buffer, settings.source());
                NpcProtectTargets.STREAM_CODEC.encode(buffer, settings.protect());
                NpcBodyguardHire.STREAM_CODEC.encode(buffer, settings.hire());
                ByteBufCodecs.VAR_INT.encode(buffer, settings.range());
                ByteBufCodecs.VAR_INT.encode(buffer, settings.stayRadius());
                ByteBufCodecs.VAR_INT.encode(buffer, settings.walkWithin());
                ByteBufCodecs.VAR_INT.encode(buffer, settings.teleportDistance());
                ByteBufCodecs.VAR_INT.encode(buffer, settings.defenseSeconds());
                ByteBufCodecs.BOOL.encode(buffer, settings.assistAttacks());
                ByteBufCodecs.BOOL.encode(buffer, settings.retreat());
                ByteBufCodecs.VAR_INT.encode(buffer, settings.retreatDistance());
                ByteBufCodecs.BOOL.encode(buffer, settings.friendlyFire());
                ByteBufCodecs.BOOL.encode(buffer, settings.keepAfterDeath());
                NpcGiveUpAction.STREAM_CODEC.encode(buffer, settings.giveUp());
            },
            buffer -> new NpcBodyguardSettings(
                    NpcBodyguardSource.STREAM_CODEC.decode(buffer),
                    NpcProtectTargets.STREAM_CODEC.decode(buffer),
                    NpcBodyguardHire.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    NpcGiveUpAction.STREAM_CODEC.decode(buffer)));

    public static NpcBodyguardSettings defaults() {
        return new NpcBodyguardSettings(NpcBodyguardSource.TARGETS, NpcProtectTargets.none(),
                NpcBodyguardHire.defaults(), 24, 3, 5, 0, 0, false, true, 16, true, true, NpcGiveUpAction.STAY);
    }

    // A TARGETS guard for these protect targets, everything else default.
    public static NpcBodyguardSettings protecting(NpcProtectTargets targets) {
        return defaults().withProtect(targets);
    }

    // A HIRE guard with this offer, everything else default.
    public static NpcBodyguardSettings forHire(NpcBodyguardHire offer) {
        return defaults().withHire(offer);
    }

    // Also switches the source to TARGETS.
    public NpcBodyguardSettings withProtect(NpcProtectTargets targets) {
        return new NpcBodyguardSettings(NpcBodyguardSource.TARGETS, targets, hire, range, stayRadius, walkWithin,
                teleportDistance, defenseSeconds, assistAttacks, retreat, retreatDistance, friendlyFire,
                keepAfterDeath, giveUp);
    }

    // Also switches the source to HIRE.
    public NpcBodyguardSettings withHire(NpcBodyguardHire offer) {
        return new NpcBodyguardSettings(NpcBodyguardSource.HIRE, protect, offer, range, stayRadius, walkWithin,
                teleportDistance, defenseSeconds, assistAttacks, retreat, retreatDistance, friendlyFire,
                keepAfterDeath, giveUp);
    }

    public NpcBodyguardSettings withSource(NpcBodyguardSource newSource) {
        return new NpcBodyguardSettings(newSource, protect, hire, range, stayRadius, walkWithin,
                teleportDistance, defenseSeconds, assistAttacks, retreat, retreatDistance, friendlyFire,
                keepAfterDeath, giveUp);
    }

    public NpcBodyguardSettings withFollow(int newRange, int newStayRadius, int newWalkWithin) {
        return new NpcBodyguardSettings(source, protect, hire, newRange, newStayRadius, newWalkWithin,
                teleportDistance, defenseSeconds, assistAttacks, retreat, retreatDistance, friendlyFire,
                keepAfterDeath, giveUp);
    }

    // 0 = never teleport (it gives up beyond its range instead).
    public NpcBodyguardSettings withTeleport(int distance) {
        return new NpcBodyguardSettings(source, protect, hire, range, stayRadius, walkWithin,
                distance, defenseSeconds, assistAttacks, retreat, retreatDistance, friendlyFire,
                keepAfterDeath, giveUp);
    }

    // 0 seconds = defend until the attacker is dead.
    public NpcBodyguardSettings withDefense(int seconds, boolean assist) {
        return new NpcBodyguardSettings(source, protect, hire, range, stayRadius, walkWithin,
                teleportDistance, seconds, assist, retreat, retreatDistance, friendlyFire, keepAfterDeath, giveUp);
    }

    public NpcBodyguardSettings withRetreat(boolean enabled, int distance) {
        return new NpcBodyguardSettings(source, protect, hire, range, stayRadius, walkWithin,
                teleportDistance, defenseSeconds, assistAttacks, enabled, distance, friendlyFire,
                keepAfterDeath, giveUp);
    }

    // false = the ones it guards can't hurt it.
    public NpcBodyguardSettings withFriendlyFire(boolean guardedCanHurt) {
        return new NpcBodyguardSettings(source, protect, hire, range, stayRadius, walkWithin,
                teleportDistance, defenseSeconds, assistAttacks, retreat, retreatDistance, guardedCanHurt,
                keepAfterDeath, giveUp);
    }

    public NpcBodyguardSettings withGivingUp(boolean keepThroughDeath, NpcGiveUpAction action) {
        return new NpcBodyguardSettings(source, protect, hire, range, stayRadius, walkWithin,
                teleportDistance, defenseSeconds, assistAttacks, retreat, retreatDistance, friendlyFire,
                keepThroughDeath, action);
    }

    // True when nothing in its settings can ever give it someone to protect (hires by dialog, command or
    // API aside).
    public boolean protectsNobody() {
        return source == NpcBodyguardSource.TARGETS && protect.isEmpty();
    }

    public boolean isValid() {
        return source != null && giveUp != null && protect != null && protect.isValid()
                && hire != null && hire.isValid()
                && range >= 1 && range <= NpcValidation.MAX_BODYGUARD_RANGE
                && stayRadius >= 1 && stayRadius <= NpcValidation.MAX_BODYGUARD_STAY_RADIUS
                && walkWithin >= 1 && walkWithin <= NpcValidation.MAX_BODYGUARD_WALK_WITHIN
                && teleportDistance >= 0 && teleportDistance <= NpcValidation.MAX_BODYGUARD_TELEPORT_DISTANCE
                && defenseSeconds >= 0 && defenseSeconds <= NpcValidation.MAX_BODYGUARD_DEFENSE_SECONDS
                && retreatDistance >= 1 && retreatDistance <= NpcValidation.MAX_BODYGUARD_RETREAT_DISTANCE;
    }
}
