package za.co.neroland.nerocreatures.entity.boss;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.block.EphemeralCrustBlock;
import za.co.neroland.nerocreatures.boss.BossController;
import za.co.neroland.nerocreatures.boss.BossPhase;
import za.co.neroland.nerocreatures.boss.BossSummons;
import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;
import za.co.neroland.nerocreatures.world.CreatureGriefing;

/**
 * <b>Rime Matriarch</b> — Glacira's signature boss (altar-summoned only, decision 001).
 *
 * <p>Built on {@link BossController} like the Cinder Tyrant. The fight (numbers in
 * {@link MatriarchRules}):
 *
 * <ul>
 *   <li><b>Blizzard beam</b> (every phase, faster each phase): a 20-tick telegraph, then a 20-tick
 *       beam along the direction locked at the end of the telegraph. The first solid block stops it.</li>
 *   <li><b>Ice pillars</b> are the cover: {@code ephemeral_crust[variant=rime,restore=air]} columns
 *       raised into air only on a ring around the arena centre, breakable, capped, self-melting, and
 *       removed when the fight ends. Gated by {@code bossArenaBlocks} through
 *       {@link CreatureGriefing} (decision 006). Denied: no pillars, any solid block is cover and the
 *       telegraph is twice as long, so the fight stays fair without touching the world.</li>
 *   <li><b>Freezing floor</b> spreading in stages (radius per phase): players standing on it gain
 *       freeze ticks (short of damage until phase 3), and still water sources in it turn into
 *       self-reverting rime crust that gives the water back. Same gate; denied = status zone only.</li>
 * </ul>
 *
 * <p>Everything is server-authoritative. Arena blocks are tracked in capped {@link ArenaLedger}s and
 * given back at once when the fight ends ({@link FightEndLatch}: death or discard, never unload);
 * every one also reverts by itself on its decay tick, which survives a server stop. Nothing about a
 * player — identity or position — is logged or stored by this class.
 */
public class RimeMatriarch extends NeroBossEntity {

    private final PhaseClock clock = new PhaseClock();
    private final ArenaLedger pillarBlocks = new ArenaLedger(MatriarchRules.MAX_PILLAR_BLOCKS);
    private final ArenaLedger pillarBases = new ArenaLedger(MatriarchRules.MAX_PILLARS);
    private final ArenaLedger frozenBlocks = new ArenaLedger(MatriarchRules.MAX_FROZEN_BLOCKS);
    private final FightEndLatch latch = new FightEndLatch();

    /** Whether the last arena-block attempt was refused (decision 006 fallback in effect). */
    private boolean arenaDenied;
    private int floorTick;
    private int pillarTick;

    public RimeMatriarch(EntityType<? extends RimeMatriarch> type, Level level) {
        super(type, level, CreatureTier.APEX);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return NeroCreatureEntity.createCreatureAttributes(CreatureTier.APEX)
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.24D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.ARMOR, 10.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    /** The boss id, as used by {@link BossSummons} and the Apex Altar. */
    public static Identifier bossId() {
        return Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "rime_matriarch");
    }

    // --- summoning (the Apex Altar's entry point) ------------------------------------------

    /** Adds the Matriarch to {@link BossSummons}' summonable set. Idempotent. */
    public static void registerSummon() {
        BossSummons.register(bossId(), RimeMatriarch::create);
    }

    /**
     * Summons a Matriarch at {@code pos} as a <b>contribution-tracked</b> fight (the only way she
     * should appear; she has no natural spawn rule). Delegates to {@link BossSummons#summon}, which
     * binds the fight before the first hit and respects the population caps.
     *
     * @param initiator the summoning player, recorded as a zero-damage participant only; may be null
     * @return the boss, or empty if refused (caps, unknown type)
     */
    public static Optional<NeroBossEntity> summon(ServerLevel level, BlockPos pos, @Nullable UUID initiator) {
        registerSummon();
        return BossSummons.summon(level, pos, bossId(), initiator);
    }

