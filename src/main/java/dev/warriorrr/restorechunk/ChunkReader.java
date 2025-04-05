package dev.warriorrr.restorechunk;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import net.minecraft.world.level.chunk.storage.RegionStorageInfo;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Handles reading chunks as NBT from the plugin folder.
 */
public class ChunkReader {
    private static final MethodHandle REGION_FILE_STORAGE_CONSTRUCTOR;
    private static Throwable CONSTRUCTOR_NOT_FOUND_TRACE = null;

    private final RestoreChunk plugin;
    private final Path dataFolderPath;

    public ChunkReader(final RestoreChunk plugin) {
        this.plugin = plugin;
        this.dataFolderPath = plugin.getDataPath();

        try {
            Files.createDirectory(this.dataFolderPath);
        } catch (IOException e) {
            if (!(e instanceof FileAlreadyExistsException)) {
                plugin.logger().warn("Failed to create the plugin data folder", e);
            }
        }

        if (CONSTRUCTOR_NOT_FOUND_TRACE != null) {
            plugin.logger().error("Could not find the region file storage constructor, the plugin will not be able to work. (Are you up to date?)", CONSTRUCTOR_NOT_FOUND_TRACE);
        }
    }

    @Nullable
    public CompoundTag readChunkData(ServerLevel level, ChunkPos chunkPos) throws IOException {
        final Path regionDir = dataFolderPath.resolve(level.levelStorageAccess.getLevelId()).resolve("region");
        if (!Files.exists(regionDir))
            Files.createDirectories(regionDir);

        try (final RegionFileStorage storage = createRegionFileStorage(level, regionDir)) {
            return storage.read(chunkPos);
        } catch (Throwable throwable) {
            if (throwable instanceof Error err)
                throw err;

            plugin.logger().error("An exception occurred while reading chunk @ {} in world {}.", chunkPos, level.dimension().location());
            return null;
        }
    }

    private RegionFileStorage createRegionFileStorage(ServerLevel level, Path path) throws Throwable {
        return (RegionFileStorage) REGION_FILE_STORAGE_CONSTRUCTOR.invokeExact(new RegionStorageInfo(level.levelStorageAccess.getLevelId(),level.dimension(), "chunk"), path, false);
    }

    static {
        MethodHandle temp = null;

        try {
            temp = MethodHandles.privateLookupIn(RegionFileStorage.class, MethodHandles.lookup()).findConstructor(RegionFileStorage.class, MethodType.methodType(void.class, RegionStorageInfo.class, Path.class, boolean.class));
        } catch (Throwable throwable) {
            CONSTRUCTOR_NOT_FOUND_TRACE = throwable;
        }

        REGION_FILE_STORAGE_CONSTRUCTOR = temp;
    }
}
