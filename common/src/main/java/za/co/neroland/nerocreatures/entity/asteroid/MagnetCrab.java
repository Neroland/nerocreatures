package za.co.neroland.nerocreatures.entity.asteroid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.data.CreatureOwnershipState;
import za.co.neroland.nerocreatures.entity.ai.shared.CadenceTimer;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.tame.SlagBeetleClaims;
import za.co.neroland.nerocreatures.entity.tame.TameableCreature;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;
import za.co.neroland.nerocreatures.world.CreatureGriefing;
import za.co.neroland.nerocreatures.registry.ModSounds;
import za.co.neroland.nerocreatures.registry.ModParticles;

/**
 * <b>Magnet Crab</b> (Asteroid, tameable): an auto-pickup pet for mining.
 *
 * <h2>Wild: shell thief</h2>
 * A wild crab walks to metal items ({@link #MAGNETIC}, plain stacks only) within
 * {@value MagnetCrabRules#STEAL_RADIUS} blocks and sticks them to its shell, up to
 * {@value MagnetCrabRules#SHELL_SLOTS} stacks. Each grab is a {@code CreatureGriefing}
 * {@code Change.ITEM} decision under {@code magnetCrabSteals}; denied, it ignores the item. A crab
 * holding loot never despawns (vanilla pick-up parity), and everything comes back on death. Saved
 * under {@value #SHELL_KEY} (item ids and counts only).
 *
 * <h2>Tamed: item magnet</h2>
 * Every {@value MagnetCrabRules#MAGNET_PULSE_TICKS} ticks (far crabs slower) a tamed crab moves at most
 * {@value MagnetCrabRules#ITEMS_PER_PULSE} ground item within {@value MagnetCrabRules#MAGNET_RADIUS}
 * blocks to its owner's feet, where vanilla pick-up takes over (so a full inventory, pick-up events and
 * the item's pick-up delay all behave as normal). The owner is the vanilla owner of record, confirmed in
 * the ownership index, online in this level and within {@value MagnetCrabRules#DELIVERY_RANGE} blocks;
 * never a nearest-player guess. Skipped: items a Slag Beetle has claimed
 * ({@link SlagBeetleClaims#isReservedForBeetle}), items thrown by anyone but the owner, items with an
 * owner-only pick-up target, and items already at the owner's feet. Each move is a
 * {@code CreatureGriefing} {@code Change.ITEM} decision under {@code magnetCrabCollects} (which also
 * checks the owner may build where the item lies). It works while sitting: park it at a dig site.
 *
 * <p>Visuals: {@code stage} 0-3 = stuck shell plates by stored item count; {@code ability} = grab,
 * {@value MagnetCrabRules#GRAB_CLIP_TICKS} ticks, the item is taken when the claws snap at tick
 * {@value MagnetCrabRules#GRAB_SNAP_TICK}; {@code ability_2} = tamed pull, <b>held</b> (looping) while
 * the magnet is delivering and for {@value MagnetCrabRules#PULL_LINGER_TICKS} ticks after;
 * {@code rest} (perch) held while sitting.
 *
 * <p>Breeding: {@code nerocreatures:food/magnet_crab} (iron nugget), egg birth.
 */
public class MagnetCrab extends TameableCreature {

