package net.lunacore.luna_npc.item;

import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.lunacore.luna_npc.LunaNpc;

// Cosmetic animated-flame items. Fire has no vanilla item form, so these exist only to be pickable
// as a projectile look (the picker lists every registered item). They stay out of the creative tab,
// so they are projectile-only rather than obtainable.
public final class LunaNpcProjectileItems {

    public static final Item FIRE = register("fire");
    public static final Item BLUE_FIRE = register("blue_fire");
    public static final Item GREEN_FIRE = register("green_fire");
    public static final Item PURPLE_FIRE = register("purple_fire");
    public static final Item RED_FIRE = register("red_fire");
    public static final Item WHITE_FIRE = register("white_fire");
    public static final Item CYAN_FIRE = register("cyan_fire");
    public static final Item PINK_FIRE = register("pink_fire");

    private LunaNpcProjectileItems() {
    }

    public static void initialize() {
    }

    private static Item register(String name) {
        ResourceKey<Item> key = ResourceKey.create(Registries.ITEM,
                Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, name));
        return Registry.register(BuiltInRegistries.ITEM, key, new Item(new Item.Properties().setId(key)));
    }
}
