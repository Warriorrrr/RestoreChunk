package dev.warriorrr.restorechunk.parsing;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArgumentParser {
    private static final Predicate<BlockPos> TRUE_PREDICATE = location -> true;

    private static final String PREDICATE_STRING = "([xyz])([<>=%&]|>=|<=)(\\d+|-\\d+)";
    private static final Pattern PREDICATE_PATTERN = Pattern.compile("\\(" + PREDICATE_STRING + "\\)");
    private static final Pattern PREDICATE_PATTERN_NO_PARENTHESIS = Pattern.compile(PREDICATE_STRING);

    private ArgumentParser() {}

    public static ParseResults parse(String[] args) throws ParsingException {
        final Map<Block, Predicate<BlockPos>> includeMaterials = new HashMap<>();
        final Set<Predicate<BlockPos>> predicates = new HashSet<>();
        boolean preview = false;
        boolean relight = false;

        for (String arg : args) {
            if (arg.startsWith("i:") || arg.startsWith("include:")) {
                String[] blocksToInclude = arg.split(":", 2)[1].split(",");
                for (String blockName : blocksToInclude) {
                    Predicate<BlockPos> predicate = parsePredicate(blockName, PREDICATE_PATTERN);

                    if (predicate != null)
                        blockName = PREDICATE_PATTERN.matcher(blockName).replaceAll("").trim();

                    final Block block = Optional.ofNullable(ResourceLocation.tryParse(blockName))
                            .map(key -> BuiltInRegistries.BLOCK.get(ResourceKey.create(Registries.BLOCK, key)))
                            .orElse(null);

                    if (block == null)
                        throw new ParsingException("Invalid block type: " + blockName);

                    includeMaterials.put(block, predicate == null ? TRUE_PREDICATE : predicate);
                }
            } else if (arg.startsWith("p:") || arg.startsWith("predicate:")) {
                String[] predicateArray = arg.split(":", 2)[1].split(",");
                for (String stringPredicate : predicateArray) {
                    Predicate<BlockPos> predicate = parsePredicate(stringPredicate, PREDICATE_PATTERN_NO_PARENTHESIS);
                    if (predicate == null)
                        throw new ParsingException("Invalid predicate format: " + stringPredicate + ". Must be " + PREDICATE_PATTERN.pattern() + ".");

                    predicates.add(predicate);
                }
            } else if ("#preview".equals(arg))
                preview = true;
            else if ("#relight".equals(arg))
                relight = true;
            else
                throw new ParsingException("Unknown argument: " + arg);
        }

        return new ParseResults(preview, relight, includeMaterials, predicates);
    }

    @Nullable
    private static Predicate<BlockPos> parsePredicate(String string, Pattern pattern) throws ParsingException {
        final Matcher matcher = pattern.matcher(string);
        if (!matcher.find())
            return null;

        try {
            final String xyz = matcher.group(1);
            final String operator = matcher.group(2);
            final int coord = Integer.parseInt(matcher.group(3));

            return (location -> {
                int block = switch (xyz) {
                    case "x" -> location.getX();
                    case "y" -> location.getY();
                    case "z" -> location.getZ();
                    default -> throw new RuntimeException("invalid argument: " + xyz);
                };

                return switch (operator) {
                    case ">" -> block > coord;
                    case "<" -> block < coord;
                    case ">=" -> block >= coord;
                    case "<=" -> block <= coord;
                    case "=" -> block == coord;
                    case "%" -> block % coord == 0;
                    case "&" -> (block & coord) == 0;
                    default -> throw new RuntimeException("invalid operator: " + operator);
                };
            });
        } catch (Exception e) {
            throw new ParsingException("Invalid predicate: " + matcher.group());
        }
    }
}
