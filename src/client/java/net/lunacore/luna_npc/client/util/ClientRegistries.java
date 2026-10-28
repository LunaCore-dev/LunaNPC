package net.lunacore.luna_npc.client.util;

import net.minecraft.client.Minecraft;
import net.minecraft.core.HolderLookup;

public final class ClientRegistries {

    private ClientRegistries() {
    }

    // Null while no world/connection is up; NpcEquipment.resolve treats that as "no enchants".
    public static HolderLookup.Provider lookup() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() != null) {
            return minecraft.getConnection().registryAccess();
        }
        return minecraft.level == null ? null : minecraft.level.registryAccess();
    }
}
