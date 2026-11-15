package za.co.neroland.nerocreatures.compat;

import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;

import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * The service NeroCompanion (or any other companion-behaviour mod) implements to take over the
 * <b>deep</b> half of pet behaviour from NeroCreatures.
 *
 * <p>This interface is the whole boundary, and it is declared <b>here</b> on purpose: NeroCreatures
 * publishes the contract, NeroCompanion implements it, and neither mod ever names a class from the
 * other. There is no reflection anywhere in this package — the implementation is found through
 * {@link java.util.ServiceLoader}, which simply finds nothing when the companion mod is absent.
 *
 * <h2>Who owns what</h2>
 *
 * <table border="1">
 *   <caption>Division of responsibility</caption>
 *   <tr><th>NeroCreatures (always)</th><th>NeroCompanion (if installed)</th></tr>
 *   <tr>
 *     <td>Taming, the ownership store and its caps, the sit/stay/follow command cycle, owner-only
 *         interaction, defending its owner, the species' one passive perk, loot and rendering.</td>
 *     <td>Everything deeper: personality, moods, levelling, named abilities, inventories, chat,
 *         cross-mod errands — none of which NeroCreatures will ever grow.</td>
 *   </tr>
 * </table>
 *
 * <p>Every method has a no-op (or "no, thanks") default, so an implementation only overrides what it
 * actually wants and this interface can grow without breaking anyone.
 *
 * <h2>Two directions (0.5.0 additions)</h2>
 *
 * <p>Most methods are <b>callbacks</b>: NeroCreatures calls them and the companion mod answers
 * ({@link #addCompanionGoals}, {@link #allowsRecovery}, {@link #hatFor}, {@link #onPetLoaded} and the
 * four originals). A few are <b>questions and notices to NeroCreatures</b>: the companion mod calls them
 * on its own implementation and the default body is NeroCreatures' answer ({@link #isSmall},
 * {@link #canFetch}, {@link #onPetPerched}, {@link #onPetParking}, {@link #onPetUnparked}). An
 * implementation should not override those; if it does, it must call
 * {@code CompanionPetHooks.super.<method>(pet)} so the ownership index and the per-species answer still
 * apply. NeroCreatures never calls them itself. Without a companion mod nothing calls them at all.
 *
 * <p>Which species are small (perch) and which fetch is data: the entity-type tags
 * {@code #nerocreatures:companion_perch} and {@code #nerocreatures:companion_fetch}.
 *
 * <p><b>Privacy (POPIA/GDPR):</b> these callbacks pass live game objects, never stored player data.
 * A companion mod that persists anything player-keyed is responsible for registering its own eraser
 * with Core's {@code PlayerDataErasure} hook — NeroCreatures erases only its own store.
 */
public interface CompanionPetHooks {

    /**
     * A pet has just been tamed and indexed against its owner.
     *
     * <p>Called on the server thread, after the ownership store has been updated, so an
     * implementation may safely read the pet's owner.
     */
    default void onPetTamed(TamableAnimal pet, Player owner) {
        // no-op
    }

    /**
     * The owner cycled their pet's command.
     *
     * @param command the new command's id — {@code "sit"}, {@code "guard"} or {@code "follow"}.
     *                A plain string keeps the SPI free of NeroCreatures-internal types.
     */
    default void onPetCommandChanged(TamableAnimal pet, String command) {
        // no-op
    }

    /**
     * A pet has stopped belonging to anyone — it died, or its owner's data was erased and it was
     * returned to the wild. An implementation must drop any state it kept for that pet.
     */
    default void onPetReleased(TamableAnimal pet) {
        // no-op
    }

    /**
     * Whether the companion mod is driving this pet's <b>idle</b> behaviour right now.
     *
     * <p>Returning {@code true} makes NeroCreatures stand down from the small things it does when a
     * pet has nothing else to do — currently its species perk — so the two mods never both act on
     * the same tick. It never stands down from taming, ownership, caps, the command cycle or
     * defending its owner: those are NeroCreatures' contract with the player and stay put.
     */
    default boolean overridesIdleBehaviour(TamableAnimal pet) {
        return false;
    }

    // ---- 1. Small / perch opt-in (asked of NeroCreatures) ---------------------------------------------

    /**
     * Whether NeroCreatures allows this pet to ride its owner's shoulder when the companion mod asks.
     * NeroCreatures answers per species ({@code #nerocreatures:companion_perch}: lumen_moth, magnet_crab,
     * glacite_wisp, slag_beetle; never a baby, never a wild one). While perched the companion mod pins
     * the pet's position each tick and calls {@link #onPetPerched}; NeroCreatures' own goals then treat
     * the pet as sitting.
     */
    default boolean isSmall(TamableAnimal pet) {
        return CompanionSupport.isSmall(pet);
    }

    /** The companion mod has put the pet on, or taken it off, the owner's shoulder. */
    default void onPetPerched(TamableAnimal pet, boolean perched) {
        CompanionSupport.perched(pet, perched);
    }

    // ---- 2. Fetch opt-in (asked of NeroCreatures) / companion goals (callback) ------------------------

    /**
     * Whether this pet may fetch a toy for its owner ({@code #nerocreatures:companion_fetch}: lumen_moth,
     * magnet_crab). When true, the companion mod adds its own fetch goal through
     * {@link #addCompanionGoals} and NeroCreatures does nothing else.
     */
    default boolean canFetch(TamableAnimal pet) {
        return CompanionSupport.canFetch(pet);
    }

    /**
     * Callback: add goals (fetch, play) to the pet's goal selector. Called once per loaded pet instance,
     * on its first server tick, for every tameable NeroCreatures species (tamed or not, so check
     * ownership in the goal). {@code addGoal} wraps {@code goalSelector.addGoal(priority, goal)} and only
     * accepts priorities {@value CompanionSupport#MIN_GOAL_PRIORITY}..{@value
     * CompanionSupport#MAX_GOAL_PRIORITY} (below float/sit/attack, above stroll); others are ignored.
     */
    default void addCompanionGoals(TamableAnimal pet, BiConsumer<Integer, Goal> addGoal) {
    }

    // ---- 3. Bay park / unpark (notices to NeroCreatures) ------------------------------------------------

    /**
     * The companion mod is about to remove the pet from the world to park it (it saves the pet's full
     * NBT itself). NeroCreatures keeps the ownership row (the pet still counts against its cap) and marks
     * it parked, so erasure and lost-pet logic do not look for it in its old dimension. Returns false to
     * refuse: not a tamed NeroCreatures pet, ridden, in combat or recently hurt.
     */
    default boolean onPetParking(TamableAnimal pet) {
        return CompanionSupport.parking(pet);
    }

    /**
     * The pet was re-created from parked NBT (same entity UUID) at a new place. NeroCreatures updates the
     * row's dimension and clears the parked mark. A row erasure removed meanwhile is not re-added: the
     * pet is released on its first tick instead.
     */
    default void onPetUnparked(TamableAnimal pet) {
        CompanionSupport.unparked(pet);
    }

    // ---- 4. Recovery-ledger registration (callback) ------------------------------------------------------

    /**
     * Whether the companion mod keeps a recovery record for this pet. When true, NeroCreatures' death
     * path still calls {@link #onPetReleased} and removes the row, but raises no companion-app death
     * event of its own (the companion mod raises one), so the owner gets one push.
     */
    default boolean allowsRecovery(TamableAnimal pet) {
        return false;
    }

    // ---- 5. Hats (client callback) -------------------------------------------------------------------------

    /**
     * Client: the hat to draw on this pet at NeroCreatures' head anchor, or empty for none. The companion
     * mod keeps and syncs the choice over its own payload.
     */
    default Optional<ItemStack> hatFor(TamableAnimal pet) {
        return Optional.empty();
    }

    // ---- 6. "Pet loaded" (callback) ----------------------------------------------------------------------

    /**
     * A tame pet was loaded into the world (chunk load, server start, dimension change, unpark). Called
     * once per loaded instance, server side, on its first tick, after its owner, command and the
     * load-time ownership check.
     */
    default void onPetLoaded(TamableAnimal pet, UUID owner) {
    }
}
