package net.earthmc.restorechunk;

import net.earthmc.restorechunk.command.RestoreChunkCommand;
import net.earthmc.restorechunk.command.RestoreEntitiesCommand.ItemEntry;
import net.earthmc.restorechunk.listener.InventoryListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class RestoreChunkPlugin extends JavaPlugin {

    public final NamespacedKey key = new NamespacedKey(this, "button");
    public IOWorker entityWorker = null;
    public IOWorker chunkWorker = null;

    @Override
    public void onEnable() {
        getDataFolder().mkdir();

        Constructor<?> ioWorkerConstructor;
        try {
            ioWorkerConstructor = IOWorker.class.getDeclaredConstructor(Path.class, boolean.class, String.class);
            ioWorkerConstructor.setAccessible(true);
        } catch (NoSuchMethodException e) {
            getLogger().severe("Could not find the IOWorker constructor, shutting down...");
            e.printStackTrace();
            this.setEnabled(false);
            return;
        }

        try {
            this.entityWorker = (IOWorker) ioWorkerConstructor.newInstance(getDataFolder().toPath().resolve("entity"), false, "entityrestore");
        } catch (Exception e) {
            getLogger().warning("An unknown exception occurred when initializing the entity IO worker.");
            e.printStackTrace();
            this.setEnabled(false);
            return;
        }

        try {
            this.chunkWorker = (IOWorker) ioWorkerConstructor.newInstance(getDataFolder().toPath().resolve("region"), false, "chunkrestore");
        } catch (Exception e) {
            getLogger().warning("An unknown exception occurred when initializing the chunk IO worker.");
            e.printStackTrace();
            this.setEnabled(false);
            return;
        }

        //getCommand("restoreentities").setExecutor(new RestoreEntitiesCommand(this));
        getCommand("restorechunk").setExecutor(new RestoreChunkCommand(this));
        Bukkit.getPluginManager().registerEvents(new InventoryListener(this), this);
    }

    @Override
    public void onDisable() {
        if (entityWorker != null) {
            try {
                entityWorker.close();
                entityWorker = null;
            } catch (IOException e) {
                getLogger().warning("Exception while closing entity IO worker");
                e.printStackTrace();
            }
        }

        if (chunkWorker != null) {
            try {
                chunkWorker.close();
                chunkWorker = null;
            } catch (IOException e) {
                getLogger().warning("Exception while closing chunk IO worker");
                e.printStackTrace();
            }
        }
    }

    @Nullable
    public CompoundTag loadEntities(ChunkPos chunkPos) throws Exception {
        if (entityWorker == null)
            throw new Exception("Unable to load entities because the worker isn't initialized, check the startup log for errors.");

        return entityWorker.load(chunkPos);
    }

    @Nullable
    public CompoundTag loadChunk(ChunkPos chunkPos) throws Exception {
        if (chunkWorker == null)
            throw new Exception("Unable to load chunk because the chunk worker isn't initialized, check the startup log for errors.");

        return chunkWorker.load(chunkPos);
    }

    public Inventory createBlankPage(int pageCount) {
        Inventory page = Bukkit.createInventory(null, 54, Component.text("Restored Items | Page " + pageCount));

        ItemStack nextPage = new ItemStack(Material.ARROW);
        ItemMeta meta = nextPage.getItemMeta();
        meta.displayName(Component.text("Next", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, 1);
        nextPage.setItemMeta(meta);

        ItemStack prevPage = new ItemStack(Material.ARROW);
        meta = prevPage.getItemMeta();
        meta.displayName(Component.text("Back", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(key, PersistentDataType.INTEGER, -1);
        prevPage.setItemMeta(meta);


        page.setItem(53, nextPage);
        page.setItem(45, prevPage);
        return page;
    }

    public void openInventory(Player player, Collection<ItemEntry> items) {
        int pageCount = 1;
        Inventory page = createBlankPage(pageCount);
        List<Inventory> pages = new ArrayList<>();

        for (ItemEntry entry : items) {
            if (page.firstEmpty() == 46) {
                pages.add(page);
                page = createBlankPage(++pageCount);
            }

            ItemStack item = entry.itemStack();

            ItemMeta meta = item.getItemMeta();
            Component loreComponent = Component.text("Original Location: " + entry.location().getBlockX() + ", " + entry.location().getBlockY() + ", " + entry.location().getBlockZ()).decoration(TextDecoration.ITALIC, false);

            if (meta.hasLore())
                meta.lore().add(loreComponent);
            else {
                List<Component> lore = new ArrayList<>();
                lore.add(loreComponent);
                meta.lore(lore);
            }

            item.setItemMeta(meta);

            page.addItem(item);
        }

        if (pages.size() > 0) {
            ItemStack dumpItems = new ItemStack(Material.RED_WOOL);
            ItemMeta itemMeta = dumpItems.getItemMeta();

            itemMeta.displayName(Component.text("Dump Items", NamedTextColor.RED).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
            itemMeta.getPersistentDataContainer().set(key, PersistentDataType.STRING, "dump");
            dumpItems.setItemMeta(itemMeta);

            page.setItem(49, dumpItems);
        }

        pages.add(page);

        new RestoredInventoryHolder(player, pages, this);
    }
}
