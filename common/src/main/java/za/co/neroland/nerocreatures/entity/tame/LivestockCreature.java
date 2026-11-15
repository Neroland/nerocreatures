package za.co.neroland.nerocreatures.entity.tame;

import org.jetbrains.annotations.Nullable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
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
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

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
 *   <li><b>Husbanded</b> (Stage 2 review M1, {@link HusbandryRules}): a player feed, breed, shear,
 *       milk, harvest, leash or name marks the animal. A husbanded animal never despawns, is never
 *       wild prey, and passes the mark to its babies. Saved; older saves are marked on load when the
 *       animal has an owner or a name.</li>
 * </ul>
 */
public abstract class LivestockCreature extends TameableCreature {

    private static final String HUSBANDED_KEY = "nerocreatures:husbanded";

    /** Kept or bred by a player (M1). Server state, saved. */
    private boolean husbanded;

    protected LivestockCreature(EntityType<? extends LivestockCreature> type, Level level, CreatureTier tier) {
        super(type, level, tier);
    }

    // --- husbandry (M1) -------------------------------------------------------------------

    /** Whether a player keeps or bred this animal. */
    public boolean isHusbanded() {
        return this.husbanded;
    }

    /**
     * Marks this animal as kept by a player: it stops being a despawnable wild spawn and stops being
     * prey. Also sets vanilla persistence, so every other "is this somebody's animal" check agrees.
     */
    public void markHusbanded() {
        if (!this.husbanded) {
            this.husbanded = true;
            this.setPersistenceRequired();
        }
    }

    @Override
    public boolean requiresCustomPersistence() {
        return super.requiresCustomPersistence() || this.husbanded;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayerSqr) {
        return HusbandryRules.mayDespawn(this.isPersistenceRequired(), this.isTame(), this.husbanded)
                && super.removeWhenFarAway(distanceToClosestPlayerSqr);
    }

    /** Feeding, breeding food and the species' own shear/milk/harvest (which call super) mark it. */
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        InteractionResult result = super.mobInteract(player, hand);
        if (HusbandryRules.marksOnInteraction(this.level() instanceof ServerLevel, result.consumesAction())) {
            this.markHusbanded();
        }
        return result;
    }

    /** A leash or a name tag marks it; checked on a cadence rather than per interaction path. */
    @Override
    public void tick() {
        super.tick();
        if (!this.husbanded && this.level() instanceof ServerLevel
                && (this.tickCount + this.getId()) % HusbandryRules.CHECK_INTERVAL_TICKS == 0
                && HusbandryRules.marksOnCheck(this.isLeashed(), this.hasCustomName())) {
            this.markHusbanded();
        }
    }

    /** Both parents were fed by a player; the baby inherits the mark. */
    @Override
    public void finalizeSpawnChildFromBreeding(ServerLevel level, Animal partner, @Nullable AgeableMob offspring) {
        boolean partnerKept = partner instanceof LivestockCreature other && other.isHusbanded();
        if (offspring instanceof LivestockCreature child
                && HusbandryRules.offspringHusbanded(this.husbanded, partnerKept)) {
            child.markHusbanded();
        }
        super.finalizeSpawnChildFromBreeding(level, partner, offspring);
    }

    //? if >=1.21.6 {
    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
    //?} else {
    /*@Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag nbt) {
        super.addAdditionalSaveData(nbt);
        ValueOutput output = ValueOutput.of(nbt, this.registryAccess());
    *///?}
        if (this.husbanded) {
            output.putBoolean(HUSBANDED_KEY, true);
        }
    }

    //? if >=1.21.6 {
    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
    //?} else {
    /*@Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag nbt) {
        super.readAdditionalSaveData(nbt);
        ValueInput input = ValueInput.of(nbt, this.registryAccess());
    *///?}
        // The custom name is read before this method; the owner reference by super.
        if (HusbandryRules.husbandedOnLoad(input.getBooleanOr(HUSBANDED_KEY, false), this.hasCustomName(),
                this.ownerId() != null)) {
            this.markHusbanded();
        }
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
