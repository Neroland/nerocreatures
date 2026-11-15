package za.co.neroland.nerocreatures.entity.boss;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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
import za.co.neroland.nerocreatures.entity.visual.CreatureVisuals;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;
import za.co.neroland.nerocreatures.registry.ModEntities;
import za.co.neroland.nerocreatures.sound.CreatureSound;
import za.co.neroland.nerocreatures.world.CreatureGriefing;
import za.co.neroland.nerocreatures.world.CreatureOwners;
import za.co.neroland.nerocreatures.registry.ModSounds;

/**
 * <b>Facet Colossus</b> — Nyxara's boss: a standing monolith inside a ring of six facet plates.
 * Altar-summoned only (decision 001); no natural spawn rule.
 *
 * <h2>The fight</h2>
 *
 * <ul>
 *   <li><b>One facet.</b> Only the lit facet takes damage; the other plates are armour
 *       ({@link FacetRules}). The lit facet rotates to a different plate on a phase-scaled timer.</li>
 *   <li><b>Beam.</b> A 50-tick {@code ability}: the ring stops and the lit facet turns to the front,
 *       the aim locks at tick 10 and is drawn, the beam fires from tick 20 and hits at 20, 30 and 40.
 *       It hurts players inside the arena and wild hostile mobs only, never pets, mounts, livestock,
 *       villagers or a player outside the arena ({@link ArenaCrystalRules#beamMayHit}).
 *       Standing in front is the only way to hit the facet while it fires.</li>
 *   <li><b>Crystals.</b> Prism pillars ({@code ephemeral_crust[variant=prism]}) grow around the arena
 *       through {@code CreatureGriefing} ({@code bossArenaBlocks}); the beam refracts through them.
 *       Players break them (they drop nothing). Capped, self-reverting, and every one still standing
 *       is reverted when the fight ends ({@link ArenaCrystalRules}). Denied: the beam splits off the
 *       boss's own facets instead (decision 006 fallback).</li>
 *   <li><b>Phase 2</b> (66%): two decoy facets; striking one sparks back at the attacker (capped).
 *       <b>Phase 3</b> (33%): the core is exposed for 30 ticks after every rotation (half damage
 *       from any side) and crystals regrow at once.</li>
 * </ul>
 *
 * <p>Server-authoritative; nothing is stored about any player. Contribution and rewards come from
 * {@link BossController} for summoned fights only.
 */
public class FacetColossus extends NeroBossEntity {

    private static final float PHASE_DECOYS = 0.66F;
    private static final float PHASE_SHATTER = 0.33F;
    private static final double ARENA_RADIUS = 28.0D;
    private static final double EMITTER_HEIGHT = 1.5D;
    private static final double MAX_PITCH = 30.0D;
    private static final float RETALIATION_DAMAGE = 3.0F;
    private static final double RETALIATION_RANGE = 24.0D;
    private static final int MAX_SEGMENTS = ArenaCrystalRules.MAX_BOUNCES + 3;
    private static final int MAX_PARTICLES_PER_DRAW = 64;

    /** One straight piece of a beam. */
    private record Segment(Vec3 from, Vec3 to) {
    }

    private int vulnerable;
    private int decoys;
    private int rotateIn = 80;
    private int exposedFor;
    private int beamTick = -1;
    private int beamCooldown = 100;
    private double beamYaw;
    private double beamPitch;
    private int crystalIn = 40;
    private boolean crystalsDenied;
    private int retaliateIn;
    private final ArenaCrystalRules.CrystalLedger crystals = new ArenaCrystalRules.CrystalLedger();

    public FacetColossus(EntityType<? extends FacetColossus> type, Level level) {
        super(type, level, CreatureTier.APEX);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return NeroCreatureEntity.createCreatureAttributes(CreatureTier.APEX)
                .add(Attributes.MAX_HEALTH, 380.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.16D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.ARMOR, 14.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 6.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D);
    }

