package za.co.neroland.nerocreatures.platform;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerocreatures.world.CreatureGriefing;

/**
 * The loader-specific facts the common module is allowed to depend on.
 *
 * <p>Each loader module ships exactly one implementation, registered via a
 * {@code META-INF/services/za.co.neroland.nerocreatures.platform.PlatformInfo} file so
 * {@link Services} can load it with {@link java.util.ServiceLoader}. This is the
 * lightweight, dependency-free alternative to Architectury's {@code @ExpectPlatform},
 * and it keeps {@code common/} free of {@code net.neoforged.*} / {@code net.fabricmc.*} /
 * {@code net.minecraftforge.*} imports.
 *
 * <p>Deliberately minimal: it answers only what
 * {@link za.co.neroland.nerocreatures.telemetry.NeroCreaturesTelemetry} needs. Neroland Core's own
 * {@code IPlatformHelper} covers the wider surface (config dir, {@code isModLoaded}, capability
 * invalidation) but reports <em>Core's</em> mod version, so NeroCreatures needs its own seam to tag
 * crash reports with the NeroCreatures release. Everything exposed here is a public manifest
 * string — never personal data (POPIA/GDPR).
 */
public interface PlatformInfo {

    /** Human-readable platform name ("Fabric" / "Forge" / "NeoForge"). */
    String getPlatformName();

    /** True when running in a development (dev/data/test) environment. */
    boolean isDevelopmentEnvironment();

    /** True on the physical client (renderers, screens, HUD available). */
    boolean isClient();

    /** This mod's version string (a public manifest value — safe as a telemetry release tag), or "unknown". */
    String getModVersion();

    /**
     * The ids + versions of every loaded mod ("modid version"), sorted, for crash mod-conflict
     * triage. These are public manifest strings only — never personal data (POPIA/GDPR).
     */
    List<String> getLoadedModIds();

    /**
     * Whether {@code actor} may grief at all: the loader's mob-griefing hook (NeoForge
     * {@code EntityMobGriefingEvent}, Forge's equivalent), which itself defaults to the
     * {@code mobGriefing} game rule. The default (Fabric) reads the game rule directly. Only
     * {@link CreatureGriefing} calls this.
     */
    default boolean canMobGrief(ServerLevel level, Mob actor) {
        return CreatureGriefing.mobGriefingRule(level);
    }

    /**
     * The loader's "may this entity place a block here" event, which claim mods listen to (NeoForge
     * and Forge {@code EntityPlaceEvent}). {@code newState} is the state about to be placed, or
     * {@code null} when unknown. Default (Fabric, no standard event): {@code true}. Only {@link CreatureGriefing} calls this.
     */
    default boolean mayPlace(ServerLevel level, Mob actor, BlockPos pos, @Nullable BlockState newState) {
        return true;
    }
}
