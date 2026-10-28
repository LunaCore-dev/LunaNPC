package net.lunacore.luna_npc.api;

import java.util.List;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// A reusable attack the advanced-combat rules can invoke: a kind (melee/projectile/pattern/aoe/beam) with
// its damage, reach and projectile tuning, the effects it applies, and the placement its shots spawn from.
public record NpcCombatAttack(String name, NpcCombatAttackKind kind, float damage, float range, float speedBps,
                              String projectileItemId, boolean homing, boolean curve, int projectileCount,
                              float spreadDegrees, List<NpcAttackEffect> effects, NpcCombatPlacement placement) {

    public NpcCombatAttack {
        effects = List.copyOf(effects);
    }

    public static final Codec<NpcCombatAttack> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("name").forGetter(NpcCombatAttack::name),
            NpcCombatAttackKind.CODEC.optionalFieldOf("kind", NpcCombatAttackKind.MELEE)
                    .forGetter(NpcCombatAttack::kind),
            Codec.FLOAT.optionalFieldOf("damage", 5.0F).forGetter(NpcCombatAttack::damage),
            Codec.FLOAT.optionalFieldOf("range", 3.0F).forGetter(NpcCombatAttack::range),
            Codec.FLOAT.optionalFieldOf("speed_bps", 20.0F).forGetter(NpcCombatAttack::speedBps),
            Codec.STRING.optionalFieldOf("projectile_item", "minecraft:arrow")
                    .forGetter(NpcCombatAttack::projectileItemId),
            Codec.BOOL.optionalFieldOf("homing", false).forGetter(NpcCombatAttack::homing),
            Codec.BOOL.optionalFieldOf("curve", false).forGetter(NpcCombatAttack::curve),
            Codec.INT.optionalFieldOf("projectile_count", 1).forGetter(NpcCombatAttack::projectileCount),
            Codec.FLOAT.optionalFieldOf("spread_degrees", 0.0F).forGetter(NpcCombatAttack::spreadDegrees),
            NpcAttackEffect.CODEC.listOf().optionalFieldOf("effects", List.of())
                    .forGetter(NpcCombatAttack::effects),
            NpcCombatPlacement.CODEC.optionalFieldOf("placement", NpcCombatPlacement.atTarget())
                    .forGetter(NpcCombatAttack::placement)
    ).apply(instance, NpcCombatAttack::new));

    // Twelve fields plus a list exceed StreamCodec.composite, so the network codec is hand-rolled.
    public static final StreamCodec<ByteBuf, NpcCombatAttack> STREAM_CODEC = StreamCodec.of(
            (buffer, attack) -> {
                ByteBufCodecs.STRING_UTF8.encode(buffer, attack.name());
                NpcCombatAttackKind.STREAM_CODEC.encode(buffer, attack.kind());
                ByteBufCodecs.FLOAT.encode(buffer, attack.damage());
                ByteBufCodecs.FLOAT.encode(buffer, attack.range());
                ByteBufCodecs.FLOAT.encode(buffer, attack.speedBps());
                ByteBufCodecs.STRING_UTF8.encode(buffer, attack.projectileItemId());
                ByteBufCodecs.BOOL.encode(buffer, attack.homing());
                ByteBufCodecs.BOOL.encode(buffer, attack.curve());
                ByteBufCodecs.VAR_INT.encode(buffer, attack.projectileCount());
                ByteBufCodecs.FLOAT.encode(buffer, attack.spreadDegrees());
                NpcAttackEffect.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, attack.effects());
                NpcCombatPlacement.STREAM_CODEC.encode(buffer, attack.placement());
            },
            buffer -> new NpcCombatAttack(
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    NpcCombatAttackKind.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.FLOAT.decode(buffer),
                    ByteBufCodecs.FLOAT.decode(buffer),
                    ByteBufCodecs.FLOAT.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.FLOAT.decode(buffer),
                    NpcAttackEffect.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer),
                    NpcCombatPlacement.STREAM_CODEC.decode(buffer)));

    public boolean isValid() {
        if (name == null || name.isBlank() || kind == null || damage < 0.0F || range < 0.0F
                || placement == null || !placement.isValid()) {
            return false;
        }
        for (NpcAttackEffect effect : effects) {
            if (!effect.isValid()) {
                return false;
            }
        }
        return true;
    }
}
