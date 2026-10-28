package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;
import net.lunacore.luna_npc.api.NpcQuestObjective;

// Server -> client: every quest the player currently has accepted, so the quest book can render each
// objective's live progress and count the time-left down locally between refreshes.
public record ActiveQuestsPayload(List<ActiveQuestView> quests) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ActiveQuestsPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "active_quests"));

    public static final StreamCodec<ByteBuf, ActiveQuestsPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.collection(ArrayList::new, ActiveQuestView.STREAM_CODEC), ActiveQuestsPayload::quests,
            ActiveQuestsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // One accepted quest: display title, free-form description, each objective paired with the count
    // done so far, and the countdown snapshot (timed = false when the quest has no limit).
    public record ActiveQuestView(String questId, String title, String description,
                                  List<NpcQuestObjective> objectives, List<Integer> progress,
                                  boolean timed, int secondsLeft) {

        public ActiveQuestView {
            objectives = List.copyOf(objectives);
            progress = List.copyOf(progress);
        }

        public static final StreamCodec<ByteBuf, ActiveQuestView> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, ActiveQuestView::questId,
                ByteBufCodecs.STRING_UTF8, ActiveQuestView::title,
                ByteBufCodecs.STRING_UTF8, ActiveQuestView::description,
                ByteBufCodecs.collection(ArrayList::new, NpcQuestObjective.STREAM_CODEC), ActiveQuestView::objectives,
                ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.VAR_INT), ActiveQuestView::progress,
                ByteBufCodecs.BOOL, ActiveQuestView::timed,
                ByteBufCodecs.VAR_INT, ActiveQuestView::secondsLeft,
                ActiveQuestView::new);
    }
}
