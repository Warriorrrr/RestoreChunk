package net.earthmc.restorechunk.command;

import net.earthmc.restorechunk.RestoreChunkPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RestoreEntitiesCommand implements CommandExecutor {
    private final RestoreChunkPlugin plugin;

    public RestoreEntitiesCommand(RestoreChunkPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        handleCommand(sender);
        return true;
    }

    public void handleCommand(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command cannot be executed by console."));
            return;
        }

        if (!sender.hasPermission("itemframerestorer.command.restoreentities")) {
            sender.sendMessage(Component.text("You do not have enough permissions to execute this command.", NamedTextColor.RED));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            CompoundTag compoundTag;
            ChunkPos pos = new ChunkPos(new BlockPos(player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ()));

            try {
                compoundTag = plugin.loadEntities(pos);
            } catch (Exception e) {
                sender.sendMessage(Component.text("An unknown exception occurred when loading entity NBT: " + e.getClass().getName() + ": " + e.getMessage(), NamedTextColor.RED));
                e.printStackTrace();
                return;
            }

            if (compoundTag == null) {
                sender.sendMessage(Component.text("Could not find any entities to restore for this chunk.", NamedTextColor.RED));
                return;
            }

            List<ItemEntry> entries = new ArrayList<>();
            for (Tag tag : compoundTag.getList("Entities", 10)) {
                CompoundTag entityTag = (CompoundTag) tag;

                Optional<EntityType<?>> type = EntityType.by(entityTag);
                if (type.isPresent() && type.get().id.equals("item_frame")) {
                    net.minecraft.world.item.ItemStack framedItem = net.minecraft.world.item.ItemStack.of(entityTag.getCompound("Item"));

                    if (!framedItem.isEmpty()) {
                        ListTag posTag = entityTag.getList("Pos", 6);
                        Location itemFrameLocation = new Location(player.getWorld(), posTag.getDouble(0), posTag.getDouble(1), posTag.getDouble(2));

                        entries.add(new ItemEntry(itemFrameLocation, framedItem.getBukkitStack()));
                    }
                }
            }

            if (entries.isEmpty()) {
                sender.sendMessage(Component.text("Could not find any items to restore for this chunk.", NamedTextColor.RED));
                return;
            }

            plugin.openInventory(player, entries);
        });
    }

    public record ItemEntry(Location location, ItemStack itemStack) {}
}
