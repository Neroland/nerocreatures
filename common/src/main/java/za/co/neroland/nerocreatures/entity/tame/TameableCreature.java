package za.co.neroland.nerocreatures.entity.tame;

import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.compat.CompanionBridge;
import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.data.CreatureOwnershipState;
import za.co.neroland.nerocreatures.data.OwnedCreature;
import za.co.neroland.nerocreatures.entity.ai.HoldAreaGoal;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;
import za.co.neroland.nerocreatures.link.CreatureLinkEvents;

/**
 * Shared base for NeroCreatures' tameable alien pets — everything the two species have in common,
 * which is nearly everything.
 *
 * <h2>Why this extends vanilla {@code TamableAnimal} rather than the mod's own base</h2>
 *
 * <p>The rest of the roster derives from {@link NeroCreatureEntity}, but a tameable cannot: vanilla
 * puts owner storage, the sitting flag, the taming particles and four owner-aware goals
 * ({@code SitWhenOrderedToGoal}, {@code FollowOwnerGoal}, {@code OwnerHurtByTargetGoal},
 * {@code OwnerHurtTargetGoal}) on {@link TamableAnimal}, and those goals take a {@code TamableAnimal}
 * by type. Reimplementing all of it on a {@code PathfinderMob} to preserve one class hierarchy would
 * be a large pile of code whose only merit is symmetry. So pets sit on vanilla's tree and borrow the
 * two pieces of NeroCreatures' base that actually matter — {@linkplain
 * NeroCreatureEntity#createCreatureAttributes(CreatureTier) tier attributes} (called directly) and
 * population accounting ({@code spawn/CreatureCensus} counts this class as a second root, so a wild
 * pet is inside the spawn caps exactly like every other creature).
 *
 * <h2>The player-facing contract</h2>
 *
 * <ul>
 *   <li><b>Taming</b> — feed an untamed pet its species' reagent (see {@link #tamingFood()}). The
 *       roll is one in {@value #TAME_CHANCE_DENOMINATOR}, vanilla-wolf style, and the reagent is
 *       consumed either way.</li>
 *   <li><b>The cap is checked at tame time</b>, server-side, against
 *       {@link CreatureOwnershipState} and {@code maxPetsPerPlayer}. A player at their cap is told
 *       so on the action bar and loses nothing — no reagent, no pet, no shouting in chat.</li>
 *   <li><b>Commands</b> — shift-interact your own pet to cycle {@link PetCommand}: sit, stay,
 *       follow.</li>
 *   <li><b>Owner-only.</b> Anyone else interacting with a tamed pet gets
 *       {@link InteractionResult#PASS} — their click falls through to whatever they were actually
 *       trying to do, which is friendlier than swallowing it.</li>
 *   <li><b>Healing</b> — feeding a tamed, injured pet its reagent heals it.</li>
 *   <li><b>One passive perk</b> per species, applied to the owner when they are close by. Perks are
 *       deliberately comfort-tier and never combat stats — see {@link #applyOwnerPerk}.</li>
 * </ul>
 *
 * <p><b>Privacy (POPIA/GDPR).</b> A tamed animal's owner UUID lives in its own entity data; that is
 * vanilla's design and is inherent to being tameable, and it is declared in {@code PRIVACY.md}.
 * NeroCreatures adds no further player data to the entity — no names, no coordinates, no history —
 * and its own index ({@link CreatureOwnershipState}) is erasable and export-able. Nothing on this
 * class logs player identity.
 */
public abstract class TameableCreature extends TamableAnimal {

    /** One in this many feeds tames — the vanilla wolf's odds. */
    private static final int TAME_CHANCE_DENOMINATOR = 3;

    /** How much a feed heals a tamed pet, in half-hearts. */
    private static final float FEED_HEAL_AMOUNT = 4.0F;

    /** Ticks between species-perk checks. Two seconds: a comfort perk does not need to be prompt. */
    private static final int PERK_INTERVAL_TICKS = 40;

    /** How close the owner must be for the perk to apply, in blocks. */
    private static final double PERK_RADIUS = 8.0D;

    /** How far a guarding pet may stray from its post before it walks back, in blocks. */
    private static final double GUARD_RADIUS = 8.0D;

    private final CreatureTier tier;

    private PetCommand command = PetCommand.FOLLOW;

    /** Where a {@code GUARD} order was issued. Not saved — a reloaded guard re-anchors where it is. */
    @Nullable
    private BlockPos guardAnchor;

    private int perkCooldown;

    protected TameableCreature(EntityType<? extends TameableCreature> type, Level level, CreatureTier tier) {
        super(type, level);
        this.tier = tier;
        this.xpReward = tier.experienceReward();
    }

    /** The power band this pet was built for. */
    public final CreatureTier tier() {
        return this.tier;
    }

    /**
     * The item that tames — and afterwards feeds — this species. One item, not a tag: the two
     * reagents are specific creature drops and the Drop Map is where that contract is written down.
     */
    public abstract Item tamingFood();

