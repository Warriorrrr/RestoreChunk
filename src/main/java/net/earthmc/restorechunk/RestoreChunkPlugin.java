package net.earthmc.restorechunk;

import net.earthmc.restorechunk.command.RestoreChunkCommand;
import net.earthmc.restorechunk.command.RestoreEntitiesCommand;
import net.earthmc.restorechunk.command.RestoreEntitiesCommand.ItemEntry;
import net.earthmc.restorechunk.listener.InventoryListener;
import net.earthmc.restorechunk.object.RestoredInventoryHolder;
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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public final class RestoreChunkPlugin extends JavaPlugin {

    public final NamespacedKey key = new NamespacedKey(this, "button");
    private Constructor<?> ioWorkerConstructor;
    private Path dataFolderPath;

    @Override
    public void onEnable() {
        getDataFolder().mkdir();
        this.dataFolderPath = getDataFolder().toPath();

        try {
            this.ioWorkerConstructor = IOWorker.class.getDeclaredConstructor(Path.class, boolean.class, String.class);
            this.ioWorkerConstructor.setAccessible(true);
        } catch (NoSuchMethodException e) {
            getSLF4JLogger().error("Could not find the IOWorker constructor, shutting down...", e);
            this.setEnabled(false);
            return;
        }

        getCommand("restoreentities").setExecutor(new RestoreEntitiesCommand(this));
        getCommand("restorechunk").setExecutor(new RestoreChunkCommand(this));
        Bukkit.getPluginManager().registerEvents(new InventoryListener(this), this);
    }

    @Override
    public void onDisable() {
        if (!Bukkit.isStopping()) {
            for (Player player : Bukkit.getOnlinePlayers())
                if (player.getOpenInventory().getTopInventory().getHolder() instanceof RestoredInventoryHolder)
                    player.closeInventory();
        }
    }

    @Nullable
    public CompoundTag loadEntities(String worldName, ChunkPos chunkPos) throws Exception {
        try (IOWorker worker = createIOWorker(dataFolderPath.resolve(worldName).resolve("entity"), "entityrestore")) {
            return worker.load(chunkPos);
        }
    }

    @Nullable
    public CompoundTag loadChunk(String worldName, ChunkPos chunkPos) throws Exception {
        try (IOWorker worker = createIOWorker(dataFolderPath.resolve(worldName).resolve("region"), "chunkrestore")) {
            return worker.load(chunkPos);
        }
    }

    private IOWorker createIOWorker(Path path, String name) throws Exception {
        if (!Files.exists(path))
            Files.createDirectories(path);

        return (IOWorker) ioWorkerConstructor.newInstance(path, false, name);
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
