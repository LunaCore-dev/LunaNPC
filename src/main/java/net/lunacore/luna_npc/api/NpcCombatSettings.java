package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.npc.NpcDefaults;
import net.lunacore.luna_npc.npc.NpcValidation;

// The two attack categories plus the shared combat brain settings: the distance preference the
// NPC keeps in ranged mode (retreating when the target is closer, closing in for the kill when
// it dives under half of it), the aggro range, whether a clear line of sight is required before
// the NPC will attack (and it repositions to get one), and the give-up timeout — how many
// seconds a target may sit in range unhurt before the NPC stops chasing it (0 = never gives up).
// friendlyFire is this NPC's own vote on whether members of its own faction may fight it (and it
// them); the alliance-wide rule can override it (see NpcAlliance / NpcAllianceManager).
// difficultyScaling lets the world difficulty auto-adjust the dealt damage (vanilla mob behaviour:
// roughly half on Easy, +50% on Hard); off (the default) means the configured damage lands exactly.
public record NpcCombatSettings(NpcAttackSettings melee, NpcAttackSettings ranged,
                                NpcProjectileSettings projectile, float distancePreference,
                                float aggroRange, boolean requireLineOfSight, int giveUpSeconds,
                                boolean friendlyFire, boolean difficultyScaling, NpcRangeSwitch rangeSwitch,
                                NpcCombatMode combatMode, NpcAdvancedCombat advanced) {

    public static final Codec<NpcCombatSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            NpcAttackSettings.CODEC.optionalFieldOf("melee", NpcAttackSettings.defaultMelee())
                    .forGetter(NpcCombatSettings::melee),
            NpcAttackSettings.CODEC.optionalFieldOf("ranged", NpcAttackSettings.defaultRanged())
                    .forGetter(NpcCombatSettings::ranged),
            NpcProjectileSettings.CODEC.optionalFieldOf("projectile", NpcProjectileSettings.defaults())
                    .forGetter(NpcCombatSettings::projectile),
            Codec.FLOAT.optionalFieldOf("distance_preference", NpcDefaults.DISTANCE_PREFERENCE)
                    .forGetter(NpcCombatSettings::distancePreference),
            Codec.FLOAT.optionalFieldOf("aggro_range", NpcDefaults.AGGRO_RANGE)
                    .forGetter(NpcCombatSettings::aggroRange),
            Codec.BOOL.optionalFieldOf("require_line_of_sight", NpcDefaults.REQUIRE_LINE_OF_SIGHT)
                    .forGetter(NpcCombatSettings::requireLineOfSight),
            Codec.INT.optionalFieldOf("give_up_seconds", NpcDefaults.GIVE_UP_SECONDS)
                    .forGetter(NpcCombatSettings::giveUpSeconds),
            Codec.BOOL.optionalFieldOf("friendly_fire", NpcDefaults.FRIENDLY_FIRE)
                    .forGetter(NpcCombatSettings::friendlyFire),
            Codec.BOOL.optionalFieldOf("difficulty_scaling", NpcDefaults.DIFFICULTY_SCALING)
                    .forGetter(NpcCombatSettings::difficultyScaling),
            NpcRangeSwitch.CODEC.optionalFieldOf("range_switch", NpcRangeSwitch.defaults())
                    .forGetter(NpcCombatSettings::rangeSwitch),
            NpcCombatMode.CODEC.optionalFieldOf("combat_mode", NpcCombatMode.SIMPLE)
                    .forGetter(NpcCombatSettings::combatMode),
            NpcAdvancedCombat.CODEC.optionalFieldOf("advanced", NpcAdvancedCombat.defaults())
                    .forGetter(NpcCombatSettings::advanced)
    ).apply(instance, NpcCombatSettings::new));

    // A dozen fields with nested list-bearing records exceed StreamCodec.composite, so the network codec
    // is hand-rolled (same as NpcSpawnSettings / NpcSummary).
    public static final StreamCodec<ByteBuf, NpcCombatSettings> STREAM_CODEC = StreamCodec.of(
            (buffer, settings) -> {
                NpcAttackSettings.STREAM_CODEC.encode(buffer, settings.melee());
                NpcAttackSettings.STREAM_CODEC.encode(buffer, settings.ranged());
                NpcProjectileSettings.STREAM_CODEC.encode(buffer, settings.projectile());
                ByteBufCodecs.FLOAT.encode(buffer, settings.distancePreference());
                ByteBufCodecs.FLOAT.encode(buffer, settings.aggroRange());
                ByteBufCodecs.BOOL.encode(buffer, settings.requireLineOfSight());
                ByteBufCodecs.VAR_INT.encode(buffer, settings.giveUpSeconds());
                ByteBufCodecs.BOOL.encode(buffer, settings.friendlyFire());
                ByteBufCodecs.BOOL.encode(buffer, settings.difficultyScaling());
                NpcRangeSwitch.STREAM_CODEC.encode(buffer, settings.rangeSwitch());
                NpcCombatMode.STREAM_CODEC.encode(buffer, settings.combatMode());
                NpcAdvancedCombat.STREAM_CODEC.encode(buffer, settings.advanced());
            },
            buffer -> new NpcCombatSettings(
                    NpcAttackSettings.STREAM_CODEC.decode(buffer),
                    NpcAttackSettings.STREAM_CODEC.decode(buffer),
                    NpcProjectileSettings.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.FLOAT.decode(buffer),
                    ByteBufCodecs.FLOAT.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    NpcRangeSwitch.STREAM_CODEC.decode(buffer),
                    NpcCombatMode.STREAM_CODEC.decode(buffer),
                    NpcAdvancedCombat.STREAM_CODEC.decode(buffer)));

    public static NpcCombatSettings defaults() {
        return new NpcCombatSettings(NpcAttackSettings.defaultMelee(), NpcAttackSettings.defaultRanged(),
                NpcProjectileSettings.defaults(), NpcDefaults.DISTANCE_PREFERENCE, NpcDefaults.AGGRO_RANGE,
                NpcDefaults.REQUIRE_LINE_OF_SIGHT, NpcDefaults.GIVE_UP_SECONDS, NpcDefaults.FRIENDLY_FIRE,
                NpcDefaults.DIFFICULTY_SCALING, NpcRangeSwitch.defaults(), NpcCombatMode.SIMPLE,
                NpcAdvancedCombat.defaults());
    }

    // Developer convenience: each returns a new record with a single field swapped, immutability kept.
    public NpcCombatSettings withMelee(NpcAttackSettings melee) {
        return new NpcCombatSettings(melee, ranged, projectile, distancePreference, aggroRange,
                requireLineOfSight, giveUpSeconds, friendlyFire, difficultyScaling, rangeSwitch,
                combatMode, advanced);
    }

    public NpcCombatSettings withRanged(NpcAttackSettings ranged) {
        return new NpcCombatSettings(melee, ranged, projectile, distancePreference, aggroRange,
                requireLineOfSight, giveUpSeconds, friendlyFire, difficultyScaling, rangeSwitch,
                combatMode, advanced);
    }

    public NpcCombatSettings withProjectile(NpcProjectileSettings projectile) {
        return new NpcCombatSettings(melee, ranged, projectile, distancePreference, aggroRange,
                requireLineOfSight, giveUpSeconds, friendlyFire, difficultyScaling, rangeSwitch,
                combatMode, advanced);
    }

    public NpcCombatSettings withAggroRange(float aggroRange) {
        return new NpcCombatSettings(melee, ranged, projectile, distancePreference, aggroRange,
                requireLineOfSight, giveUpSeconds, friendlyFire, difficultyScaling, rangeSwitch,
                combatMode, advanced);
    }

    public NpcCombatSettings withDistancePreference(float distancePreference) {
        return new NpcCombatSettings(melee, ranged, projectile, distancePreference, aggroRange,
                requireLineOfSight, giveUpSeconds, friendlyFire, difficultyScaling, rangeSwitch,
                combatMode, advanced);
    }

    public NpcCombatSettings withGiveUpSeconds(int giveUpSeconds) {
        return new NpcCombatSettings(melee, ranged, projectile, distancePreference, aggroRange,
                requireLineOfSight, giveUpSeconds, friendlyFire, difficultyScaling, rangeSwitch,
                combatMode, advanced);
    }

    public NpcCombatSettings withRequireLineOfSight(boolean requireLineOfSight) {
        return new NpcCombatSettings(melee, ranged, projectile, distancePreference, aggroRange,
                requireLineOfSight, giveUpSeconds, friendlyFire, difficultyScaling, rangeSwitch,
                combatMode, advanced);
    }

    public NpcCombatSettings withFriendlyFire(boolean friendlyFire) {
        return new NpcCombatSettings(melee, ranged, projectile, distancePreference, aggroRange,
                requireLineOfSight, giveUpSeconds, friendlyFire, difficultyScaling, rangeSwitch,
                combatMode, advanced);
    }

    public NpcCombatSettings withDifficultyScaling(boolean difficultyScaling) {
        return new NpcCombatSettings(melee, ranged, projectile, distancePreference, aggroRange,
                requireLineOfSight, giveUpSeconds, friendlyFire, difficultyScaling, rangeSwitch,
                combatMode, advanced);
    }

    public NpcCombatSettings withRangeSwitch(NpcRangeSwitch rangeSwitch) {
        return new NpcCombatSettings(melee, ranged, projectile, distancePreference, aggroRange,
                requireLineOfSight, giveUpSeconds, friendlyFire, difficultyScaling, rangeSwitch,
                combatMode, advanced);
    }

    public NpcCombatSettings withCombatMode(NpcCombatMode combatMode) {
        return new NpcCombatSettings(melee, ranged, projectile, distancePreference, aggroRange,
                requireLineOfSight, giveUpSeconds, friendlyFire, difficultyScaling, rangeSwitch,
                combatMode, advanced);
    }

    public NpcCombatSettings withAdvanced(NpcAdvancedCombat advanced) {
        return new NpcCombatSettings(melee, ranged, projectile, distancePreference, aggroRange,
                requireLineOfSight, giveUpSeconds, friendlyFire, difficultyScaling, rangeSwitch,
                combatMode, advanced);
    }

    // A melee build: the given weapon damage at default reach, everything else left at defaults.
    public static NpcCombatSettings meleeWeapon(float damage) {
        return defaults().withMelee(NpcAttackSettings.melee(damage, NpcDefaults.MELEE_RANGE));
    }

    // A ranged build: the given projectile item and damage at default range, everything else at defaults.
    public static NpcCombatSettings rangedWeapon(String projectileItemId, float damage) {
        NpcCombatSettings base = defaults();
        return base.withRanged(NpcAttackSettings.ranged(damage, NpcDefaults.RANGED_RANGE))
                .withProjectile(base.projectile().withItem(projectileItemId));
    }

    public boolean isValid() {
        return melee.isValid() && ranged.isValid() && projectile.isValid()
                && hasOnlyAllowedEnchants(melee, NpcValidation.MELEE_ENCHANTS)
                && hasOnlyAllowedEnchants(ranged, NpcValidation.RANGED_ENCHANTS)
                && distancePreference >= 0.0F && distancePreference <= NpcValidation.MAX_DISTANCE_PREFERENCE
                && aggroRange >= 0.0F && aggroRange <= NpcValidation.MAX_AGGRO_RANGE
                && giveUpSeconds >= 0 && giveUpSeconds <= NpcValidation.MAX_GIVE_UP_SECONDS
                && rangeSwitch.isValid()
                && combatMode != null && advanced.isValid();
    }

    private static boolean hasOnlyAllowedEnchants(NpcAttackSettings attack, List<String> allowed) {
        for (NpcAttackEffect effect : attack.effects()) {
            if (effect.isEnchantLike() && !allowed.contains(effect.effectId())) {
                return false;
            }
        }
        return true;
    }
}
