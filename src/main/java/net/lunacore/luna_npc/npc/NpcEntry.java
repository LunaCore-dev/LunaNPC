package net.lunacore.luna_npc.npc;

import com.mojang.datafixers.util.Either;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.lunacore.luna_npc.api.NpcDefinition;
import net.lunacore.luna_npc.api.NpcBossBarSettings;
import net.lunacore.luna_npc.api.NpcCombatSettings;
import net.lunacore.luna_npc.api.NpcDropSettings;
import net.lunacore.luna_npc.api.NpcEditAccess;
import net.lunacore.luna_npc.api.NpcEquipmentSettings;
import net.lunacore.luna_npc.api.NpcJobSettings;
import net.lunacore.luna_npc.api.NpcModelSettings;
import net.lunacore.luna_npc.api.NpcMovementSettings;
import net.lunacore.luna_npc.api.NpcSpawnSettings;
import net.lunacore.luna_npc.api.NpcTraitsSettings;

final class NpcEntry implements NpcDefinition {

    // Legacy worlds stored one alliance under "alliance" as a plain string; new worlds store a list
    // under the same key. Reading either keeps old NPCs' factions on upgrade; writing always emits a
    // list.
    private static final Codec<List<String>> ALLIANCES_CODEC =
            Codec.either(Codec.STRING.listOf(), Codec.STRING).xmap(
                    either -> either.map(list -> list, List::of), Either::left);

