package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

// One audit-log record: who did what, when. actorName is captured at event time so the log stays readable
// after a rename. targetName/targetId describe the affected thing (an NPC's name and luna id, a granted
// stick, ...); targetId is 0 when the action has no numeric subject. The action's text is formatted on the
// client from a translation key, keeping the log translatable.
public record PermissionLogEntry(long timestampMillis, UUID actor, String actorName,
                                 PermissionLogAction action, String targetName, long targetId) {

    public static final Codec<PermissionLogEntry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.LONG.fieldOf("time").forGetter(PermissionLogEntry::timestampMillis),
            UUIDUtil.CODEC.fieldOf("actor").forGetter(PermissionLogEntry::actor),
            Codec.STRING.fieldOf("actor_name").forGetter(PermissionLogEntry::actorName),
            PermissionLogAction.CODEC.fieldOf("action").forGetter(PermissionLogEntry::action),
            Codec.STRING.optionalFieldOf("target_name", "").forGetter(PermissionLogEntry::targetName),
            Codec.LONG.optionalFieldOf("target_id", 0L).forGetter(PermissionLogEntry::targetId)
    ).apply(instance, PermissionLogEntry::new));

    public static final StreamCodec<ByteBuf, PermissionLogEntry> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_LONG, PermissionLogEntry::timestampMillis,
            UUIDUtil.STREAM_CODEC, PermissionLogEntry::actor,
            ByteBufCodecs.STRING_UTF8, PermissionLogEntry::actorName,
            PermissionLogAction.STREAM_CODEC, PermissionLogEntry::action,
            ByteBufCodecs.STRING_UTF8, PermissionLogEntry::targetName,
            ByteBufCodecs.VAR_LONG, PermissionLogEntry::targetId,
            PermissionLogEntry::new);
}
