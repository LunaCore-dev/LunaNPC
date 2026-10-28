package net.lunacore.luna_npc.item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.lunacore.luna_npc.LunaNpc;

public final class LunaNpcItems {

    // Creative-tab grouping. Ordinal order is the display order between families: all sticks, then the
    // quest book, then anything else.
    public enum Family { STICK, BOOK, OTHER }

    private static final List<Item> ORDERED = new ArrayList<>();
    private static final Map<Item, Family> FAMILIES = new IdentityHashMap<>();

    public static final Item CREATE_NPC = registerItem("create_npc");
    public static final Item EDIT_NPC = registerItem("edit_npc");
    public static final Item SPAWN_NPC = registerItem("spawn_npc");
    public static final Item EDIT_ALLIANCES = registerItem("edit_alliances");
    public static final Item ZONE_WAND = registerItem("zone_wand");
    public static final Item ROUTE_CREATOR = registerItem("route_creator");
    public static final Item QUEST_BOOK = registerItem("quest_book", 64, null, Family.BOOK);
    // Anyone may hold it, but only OPs can open it — the gate lives in LunaNpcClient / the payload handler.
    public static final Item MANAGE_PERMISSIONS = registerItem("manage_permissions");
    // Red name marks it as the destructive tool: clicking an NPC with it permanently wipes that
    // instance (respawn record and all) after a confirmation prompt.
    public static final Item REMOVE_NPC = registerItem("remove_npc", 1,
            Component.translatable("item.luna_npc.remove_npc").withStyle(ChatFormatting.RED));

    private LunaNpcItems() {
    }

    public static void initialize() {
    }

    // Every registered item in creative-tab order: sticks kept together as one family first, then the quest
    // book, then anything else. The sort is stable, so a newly registered stick auto-joins the stick group
    // in its registration position with no manual reordering.
    public static List<Item> creativeTabOrder() {
        List<Item> items = new ArrayList<>(ORDERED);
        items.sort(Comparator.comparingInt(item -> FAMILIES.getOrDefault(item, Family.OTHER).ordinal()));
        return items;
    }

    private static Item registerItem(String name) {
        return registerItem(name, 1, null, Family.STICK);
    }

    private static Item registerItem(String name, int maxStack) {
        return registerItem(name, maxStack, null, Family.STICK);
    }

    private static Item registerItem(String name, int maxStack, Component itemName) {
        return registerItem(name, maxStack, itemName, Family.STICK);
    }

    private static Item registerItem(String name, int maxStack, Component itemName, Family family) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, name));
        Item.Properties properties = new Item.Properties().setId(key).stacksTo(maxStack);
        if (itemName != null) {
            properties = properties.component(DataComponents.ITEM_NAME, itemName);
        }
        Item item = Registry.register(BuiltInRegistries.ITEM, key, new Item(properties));
        ORDERED.add(item);
        FAMILIES.put(item, family);
        return item;
    }
}
