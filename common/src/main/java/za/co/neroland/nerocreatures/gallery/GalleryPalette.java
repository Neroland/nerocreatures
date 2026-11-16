package za.co.neroland.nerocreatures.gallery;

import java.util.List;
import java.util.Map;

/**
 * The Menagerie's block palettes, as block-state strings ({@code id[prop=value]}). Pure data; the
 * builder resolves the strings. Every vanilla block named here exists on 1.21.1.
 *
 * @param floor     bay floor
 * @param inlay     the 3 x 3 inlay under each exhibit
 * @param threshold the accent strip on the bay's front edge
 * @param wall      back wall
 * @param wallAlt   wall speckle (or the same block)
 * @param diorama   diorama blocks, in use order
 * @param lamp      the lamp set into the floor
 * @param sign      the standing sign's block id (rotation is added by the builder)
 * @param wallSign  the wall sign's block id
 * @param glowText  glowing white text instead of black
 */
public record GalleryPalette(String floor, String inlay, String threshold, String wall, String wallAlt,
        List<String> diorama, String lamp, String sign, String wallSign, boolean glowText) {

    private static GalleryPalette p(String floor, String inlay, String threshold, String wall, String wallAlt,
            List<String> diorama, String lamp, String wood, boolean glow) {
        return new GalleryPalette("minecraft:" + floor, "minecraft:" + inlay, "minecraft:" + threshold,
                "minecraft:" + wall, "minecraft:" + wallAlt, diorama.stream().map(id -> "minecraft:" + id).toList(),
                "minecraft:" + lamp, "minecraft:" + wood + "_sign", "minecraft:" + wood + "_wall_sign", glow);
    }

    public static final Map<String, GalleryPalette> BY_ID = Map.of(
            "luna", p("smooth_stone", "calcite", "yellow_concrete", "polished_diorite", "polished_diorite",
                    List.of("gravel", "cobbled_deepslate", "dead_tube_coral_block"), "pearlescent_froglight", "birch",
                    false),
            "mars", p("smooth_red_sandstone", "terracotta", "prismarine_bricks", "cut_red_sandstone",
                    "cut_red_sandstone", List.of("red_sand", "orange_terracotta", "dead_bush"), "ochre_froglight",
                    "acacia", false),
            "cindara", p("polished_blackstone", "polished_basalt", "orange_concrete", "blackstone",
                    "gilded_blackstone", List.of("basalt", "netherrack", "magma_block"), "shroomlight", "crimson",
                    true),
            "glacira", p("snow_block", "blue_ice", "light_blue_concrete", "packed_ice", "packed_ice",
                    List.of("snow[layers=2]", "ice", "packed_ice"), "sea_lantern", "spruce", false),
            "greenxertz", p("moss_block", "mud_bricks", "lime_concrete", "moss_block",
                    "azalea_leaves[persistent=true]", List.of("moss_carpet", "flowering_azalea", "glow_lichen"),
                    "verdant_froglight", "jungle", false),
            "nyxara", p("calcite", "amethyst_block", "magenta_concrete", "smooth_basalt", "smooth_basalt",
                    List.of("amethyst_block", "amethyst_cluster", "tinted_glass"), "calcite", "cherry", false),
            "asteroid", p("cobbled_deepslate", "tuff_bricks", "blue_concrete", "deepslate", "deepslate",
                    List.of("raw_iron_block", "deepslate_iron_ore", "tuff"), "glowstone", "dark_oak", true),
            "wrecks", p("polished_andesite", "waxed_exposed_cut_copper", "red_concrete", "waxed_weathered_copper",
                    "iron_bars", List.of("waxed_copper_grate", "cauldron", "iron_block"),
                    "waxed_copper_bulb[lit=true]", "mangrove", true),
            "deep_space", p("black_concrete", "obsidian", "purple_concrete", "obsidian", "crying_obsidian",
                    List.of("end_stone", "purple_stained_glass", "end_stone"), "sea_lantern", "warped", true),
            "utility", p("deepslate_tiles", "deepslate_tiles", "light_gray_concrete", "polished_deepslate",
                    "polished_deepslate", List.of(), "sea_lantern", "oak", false));

    /** The trait wall's own palette. */
    public static final GalleryPalette TRAITS = p("polished_deepslate", "smooth_quartz", "white_concrete",
            "quartz_block", "white_stained_glass", List.of(), "sea_lantern", "oak", false);

    public static GalleryPalette of(String id) {
        if ("traits".equals(id)) {
            return TRAITS;
        }
        GalleryPalette palette = BY_ID.get(id);
        return palette != null ? palette : BY_ID.get("utility");
    }

    public static final String STREET = "minecraft:polished_deepslate";
    public static final String KERB = "minecraft:deepslate_tiles";
    public static final String STREET_LAMP = "minecraft:sea_lantern";
    public static final String PLAZA_CENTRE = "minecraft:chiseled_deepslate";
    public static final String LIGHT = "minecraft:light[level=15]";
    public static final String PLATE = "minecraft:light_weighted_pressure_plate";
}
