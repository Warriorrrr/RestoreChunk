package net.earthmc.restorechunk.listener;

import net.earthmc.restorechunk.RestoreChunkPlugin;
import net.earthmc.restorechunk.RestoredInventoryHolder;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collections;

public class InventoryListener implements Listener {

    private final RestoreChunkPlugin plugin;
    Sound clickSound = Sound.sound(Key.key(Key.MINECRAFT_NAMESPACE, "block.stone_button.click_on"), Sound.Source.BLOCK, 1.0f, 1.0f);

    public InventoryListener(RestoreChunkPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || !(event.getClickedInventory().getHolder() instanceof RestoredInventoryHolder holder) || event.getCurrentItem() == null)
            return;

        event.setCancelled(true);

        Player player = (Player) event.getWhoClicked();
        player.playSound(clickSound);

        ItemStack clickedItem = event.getCurrentItem();
        ItemMeta meta = clickedItem.getItemMeta();

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (pdc.has(plugin.key, PersistentDataType.INTEGER)) {
            holder.handleClick(player, pdc.get(plugin.key, PersistentDataType.INTEGER));
            return;
        } else if (pdc.has(plugin.key, PersistentDataType.STRING) && pdc.get(plugin.key, PersistentDataType.STRING).equals("dump")) {
            for (Inventory inventory : holder.inventories) {
                for (ItemStack item : inventory.getContents()) {
                    if (item == null)
                        continue;

                    ItemMeta itemMeta = item.getItemMeta();
                    itemMeta.lore(Collections.emptyList());
                    item.setItemMeta(itemMeta);

                    player.getWorld().dropItem(player.getLocation(), item);
                }
            }
            player.closeInventory(InventoryCloseEvent.Reason.PLUGIN);
            return;
        }

        // Clear lore
        meta.lore(Collections.emptyList());
        ItemStack clone = clickedItem.clone();
        clone.setItemMeta(meta);

        if (!player.getInventory().addItem(clone).isEmpty())
            player.sendMessage(Component.text("Could not store the clicked item in your inventory.", NamedTextColor.RED));
    }
}
