package net.earthmc.restorechunk.object.parsing;

import org.bukkit.Location;
import org.bukkit.Material;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ArgumentParser {
    private static final Predicate<Location> TRUE_PREDICATE = location -> true;

    private static final String PREDICATE_STRING = "([xyz])([<>=]|>=|<=)(\\d+|-\\d+)";
    private static final Pattern PREDICATE_PATTERN = Pattern.compile("\\(" + PREDICATE_STRING + "\\)");
    private static final Pattern PREDICATE_PATTERN_NO_PARENTHESIS = Pattern.compile(PREDICATE_STRING);

    private final Map<Material, Predicate<Location>> includeMaterials = new HashMap<>();
    private final Set<Predicate<Location>> predicates = new HashSet<>();
    private boolean preview;
    private boolean relight;

    private ArgumentParser(String[] args) throws ParsingException {
        for (String arg : args) {
            if (arg.startsWith("i:") || arg.startsWith("include:")) {
                String[] blocksToInclude = arg.split(":", 2)[1].split(",");
                for (String blockName : blocksToInclude) {
                    Predicate<Location> predicate = parsePredicate(blockName, PREDICATE_PATTERN);

                    if (predicate != null)
                        blockName = PREDICATE_PATTERN.matcher(blockName).replaceAll("").trim();

                    Material material = Material.matchMaterial(blockName);

                    if (material == null || !material.isBlock())
                        throw new ParsingException("Invalid block type: " + blockName);

                    includeMaterials.put(material, predicate == null ? TRUE_PREDICATE : predicate);
                }
            } else if (arg.startsWith("p:") || arg.startsWith("predicate:")) {
                String[] predicateArray = arg.split(":", 2)[1].split(",");
                for (String stringPredicate : predicateArray) {
                    Predicate<Location> predicate = parsePredicate(stringPredicate, PREDICATE_PATTERN_NO_PARENTHESIS);
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
    }

    @Nullable
    private Predicate<Location> parsePredicate(String string, Pattern pattern) throws ParsingException {
        final Matcher matcher = pattern.matcher(string);
        if (!matcher.find())
            return null;

        try {
            final String xyz = matcher.group(1);
            final String comparator = matcher.group(2);
            final int coord = Integer.parseInt(matcher.group(3));

            return (location -> {
                int block = switch (xyz) {
                    case "x" -> location.getBlockX();
                    case "y" -> location.getBlockY();
                    case "z" -> location.getBlockZ();
                    default -> throw new RuntimeException("invalid argument: " + xyz);
                };

                return switch (comparator) {
                    case ">" -> block > coord;
                    case "<" -> block < coord;
                    case ">=" -> block >= coord;
                    case "<=" -> block <= coord;
                    case "=" -> block == coord;
                    default -> throw new RuntimeException("invalid comparator: " + comparator);
                };
            });
        } catch (Exception e) {
            throw new ParsingException("Invalid predicate: " + matcher.group());
        }
    }

    public static ArgumentParser parse(String[] args) throws ParsingException {
        return new ArgumentParser(args);
    }

    public boolean preview() {
        return this.preview;
    }

    public boolean relight() {
        return this.relight;
    }

    public Map<Material, Predicate<Location>> includes() {
        return this.includeMaterials;
    }

    public Set<Predicate<Location>> predicates() {
        return this.predicates;
    }
}
