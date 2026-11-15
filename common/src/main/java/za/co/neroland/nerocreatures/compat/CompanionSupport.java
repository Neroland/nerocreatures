package za.co.neroland.nerocreatures.compat;

import java.util.UUID;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.TamableAnimal;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.data.CreatureOwnershipState;
import za.co.neroland.nerocreatures.data.OwnedCreature;
import za.co.neroland.nerocreatures.entity.tame.TameableCreature;

/**
 * NeroCreatures' side of the companion SPI's questions and notices (the default bodies of
 * {@link CompanionPetHooks#isSmall}, {@link CompanionPetHooks#canFetch},
 * {@link CompanionPetHooks#onPetPerched}, {@link CompanionPetHooks#onPetParking} and
 * {@link CompanionPetHooks#onPetUnparked}). Server thread only; nothing here logs player identity.
 */
public final class CompanionSupport {

    /** Lowest goal priority a companion goal may use (below float 0, sit 1, attack 2). */
    public static final int MIN_GOAL_PRIORITY = 3;

    /** Highest goal priority a companion goal may use (above stroll 7 and look 8). */
    public static final int MAX_GOAL_PRIORITY = 6;

    /** Species that may perch on the owner's shoulder. */
    public static final TagKey<EntityType<?>> PERCH = TagKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "companion_perch"));

    /** Species that may fetch for the owner. */
    public static final TagKey<EntityType<?>> FETCH = TagKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "companion_fetch"));

    private CompanionSupport() {
    }

    // --- pure rules (tested) --------------------------------------------------------------------

    /** Perch and fetch need a tamed adult of an opted-in species. */
    public static boolean optedIn(boolean nerocreaturesPet, boolean tame, boolean baby, boolean tagged) {
        return nerocreaturesPet && tame && !baby && tagged;
    }

    /** Whether a companion goal's priority is inside the allowed band. */
    public static boolean allowedPriority(int priority) {
        return priority >= MIN_GOAL_PRIORITY && priority <= MAX_GOAL_PRIORITY;
    }

    /** Parking is refused for a wild pet, a ridden or riding one, and one in or just out of a fight. */
    public static boolean mayPark(boolean tame, boolean hasOwner, boolean ridden, boolean inCombat) {
        return tame && hasOwner && !ridden && !inCombat;
    }

    // --- SPI default bodies ---------------------------------------------------------------------

    static boolean isSmall(TamableAnimal pet) {
        return optedIn(pet instanceof TameableCreature, pet.isTame(), pet.isBaby(), pet.getType().is(PERCH));
    }

    static boolean canFetch(TamableAnimal pet) {
        return optedIn(pet instanceof TameableCreature, pet.isTame(), pet.isBaby(), pet.getType().is(FETCH));
    }

    static void perched(TamableAnimal pet, boolean perched) {
        if (pet instanceof TameableCreature creature && !pet.level().isClientSide()) {
            creature.setPerched(perched && isSmall(pet));
        }
    }

    static boolean parking(TamableAnimal pet) {
        if (!(pet instanceof TameableCreature creature) || !(pet.level() instanceof ServerLevel level)) {
            return false;
        }
        UUID owner = creature.ownerId();
        boolean inCombat = pet.getTarget() != null || pet.getLastHurtByMob() != null;
        if (!mayPark(pet.isTame(), owner != null, pet.isVehicle() || pet.isPassenger(), inCombat)) {
            return false;
        }
        CreatureOwnershipState state = CreatureOwnershipState.get(level.getServer());
        switch (state.loadVerdict(owner, pet.getUUID())) {
            case INDEXED -> state.setParked(owner, pet.getUUID(), true, null);
            // Index gap: the entity wins (as on load), and the re-added row is parked straight away.
            case REINDEX -> state.add(owner, OwnedCreature.of(pet, creature.ownershipKind()).withParked(true, null));
            // Erasure already recorded this pet or its owner: it is not anyone's to park.
            case RELEASE -> {
                return false;
            }
        }
        creature.setPerched(false);
        return true;
    }

    static void unparked(TamableAnimal pet) {
        if (!(pet instanceof TameableCreature creature) || !(pet.level() instanceof ServerLevel level)
                || !pet.isTame()) {
            return;
        }
        UUID owner = creature.ownerId();
        if (owner != null) {
            CreatureOwnershipState.get(level.getServer()).setParked(owner, pet.getUUID(), false,
                    level.dimension().identifier());
        }
    }
}
