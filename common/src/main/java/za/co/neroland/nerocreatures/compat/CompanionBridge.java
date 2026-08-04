package za.co.neroland.nerocreatures.compat;

import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

import za.co.neroland.nerolandcore.platform.Services;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/**
 * The NeroCompanion hand-off seam: the single place NeroCreatures asks "is something else driving
 * this pet?".
 *
 * <p>Everything about it is deliberately boring, because the interesting failure modes of cross-mod
 * integration are all avoided by <em>not</em> doing anything clever:
 *
 * <ul>
 *   <li><b>No reflection.</b> Nothing here names a NeroCompanion class, loads one by name, or pokes
 *       at one's fields. The contract is {@link CompanionPetHooks}, declared in this mod, and a
 *       companion mod ships an implementation plus a {@code META-INF/services} entry — the same
 *       mechanism the loader seams already use.</li>
 *   <li><b>Absence is the normal case.</b> With no companion mod installed the lookup finds nothing
 *       and every call lands on the no-op default. There is no error path, no log line and no
 *       behaviour change.</li>
 *   <li><b>Resolved once, eagerly enough.</b> The lookup happens on first use and is cached, and any
 *       {@link ServiceConfigurationError} or runtime failure from a third-party implementation is
 *       swallowed into the no-op — a broken companion mod must not be able to break taming.</li>
 * </ul>
 *
 * <p>The {@code isModLoaded} check is a cheap pre-filter, not the actual mechanism: an
 * implementation is only ever used if it is both declared as a service <em>and</em> the companion
 * mod is present, so an unrelated jar cannot quietly take over the ecosystem's pets.
 *
 * <p>Where the line falls between the two mods is written down in {@link CompanionPetHooks} and, for
 * players, in {@code wiki/Pets-and-Drones.md}.
 */
public final class CompanionBridge {

    /** The companion mod's id. Referenced as a string only — never as a class. */
    public static final String COMPANION_MOD_ID = "nerocompanion";

    /** What every call falls back to: nothing happens, and NeroCreatures keeps doing its own job. */
    private static final CompanionPetHooks NO_OP = new CompanionPetHooks() {
    };

    private static volatile CompanionPetHooks resolved;

    private CompanionBridge() {
    }

    /** Whether a companion mod is installed at all. Cheap; safe to call from a tick path. */
    public static boolean companionPresent() {
        try {
            return Services.PLATFORM.isModLoaded(COMPANION_MOD_ID);
        } catch (RuntimeException e) {
            return false;
        }
    }

    /**
     * The active hooks — a real implementation when a companion mod supplies one, and the shared
     * no-op otherwise. Never null, never throws.
     */
    public static CompanionPetHooks hooks() {
        CompanionPetHooks current = resolved;
        if (current == null) {
            current = resolve();
            resolved = current;
        }
        return current;
    }

    private static CompanionPetHooks resolve() {
        if (!companionPresent()) {
            return NO_OP;
        }
        try {
            CompanionPetHooks found = ServiceLoader.load(CompanionPetHooks.class)
                    .findFirst()
                    .orElse(NO_OP);
            if (found != NO_OP) {
                // Mod id only — no player data, no file paths (POPIA/GDPR).
                NeroCreaturesCommon.LOGGER.info(
                        "[NeroCreatures] Companion pet hooks provided by an installed companion mod; "
                                + "deep pet behaviour deferred to it.");
            }
            return found;
        } catch (RuntimeException | ServiceConfigurationError e) {
            NeroCreaturesCommon.LOGGER.warn(
                    "[NeroCreatures] A companion pet-hook implementation could not be loaded; "
                            + "continuing with NeroCreatures' own behaviour.", e);
            return NO_OP;
        }
    }

    /**
     * Drops the cached lookup. Only useful for tests and for a loader that can hot-swap services;
     * gameplay code never needs it.
     */
    public static void reset() {
        resolved = null;
    }
}