    /** Creates an unspawned Matriarch, looked up by id so this class needs no registry reference. */
    @Nullable
    static NeroBossEntity create(ServerLevel level, BlockPos pos) {
        Identifier id = bossId();
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return null;
        }
        Entity entity = BuiltInRegistries.ENTITY_TYPE.getValue(id).create(level, EntitySpawnReason.EVENT);
        return entity instanceof RimeMatriarch boss ? boss : null;
    }

    // --- goals and phases -------------------------------------------------------------------

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 20.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected BossController createController() {
        return new BossController(this, this, bossId(),
                Component.translatable("entity.nerocreatures.rime_matriarch"),
                BossEvent.BossBarColor.BLUE, MatriarchRules.ARENA_RADIUS, this.phases());
    }

    private List<BossPhase> phases() {
        return List.of(
                BossPhase.of("veiled",
                        Component.translatable("boss.nerocreatures.rime_matriarch.veiled"), 1.0F,
                        () -> List.of(BossPhase.goal(1, new BlizzardBeamGoal(1)))),
                new BossPhase("spreading_frost",
                        Component.translatable("boss.nerocreatures.rime_matriarch.spreading_frost"),
                        MatriarchRules.PHASE_2,
                        () -> List.of(BossPhase.goal(1, new BlizzardBeamGoal(2))),
                        this::onPhaseEntered),
                new BossPhase("shattered_crown",
                        Component.translatable("boss.nerocreatures.rime_matriarch.shattered_crown"),
                        MatriarchRules.PHASE_3,
                        () -> List.of(BossPhase.goal(1, new BlizzardBeamGoal(3))),
                        this::onPhaseEntered));
    }

    /** One-off on entering phases 2 and 3: the floor spreads and the pillars are topped up now. */
    private void onPhaseEntered(ServerLevel level) {
        this.pillarTick = 0;
        this.floorTick = 0;
        level.playSound(null, this.blockPosition(), SoundEvents.POWDER_SNOW_BREAK, SoundSource.HOSTILE, 2.0F, 0.5F);
        level.sendParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY() + 2.0D, this.getZ(),
                60, 2.0D, 1.5D, 2.0D, 0.05D);
    }

    // --- per tick ---------------------------------------------------------------------------

    @Override
    protected void onFirstServerTick(ServerLevel level) {
        super.onFirstServerTick(level);
        this.adoptArena(level);
    }

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
        if (this.controller.isDefeated() || !this.isAlive()) {
            return;
        }
        int phase = Math.max(1, this.controller.phaseNumber());
        if (this.clock.tick(phase)) {
            this.getNavigation().stop();
        }
        this.visuals().setPhase(phase);
        if (this.clock.busy()) {
            return;
        }
        BlockPos anchor = this.controller.anchor();
        if (anchor == null) {
            return;
        }
        if (--this.pillarTick <= 0) {
            this.pillarTick = MatriarchRules.PILLAR_INTERVAL;
            this.raisePillars(level, anchor, phase);
        }
        if (--this.floorTick <= 0) {
            this.floorTick = MatriarchRules.FLOOR_INTERVAL;
            this.freezeFloor(level, anchor, phase);
        }
    }

    /** Never frozen by her own floor. */
    @Override
    public boolean canFreeze() {
        return false;
    }

    // --- pillars ----------------------------------------------------------------------------

    private void raisePillars(ServerLevel level, BlockPos anchor, int phase) {
        EphemeralCrustBlock crust = EphemeralCrustBlock.registered();
        this.pruneArena(level);
        if (crust == null) {
            this.arenaDenied = true;
            return;
        }
        int wanted = MatriarchRules.pillarsToRaise(phase, this.pillarBases.size(), true);
        if (wanted <= 0) {
            return;
        }
        BlockState ice = crust.stateFor(EphemeralCrustBlock.Variant.RIME, EphemeralCrustBlock.Restore.AIR);
        int slots = MatriarchRules.MAX_PILLARS * 2;
        int offset = this.random.nextInt(slots);
        boolean anyAllowed = false;
        boolean anyDenied = false;
        for (int i = 0; i < slots && wanted > 0 && this.pillarBases.hasRoom(); i++) {
            double angle = ((i + offset) % slots) * (Math.PI * 2.0D / slots);
            BlockPos column = BlockPos.containing(
                    anchor.getX() + 0.5D + Math.cos(angle) * MatriarchRules.PILLAR_RING,
                    anchor.getY(),
                    anchor.getZ() + 0.5D + Math.sin(angle) * MatriarchRules.PILLAR_RING);
            BlockPos base = findPillarBase(level, column);
            if (base == null || this.pillarBases.contains(base.asLong())) {
                continue;
            }
            int placed = this.raiseColumn(level, crust, ice, base);
            if (placed < 0) {
                anyDenied = true;
                break;
            }
            if (placed > 0) {
                anyAllowed = true;
                this.pillarBases.tryAdd(base.asLong());
                wanted--;
                level.playSound(null, base, SoundEvents.GLASS_BREAK, SoundSource.HOSTILE, 0.8F, 1.6F);
            }
        }
        if (anyDenied || anyAllowed) {
            this.arenaDenied = anyDenied && !anyAllowed;
        }
    }

    /**
     * Places one pillar column into air only.
     *
     * @return blocks placed, or {@code -1} if the griefing gate refused (stop trying this pass)
     */
    private int raiseColumn(ServerLevel level, EphemeralCrustBlock crust, BlockState ice, BlockPos base) {
        int placed = 0;
        for (int dy = 0; dy < MatriarchRules.PILLAR_HEIGHT; dy++) {
            BlockPos at = base.above(dy);
            if (!level.getBlockState(at).isAir() || !this.pillarBlocks.hasRoom()) {
                break;
            }
            if (!CreatureGriefing.mayAlter(level, this, at, NeroCreaturesConfig.BOSS_ARENA_BLOCKS,
                    CreatureGriefing.Change.PLACE, ice)) {
                return placed == 0 ? -1 : placed;
            }
            if (crust.place(level, at, ice, MatriarchRules.PILLAR_DECAY_TICKS)) {
                this.pillarBlocks.tryAdd(at.asLong());
                placed++;
            }
        }
        return placed;
    }

    /** Ground under a column near the anchor's height with room for a full pillar and nobody in it. */
    @Nullable
    private static BlockPos findPillarBase(ServerLevel level, BlockPos column) {
        for (int dy = 3; dy >= -4; dy--) {
            BlockPos base = column.above(dy);
            BlockPos below = base.below();
            if (!level.isLoaded(base) || !level.getBlockState(base).isAir()
                    || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                continue;
            }
            for (int up = 1; up < MatriarchRules.PILLAR_HEIGHT; up++) {
                if (!level.getBlockState(base.above(up)).isAir()) {
                    return null;
                }
            }
            AABB space = new AABB(base.getX(), base.getY(), base.getZ(),
                    base.getX() + 1.0D, base.getY() + MatriarchRules.PILLAR_HEIGHT, base.getZ() + 1.0D);
            return level.getEntitiesOfClass(LivingEntity.class, space).isEmpty() ? base : null;
        }
        return null;
    }

    // --- freezing floor ---------------------------------------------------------------------

    private void freezeFloor(ServerLevel level, BlockPos anchor, int phase) {
        int radius = MatriarchRules.floorRadius(phase);
        double radiusSqr = (double) radius * radius;
        double cx = anchor.getX() + 0.5D;
        double cz = anchor.getZ() + 0.5D;
        // Status zone: the floor itself is the hazard, so it touches whoever stands on it.
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.isCreative() || !player.onGround() || !player.canFreeze()) {
                continue;
            }
            double dx = player.getX() - cx;
            double dz = player.getZ() - cz;
            if (dx * dx + dz * dz > radiusSqr || Math.abs(player.getY() - anchor.getY()) > 4.0D) {
                continue;
            }
            player.setTicksFrozen(MatriarchRules.freezeTicks(player.getTicksFrozen(),
                    player.getTicksRequiredToFreeze(), phase));
        }
        level.sendParticles(ParticleTypes.SNOWFLAKE, cx, anchor.getY() + 0.2D, cz,
                6 + radius, radius * 0.5D, 0.05D, radius * 0.5D, 0.0D);
        this.freezeWater(level, anchor, radius);
    }

    /** Still water sources in the zone become rime crust that gives the water back. Capped, gated. */
    private void freezeWater(ServerLevel level, BlockPos anchor, int radius) {
        EphemeralCrustBlock crust = EphemeralCrustBlock.registered();
        if (crust == null || !this.frozenBlocks.hasRoom()) {
            return;
        }
        BlockState frozen = crust.stateFor(EphemeralCrustBlock.Variant.RIME, EphemeralCrustBlock.Restore.WATER);
        int done = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int attempt = 0; attempt < MatriarchRules.FREEZE_PER_PASS * 4
                && done < MatriarchRules.FREEZE_PER_PASS && this.frozenBlocks.hasRoom(); attempt++) {
            int dx = this.random.nextInt(radius * 2 + 1) - radius;
            int dz = this.random.nextInt(radius * 2 + 1) - radius;
            if (dx * dx + dz * dz > radius * radius) {
                continue;
            }
            at.set(anchor.getX() + dx, anchor.getY() - 1 - this.random.nextInt(2), anchor.getZ() + dz);
            if (!level.isLoaded(at)) {
                continue;
            }
            BlockState state = level.getBlockState(at);
            if (!state.is(Blocks.WATER) || !state.getFluidState().isSource()
                    || !level.getBlockState(at.above()).isAir()) {
                continue;
            }
            BlockPos pos = at.immutable();
            if (!CreatureGriefing.mayAlter(level, this, pos, NeroCreaturesConfig.BOSS_ARENA_BLOCKS,
                    CreatureGriefing.Change.REPLACE, frozen)) {
                return;
            }
            if (crust.place(level, pos, frozen, MatriarchRules.FROZEN_DECAY_TICKS)) {
                this.frozenBlocks.tryAdd(pos.asLong());
                done++;
            }
        }
    }

    // --- arena bookkeeping ------------------------------------------------------------------

    private boolean isOurIce(ServerLevel level, long packed) {
        BlockPos pos = BlockPos.of(packed);
        if (!level.isLoaded(pos)) {
            return true; // cannot see it; its own decay tick will clear it
        }
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof EphemeralCrustBlock
                && state.getValue(EphemeralCrustBlock.VARIANT) == EphemeralCrustBlock.Variant.RIME;
    }

    private void pruneArena(ServerLevel level) {
        this.pillarBlocks.prune(pos -> this.isOurIce(level, pos));
        this.frozenBlocks.prune(pos -> this.isOurIce(level, pos));
        this.pillarBases.prune(base -> {
            BlockPos b = BlockPos.of(base);
            for (int dy = 0; dy < MatriarchRules.PILLAR_HEIGHT; dy++) {
                if (this.pillarBlocks.contains(b.above(dy).asLong())) {
                    return true;
                }
            }
            return false;
        });
    }

    /**
     * After a load the ledgers are empty: adopt the rime crust still standing in the arena (up to
     * the caps) so the fight's end gives it back too; anything beyond the caps is reverted now.
     */
    private void adoptArena(ServerLevel level) {
        BlockPos anchor = this.controller.anchor();
        if (anchor == null || this.controller.isDefeated()) {
            return;
        }
        int r = MatriarchRules.floorRadius(3);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -3; dy <= 6; dy++) {
                    at.set(anchor.getX() + dx, anchor.getY() + dy, anchor.getZ() + dz);
                    if (!level.isLoaded(at)) {
                        continue;
                    }
                    BlockState state = level.getBlockState(at);
                    if (!(state.getBlock() instanceof EphemeralCrustBlock)
                            || state.getValue(EphemeralCrustBlock.VARIANT) != EphemeralCrustBlock.Variant.RIME) {
                        continue;
                    }
                    boolean water = state.getValue(EphemeralCrustBlock.RESTORE) == EphemeralCrustBlock.Restore.WATER;
                    ArenaLedger ledger = water ? this.frozenBlocks : this.pillarBlocks;
                    if (!ledger.tryAdd(at.asLong())) {
                        restoreOne(level, at.immutable());
                    } else if (!water) {
                        BlockPos below = at.below();
                        BlockState under = level.getBlockState(below);
                        if (!(under.getBlock() instanceof EphemeralCrustBlock)) {
                            this.pillarBases.tryAdd(at.asLong());
                        }
                    }
                }
            }
        }
    }

    /** Gives the whole arena back now. Runs once per fight (see {@link FightEndLatch}). */
    private void restoreArena(ServerLevel level) {
        List<Long> all = new ArrayList<>(this.pillarBlocks.drain());
        all.addAll(this.frozenBlocks.drain());
        this.pillarBases.drain();
        for (long packed : all) {
            restoreOne(level, BlockPos.of(packed));
        }
    }

    /** Undoes one crust block. Not griefing-gated: it puts back what was there. */
    private static void restoreOne(ServerLevel level, BlockPos pos) {
        if (!level.isLoaded(pos)) {
            return; // its scheduled decay tick runs when the chunk loads
        }
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof EphemeralCrustBlock
                && state.getValue(EphemeralCrustBlock.VARIANT) == EphemeralCrustBlock.Variant.RIME) {
            level.setBlock(pos, state.getValue(EphemeralCrustBlock.RESTORE).state(), 3);
        }
    }

    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        if (this.level() instanceof ServerLevel level && this.latch.claim(FightEndLatch.Exit.DEATH)) {
            this.restoreArena(level);
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        FightEndLatch.Exit exit = reason == Entity.RemovalReason.UNLOADED_TO_CHUNK
                || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER
                || reason == Entity.RemovalReason.CHANGED_DIMENSION
                ? FightEndLatch.Exit.UNLOAD : FightEndLatch.Exit.DISCARD;
        if (this.level() instanceof ServerLevel level && this.latch.claim(exit)) {
            this.restoreArena(level);
        }
        super.remove(reason);
    }

    // --- the blizzard beam ------------------------------------------------------------------

    /**
     * The blizzard beam: optional extra telegraph (arena blocks denied), then the 50-tick
     * {@code ability} clip — telegraph 20, beam 20–40 along the direction locked at tick 20,
     * recovery to 50.
     */
    final class BlizzardBeamGoal extends Goal {

        private final int phase;
        private int cooldown;
        private int clipTick;
        private boolean running;
        @Nullable
        private Vec3 direction;

        BlizzardBeamGoal(int phase) {
            this.phase = phase;
            this.cooldown = BossController.scaleCooldown(MatriarchRules.beamCooldown(phase)) / 2;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.cooldown > 0) {
                this.cooldown--;
                return false;
            }
            LivingEntity target = RimeMatriarch.this.getTarget();
            return !RimeMatriarch.this.clock.busy() && target != null && target.isAlive()
                    && RimeMatriarch.this.distanceTo(target) <= MatriarchRules.BEAM_RANGE;
        }

        @Override
        public boolean canContinueToUse() {
            return this.running && RimeMatriarch.this.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            this.running = true;
            this.direction = null;
            this.clipTick = -MatriarchRules.preTelegraph(!RimeMatriarch.this.arenaDenied);
            RimeMatriarch.this.getNavigation().stop();
            RimeMatriarch.this.playSound(SoundEvents.POLAR_BEAR_WARNING, 2.0F, 0.6F);
        }

        @Override
        public void stop() {
            this.running = false;
            this.direction = null;
            this.cooldown = BossController.scaleCooldown(MatriarchRules.beamCooldown(this.phase));
        }

        @Override
        public void tick() {
            if (!(RimeMatriarch.this.level() instanceof ServerLevel level)) {
                this.running = false;
                return;
            }
            LivingEntity target = RimeMatriarch.this.getTarget();
            if (this.direction == null && target != null) {
                RimeMatriarch.this.getLookControl().setLookAt(target, 30.0F, 30.0F);
            }
            Vec3 eye = RimeMatriarch.this.getEyePosition();
            if (this.clipTick == 0) {
                RimeMatriarch.this.visuals().playPose(VisualPose.ABILITY, MatriarchRules.BEAM_CLIP_TICKS);
            }
            if (this.clipTick < MatriarchRules.TELEGRAPH_TICKS) {
                if ((this.clipTick & 3) == 0) {
                    level.sendParticles(ParticleTypes.SNOWFLAKE, eye.x, eye.y + 0.6D, eye.z,
                            8, 0.6D, 0.4D, 0.6D, 0.02D);
                }
            }
            if (this.clipTick == MatriarchRules.TELEGRAPH_TICKS) {
                this.direction = target != null
                        ? target.getEyePosition().subtract(eye).normalize()
                        : RimeMatriarch.this.getLookAngle();
                RimeMatriarch.this.playSound(SoundEvents.POWDER_SNOW_BREAK, 2.0F, 0.4F);
            }
            Vec3 locked = this.direction;
            if (locked != null && MatriarchRules.beamLive(this.clipTick)) {
                this.fire(level, eye, locked, MatriarchRules.beamPulse(this.clipTick));
            }
            if (++this.clipTick >= MatriarchRules.BEAM_CLIP_TICKS) {
                this.running = false;
            }
        }

        private void fire(ServerLevel level, Vec3 from, Vec3 dir, boolean pulse) {
            Vec3 to = from.add(dir.scale(MatriarchRules.BEAM_RANGE));
            HitResult block = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, RimeMatriarch.this));
            double blockDistance = block.getType() == HitResult.Type.MISS
                    ? Double.POSITIVE_INFINITY : from.distanceTo(block.getLocation());
            double drawn = Math.min(blockDistance, MatriarchRules.BEAM_RANGE);
            for (double d = 1.0D; d < drawn; d += 1.5D) {
                Vec3 p = from.add(dir.scale(d));
                level.sendParticles(ParticleTypes.ITEM_SNOWBALL, p.x, p.y, p.z, 1, 0.1D, 0.1D, 0.1D, 0.0D);
            }
            if (!pulse) {
                return;
            }
            DamageSource source = RimeMatriarch.this.damageSources().mobAttack(RimeMatriarch.this);
            float damage = MatriarchRules.beamDamage(this.phase);
            int hit = 0;
            double reachSqr = (MatriarchRules.BEAM_RANGE + 2.0D) * (MatriarchRules.BEAM_RANGE + 2.0D);
            for (ServerPlayer player : level.players()) {
                if (hit >= MatriarchRules.MAX_BEAM_VICTIMS) {
                    break;
                }
                if (player.isSpectator() || !player.isAlive() || player.distanceToSqr(from) > reachSqr) {
                    continue;
                }
                Optional<Vec3> on = player.getBoundingBox().inflate(0.3D).clip(from, to);
                if (on.isEmpty() || !MatriarchRules.beamReaches(blockDistance, from.distanceTo(on.get()))) {
                    continue;
                }
                if (player.hurtServer(level, source, damage)) {
                    hit++;
                    if (player.canFreeze()) {
                        player.setTicksFrozen(Mth.clamp(player.getTicksFrozen() + 40, 0,
                                player.getTicksRequiredToFreeze() + 40));
                    }
                }
            }
        }
    }

    // --- sounds -----------------------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.STRAY_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.STRAY_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.STRAY_DEATH;
    }
}