    /**
     * The species' one passive perk, applied to the owner when they are within
     * {@value #PERK_RADIUS} blocks. Called at most once every {@value #PERK_INTERVAL_TICKS} ticks,
     * on the server, and only while the pet is tamed.
     *
     * <p>Implementations must stay in comfort territory — no damage, no armour, no speed, nothing
     * that turns a pet into a combat stat. If a companion mod is driving this pet's idle behaviour
     * ({@link CompanionBridge}) this is not called at all.
     */
    protected abstract void applyOwnerPerk(ServerLevel level, Player owner);

    /** The standard attribute block for a pet tier. Pets are built from the shared bands. */
    public static AttributeSupplier.Builder createPetAttributes(CreatureTier tier) {
        return NeroCreatureEntity.createCreatureAttributes(tier);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, true));
        this.goalSelector.addGoal(5, new CommandedFollowOwnerGoal(this, 1.1D, 9.0F, 3.0F));
        this.goalSelector.addGoal(6, new HoldAreaGoal(this, () -> this.guardAnchor, GUARD_RADIUS, 1.0D,
                () -> this.command == PetCommand.GUARD));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        // Defence, and only defence: a pet never picks a fight of its own.
        this.targetSelector.addGoal(1, new OwnerHurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new OwnerHurtTargetGoal(this));
        this.targetSelector.addGoal(3, new HurtByTargetGoal(this));
    }

    // --- ownership ----------------------------------------------------------

    /** The owner's game UUID, or {@code null} if this pet is wild. Never logged. */
    @Nullable
    public UUID ownerId() {
        EntityReference<LivingEntity> reference = this.getOwnerReference();
        return reference == null ? null : reference.getUUID();
    }

    /** The pet's current standing order. */
    public PetCommand command() {
        return this.command;
    }

    /**
     * Sets the standing order and keeps the two vanilla flags the client actually renders in step.
     * {@code SIT} additionally stops the pet where it is and drops any target; {@code GUARD}
     * re-anchors the hold area to the pet's current position.
     */
    public void setCommand(PetCommand command) {
        this.command = command;
        boolean sitting = command == PetCommand.SIT;
        this.setOrderedToSit(sitting);
        this.setInSittingPose(sitting);
        if (sitting) {
            this.getNavigation().stop();
            this.setTarget(null);
        }
        this.guardAnchor = command == PetCommand.GUARD ? this.blockPosition() : null;
    }

    /**
     * Returns this pet to the wild: no owner, no order, and despawnable again. Used by the
     * player-data erasure path and by anything else that has to sever the link without killing the
     * animal.
     *
     * <p>Passing {@code null} to {@code setOwnerReference} is vanilla's own "nobody owns this"
     * representation — the synced value is an {@code Optional}.
     */
    public void releaseToWild() {
        UUID formerOwner = this.ownerId();
        this.setCommand(PetCommand.FOLLOW);
        this.setTame(false, false);
        this.setOwnerReference(null);
        CompanionBridge.hooks().onPetReleased(this);
        if (this.level() instanceof ServerLevel level) {
            CreatureLinkEvents.petStateChanged(level, formerOwner, this, OwnedCreature.Kind.PET,
                    CreatureLinkEvents.STATE_RELEASED);
        }
    }

    // --- interaction --------------------------------------------------------

    // Public rather than protected: Animal widens Mob's protected mobInteract to public, so an
    // override on this branch of the hierarchy has to match it.
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (this.isTame()) {
            return this.interactWhileTame(player, hand, held);
        }
        if (held.getItem() != this.tamingFood()) {
            return InteractionResult.PASS;
        }
        if (!(this.level() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS; // client: swing, then let the server decide
        }
        return this.tryTame(level, player, held);
    }

    private InteractionResult interactWhileTame(Player player, InteractionHand hand, ItemStack held) {
        if (!this.isOwnedBy(player)) {
            // Somebody else's pet. Fall through so their click still does whatever they meant.
            return InteractionResult.PASS;
        }
        if (!(this.level() instanceof ServerLevel level)) {
            boolean handled = player.isSecondaryUseActive()
                    || (held.getItem() == this.tamingFood() && this.getHealth() < this.getMaxHealth());
            return handled ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (player.isSecondaryUseActive()) {
            this.cycleCommand(player);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (held.getItem() == this.tamingFood() && this.getHealth() < this.getMaxHealth()) {
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            this.heal(FEED_HEAL_AMOUNT);
            level.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + this.getBbHeight(),
                    this.getZ(), 3, 0.3D, 0.3D, 0.3D, 0.0D);
            return InteractionResult.SUCCESS_SERVER;
        }
        return InteractionResult.PASS;
    }

    /**
     * The cap check and the taming roll. The cap is enforced <b>here</b>, at tame time, rather than
     * anywhere later: it is the only moment at which refusing costs the player nothing.
     */
    private InteractionResult tryTame(ServerLevel level, Player player, ItemStack held) {
        UUID owner = player.getUUID();
        CreatureOwnershipState state = CreatureOwnershipState.get(level.getServer());
        int cap = NeroCreaturesConfig.MAX_PETS_PER_PLAYER.get();
        if (cap <= 0 || state.count(owner, OwnedCreature.Kind.PET) >= cap) {
            notifyOwner(player, Component.translatable("message.nerocreatures.pet_cap", cap));
            this.level().broadcastEntityEvent(this, (byte) 6); // vanilla "taming failed" smoke
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        if (this.random.nextInt(TAME_CHANCE_DENOMINATOR) != 0) {
            this.level().broadcastEntityEvent(this, (byte) 6);
            return InteractionResult.SUCCESS_SERVER;
        }
        this.tame(player);
        this.setCommand(PetCommand.FOLLOW);
        this.setTarget(null);
        state.add(owner, OwnedCreature.of(this, OwnedCreature.Kind.PET));
        this.level().broadcastEntityEvent(this, (byte) 7); // vanilla "taming succeeded" hearts
        notifyOwner(player, Component.translatable("message.nerocreatures.pet_tamed",
                this.getType().getDescription()));
        CompanionBridge.hooks().onPetTamed(this, player);
        CreatureLinkEvents.petStateChanged(level, owner, this, OwnedCreature.Kind.PET,
                CreatureLinkEvents.STATE_TAMED);
        return InteractionResult.SUCCESS_SERVER;
    }

    private void cycleCommand(Player player) {
        this.setCommand(this.command.next());
        notifyOwner(player, Component.translatable(
                "message.nerocreatures.pet_command." + this.command.key()));
        CompanionBridge.hooks().onPetCommandChanged(this, this.command.key());
    }

    /** Action-bar feedback, server-side, to one player only. Never chat, never broadcast. */
    protected static void notifyOwner(Player player, Component message) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(message, true);
        }
    }

    // --- lifecycle ----------------------------------------------------------

    /**
     * Ticks the species perk. Everything about it is throttled and bounded: one owner lookup and one
     * squared distance every {@value #PERK_INTERVAL_TICKS} ticks, and nothing at all while the pet
     * is wild or a companion mod is driving it.
     */
    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (!this.isTame() || --this.perkCooldown > 0) {
            return;
        }
        this.perkCooldown = PERK_INTERVAL_TICKS;
        if (CompanionBridge.hooks().overridesIdleBehaviour(this)) {
            return;
        }
        if (this.getOwner() instanceof Player owner && owner.level() == this.level()
                && this.distanceToSqr(owner) <= PERK_RADIUS * PERK_RADIUS) {
            this.applyOwnerPerk(level, owner);
        }
    }

    /** A dead pet stops being anyone's, so its row leaves the index with it. */
    @Override
    public void die(DamageSource source) {
        if (this.level() instanceof ServerLevel level && this.isTame()) {
            UUID owner = this.ownerId();
            if (owner != null) {
                CreatureOwnershipState.get(level.getServer()).remove(owner, this.getUUID());
            }
            CompanionBridge.hooks().onPetReleased(this);
            // The owner's companion app is told what died and nothing else — never what killed it,
            // and never anybody else's pet (POPIA/GDPR).
            CreatureLinkEvents.petDied(level, owner, this, OwnedCreature.Kind.PET);
        }
        super.die(source);
    }

    /** A tamed pet is somebody's property and never despawns; a wild one is an ordinary spawn. */
    @Override
    public boolean requiresCustomPersistence() {
        return super.requiresCustomPersistence() || this.isTame();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayerSqr) {
        return !this.isPersistenceRequired() && !this.requiresCustomPersistence();
    }

    /** These are alien fauna, not livestock: they tame, they do not breed. */
    @Override
    public boolean canFallInLove() {
        return false;
    }

    @Override
    @Nullable
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        return null;
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return stack.getItem() == this.tamingFood();
    }

    // --- persistence --------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putString("Command", this.command.key());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        // setCommand rather than a bare assignment: it also restores the sitting flags the client
        // renders, and re-anchors a guarding pet where it woke up.
        this.setCommand(PetCommand.fromKey(input.getStringOr("Command", PetCommand.FOLLOW.key())));
    }

    /**
     * Vanilla's follow-owner goal, gated on the standing order. A pet on {@code SIT} or
     * {@code GUARD} is told to stay somewhere, and following would be the opposite of that.
     */
    private static final class CommandedFollowOwnerGoal extends FollowOwnerGoal {

        private final TameableCreature pet;

        CommandedFollowOwnerGoal(TameableCreature pet, double speed, float startDistance,
                float stopDistance) {
            super(pet, speed, startDistance, stopDistance);
            this.pet = pet;
        }

        @Override
        public boolean canUse() {
            return this.pet.command() == PetCommand.FOLLOW && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return this.pet.command() == PetCommand.FOLLOW && super.canContinueToUse();
        }
    }
}
