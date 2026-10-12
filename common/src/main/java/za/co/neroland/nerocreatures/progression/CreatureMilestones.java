package za.co.neroland.nerocreatures.progression;

import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;

import za.co.neroland.nerolandcore.progression.ProgressionGates;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/**
 * NeroCreatures' personal milestone records: player-scope Neroland Core gates
 * ({@code data/nerocreatures/neroland_gates/*.json}) opened for a player the first time something
 * notable happens — a pet is tamed, a Terraforming Drone is deployed. They are <b>records</b>, not
 * locks: nothing in NeroCreatures checks them for access, and a quest pack reads them with a
 * read-only {@code gate_open} objective. Re-opening an open gate is a no-op in Core.
 *
 * <p><b>Privacy (POPIA/GDPR):</b> the only player-keyed input is the acting player, who is online
 * and holding the item at that moment. The UUID goes only into
 * {@link ProgressionGates#open(ServerPlayer, Identifier)}; it is never logged or broadcast. The gate
 * rows live in Core's progression store and are erased through Core's shared erasure hook.
 */
public final class CreatureMilestones {

    /** The player tamed their first pet (any tameable species). */
    public static final Identifier FIRST_TAME = gate("first_tame");
    /** The player deployed their first Terraforming Drone from a drone shell. */
    public static final Identifier FIRST_DEPLOY = gate("first_deploy");

    private CreatureMilestones() {
    }

    private static Identifier gate(String path) {
        return Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, path);
    }

    /**
     * Records {@code gate} for {@code player}. Does nothing for a client-side or non-server player.
     *
     * @return {@code true} if the gate was newly opened
     */
    public static boolean record(Player player, Identifier gate) {
        if (player instanceof ServerPlayer serverPlayer) {
            return ProgressionGates.open(serverPlayer, gate);
        }
        return false;
    }
}
