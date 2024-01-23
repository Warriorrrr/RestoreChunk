package dev.warriorrr.restorechunk.parsing;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;

import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

public record ParseResults(boolean preview, boolean relight, boolean updateInhabited, Map<Block, Predicate<BlockPos>> includes, Set<Predicate<BlockPos>> predicates) {}
