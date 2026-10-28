package net.lunacore.luna_npc.job;

import java.util.List;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.lunacore.luna_npc.LunaNpc;
import net.lunacore.luna_npc.api.NpcTrade;
import net.lunacore.luna_npc.api.NpcTradeSubType;
import net.lunacore.luna_npc.entity.NpcEntity;

// The mod's menu types. The trade menu opens through Fabric's ExtendedMenuType (the successor to the
// removed ExtendedScreenHandlerType), which carries the TradeMenuData snapshot to the client as the
// menu opens — the trades and stock a player sees are resolved server-side from the NPC's rolled
// sub-type, never sent up by the client.
public final class LunaNpcMenus {

    public static ExtendedMenuType<TradeMenu, TradeMenuData> TRADE;

    private LunaNpcMenus() {
    }

    public static void register() {
        TRADE = Registry.register(BuiltInRegistries.MENU,
                Identifier.fromNamespaceAndPath(LunaNpc.MOD_ID, "trade"),
                new ExtendedMenuType<>(TradeMenu::new, TradeMenuData.STREAM_CODEC));
    }

    public static void openTradeMenu(ServerPlayer player, NpcEntity entity) {
        openTradeMenu(player, entity, entity.rolledJobSubType());
    }

    public static void openTradeMenu(ServerPlayer player, NpcEntity entity, int subType) {
        List<NpcTradeSubType> subTypes = entity.jobSettings().tradeSubTypes();
        if (subType < 0 || subType >= subTypes.size()) {
            return;
        }
        List<NpcTrade> trades = subTypes.get(subType).trades();
        if (trades.isEmpty()) {
            return;
        }
        player.openMenu(new ExtendedMenuProvider<TradeMenuData>() {
            @Override
            public Component getDisplayName() {
                return entity.getDisplayName();
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player opener) {
                return new TradeMenu(containerId, inventory, entity, trades);
            }

            @Override
            public TradeMenuData getScreenOpeningData(ServerPlayer serverPlayer) {
                MinecraftServer server = serverPlayer.level().getServer();
                return new TradeMenuData(entity.getId(), trades,
                        TradeMenu.buildStockViews(server, entity, serverPlayer, trades));
            }
        });
    }
}