    /** Metal items a crab is drawn to. Hand-authored; packs may extend it. */
    public static final TagKey<Item> MAGNETIC = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "magnetic"));

    /** Save key of the shell. */
    public static final String SHELL_KEY = "nerocreatures:shell";

    private static final int POSE_CHECK_TICKS = 10;

    // No initialisers that registerGoals() needs: it runs from the Mob constructor.
    private final List<ItemStack> shell = new ArrayList<>(MagnetCrabRules.SHELL_SLOTS);
    private final CadenceTimer magnetPulse = new CadenceTimer(MagnetCrabRules.MAGNET_PULSE_TICKS);
    private int poseCheck;
    private long lastPull = Long.MIN_VALUE / 2;
    private long lastMagnetSound = Long.MIN_VALUE / 2;

    public MagnetCrab(EntityType<? extends MagnetCrab> type, Level level) {
        super(type, level, CreatureTier.LESSER);
    }

    /** Small, armoured and quick sideways. */
    public static AttributeSupplier.Builder createAttributes() {
        return TameableCreature.createPetAttributes(CreatureTier.LESSER)
                .add(Attributes.MAX_HEALTH, 14.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.28D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 16.0D);
    }

    @Override
    public Item tamingFood() {
        return Items.IRON_NUGGET;
    }

    @Override
    public boolean breedable() {
        return true;
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // Wild only (the goal checks); above the stroll, below melee and breeding.
        this.goalSelector.addGoal(4, new ShellStealGoal());
    }

    /** The crab's use is its magnet, not a perk on the owner. */
    @Override
    protected void applyOwnerPerk(ServerLevel level, Player owner) {
    }

    /** Stacks currently stuck to the shell. */
    public int shellSlotsUsed() {
        return this.shell.size();
    }

    private int storedItems() {
        int total = 0;
        for (ItemStack stack : this.shell) {
            total += stack.getCount();
        }
        return total;
    }

    private void updateStage() {
        this.visuals().setStage(MagnetCrabRules.stage(this.storedItems()));
    }

    static boolean isPlainMetal(ItemStack stack) {
        return !stack.isEmpty() && stack.is(MAGNETIC) && stack.getComponentsPatch().isEmpty();
    }

    // --- tamed magnet -------------------------------------------------------------

    //? if >=1.21.2 {
    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
    //?} else {
    /*@Override
    protected void customServerAiStep() {
        super.customServerAiStep();
        ServerLevel level = (ServerLevel) this.level();
    *///?}
        if (--this.poseCheck <= 0) {
            this.poseCheck = POSE_CHECK_TICKS;
            this.updateRestPose();
        }
        if (!this.isTame()) {
            return;
        }
        if (!this.shell.isEmpty()) {
            this.dropShell(level); // a tamed crab gives back what it stole as a wild one
        }
        if (this.magnetPulse.ready(level.getGameTime(), this.getId()) && !this.farTick().skip(this)) {
            this.magnetPulse(level);
        }
    }

    private void updateRestPose() {
        VisualPose pose = this.visuals().pose();
        if (pose == VisualPose.ABILITY_2 && !MagnetCrabRules.stillPulling(this.level().getGameTime(), this.lastPull)) {
            this.visuals().holdPose(VisualPose.IDLE);
            pose = VisualPose.IDLE;
        }
        boolean resting = this.isResting();
        if (resting && pose == VisualPose.IDLE) {
            this.visuals().holdPose(VisualPose.REST);
        } else if (!resting && pose == VisualPose.REST) {
            this.visuals().holdPose(VisualPose.IDLE);
        }
    }

    private void magnetPulse(ServerLevel level) {
        if (!Boolean.TRUE.equals(NeroCreaturesConfig.MAGNET_CRAB_COLLECTS.get())) {
            return;
        }
        ServerPlayer owner = this.deliveryOwner(level);
        if (owner == null || !MagnetCrabRules.ownerInRange(true, this.distanceToSqr(owner))) {
            return;
        }
        double skipSqr = MagnetCrabRules.OWNER_SKIP_RADIUS * MagnetCrabRules.OWNER_SKIP_RADIUS;
        List<ItemEntity> found = new ArrayList<>(MagnetCrabRules.MAX_CANDIDATES);
        level.getEntities(EntityTypeTest.forClass(ItemEntity.class),
                this.getBoundingBox().inflate(MagnetCrabRules.MAGNET_RADIUS),
                item -> item.isAlive() && !item.getItem().isEmpty() && item.distanceToSqr(owner) > skipSqr,
                found, MagnetCrabRules.MAX_CANDIDATES);
        int moved = 0;
        for (ItemEntity item : found) {
            if (!MagnetCrabRules.underRate(moved)) {
                break;
            }
            // Cheapest first: the beetle claim, then the thrower/target read, then the griefing gate.
            boolean reserved = SlagBeetleClaims.isReservedForBeetle(level, item);
            boolean free = !reserved && claimFree(level, item, owner.getUUID());
            boolean allowed = free && CreatureGriefing.mayAlter(level, this, item.blockPosition(),
                    NeroCreaturesConfig.MAGNET_CRAB_COLLECTS, CreatureGriefing.Change.ITEM);
            if (MagnetCrabRules.mayCollect(this.isTame(), reserved, free, allowed)) {
                this.deliver(level, item, owner);
                moved++;
            }
        }
    }

    /**
     * The owner of record, only if the ownership index agrees, online, alive, not spectating and in this
     * level. Never a nearest-player lookup. The UUID is compared, never logged.
     */
    @Nullable
    private ServerPlayer deliveryOwner(ServerLevel level) {
        UUID owner = this.ownerId();
        MinecraftServer server = level.getServer();
        if (owner == null || server == null
                || !CreatureOwnershipState.get(server).contains(owner, this.getUUID())) {
            return null;
        }
        ServerPlayer player = server.getPlayerList().getPlayer(owner);
        if (player == null || player.level() != level || !player.isAlive() || player.isSpectator()) {
            return null;
        }
        return player;
    }

    /**
     * The griefing rule: the thrower must be the owner or absent, and no owner-only pick-up target may be set.
     * A loaded thrower is compared directly; otherwise the item's saved {@code Thrower}/{@code Owner}
     * keys are read (presence only), so an absent-but-recorded thrower (another player, offline) is
     * still refused.
     */
    static boolean claimFree(ServerLevel level, ItemEntity item, UUID owner) {
        Entity thrower = item.getOwner();
        if (thrower != null && !owner.equals(thrower.getUUID())) {
            return false;
        }
        CompoundTag saved = savedItem(level, item);
        return MagnetCrabRules.claimFree(thrower != null, saved.contains("Thrower"), saved.contains("Owner"));
    }

    private static CompoundTag savedItem(ServerLevel level, ItemEntity item) {
        //? if >=1.21.6 {
        net.minecraft.world.level.storage.TagValueOutput output = net.minecraft.world.level.storage.TagValueOutput
                .createWithContext(net.minecraft.util.ProblemReporter.DISCARDING, level.registryAccess());
        item.saveWithoutId(output);
        return output.buildResult();
        //?} else {
        /*return item.saveWithoutId(new CompoundTag());
        *///?}
    }

    /** Moves the item to the owner's feet; vanilla pick-up does the rest. */
    private void deliver(ServerLevel level, ItemEntity item, ServerPlayer owner) {
        level.sendParticles(ModParticles.MAGNET_SPARK.get(), item.getX(), item.getY() + 0.2D, item.getZ(),
                4, 0.15D, 0.15D, 0.15D, 0.02D);
        item.setPos(owner.getX(), owner.getY() + 0.25D, owner.getZ());
        item.setDeltaMovement(0.0D, 0.0D, 0.0D);
        level.sendParticles(ModParticles.MAGNET_SPARK.get(), owner.getX(), owner.getY() + 0.5D, owner.getZ(),
                3, 0.2D, 0.2D, 0.2D, 0.02D);
        this.lastPull = level.getGameTime();
        if (this.visuals().pose() != VisualPose.ABILITY) {
            this.visuals().holdPose(VisualPose.ABILITY_2);
        }
        if (MagnetCrabRules.magnetSoundDue(this.lastMagnetSound, level.getGameTime())) {
            this.lastMagnetSound = level.getGameTime();
            ModSounds.MAGNET_CRAB_MAGNET.play(this);
        }
    }

    // --- wild shell -----------------------------------------------------------------

    /** Grabs (part of) a metal stack into the shell if every gate passes. */
    private boolean tryGrab(ServerLevel level, ItemEntity item) {
        if (!item.isAlive()) {
            return false;
        }
        ItemStack stack = item.getItem();
        boolean metal = stack.is(MAGNETIC);
        boolean plain = isPlainMetal(stack);
        boolean reserved = SlagBeetleClaims.isReservedForBeetle(level, item);
        boolean allowed = metal && plain && !reserved && !this.isTame()
                && this.shell.size() < MagnetCrabRules.SHELL_SLOTS
                && CreatureGriefing.mayAlter(level, this, item.blockPosition(),
                        NeroCreaturesConfig.MAGNET_CRAB_STEALS, CreatureGriefing.Change.ITEM);
        if (!MagnetCrabRules.maySteal(this.isTame(), this.shell.size(), metal, plain, reserved, allowed)) {
            return false;
        }
        int count = MagnetCrabRules.takeCount(stack.getCount(), this.shell.size());
        if (count <= 0) {
            return false;
        }
        ItemStack taken = stack.copyWithCount(count);
        this.take(item, count);
        ItemStack rest = stack.copy();
        rest.shrink(count);
        if (rest.isEmpty()) {
            item.discard();
        } else {
            item.setItem(rest);
        }
        this.shell.add(taken);
        this.setPersistenceRequired();
        this.updateStage();
        ModSounds.MAGNET_CRAB_SNAP.play(this);
        return true;
    }

    private void dropShell(ServerLevel level) {
        for (ItemStack stack : this.shell) {
            if (!stack.isEmpty()) {
                this.spawnAtLocation(level, stack.copy());
            }
        }
        this.shell.clear();
        this.visuals().setStage(0);
    }

    @Override
    public void die(DamageSource source) {
        if (this.level() instanceof ServerLevel level) {
            this.dropShell(level);
        }
        super.die(source);
    }

    /** A crab carrying stolen items keeps them safe: it does not despawn (vanilla pick-up parity). */
    @Override
    public boolean requiresCustomPersistence() {
        return super.requiresCustomPersistence() || (this.shell != null && !this.shell.isEmpty());
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
        List<String> ids = new ArrayList<>(this.shell.size());
        List<Integer> counts = new ArrayList<>(this.shell.size());
        for (ItemStack stack : this.shell) {
            ids.add(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString());
            counts.add(stack.getCount());
        }
        String encoded = MagnetCrabRules.encode(ids, counts);
        if (!encoded.isEmpty()) {
            output.putString(SHELL_KEY, encoded);
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
        this.shell.clear();
        for (MagnetCrabRules.Slot slot : MagnetCrabRules.decode(input.getStringOr(SHELL_KEY, ""))) {
            Identifier key = Identifier.tryParse(slot.id());
            if (key != null && BuiltInRegistries.ITEM.containsKey(key)) {
                this.shell.add(new ItemStack(BuiltInRegistries.ITEM.getValue(key), slot.count()));
            }
        }
        this.updateStage();
    }

    // Mapped vanilla events, pitched up: a small clicking crustacean.

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.MAGNET_CRAB_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ModSounds.MAGNET_CRAB_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.MAGNET_CRAB_DEATH.get();
    }

    @Override
    public float getVoicePitch() {
        return super.getVoicePitch() * 1.3F;
    }

    /**
     * Wild only: walk to the nearest unclaimed metal item and grab it. Scans every
     * {@value MagnetCrabRules#STEAL_SCAN_TICKS} ticks (far crabs slower), bounded query, gives up after
     * {@value MagnetCrabRules#CHASE_TICKS} ticks.
     */
    private final class ShellStealGoal extends Goal {

        private final CadenceTimer cadence = new CadenceTimer(MagnetCrabRules.STEAL_SCAN_TICKS);
        @Nullable
        private ItemEntity loot;
        private int chase;
        /** Ticks until the claws snap (the grab clip is running), or -1. */
        private int snapIn = -1;

        ShellStealGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            MagnetCrab crab = MagnetCrab.this;
            if (crab.isTame() || crab.shell.size() >= MagnetCrabRules.SHELL_SLOTS
                    || !(crab.level() instanceof ServerLevel level)
                    || !Boolean.TRUE.equals(NeroCreaturesConfig.MAGNET_CRAB_STEALS.get())
                    || !this.cadence.ready(level.getGameTime(), crab.getId())
                    || crab.farTick().skip(crab)) {
                return false;
            }
            this.loot = this.findLoot(level);
            return this.loot != null;
        }

        @Nullable
        private ItemEntity findLoot(ServerLevel level) {
            MagnetCrab crab = MagnetCrab.this;
            List<ItemEntity> found = new ArrayList<>(MagnetCrabRules.MAX_CANDIDATES);
            level.getEntities(EntityTypeTest.forClass(ItemEntity.class),
                    crab.getBoundingBox().inflate(MagnetCrabRules.STEAL_RADIUS, 2.0D, MagnetCrabRules.STEAL_RADIUS),
                    item -> item.isAlive() && !item.hasPickUpDelay() && isPlainMetal(item.getItem()),
                    found, MagnetCrabRules.MAX_CANDIDATES);
            found.sort(Comparator.comparingDouble(crab::distanceToSqr));
            for (ItemEntity item : found) {
                if (!SlagBeetleClaims.isReservedForBeetle(level, item)) {
                    return item;
                }
            }
            return null;
        }

        @Override
        public boolean canContinueToUse() {
            MagnetCrab crab = MagnetCrab.this;
            return this.loot != null && this.loot.isAlive() && (this.chase > 0 || this.snapIn >= 0) && !crab.isTame()
                    && crab.shell.size() < MagnetCrabRules.SHELL_SLOTS;
        }

        @Override
        public void start() {
            this.chase = MagnetCrabRules.CHASE_TICKS;
            this.snapIn = -1;
            if (this.loot != null) {
                MagnetCrab.this.getNavigation().moveTo(this.loot, 1.2D);
            }
        }

        @Override
        public void tick() {
            MagnetCrab crab = MagnetCrab.this;
            ItemEntity item = this.loot;
            if (item == null || !(crab.level() instanceof ServerLevel level)) {
                return;
            }
            this.chase--;
            if (this.snapIn >= 0) {
                // Grab clip running: the item is taken when the claws snap, if it is still in reach.
                crab.getLookControl().setLookAt(item, 30.0F, 30.0F);
                if (--this.snapIn <= 0) {
                    this.snapIn = -1;
                    if (crab.distanceToSqr(item) <= MagnetCrabRules.GRAB_REACH_SQR * 2.0D) {
                        crab.tryGrab(level, item);
                    }
                    this.loot = null;
                }
                return;
            }
            if (crab.distanceToSqr(item) <= MagnetCrabRules.GRAB_REACH_SQR) {
                crab.getNavigation().stop();
                crab.visuals().playPose(VisualPose.ABILITY, MagnetCrabRules.GRAB_CLIP_TICKS);
                this.snapIn = MagnetCrabRules.GRAB_SNAP_TICK;
                return;
            }
            if (this.chase % 10 == 0) {
                crab.getNavigation().moveTo(item, 1.2D);
            }
        }

        @Override
        public void stop() {
            this.loot = null;
            this.snapIn = -1;
            MagnetCrab.this.getNavigation().stop();
        }
    }
}
