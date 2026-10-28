package net.lunacore.luna_npc.item;

import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.lunacore.luna_npc.LunaNpc;

public final class LunaNpcCreativeTab {

    private LunaNpcCreativeTab() {
    }

    public static void register() {
        ResourceKey<CreativeModeTab> key = ResourceKey.create(Registries.CREATIVE_MODE_TAB,
                Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "luna_npc"));
        Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, key, FabricCreativeModeTab.builder()
                .title(Component.translatable("itemGroup.luna_npc"))
                .icon(() -> new ItemStack(LunaNpcItems.CREATE_NPC))
                .displayItems((parameters, output) ->
                        LunaNpcItems.creativeTabOrder().forEach(output::accept))
                .build());
    }
}
