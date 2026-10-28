package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// What event fires an advanced-combat rule. New constants must be appended — the stream codec sends the
// ordinal.
public enum NpcCombatTriggerType implements StringRepresentable {
    ALWAYS("always"),
    INTERVAL("interval"),
    ON_COMBAT_START("on_combat_start"),
    ON_HP_CROSSES("on_hp_crosses"),
    ON_TOOK_DAMAGE("on_took_damage"),
    ON_DEALT_DAMAGE("on_dealt_damage"),
    ON_KILLED_TARGET("on_killed_target"),
    ON_ADD_DIED("on_add_died"),
    ON_ALL_ADDS_DEAD("on_all_adds_dead"),
    ON_TARGET_ACQUIRED("on_target_acquired"),
    ON_TARGET_LOST("on_target_lost"),
    ON_TIMER_ELAPSED("on_timer_elapsed"),
    ON_INTERRUPTED("on_interrupted"),
    ON_TARGET_ACTION("on_target_action"),
    ON_STATUS_APPLIED("on_status_applied"),
    ON_SURROUNDED("on_surrounded"),
    ON_DAMAGE_BURST("on_damage_burst"),
    ON_ENRAGE_TIMER("on_enrage_timer");

    public static final com.mojang.serialization.Codec<NpcCombatTriggerType> CODEC =
            StringRepresentable.fromEnum(NpcCombatTriggerType::values);
    public static final StreamCodec<ByteBuf, NpcCombatTriggerType> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], type -> (byte) type.ordinal());

    private final String id;

    NpcCombatTriggerType(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
