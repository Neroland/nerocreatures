package za.co.neroland.nerocreatures.entity.ai.shared;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;

/**
 * Eat thrown items, digest them on a timer, produce an output.
 *
 * <ol>
 *   <li>Every {@code scanInterval} ticks, with room in the {@link DigestState}, it looks for the
 *       nearest {@link ItemEntity} within {@code radius} (bounded query) that the {@link Filter}
 *       accepts and the {@link Permission} allows, walks to it and swallows one item.</li>
 *   <li>On the same cadence it polls the stomach and hands each finished item to the
 *       {@link Output} callback (smelted ore plus a bonus roll, for the Slag Beetle).</li>
 * </ol>
 *
 * <p>{@link Permission} is where the griefing gate goes (decision 006: picking up a ground item goes
 * through {@code CreatureGriefing.mayAlter(..., Change.ITEM)}). There is deliberately no default
 * that skips it.
 */
public class ItemEatingGoal extends Goal {

    /** Which items are food (e.g. raw ores, and only those thrown by the owner). */
    @FunctionalInterface
    public interface Filter {
        boolean accepts(PathfinderMob eater, ItemEntity item);
    }

    /** The world-change gate for taking a ground item. */
    @FunctionalInterface
    public interface Permission {
        boolean mayTake(ServerLevel level, PathfinderMob eater, ItemEntity item);
    }

    /** What a digested item becomes. Drops, effects and bonus rolls happen here. */
    @FunctionalInterface
    public interface Output {
        void produce(ServerLevel level, PathfinderMob eater, Item eaten);
    }

    private static final int MAX_CANDIDATES = 6;
    private static final double REACH_SQR = 2.25D;

    private final PathfinderMob mob;
    private final DigestState stomach;
    private final double radius;
    private final double speed;
    private final Filter filter;
    private final Permission permission;
    private final Output output;
    private final CadenceTimer cadence;

    private ItemEntity food;
    private int chaseTicks;

    /**
     * @param mob          the eater
     * @param stomach      capacity and digestion time; the creature persists it
     * @param radius       search radius in blocks (6-8)
     * @param scanInterval ticks between searches and digestion polls (20)
     * @param speed        walk speed modifier
     */
    public ItemEatingGoal(PathfinderMob mob, DigestState stomach, double radius, int scanInterval, double speed,
            Filter filter, Permission permission, Output output) {
        this.mob = mob;
        this.stomach = stomach;
        this.radius = radius;
        this.speed = speed;
        this.filter = filter;
        this.permission = permission;
        this.output = output;
        this.cadence = new CadenceTimer(scanInterval);
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    /** True if {@code item} was thrown by the entity with {@code thrower} (while it is loaded). */
    public static boolean thrownBy(ItemEntity item, UUID thrower) {
        Entity owner = item.getOwner();
        return thrower != null && owner != null && thrower.equals(owner.getUUID());
    }

    @Override
    public boolean canUse() {
        if (!(this.mob.level() instanceof ServerLevel level)
                || !this.cadence.ready(level.getGameTime(), this.mob.getId())) {
            return false;
        }
        this.tickDigestion(level);
        if (!this.stomach.hasRoom()) {
            return false;
        }
        AABB box = this.mob.getBoundingBox().inflate(this.radius, 2.0D, this.radius);
        List<ItemEntity> found = new ArrayList<>(MAX_CANDIDATES);
        level.getEntities(EntityTypeTest.forClass(ItemEntity.class), box,
                e -> e.isAlive() && !e.getItem().isEmpty() && this.filter.accepts(this.mob, e), found, MAX_CANDIDATES);
        ItemEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (ItemEntity candidate : found) {
            double d = this.mob.distanceToSqr(candidate);
            if (d < bestDist && this.permission.mayTake(level, this.mob, candidate)) {
                best = candidate;
                bestDist = d;
            }
        }
        this.food = best;
        return best != null;
    }

    /**
     * Hands finished items to the output. Called from {@link #canUse()}; also call it from the
     * creature's server AI step on a cadence when a higher-priority MOVE goal (follow owner) can
     * keep this goal from being evaluated.
     */
    public void tickDigestion(ServerLevel level) {
        for (String id : this.stomach.pollReady(level.getGameTime())) {
            Identifier key = Identifier.tryParse(id);
            if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
                continue;
            }
            this.output.produce(level, this.mob, BuiltInRegistries.ITEM.getValue(key));
        }
    }

    @Override
    public void start() {
        this.chaseTicks = 0;
        this.mob.getNavigation().moveTo(this.food, this.speed);
    }

    @Override
    public boolean canContinueToUse() {
        return this.food != null && this.food.isAlive() && this.chaseTicks < 200 && this.stomach.hasRoom();
    }

    @Override
    public void tick() {
        this.chaseTicks++;
        if (this.food == null || !(this.mob.level() instanceof ServerLevel level)) {
            return;
        }
        if (this.mob.distanceToSqr(this.food) > REACH_SQR) {
            if (this.chaseTicks % 10 == 0) {
                this.mob.getNavigation().moveTo(this.food, this.speed);
            }
            return;
        }
        if (this.food.hasPickUpDelay() || !this.permission.mayTake(level, this.mob, this.food)) {
            return;
        }
        ItemStack stack = this.food.getItem();
        String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (this.stomach.eat(id, level.getGameTime())) {
            ItemStack rest = stack.copy();
            rest.shrink(1);
            if (rest.isEmpty()) {
                this.food.discard();
            } else {
                this.food.setItem(rest);
            }
        }
        this.food = null;
    }

    @Override
    public void stop() {
        this.food = null;
        this.mob.getNavigation().stop();
    }

    public DigestState stomach() {
        return this.stomach;
    }
}
