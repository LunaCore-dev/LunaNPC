package net.lunacore.luna_npc.api;

import java.util.List;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// One advanced-combat rule: when the trigger fires and every condition passes, the actions run in order.
// cooldownSeconds gates re-firing; once = at most once per fight. graphX/graphY persist the node's canvas
// position for the editor.
public record NpcCombatRule(NpcCombatTrigger trigger, List<NpcCombatCondition> conditions,
                            List<NpcCombatAction> actions, float cooldownSeconds, boolean once,
                            int graphX, int graphY) {

    public NpcCombatRule {
        conditions = List.copyOf(conditions);
        actions = List.copyOf(actions);
    }

    public static final Codec<NpcCombatRule> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            NpcCombatTrigger.CODEC.optionalFieldOf("trigger", NpcCombatTrigger.always())
                    .forGetter(NpcCombatRule::trigger),
            NpcCombatCondition.CODEC.listOf().optionalFieldOf("conditions", List.of())
                    .forGetter(NpcCombatRule::conditions),
            NpcCombatAction.CODEC.listOf().optionalFieldOf("actions", List.of())
                    .forGetter(NpcCombatRule::actions),
            Codec.FLOAT.optionalFieldOf("cooldown_seconds", 0.0F).forGetter(NpcCombatRule::cooldownSeconds),
            Codec.BOOL.optionalFieldOf("once", false).forGetter(NpcCombatRule::once),
            Codec.INT.optionalFieldOf("graph_x", 0).forGetter(NpcCombatRule::graphX),
            Codec.INT.optionalFieldOf("graph_y", 0).forGetter(NpcCombatRule::graphY)
    ).apply(instance, NpcCombatRule::new));

    public static final StreamCodec<ByteBuf, NpcCombatRule> STREAM_CODEC = StreamCodec.of(
            (buffer, rule) -> {
                NpcCombatTrigger.STREAM_CODEC.encode(buffer, rule.trigger());
                NpcCombatCondition.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, rule.conditions());
                NpcCombatAction.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buffer, rule.actions());
                ByteBufCodecs.FLOAT.encode(buffer, rule.cooldownSeconds());
                ByteBufCodecs.BOOL.encode(buffer, rule.once());
                ByteBufCodecs.VAR_INT.encode(buffer, rule.graphX());
                ByteBufCodecs.VAR_INT.encode(buffer, rule.graphY());
            },
            buffer -> new NpcCombatRule(
                    NpcCombatTrigger.STREAM_CODEC.decode(buffer),
                    NpcCombatCondition.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer),
                    NpcCombatAction.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buffer),
                    ByteBufCodecs.FLOAT.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer)));

    public boolean isValid() {
        if (trigger == null || !trigger.isValid() || cooldownSeconds < 0.0F) {
            return false;
        }
        for (NpcCombatCondition condition : conditions) {
            if (!condition.isValid()) {
                return false;
            }
        }
        for (NpcCombatAction action : actions) {
            if (!action.isValid()) {
                return false;
            }
        }
        return true;
    }
}
