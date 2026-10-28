package net.lunacore.luna_npc.api;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.lunacore.luna_npc.npc.NpcValidation;

// One item stack in a trade slot or quest reward slot: an item id, its amount (1-64) and the
// enchantments baked onto it.
public record NpcTradeStack(String itemId, int count, List<NpcItemEnchant> enchants) {

    public NpcTradeStack {
        enchants = List.copyOf(enchants);
    }

    public NpcTradeStack(String itemId, int count) {
        this(itemId, count, List.of());
    }

    public static final Codec<NpcTradeStack> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("item").forGetter(NpcTradeStack::itemId),
            Codec.INT.optionalFieldOf("count", 1).forGetter(NpcTradeStack::count),
            NpcItemEnchant.CODEC.listOf().optionalFieldOf("enchants", List.of())
                    .forGetter(NpcTradeStack::enchants)
    ).apply(instance, NpcTradeStack::new));

    public static final StreamCodec<ByteBuf, NpcTradeStack> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, NpcTradeStack::itemId,
            ByteBufCodecs.VAR_INT, NpcTradeStack::count,
            NpcItemEnchant.STREAM_CODEC.apply(ByteBufCodecs.list()), NpcTradeStack::enchants,
            NpcTradeStack::new);

    public boolean isValid() {
        if (itemId == null || itemId.isBlank()
                || count < 1 || count > NpcValidation.MAX_TRADE_SLOT_COUNT) {
            return false;
        }
        for (NpcItemEnchant enchant : enchants) {
            if (!enchant.isValid()) {
                return false;
            }
        }
        return true;
    }
}
