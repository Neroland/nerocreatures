package za.co.neroland.nerocreatures.entity.tame;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import za.co.neroland.nerocreatures.entity.base.CreatureTier;

/**
 * Root for breedable, untameable livestock (decision 003, option B): Lode Tortoise, Rime Yak,
 * Mossback, Prism Stag. It is a {@link TameableCreature} with taming switched off, so it inherits
 * vanilla age, love mode, breeding, leash and baby scaling, is counted by the census as the second
 * root, and still fits the companion SPI's {@code TamableAnimal} type.
 *
 * <ul>
 *   <li>{@link #isTameable()} is {@code false}: interaction never tames, never writes an owner and
 *       never adds an ownership row.</li>
 *   <li>{@link #breedable()} is {@code true}; food is the species tag
 *       {@code nerocreatures:food/<species>} only.</li>
 *   <li>Default goals are herbivore goals: panic, breed, tempt by food, follow parent, stroll.
 *       Species add their own (herd, product timer, predator/prey) on top.</li>
 * </ul>
 */
public abstract class LivestockCreature extends TameableCreature {

    protected LivestockCreature(EntityType<? extends LivestockCreature> type, Level level, CreatureTier tier) {
        super(type, level, tier);
    }

    @Override
    public final boolean isTameable() {
        return false;
    }

    @Override
    public boolean breedable() {
        return true;
    }

    /** Livestock are never tamed; their food is the species tag only. */
    @Override
    public final Item tamingFood() {
        return Items.AIR;
    }

    /** Livestock have no owner, so no owner perk. */
    @Override
    protected final void applyOwnerPerk(ServerLevel level, Player owner) {
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.25D));
        this.goalSelector.addGoal(2, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.1D, this::isSpeciesFood, false));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.1D));
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 6.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
    }
}