    /** The Colossus's id, as used by {@code boss/BossSummons}. */
    public static Identifier bossId() {
        return Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "facet_colossus");
    }

    /**
     * Summons a Colossus as a contribution-tracked fight (the Apex Altar's factory). Empty when the
     * boss is not registered with {@code BossSummons}, the population caps are full, or the entity could
     * not be added.
     */
    public static Optional<NeroBossEntity> summon(ServerLevel level, BlockPos pos, @Nullable UUID initiator) {
        registerSummon();
        return BossSummons.summon(level, pos, bossId(), initiator);
    }

    /** Adds the Colossus to {@link BossSummons}' summonable set. Idempotent. */
    public static void registerSummon() {
        BossSummons.register(bossId(), (level, pos) -> ModEntities.FACET_COLOSSUS.get().create(level, EntitySpawnReason.EVENT));
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 0.8D, true));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.4D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected BossController createController() {
        return new BossController(this, this, bossId(),
                Component.translatable("entity.nerocreatures.facet_colossus"),
                BossEvent.BossBarColor.PURPLE, ARENA_RADIUS, this.phases());
    }

    private List<BossPhase> phases() {
        return List.of(
                BossPhase.of("facets", Component.translatable("boss.nerocreatures.facet_colossus.facets"), 1.0F,
                        () -> List.of()),
                new BossPhase("decoys", Component.translatable("boss.nerocreatures.facet_colossus.decoys"),
                        PHASE_DECOYS, () -> List.of(), this::enterDecoys),
                new BossPhase("shatter", Component.translatable("boss.nerocreatures.facet_colossus.shatter"),
                        PHASE_SHATTER, () -> List.of(), this::enterShatter));
    }

    private void enterDecoys(ServerLevel level) {
        this.rotateIn = 1;
        this.burst(level, ParticleTypes.END_ROD, 30);
        ModSounds.FACET_COLOSSUS_PHASE.play(this);
    }

    private void enterShatter(ServerLevel level) {
        this.rotateIn = 1;
        this.crystalIn = 1;
        this.burst(level, ParticleTypes.END_ROD, 50);
        ModSounds.FACET_COLOSSUS_PHASE.play(this);
    }

    // --- per tick -------------------------------------------------------------

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
        if (!this.isAlive() || this.controller.isDefeated()) {
            return;
        }
        int phase = this.phase();
        this.tickFacets(phase);
        this.tickBeam(level, phase);
        this.tickCrystals(level, phase);
        this.syncVisuals(phase);
    }

    private int phase() {
        return Math.max(1, this.controller.phaseNumber());
    }

    private void tickFacets(int phase) {
        if (this.retaliateIn > 0) {
            this.retaliateIn--;
        }
        if (this.exposedFor > 0) {
            this.exposedFor--;
        }
        if (this.beamTick >= 0 || --this.rotateIn > 0) {
            return; // the ring is locked while the beam fires
        }
        this.vulnerable = FacetRules.nextFacet(this.vulnerable, this.random.nextInt(FacetRules.FACETS - 1));
        this.decoys = FacetRules.decoysActive(phase)
                ? FacetRules.decoyMask(this.vulnerable, this.random.nextInt(20)) : 0;
        this.exposedFor = FacetRules.exposedTicks(phase);
        this.rotateIn = BossController.scaleCooldown(FacetRules.rotationTicks(phase));
        ModSounds.FACET_COLOSSUS_ROTATE.play(this);
    }

    private double ringAngle(ServerLevel level, int phase) {
        return this.beamTick >= 0 ? FacetRules.beamRingAngle(this.vulnerable)
                : FacetRules.ringAngle(level.getGameTime(), phase);
    }

    private void syncVisuals(int phase) {
        CreatureVisuals visuals = this.visuals();
        if (visuals.phase() != phase) {
            visuals.setPhase(phase);
        }
        int variant = FacetRules.visualVariant(this.beamTick >= 0, this.vulnerable);
        if (visuals.variant() != variant) {
            visuals.setVariant(variant);
        }
        // Phase-2+ decoys for the art: form = first decoy facet + 1 (0 none), stage = cyclic gap - 1.
        int shown = FacetRules.visualDecoys(this.beamTick >= 0, this.vulnerable, this.decoys);
        int form = FacetRules.decoyForm(shown);
        int stage = FacetRules.decoyStage(shown);
        if (visuals.form() != form) {
            visuals.setForm(form);
        }
        if (visuals.stage() != stage) {
            visuals.setStage(stage);
        }
    }

    // --- beam -----------------------------------------------------------------

    private void tickBeam(ServerLevel level, int phase) {
        LivingEntity target = this.getTarget();
        if (this.beamTick < 0) {
            if (this.beamCooldown > 0) {
                this.beamCooldown--;
                return;
            }
            double range = ArenaCrystalRules.BEAM_RANGE;
            if (target == null || !target.isAlive() || this.distanceToSqr(target) > range * range) {
                return;
            }
            this.beamTick = 0;
            this.visuals().playPose(VisualPose.ABILITY, ArenaCrystalRules.BEAM_POSE_TICKS);
            this.getNavigation().stop();
            ModSounds.FACET_COLOSSUS_BEAM.play(this);
            return;
        }
        int t = this.beamTick++;
        this.getNavigation().stop();
        if (t == ArenaCrystalRules.BEAM_LOCK_TICK) {
            this.lockAim(target);
        }
        if (t >= ArenaCrystalRules.BEAM_LOCK_TICK) {
            float yaw = (float) this.beamYaw;
            this.setYRot(yaw);
            this.yBodyRot = yaw;
            this.yHeadRot = yaw;
            if (t % 2 == 0) {
                List<Segment> path = this.trace(level);
                this.draw(level, path, ArenaCrystalRules.firing(t));
                if (ArenaCrystalRules.pulseNow(t)) {
                    this.pulse(level, path, phase);
                }
            }
        }
        if (this.beamTick >= ArenaCrystalRules.BEAM_POSE_TICKS) {
            this.beamTick = -1;
            this.beamCooldown = BossController.scaleCooldown(ArenaCrystalRules.beamCooldownTicks(phase));
        }
    }

    private void lockAim(@Nullable LivingEntity target) {
        if (target == null || !target.isAlive()) {
            this.beamYaw = this.yBodyRot;
            this.beamPitch = 0.0D;
            return;
        }
        double dx = target.getX() - this.getX();
        double dz = target.getZ() - this.getZ();
        double dy = target.getY() + target.getBbHeight() * 0.5D - (this.getY() + EMITTER_HEIGHT);
        this.beamYaw = FacetRules.attackYaw(dx, dz);
        double pitch = Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        this.beamPitch = Math.max(-MAX_PITCH, Math.min(MAX_PITCH, pitch));
    }

    private List<Segment> trace(ServerLevel level) {
        List<Segment> out = new ArrayList<>(MAX_SEGMENTS);
        Vec3 origin = new Vec3(this.getX(), this.getY() + EMITTER_HEIGHT, this.getZ());
        this.traceRay(level, origin, this.beamYaw, this.beamPitch, ArenaCrystalRules.BEAM_RANGE,
                ArenaCrystalRules.MAX_BOUNCES, out);
        if (this.crystalsDenied) {
            // Decision 006 fallback: no blocks, so the beam splits off the boss's own facets.
            this.traceRay(level, origin, this.beamYaw - ArenaCrystalRules.REFRACT_DEGREES, this.beamPitch,
                    ArenaCrystalRules.SPLIT_RANGE, 0, out);
            this.traceRay(level, origin, this.beamYaw + ArenaCrystalRules.REFRACT_DEGREES, this.beamPitch,
                    ArenaCrystalRules.SPLIT_RANGE, 0, out);
        }
        return out;
    }

    private void traceRay(ServerLevel level, Vec3 origin, double startYaw, double pitch, double range,
            int maxBounces, List<Segment> out) {
        EphemeralCrustBlock crust = EphemeralCrustBlock.registered();
        Vec3 start = origin;
        Vec3 point = origin;
        Vec3 dir = direction(startYaw, pitch);
        double yaw = startYaw;
        int bounces = 0;
        BlockPos last = BlockPos.containing(point);
        for (double travelled = 0.0D; travelled < range; travelled += ArenaCrystalRules.BEAM_STEP) {
            point = point.add(dir.scale(ArenaCrystalRules.BEAM_STEP));
            BlockPos cell = BlockPos.containing(point);
            if (cell.equals(last)) {
                continue;
            }
            last = cell;
            if (!level.isLoaded(cell)) {
                break;
            }
            BlockState state = level.getBlockState(cell);
            if (isPrism(crust, state)) {
                out.add(new Segment(start, point));
                if (bounces >= maxBounces || out.size() >= MAX_SEGMENTS) {
                    return;
                }
                bounces++;
                yaw = ArenaCrystalRules.refract(yaw, cell.asLong());
                dir = direction(yaw, 0.0D); // refracted rays run level, at crystal height
                start = Vec3.atCenterOf(cell);
                point = start;
                continue;
            }
            if (!state.getCollisionShape(level, cell).isEmpty()) {
                break;
            }
        }
        if (out.size() < MAX_SEGMENTS) {
            out.add(new Segment(start, point));
        }
    }

    private static boolean isPrism(@Nullable EphemeralCrustBlock crust, BlockState state) {
        return crust != null && state.is(crust) && state.hasProperty(EphemeralCrustBlock.VARIANT)
                && state.getValue(EphemeralCrustBlock.VARIANT) == EphemeralCrustBlock.Variant.PRISM;
    }

    private static Vec3 direction(double yawDeg, double pitchDeg) {
        double yaw = Math.toRadians(yawDeg);
        double pitch = Math.toRadians(pitchDeg);
        double flat = Math.cos(pitch);
        return new Vec3(-Math.sin(yaw) * flat, Math.sin(pitch), Math.cos(yaw) * flat);
    }

    private void draw(ServerLevel level, List<Segment> path, boolean firing) {
        ParticleOptions particle = firing ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK;
        int budget = MAX_PARTICLES_PER_DRAW;
        for (Segment segment : path) {
            double length = segment.from().distanceTo(segment.to());
            int points = (int) Math.min(budget, Math.max(1.0D, Math.ceil(length)));
            for (int i = 0; i < points; i++) {
                Vec3 at = segment.from().lerp(segment.to(), (i + 0.5D) / points);
                level.sendParticles(particle, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
            }
            budget -= points;
            if (budget <= 0) {
                return;
            }
        }
    }

    private void pulse(ServerLevel level, List<Segment> path, int phase) {
        float damage = (float) (ArenaCrystalRules.beamDamage(phase)
                * NeroCreaturesConfig.BOSS_DIFFICULTY_MULTIPLIER.get());
        DamageSource source = this.damageSources().mobAttack(this);
        List<LivingEntity> hit = new ArrayList<>();
        for (Segment segment : path) {
            AABB box = new AABB(segment.from(), segment.to()).inflate(ArenaCrystalRules.HIT_RADIUS);
            for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, box, this::beamVictim)) {
                if (hit.size() >= ArenaCrystalRules.MAX_VICTIMS_PER_PULSE) {
                    break;
                }
                if (hit.contains(victim) || distanceToSegment(victim.getBoundingBox().getCenter(), segment)
                        > ArenaCrystalRules.HIT_RADIUS + victim.getBbWidth() * 0.5D) {
                    continue;
                }
                hit.add(victim);
                victim.hurtServer(level, source, damage);
            }
        }
        if (!path.isEmpty()) {
            this.playSound(SoundEvents.BEACON_POWER_SELECT, 1.5F, 1.8F);
        }
    }

    private boolean beamVictim(LivingEntity candidate) {
        if (candidate == this || !candidate.isAlive() || candidate.isSpectator()
                || candidate instanceof NeroBossEntity) {
            return false;
        }
        BlockPos anchor = this.arenaAnchor();
        boolean inArena = anchor != null && candidate.distanceToSqr(anchor.getX() + 0.5D, anchor.getY(),
                anchor.getZ() + 0.5D) <= ARENA_RADIUS * ARENA_RADIUS;
        boolean owned = candidate instanceof Mob mob ? CreatureOwners.controllerUuid(mob) != null
                : CreatureOwners.ownerUuid(candidate) != null;
        return ArenaCrystalRules.beamMayHit(candidate instanceof Player, inArena, candidate instanceof Enemy, owned);
    }

    private static double distanceToSegment(Vec3 p, Segment s) {
        Vec3 ab = s.to().subtract(s.from());
        double lengthSqr = ab.lengthSqr();
        double t = lengthSqr <= 1.0E-9D ? 0.0D
                : Math.max(0.0D, Math.min(1.0D, p.subtract(s.from()).dot(ab) / lengthSqr));
        return p.distanceTo(s.from().add(ab.scale(t)));
    }

    // --- crystals -------------------------------------------------------------

    private void tickCrystals(ServerLevel level, int phase) {
        long now = level.getGameTime();
        this.crystals.prune(now);
        if (--this.crystalIn > 0) {
            return;
        }
        this.crystalIn = ArenaCrystalRules.WAVE_INTERVAL_TICKS;
        this.placeCrystalWave(level, phase, now);
    }

    private void placeCrystalWave(ServerLevel level, int phase, long now) {
        EphemeralCrustBlock crust = EphemeralCrustBlock.registered();
        BlockPos anchor = this.arenaAnchor();
        boolean keyOn = Boolean.TRUE.equals(NeroCreaturesConfig.BOSS_ARENA_BLOCKS.get());
        if (crust == null || anchor == null || !keyOn) {
            this.crystalsDenied = true;
            return;
        }
        int want = ArenaCrystalRules.pillarsToPlace(phase, this.crystals.size(), true);
        if (want <= 0) {
            return;
        }
        BlockState prism = crust.stateFor(EphemeralCrustBlock.Variant.PRISM, EphemeralCrustBlock.Restore.AIR);
        int placed = 0;
        boolean denied = false;
        for (int attempt = 0; attempt < ArenaCrystalRules.PLACE_ATTEMPTS_PER_WAVE && placed < want
                && ArenaCrystalRules.roomForPillar(this.crystals.size()); attempt++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            int radius = ArenaCrystalRules.MIN_RING
                    + this.random.nextInt(ArenaCrystalRules.MAX_RING - ArenaCrystalRules.MIN_RING + 1);
            int x = anchor.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = anchor.getZ() + (int) Math.round(Math.sin(angle) * radius);
            BlockPos base = findGround(level, x, anchor.getY(), z);
            if (base == null) {
                continue;
            }
            BlockPos top = base.above();
            if (!level.getBlockState(top).isAir()) {
                continue;
            }
            if (!CreatureGriefing.mayAlter(level, this, base, NeroCreaturesConfig.BOSS_ARENA_BLOCKS,
                    CreatureGriefing.Change.PLACE, prism)
                    || !CreatureGriefing.mayAlter(level, this, top, NeroCreaturesConfig.BOSS_ARENA_BLOCKS,
                            CreatureGriefing.Change.PLACE, prism)) {
                denied = true;
                continue;
            }
            long expires = now + ArenaCrystalRules.DECAY_TICKS;
            if (crust.place(level, base, prism, ArenaCrystalRules.DECAY_TICKS)
                    && !this.crystals.add(base.asLong(), expires)) {
                level.setBlock(base, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            if (crust.place(level, top, prism, ArenaCrystalRules.DECAY_TICKS)
                    && !this.crystals.add(top.asLong(), expires)) {
                level.setBlock(top, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            placed++;
        }
        this.crystalsDenied = placed == 0 && denied;
    }

    /** The first air cell with a sturdy floor in a short column around {@code y}; never loads a chunk. */
    @Nullable
    private static BlockPos findGround(ServerLevel level, int x, int y, int z) {
        for (int dy = 3; dy >= -4; dy--) {
            BlockPos cell = new BlockPos(x, y + dy, z);
            if (!level.isLoaded(cell)) {
                return null;
            }
            BlockPos below = cell.below();
            if (level.getBlockState(cell).isAir()
                    && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                return cell;
            }
        }
        return null;
    }

    @Override
    protected void onFirstServerTick(ServerLevel level) {
        super.onFirstServerTick(level);
        this.adoptCrystals(level);
    }

    /**
     * After a load the ledger is empty: adopt the prism crystals still standing in the arena ring (up to
     * the cap) so the fight's end removes them too; any beyond the cap are removed now. Runs once.
     */
    private void adoptCrystals(ServerLevel level) {
        BlockPos anchor = this.arenaAnchor();
        EphemeralCrustBlock crust = EphemeralCrustBlock.registered();
        if (anchor == null || crust == null || this.controller.isDefeated()) {
            return;
        }
        long expires = level.getGameTime() + ArenaCrystalRules.DECAY_TICKS;
        int r = ArenaCrystalRules.MAX_RING + 1;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dy = -4; dy <= 5; dy++) {
                    at.set(anchor.getX() + dx, anchor.getY() + dy, anchor.getZ() + dz);
                    if (!level.isLoaded(at) || !isPrism(crust, level.getBlockState(at))) {
                        continue;
                    }
                    if (!this.crystals.contains(at.asLong()) && !this.crystals.add(at.asLong(), expires)) {
                        level.setBlock(at.immutable(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    }
                }
            }
        }
    }

    /** Reverts every crystal this boss still owns. Called when the fight ends. */
    private void restoreCrystals() {
        if (!(this.level() instanceof ServerLevel level)) {
            return;
        }
        EphemeralCrustBlock crust = EphemeralCrustBlock.registered();
        for (long packed : this.crystals.drainForRestore()) {
            BlockPos pos = BlockPos.of(packed);
            if (level.isLoaded(pos) && isPrism(crust, level.getBlockState(pos))) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        this.restoreCrystals();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        boolean unloading = reason == Entity.RemovalReason.UNLOADED_TO_CHUNK
                || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER
                || reason == Entity.RemovalReason.CHANGED_DIMENSION;
        if (!unloading) {
            // Unloaded crystals revert on their own scheduled tick; a discarded fight cleans up now.
            this.restoreCrystals();
        }
        super.remove(reason);
    }

    // --- damage ---------------------------------------------------------------

    //? if >=1.21.2 {
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
    //?} else {
    /*@Override
    public boolean hurt(DamageSource source, float amount) {
        if (!(this.level() instanceof ServerLevel level)) {
            return super.hurt(source, amount);
        }
    *///?}
        float scaled = amount;
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            FacetRules.Hit hit = this.classify(level, source);
            float factor = FacetRules.damageFactor(hit);
            if (factor <= 0.0F) {
                if (hit == FacetRules.Hit.DECOY) {
                    this.retaliate(level, source);
                }
                level.playSound(null, this.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.HOSTILE,
                        1.0F, 0.6F);
                return false;
            }
            scaled = amount * factor;
        }
        return super.hurtServer(level, source, scaled);
    }

    private FacetRules.Hit classify(ServerLevel level, DamageSource source) {
        Vec3 at = source.getSourcePosition();
        double yaw = at == null ? 0.0D : FacetRules.attackYaw(at.x - this.getX(), at.z - this.getZ());
        return FacetRules.classify(at != null, yaw, this.yBodyRot, this.ringAngle(level, this.phase()),
                this.vulnerable, this.decoys, this.exposedFor > 0);
    }

    /** A decoy hit sparks back at the attacker, at most once per cooldown. */
    private void retaliate(ServerLevel level, DamageSource source) {
        if (this.retaliateIn > 0) {
            return;
        }
        this.retaliateIn = FacetRules.RETALIATION_COOLDOWN_TICKS;
        if (source.getEntity() instanceof LivingEntity attacker && attacker.isAlive()
                && this.distanceToSqr(attacker) <= RETALIATION_RANGE * RETALIATION_RANGE) {
            attacker.hurtServer(level, this.damageSources().thorns(this), RETALIATION_DAMAGE);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, attacker.getX(), attacker.getY() + 1.0D,
                    attacker.getZ(), 8, 0.3D, 0.4D, 0.3D, 0.05D);
        }
    }

    private void burst(ServerLevel level, ParticleOptions particle, int count) {
        level.sendParticles(particle, this.getX(), this.getY() + this.getBbHeight() * 0.5D, this.getZ(), count,
                1.4D, 1.6D, 1.4D, 0.05D);
    }

    /** Crystal through and through: poison and wither find nothing to take hold of. */
    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        if (effect.getEffect().is(MobEffects.POISON) || effect.getEffect().is(MobEffects.WITHER)) {
            return false;
        }
        return super.canBeAffected(effect);
    }

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.FACET_COLOSSUS_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ModSounds.FACET_COLOSSUS_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.FACET_COLOSSUS_DEATH.get();
    }

    @Override
    public CreatureSound summonSound() {
        return ModSounds.FACET_COLOSSUS_SUMMON;
    }
}
