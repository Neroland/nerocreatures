package za.co.neroland.nerocreatures.entity.mars;

import java.util.EnumSet;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.tame.TameableCreature;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;
import za.co.neroland.nerocreatures.trait.TraitEffects;
import za.co.neroland.nerocreatures.trait.TraitPool;

/**
 * <b>Strata Hound</b> (Mars; tameable, breedable, live birth) — a prospecting dog with a shovel
 * snout. Prospecting without x-ray.
 *
 * <p><b>Ability.</b> Its owner shows it a block or a raw ore (use the item on it; not its food, not
 * while sneaking). The hound sniffs out the nearest matching block within range, leads its owner
 * there and points at it, marking the spot with particles. Nothing in the world changes: the scan
 * only reads block states, and the mark is particles plus the hound's own glow colour.
 *
 * <ul>
 *   <li><b>Bounded scan</b> ({@link StrataScan}): range {@code strataHoundRange} × Keen, capped at
 *       {@value StrataScan#MAX_RADIUS} blocks across and {@value StrataScan#MAX_VERTICAL} up and
 *       down; loaded chunks only; at most {@value StrataScan#BLOCKS_PER_STEP} block reads per step,
 *       one step every {@value #SCAN_INTERVAL_TICKS} ticks; never a per-tick world scan. A search
 *       that runs past {@value #SEARCH_TIMEOUT_TICKS} ticks gives up.</li>
 *   <li><b>Cached result</b>: one position, re-validated every {@value #VALIDATE_TICKS} ticks with a
 *       single block read. Mined out, the hound stops.</li>
 *   <li>Leading lasts at most {@value #LEAD_TIMEOUT_TICKS} ticks, pointing {@value #POINT_TICKS}.</li>
 * </ul>
 *
 * <p>Only the owner can set it searching, and only the owner hears about it (action bar). Tracking is
 * not saved: a reloaded hound starts idle.
 *
 * <p>Visuals: {@code ability} (sniff) while searching, {@code ability_2} (pointer pose) while
 * pointing, {@code rest} while sitting; {@code variant} = the tracked ore's colour family
 * ({@link StrataTargets#colourFamily}), 0 when idle.
 */
public class StrataHound extends TameableCreature {

    static final int SCAN_INTERVAL_TICKS = 2;
    static final int SEARCH_TIMEOUT_TICKS = 1200;
    static final int VALIDATE_TICKS = 20;
    static final int LEAD_TIMEOUT_TICKS = 1200;
    static final int POINT_TICKS = 600;
    private static final int MARK_TICKS = 10;
    private static final int MARK_COLUMN_MAX = 12;

    enum Mode { IDLE, SEARCHING, LEADING, POINTING }

    private Mode mode = Mode.IDLE;
    private int modeTicks;

    @Nullable
    private Predicate<BlockState> matcher;
    @Nullable
    private StrataScan scan;
    @Nullable
    private BlockPos scanOrigin;
    @Nullable
    private BlockPos foundPos;

    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    public StrataHound(EntityType<? extends StrataHound> type, Level level) {
        super(type, level, CreatureTier.LESSER);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return TameableCreature.createPetAttributes(CreatureTier.LESSER)
                .add(Attributes.MAX_HEALTH, 22.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // Above follow-owner (5): while leading, the hound walks to the find, not to its owner.
        this.goalSelector.addGoal(4, new LeadToFindGoal(this));
    }

    @Override
    public Item tamingFood() {
        return Items.BONE;
    }

    @Override
    public boolean breedable() {
        return true;
    }

    /** The hound's use is the search itself; no passive perk. */
    @Override
    protected void applyOwnerPerk(ServerLevel level, Player owner) {
    }

    // --- showing it something ------------------------------------------------------------

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (this.isTame() && !this.isBaby() && this.isOwnedBy(player) && !player.isSecondaryUseActive()
                && !held.isEmpty() && !this.isSpeciesFood(held)) {
            String spec = specFor(held);
            if (spec != null) {
                if (this.level() instanceof ServerLevel level) {
                    this.startTracking(level, player, spec);
                    return InteractionResult.SUCCESS_SERVER;
                }
                return InteractionResult.SUCCESS;
            }
        }
        return super.mobInteract(player, hand);
    }

