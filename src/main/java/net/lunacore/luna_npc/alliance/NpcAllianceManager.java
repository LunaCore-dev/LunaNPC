package net.lunacore.luna_npc.alliance;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.saveddata.SavedDataType;
import net.lunacore.luna_npc.LunaNpc;
import net.lunacore.luna_npc.api.AllianceRegistry;
import net.lunacore.luna_npc.entity.NpcEntity;
import net.lunacore.luna_npc.npc.NpcValidation;
import net.lunacore.luna_npc.api.NpcAlliance;

// The world's faction registry: built-in alliances that partition every vanilla mob per the
// AllianceTaxonomy ecosystem, alongside any alliances a creator adds. Every living thing resolves
// to exactly one alliance — LunaNPCs by their own allianceId, players by explicit assignment else
// Players, mobs by their type, with a Neutral safety net so nothing is ever unclassified even for
// a mob added by another mod.
public final class NpcAllianceManager extends SavedData implements AllianceRegistry {

    // Bumped on every change, so LunaNpcNetworking can push API edits to clients too.
    private int changeCount;

    public static final String CREATURES = "creatures";
    public static final String PLAYERS = "players";
    public static final String DEFAULT_NPC_ALLIANCE = net.lunacore.luna_npc.npc.NpcDefaults.ALLIANCE;

    // Bumped whenever the built-in taxonomy changes; a world saved at a lower version re-seeds its
    // built-in factions from the current taxonomy on load.
    private static final int TAXONOMY_VERSION = 1;

    private static final java.util.Set<String> BUILTIN_IDS = AllianceTaxonomy.BUILTINS.stream()
            .map(AllianceTaxonomy.Builtin::id).collect(java.util.stream.Collectors.toUnmodifiableSet());

