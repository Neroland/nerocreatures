package za.co.neroland.nerocreatures.compat;

import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;

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
}
