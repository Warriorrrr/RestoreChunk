package dev.warriorrr.restorechunk.command.argument;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public record ParseResults(boolean preview, boolean relight, Map<Block, Predicate<BlockPos>> includes, Set<Predicate<BlockPos>> predicates) {}