    /** The target spec for a shown stack, or null. */
    @Nullable
    static String specFor(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem blockItem) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(blockItem.getBlock());
            return StrataTargets.specForBlock(id.getNamespace(), id.getPath());
        }
        Identifier id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return StrataTargets.specForItem(id.getNamespace(), id.getPath());
    }

    /** {@code #nerocreatures:strata_trackable}: the only blocks a hound will ever track (M5). */
    static final TagKey<Block> TRACKABLE = TagKey.create(Registries.BLOCK,
            Identifier.tryParse(StrataTargets.TRACKABLE_TAG));

    /** Whether one block state passes the allow list ({@link StrataTargets#trackable}). */
    static boolean isTrackable(BlockState state) {
        return StrataTargets.trackable(state.is(TRACKABLE), state.hasBlockEntity());
    }

    /**
     * Turns a spec into a block-state test, or null if it names nothing or names a block the hound
     * refuses (not in the allow tag, or a block entity). Every match also passes the allow list.
     */
    @Nullable
    static Predicate<BlockState> resolve(String spec) {
        Identifier id = Identifier.tryParse(StrataTargets.idOf(spec));
        if (id == null) {
            return null;
        }
        if (StrataTargets.isTag(spec)) {
            TagKey<Block> tag = TagKey.create(Registries.BLOCK, id);
            return state -> state.is(tag) && isTrackable(state);
        }
        Block block = BuiltInRegistries.BLOCK.getValue(id);
        if (block == null || block == Blocks.AIR || !isTrackable(block.defaultBlockState())) {
            return null;
        }
        return state -> state.is(block) && isTrackable(state);
    }

    private void startTracking(ServerLevel level, Player owner, String spec) {
        Predicate<BlockState> test = resolve(spec);
        if (test == null) {
            notifyOwner(owner, Component.translatable("message.nerocreatures.strata_hound.unknown"));
            return;
        }
        this.matcher = test;
        int radius = StrataScan.effectiveRadius(NeroCreaturesConfig.STRATA_HOUND_RANGE.get(),
                TraitEffects.abilityRangeMultiplier(this.traits(), TraitPool.get()));
        this.scan = new StrataScan(radius, StrataScan.verticalFor(radius));
        this.scanOrigin = this.blockPosition();
        this.foundPos = null;
        this.setMode(Mode.SEARCHING);
        this.visuals().setVariant(StrataTargets.colourFamily(spec));
        this.playSound(SoundEvents.FOX_SNIFF, 1.0F, 0.8F);
        notifyOwner(owner, Component.translatable("message.nerocreatures.strata_hound.searching"));
    }

    // --- server step -------------------------------------------------------------------

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
        this.tickProspecting(level);
        this.updatePose();
    }

    private void tickProspecting(ServerLevel level) {
        if (this.mode == Mode.IDLE) {
            if (this.visuals().variant() != 0) {
                this.visuals().setVariant(0); // a reloaded hound starts idle
            }
            return;
        }
        if (!this.isTame()) {
            this.stopTracking(null);
            return;
        }
        this.modeTicks++;
        switch (this.mode) {
            case SEARCHING -> this.tickSearch(level);
            case LEADING -> {
                if (this.modeTicks % VALIDATE_TICKS == 0 && !this.findStillThere(level)) {
                    this.stopTracking("gone");
                } else if (this.modeTicks > LEAD_TIMEOUT_TICKS) {
                    this.point();
                }
            }
            case POINTING -> this.tickPointing(level);
            default -> {
            }
        }
    }

    private void tickSearch(ServerLevel level) {
        if (this.modeTicks > SEARCH_TIMEOUT_TICKS || this.scan == null || this.scanOrigin == null
                || this.matcher == null) {
            this.stopTracking("nothing");
            return;
        }
        if (this.modeTicks % SCAN_INTERVAL_TICKS != 0 || this.farTick().skip(this)) {
            return;
        }
        BlockPos origin = this.scanOrigin;
        Predicate<BlockState> test = this.matcher;
        boolean done = this.scan.step(
                (dx, dz) -> level.isLoaded(this.cursor.set(origin.getX() + dx, origin.getY(), origin.getZ() + dz)),
                (dx, dy, dz) -> {
                    this.cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    return !level.isOutsideBuildHeight(this.cursor) && test.test(level.getBlockState(this.cursor));
                });
        if (!done) {
            return;
        }
        if (!this.scan.found()) {
            this.stopTracking("nothing");
            return;
        }
        this.foundPos = origin.offset(this.scan.bestDx(), this.scan.bestDy(), this.scan.bestDz());
        this.scan = null;
        this.setMode(Mode.LEADING);
        this.playSound(SoundEvents.FOX_SNIFF, 1.0F, 1.2F);
        if (this.getOwner() instanceof Player owner) {
            int distance = (int) Math.round(Math.sqrt(this.foundPos.distSqr(this.blockPosition())));
            notifyOwner(owner, Component.translatable("message.nerocreatures.strata_hound.found", distance));
        }
    }

    private void tickPointing(ServerLevel level) {
        if (this.foundPos == null || this.modeTicks > POINT_TICKS) {
            this.stopTracking(null);
            return;
        }
        if (this.modeTicks % VALIDATE_TICKS == 0 && !this.findStillThere(level)) {
            this.stopTracking("gone");
            return;
        }
        this.getNavigation().stop();
        this.getLookControl().setLookAt(this.foundPos.getX() + 0.5D, this.foundPos.getY() + 0.5D,
                this.foundPos.getZ() + 0.5D);
        if (this.modeTicks % MARK_TICKS == 0) {
            this.mark(level, this.foundPos);
        }
    }

    /** The mark: particles on the find, and a short rising column toward the hound so a buried find shows. */
    private void mark(ServerLevel level, BlockPos pos) {
        double x = pos.getX() + 0.5D;
        double z = pos.getZ() + 0.5D;
        level.sendParticles(ParticleTypes.END_ROD, x, pos.getY() + 0.5D, z, 4, 0.3D, 0.3D, 0.3D, 0.0D);
        int top = (int) Math.floor(this.getY());
        int placed = 0;
        for (int y = pos.getY() + 2; y <= top && placed < MARK_COLUMN_MAX; y += 2, placed++) {
            level.sendParticles(ParticleTypes.END_ROD, x, y + 0.5D, z, 1, 0.05D, 0.2D, 0.05D, 0.0D);
        }
    }

    /** One block read: is the cached find still the target? */
    private boolean findStillThere(ServerLevel level) {
        BlockPos pos = this.foundPos;
        Predicate<BlockState> test = this.matcher;
        return pos != null && test != null && level.isLoaded(pos) && test.test(level.getBlockState(pos));
    }

    void point() {
        if (this.mode == Mode.LEADING) {
            this.setMode(Mode.POINTING);
            this.playSound(SoundEvents.AMETHYST_BLOCK_CHIME, 1.0F, 0.8F);
        }
    }

    /** Back to idle; {@code reason} picks the owner's message ({@code null} = none). */
    private void stopTracking(@Nullable String reason) {
        if (reason != null && this.getOwner() instanceof Player owner) {
            notifyOwner(owner, Component.translatable("message.nerocreatures.strata_hound." + reason));
        }
        this.setMode(Mode.IDLE);
        this.matcher = null;
        this.scan = null;
        this.scanOrigin = null;
        this.foundPos = null;
        this.visuals().setVariant(0);
    }

    private void setMode(Mode mode) {
        this.mode = mode;
        this.modeTicks = 0;
    }

    private void updatePose() {
        VisualPose wanted = switch (this.mode) {
            case SEARCHING -> VisualPose.ABILITY;
            case POINTING -> VisualPose.ABILITY_2;
            default -> this.isOrderedToSit() ? VisualPose.REST : VisualPose.IDLE;
        };
        if (this.visuals().pose() != wanted) {
            this.visuals().holdPose(wanted);
        }
    }

    @Override
    public void die(DamageSource source) {
        this.stopTracking(null);
        super.die(source);
    }

    // --- sounds (mapped vanilla events) ------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.FOX_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.FOX_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.FOX_DEATH;
    }

    /**
     * Walks to the find while the hound is leading. One navigation call every
     * {@value #REPATH_TICKS} ticks; hands over to pointing within reach or when the path cannot get
     * any closer (finds are usually buried).
     */
    static final class LeadToFindGoal extends Goal {

        private static final int REPATH_TICKS = 10;
        private static final double ARRIVE_SQR = 3.0D * 3.0D;
        private static final int MAX_STALLS = 3;

        private final StrataHound hound;
        private int repath;
        private int stalls;

        LeadToFindGoal(StrataHound hound) {
            this.hound = hound;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return this.hound.mode == Mode.LEADING && this.hound.foundPos != null
                    && !this.hound.isOrderedToSit() && !this.hound.isPassenger();
        }

        @Override
        public boolean canContinueToUse() {
            return this.canUse();
        }

        @Override
        public void start() {
            this.repath = 0;
            this.stalls = 0;
        }

        @Override
        public void stop() {
            this.hound.getNavigation().stop();
        }

        @Override
        public void tick() {
            BlockPos find = this.hound.foundPos;
            if (find == null || --this.repath > 0) {
                return;
            }
            this.repath = REPATH_TICKS;
            double dx = find.getX() + 0.5D - this.hound.getX();
            double dz = find.getZ() + 0.5D - this.hound.getZ();
            if (dx * dx + dz * dz <= ARRIVE_SQR) {
                this.hound.point();
                return;
            }
            boolean moving = this.hound.getNavigation().moveTo(find.getX() + 0.5D, find.getY() + 1.0D,
                    find.getZ() + 0.5D, 1.15D);
            if (!moving || this.hound.getNavigation().isDone()) {
                if (++this.stalls >= MAX_STALLS) {
                    this.hound.point();
                }
            } else {
                this.stalls = 0;
            }
        }
    }
}
