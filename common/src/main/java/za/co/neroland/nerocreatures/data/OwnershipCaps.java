package za.co.neroland.nerocreatures.data;

import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;

/**
 * The per-player, per-kind caps on {@link CreatureOwnershipState} rows, all server-authoritative
 * config keys (decision 004):
 *
 * <ul>
 *   <li>{@link OwnedCreature.Kind#PET}: {@code maxPetsPerPlayer} (default 3)</li>
 *   <li>{@link OwnedCreature.Kind#DRONE}: {@code maxDronesPerPlayer} (default 2)</li>
 *   <li>{@link OwnedCreature.Kind#MOUNT}: {@code maxMountsPerPlayer} (default {@value #DEFAULT_MAX_MOUNTS},
 *       0-16). Mounts never count against the pet cap.</li>
 *   <li>{@link OwnedCreature.Kind#EGG}: {@code maxIncubatingEggsPerPlayer} (default
 *       {@value #DEFAULT_MAX_EGGS}, 0-64)</li>
 * </ul>
 *
 * <p>The store's own {@code MAX_ROWS_PER_PLAYER} (256) stays the hard ceiling above all of them.
 */
public final class OwnershipCaps {

    public static final int DEFAULT_MAX_MOUNTS = 2;
    public static final int DEFAULT_MAX_EGGS = 8;

    private OwnershipCaps() {
    }

    /** The current cap for one kind. 0 refuses every new row of that kind. */
    public static int capFor(OwnedCreature.Kind kind) {
        return switch (kind) {
            case PET -> NeroCreaturesConfig.MAX_PETS_PER_PLAYER.get();
            case DRONE -> NeroCreaturesConfig.MAX_DRONES_PER_PLAYER.get();
            case MOUNT -> NeroCreaturesConfig.MAX_MOUNTS_PER_PLAYER.get();
            case EGG -> NeroCreaturesConfig.MAX_INCUBATING_EGGS_PER_PLAYER.get();
        };
    }
}
