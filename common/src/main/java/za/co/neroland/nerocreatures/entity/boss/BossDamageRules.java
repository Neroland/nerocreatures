package za.co.neroland.nerocreatures.entity.boss;

/**
 * Which damage a boss takes at all (pure, unit-tested).
 *
 * <p>A boss only takes damage that a player stands behind: the player's own hit or shot, or a hit by
 * something a player owns (a tamed pet, a mount, a drone). Lava, fire, suffocation in a wall,
 * falling, drowning, cacti, dispensers and other mobs do nothing to a boss, so it cannot be killed
 * at no risk by parking it in a hazard. Damage that bypasses invulnerability ({@code /kill}, the
 * void) always lands, so an operator can still remove a boss.
 */
public final class BossDamageRules {

    private BossDamageRules() {
    }

    /**
     * Whether a boss takes this hit.
     *
     * @param playerBehind            a player, or something a player owns, caused the damage
     * @param bypassesInvulnerability the damage type is in {@code bypasses_invulnerability}
     */
    public static boolean accepts(boolean playerBehind, boolean bypassesInvulnerability) {
        return bypassesInvulnerability || playerBehind;
    }
}
