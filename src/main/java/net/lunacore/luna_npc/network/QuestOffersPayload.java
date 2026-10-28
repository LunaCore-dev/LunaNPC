package net.lunacore.luna_npc.network;

import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.lunacore.luna_npc.LunaNpc;
import net.lunacore.luna_npc.api.NpcQuest;

// Server -> client: the quests a quest NPC's rolled sub-type offers, plus this player's live standing
// on each one, so the offer screen can render per-objective progress and enable the right button.
public record QuestOffersPayload(int npcEntityId, List<NpcQuest> quests, List<QuestStatusView> statuses)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<QuestOffersPayload> TYPE =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "quest_offers"));

    public static final StreamCodec<ByteBuf, QuestOffersPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, QuestOffersPayload::npcEntityId,
            ByteBufCodecs.collection(ArrayList::new, NpcQuest.STREAM_CODEC), QuestOffersPayload::quests,
            ByteBufCodecs.collection(ArrayList::new, QuestStatusView.STREAM_CODEC), QuestOffersPayload::statuses,
            QuestOffersPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    // One player's standing on one offered quest. progress carries a count per objective (kills done,
    // or items currently held for a collect goal); secondsLeft is the live countdown of a time-limited
    // accepted quest, 0 when unlimited or not yet accepted.
    public record QuestStatusView(String questId, byte state, List<Integer> progress, int secondsLeft) {

        public static final byte OFFERABLE = 0;
        public static final byte ACTIVE = 1;
        public static final byte READY = 2;

        public QuestStatusView {
            progress = List.copyOf(progress);
        }

        public static final StreamCodec<ByteBuf, QuestStatusView> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, QuestStatusView::questId,
                ByteBufCodecs.BYTE, QuestStatusView::state,
                ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.VAR_INT), QuestStatusView::progress,
                ByteBufCodecs.VAR_INT, QuestStatusView::secondsLeft,
                QuestStatusView::new);
    }
}
