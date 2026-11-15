package za.co.neroland.nerocreatures.entity.wrecks;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import net.minecraft.world.item.ItemStack;

/**
 * The Static Swarm's item-energy façade.
 *
 * <p>Neroland Core has no item-energy API (audit 8.5): it exposes block-side storages only. So the
 * swarm drains only stacks in {@code #nerocreatures:chargeable}, and the actual drain goes through this
 * class:
 *
 * <ol>
 *   <li><b>Built in:</b> a {@code nerocreatures:plasma_cell} is a charged cell, so one cell per unit is
 *       consumed ({@link SwarmRules#builtInDrain}).</li>
 *   <li><b>Handlers:</b> a mod (or a later Core) that knows how to pull charge out of its own items
 *       registers a {@link Handler} during common init. Each handler call is isolated: a
 *       {@code RuntimeException} or {@code LinkageError} (a handler built against an absent class)
 *       is swallowed and that handler is dropped, so a broken integration can never crash a swarm.</li>
 *   <li><b>Otherwise:</b> a documented no-op. A chargeable stack nothing understands loses nothing.</li>
 * </ol>
 *
 * <p>Server thread only. No player data passes through here; handlers see the stack only.
 */
public final class ChargeDrain {

    /** Pulls up to {@code units} of charge out of a stack; returns how many it pulled (0 = not mine). */
    @FunctionalInterface
    public interface Handler {
        int drain(ItemStack stack, int units);
    }

    private static final List<Handler> HANDLERS = new CopyOnWriteArrayList<>();

    private ChargeDrain() {
    }

    /** Registers an item-energy handler. Call during common init. */
    public static void register(Handler handler) {
        if (handler != null) {
            HANDLERS.add(handler);
        }
    }

    /** Number of registered handlers (tests and the budget report). */
    public static int handlerCount() {
        return HANDLERS.size();
    }

    /** Removes every handler (tests only). */
    static void clearForTests() {
        HANDLERS.clear();
    }

    /**
     * Drains up to {@code units} from {@code stack} (which the caller already checked is chargeable).
     *
     * @param isPlasmaCell whether the stack is the built-in charged cell
     * @return units drained, 0 when nothing could be drained
     */
    public static int drain(ItemStack stack, boolean isPlasmaCell, int units) {
        if (stack.isEmpty() || units <= 0) {
            return 0;
        }
        int builtIn = SwarmRules.builtInDrain(isPlasmaCell, stack.getCount(), units);
        if (builtIn > 0) {
            stack.shrink(builtIn);
            return builtIn;
        }
        for (Handler handler : HANDLERS) {
            int drained;
            try {
                drained = handler.drain(stack, units);
            } catch (RuntimeException | LinkageError broken) {
                // An integration built against a class that is not here, or one that throws: drop it.
                HANDLERS.remove(handler);
                continue;
            }
            if (drained > 0) {
                return Math.min(drained, units);
            }
        }
        return 0;
    }
}
