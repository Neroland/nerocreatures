package za.co.neroland.nerocreatures.entity.alpha;

import java.util.List;

/**
 * Global alpha tuning, read from {@code data/nerocreatures/nerocreatures/alpha/settings.json}.
 *
 * @param chance          chance (0-1) that an eligible natural spawn becomes an alpha
 * @param maxPerDimension hard cap of loaded alphas per dimension
 * @param scaleBonus      added to {@code minecraft:scale} as {@code add_multiplied_base} (0.3 = 1.3x)
 * @param base            attribute bonuses every alpha gets on top of its rolled modifier
 */
public record AlphaSettings(double chance, int maxPerDimension, double scaleBonus,
        List<AlphaModifier.AttributeBonus> base) {

    /** Used when the settings file is missing or unreadable. */
    public static final AlphaSettings DEFAULTS = new AlphaSettings(0.02D, 4, 0.3D,
            List.of(new AlphaModifier.AttributeBonus("minecraft:max_health", 0.5D, "add_multiplied_base")));

    /** Hard ceiling regardless of data, so a typo cannot flood a dimension. */
    public static final int ABSOLUTE_MAX_PER_DIMENSION = 32;
}
