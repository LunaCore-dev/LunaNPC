package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// What an advanced-combat rule does when it fires. New constants must be appended — the stream codec sends
// the ordinal.
public enum NpcCombatActionType implements StringRepresentable {
    USE_ATTACK("use_attack"),
    EXPLOSION("explosion"),
    TELEGRAPH("telegraph"),
    SUMMON("summon"),
    TELEPORT("teleport"),
    MOVE_TO("move_to"),
    HEAL("heal"),
    BUFF_SELF("buff_self"),
    APPLY_STATUS("apply_status"),
    CLEANSE("cleanse"),
    SHIELD("shield"),
    INVULN("invuln"),
    GUARD("guard"),
    UNTARGETABLE("untargetable"),
    SWITCH_NAV_MODE("switch_nav_mode"),
    KNOCKBACK("knockback"),
    PULL("pull"),
    SWITCH_AGGRO("switch_aggro"),
    SET_FLAG("set_flag"),
    CLEAR_FLAG("clear_flag"),
    INC_COUNTER("inc_counter"),
    START_TIMER("start_timer"),
    ENRAGE("enrage"),
    BOSS_BAR("boss_bar"),
    PARTICLE("particle"),
    SOUND("sound"),
    PLACE_HAZARD("place_hazard"),
    TAUNT("taunt"),
    RUN_COMMAND("run_command"),
    CUSTOM_EVENT("custom_event"),
    WAIT("wait"),
    END_COMBAT("end_combat");

    public static final com.mojang.serialization.Codec<NpcCombatActionType> CODEC =
            StringRepresentable.fromEnum(NpcCombatActionType::values);
    public static final StreamCodec<ByteBuf, NpcCombatActionType> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], type -> (byte) type.ordinal());

    private final String id;

    NpcCombatActionType(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
