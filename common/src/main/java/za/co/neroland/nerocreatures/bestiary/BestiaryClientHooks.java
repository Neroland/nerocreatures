package za.co.neroland.nerocreatures.bestiary;

import java.util.function.Consumer;

import za.co.neroland.nerocreatures.network.BestiaryCountsPayload;
import za.co.neroland.nerocreatures.network.BestiaryEventPayload;

/**
 * Seams from common code into the client-only Bestiary classes. Each loader's client setup fills
 * them in; on a dedicated server they stay no-ops, so common code (the Field Scanner item, the
 * clientbound payload handlers) never references a client class.
 */
public final class BestiaryClientHooks {

    /** Opens the Bestiary screen (Sneak + Use with the Field Scanner). */
    public static volatile Runnable openScreen = () -> { };

    /** A {@code bestiary_counts} payload arrived. */
    public static volatile Consumer<BestiaryCountsPayload> counts = payload -> { };

    /** A {@code bestiary_event} payload arrived. */
    public static volatile Consumer<BestiaryEventPayload> event = payload -> { };

    /** The player left a world: drop every client-side Bestiary state. */
    public static volatile Runnable clear = () -> { };

    private BestiaryClientHooks() {
    }
}
