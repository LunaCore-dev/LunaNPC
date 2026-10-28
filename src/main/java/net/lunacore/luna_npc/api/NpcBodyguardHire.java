package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.npc.NpcValidation;

// The offer of a HIRE bodyguard: the item price (empty = free) and how long one hire lasts (0 = until it
// gives up). hireSeconds is also the default time for dialog, command and API hires.
public record NpcBodyguardHire(List<NpcTradeStack> cost, int hireSeconds) {

    public NpcBodyguardHire {
        cost = List.copyOf(cost);
    }

    public static final Codec<NpcBodyguardHire> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            NpcTradeStack.CODEC.listOf().optionalFieldOf("cost", List.of()).forGetter(NpcBodyguardHire::cost),
            Codec.INT.optionalFieldOf("hire_seconds", 0).forGetter(NpcBodyguardHire::hireSeconds)
    ).apply(instance, NpcBodyguardHire::new));

    public static final StreamCodec<ByteBuf, NpcBodyguardHire> STREAM_CODEC = StreamCodec.composite(
            NpcTradeStack.STREAM_CODEC.apply(ByteBufCodecs.list()), NpcBodyguardHire::cost,
            ByteBufCodecs.VAR_INT, NpcBodyguardHire::hireSeconds,
            NpcBodyguardHire::new);

    public static NpcBodyguardHire defaults() {
        return new NpcBodyguardHire(List.of(), 0);
    }

    public boolean isValid() {
        if (hireSeconds < 0 || hireSeconds > NpcValidation.MAX_BODYGUARD_HIRE_SECONDS
                || cost.size() > NpcValidation.MAX_TRADE_SLOTS) {
            return false;
        }
        return cost.stream().allMatch(NpcTradeStack::isValid);
    }
}
