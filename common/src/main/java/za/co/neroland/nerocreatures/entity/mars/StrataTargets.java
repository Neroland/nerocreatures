package za.co.neroland.nerocreatures.entity.mars;

import java.util.Locale;

import org.jetbrains.annotations.Nullable;

/**
 * What a Strata Hound tracks when shown an item, as pure string rules (no registry access), so the
 * mapping is testable and identical on every line.
 *
 * <p>A <b>target spec</b> is either a block tag ({@code "#minecraft:iron_ores"}) or a block id
 * ({@code "minecraft:amethyst_block"}). It is what the hound saves; it is creature data, never
 * player data.
 *
 * <ul>
 *   <li>A raw ore or a vanilla ore product (raw iron, coal, diamond, lapis, ...) maps to the vanilla
 *       ore tag for that material, so every stone and deepslate variant matches.</li>
 *   <li>Any other {@code raw_<material>} item maps to the convention tag {@code #c:ores/<material>},
 *       which other mods fill. An empty tag simply finds nothing.</li>
 *   <li>An ore block item ({@code *_ore}) of a vanilla material maps to the same vanilla tag; any
 *       other block item tracks exactly that block.</li>
 * </ul>
 */
public final class StrataTargets {

    /** Colour families for the back-stripe glow (entity contract {@code variant} 0-7). */
    public static final int FAMILY_NONE = 0;
    public static final int FAMILY_COAL = 1;
    public static final int FAMILY_IRON = 2;
    public static final int FAMILY_COPPER = 3;
    public static final int FAMILY_GOLD = 4;
    public static final int FAMILY_REDSTONE = 5;
    public static final int FAMILY_BLUE = 6;
    public static final int FAMILY_GREEN = 7;

    private StrataTargets() {
    }

    /** The vanilla ore tag for a material word, or null. */
    @Nullable
    static String vanillaOreTag(String material) {
        return switch (material) {
            case "iron" -> "#minecraft:iron_ores";
            case "copper" -> "#minecraft:copper_ores";
            case "gold" -> "#minecraft:gold_ores";
            case "coal" -> "#minecraft:coal_ores";
            case "diamond" -> "#minecraft:diamond_ores";
            case "emerald" -> "#minecraft:emerald_ores";
            case "lapis", "lapis_lazuli" -> "#minecraft:lapis_ores";
            case "redstone" -> "#minecraft:redstone_ores";
            default -> null;
        };
    }

    /**
     * The target spec for a shown item that is <b>not</b> a block item, or null if the hound has no
     * idea what to look for.
     */
    @Nullable
    public static String specForItem(String namespace, String path) {
        if (namespace == null || path == null || path.isEmpty()) {
            return null;
        }
        String vanilla = vanillaOreTag(path);
        if (vanilla != null && "minecraft".equals(namespace)) {
            return vanilla;
        }
        if (path.startsWith("raw_") && path.length() > 4 && !path.endsWith("_block")) {
            String material = path.substring(4);
            String tag = vanillaOreTag(material);
            if (tag != null) {
                return tag;
            }
            if (isSimpleWord(material)) {
                return "#c:ores/" + material;
            }
        }
        return null;
    }

    /**
     * The target spec for a shown block item whose block is {@code blockNamespace:blockPath}. Ores of
     * a vanilla material widen to the vanilla tag; anything else is that exact block. Null for air.
     */
    @Nullable
    public static String specForBlock(String blockNamespace, String blockPath) {
        if (blockNamespace == null || blockPath == null || blockPath.isEmpty() || "air".equals(blockPath)) {
            return null;
        }
        if (blockPath.endsWith("_ore")) {
            String tag = vanillaOreTag(materialOf(blockPath));
            if (tag != null) {
                return tag;
            }
        }
        return blockNamespace + ":" + blockPath;
    }

    /** The material word of an ore path: {@code deepslate_iron_ore} → {@code iron}. */
    static String materialOf(String orePath) {
        String p = orePath;
        if (p.endsWith("_ore")) {
            p = p.substring(0, p.length() - 4);
        }
        for (String prefix : new String[] {"deepslate_", "nether_", "end_", "stone_"}) {
            if (p.startsWith(prefix)) {
                p = p.substring(prefix.length());
            }
        }
        return p;
    }

    /** Whether a spec names a tag ({@code #ns:path}). */
    public static boolean isTag(String spec) {
        return spec != null && spec.startsWith("#");
    }

    /** The id part of a spec (without {@code #}). */
    public static String idOf(String spec) {
        return isTag(spec) ? spec.substring(1) : spec;
    }

    /** The glow colour family for a spec (by the material word it contains). */
    public static int colourFamily(@Nullable String spec) {
        if (spec == null) {
            return FAMILY_NONE;
        }
        String s = spec.toLowerCase(Locale.ROOT);
        if (s.contains("coal")) {
            return FAMILY_COAL;
        }
        if (s.contains("redstone")) {
            return FAMILY_REDSTONE;
        }
        if (s.contains("copper")) {
            return FAMILY_COPPER;
        }
        if (s.contains("gold")) {
            return FAMILY_GOLD;
        }
        if (s.contains("iron")) {
            return FAMILY_IRON;
        }
        if (s.contains("lapis") || s.contains("diamond")) {
            return FAMILY_BLUE;
        }
        if (s.contains("emerald")) {
            return FAMILY_GREEN;
        }
        return FAMILY_NONE;
    }

    /** A plain lowercase path word (letters, digits, underscore): safe to build a tag id from. */
    static boolean isSimpleWord(String s) {
        if (s.isEmpty()) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (!((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '_')) {
                return false;
            }
        }
        return true;
    }
}
