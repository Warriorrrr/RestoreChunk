package net.earthmc.restorechunk.command;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.earthmc.restorechunk.RestoreChunkPlugin;
import net.earthmc.restorechunk.object.parsing.ArgumentParser;
import net.earthmc.restorechunk.object.parsing.ParsingException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_19_R1.CraftChunk;
import org.bukkit.craftbukkit.v1_19_R1.CraftWorld;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class RestoreChunkCommand implements CommandExecutor {
    private final RestoreChunkPlugin plugin;
    private final Logger logger;

    private final Map<UUID, Tuple<Integer, PreviewData>> previewMap = new HashMap<>();

    public RestoreChunkCommand(RestoreChunkPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getSLF4JLogger();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && "apply".equalsIgnoreCase(args[0]))
            apply(sender);
        else
            execute(sender, args);
        return true;
    }

    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command cannot be used by console.", NamedTextColor.RED));
            return;
        }

        if (!player.hasPermission("restorechunk.command.restorechunk")) {
            sender.sendMessage(Component.text("You do not have enough permissions to use this command.", NamedTextColor.RED));
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            // Parse args
            ArgumentParser parsedArgs;
            try {
                parsedArgs = ArgumentParser.parse(args);
            } catch (ParsingException e) {
                sender.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
                return;
            }

            final long start = System.currentTimeMillis();

            CompoundTag compoundTag;
            ChunkPos pos = new ChunkPos(new BlockPos(player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ()));
            LevelChunk chunk = ((CraftChunk) player.getChunk()).getHandle();

            try {
                compoundTag = plugin.loadChunk(player.getWorld().getName(), pos);
            } catch (Exception e) {
                sender.sendMessage(Component.text("An unknown exception occurred when loading chunk: " + e.getClass().getName() + ": " + e.getMessage(), NamedTextColor.RED));
                e.printStackTrace();
                return;
            }

            if (compoundTag == null) {
                sender.sendMessage(Component.text("Could not find a chunk to restore in the backup.", NamedTextColor.RED));
                return;
            }

            ServerLevel level = ((CraftWorld) player.getWorld()).getHandle();
            ChunkMap chunkMap = level.getChunkSource().chunkMap;

            compoundTag = chunkMap.upgradeChunkTag(level.getTypeKey(), chunkMap.overworldDataStorage, compoundTag, chunkMap.generator.getTypeNameForDataFixer(), pos, level);

            List<LevelChunkSection> chunkSections = new ArrayList<>();

            for (Tag tag : compoundTag.getList("sections", 10)) {
                CompoundTag sectionData = (CompoundTag) tag;
                byte y = sectionData.getByte("Y");

                PalettedContainer<BlockState> blockStates;
                PalettedContainer<Holder<Biome>> biomes;

                if (sectionData.contains("block_states", 10)) {
                    DataResult<PalettedContainer<BlockState>> dataResult = ChunkSerializer.BLOCK_STATE_CODEC.parse(NbtOps.INSTANCE, sectionData.getCompound("block_states")).promotePartial((s) -> logger.error("Error when getting chunk data: " + s));

                    blockStates = dataResult.getOrThrow(false, logger::error);
                } else
                    continue;

                Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(Registry.BIOME_REGISTRY);
                Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec = PalettedContainer.codecRO(biomeRegistry.asHolderIdMap(), biomeRegistry.holderByNameCodec(), PalettedContainer.Strategy.SECTION_BIOMES, biomeRegistry.getHolderOrThrow(Biomes.PLAINS));

                if (sectionData.contains("biomes", 10)) {
                    DataResult<PalettedContainerRO<Holder<Biome>>> dataResult = biomeCodec.parse(NbtOps.INSTANCE, sectionData.getCompound("biomes")).promotePartial(s -> logger.error("Error when getting biome data: " + s));
                    biomes = dataResult.getOrThrow(false, logger::error).recreate();
                } else
                    biomes = new PalettedContainer<>(biomeRegistry.asHolderIdMap(), biomeRegistry.getHolderOrThrow(Biomes.PLAINS), PalettedContainer.Strategy.SECTION_BIOMES, null);

                LevelChunkSection chunkSection = new LevelChunkSection(y, blockStates, biomes);

                chunkSections.add(chunkSection);
            }

            // Loop through chunk sections and set the blocks.
            Map<Block, BlockData> blocks = new HashMap<>();
            Map<BlockPos, Holder<Biome>> biomes = new HashMap<>();
            for (LevelChunkSection section : chunkSections) {
                if (section == null)
                    continue;

                final int sectionY = section.bottomBlockY() >> 4;
                for (int y = 0; y < 16; y++) {
                    for (int x = 0; x < 16; x++) {
                        for (int z = 0; z < 16; z++) {
                            BlockPos blockPos = new BlockPos(x, SectionPos.sectionToBlockCoord(sectionY, y), z);

                            BlockState state = section.getBlockState(x, y, z);
                            if (state.getBlock() != chunk.getBlockIfLoaded(blockPos))
                                blocks.put(chunk.bukkitChunk.getBlock(x, SectionPos.sectionToBlockCoord(sectionY, y), z), state.createCraftBlockData());

                            try {
                                biomes.put(blockPos, section.getNoiseBiome(x, y, z));
                            } catch (ArrayIndexOutOfBoundsException ignored) {}
                        }
                    }
                }
            }

            // Filter blocks to included materials
            if (!parsedArgs.includes().isEmpty()) {
                blocks.entrySet().removeIf(entry -> {
                    Predicate<Location> predicate = parsedArgs.includes().get(entry.getValue().getMaterial());

                    return predicate == null || !predicate.test(entry.getKey().getLocation());
                });
            }

            // Apply predicates
            if (!parsedArgs.predicates().isEmpty())
                blocks.keySet().removeIf(block -> !parsedArgs.predicates().stream().allMatch(predicate -> predicate.test(block.getLocation())));

            if (parsedArgs.preview() && !blocks.isEmpty()) {
                int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> previewMap.remove(player.getUniqueId()), 120 * 20L).getTaskId();
                previewMap.put(player.getUniqueId(), new Tuple<>(taskId, new PreviewData(level, chunk.getPos(), blocks, biomes)));

                player.sendMultiBlockChange(blocks.entrySet().stream().collect(Collectors.toMap(entry -> entry.getKey().getLocation(), Map.Entry::getValue)), true);
                player.sendMessage(Component.text("You are now previewing a restore, use /restorechunk apply to apply.", NamedTextColor.GREEN));

                return;
            }

            if (!blocks.isEmpty()) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    for (Map.Entry<Block, BlockData> entry : blocks.entrySet())
                        entry.getKey().setBlockData(entry.getValue());

                    for (Map.Entry<BlockPos, Holder<Biome>> entry : biomes.entrySet())
                        chunk.setBiome(entry.getKey().getX() >> 2, entry.getKey().getY() >> 2, entry.getKey().getZ() >> 2, entry.getValue());
                });
            }

            player.sendMessage(Component.text("Successfully restored chunk ", NamedTextColor.GREEN)
                    .append(Component.text(String.format("(%d, %d)", chunk.getPos().x, chunk.getPos().z), NamedTextColor.AQUA))
                    .append(Component.text(" in "))
                    .append(Component.text(String.format("%dms", System.currentTimeMillis() - start), NamedTextColor.AQUA))
                    .append(Component.text(", affecting "))
                    .append(Component.text(blocks.size(), NamedTextColor.AQUA))
                    .append(Component.text(" blocks.")));

            // Load tile entities a few ticks afterwards, experienced crashes when opening a chest with 0-1 tick delay
            /*
            Bukkit.getScheduler().runTaskLaterAsynchronously(plugin, () -> {
                LevelChunk levelChunk = ((CraftChunk) chunk).getHandle();
                for (Tag tag : compoundTag.getList("TileEntities", 10)) {
                    CompoundTag blockEntityNbt = (CompoundTag) tag;

                    if (blockEntityNbt.getBoolean("keepPacked")) {
                        levelChunk.setBlockEntityNbt(blockEntityNbt);
                        continue;
                    }

                    BlockPos blockPos = new BlockPos(blockEntityNbt.getInt("x"), blockEntityNbt.getInt("y"), blockEntityNbt.getInt("z"));
                    BlockEntity blockEntity = BlockEntity.loadStatic(blockPos, levelChunk.getBlockState(blockPos), blockEntityNbt);

                    if (blockEntity != null)
                        Bukkit.getScheduler().runTask(plugin, () -> levelChunk.setBlockEntity(blockEntity));
                }
            }, 3);
            */
        });
    }

    public void apply(CommandSender sender) {
        if (!(sender instanceof Player player))
            return;

        Tuple<Integer, PreviewData> tuple = previewMap.remove(player.getUniqueId());

        if (tuple == null) {
            sender.sendMessage(Component.text("You have nothing to apply!", NamedTextColor.RED));
            return;
        }

        Bukkit.getScheduler().cancelTask(tuple.getA());
        PreviewData data = tuple.getB();

        LevelChunk chunk = data.level.getChunkIfLoaded(data.chunkPos.x, data.chunkPos.z);
        if (chunk == null) {
            sender.sendMessage(Component.text("Cannot process restore for an unloaded chunk.", NamedTextColor.RED));
            return;
        }

        for (Map.Entry<Block, BlockData> entry : data.blocks.entrySet())
            entry.getKey().setBlockData(entry.getValue());

        for (Map.Entry<BlockPos, Holder<Biome>> entry : data.biomes.entrySet())
            chunk.setBiome(entry.getKey().getX() >> 2, entry.getKey().getY() >> 2, entry.getKey().getZ() >> 2, entry.getValue());
    }

    private record PreviewData(Level level, ChunkPos chunkPos, Map<Block, BlockData> blocks, Map<BlockPos, Holder<Biome>> biomes) {}
}
