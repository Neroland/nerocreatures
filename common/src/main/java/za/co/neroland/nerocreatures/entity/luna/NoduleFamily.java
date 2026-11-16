package za.co.neroland.nerocreatures.entity.luna;

/**
 * The eight ore colour families a Lode Tortoise's shell nodules can take (synced as
 * the visuals {@code variant}). Each family is matched by the block tag
 * {@code nerocreatures:lode_tortoise/<name>} and yields a vanilla item (nuggets or raw ore).
 *
 * <p>Pure data: the yield item lives in {@code LodeTortoise} so this enum stays game-type free.
 */
public enum NoduleFamily {
    COAL(0, "coal", 2, 1),
    COPPER(1, "copper", 1, 1),
    IRON(2, "iron", 3, 1),
    GOLD(3, "gold", 2, 2),
    REDSTONE(4, "redstone", 3, 1),
    LAPIS(5, "lapis", 3, 1),
    DIAMOND(6, "diamond", 1, 4),
    EMERALD(7, "emerald", 1, 4);

    private final int variant;
    private final String tagPath;
    private final int baseYield;
    private final int regrowMultiplier;

    NoduleFamily(int variant, String name, int baseYield, int regrowMultiplier) {
        this.variant = variant;
        this.tagPath = "lode_tortoise/" + name;
        this.baseYield = baseYield;
        this.regrowMultiplier = regrowMultiplier;
    }

    /** {@code CreatureVisuals} variant, 0-7. */
    public int variant() {
        return this.variant;
    }

    /** Block tag path under {@code nerocreatures:}. */
    public String tagPath() {
        return this.tagPath;
    }

    /** Items per harvest before the Bountiful roll and the cap. */
    public int baseYield() {
        return this.baseYield;
    }

    /** Rare families grow this many times slower. */
    public int regrowMultiplier() {
        return this.regrowMultiplier;
    }

    /** The family for a saved/synced variant, or {@code null} for anything out of range. */
    public static NoduleFamily byVariant(int variant) {
        for (NoduleFamily family : values()) {
            if (family.variant == variant) {
                return family;
            }
        }
        return null;
    }
}
