package net.lunacore.luna_npc;

import net.fabricmc.api.ModInitializer;
import net.lunacore.luna_npc.combat.ImpactEffects;
import net.lunacore.luna_npc.command.LunaNpcCommand;
import net.lunacore.luna_npc.entity.LunaNpcEntities;
import net.lunacore.luna_npc.item.LunaNpcCreativeTab;
import net.lunacore.luna_npc.item.LunaNpcItems;
import net.lunacore.luna_npc.item.LunaNpcProjectileItems;
import net.lunacore.luna_npc.job.DialogEvents;
import net.lunacore.luna_npc.job.LunaNpcMenus;
import net.lunacore.luna_npc.job.QuestEvents;
import net.lunacore.luna_npc.network.LunaNpcNetworking;
import net.lunacore.luna_npc.permission.PermissionSync;
import net.lunacore.luna_npc.spawn.NpcSpawner;

public class LunaNpc implements ModInitializer {

    public static final String MOD_ID = "luna_npc";

    @Override
    public void onInitialize() {
        LunaNpcConfig.load();
        LunaNpcItems.initialize();
        net.lunacore.luna_npc.chunk.NpcChunkLoader.initialize();
        LunaNpcProjectileItems.initialize();
        LunaNpcCreativeTab.register();
        LunaNpcEntities.initialize();
        LunaNpcMenus.register();
        LunaNpcNetworking.register();
        QuestEvents.register();
        DialogEvents.register();
        net.lunacore.luna_npc.job.BodyguardEvents.register();
        LunaNpcCommand.register();
        ImpactEffects.register();
        NpcSpawner.register();
        PermissionSync.register();
        LunaNpcPersistence.register();
    }
}
