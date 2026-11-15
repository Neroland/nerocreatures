package za.co.neroland.nerocreatures.world;

import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerolandcore.config.ConfigValue;

import za.co.neroland.nerocreatures.platform.Services;

/**
 * The only way creature code may change the world: set or replace a block (or its state), pick up
 * or move a ground item it does not own, or spawn a helper entity from world state. A reviewer
 * greps for {@code setBlock}, {@code growCrop}, {@code randomTick} and item pickups outside this
 * class.
 *
 * <pre>{@code
 * if (CreatureGriefing.mayAlter(level, this, pos, NeroCreaturesConfig.MAGMA_WADER_SLAG,
 *         CreatureGriefing.Change.PLACE, slagState)) {
 *     level.setBlock(pos, slagState, Block.UPDATE_ALL);
 * } else {
 *     // the ability's non-world-changing fallback
 * }
 * }</pre>
 *
 * <p>Every check must pass, in this order (each is consulted only if the ones before passed):
 * <ol>
 *   <li>the ability's own config key is {@code true};</li>
 *   <li>the loader's mob-griefing hook allows it ({@link
 *       za.co.neroland.nerocreatures.platform.PlatformInfo#canMobGrief}: NeoForge/Forge fire their
 *       mob-griefing event, which defaults to the {@code mobGriefing} game rule; Fabric reads the
 *       game rule);</li>
 *   <li>{@code pos} is loaded and inside the world border. Never loads a chunk. This runs before the
 *       place event because firing that event on an unloaded position would load it;</li>
 *   <li>for {@link Change#PLACE} and {@link Change#REPLACE}: the loader's block-place event is not
 *       cancelled ({@link za.co.neroland.nerocreatures.platform.PlatformInfo#mayPlace}; what claim
 *       mods listen to on NeoForge and Forge; always true on Fabric);</li>
 *   <li>an <b>owned</b> actor (owner, or a player holding its leash) acts only as its owner could in
 *       person: the owner must be online in the same level, {@code mayBuild()}, pass
 *       {@code level.mayInteract(owner, pos)} and not be under spawn protection there. With the
 *       owner offline, owned world changes pause.</li>
 * </ol>
 *
 * <p>A denial never throws and logs nothing; the caller runs its fallback. Fabric gap: a claim mod
 * that does not hook the {@code mobGriefing} rule is not consulted for <em>wild</em> creatures;
 * owned ones are still bounded by their owner's own permissions (check 5).
 *
 * <p>Server thread only.
 */
public final class CreatureGriefing {

    /** What kind of world change an ability makes. */
    public enum Change {
        /** A block appears where there was none (air, or a replaceable). */
        PLACE,
        /** An existing block or block state is replaced or advanced (freezing, crop growth). */
        REPLACE,
        /** A ground item the creature does not own is picked up, moved or consumed. */
        ITEM,
        /** A helper entity is spawned from world state. */
        ENTITY;

        /** Whether this change fires the loader's block-place check. */
        public boolean placesBlock() {
            return this == PLACE || this == REPLACE;
        }
    }

    /**
     * The five checks, as a seam so the decision order can be tested without a world. Each method
     * is called at most once per decision and only when every earlier check passed.
     */
    public interface Checks {
        boolean keyEnabled();

        boolean canMobGrief();

        boolean loadedAndInBorder();

        boolean mayPlace();

        boolean ownerPermits();
    }

    private CreatureGriefing() {
    }

    /** {@link #mayAlter(ServerLevel, Mob, BlockPos, ConfigValue, Change, BlockState)} with no new state. */
    public static boolean mayAlter(ServerLevel level, Mob actor, BlockPos pos, ConfigValue<Boolean> key,
            Change change) {
        return mayAlter(level, actor, pos, key, change, null);
    }

    /**
     * Whether {@code actor} may make {@code change} at {@code pos}.
     *
     * @param newState the block state about to be placed, for the loader's place event; may be null
     */
    public static boolean mayAlter(ServerLevel level, Mob actor, BlockPos pos, ConfigValue<Boolean> key,
            Change change, @Nullable BlockState newState) {
        if (level == null || actor == null || pos == null || key == null || change == null) {
            return false;
        }
        return decide(change, new Checks() {
            @Override
            public boolean keyEnabled() {
                return Boolean.TRUE.equals(key.get());
            }

            @Override
            public boolean canMobGrief() {
                return Services.PLATFORM.canMobGrief(level, actor);
            }

            @Override
            public boolean loadedAndInBorder() {
                return level.isLoaded(pos) && level.getWorldBorder().isWithinBounds(pos);
            }

            @Override
            public boolean mayPlace() {
                return Services.PLATFORM.mayPlace(level, actor, pos, newState);
            }

            @Override
            public boolean ownerPermits() {
                return ownerMayAlter(level, actor, pos);
            }
        });
    }

    /** The decision itself. Public so tests (and the Stage 2 denied-griefing tests) can drive it. */
    public static boolean decide(Change change, Checks checks) {
        if (!checks.keyEnabled()) {
            return false;
        }
        if (!checks.canMobGrief()) {
            return false;
        }
        if (!checks.loadedAndInBorder()) {
            return false;
        }
        if (change.placesBlock() && !checks.mayPlace()) {
            return false;
        }
        return checks.ownerPermits();
    }

    /** Check 5: a wild actor passes; an owned one needs its owner online here and permitted. */
    private static boolean ownerMayAlter(ServerLevel level, Mob actor, BlockPos pos) {
        UUID controller = CreatureOwners.controllerUuid(actor);
        if (controller == null) {
            return true;
        }
        ServerPlayer owner = CreatureOwners.onlineController(level, actor);
        if (owner == null) {
            return false; // owner offline or elsewhere: owned world changes pause
        }
        MinecraftServer server = level.getServer();
        return owner.mayBuild()
                && level.mayInteract(owner, pos)
                && (server == null || !server.isUnderSpawnProtection(level, pos, owner));
    }

    /**
     * The {@code mobGriefing} game rule, read through the one accessor that knows the 1.21.11
     * game-rule rewrite. The default for {@code PlatformInfo#canMobGrief}.
     */
    public static boolean mobGriefingRule(ServerLevel level) {
        //? if >=1.21.11 {
        return Boolean.TRUE.equals(level.getGameRules().get(net.minecraft.world.level.gamerules.GameRules.MOB_GRIEFING));
        //?} else {
        /*return level.getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_MOBGRIEFING);
        *///?}
    }
}
