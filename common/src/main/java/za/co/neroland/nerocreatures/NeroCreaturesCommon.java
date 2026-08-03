package za.co.neroland.nerocreatures;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.data.CreatureData;
import za.co.neroland.nerocreatures.network.CreatureNetwork;
import za.co.neroland.nerocreatures.registry.ModItems;
import za.co.neroland.nerocreatures.spawn.CreatureSpawns;
import za.co.neroland.nerocreatures.telemetry.NeroCreaturesTelemetry;

/**
 * Loader-agnostic entry point for NeroCreatures. Each loader entry point
 * (Fabric / Forge / NeoForge) calls {@link #init()} once during mod construction.
 *
 * <p>The ordering below is not cosmetic. Fabric registers <em>eagerly</em> — the moment a registry
 * class is touched — so anything that must exist before something else has to be listed before it,
 * on every loader, whether or not that loader would have cared.
 */
public final class NeroCreaturesCommon {

    public static final String MOD_ID = "nerocreatures";
    public static final Logger LOGGER = LoggerFactory.getLogger("NeroCreatures");

    private NeroCreaturesCommon() {
    }

    /** Called once per loader during mod construction. */
    public static void init() {
        LOGGER.info("[NeroCreatures] common init");
        // 1. Config first: everything below reads it, including telemetry's opt-out flag.
        NeroCreaturesConfig.init();
        // 2. Anonymous, NeroCreatures-only crash reporting. Must follow the config registration and
        //    precede the rest of init so early failures are still reported. Inert until a real
        //    Sentry DSN is configured (see NeroCreaturesTelemetry).
        NeroCreaturesTelemetry.init();
        // 3. Content registration. On Fabric this registers eagerly here; on NeoForge/Forge it only
        //    builds the DeferredRegisters, which each of those entry points then attaches to its mod
        //    event bus with RegistrationProvider.attach(...).
        ModItems.init();
        // 4. The drops join Neroland Core's shared creative tab — no NeroCreatures tab of its own.
        //    Core's tab reads its contents lazily when displayed, so contributing after Core has
        //    already built the tab is fine.
        ModItems.addToCreativeTab();
        // 5. Player-data erasure registration. Registered before any store exists on purpose:
        //    registering late is how an erasure request silently misses a store (POPIA/GDPR).
        CreatureData.init();
        // 6. Declare the payloads before any loader registers them: every loader entry point runs
        //    this method first, then wires its own networking.
        CreatureNetwork.init();
        // 7. Spawn rules last of the content steps — they reference entity types, which on Fabric
        //    must already have been registered by the step above, and Core's entity seam applies
        //    Fabric registrations eagerly too.
        CreatureSpawns.init();
        // 8. (Later) the NeroLink module registers NeroCreatures' read/write/live surfaces with
        //    Core's link registry. It goes last so a companion client is never told about something
        //    before the mod itself has finished reacting to it.
    }
}
