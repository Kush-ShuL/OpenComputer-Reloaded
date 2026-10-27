package li.cil.oc.client;

import java.util.List;
import li.cil.oc.common.init.Blocks;
import li.cil.oc.common.recipe.Recipes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.CreativeModeTabSearchRegistry;

/** Event-driven refresh of the two creative caches affected by world program disks. */
@OnlyIn(Dist.CLIENT)
public final class LootClientLifecycle {
    private static CreativeModeTab.ItemDisplayParameters lastParameters;
    private LootClientLifecycle() {}

    /** Called by the client payload's enqueueWork, after its complete disk snapshot has arrived. */
    public static void receive(List<ItemStack> disks, Player expectedPlayer) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(() -> receive(disks, expectedPlayer));
            return;
        }
        // Ignore a queued packet belonging to a connection which has already closed or changed.
        if (minecraft.player == null || minecraft.player != expectedPlayer || minecraft.getConnection() == null) return;
        Recipes.setClientWorldLootDisks(disks);
        li.cil.oc.client.integration.jei.JeiIntegration.refreshLoot();
        lastParameters = parameters(minecraft, minecraft.player);
        refresh(minecraft, lastParameters, minecraft.getConnection(), true);
    }

    public static void logout(LocalPlayer departingPlayer) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!minecraft.isSameThread()) {
            minecraft.execute(() -> logout(departingPlayer));
            return;
        }
        Recipes.setClientWorldLootDisks(List.of());
        li.cil.oc.client.integration.jei.JeiIntegration.refreshLoot();
        CreativeModeTab.ItemDisplayParameters parameters = departingPlayer == null
                ? lastParameters : parameters(minecraft, departingPlayer);
        if (parameters != null) refresh(minecraft, parameters,
                departingPlayer == null ? null : departingPlayer.connection, false);
        lastParameters = null;
    }

    private static CreativeModeTab.ItemDisplayParameters parameters(Minecraft minecraft, LocalPlayer player) {
        return new CreativeModeTab.ItemDisplayParameters(player.level().enabledFeatures(),
                player.canUseGameMasterBlocks() && minecraft.options.operatorItemsTab().get(), player.level().registryAccess());
    }

    private static void refresh(Minecraft minecraft, CreativeModeTab.ItemDisplayParameters parameters,
                                ClientPacketListener connection, boolean refreshOpenScreen) {
        CreativeModeTab oc = Blocks.TAB.get();
        CreativeModeTab search = CreativeModeTabs.searchTab();
        // The vanilla aggregate search tab derives its contents from category tabs, so build OC first.
        oc.buildContents(parameters);
        search.buildContents(parameters);
        if (connection != null) {
            updateSearch(connection, parameters, oc);
            updateSearch(connection, parameters, search);
        }
        if (refreshOpenScreen && minecraft.screen instanceof CreativeModeInventoryScreen screen
                && minecraft.player != null && minecraft.gameMode != null && minecraft.gameMode.hasInfiniteItems()) {
            // Public resize keeps the selected tab, search text and scroll position, and rebinds its menu items.
            screen.resize(minecraft, screen.width, screen.height);
        }
    }

    private static void updateSearch(ClientPacketListener connection, CreativeModeTab.ItemDisplayParameters parameters,
                                     CreativeModeTab tab) {
        if (!tab.hasSearchBar()) return;
        List<ItemStack> items = List.copyOf(tab.getDisplayItems());
        connection.searchTrees().updateCreativeTooltips(parameters.holders(), items,
                CreativeModeTabSearchRegistry.getNameSearchKey(tab));
        connection.searchTrees().updateCreativeTags(items, CreativeModeTabSearchRegistry.getTagSearchKey(tab));
    }
}
