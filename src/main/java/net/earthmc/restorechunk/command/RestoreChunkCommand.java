package net.earthmc.restorechunk.command;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import io.papermc.paper.util.MCUtil;
import net.earthmc.restorechunk.RestoreChunkPlugin;
import net.earthmc.restorechunk.object.parsing.ArgumentParser;
import net.earthmc.restorechunk.object.parsing.ParsingException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ThreadedLevelLightEngine;
import net.minecraft.util.Tuple;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import net.minecraft.world.level.chunk.storage.ChunkStorage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_19_R3.CraftChunk;
import org.bukkit.craftbukkit.v1_19_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_19_R3.block.CraftBlock;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@SuppressWarnings("UnstableApiUsage")
public class RestoreChunkCommand implements CommandExecutor {
    private final RestoreChunkPlugin plugin;
    private final Logger logger;

    private final Map<UUID, Tuple<Integer, RestoreData>> previewMap = new HashMap<>();

    public RestoreChunkCommand(RestoreChunkPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.getSLF4JLogger();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length > 0 && "apply".equalsIgnoreCase(args[0]))
            apply(sender);
        else
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> execute(sender, args));
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
        LevelChunk chunk = (LevelChunk) ((CraftChunk) player.getChunk()).getHandle(ChunkStatus.FULL);

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

        Map<BlockPos, BlockState> blocks = new HashMap<>();
        Map<BlockPos, Holder<Biome>> biomes = new HashMap<>();

        for (Tag tag : compoundTag.getList("sections", 10)) {
            CompoundTag sectionData = (CompoundTag) tag;
            final byte sectionY = sectionData.getByte("Y");

            PalettedContainer<BlockState> blockStates;
            PalettedContainer<Holder<Biome>> biomeHolders;

            if (sectionData.contains("block_states", 10)) {
                DataResult<PalettedContainer<BlockState>> dataResult = ChunkSerializer.BLOCK_STATE_CODEC.parse(NbtOps.INSTANCE, sectionData.getCompound("block_states")).promotePartial((s) -> logger.error("Error when getting chunk data: " + s));

                blockStates = dataResult.getOrThrow(false, logger::error);
            } else
                continue;

            Registry<Biome> biomeRegistry = level.registryAccess().registryOrThrow(Registries.BIOME);
            Codec<PalettedContainerRO<Holder<Biome>>> biomeCodec = PalettedContainer.codecRO(biomeRegistry.asHolderIdMap(), biomeRegistry.holderByNameCodec(), PalettedContainer.Strategy.SECTION_BIOMES, biomeRegistry.getHolderOrThrow(Biomes.PLAINS));

            if (sectionData.contains("biomes", 10)) {
                DataResult<PalettedContainerRO<Holder<Biome>>> dataResult = biomeCodec.parse(NbtOps.INSTANCE, sectionData.getCompound("biomes")).promotePartial(s -> logger.error("Error when getting biome data: " + s));
                biomeHolders = dataResult.getOrThrow(false, logger::error).recreate();
            } else
                biomeHolders = new PalettedContainer<>(biomeRegistry.asHolderIdMap(), biomeRegistry.getHolderOrThrow(Biomes.PLAINS), PalettedContainer.Strategy.SECTION_BIOMES, null);

            for (int y = 0; y < 16; y++) {
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        final BlockPos blockPos = new BlockPos(SectionPos.sectionToBlockCoord(chunk.locX, x), SectionPos.sectionToBlockCoord(sectionY, y), SectionPos.sectionToBlockCoord(chunk.locZ, z));

                        final BlockState state = blockStates.get(x, y, z);
                        if (state.getBlock() != chunk.getBlockIfLoaded(blockPos))
                            blocks.put(blockPos, state);

                        try {
                            biomes.put(blockPos, biomeHolders.get(x, y, z));
                        } catch (ArrayIndexOutOfBoundsException ignored) {}
                    }
                }
            }
        }

        final List<CompoundTag> blockEntities = compoundTag.getList("block_entities", 10).stream()
                .map(tag -> tag.getId() == 10 ? (CompoundTag) tag : new CompoundTag())
                .toList();

        final long inhabitedTime = compoundTag.getLong("InhabitedTime");

        // Filter blocks to included materials
        if (!parsedArgs.includes().isEmpty()) {
            blocks.entrySet().removeIf(entry -> {
                Predicate<BlockPos> predicate = parsedArgs.includes().get(entry.getValue().getBlock());

                return predicate == null || !predicate.test(entry.getKey());
            });
        }

        // Apply predicates
        if (!parsedArgs.predicates().isEmpty())
            blocks.keySet().removeIf(blockPos -> !parsedArgs.predicates().stream().allMatch(predicate -> predicate.test(blockPos)));

        final RestoreData data = new RestoreData(level, chunk.getPos(), blocks, biomes, System.currentTimeMillis() - start, blockEntities, inhabitedTime, parsedArgs);

        if (parsedArgs.preview() && !blocks.isEmpty()) {
            int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> previewMap.remove(player.getUniqueId()), 120 * 20L).getTaskId();
            previewMap.put(player.getUniqueId(), new Tuple<>(taskId, data));

            player.sendMultiBlockChange(blocks.entrySet().stream().collect(Collectors.toMap(entry -> CraftBlock.at(level, entry.getKey()).getLocation(), entry -> entry.getValue().createCraftBlockData())), true);
            player.sendMessage(Component.text("You are now previewing a restore, use /restorechunk apply to apply.", NamedTextColor.GREEN));
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> finishRestore(player, data));
    }

    public void apply(CommandSender sender) {
        if (!(sender instanceof Player player))
            return;

        Tuple<Integer, RestoreData> tuple = previewMap.remove(player.getUniqueId());

        if (tuple == null) {
            sender.sendMessage(Component.text("You have nothing to apply!", NamedTextColor.RED));
            return;
        }

        Bukkit.getScheduler().cancelTask(tuple.getA());
        finishRestore(player, tuple.getB());
    }

    private void finishRestore(final @NotNull Player player, final @NotNull RestoreData data) {
        final LevelChunk chunk = data.level.getChunkIfLoaded(data.chunkPos.x, data.chunkPos.z);
        if (chunk == null) {
            player.sendMessage(Component.text("Unable to complete restore due to chunk being unloaded.", NamedTextColor.RED));
            return;
        }

        chunk.setInhabitedTime(data.inhabitedTime);

        if (!data.blocks.isEmpty()) {
            chunk.clearAllBlockEntities();

            for (Map.Entry<BlockPos, BlockState> entry : data.blocks.entrySet())
                data.level.setBlockAndUpdate(entry.getKey(), entry.getValue());

            for (CompoundTag blockEntityTag : data.blockEntities()) {
                if (blockEntityTag.getBoolean("keepPacked")) {
                    chunk.setBlockEntityNbt(blockEntityTag);
                    continue;
                }

                BlockPos blockPos = BlockEntity.getPosFromTag(blockEntityTag);
                BlockEntity entity = BlockEntity.loadStatic(blockPos, chunk.getBlockState(blockPos), blockEntityTag);

                if (entity != null)
                    chunk.setBlockEntity(entity);
            }

            chunk.registerAllBlockEntitiesAfterLevelLoad();

            for (Map.Entry<BlockPos, Holder<Biome>> entry : data.biomes.entrySet())
                chunk.setBiome(entry.getKey().getX() >> 2, entry.getKey().getY() >> 2, entry.getKey().getZ() >> 2, entry.getValue());

            if (data.args.relight())
                relightChunks(data.level.chunkSource.getLightEngine(), chunk.getPos());

            if (!data.biomes.isEmpty())
                data.level.chunkSource.chunkMap.resendBiomesForChunks(List.of(chunk));
        }

        player.sendMessage(Component.text("Successfully restored chunk ", NamedTextColor.GREEN)
                .append(Component.text(String.format("(%d, %d)", chunk.getPos().x, chunk.getPos().z), NamedTextColor.AQUA))
                .append(Component.text(" in "))
                .append(Component.text(String.format("%dms", data.timeTaken()), NamedTextColor.AQUA))
                .append(Component.text(", affecting "))
                .append(Component.text(data.blocks.size(), NamedTextColor.AQUA))
                .append(Component.text(" blocks.")));
    }

    private void relightChunks(ThreadedLevelLightEngine lightEngine, ChunkPos center) {
        lightEngine.relight(new HashSet<>(MCUtil.getSpiralOutChunks(center.getWorldPosition(), 1)), progress -> {}, complete -> {});
    }

    private record RestoreData(ServerLevel level, ChunkPos chunkPos, Map<BlockPos, BlockState> blocks, Map<BlockPos, Holder<Biome>> biomes, long timeTaken, List<CompoundTag> blockEntities, long inhabitedTime, ArgumentParser args) {}
}
