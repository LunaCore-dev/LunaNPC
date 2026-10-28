package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.lunacore.luna_npc.npc.NpcValidation;

// An NPC's job: exactly one type (NONE = not interactable, the default), configured as up to 25
// weighted sub-type variants — each spawned NPC rolls one sub-type at spawn and keeps it for life,
// which is the randomization mechanism. Trade/farm weights must total exactly 100%; quest weights
// are relative. DIALOG is the exception: it holds one fixed dialog tree with no weighting and no roll
// (weighted randomness could dead-end a scripted story). All sub-type lists and the dialog are
// persisted regardless of jobType, so switching the type in the editor never destroys the other jobs'
// configuration; only the data matching jobType is live. Getting hit can lock the NPC's menu:
// blockOnAnyHit locks it for everyone, blockAttacker only for the player who landed the hit.
// offerAllQuests skips the quest sub-type roll and offers every quest across all quest sub-types. The
// three sound ids override the job's feedback sounds (empty = the built-in default). PATROL rolls one
// weighted route variant like the other lists; BODYGUARD, like DIALOG, holds one fixed settings block.
public record NpcJobSettings(NpcJobType jobType,
                             boolean blockOnAnyHit, int blockAllSeconds,
                             boolean blockAttacker, int blockAttackerSeconds,
                             List<NpcTradeSubType> tradeSubTypes,
                             List<NpcQuestSubType> questSubTypes,
                             List<NpcFarmSubType> farmSubTypes,
                             boolean offerAllQuests,
                             String tradeSuccessSound, String tradeFailSound,
                             String questCompleteSound, NpcDialog dialog,
                             List<NpcPatrolSubType> patrolSubTypes, NpcBodyguardSettings bodyguard) {

    public NpcJobSettings {
        tradeSubTypes = List.copyOf(tradeSubTypes);
        questSubTypes = List.copyOf(questSubTypes);
        farmSubTypes = List.copyOf(farmSubTypes);
        patrolSubTypes = List.copyOf(patrolSubTypes);
    }

    private static final int DEFAULT_BLOCK_SECONDS = 10;

    private static final StreamCodec<ByteBuf, List<NpcTradeSubType>> TRADE_SUB_TYPES =
            NpcTradeSubType.STREAM_CODEC.apply(ByteBufCodecs.list());
    private static final StreamCodec<ByteBuf, List<NpcQuestSubType>> QUEST_SUB_TYPES =
            NpcQuestSubType.STREAM_CODEC.apply(ByteBufCodecs.list());
    private static final StreamCodec<ByteBuf, List<NpcFarmSubType>> FARM_SUB_TYPES =
            NpcFarmSubType.STREAM_CODEC.apply(ByteBufCodecs.list());
    private static final StreamCodec<ByteBuf, List<NpcPatrolSubType>> PATROL_SUB_TYPES =
            NpcPatrolSubType.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final Codec<NpcJobSettings> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            NpcJobType.CODEC.optionalFieldOf("job_type", NpcJobType.NONE).forGetter(NpcJobSettings::jobType),
            Codec.BOOL.optionalFieldOf("block_any_hit", false).forGetter(NpcJobSettings::blockOnAnyHit),
            Codec.INT.optionalFieldOf("block_all_seconds", DEFAULT_BLOCK_SECONDS)
                    .forGetter(NpcJobSettings::blockAllSeconds),
            Codec.BOOL.optionalFieldOf("block_attacker", false).forGetter(NpcJobSettings::blockAttacker),
            Codec.INT.optionalFieldOf("block_attacker_seconds", DEFAULT_BLOCK_SECONDS)
                    .forGetter(NpcJobSettings::blockAttackerSeconds),
            NpcTradeSubType.CODEC.listOf().optionalFieldOf("trade_sub_types", List.of())
                    .forGetter(NpcJobSettings::tradeSubTypes),
            NpcQuestSubType.CODEC.listOf().optionalFieldOf("quest_sub_types", List.of())
                    .forGetter(NpcJobSettings::questSubTypes),
            NpcFarmSubType.CODEC.listOf().optionalFieldOf("farm_sub_types", List.of())
                    .forGetter(NpcJobSettings::farmSubTypes),
            Codec.BOOL.optionalFieldOf("offer_all_quests", false)
                    .forGetter(NpcJobSettings::offerAllQuests),
            Codec.STRING.optionalFieldOf("trade_success_sound", "")
                    .forGetter(NpcJobSettings::tradeSuccessSound),
            Codec.STRING.optionalFieldOf("trade_fail_sound", "")
                    .forGetter(NpcJobSettings::tradeFailSound),
            Codec.STRING.optionalFieldOf("quest_complete_sound", "")
                    .forGetter(NpcJobSettings::questCompleteSound),
            NpcDialog.CODEC.optionalFieldOf("dialog", NpcDialog.defaults())
                    .forGetter(NpcJobSettings::dialog),
            NpcPatrolSubType.CODEC.listOf().optionalFieldOf("patrol_sub_types", List.of())
                    .forGetter(NpcJobSettings::patrolSubTypes),
            NpcBodyguardSettings.CODEC.optionalFieldOf("bodyguard", NpcBodyguardSettings.defaults())
                    .forGetter(NpcJobSettings::bodyguard)
    ).apply(instance, NpcJobSettings::new));

    public static final StreamCodec<ByteBuf, NpcJobSettings> STREAM_CODEC = StreamCodec.of(
            (buffer, settings) -> {
                NpcJobType.STREAM_CODEC.encode(buffer, settings.jobType());
                ByteBufCodecs.BOOL.encode(buffer, settings.blockOnAnyHit());
                ByteBufCodecs.VAR_INT.encode(buffer, settings.blockAllSeconds());
                ByteBufCodecs.BOOL.encode(buffer, settings.blockAttacker());
                ByteBufCodecs.VAR_INT.encode(buffer, settings.blockAttackerSeconds());
                TRADE_SUB_TYPES.encode(buffer, settings.tradeSubTypes());
                QUEST_SUB_TYPES.encode(buffer, settings.questSubTypes());
                FARM_SUB_TYPES.encode(buffer, settings.farmSubTypes());
                ByteBufCodecs.BOOL.encode(buffer, settings.offerAllQuests());
                ByteBufCodecs.STRING_UTF8.encode(buffer, settings.tradeSuccessSound());
                ByteBufCodecs.STRING_UTF8.encode(buffer, settings.tradeFailSound());
                ByteBufCodecs.STRING_UTF8.encode(buffer, settings.questCompleteSound());
                NpcDialog.STREAM_CODEC.encode(buffer, settings.dialog());
                PATROL_SUB_TYPES.encode(buffer, settings.patrolSubTypes());
                NpcBodyguardSettings.STREAM_CODEC.encode(buffer, settings.bodyguard());
            },
            buffer -> new NpcJobSettings(
                    NpcJobType.STREAM_CODEC.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.VAR_INT.decode(buffer),
                    TRADE_SUB_TYPES.decode(buffer),
                    QUEST_SUB_TYPES.decode(buffer),
                    FARM_SUB_TYPES.decode(buffer),
                    ByteBufCodecs.BOOL.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    ByteBufCodecs.STRING_UTF8.decode(buffer),
                    NpcDialog.STREAM_CODEC.decode(buffer),
                    PATROL_SUB_TYPES.decode(buffer),
                    NpcBodyguardSettings.STREAM_CODEC.decode(buffer)));

    public static NpcJobSettings defaults() {
        return new NpcJobSettings(NpcJobType.NONE, false, DEFAULT_BLOCK_SECONDS,
                false, DEFAULT_BLOCK_SECONDS, List.of(), List.of(), List.of(), false, "", "", "",
                NpcDialog.defaults(), List.of(), NpcBodyguardSettings.defaults());
    }

    // Additive copy-methods for scripting/API use. The withXSubType methods also flip the job type
    // to the matching job, so a single call is enough to arm it.

    public NpcJobSettings withJobType(NpcJobType type) {
        return new NpcJobSettings(type, blockOnAnyHit, blockAllSeconds, blockAttacker,
                blockAttackerSeconds, tradeSubTypes, questSubTypes, farmSubTypes, offerAllQuests,
                tradeSuccessSound, tradeFailSound, questCompleteSound, dialog, patrolSubTypes, bodyguard);
    }

    // 0 seconds disables the block; a positive value enables it for that long.
    public NpcJobSettings withBlockOnAnyHit(int seconds) {
        return new NpcJobSettings(jobType, seconds > 0, seconds > 0 ? seconds : blockAllSeconds,
                blockAttacker, blockAttackerSeconds, tradeSubTypes, questSubTypes, farmSubTypes,
                offerAllQuests, tradeSuccessSound, tradeFailSound, questCompleteSound, dialog, patrolSubTypes, bodyguard);
    }

    public NpcJobSettings withBlockAttacker(int seconds) {
        return new NpcJobSettings(jobType, blockOnAnyHit, blockAllSeconds,
                seconds > 0, seconds > 0 ? seconds : blockAttackerSeconds,
                tradeSubTypes, questSubTypes, farmSubTypes, offerAllQuests,
                tradeSuccessSound, tradeFailSound, questCompleteSound, dialog, patrolSubTypes, bodyguard);
    }

    public NpcJobSettings withTradeSubType(NpcTradeSubType subType) {
        List<NpcTradeSubType> subTypes = new ArrayList<>(tradeSubTypes);
        subTypes.add(subType);
        return new NpcJobSettings(NpcJobType.TRADING, blockOnAnyHit, blockAllSeconds,
                blockAttacker, blockAttackerSeconds, subTypes, questSubTypes, farmSubTypes,
                offerAllQuests, tradeSuccessSound, tradeFailSound, questCompleteSound, dialog, patrolSubTypes, bodyguard);
    }

    public NpcJobSettings withQuestSubType(NpcQuestSubType subType) {
        List<NpcQuestSubType> subTypes = new ArrayList<>(questSubTypes);
        subTypes.add(subType);
        return new NpcJobSettings(NpcJobType.QUESTS, blockOnAnyHit, blockAllSeconds,
                blockAttacker, blockAttackerSeconds, tradeSubTypes, subTypes, farmSubTypes,
                offerAllQuests, tradeSuccessSound, tradeFailSound, questCompleteSound, dialog, patrolSubTypes, bodyguard);
    }

    public NpcJobSettings withFarmSubType(NpcFarmSubType subType) {
        List<NpcFarmSubType> subTypes = new ArrayList<>(farmSubTypes);
        subTypes.add(subType);
        return new NpcJobSettings(NpcJobType.FARMING, blockOnAnyHit, blockAllSeconds,
                blockAttacker, blockAttackerSeconds, tradeSubTypes, questSubTypes, subTypes,
                offerAllQuests, tradeSuccessSound, tradeFailSound, questCompleteSound, dialog, patrolSubTypes, bodyguard);
    }

    public NpcJobSettings withOfferAllQuests(boolean offerAll) {
        return new NpcJobSettings(jobType, blockOnAnyHit, blockAllSeconds, blockAttacker,
                blockAttackerSeconds, tradeSubTypes, questSubTypes, farmSubTypes, offerAll,
                tradeSuccessSound, tradeFailSound, questCompleteSound, dialog, patrolSubTypes, bodyguard);
    }

    public NpcJobSettings withTradeSuccessSound(String soundId) {
        return new NpcJobSettings(jobType, blockOnAnyHit, blockAllSeconds, blockAttacker,
                blockAttackerSeconds, tradeSubTypes, questSubTypes, farmSubTypes, offerAllQuests,
                soundId, tradeFailSound, questCompleteSound, dialog, patrolSubTypes, bodyguard);
    }

    public NpcJobSettings withTradeFailSound(String soundId) {
        return new NpcJobSettings(jobType, blockOnAnyHit, blockAllSeconds, blockAttacker,
                blockAttackerSeconds, tradeSubTypes, questSubTypes, farmSubTypes, offerAllQuests,
                tradeSuccessSound, soundId, questCompleteSound, dialog, patrolSubTypes, bodyguard);
    }

    public NpcJobSettings withQuestCompleteSound(String soundId) {
        return new NpcJobSettings(jobType, blockOnAnyHit, blockAllSeconds, blockAttacker,
                blockAttackerSeconds, tradeSubTypes, questSubTypes, farmSubTypes, offerAllQuests,
                tradeSuccessSound, tradeFailSound, soundId, dialog, patrolSubTypes, bodyguard);
    }

    // Sets the fixed dialog tree and flips the job type to DIALOG.
    public NpcJobSettings withDialog(NpcDialog newDialog) {
        return new NpcJobSettings(NpcJobType.DIALOG, blockOnAnyHit, blockAllSeconds, blockAttacker,
                blockAttackerSeconds, tradeSubTypes, questSubTypes, farmSubTypes, offerAllQuests,
                tradeSuccessSound, tradeFailSound, questCompleteSound, newDialog, patrolSubTypes, bodyguard);
    }

    public NpcJobSettings withPatrolSubType(NpcPatrolSubType subType) {
        List<NpcPatrolSubType> subTypes = new ArrayList<>(patrolSubTypes);
        subTypes.add(subType);
        return new NpcJobSettings(NpcJobType.PATROL, blockOnAnyHit, blockAllSeconds, blockAttacker,
                blockAttackerSeconds, tradeSubTypes, questSubTypes, farmSubTypes, offerAllQuests,
                tradeSuccessSound, tradeFailSound, questCompleteSound, dialog, subTypes, bodyguard);
    }

    // Sets the bodyguard settings and flips the job type to BODYGUARD.
    public NpcJobSettings withBodyguard(NpcBodyguardSettings settings) {
        return new NpcJobSettings(NpcJobType.BODYGUARD, blockOnAnyHit, blockAllSeconds, blockAttacker,
                blockAttackerSeconds, tradeSubTypes, questSubTypes, farmSubTypes, offerAllQuests,
                tradeSuccessSound, tradeFailSound, questCompleteSound, dialog, patrolSubTypes, settings);
    }

    // Static factories: a default settings block already armed with one job's sub-types.
    public static NpcJobSettings trading(NpcTradeSubType... subTypes) {
        NpcJobSettings settings = defaults().withJobType(NpcJobType.TRADING);
        for (NpcTradeSubType subType : subTypes) {
            settings = settings.withTradeSubType(subType);
        }
        return settings;
    }

    public static NpcJobSettings quests(NpcQuestSubType... subTypes) {
        NpcJobSettings settings = defaults().withJobType(NpcJobType.QUESTS);
        for (NpcQuestSubType subType : subTypes) {
            settings = settings.withQuestSubType(subType);
        }
        return settings;
    }

    public static NpcJobSettings farming(NpcFarmSubType subType) {
        return defaults().withFarmSubType(subType);
    }

    public static NpcJobSettings dialog(NpcDialog dialog) {
        return defaults().withDialog(dialog);
    }

    public static NpcJobSettings patrol(NpcPatrolSubType... subTypes) {
        NpcJobSettings settings = defaults().withJobType(NpcJobType.PATROL);
        for (NpcPatrolSubType subType : subTypes) {
            settings = settings.withPatrolSubType(subType);
        }
        return settings;
    }

    public static NpcJobSettings bodyguard(NpcBodyguardSettings settings) {
        return defaults().withBodyguard(settings);
    }

    // The weights of the sub-type list matching the live job type, in list order.
    public List<Integer> liveSubTypeWeights() {
        return switch (jobType) {
            case NONE, DIALOG, BODYGUARD -> List.of();
            case PATROL -> patrolSubTypes.stream().map(NpcPatrolSubType::weightPercent).toList();
            case TRADING -> tradeSubTypes.stream().map(NpcTradeSubType::weightPercent).toList();
            case QUESTS -> questSubTypes.stream().map(NpcQuestSubType::weightPercent).toList();
            case FARMING -> farmSubTypes.stream().map(NpcFarmSubType::weightPercent).toList();
        };
    }

    // The one shared weighted roll (cumulative pick, like the world spawner's); -1 when nothing
    // can roll. Every caller — spawn roll, definition-edit re-roll — goes through here.
    public static int rollSubTypeIndex(List<Integer> weights, RandomSource random) {
        int total = 0;
        for (int weight : weights) {
            total += weight;
        }
        if (total <= 0) {
            return -1;
        }
        int roll = random.nextInt(total);
        for (int index = 0; index < weights.size(); index++) {
            roll -= weights.get(index);
            if (roll < 0) {
                return index;
            }
        }
        return -1;
    }

    public boolean isValid() {
        if (jobType == null
                || blockAllSeconds < 0 || blockAllSeconds > NpcValidation.MAX_INTERACTION_BLOCK_SECONDS
                || blockAttackerSeconds < 0
                || blockAttackerSeconds > NpcValidation.MAX_INTERACTION_BLOCK_SECONDS) {
            return false;
        }
        if (!isValidSoundId(tradeSuccessSound) || !isValidSoundId(tradeFailSound)
                || !isValidSoundId(questCompleteSound)) {
            return false;
        }
        if (dialog == null || !dialog.isValid() || bodyguard == null || !bodyguard.isValid()) {
            return false;
        }
        if (tradeSubTypes.size() > NpcValidation.MAX_JOB_SUB_TYPES
                || questSubTypes.size() > NpcValidation.MAX_JOB_SUB_TYPES
                || farmSubTypes.size() > NpcValidation.MAX_JOB_SUB_TYPES
                || patrolSubTypes.size() > NpcValidation.MAX_JOB_SUB_TYPES) {
            return false;
        }
        int tradeWeightSum = 0;
        for (NpcTradeSubType subType : tradeSubTypes) {
            if (!subType.isValid()) {
                return false;
            }
            tradeWeightSum += subType.weightPercent();
        }
        for (NpcQuestSubType subType : questSubTypes) {
            if (!subType.isValid()) {
                return false;
            }
        }
        int farmWeightSum = 0;
        for (NpcFarmSubType subType : farmSubTypes) {
            if (!subType.isValid()) {
                return false;
            }
            farmWeightSum += subType.weightPercent();
        }
        int patrolWeightSum = 0;
        for (NpcPatrolSubType subType : patrolSubTypes) {
            if (!subType.isValid()) {
                return false;
            }
            patrolWeightSum += subType.weightPercent();
        }
        return (tradeSubTypes.isEmpty() || tradeWeightSum == NpcValidation.JOB_WEIGHT_TOTAL_PERCENT)
                && (farmSubTypes.isEmpty() || farmWeightSum == NpcValidation.JOB_WEIGHT_TOTAL_PERCENT)
                && (patrolSubTypes.isEmpty() || patrolWeightSum == NpcValidation.JOB_WEIGHT_TOTAL_PERCENT);
    }

    private static boolean isValidSoundId(String soundId) {
        return soundId != null && (soundId.isEmpty() || Identifier.tryParse(soundId) != null);
    }
}
