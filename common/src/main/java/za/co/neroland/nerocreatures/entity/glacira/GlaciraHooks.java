package za.co.neroland.nerocreatures.entity.glacira;

import net.minecraft.server.level.ServerPlayer;

/**
 * Seams the Glacira creatures expose to other systems. All default to no-ops, so a Core-only server
 * is fully playable; a compat facade (for example a sibling mod's temperature or heat system) binds
 * one at common init.
 *
 * <ul>
 *   <li>{@link Cooling}: called on the server for every player inside a Rime Yak's cold aura, once
 *       per aura pass ({@value RimeYakRules#AURA_INTERVAL} ticks). Vanilla effect already applied:
 *       the player's fire is put out. Nothing about the player is stored or logged.</li>
 * </ul>
 */
public final class GlaciraHooks {

    /** A player stands in a Rime Yak's cold aura for the next {@code ticks} ticks. */
    @FunctionalInterface
    public interface Cooling {
        Cooling NONE = (player, ticks) -> {
        };

        void cool(ServerPlayer player, int ticks);
    }

    private static volatile Cooling cooling = Cooling.NONE;

    private GlaciraHooks() {
    }

    /** Binds the cooling hook; {@code null} restores the no-op. */
    public static void bindCooling(Cooling hook) {
        cooling = hook == null ? Cooling.NONE : hook;
    }

    public static Cooling cooling() {
        return cooling;
    }
}
