package net.lunacore.luna_npc.api;

import java.util.List;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// One thing the NPC does when a rule fires. type picks the effect; the shared fields carry its params —
// stringArg is the attack name / summon id / flag or counter key / timer name / status id / sound / nav
// mode / command / boss-bar colour depending on type; amount is a dynamic number (heal / count / damage);
// placement is where; target is who; effects are the statuses APPLY_STATUS grants. A tagged union, like
// NpcDialogAction.
public record NpcCombatAction(NpcCombatActionType type, NpcTargetSelector target, NpcCombatPlacement placement,
                              NpcValueSource amount, int durationTicks, int delayTicks, String stringArg,
                              List<NpcAttackEffect> effects, boolean flag) {

    public NpcCombatAction {
        effects = List.copyOf(effects);
    }

    public static final Codec<NpcCombatAction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            NpcCombatActionType.CODEC.fieldOf("type").forGetter(NpcCombatAction::type),
            NpcTargetSelector.CODEC.optionalFieldOf("target", NpcTargetSelector.PRIMARY)
                    .forGetter(NpcCombatAction::target),
            NpcCombatPlacement.CODEC.optionalFieldOf("placement", NpcCombatPlacement.atTarget())
                    .forGetter(NpcCombatAction::placement),
            NpcValueSource.CODEC.optionalFieldOf("amount", NpcValueSource.constant(0.0F))
                    .forGetter(NpcCombatAction::amount),
            Codec.INT.optionalFieldOf("duration_ticks", 0).forGetter(NpcCombatAction::durationTicks),
            Codec.INT.optionalFieldOf("delay_ticks", 0).forGetter(NpcCombatAction::delayTicks),
            Codec.STRING.optionalFieldOf("arg", "").forGetter(NpcCombatAction::stringArg),
            NpcAttackEffect.CODEC.listOf().optionalFieldOf("effects", List.of())
                    .forGetter(NpcCombatAction::effects),
            Codec.BOOL.optionalFieldOf("flag", false).forGetter(NpcCombatAction::flag)
    ).apply(instance, NpcCombatAction::new));

    public static final StreamCodec<ByteBuf, NpcCombatAction> STREAM_CODEC = StreamCodec.of(
            (buffer, action) -> {
                NpcCombatActionType.STREAM_CODEC.encode(buffer, action.type());
                NpcTargetSelector.STREAM_CODEC.encode(buffer, action.target());
                NpcCombatPlacement.STREAM_CODEC.encode(buffer, action.placement());
                NpcValueSource.STREAM_CODEC.encode(buffer, action.amount());
                ByteBufCodecs.VAR_INT.encode(buffer, action.durationTicks());
                ByteBufCodecs.VAR_INT.encode(buffer, action.delayTicks());
                ByteBufCodecs.STRING_UTF8.encode(buffer, action.stringArg());
                NpcAttackEffect.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, action.effects());
                ByteBufCodecs.BOOL.encode(buffer, action.flag());
            },
            buffer -> new NpcCombatAction(
                    NpcCombatActionType.STREAM_CODEC.decode(buffer),
                    NpcTargetSelector.STREAM_CODEC.decode(buffer),
                    NpcCombatPlacement.STREAM_CODEC.decode(buffer),
                    NpcValueSource.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    NpcAttackEffect.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer)));

    public boolean isValid() {
        if (type == null || target == null || placement == null || !placement.isValid()
                || amount == null || !amount.isValid() || stringArg == null
                || durationTicks < 0 || delayTicks < 0) {
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
