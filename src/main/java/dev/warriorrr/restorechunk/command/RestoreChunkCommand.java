package dev.warriorrr.restorechunk.command;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import dev.warriorrr.restorechunk.RestoreChunkPlugin;
import dev.warriorrr.restorechunk.parsing.ParseResults;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import io.papermc.paper.util.MCUtil;
import dev.warriorrr.restorechunk.parsing.ArgumentParser;
import dev.warriorrr.restorechunk.parsing.ParsingException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.PalettedContainer;
import net.minecraft.world.level.chunk.PalettedContainerRO;
import net.minecraft.world.level.chunk.storage.ChunkSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.craftbukkit.v1_20_R3.CraftChunk;
import org.bukkit.craftbukkit.v1_20_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_20_R3.block.CraftBlock;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class RestoreChunkCommand implements CommandExecutor {
    private final RestoreChunkPlugin plugin;
    private final Logger logger;

    private final Map<UUID, RestoreData> previewMap = new HashMap<>();

    public RestoreChunkCommand(RestoreChunkPlugin plugin) {
        this.plugin = plugin;
        this.logger = plugin.logger();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command cannot be used by console.", NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("restorechunk.command.restorechunk")) {
            sender.sendMessage(Component.text("You do not have enough permissions to use this command.", NamedTextColor.RED));
            return true;
        }

        if (args.length > 0 && "apply".equalsIgnoreCase(args[0])) {
            apply(player);
            return true;
        }

        // Copy block states from the players chunk while still on their thread
        final LevelChunk chunk = (LevelChunk) ((CraftChunk) player.getChunk()).getHandle(ChunkStatus.FULL);
        final List<PalettedContainer<BlockState>> states = new ArrayList<>();

        for (final LevelChunkSection section : chunk.getSections()) {
            states.add(section.getStates().copy());
        }

        plugin.getServer().getAsyncScheduler().runNow(plugin, task -> execute(player, states, args));
        return true;
    }

    public void execute(final Player player, final List<PalettedContainer<BlockState>> chunkBlockStates, final String[] args) {
        ParseResults arguments;
        try {
            arguments = ArgumentParser.parse(args);
        } catch (ParsingException e) {
            player.sendMessage(Component.text(e.getMessage(), NamedTextColor.RED));
            return;
        }

        final long start = System.currentTimeMillis();

        CompoundTag chunkTag;
        ChunkPos chunkPos = new ChunkPos(new BlockPos(player.getLocation().getBlockX(), player.getLocation().getBlockY(), player.getLocation().getBlockZ()));

        try {
            chunkTag = plugin.loadChunk(player.getWorld().getName(), chunkPos);
        } catch (IOException e) {
            player.sendMessage(Component.text("An unknown exception occurred when loading chunk: " + e.getClass().getName() + ": " + e.getMessage(), NamedTextColor.RED));
            plugin.getSLF4JLogger().warn("An unknown exception occurred when loading chunk", e);
            return;
        }

        if (chunkTag == null) {
            player.sendMessage(Component.text("Could not find a chunk to restore in the backup.", NamedTextColor.RED));
            return;
        }

        final ServerLevel level = ((CraftWorld) player.getWorld()).getHandle();
        final ChunkMap chunkMap = level.getChunkSource().chunkMap;

        chunkTag = chunkMap.upgradeChunkTag(level.getTypeKey(), chunkMap.overworldDataStorage, chunkTag, chunkMap.generator.getTypeNameForDataFixer(), chunkPos, level);

        final Map<BlockPos, BlockState> blocks = new HashMap<>();
        final Map<BlockPos, Holder<Biome>> biomes = new HashMap<>();

        final int minSection = SectionPos.blockToSectionCoord(level.getMinBuildHeight());

        for (final Tag tag : chunkTag.getList("sections", 10)) {
            final CompoundTag sectionData = (CompoundTag) tag;
            final byte sectionY = sectionData.getByte("Y");

            final int sectionIndex = sectionY + Math.abs(minSection);
            if (sectionIndex < 0 || sectionIndex >= chunkBlockStates.size())
                continue;

            final PalettedContainer<BlockState> currentChunkStates = chunkBlockStates.get(sectionIndex);

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
                        final BlockPos blockPos = new BlockPos(SectionPos.sectionToBlockCoord(chunkPos.x, x), SectionPos.sectionToBlockCoord(sectionY, y), SectionPos.sectionToBlockCoord(chunkPos.z, z));

                        final BlockState state = blockStates.get(x, y, z);
                        if (state.getBlock() != currentChunkStates.get(x, y, z).getBlock())
                            blocks.put(blockPos, state);

                        try {
                            biomes.put(blockPos, biomeHolders.get(x, y, z));
                        } catch (ArrayIndexOutOfBoundsException ignored) {}
                    }
                }
            }
        }

        final List<CompoundTag> blockEntities = chunkTag.getList("block_entities", 10).stream()
                .map(tag -> tag.getId() == 10 ? (CompoundTag) tag : new CompoundTag())
                .toList();

        final long inhabitedTime = chunkTag.getLong("InhabitedTime");

        // Filter blocks to included materials
        if (!arguments.includes().isEmpty()) {
            blocks.entrySet().removeIf(entry -> {
                Predicate<BlockPos> predicate = arguments.includes().get(entry.getValue().getBlock());

                return predicate == null || !predicate.test(entry.getKey());
            });
        }

        // Apply predicates
        if (!arguments.predicates().isEmpty())
            blocks.keySet().removeIf(blockPos -> !arguments.predicates().stream().allMatch(predicate -> predicate.test(blockPos)));

        if (blocks.isEmpty()) {
            player.sendMessage(Component.text("No blocks were found or changed.", NamedTextColor.RED));
            return;
        }

        final ScheduledTask scheduledTask = plugin.getServer().getAsyncScheduler().runDelayed(plugin, t -> previewMap.remove(player.getUniqueId()), 120L, TimeUnit.SECONDS);
        final RestoreData data = new RestoreData(scheduledTask, level, chunkPos, blocks, biomes, System.currentTimeMillis() - start, blockEntities, inhabitedTime, arguments);

        if (arguments.preview()) {
            previewMap.put(player.getUniqueId(), data);

            // noinspection UnstableApiUsage
            player.sendMultiBlockChange(blocks.entrySet().stream().collect(Collectors.toMap(entry -> CraftBlock.at(level, entry.getKey()).getLocation(), entry -> entry.getValue().createCraftBlockData())));
            player.sendMessage(Component.text("You are now previewing a restore, use /restorechunk apply to apply.", NamedTextColor.GREEN));
        } else {
            // Not previewing a restore so we don't need the task after all
            scheduledTask.cancel();

            player.getScheduler().run(plugin, task -> finishRestore(player, data), () -> {});
        }
    }

    public void apply(final Player player) {
        final RestoreData data = previewMap.remove(player.getUniqueId());

        if (data == null) {
            player.sendMessage(Component.text("You have nothing to apply!", NamedTextColor.RED));
            return;
        }

        if (data.task() != null)
            data.task().cancel();

        finishRestore(player, data);
    }

    private void finishRestore(final @NotNull Player player, final @NotNull RestoreData data) {
        final LevelChunk chunk = data.level.getChunkIfLoaded(data.chunkPos.x, data.chunkPos.z);
        if (chunk == null) {
            player.sendMessage(Component.text("Unable to complete restore due to chunk being unloaded.", NamedTextColor.RED));
            return;
        }

        if (data.arguments.updateInhabited())
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

            if (data.arguments.relight())
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

    private record RestoreData(ScheduledTask task, ServerLevel level, ChunkPos chunkPos, Map<BlockPos, BlockState> blocks, Map<BlockPos, Holder<Biome>> biomes, long timeTaken, List<CompoundTag> blockEntities, long inhabitedTime, ParseResults arguments) {}
}
