package za.co.neroland.nerocreatures.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.IntSupplier;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import za.co.neroland.nerocreatures.data.CreatureOwnershipState;
import za.co.neroland.nerocreatures.entity.tame.PetCommand;
import za.co.neroland.nerocreatures.entity.tame.TameableCreature;

/**
 * The creature side of the Nest ({@link NestRules}). The home lives in one entity tag on the creature
 * itself, so it saves, loads and is erased with the creature and never enters a player store.
 * {@link #tick} is called from {@code TameableCreature}'s server AI step and does work only every
 * {@value NestRules#CHECK_TICKS} ticks for a creature that has a nest.
 */
public final class NestHome {

    private static IntSupplier healIntervalTicks = () -> NestRules.DEFAULT_HEAL_INTERVAL_TICKS;

    private NestHome() {
    }

    /** Wires {@code nestHealIntervalTicks} (0 = no healing); once at common init. */
    public static void configure(IntSupplier healInterval) {
        if (healInterval != null) {
            healIntervalTicks = healInterval;
        }
    }

    /** The home a nest block at {@code pos} in {@code level} stands for. */
    public static NestRules.Home homeAt(ServerLevel level, BlockPos pos) {
        return new NestRules.Home(level.dimension().identifier().toString(), pos.getX(), pos.getY(), pos.getZ());
    }

    /** A creature's nest, if it has one. */
    public static Optional<NestRules.Home> home(Entity entity) {
        for (String tag : tagsOf(entity)) {
            if (tag.startsWith(NestRules.TAG_PREFIX)) {
                return NestRules.Home.decode(tag);
            }
        }
        return Optional.empty();
    }

    /** Homes a creature at a nest, replacing any earlier one. */
    public static void bind(Entity entity, NestRules.Home home) {
        clear(entity);
        entity.addTag(home.encode());
    }

    /** Forgets a creature's nest. */
    public static void clear(Entity entity) {
        List<String> stale = new ArrayList<>();
        for (String tag : tagsOf(entity)) {
            if (tag.startsWith(NestRules.TAG_PREFIX)) {
                stale.add(tag);
            }
        }
        for (String tag : stale) {
            entity.removeTag(tag);
        }
    }

    /** The entity's scoreboard-style tags (renamed in 26.1). */
    private static java.util.Set<String> tagsOf(Entity entity) {
        //? if >=26.1 {
        return entity.entityTags();
        //?} else {
        /*return entity.getTags();
        *///?}
    }

    /** Server AI step hook. Cheap for a creature with no nest (a tag scan every few seconds). */
    public static void tick(TameableCreature creature) {
        if (!(creature.level() instanceof ServerLevel level)
                || (creature.tickCount + creature.getId()) % NestRules.CHECK_TICKS != 0) {
            return;
        }
        Optional<NestRules.Home> found = home(creature);
        if (found.isEmpty()) {
            return;
        }
        NestRules.Home home = found.get();
        if (!home.dimension().equals(level.dimension().identifier().toString())) {
            return; // travelling with its owner; the nest waits
        }
        BlockPos nest = new BlockPos(home.x(), home.y(), home.z());
        // An unloaded nest is not a missing one: never load a chunk to check.
        boolean nestPresent = !level.isLoaded(nest) || level.getBlockState(nest).getBlock() instanceof NestBlock;
        UUID owner = creature.ownerId();
        boolean valid = creature.isTame() && owner != null && nestPresent
                && CreatureOwnershipState.get(level.getServer()).contains(owner, creature.getUUID());
        LivingEntity ownerEntity = creature.getOwner();
        boolean following = creature.command() == PetCommand.FOLLOW && ownerEntity != null
                && ownerEntity.level() == level;
        boolean sitting = creature.command() == PetCommand.SIT || creature.command() == PetCommand.STAY;
        double distance = Math.sqrt(creature.distanceToSqr(nest.getX() + 0.5D, nest.getY() + 0.5D, nest.getZ() + 0.5D));
        NestRules.Action action = NestRules.decide(valid, following, creature.isVehicle(), sitting, distance);
        switch (action) {
            case FORGET -> clear(creature);
            case RETURN -> creature.getNavigation().moveTo(nest.getX() + 0.5D, nest.getY() + 1.0D,
                    nest.getZ() + 0.5D, 1.0D);
            case REST -> {
                int interval = healIntervalTicks.getAsInt();
                if (interval > 0 && creature.getHealth() < creature.getMaxHealth()
                        && (creature.tickCount / NestRules.CHECK_TICKS) % Math.max(1, interval / NestRules.CHECK_TICKS) == 0) {
                    creature.heal(1.0F);
                }
            }
            default -> {
            }
        }
    }
}
