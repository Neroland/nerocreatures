package za.co.neroland.nerocreatures.entity.tame;

/**
 * Pure rules for <b>husbanded</b> livestock: an animal a player keeps or bred.
 * No Minecraft types, so every rule is unit-tested.
 *
 * <ul>
 *   <li>Any player husbandry marks the animal: a successful feed, a breed, a shear, a milk, a
 *       harvest, a leash or a name. A baby of two animals inherits the mark if either parent has
 *       it.</li>
 *   <li>A husbanded animal never despawns and is never wild prey for predators or Bloom Hosts.</li>
 *   <li>Saves from before the flag existed: an animal with an owner or a name is husbanded on
 *       load.</li>
 * </ul>
 */
public final class HusbandryRules {

    /** How often (ticks) a living animal checks whether it was leashed or named. */
    public static final int CHECK_INTERVAL_TICKS = 20;

    private HusbandryRules() {
    }

    /** The flag after a save is read. */
    public static boolean husbandedOnLoad(boolean savedFlag, boolean named, boolean owned) {
        return savedFlag || named || owned;
    }

    /** A player interaction marks the animal when the server accepted it. */
    public static boolean marksOnInteraction(boolean serverSide, boolean consumed) {
        return serverSide && consumed;
    }

    /** A leash or a name marks the animal. */
    public static boolean marksOnCheck(boolean leashed, boolean named) {
        return leashed || named;
    }

    /** A baby inherits the mark from either parent. */
    public static boolean offspringHusbanded(boolean parent, boolean partner) {
        return parent || partner;
    }

    /** Whether vanilla's far-away despawn may remove the animal. */
    public static boolean mayDespawn(boolean persistenceRequired, boolean tame, boolean husbanded) {
        return !persistenceRequired && !tame && !husbanded;
    }

    /** Whether a predator may treat the animal as wild prey. */
    public static boolean wildPrey(boolean named, boolean tame, boolean owned, boolean husbanded) {
        return !named && !tame && !owned && !husbanded;
    }
}