    static final Codec<NpcEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("luna_id").forGetter(NpcEntry::lunaId),
            Codec.STRING.fieldOf("name").forGetter(NpcEntry::name),
            Codec.BOOL.fieldOf("show_name_tag").forGetter(NpcEntry::isNameTagShown),
            Codec.INT.fieldOf("health").forGetter(NpcEntry::health),
            NpcSoundSettings.CODEC.optionalFieldOf("sounds", NpcSoundSettings.defaults())
                    .forGetter(NpcSoundSettings::of),
            NpcModelSettings.CODEC.optionalFieldOf("model", NpcModelSettings.defaults())
                    .forGetter(NpcEntry::modelSettings),
            Codec.STRING.optionalFieldOf("tag_color", NpcDefaults.TAG_COLOR).forGetter(NpcEntry::tagColor),
            Codec.STRING.optionalFieldOf("category", NpcDefaults.CATEGORY).forGetter(NpcEntry::category),
            NpcEquipmentSettings.CODEC.optionalFieldOf("equipment", NpcEquipmentSettings.empty())
                    .forGetter(NpcEntry::equipment),
            NpcDropSettings.CODEC.optionalFieldOf("drop_settings", NpcDropSettings.defaults())
                    .forGetter(NpcEntry::dropSettings),
            NpcMovementSettings.CODEC.optionalFieldOf("movement", NpcMovementSettings.defaults())
                    .forGetter(NpcEntry::movement),
            NpcBossBarSettings.CODEC.optionalFieldOf("boss_bar", NpcBossBarSettings.defaults())
                    .forGetter(NpcEntry::bossBar),
            NpcTraitsSettings.CODEC.optionalFieldOf("traits", NpcTraitsSettings.defaults())
                    .forGetter(NpcEntry::traits),
            NpcCombatSettings.CODEC.optionalFieldOf("combat", NpcCombatSettings.defaults())
                    .forGetter(NpcEntry::combat),
            ALLIANCES_CODEC.optionalFieldOf("alliance", List.of(NpcDefaults.ALLIANCE)).forGetter(NpcEntry::allianceIds),
            // RecordCodecBuilder groups cap at 16 entries, so spawn and jobs share one slot as a flat
            // Pair. They still serialize as the separate "spawn"/"jobs" fields.
            // NPCs saved before edit access existed behave as before: SHARED.
            Codec.mapPair(Codec.mapPair(
                    NpcSpawnSettings.CODEC.optionalFieldOf("spawn", NpcSpawnSettings.defaults()),
                    NpcJobSettings.CODEC.optionalFieldOf("jobs", NpcJobSettings.defaults())),
                    NpcEditAccess.CODEC.optionalFieldOf("edit_access", NpcEditAccess.SHARED))
                    .forGetter(entry -> Pair.of(Pair.of(entry.spawn(), entry.jobs()), entry.editAccess()))
    ).apply(instance, (lunaId, name, nameTagShown, health, sounds, model, tagColor, category,
                       equipment, dropSettings, movement, bossBar, traits, combat, allianceIds,
                       rest) -> {
        NpcEntry entry = new NpcEntry(lunaId, name, nameTagShown, health, sounds, model, tagColor, category,
                equipment, dropSettings, movement, bossBar, traits, combat, allianceIds,
                rest.getFirst().getFirst(), rest.getFirst().getSecond());
        entry.initAccess(rest.getSecond());
        return entry;
    }));

    private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("LunaNPC");

    private final long lunaId;
    private String name;
    private boolean nameTagShown;
    private int health;
    private String hurtSoundId;
    private String dieSoundId;
    private String ambientSoundId;
    private String aggroSoundId;
    private String combatCloseSoundId;
    private String combatFarSoundId;
    private String closeImpactSoundId;
    private String farImpactSoundId;
    private int ambientIntervalSeconds;
    private NpcModelSettings modelSettings;
    private String tagColor;
    private String category;
    private NpcEquipmentSettings equipment;
    private NpcDropSettings dropSettings;
    private NpcMovementSettings movement;
    private NpcBossBarSettings bossBar;
    private NpcTraitsSettings traits;
    private NpcCombatSettings combat;
    private List<String> allianceIds;
    private NpcSpawnSettings spawn;
    private NpcJobSettings jobs;
    private NpcManager owner;
    private NpcEditAccess editAccess = NpcEditAccess.SHARED;
    // True while NpcManager.editInGame runs an editor change on this NPC.
    boolean inGameEdit;

    NpcEntry(long lunaId, String name) {
        this(lunaId, name, NpcDefaults.NAME_TAG_SHOWN, NpcDefaults.HEALTH,
                NpcSoundSettings.defaults(), NpcModelSettings.defaults(),
                NpcDefaults.TAG_COLOR, NpcDefaults.CATEGORY,
                NpcEquipmentSettings.empty(), NpcDropSettings.defaults(),
                NpcMovementSettings.defaults(), NpcBossBarSettings.defaults(),
                NpcTraitsSettings.defaults(), NpcCombatSettings.defaults(), List.of(NpcDefaults.ALLIANCE),
                NpcSpawnSettings.defaults(), NpcJobSettings.defaults());
    }

    NpcEntry(long lunaId, String name, boolean nameTagShown, int health,
             NpcSoundSettings sounds, NpcModelSettings model, String tagColor, String category,
             NpcEquipmentSettings equipment, NpcDropSettings dropSettings,
             NpcMovementSettings movement, NpcBossBarSettings bossBar, NpcTraitsSettings traits,
             NpcCombatSettings combat, List<String> allianceIds, NpcSpawnSettings spawn,
             NpcJobSettings jobs) {
        this.lunaId = lunaId;
        this.name = name;
        this.nameTagShown = nameTagShown;
        this.health = health;
        this.hurtSoundId = sounds.hurtSoundId();
        this.dieSoundId = sounds.dieSoundId();
        this.ambientSoundId = sounds.ambientSoundId();
        this.aggroSoundId = sounds.aggroSoundId();
        this.combatCloseSoundId = sounds.combatCloseSoundId();
        this.combatFarSoundId = sounds.combatFarSoundId();
        this.closeImpactSoundId = sounds.closeImpactSoundId();
        this.farImpactSoundId = sounds.farImpactSoundId();
        this.ambientIntervalSeconds = sounds.ambientIntervalSeconds();
        this.modelSettings = model;
        this.tagColor = tagColor;
        this.category = category;
        this.equipment = equipment;
        this.dropSettings = dropSettings;
        this.movement = movement;
        this.bossBar = bossBar;
        this.traits = traits;
        this.combat = combat;
        this.allianceIds = normalizeAlliances(allianceIds);
        this.spawn = spawn;
        this.jobs = jobs;
    }

    @Override
    public String tagColor() {
        return tagColor;
    }

    @Override
    public void setTagColor(String tagColor) {
        checkEditable();
        if (!NpcValidation.isValidTagColor(tagColor)) {
            throw new IllegalArgumentException("Invalid NPC tag color: " + tagColor);
        }
        this.tagColor = tagColor;
        markDirty();
    }

    @Override
    public String category() {
        return category;
    }

    @Override
    public void setCategory(String category) {
        checkEditable();
        if (!NpcValidation.isValidCategory(category)) {
            throw new IllegalArgumentException("Invalid NPC category: " + category);
        }
        if (!category.isEmpty() && owner != null && !owner.canUseCategory(category)) {
            throw new IllegalArgumentException("Category limit of " + NpcValidation.MAX_CATEGORIES + " reached");
        }
        this.category = category;
        markDirty();
    }

    void attachTo(NpcManager owner) {
        this.owner = owner;
    }

    void initAccess(NpcEditAccess access) {
        this.editAccess = access;
    }

    // Throws when the side making this change (the in-game editor or the API) may not edit this NPC.
    void checkEditable() {
        if (inGameEdit ? !editAccess.guiCanEdit() : !editAccess.apiCanEdit()) {
            throw new IllegalStateException("NPC '" + name + "' (LID " + lunaId + ") is "
                    + editAccess.getSerializedName() + ": " + (inGameEdit ? "the in-game editor" : "the API")
                    + " can read it but not change it");
        }
    }

    @Override
    public NpcEditAccess editAccess() {
        return editAccess;
    }

    @Override
    public void setEditAccess(NpcEditAccess access) {
        checkEditable();
        if (access == null) {
            throw new IllegalArgumentException("NPC edit access is required");
        }
        this.editAccess = access;
        markDirty();
    }

    @Override
    public long lunaId() {
        return lunaId;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void setName(String name) {
        checkEditable();
        if (!NpcValidation.isValidName(name)) {
            throw new IllegalArgumentException("Invalid NPC name: " + name);
        }
        this.name = name;
        markDirty();
    }

    @Override
    public boolean isNameTagShown() {
        return nameTagShown;
    }

    @Override
    public void setNameTagShown(boolean shown) {
        checkEditable();
        this.nameTagShown = shown;
        markDirty();
    }

    @Override
    public int health() {
        return health;
    }

    @Override
    public void setHealth(int health) {
        checkEditable();
        if (!NpcValidation.isValidHealth(health)) {
            throw new IllegalArgumentException("NPC health must be between " + NpcValidation.MIN_HEALTH
                    + " and " + NpcValidation.MAX_HEALTH + ", got: " + health);
        }
        this.health = health;
        markDirty();
    }

    @Override
    public String hurtSoundId() {
        return hurtSoundId;
    }

    @Override
    public void setHurtSoundId(String soundId) {
        checkEditable();
        this.hurtSoundId = requireSoundId(soundId);
        markDirty();
    }

    @Override
    public String dieSoundId() {
        return dieSoundId;
    }

    @Override
    public void setDieSoundId(String soundId) {
        checkEditable();
        this.dieSoundId = requireSoundId(soundId);
        markDirty();
    }

    @Override
    public String ambientSoundId() {
        return ambientSoundId;
    }

    @Override
    public void setAmbientSoundId(String soundId) {
        checkEditable();
        this.ambientSoundId = soundId == null ? "" : soundId;
        markDirty();
    }

    @Override
    public String aggroSoundId() {
        return aggroSoundId;
    }

    @Override
    public void setAggroSoundId(String soundId) {
        checkEditable();
        this.aggroSoundId = soundId == null ? "" : soundId;
        markDirty();
    }

    @Override
    public String combatCloseSoundId() {
        return combatCloseSoundId;
    }

    @Override
    public void setCombatCloseSoundId(String soundId) {
        checkEditable();
        this.combatCloseSoundId = soundId == null ? "" : soundId;
        markDirty();
    }

    @Override
    public String combatFarSoundId() {
        return combatFarSoundId;
    }

    @Override
    public void setCombatFarSoundId(String soundId) {
        checkEditable();
        this.combatFarSoundId = soundId == null ? "" : soundId;
        markDirty();
    }

    @Override
    public String closeImpactSoundId() {
        return closeImpactSoundId;
    }

    @Override
    public void setCloseImpactSoundId(String soundId) {
        checkEditable();
        this.closeImpactSoundId = soundId == null ? "" : soundId;
        markDirty();
    }

    @Override
    public String farImpactSoundId() {
        return farImpactSoundId;
    }

    @Override
    public void setFarImpactSoundId(String soundId) {
        checkEditable();
        this.farImpactSoundId = soundId == null ? "" : soundId;
        markDirty();
    }

    @Override
    public int ambientIntervalSeconds() {
        return ambientIntervalSeconds;
    }

    @Override
    public void setAmbientIntervalSeconds(int seconds) {
        checkEditable();
        if (!NpcValidation.isValidAmbientInterval(seconds)) {
            throw new IllegalArgumentException("Ambient interval must be between "
                    + NpcValidation.MIN_AMBIENT_INTERVAL_SECONDS + " and "
                    + NpcValidation.MAX_AMBIENT_INTERVAL_SECONDS + " seconds, got: " + seconds);
        }
        this.ambientIntervalSeconds = seconds;
        markDirty();
    }

    @Override
    public NpcModelSettings modelSettings() {
        return modelSettings;
    }

    @Override
    public void setModelSettings(NpcModelSettings settings) {
        checkEditable();
        if (settings == null || !settings.isValid()) {
            throw new IllegalArgumentException("Invalid NPC model settings: " + settings);
        }
        this.modelSettings = settings;
        markDirty();
    }

    @Override
    public NpcEquipmentSettings equipment() {
        return equipment;
    }

    @Override
    public void setEquipment(NpcEquipmentSettings equipment) {
        checkEditable();
        if (equipment == null) {
            throw new IllegalArgumentException("Equipment must not be null");
        }
        this.equipment = equipment;
        markDirty();
    }

    @Override
    public NpcDropSettings dropSettings() {
        return dropSettings;
    }

    @Override
    public void setDropSettings(NpcDropSettings dropSettings) {
        checkEditable();
        if (dropSettings == null || !dropSettings.isValid()) {
            throw new IllegalArgumentException("Invalid NPC drop settings: " + dropSettings);
        }
        this.dropSettings = dropSettings;
        markDirty();
    }

    @Override
    public NpcMovementSettings movement() {
        return movement;
    }

    @Override
    public void setMovement(NpcMovementSettings movement) {
        checkEditable();
        if (movement == null || !movement.isValid()) {
            throw new IllegalArgumentException("Invalid NPC movement settings: " + movement);
        }
        this.movement = movement;
        markDirty();
    }

    @Override
    public NpcBossBarSettings bossBar() {
        return bossBar;
    }

    @Override
    public void setBossBar(NpcBossBarSettings bossBar) {
        checkEditable();
        if (bossBar == null || !bossBar.isValid()) {
            throw new IllegalArgumentException("Invalid NPC boss bar settings: " + bossBar);
        }
        this.bossBar = bossBar;
        markDirty();
    }

    @Override
    public NpcTraitsSettings traits() {
        return traits;
    }

    @Override
    public void setTraits(NpcTraitsSettings traits) {
        checkEditable();
        if (traits == null || !traits.isValid()) {
            throw new IllegalArgumentException("Invalid NPC traits settings: " + traits);
        }
        this.traits = traits;
        markDirty();
    }

    @Override
    public NpcCombatSettings combat() {
        return combat;
    }

    @Override
    public void setCombat(NpcCombatSettings combat) {
        checkEditable();
        if (combat == null || !combat.isValid()) {
            throw new IllegalArgumentException("Invalid NPC combat settings: " + combat);
        }
        this.combat = combat;
        markDirty();
    }

    @Override
    public List<String> allianceIds() {
        return List.copyOf(allianceIds);
    }

    @Override
    public void setAllianceIds(List<String> allianceIds) {
        checkEditable();
        this.allianceIds = normalizeAlliances(allianceIds);
        markDirty();
    }

    // Drops blanks/duplicates and falls back to the default faction when nothing valid remains, so an
    // NPC always has at least one home alliance.
    private static List<String> normalizeAlliances(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of(NpcDefaults.ALLIANCE);
        }
        List<String> out = new ArrayList<>();
        for (String id : ids) {
            if (id != null && !id.isBlank() && !out.contains(id)) {
                out.add(id);
            }
        }
        return out.isEmpty() ? List.of(NpcDefaults.ALLIANCE) : List.copyOf(out);
    }

    @Override
    public NpcSpawnSettings spawn() {
        return spawn;
    }

    @Override
    public void setSpawn(NpcSpawnSettings spawn) {
        checkEditable();
        if (spawn == null || !spawn.isValid()) {
            throw new IllegalArgumentException("Invalid NPC spawn settings: " + spawn);
        }
        boolean wasLoop = this.spawn != null && this.spawn.naturalSpawnEnabled() && this.spawn.respawnEnabled();
        this.spawn = spawn;
        // Natural spawning + respawn is an unbounded-population combo (each explored area keeps
        // respawning its NPCs). It stays allowed, but a headless API user has no UI warning to see, so
        // log once when it is switched on — only on the transition, so it never spams.
        if (spawn.naturalSpawnEnabled() && spawn.respawnEnabled() && !wasLoop) {
            LOGGER.warn("NPC '{}' (lunaId {}) has natural spawning AND respawn enabled — its population "
                    + "can climb without limit as players explore. Cap it with Max nearby or a zone max count.",
                    name(), lunaId());
        }
        markDirty();
    }

    @Override
    public NpcJobSettings jobs() {
        return jobs;
    }

    @Override
    public void setJobs(NpcJobSettings jobs) {
        checkEditable();
        if (jobs == null || !jobs.isValid()) {
            throw new IllegalArgumentException("Invalid NPC job settings: " + jobs);
        }
        this.jobs = jobs;
        markDirty();
    }

    private static String requireSoundId(String soundId) {
        if (soundId == null || soundId.isBlank()) {
            throw new IllegalArgumentException("Sound id must not be empty");
        }
        return soundId;
    }

    private void markDirty() {
        if (owner != null) {
            owner.setDirty();
        }
    }
}
