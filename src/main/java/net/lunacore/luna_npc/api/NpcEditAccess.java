package net.lunacore.luna_npc.api;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

// Who may change an NPC definition — its settings, name and alliance memberships. API_ONLY: only the API;
// the editor lists it locked. GUI_ONLY: only the in-game editor; API writes throw IllegalStateException.
// SHARED: both, last write wins, so API code should treat its setup as a one-time run. Reading,
// spawning copies, removing live ones and live NpcInstance actions stay open in every mode. Shared things
// an NPC uses (alliances themselves, routes, zones) are not covered.
// New constants must be appended — the stream codec sends ordinals.
public enum NpcEditAccess implements StringRepresentable {
    API_ONLY("api_only"),
    GUI_ONLY("gui_only"),
    SHARED("shared");

    public static final com.mojang.serialization.Codec<NpcEditAccess> CODEC =
            StringRepresentable.fromEnum(NpcEditAccess::values);
    public static final StreamCodec<ByteBuf, NpcEditAccess> STREAM_CODEC =
            ByteBufCodecs.BYTE.map(b -> values()[b], value -> (byte) value.ordinal());

    private final String id;

    NpcEditAccess(String id) {
        this.id = id;
    }

    public boolean apiCanEdit() {
        return this != GUI_ONLY;
    }

    public boolean guiCanEdit() {
        return this != API_ONLY;
    }

    @Override
    public String getSerializedName() {
        return id;
    }
}
