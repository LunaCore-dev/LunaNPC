package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.Mth;
import net.lunacore.luna_npc.npc.NpcDefaults;
import net.lunacore.luna_npc.npc.NpcEquipment;
import net.lunacore.luna_npc.npc.NpcValidation;

// How the long-range projectile looks and flies. The item is purely cosmetic (arrow, fire
// charge, TNT, wind charge, anything). Homing bends the shot a tiny bit towards the target;
// curve gives it an arc that bends 90° down over curveMaxDistance. Accuracy comes from the
// anchor curve: with dynamic accuracy on and no anchors, shots are perfect up close and fall to
// 50% at 10 blocks (10% per block); anchors replace that curve point by point. With dynamic
// accuracy off, every shot uses fixedAccuracy (percent) at any distance.
public record NpcProjectileSettings(String itemId, float speedBps, boolean homing, boolean curve,
                                    float curveMaxDistance, boolean dynamicAccuracy, int fixedAccuracy,
                                    List<NpcAccuracyAnchor> accuracyAnchors) {

    public NpcProjectileSettings {
        accuracyAnchors = List.copyOf(accuracyAnchors);
    }

    public static final Codec<NpcProjectileSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("item", NpcDefaults.PROJECTILE_ITEM)
                    .forGetter(NpcProjectileSettings::itemId),
            Codec.FLOAT.optionalFieldOf("speed_bps", NpcDefaults.PROJECTILE_SPEED)
                    .forGetter(NpcProjectileSettings::speedBps),
            Codec.BOOL.optionalFieldOf("homing", true).forGetter(NpcProjectileSettings::homing),
            Codec.BOOL.optionalFieldOf("curve", false).forGetter(NpcProjectileSettings::curve),
            Codec.FLOAT.optionalFieldOf("curve_max_distance", NpcDefaults.CURVE_MAX_DISTANCE)
                    .forGetter(NpcProjectileSettings::curveMaxDistance),
            Codec.BOOL.optionalFieldOf("dynamic_accuracy", true)
                    .forGetter(NpcProjectileSettings::dynamicAccuracy),
            Codec.INT.optionalFieldOf("fixed_accuracy", 100)
                    .forGetter(NpcProjectileSettings::fixedAccuracy),
            NpcAccuracyAnchor.CODEC.listOf().optionalFieldOf("accuracy_anchors", List.of())
                    .forGetter(NpcProjectileSettings::accuracyAnchors)
    ).apply(instance, NpcProjectileSettings::new));

    public static final StreamCodec<ByteBuf, NpcProjectileSettings> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, NpcProjectileSettings::itemId,
            ByteBufCodecs.FLOAT, NpcProjectileSettings::speedBps,
            ByteBufCodecs.BOOL, NpcProjectileSettings::homing,
            ByteBufCodecs.BOOL, NpcProjectileSettings::curve,
            ByteBufCodecs.FLOAT, NpcProjectileSettings::curveMaxDistance,
            ByteBufCodecs.BOOL, NpcProjectileSettings::dynamicAccuracy,
            ByteBufCodecs.VAR_INT, NpcProjectileSettings::fixedAccuracy,
            ByteBufCodecs.collection(ArrayList::new, NpcAccuracyAnchor.STREAM_CODEC),
            NpcProjectileSettings::accuracyAnchors,
            NpcProjectileSettings::new);

    public static NpcProjectileSettings defaults() {
        return new NpcProjectileSettings(NpcDefaults.PROJECTILE_ITEM, NpcDefaults.PROJECTILE_SPEED,
                true, false, NpcDefaults.CURVE_MAX_DISTANCE, true, 100, List.of());
    }

    // Developer convenience: each returns a new record with a single field swapped, immutability kept.
    public NpcProjectileSettings withItem(String itemId) {
        return new NpcProjectileSettings(itemId, speedBps, homing, curve, curveMaxDistance,
                dynamicAccuracy, fixedAccuracy, accuracyAnchors);
    }

    public NpcProjectileSettings withSpeed(float speedBps) {
        return new NpcProjectileSettings(itemId, speedBps, homing, curve, curveMaxDistance,
                dynamicAccuracy, fixedAccuracy, accuracyAnchors);
    }

    public NpcProjectileSettings withHoming(boolean homing) {
        return new NpcProjectileSettings(itemId, speedBps, homing, curve, curveMaxDistance,
                dynamicAccuracy, fixedAccuracy, accuracyAnchors);
    }

    public NpcProjectileSettings withCurve(boolean curve) {
        return new NpcProjectileSettings(itemId, speedBps, homing, curve, curveMaxDistance,
                dynamicAccuracy, fixedAccuracy, accuracyAnchors);
    }

    public NpcProjectileSettings withCurveMaxDistance(float curveMaxDistance) {
        return new NpcProjectileSettings(itemId, speedBps, homing, curve, curveMaxDistance,
                dynamicAccuracy, fixedAccuracy, accuracyAnchors);
    }

    public NpcProjectileSettings withDynamicAccuracy(boolean dynamicAccuracy) {
        return new NpcProjectileSettings(itemId, speedBps, homing, curve, curveMaxDistance,
                dynamicAccuracy, fixedAccuracy, accuracyAnchors);
    }

    public NpcProjectileSettings withFixedAccuracy(int fixedAccuracy) {
        return new NpcProjectileSettings(itemId, speedBps, homing, curve, curveMaxDistance,
                dynamicAccuracy, fixedAccuracy, accuracyAnchors);
    }

    public NpcProjectileSettings withAccuracyAnchors(List<NpcAccuracyAnchor> accuracyAnchors) {
        return new NpcProjectileSettings(itemId, speedBps, homing, curve, curveMaxDistance,
                dynamicAccuracy, fixedAccuracy, new ArrayList<>(accuracyAnchors));
    }

    // Accuracy percentage at the given distance. Without dynamic accuracy it is the fixed value.
    public float accuracyAt(float distance) {
        if (!dynamicAccuracy) {
            return fixedAccuracy;
        }
        return curveAccuracyAt(accuracyAnchors, distance);
    }

    // The raw anchor curve, shared with the client-side curve editor so the graph shows exactly
    // what the server will shoot.
    public static float curveAccuracyAt(List<NpcAccuracyAnchor> accuracyAnchors, float distance) {
        if (accuracyAnchors.isEmpty()) {
            // The built-in curve: 100% up to 5 blocks, then -10% per block down to 50% at 10+.
            if (distance <= 5.0F) {
                return 100.0F;
            }
            return Math.max(50.0F, 100.0F - (distance - 5.0F) * 10.0F);
        }
        List<NpcAccuracyAnchor> sorted = new ArrayList<>(accuracyAnchors);
        sorted.sort(java.util.Comparator.comparingInt(NpcAccuracyAnchor::distanceBlocks));
        // 0 blocks is implicitly perfect unless an anchor overrides it.
        if (sorted.getFirst().distanceBlocks() > 0) {
            sorted.addFirst(new NpcAccuracyAnchor(0, 100));
        }
        if (distance <= sorted.getFirst().distanceBlocks()) {
            return sorted.getFirst().percent();
        }
        for (int index = 1; index < sorted.size(); index++) {
            NpcAccuracyAnchor previous = sorted.get(index - 1);
            NpcAccuracyAnchor next = sorted.get(index);
            if (distance <= next.distanceBlocks()) {
                float progress = (distance - previous.distanceBlocks())
                        / (float) (next.distanceBlocks() - previous.distanceBlocks());
                return Mth.lerp(progress, previous.percent(), next.percent());
            }
        }
        return sorted.getLast().percent();
    }

    public boolean isValid() {
        if (!NpcEquipment.isKnownOrEmpty(itemId)
                || speedBps < 0.0F || speedBps > NpcValidation.MAX_PROJECTILE_SPEED
                || curveMaxDistance < NpcValidation.MIN_CURVE_DISTANCE
                || curveMaxDistance > NpcValidation.MAX_CURVE_DISTANCE
                || fixedAccuracy < 0 || fixedAccuracy > 100
                || accuracyAnchors.size() > NpcValidation.MAX_ACCURACY_DISTANCE + 1) {
            return false;
        }
        Set<Integer> distances = new HashSet<>();
        for (NpcAccuracyAnchor anchor : accuracyAnchors) {
            if (!anchor.isValid() || !distances.add(anchor.distanceBlocks())) {
                return false;
            }
        }
        return true;
    }
}