    private static final Codec<NpcAllianceManager> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            NpcAlliance.CODEC.listOf().optionalFieldOf("alliances", List.of())
                    .forGetter(manager -> List.copyOf(manager.alliances.values())),
            Codec.INT.optionalFieldOf("taxonomy_version", 0).forGetter(manager -> manager.version)
    ).apply(instance, NpcAllianceManager::new));

    private static final SavedDataType<NpcAllianceManager> TYPE = new SavedDataType<>(
            Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "alliances"),
            NpcAllianceManager::new,
            CODEC,
            DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("LunaNPC");

    private final Map<String, NpcAlliance> alliances = new LinkedHashMap<>();
    private int version;

    private NpcAllianceManager() {
        this(List.of(), 0);
    }

    private NpcAllianceManager(List<NpcAlliance> stored, int storedVersion) {
        for (NpcAlliance alliance : stored) {
            // The codec decodes immutable lists; the edit methods mutate in place, so store a
            // mutable copy.
            alliances.put(alliance.id(), mutableCopy(alliance));
        }
        if (storedVersion < TAXONOMY_VERSION) {
            // Fresh world, or upgrade from an older taxonomy: (re)build the built-in factions from
            // the current ecosystem and redistribute every mob's membership. Custom alliances keep
            // their identity, relationships and player membership; only mob membership is reset.
            reseedFromTaxonomy();
            setDirty();
        } else {
            ensureBuiltins();
        }
        this.version = TAXONOMY_VERSION;
    }

    public static NpcAllianceManager get(MinecraftServer server) {
        return server.getDataStorage().computeIfAbsent(TYPE);
    }

    // Re-adds any built-in alliance definition that is missing, so the core factions always exist
    // even if a client edit dropped one. Existing built-ins are left untouched.
    private void ensureBuiltins() {
        for (AllianceTaxonomy.Builtin builtin : AllianceTaxonomy.BUILTINS) {
            if (!alliances.containsKey(builtin.id())) {
                alliances.put(builtin.id(), new NpcAlliance(builtin.id(), builtin.name(), true,
                        new ArrayList<>(), new ArrayList<>(),
                        new ArrayList<>(builtin.enemies()), new ArrayList<>(builtin.mates()), false,
                        NpcAlliance.FF_PER_NPC));
            }
        }
    }

    private void reseedFromTaxonomy() {
        // Reset built-in definitions (relationships + names) to the taxonomy, keeping their player
        // membership; custom alliances keep everything, including any mobs the creator assigned.
        for (AllianceTaxonomy.Builtin builtin : AllianceTaxonomy.BUILTINS) {
            NpcAlliance existing = alliances.get(builtin.id());
            List<String> players = existing == null ? new ArrayList<>()
                    : new ArrayList<>(existing.memberPlayers());
            boolean matchAll = existing != null && existing.matchAllPlayers();
            String friendlyFire = existing == null ? NpcAlliance.FF_PER_NPC : existing.friendlyFire();
            alliances.put(builtin.id(), new NpcAlliance(builtin.id(), builtin.name(), true,
                    new ArrayList<>(), players,
                    new ArrayList<>(builtin.enemies()), new ArrayList<>(builtin.mates()), matchAll,
                    friendlyFire));
        }
        seedDefaultMembership();
    }

    // Assigns every registered mob to its taxonomy alliance, unless a (custom) alliance already
    // claims it. Anything the taxonomy doesn't list (a modded mob) falls to Neutral so it is still
    // classified, and is logged for visibility.
    private void seedDefaultMembership() {
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            String id = BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
            if ("minecraft:player".equals(id) || isMobClaimed(id)) {
                continue;
            }
            String alliance = AllianceTaxonomy.allianceFor(id);
            if (alliance == null) {
                // Only living things need a faction; skip projectiles, displays, vehicles, etc.
                if (type.getCategory() == MobCategory.MISC) {
                    continue;
                }
                alliance = AllianceTaxonomy.FALLBACK_ALLIANCE;
                LOGGER.info("Unmapped mob {} placed in the Neutral alliance", id);
            }
            NpcAlliance target = alliances.get(alliance);
            if (target != null) {
                target.memberMobTypes().add(id);
            }
        }
    }

    private boolean isMobClaimed(String mobTypeId) {
        for (NpcAlliance alliance : alliances.values()) {
            if (alliance.memberMobTypes().contains(mobTypeId)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<NpcAlliance> all() {
        // Hand out copies so a consumer can't corrupt the faction graph by mutating the stored lists.
        return alliances.values().stream().map(NpcAllianceManager::mutableCopy).toList();
    }

    @Override
    public NpcAlliance byId(String allianceId) {
        NpcAlliance alliance = alliances.get(allianceId);
        return alliance == null ? null : mutableCopy(alliance);
    }

    public boolean exists(String allianceId) {
        return alliances.containsKey(allianceId);
    }

    // The alliance any entity belongs to: an NPC's home alliance, a player's assignment (else
    // Players), or a mob's type mapping (falling back to Animals so nothing is unclassified).
    public String allianceIdOf(Entity entity) {
        if (entity instanceof NpcEntity npc) {
            String id = npc.allianceIds().isEmpty() ? DEFAULT_NPC_ALLIANCE : npc.allianceIds().get(0);
            return alliances.containsKey(id) ? id : DEFAULT_NPC_ALLIANCE;
        }
        if (entity instanceof Player player) {
            return allianceOfPlayer(player.getUUID());
        }
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return allianceOfMobType(key == null ? "" : key.toString());
    }

    @Override
    public String allianceOfMobType(String mobTypeId) {
        for (NpcAlliance alliance : alliances.values()) {
            if (alliance.memberMobTypes().contains(mobTypeId)) {
                return alliance.id();
            }
        }
        // Not explicitly assigned to any alliance: fall back to its taxonomy faction (so a mob
        // pulled out of a custom alliance rejoins Golems/Undead/etc., not Neutral), then Neutral.
        String taxonomy = AllianceTaxonomy.allianceFor(mobTypeId);
        return taxonomy != null && alliances.containsKey(taxonomy) ? taxonomy : AllianceTaxonomy.FALLBACK_ALLIANCE;
    }

    @Override
    public String allianceOfPlayer(UUID playerId) {
        String key = playerId.toString();
        for (NpcAlliance alliance : alliances.values()) {
            if (alliance.memberPlayers().contains(key)) {
                return alliance.id();
            }
        }
        // An explicit assignment wins; otherwise the first "all players" alliance, else Players.
        for (NpcAlliance alliance : alliances.values()) {
            if (alliance.matchAllPlayers()) {
                return alliance.id();
            }
        }
        return PLAYERS;
    }

    public NpcAlliance.Relation relation(String fromAllianceId, String toAllianceId) {
        NpcAlliance from = alliances.get(fromAllianceId);
        return from == null ? NpcAlliance.Relation.NEUTRAL : from.relationTo(toAllianceId);
    }

    // True when this alliance attacks someone or is attacked by someone — used to skip the
    // vanilla-mob aggression scan entirely for peaceful factions.
    public boolean hasHostility(String allianceId) {
        NpcAlliance alliance = alliances.get(allianceId);
        if (alliance != null && !alliance.enemies().isEmpty()) {
            return true;
        }
        for (NpcAlliance other : alliances.values()) {
            if (other.enemies().contains(allianceId)) {
                return true;
            }
        }
        return false;
    }

    // Two entities are mutually hostile when either one's faction hunts one of the other's, across
    // all their memberships (a mate tie breaks toward peace, via classify's mate > enemy order).
    public boolean mutuallyHostile(Entity a, Entity b) {
        java.util.Set<String> aSet = alliancesOf(a);
        java.util.Set<String> bSet = alliancesOf(b);
        for (String id : aSet) {
            if (classify(id, bSet) == NpcAlliance.Relation.ENEMY) {
                return true;
            }
        }
        for (String id : bSet) {
            if (classify(id, aSet) == NpcAlliance.Relation.ENEMY) {
                return true;
            }
        }
        return false;
    }

    // Every alliance an entity belongs to. Membership is a SET, never a single value, with no cap:
    // an NPC has its one home alliance; a player is ALWAYS Players PLUS every explicit or
    // match-all-players alliance; a vanilla mob belongs to every alliance whose member list holds
    // its type, falling back to its taxonomy faction then Neutral. Overlapping membership is the
    // point — it is how relationship webs across many factions and hundreds of NPCs are expressed.
    public java.util.Set<String> alliancesOf(Entity entity) {
        if (entity instanceof NpcEntity npc) {
            java.util.Set<String> result = new java.util.LinkedHashSet<>();
            for (String id : npc.allianceIds()) {
                result.add(alliances.containsKey(id) ? id : DEFAULT_NPC_ALLIANCE);
            }
            return result.isEmpty() ? java.util.Set.of(DEFAULT_NPC_ALLIANCE) : result;
        }
        if (entity instanceof Player player) {
            return alliancesOfPlayer(player.getUUID());
        }
        Identifier key = BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType());
        return alliancesOfMobType(key == null ? "" : key.toString());
    }

    // A player is always a member of Players, on top of any alliance that lists them explicitly or
    // has match-all-players on. match-all-players is ADDITIVE — it never removes the player identity,
    // so a boss whose enemy is Players still hunts a player who also belongs to some other alliance.
    public java.util.Set<String> alliancesOfPlayer(UUID playerId) {
        String key = playerId.toString();
        java.util.Set<String> result = new java.util.LinkedHashSet<>();
        result.add(PLAYERS);
        for (NpcAlliance alliance : alliances.values()) {
            if (alliance.matchAllPlayers() || alliance.memberPlayers().contains(key)) {
                result.add(alliance.id());
            }
        }
        return result;
    }

    // Every alliance whose member list holds this mob type; if none, its taxonomy faction (else Neutral).
    public java.util.Set<String> alliancesOfMobType(String mobTypeId) {
        java.util.Set<String> result = new java.util.LinkedHashSet<>();
        for (NpcAlliance alliance : alliances.values()) {
            if (alliance.memberMobTypes().contains(mobTypeId)) {
                result.add(alliance.id());
            }
        }
        if (result.isEmpty()) {
            String taxonomy = AllianceTaxonomy.allianceFor(mobTypeId);
            result.add(taxonomy != null && alliances.containsKey(taxonomy)
                    ? taxonomy : AllianceTaxonomy.FALLBACK_ALLIANCE);
        }
        return result;
    }

    // How an NPC of fromAllianceId regards a target that belongs to targetAlliances. Overlaps are
    // allowed and expected; when the target is both a mate and an enemy the fixed conflict order
    // mate > enemy > neutral applies, so a mate membership shields it. NEUTRAL for an unknown from.
    public NpcAlliance.Relation classify(String fromAllianceId, java.util.Set<String> targetAlliances) {
        NpcAlliance from = alliances.get(fromAllianceId);
        if (from == null) {
            return NpcAlliance.Relation.NEUTRAL;
        }
        boolean mate = false;
        boolean enemy = false;
        for (String id : targetAlliances) {
            if (from.mates().contains(id)) {
                mate = true;
            } else if (from.enemies().contains(id)) {
                enemy = true;
            }
        }
        if (mate) {
            return NpcAlliance.Relation.MATE;
        }
        return enemy ? NpcAlliance.Relation.ENEMY : NpcAlliance.Relation.NEUTRAL;
    }

    public NpcAlliance.Relation classify(String fromAllianceId, Entity target) {
        return classify(fromAllianceId, alliancesOf(target));
    }

    // How an entity that belongs to fromAlliances (e.g. a multi-faction NPC) regards a target in
    // targetAlliances: mate wins across every one of its factions (mate > enemy > neutral), so a mate
    // in any faction shields the target even if another faction of the NPC marks it an enemy.
    public NpcAlliance.Relation classify(java.util.Set<String> fromAlliances, java.util.Set<String> targetAlliances) {
        boolean enemy = false;
        for (String from : fromAlliances) {
            NpcAlliance.Relation relation = classify(from, targetAlliances);
            if (relation == NpcAlliance.Relation.MATE) {
                return NpcAlliance.Relation.MATE;
            }
            if (relation == NpcAlliance.Relation.ENEMY) {
                enemy = true;
            }
        }
        return enemy ? NpcAlliance.Relation.ENEMY : NpcAlliance.Relation.NEUTRAL;
    }

    // From one entity's perspective toward another, both resolved to their full alliance sets.
    public NpcAlliance.Relation classify(Entity from, Entity target) {
        return classify(alliancesOf(from), alliancesOf(target));
    }

    // Replaces the whole set from a client edit: keeps only valid alliances, forces the built-ins
    // to stay present and flagged, and scrubs relationship entries that point at deleted ids.
    public void applyAll(List<NpcAlliance> incoming) {
        Map<String, NpcAlliance> next = new LinkedHashMap<>();
        for (NpcAlliance alliance : incoming) {
            if (alliance.id().isEmpty() || next.containsKey(alliance.id())
                    || next.size() >= NpcValidation.MAX_ALLIANCES) {
                continue;
            }
            boolean builtin = isBuiltinId(alliance.id());
            next.put(alliance.id(), new NpcAlliance(alliance.id(), sanitizeName(alliance.name()), builtin,
                    new ArrayList<>(alliance.memberMobTypes()), new ArrayList<>(alliance.memberPlayers()),
                    new ArrayList<>(alliance.enemies()), new ArrayList<>(alliance.mates()), alliance.matchAllPlayers(),
                    alliance.friendlyFire()));
        }
        alliances.clear();
        alliances.putAll(next);
        ensureBuiltins();
        scrubRelationships();
        setDirty();
        alliances.keySet().forEach(net.lunacore.luna_npc.api.LunaNpcEvents::fireAllianceChanged);
    }

    private void scrubRelationships() {
        for (NpcAlliance alliance : alliances.values()) {
            alliance.enemies().removeIf(id -> !alliances.containsKey(id));
            alliance.mates().removeIf(id -> !alliances.containsKey(id));
        }
    }

    private static boolean isBuiltinId(String id) {
        return BUILTIN_IDS.contains(id);
    }

    private static NpcAlliance mutableCopy(NpcAlliance alliance) {
        return new NpcAlliance(alliance.id(), alliance.name(), alliance.builtin(),
                new ArrayList<>(alliance.memberMobTypes()), new ArrayList<>(alliance.memberPlayers()),
                new ArrayList<>(alliance.enemies()), new ArrayList<>(alliance.mates()), alliance.matchAllPlayers(),
                alliance.friendlyFire());
    }

    // Resolves whether two members of the SAME faction may damage each other: the alliance-wide
    // rule wins when it forces ALLOW/BLOCK, otherwise it defers to the NPC's own toggle.
    public boolean friendlyFireAllowed(String allianceId, boolean npcSetting) {
        NpcAlliance alliance = alliances.get(allianceId);
        if (alliance == null) {
            return npcSetting;
        }
        return switch (alliance.friendlyFire()) {
            case NpcAlliance.FF_ALLOW -> true;
            case NpcAlliance.FF_BLOCK -> false;
            default -> npcSetting;
        };
    }

    @Override
    public void setDirty() {
        super.setDirty();
        changeCount++;
    }

    public int changeCount() {
        return changeCount;
    }

    @Override
    public NpcAlliance create(String name) {
        if (alliances.size() >= NpcValidation.MAX_ALLIANCES) {
            throw new IllegalStateException("Alliance limit of " + NpcValidation.MAX_ALLIANCES + " reached");
        }
        String id = freshId(name);
        NpcAlliance alliance = new NpcAlliance(id, sanitizeName(name), false, new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), false, NpcAlliance.FF_PER_NPC);
        alliances.put(id, alliance);
        setDirty();
        return alliance;
    }

    @Override
    public void rename(String allianceId, String name) {
        NpcAlliance alliance = alliances.get(allianceId);
        if (alliance != null) {
            alliances.put(allianceId, alliance.withName(sanitizeName(name)));
            setDirty();
        }
    }

    @Override
    public void delete(String allianceId) {
        if (isBuiltinId(allianceId) || !alliances.containsKey(allianceId)) {
            return;
        }
        alliances.remove(allianceId);
        scrubRelationships();
        setDirty();
    }

    @Override
    public void setMemberMobTypes(String allianceId, List<String> mobTypeIds) {
        NpcAlliance alliance = alliances.get(allianceId);
        if (alliance == null) {
            return;
        }
        // Membership is exclusive: a mob type given to this alliance leaves every other one.
        for (NpcAlliance other : alliances.values()) {
            if (other != alliance) {
                other.memberMobTypes().removeAll(mobTypeIds);
            }
        }
        alliance.memberMobTypes().clear();
        alliance.memberMobTypes().addAll(mobTypeIds);
        setDirty();
    }

    @Override
    public void setMemberPlayers(String allianceId, List<UUID> playerIds) {
        NpcAlliance alliance = alliances.get(allianceId);
        if (alliance == null) {
            return;
        }
        List<String> keys = playerIds.stream().map(UUID::toString).toList();
        for (NpcAlliance other : alliances.values()) {
            if (other != alliance) {
                other.memberPlayers().removeAll(keys);
            }
        }
        alliance.memberPlayers().clear();
        alliance.memberPlayers().addAll(keys);
        setDirty();
    }

    @Override
    public void assignPlayer(UUID playerId, String allianceId) {
        String key = playerId.toString();
        for (NpcAlliance alliance : alliances.values()) {
            alliance.memberPlayers().remove(key);
        }
        NpcAlliance target = alliances.get(allianceId);
        if (target != null && !PLAYERS.equals(allianceId)) {
            target.memberPlayers().add(key);
        }
        setDirty();
    }

    @Override
    public void setEnemies(String allianceId, List<String> enemyAllianceIds) {
        NpcAlliance alliance = alliances.get(allianceId);
        if (alliance != null) {
            alliance.enemies().clear();
            enemyAllianceIds.stream().filter(alliances::containsKey).forEach(alliance.enemies()::add);
            setDirty();
            net.lunacore.luna_npc.api.LunaNpcEvents.fireAllianceChanged(allianceId);
        }
    }

    @Override
    public void setMates(String allianceId, List<String> mateAllianceIds) {
        NpcAlliance alliance = alliances.get(allianceId);
        if (alliance != null) {
            alliance.mates().clear();
            mateAllianceIds.stream().filter(alliances::containsKey).forEach(alliance.mates()::add);
            setDirty();
            net.lunacore.luna_npc.api.LunaNpcEvents.fireAllianceChanged(allianceId);
        }
    }

    @Override
    public void setMatchAllPlayers(String allianceId, boolean matchAll) {
        NpcAlliance alliance = alliances.get(allianceId);
        if (alliance != null) {
            alliances.put(allianceId, new NpcAlliance(alliance.id(), alliance.name(), alliance.builtin(),
                    alliance.memberMobTypes(), alliance.memberPlayers(), alliance.enemies(), alliance.mates(),
                    matchAll, alliance.friendlyFire()));
            setDirty();
            net.lunacore.luna_npc.api.LunaNpcEvents.fireAllianceChanged(allianceId);
        }
    }

    @Override
    public void setFriendlyFire(String allianceId, String rule) {
        NpcAlliance alliance = alliances.get(allianceId);
        if (alliance != null) {
            alliances.put(allianceId, new NpcAlliance(alliance.id(), alliance.name(), alliance.builtin(),
                    alliance.memberMobTypes(), alliance.memberPlayers(), alliance.enemies(), alliance.mates(),
                    alliance.matchAllPlayers(), rule));
            setDirty();
            net.lunacore.luna_npc.api.LunaNpcEvents.fireAllianceChanged(allianceId);
        }
    }

    private String freshId(String name) {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        if (base.isEmpty()) {
            base = "alliance";
        }
        String id = base;
        int suffix = 2;
        while (alliances.containsKey(id)) {
            id = base + suffix++;
        }
        return id;
    }

    private static String sanitizeName(String name) {
        String trimmed = name == null ? "" : name.strip();
        if (trimmed.length() > NpcValidation.MAX_ALLIANCE_NAME_LENGTH) {
            trimmed = trimmed.substring(0, NpcValidation.MAX_ALLIANCE_NAME_LENGTH);
        }
        return trimmed.isEmpty() ? "Alliance" : trimmed;
    }
}
