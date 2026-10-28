package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// A "lego brick" a dialog node fires when entered. Each also reaches developers as a
// DialogActionCallback event, so a mod can react even to a type it doesn't recognise. CUSTOM_EVENT has
// no built-in effect — it only fires the event, carrying stringArg as a developer tag. New constants
// must be appended — the stream codec sends ordinals. GIVE_BODYGUARD spawns an NPC beside the player and
// hires it for the player; ORDER_BODYGUARDS gives every bodyguard the player hired an order.
public enum NpcDialogActionType implements StringRepresentable {
    ASSIGN_QUEST("assign_quest"),
    OPEN_TRADE("open_trade"),
    ATTACK_PLAYER("attack_player"),
    MOVE_TO_ANCHOR("move_to_anchor"),
    SET_RELATION("set_relation"),
    GIVE_ITEMS("give_items"),
    TAKE_ITEMS("take_items"),
    GIVE_XP("give_xp"),
    RUN_COMMAND("run_command"),
    PARTICLE("particle"),
    SET_FLAG("set_flag"),
    CLEAR_FLAG("clear_flag"),
    JUMP("jump"),
    CUSTOM_EVENT("custom_event"),
    GIVE_BODYGUARD("give_bodyguard"),
    ORDER_BODYGUARDS("order_bodyguards");

    public static final com.mojang.serialization.Codec<NpcDialogActionType> CODEC =
            StringRepresentable.fromEnum(NpcDialogActionType::values);
    public static final StreamCodec<ByteBuf, NpcDialogActionType> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], type -> (byte) type.ordinal());

    private final String id;

    NpcDialogActionType(String id) {
        this.id = id;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
