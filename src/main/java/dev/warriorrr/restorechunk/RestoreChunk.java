package dev.warriorrr.restorechunk;

import dev.warriorrr.restorechunk.command.RestoreChunkCommand;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;

import java.util.Objects;

public final class RestoreChunk extends JavaPlugin {
    private final ChunkReader chunkReader = new ChunkReader(this);

    @Override
    public void onEnable() {
        Objects.requireNonNull(getCommand("restorechunk")).setExecutor(new RestoreChunkCommand(this));
    }

    public Logger logger() {
        return this.getSLF4JLogger();
    }

    public ChunkReader chunkReader() {
        return this.chunkReader;
    }
}
