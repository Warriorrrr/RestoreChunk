package net.earthmc.restorechunk.object;

import net.earthmc.restorechunk.RestoreChunkPlugin;
import net.kyori.adventure.text.Component;
import net.minecraft.util.Mth;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;

public class RestoredInventoryHolder implements InventoryHolder {

    public final List<Inventory> inventories = new ArrayList<>();
    int currentPage = 0;

    public RestoredInventoryHolder(Player player, List<Inventory> pages, RestoreChunkPlugin plugin) {
        for (Inventory page : pages) {
            Inventory newPage = Bukkit.createInventory(this, 54, Component.text("Restored Items"));
            newPage.setContents(page.getContents());
            inventories.add(newPage);
        }

        Bukkit.getScheduler().runTask(plugin, () -> player.openInventory(getInventory()));
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventories.get(currentPage);
    }

    public int getCurrentPage() {
        return currentPage;
    }

    public int getPageCount() {
        return inventories.size();
    }

    public void handleClick(Player player, int amount) {
        int current = getCurrentPage();
        int newPage = Mth.clamp(currentPage + amount, 0, getPageCount() - 1);

        if (current != newPage) {
            currentPage = newPage;
            player.openInventory(getInventory());
        }
    }
}
