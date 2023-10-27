package net.earthmc.restorechunk;

import net.earthmc.restorechunk.command.RestoreChunkCommand;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.IOWorker;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public final class RestoreChunkPlugin extends JavaPlugin {

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
            logger().error("Could not find the IOWorker constructor, shutting down...", e);
            return;
        }

        Objects.requireNonNull(getCommand("restorechunk")).setExecutor(new RestoreChunkCommand(this));
    }

    @Nullable
    public CompoundTag loadChunk(String worldName, ChunkPos chunkPos) throws IOException {
        final Path regionDir = dataFolderPath.resolve(worldName).resolve("region");
        if (!Files.exists(regionDir))
            Files.createDirectories(regionDir);

        try (IOWorker worker = createIOWorker(regionDir, "restorechunk")) {
            return worker.loadAsync(chunkPos).join().orElse(null);
        } catch (ReflectiveOperationException e) {
            return null;
        }
    }

    private IOWorker createIOWorker(Path path, String name) throws ReflectiveOperationException {
        return (IOWorker) ioWorkerConstructor.newInstance(path, false, name);
    }

    public Logger logger() {
        return this.getSLF4JLogger();
    }
}
