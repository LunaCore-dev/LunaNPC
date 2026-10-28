package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.npc.NpcValidation;

// One action a node runs, as a tagged union: only the fields its type needs are meaningful. stringArg
// is the flag key / command / relation / JUMP target node id / CUSTOM_EVENT tag / particle id; intArg
// is the XP amount / OPEN_TRADE sub-type index / MOVE_TO_ANCHOR radius / GIVE_BODYGUARD hire seconds
// (0 = the bodyguard job's own hire time); GIVE_BODYGUARD's stringArg is the lunaId to spawn and
// ORDER_BODYGUARDS' the order (follow / stay / home / dismiss); items feed GIVE_ITEMS and
// TAKE_ITEMS; quest is the inline quest ASSIGN_QUEST hands out; anchor/anchorDimension place
// MOVE_TO_ANCHOR; flag means run-as-player (RUN_COMMAND) or turn-hostile (SET_RELATION). Adding a new
// action type is a new enum constant plus one runtime arm — the shape never changes.
public record NpcDialogAction(NpcDialogActionType type, String stringArg, int intArg,
                              List<NpcTradeStack> items, NpcQuest quest,
                              BlockPos anchor, String anchorDimension, boolean flag) {

    public NpcDialogAction {
        items = List.copyOf(items);
    }

    private static final NpcQuest BLANK_QUEST = new NpcQuest("", "", "", "", List.of(),
            NpcQuestCompletion.TURN_IN, 0, 0, 0, List.of(), false, List.of());

    private static final StreamCodec<ByteBuf, List<NpcTradeStack>> ITEM_LIST =
            NpcTradeStack.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final Codec<NpcDialogAction> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            NpcDialogActionType.CODEC.fieldOf("type").forGetter(NpcDialogAction::type),
            Codec.STRING.optionalFieldOf("arg", "").forGetter(NpcDialogAction::stringArg),
            Codec.INT.optionalFieldOf("int", 0).forGetter(NpcDialogAction::intArg),
            NpcTradeStack.CODEC.listOf().optionalFieldOf("items", List.of()).forGetter(NpcDialogAction::items),
            NpcQuest.CODEC.optionalFieldOf("quest", BLANK_QUEST).forGetter(NpcDialogAction::quest),
            BlockPos.CODEC.optionalFieldOf("anchor", BlockPos.ZERO).forGetter(NpcDialogAction::anchor),
            Codec.STRING.optionalFieldOf("anchor_dim", "").forGetter(NpcDialogAction::anchorDimension),
            Codec.BOOL.optionalFieldOf("flag", false).forGetter(NpcDialogAction::flag)
    ).apply(instance, NpcDialogAction::new));

    public static final StreamCodec<ByteBuf, NpcDialogAction> STREAM_CODEC = StreamCodec.of(
            (buffer, action) -> {
                NpcDialogActionType.STREAM_CODEC.encode(buffer, action.type());
                ByteBufCodecs.STRING_UTF8.encode(buffer, action.stringArg());
                ByteBufCodecs.VAR_INT.encode(buffer, action.intArg());
                ITEM_LIST.encode(buffer, action.items());
                NpcQuest.STREAM_CODEC.encode(buffer, action.quest());
                BlockPos.STREAM_CODEC.encode(buffer, action.anchor());
                ByteBufCodecs.STRING_UTF8.encode(buffer, action.anchorDimension());
                ByteBufCodecs.BOOL.encode(buffer, action.flag());
            },
            buffer -> new NpcDialogAction(
                    NpcDialogActionType.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ITEM_LIST.decode(buffer),
                    NpcQuest.STREAM_CODEC.decode(buffer),
                    BlockPos.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer)));

    // The orders ORDER_BODYGUARDS takes in stringArg.
    public static final List<String> BODYGUARD_ORDERS = List.of("follow", "stay", "home", "dismiss");

    // GIVE_BODYGUARD's stringArg as a lunaId, or -1 when it isn't one.
    public static long parseLunaId(String value) {
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException | NullPointerException exception) {
            return -1L;
        }
    }

    public boolean isValid() {
        if (type == null || stringArg == null || anchorDimension == null || quest == null
                || intArg < 0 || items.size() > NpcValidation.MAX_DIALOG_ACTION_ITEMS) {
            return false;
        }
        for (NpcTradeStack item : items) {
            if (!item.isValid()) {
                return false;
            }
        }
        return switch (type) {
            case ASSIGN_QUEST -> quest.isValid();
            case GIVE_XP -> intArg <= NpcValidation.MAX_JOB_XP;
            case MOVE_TO_ANCHOR -> intArg <= NpcValidation.MAX_DIALOG_ANCHOR_RADIUS;
            case GIVE_BODYGUARD -> parseLunaId(stringArg) >= 0 && intArg <= NpcValidation.MAX_BODYGUARD_HIRE_SECONDS;
            case ORDER_BODYGUARDS -> BODYGUARD_ORDERS.contains(stringArg);
            default -> true;
        };
    }
}
