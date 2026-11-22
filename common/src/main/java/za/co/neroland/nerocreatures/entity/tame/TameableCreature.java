package za.co.neroland.nerocreatures.entity.tame;

import java.util.Objects;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
//? if >=1.21.5 {
import net.minecraft.world.entity.EntityReference;
//?}
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowOwnerGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.SitWhenOrderedToGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.OwnerHurtTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.breeding.BirthMode;
import za.co.neroland.nerocreatures.budget.CreatureBudget;
import za.co.neroland.nerocreatures.budget.FarTickGate;
import za.co.neroland.nerocreatures.breeding.BreedingHooks;
import za.co.neroland.nerocreatures.breeding.ProductSource;
import za.co.neroland.nerocreatures.breeding.SpeciesProfile;
import za.co.neroland.nerocreatures.breeding.SpeciesProfiles;
import za.co.neroland.nerocreatures.compat.CompanionBridge;
import za.co.neroland.nerocreatures.data.CreatureOwnershipState;
import za.co.neroland.nerocreatures.data.OwnedCreature;
import za.co.neroland.nerocreatures.data.OwnershipCaps;
import za.co.neroland.nerocreatures.entity.ai.HoldAreaGoal;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;
import za.co.neroland.nerocreatures.entity.visual.CreatureVisuals;
import za.co.neroland.nerocreatures.entity.visual.CreatureVisualsHolder;
import za.co.neroland.nerocreatures.link.CreatureLinkEvents;
import za.co.neroland.nerocreatures.progression.CreatureMilestones;
import za.co.neroland.nerocreatures.trait.Inheritance;
import za.co.neroland.nerocreatures.trait.TraitEffects;
import za.co.neroland.nerocreatures.trait.TraitPool;
import za.co.neroland.nerocreatures.trait.TraitSet;
import za.co.neroland.nerocreatures.block.NestHome;

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
 *
 * <h2>0.5.0 additions</h2>
 *
 * <ul>
 *   <li><b>Traits.</b> Every individual carries a {@link TraitSet} under {@value TraitSet#NBT_KEY}. A
 *       creature with none (a wild spawn, or any pet from an older save) rolls two on its first server
 *       tick; nothing it already had changes. Traits are creature data, never player data.</li>
 *   <li><b>Breeding</b> is opt-in per species through {@link #breedable()} (default {@code false}, so
 *       existing species behave exactly as before). Food is the species tag
 *       {@code nerocreatures:food/<species>} or the taming food; birth mode comes from
 *       {@code species.json}. A baby of a tamed pair is born tamed to the parents' owner through the
 *       cap-checked index, and is born wild over the cap.</li>
 *   <li><b>Livestock</b> ({@link LivestockCreature}) switch taming off with {@link #isTameable()}.</li>
 *   <li><b>Ownership kinds.</b> {@link #ownershipKind()} decides which cap and index kind a tame uses
 *       ({@code MountCreature} answers {@code MOUNT}).</li>
 *   <li><b>Load-time ownership check.</b> On its first server tick a tamed creature is released
 *       silently only when the ownership index positively recorded its erasure (its row was removed
 *       by erasure or retention, or its owner was erased); any other missing row is re-indexed (the
 *       entity wins). This closes the unloaded-chunk erasure gap without ever releasing a pet to an
 *       index gap, an older save or a store that recovered empty.</li>
 * </ul>
 */
public abstract class TameableCreature extends TamableAnimal implements ProductSource, CreatureVisualsHolder {

    /** The root's visuals ids are this class's first {@code defineId}. */
    // defineId must be called from the owning class itself (NeoForge 26.x rejects helper-allocated ids).
    private static final CreatureVisuals.Ids VISUALS_IDS = new CreatureVisuals.Ids(
            SynchedEntityData.defineId(TameableCreature.class, EntityDataSerializers.INT),
            SynchedEntityData.defineId(TameableCreature.class, EntityDataSerializers.INT));

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

    private static final String HATCHLING_KEY = "nerocreatures:hatchling";

    /** This individual's traits; {@code null} until rolled (first server tick) or inherited. */
    @Nullable
    private TraitSet traits;

    /** A hatchling's first feed tames at 100%. Saved. */
    private boolean hatchling;

    /** One-shot first-server-tick work (trait roll, ownership check) has run. Not saved. */
    private boolean firstTickDone;

    /**
     * On its owner's shoulder (companion SPI {@code onPetPerched}). Not saved: the companion mod pins
     * and re-perches it; a reloaded pet starts on the ground.
     */
    private boolean perched;

    /** Resolved once: tempt goals ask every tick. */
    @Nullable
    private TagKey<Item> cachedFoodTag;

    private final CreatureTier tier;

    /** Pose, stage/variant/form and the four flags (radiant, alpha, saddled, panniers), synced. */
    private final CreatureVisuals visuals = new CreatureVisuals(this, VISUALS_IDS);

    /** "Far creatures tick slowly" gate for species' own per-tick work. */
    private final FarTickGate farTick = new FarTickGate();

    private PetCommand command = PetCommand.FOLLOW;

    /** Where a {@code GUARD} order was issued. Not saved — a reloaded guard re-anchors where it is. */
    @Nullable
    private BlockPos guardAnchor;

    private int perkCooldown;

    /** Below a quarter health at the last check (companion-app "badly hurt"). Not saved. */
    private boolean badlyHurt;

    /** How often the "badly hurt" crossing is checked, in ticks. */
    private static final int HURT_CHECK_TICKS = 10;

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

    // --- 0.5.0 species hooks --------------------------------------------------

    /**
     * Whether players can tame this species. {@code false} for {@link LivestockCreature}: then
     * {@code mobInteract} never tames, never writes an owner and never adds an ownership row.
     */
    public boolean isTameable() {
        return true;
    }

    /**
     * Whether this species breeds. Default {@code false}: existing species are
     * unchanged until they opt in. Must be a per-class constant (goals are registered from the
     * constructor).
     */
    public boolean breedable() {
        return false;
    }

    /** Which ownership-index kind (and so which cap) a tame of this species uses. */
    public OwnedCreature.Kind ownershipKind() {
        return OwnedCreature.Kind.PET;
    }

    /** This species' {@code species.json} row (defaults when it has none). */
    public SpeciesProfile speciesProfile() {
        return SpeciesProfiles.of(EntityType.getKey(this.getType()).getPath());
    }

    /** Egg or live birth, from {@code species.json}. */
    public BirthMode birthMode() {
        return this.speciesProfile().birth();
    }

    /** The species breeding/taming food tag, {@code nerocreatures:food/<species>} by convention. */
    public TagKey<Item> foodTag() {
        if (this.cachedFoodTag != null) {
            return this.cachedFoodTag;
        }
        String path = EntityType.getKey(this.getType()).getPath();
        Identifier id = Identifier.tryParse(this.speciesProfile().foodTagId(path));
        if (id == null) {
            id = Identifier.fromNamespaceAndPath("nerocreatures", "food/" + path);
        }
        this.cachedFoodTag = TagKey.create(Registries.ITEM, id);
        return this.cachedFoodTag;
    }

    /** Whether a stack is this species' food: its tag, or (for tameables) its taming food item. */
    public boolean isSpeciesFood(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        return stack.is(this.foodTag()) || (this.isTameable() && stack.getItem() == this.tamingFood());
    }

    // --- traits ----------------------------------------------------------------

    /** This individual's traits, or {@code null} before the first server tick has rolled them. */
    @Nullable
    public TraitSet traits() {
        return this.traits;
    }

    /** Sets the traits (birth, hatch, capture-pod release) and applies their attribute effects. */
    public void setTraits(@Nullable TraitSet traits) {
        this.traits = traits;
        if (!this.level().isClientSide()) {
            // The visuals bit is the synced copy; the trait set is the source of truth.
            this.visuals.setRadiant(traits != null && traits.isRadiant());
            TraitEffects.applyAttributes(this, traits, TraitPool.get());
        }
    }

    /** Client-safe: whether this individual is the Radiant colour morph (cosmetic only). */
    public boolean isRadiant() {
        return this.visuals.radiant();
    }

    @Override
    public CreatureVisuals visuals() {
        return this.visuals;
    }

    /** The far-tick gate; expensive goals and timers ask {@code farTick().skip(this)}. */
    public FarTickGate farTick() {
        return this.farTick;
    }

    /** Marks a freshly hatched baby: its first feed by any player tames it at 100%. */
    public void setHatchling(boolean hatchling) {
        this.hatchling = hatchling;
    }

    public boolean isHatchling() {
        return this.hatchling;
    }

    // --- companion SPI: perch ---------------------------------------------------

    /** Whether a companion mod has this pet perched on its owner's shoulder. */
    public boolean isPerched() {
        return this.perched;
    }

    /**
     * Perched or not (companion SPI). While perched NeroCreatures' own goals treat the pet as sitting:
     * no following, guarding, strolling or targeting; its species perk still runs.
     */
    public void setPerched(boolean perched) {
        if (this.perched == perched) {
            return;
        }
        this.perched = perched;
        if (perched) {
            this.getNavigation().stop();
            this.setTarget(null);
        }
        this.setInSittingPose(perched || this.command == PetCommand.SIT);
    }

    /** Sitting by order or perched: the held {@code rest} pose. */
    public boolean isResting() {
        return this.isOrderedToSit() || this.perched;
    }

    // --- product timer hook (ticked by the shared product-timer goal) -----------

    @Override
    public int productIntervalTicks() {
        return this.speciesProfile().productIntervalTicks();
    }

    @Override
    public boolean canProduce() {
        return this.isAlive() && !this.isBaby() && this.productIntervalTicks() > 0;
    }

    /** Default: no product. Species with a {@code product_interval_ticks} override this. */
    @Override
    public void produce(ServerLevel level, int rolls) {
    }

    /** The standard attribute block for a pet tier. Pets are built from the shared bands. */
    public static AttributeSupplier.Builder createPetAttributes(CreatureTier tier) {
        return NeroCreatureEntity.createCreatureAttributes(tier);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new SitWhenOrderedToGoal(this));
        this.goalSelector.addGoal(1, new StayPutGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.2D, true));
        if (this.breedable()) {
            this.goalSelector.addGoal(3, new BreedGoal(this, 1.0D));
            this.goalSelector.addGoal(6, new FollowParentGoal(this, 1.1D));
        }
        this.goalSelector.addGoal(5, new CommandedFollowOwnerGoal(this, 1.1D, 9.0F, 3.0F));
        this.goalSelector.addGoal(6, new HoldAreaGoal(this, () -> this.guardAnchor, GUARD_RADIUS, 1.0D,
                () -> this.command == PetCommand.GUARD && !this.perched));
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
        //? if >=1.21.5 {
        EntityReference<LivingEntity> reference = this.getOwnerReference();
        return reference == null ? null : reference.getUUID();
        //?} else {
        /*return this.getOwnerUUID();
        *///?}
    }

    /** The pet's current standing order. */
    public PetCommand command() {
        return this.command;
    }

    /**
     * Sets the standing order and keeps the two vanilla flags the client actually renders in step.
     * {@code SIT} and {@code STAY} additionally stop the pet where it is and drop any target;
     * {@code GUARD} re-anchors the hold area to the pet's current position.
     */
    public void setCommand(PetCommand command) {
        this.command = command;
        boolean sitting = command == PetCommand.SIT;
        this.setOrderedToSit(sitting);
        this.setInSittingPose(sitting);
        if (sitting || command == PetCommand.STAY) {
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
        this.releaseToWild(true);
    }

    /**
     * Releases this pet for a POPIA/GDPR erasure (or Core's inactivity purge): the same as
     * {@link #releaseToWild()}, but nothing is published to the former owner, because they are the
     * person being erased.
     */
    public void releaseForErasure() {
        this.releaseToWild(false);
    }

    /**
     * {@link #releaseToWild()} with control over the companion-app event. The load-time ownership
     * check passes {@code false}: the former owner was erased, so nothing may be published to them.
     */
    protected void releaseToWild(boolean notifyOwner) {
        UUID formerOwner = notifyOwner ? this.ownerId() : null;
        this.setPerched(false);
        NestHome.clear(this);
        this.setCommand(PetCommand.FOLLOW);
        this.setTame(false, false);
        //? if >=1.21.5 {
        this.setOwnerReference(null);
        //?} else {
        /*this.setOwnerUUID(null);
        *///?}
        CompanionBridge.petReleased(this);
        if (formerOwner != null && this.level() instanceof ServerLevel level) {
            CreatureLinkEvents.petStateChanged(level, formerOwner, this, this.ownershipKind(),
                    CreatureLinkEvents.STATE_RELEASED);
        }
    }

    /** A gallery display drops nothing (no loot, no equipment) and gives no experience. */
    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
        if (!za.co.neroland.nerocreatures.gallery.GalleryDisplays.isDisplay(this)) {
            super.dropAllDeathLoot(level, source);
        }
    }

    @Override
    public boolean shouldDropExperience() {
        return !za.co.neroland.nerocreatures.gallery.GalleryDisplays.isDisplay(this) && super.shouldDropExperience();
    }

    // --- interaction --------------------------------------------------------

    // Public rather than protected: Animal widens Mob's protected mobInteract to public, so an
    // override on this branch of the hierarchy has to match it.
    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        // A gallery display does nothing (a paddock mount may be ridden by a creative player).
        if (za.co.neroland.nerocreatures.gallery.GalleryDisplays.isDisplay(this)) {
            return za.co.neroland.nerocreatures.gallery.GalleryDisplays.interact(this, player, hand);
        }
        ItemStack held = player.getItemInHand(hand);
        if (this.isTame()) {
            return this.interactWhileTame(player, hand, held);
        }
        if (!this.isTameable()) {
            // Livestock: vanilla Animal handles love mode and feeding babies; anything else passes.
            return this.breedable() && this.isFood(held) ? super.mobInteract(player, hand) : InteractionResult.PASS;
        }
        if (!this.isSpeciesFood(held)) {
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
                    || (this.isSpeciesFood(held) && this.getHealth() < this.getMaxHealth());
            return handled ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (player.isSecondaryUseActive()) {
            this.cycleCommand(player);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (this.isSpeciesFood(held) && this.getHealth() < this.getMaxHealth()) {
            if (!player.getAbilities().instabuild) {
                held.shrink(1);
            }
            this.heal(FEED_HEAL_AMOUNT);
            level.sendParticles(ParticleTypes.HEART, this.getX(), this.getY() + this.getBbHeight(),
                    this.getZ(), 3, 0.3D, 0.3D, 0.3D, 0.0D);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (this.breedable() && this.isFood(held)) {
            // Healthy and fed by its owner: vanilla love mode (or growing a baby up).
            return super.mobInteract(player, hand);
        }
        return InteractionResult.PASS;
    }

    /**
     * The cap check and the taming roll. The cap is enforced <b>here</b>, at tame time, rather than
     * anywhere later: it is the only moment at which refusing costs the player nothing.
     */
    private InteractionResult tryTame(ServerLevel level, Player player, ItemStack held) {
        UUID owner = player.getUUID();
        OwnedCreature.Kind kind = this.ownershipKind();
        CreatureOwnershipState state = CreatureOwnershipState.get(level.getServer());
        int cap = OwnershipCaps.capFor(kind);
        if (cap <= 0 || state.count(owner, kind) >= cap) {
            // message.nerocreatures.pet_cap / mount_cap
            notifyOwner(player, Component.translatable("message.nerocreatures." + kind.key() + "_cap", cap));
            this.level().broadcastEntityEvent(this, (byte) 6); // vanilla "taming failed" smoke
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        if (!this.hatchling && this.random.nextInt(TAME_CHANCE_DENOMINATOR) != 0) {
            this.level().broadcastEntityEvent(this, (byte) 6);
            return InteractionResult.SUCCESS_SERVER;
        }
        this.tame(player);
        this.hatchling = false;
        CreatureMilestones.record(player, CreatureMilestones.FIRST_TAME); // record gate, never a lock
        for (net.minecraft.resources.Identifier countGate : CreatureMilestones.tameCountGates(state.noteTame(owner))) {
            CreatureMilestones.record(player, countGate); // tamed/10, tamed/25: record gates, never locks
        }
        za.co.neroland.nerocreatures.bestiary.BestiaryProgress.noteTame(player, this); // Bestiary: tames study tameables and mounts
        this.setCommand(PetCommand.FOLLOW);
        this.setTarget(null);
        state.add(owner, OwnedCreature.of(this, kind));
        this.level().broadcastEntityEvent(this, (byte) 7); // vanilla "taming succeeded" hearts
        notifyOwner(player, Component.translatable("message.nerocreatures.pet_tamed",
                this.getType().getDescription()));
        CompanionBridge.petTamed(this, player);
        CreatureLinkEvents.petStateChanged(level, owner, this, kind,
                CreatureLinkEvents.STATE_TAMED);
        return InteractionResult.SUCCESS_SERVER;
    }

    private void cycleCommand(Player player) {
        this.setCommand(this.command.next());
        notifyOwner(player, Component.translatable(
                "message.nerocreatures.pet_command." + this.command.key()));
        CompanionBridge.petCommandChanged(this, this.command.key());
    }

    /** Action-bar feedback, server-side, to one player only. Never chat, never broadcast. */
    protected static void notifyOwner(Player player, Component message) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(message, true);
        }
    }

    // --- lifecycle ----------------------------------------------------------

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        CreatureVisuals.define(builder, VISUALS_IDS);
    }

    /**
     * First server tick: roll traits for a creature that has none (existing saves included), then the
     * load-time ownership check. Runs in {@code tick} rather than the AI step so a NoAI creature is
     * covered too. One map lookup per owned creature per load.
     */
    @Override
    public void tick() {
        long budgetStart = CreatureBudget.begin();
        super.tick();
        if (!this.level().isClientSide()) {
            this.visuals.tick();
        }
        if (!this.firstTickDone && this.level() instanceof ServerLevel level) {
            this.firstTickDone = true;
            if (this.traits == null) {
                this.setTraits(Inheritance.fresh(TraitPool.get(), this.random));
            }
            this.verifyOwnership(level);
            if (this.isTameable() && !za.co.neroland.nerocreatures.gallery.GalleryDisplays.isDisplay(this)) {
                // Companion SPI: goals once per loaded instance (band-checked), then "pet loaded".
                CompanionBridge.addCompanionGoals(this, this.goalSelector);
                UUID owner = this.isTame() ? this.ownerId() : null;
                if (owner != null) {
                    CompanionBridge.petLoaded(this, owner);
                }
            }
        }
        CreatureBudget.end(this, budgetStart);
    }

    /**
     * The load-time half of erasure. Released silently (clearing the UUID from entity
     * data, no app event) only on a positive erasure record; any other missing row is re-added, so a
     * pet from an older save or a recovered-empty store stays tamed.
     */
    private void verifyOwnership(ServerLevel level) {
        UUID owner = this.isTame() ? this.ownerId() : null;
        if (owner == null) {
            return;
        }
        CreatureOwnershipState state = CreatureOwnershipState.get(level.getServer());
        switch (state.loadVerdict(owner, this.getUUID())) {
            case INDEXED -> {
            }
            case REINDEX -> state.add(owner, OwnedCreature.of(this, this.ownershipKind()));
            case RELEASE -> {
                this.releaseToWild(false);
                state.creatureFreed(this.getUUID());
            }
        }
    }

    /**
     * Ticks the species perk. Everything about it is throttled and bounded: one owner lookup and one
     * squared distance every {@value #PERK_INTERVAL_TICKS} ticks, and nothing at all while the pet
     * is wild or a companion mod is driving it.
     */
    //? if >=1.21.2 {
    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
    //?} else {
    /*@Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        // Server-side only, so the level is always a ServerLevel here.
        ServerLevel level = (ServerLevel) this.level();
    *///?}
        if (this.perched) {
            // Treated as sitting: the companion mod pins the position; nothing walks or fights.
            if (!this.isTame()) {
                this.setPerched(false);
            } else {
                this.getNavigation().stop();
                if (this.getTarget() != null) {
                    this.setTarget(null);
                }
            }
        }
        // Companion app: "badly hurt" crossing for the owner, checked on a cadence (debounced per creature).
        if (this.isTame() && this.tickCount % HURT_CHECK_TICKS == 0) {
            this.checkBadlyHurt(level);
        }
        // Nest home: rest/heal at the nest, return to it; forgets an invalid nest. Throttled.
        NestHome.tick(this);
        if (!this.isTame() || --this.perkCooldown > 0) {
            return;
        }
        this.perkCooldown = PERK_INTERVAL_TICKS;
        if (CompanionBridge.overridesIdleBehaviour(this)) {
            return;
        }
        if (this.getOwner() instanceof Player owner && owner.level() == this.level()
                && this.distanceToSqr(owner) <= PERK_RADIUS * PERK_RADIUS) {
            this.applyOwnerPerk(level, owner);
        }
    }

    /**
     * Publishes {@code creature_hurt} to the owner when this creature's health has just crossed below a
     * quarter, unless its own owner is the one hurting it. The crossing is remembered on the entity (not
     * saved); the five-minute debounce lives in {@link CreatureLinkEvents}.
     */
    private void checkBadlyHurt(ServerLevel level) {
        UUID owner = this.ownerId();
        if (owner == null) {
            return;
        }
        float max = this.getMaxHealth();
        boolean low = max > 0.0F && za.co.neroland.nerocreatures.link.CreatureLinkRules.isBadlyHurt(this.getHealth() / max);
        boolean wasLow = this.badlyHurt;
        this.badlyHurt = low;
        if (!low || wasLow) {
            return;
        }
        LivingEntity attacker = this.getLastHurtByMob();
        boolean byOwner = attacker != null && owner.equals(attacker.getUUID());
        CreatureLinkEvents.creatureHurt(level, owner, this, this.ownershipKind(), byOwner);
    }

    /** A dead pet stops being anyone's, so its row leaves the index with it. */
    @Override
    public void die(DamageSource source) {
        if (this.level() instanceof ServerLevel level && this.isTame()) {
            UUID owner = this.ownerId();
            if (owner != null) {
                CreatureOwnershipState.get(level.getServer()).remove(owner, this.getUUID());
            }
            // Asked before the release callback: the companion mod may drop its state on release.
            boolean companionRecovers = CompanionBridge.allowsRecovery(this);
            CompanionBridge.petReleased(this);
            // The owner's companion app is told what died and nothing else — never what killed it,
            // and never anybody else's pet (POPIA/GDPR). A companion mod keeping a recovery record
            // raises its own alert, so the owner gets one push, not two.
            if (!companionRecovers) {
                CreatureLinkEvents.petDied(level, owner, this, this.ownershipKind());
            }
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

    // --- breeding ---------------------------------------------

    /** Only species that opted in through {@link #breedable()} can enter love mode. */
    @Override
    public boolean canFallInLove() {
        return this.breedable() && super.canFallInLove();
    }

    /**
     * Same species, both in love, and the same standing: two tamed creatures of one owner, or (for
     * livestock) any two. Wild tameables never breed; neither do a sitting pet or two owners' pets.
     */
    @Override
    public boolean canMate(Animal partner) {
        if (!this.breedable() || !(partner instanceof TameableCreature other) || !super.canMate(partner)) {
            return false;
        }
        if (!this.isTameable()) {
            return true;
        }
        return this.isTame() && other.isTame() && !this.isOrderedToSit() && !other.isOrderedToSit()
                && Objects.equals(this.ownerId(), other.ownerId());
    }

    /** A same-species baby carrying inherited traits (one slot from each parent, plus mutation). */
    @Override
    @Nullable
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner) {
        if (!this.breedable()) {
            return null;
        }
        if (!(this.getType().create(level, EntitySpawnReason.BREEDING) instanceof TameableCreature child)) {
            return null;
        }
        TraitSet other = partner instanceof TameableCreature mate ? mate.traits() : null;
        child.setTraits(Inheritance.inherit(TraitPool.get(), this.traits, other, this.random));
        return child;
    }

    /**
     * Egg species lay an egg instead of a baby when the egg layer is bound; otherwise (and for
     * live-birth species) this is vanilla's path, including the loaders' baby-spawn events.
     */
    @Override
    public void spawnChildFromBreeding(ServerLevel level, Animal partner) {
        // Bestiary: credit each parent's love cause (the player who fed it), never a nearby player.
        za.co.neroland.nerocreatures.bestiary.BestiaryProgress.noteBreed(level, this, partner);
        BreedingHooks.EggLayer layer = BreedingHooks.eggLayer();
        if (this.breedable() && this.birthMode() == BirthMode.EGG && layer != null
                && partner instanceof TameableCreature mate) {
            TraitSet eggTraits = Inheritance.inherit(TraitPool.get(), this.traits, mate.traits(), this.random);
            if (layer.layEgg(level, this, mate, eggTraits)) {
                this.finalizeSpawnChildFromBreeding(level, partner, null);
                return;
            }
        }
        super.spawnChildFromBreeding(level, partner);
    }

    /**
     * A baby of a tamed pair is born tamed to the parents' owner, through the cap-checked index.
     * Over the cap it is born wild. The owner may be offline: the reference is copied from the
     * parent, never looked up by proximity.
     */
    @Override
    public void finalizeSpawnChildFromBreeding(ServerLevel level, Animal partner, @Nullable AgeableMob offspring) {
        if (offspring instanceof TameableCreature child && child.isTameable() && this.isTame()) {
            UUID owner = this.ownerId();
            if (owner != null) {
                OwnedCreature.Kind kind = child.ownershipKind();
                CreatureOwnershipState state = CreatureOwnershipState.get(level.getServer());
                if (state.tryAdd(owner, OwnedCreature.of(child, kind), OwnershipCaps.capFor(kind))) {
                    //? if >=1.21.5 {
                    child.setOwnerReference(this.getOwnerReference());
                    //?} else {
                    /*child.setOwnerUUID(this.getOwnerUUID());
                    *///?}
                    child.setTame(true, true);
                    child.setCommand(PetCommand.FOLLOW);
                }
            }
        }
        super.finalizeSpawnChildFromBreeding(level, partner, offspring);
    }

    @Override
    public boolean isFood(ItemStack stack) {
        return this.isSpeciesFood(stack);
    }

    // --- persistence --------------------------------------------------------

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
        output.putString("Command", this.command.key());
        output.putInt(CreatureVisuals.SAVE_KEY, this.visuals.persistentBits());
        if (this.traits != null) {
            output.store(TraitSet.NBT_KEY, TraitSet.CODEC, this.traits);
        }
        if (this.hatchling) {
            output.putBoolean(HATCHLING_KEY, true);
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
        // setCommand rather than a bare assignment: it also restores the sitting flags the client
        // renders, and re-anchors a guarding pet where it woke up.
        this.setCommand(PetCommand.fromKey(input.getStringOr("Command", PetCommand.FOLLOW.key())));
        this.visuals.restorePersistentBits(input.getIntOr(CreatureVisuals.SAVE_KEY, 0));
        // Absent on every pre-0.5.0 save: left null, rolled on the first server tick. Read after the
        // visuals bits so the trait set re-writes the radiant flag it owns.
        input.read(TraitSet.NBT_KEY, TraitSet.CODEC).ifPresent(this::setTraits);
        this.hatchling = input.getBooleanOr(HATCHLING_KEY, false);
    }

    /**
     * Vanilla's follow-owner goal, gated on the standing order. A pet on {@code SIT} or
     * {@code GUARD} is told to stay somewhere, and following would be the opposite of that.
     */
    /**
     * {@link PetCommand#STAY}: stands exactly where it was told to, on its feet. It holds the movement
     * and jump controls, so nothing lower (attacking, following, strolling) can move it, and it drops
     * any target, so it does not fight either, not even back.
     */
    private static final class StayPutGoal extends net.minecraft.world.entity.ai.goal.Goal {

        private final TameableCreature pet;

        StayPutGoal(TameableCreature pet) {
            this.pet = pet;
            this.setFlags(java.util.EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            return this.pet.command() == PetCommand.STAY && this.pet.isTame() && !this.pet.isPassenger();
        }

        @Override
        public void start() {
            this.pet.getNavigation().stop();
            this.pet.setTarget(null);
        }

        @Override
        public void tick() {
            if (!this.pet.getNavigation().isDone()) {
                this.pet.getNavigation().stop();
            }
            if (this.pet.getTarget() != null) {
                this.pet.setTarget(null);
            }
        }
    }

    private static final class CommandedFollowOwnerGoal extends FollowOwnerGoal {

        private final TameableCreature pet;

        CommandedFollowOwnerGoal(TameableCreature pet, double speed, float startDistance,
                float stopDistance) {
            super(pet, speed, startDistance, stopDistance);
            this.pet = pet;
        }

        @Override
        public boolean canUse() {
            return this.pet.command() == PetCommand.FOLLOW && !this.pet.isPerched() && super.canUse();
        }

        @Override
        public boolean canContinueToUse() {
            return this.pet.command() == PetCommand.FOLLOW && !this.pet.isPerched() && super.canContinueToUse();
        }
    }
}
