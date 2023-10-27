package net.earthmc.restorechunk;

import net.earthmc.restorechunk.command.RestoreChunkCommand;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileStorage;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class RestoreChunkPlugin extends JavaPlugin {

    private static final MethodHandle REGION_FILE_STORAGE_CONSTRUCTOR;
    private static Throwable CONSTRUCTOR_NOT_FOUND_TRACE = null;
    private Path dataFolderPath;

    @Override
    public void onEnable() {
        getDataFolder().mkdir();
        this.dataFolderPath = getDataFolder().toPath();

        if (CONSTRUCTOR_NOT_FOUND_TRACE != null) {
            logger().error("Could not find the region file storage constructor, the plugin will not work as expected until resolved.", CONSTRUCTOR_NOT_FOUND_TRACE);
            return;
        }

        Objects.requireNonNull(getCommand("restorechunk")).setExecutor(new RestoreChunkCommand(this));
    }

    @Nullable
    public CompoundTag loadChunk(String worldName, ChunkPos chunkPos) throws IOException {
        final Path regionDir = dataFolderPath.resolve(worldName).resolve("region");
        if (!Files.exists(regionDir))
            Files.createDirectories(regionDir);

        try (final RegionFileStorage storage = createRegionFileStorage(regionDir)) {
            return storage.read(chunkPos);
        } catch (Throwable throwable) {
            if (throwable instanceof Error err)
                throw err;

            return null;
        }
    }

    private RegionFileStorage createRegionFileStorage(Path path) throws Throwable {
        return (RegionFileStorage) REGION_FILE_STORAGE_CONSTRUCTOR.invokeExact(path, false);
    }

    public Logger logger() {
        return this.getSLF4JLogger();
    }

    static {
        MethodHandle temp = null;

        try {
            temp = MethodHandles.privateLookupIn(RegionFileStorage.class, MethodHandles.lookup()).findConstructor(RegionFileStorage.class, MethodType.methodType(void.class, Path.class, boolean.class));
        } catch (Throwable throwable) {
            CONSTRUCTOR_NOT_FOUND_TRACE = throwable;
        }

        REGION_FILE_STORAGE_CONSTRUCTOR = temp;
    }
}
