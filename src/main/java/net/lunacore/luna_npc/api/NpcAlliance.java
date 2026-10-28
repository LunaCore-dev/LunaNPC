package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// A named faction. Its members are vanilla mob types and specific players (LunaNPC members are
// tracked on the NPC itself, by its allianceId, so an NPC has exactly one home alliance). The
// enemies list is who an NPC of this alliance hunts; the mates list is who it protects; anyone
// on neither is neutral. friendlyFire is the alliance-wide rule for whether members of THIS same
// faction may fight each other: PER_NPC defers to each NPC's own toggle, ALLOW/BLOCK force it and
// override (and grey out) the per-NPC toggle. Built-in alliances are seeded by us and cannot be
// deleted, but their membership, relationships and friendly-fire rule stay fully editable.
public record NpcAlliance(String id, String name, boolean builtin, List<String> memberMobTypes,
                          List<String> memberPlayers, List<String> enemies, List<String> mates,
                          boolean matchAllPlayers, String friendlyFire) {

    // Alliance-wide friendly-fire rule for same-faction members.
    public static final String FF_PER_NPC = "per_npc";
    public static final String FF_ALLOW = "allow";
    public static final String FF_BLOCK = "block";

    public NpcAlliance {
        friendlyFire = switch (friendlyFire == null ? "" : friendlyFire) {
            case FF_ALLOW, FF_BLOCK, FF_PER_NPC -> friendlyFire;
            default -> FF_PER_NPC;
        };
    }

    private static final StreamCodec<ByteBuf, List<String>> STRING_LIST =
            ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8);

    public static final Codec<NpcAlliance> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(NpcAlliance::id),
            Codec.STRING.fieldOf("name").forGetter(NpcAlliance::name),
            Codec.BOOL.optionalFieldOf("builtin", false).forGetter(NpcAlliance::builtin),
            Codec.STRING.listOf().optionalFieldOf("member_mob_types", List.of())
                    .forGetter(NpcAlliance::memberMobTypes),
            Codec.STRING.listOf().optionalFieldOf("member_players", List.of())
                    .forGetter(NpcAlliance::memberPlayers),
            Codec.STRING.listOf().optionalFieldOf("enemies", List.of()).forGetter(NpcAlliance::enemies),
            Codec.STRING.listOf().optionalFieldOf("mates", List.of()).forGetter(NpcAlliance::mates),
            Codec.BOOL.optionalFieldOf("match_all_players", false).forGetter(NpcAlliance::matchAllPlayers),
            Codec.STRING.optionalFieldOf("friendly_fire", FF_PER_NPC).forGetter(NpcAlliance::friendlyFire)
    ).apply(instance, NpcAlliance::new));

    // Nine fields exceed StreamCodec.composite's arity, so the network codec is written by hand.
    public static final StreamCodec<ByteBuf, NpcAlliance> STREAM_CODEC = StreamCodec.of(
            (buffer, alliance) -> {
                ByteBufCodecs.STRING_UTF8.encode(buffer, alliance.id());
                ByteBufCodecs.STRING_UTF8.encode(buffer, alliance.name());
                ByteBufCodecs.BOOL.encode(buffer, alliance.builtin());
                STRING_LIST.encode(buffer, alliance.memberMobTypes());
                STRING_LIST.encode(buffer, alliance.memberPlayers());
                STRING_LIST.encode(buffer, alliance.enemies());
                STRING_LIST.encode(buffer, alliance.mates());
                ByteBufCodecs.BOOL.encode(buffer, alliance.matchAllPlayers());
                ByteBufCodecs.STRING_UTF8.encode(buffer, alliance.friendlyFire());
            },
            buffer -> new NpcAlliance(
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    STRING_LIST.decode(buffer),
                    STRING_LIST.decode(buffer),
                    STRING_LIST.decode(buffer),
                    STRING_LIST.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer)));

    // How an NPC of one alliance regards a member of another.
    public enum Relation {
        ENEMY,
        MATE,
        NEUTRAL
    }

    public Relation relationTo(String otherAllianceId) {
        if (enemies.contains(otherAllianceId)) {
            return Relation.ENEMY;
        }
        if (mates.contains(otherAllianceId)) {
            return Relation.MATE;
        }
        return Relation.NEUTRAL;
    }

    public NpcAlliance withName(String newName) {
        return new NpcAlliance(id, newName, builtin, memberMobTypes, memberPlayers, enemies, mates,
                matchAllPlayers, friendlyFire);
    }
}
