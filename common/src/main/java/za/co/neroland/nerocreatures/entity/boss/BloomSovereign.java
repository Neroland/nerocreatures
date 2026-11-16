package za.co.neroland.nerocreatures.entity.boss;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.boss.BossController;
import za.co.neroland.nerocreatures.boss.BossPhase;
import za.co.neroland.nerocreatures.boss.BossSummons;
import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;
import za.co.neroland.nerocreatures.entity.greenxertz.BloomHost;
import za.co.neroland.nerocreatures.entity.greenxertz.SovereignBrood;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;
import za.co.neroland.nerocreatures.registry.ModSounds;
import za.co.neroland.nerocreatures.sound.CreatureSound;
import za.co.neroland.nerocreatures.registry.ModParticles;

/**
 * <b>Bloom Sovereign</b> — Greenxertz's signature boss (altar-summoned only).
 *
 * <p>Built on {@link BossController}. She is <b>rooted</b>: no movement speed, no movement goals,
 * total knockback resistance. The fight (numbers in {@link SovereignRules}):
 *
 * <ul>
 *   <li><b>Spore pods</b> — {@code bloom_host} in pod form, entities never blocks,
 *       regrown on a ring around her roots up to a per-phase count under a hard cap of
 *       {@value SovereignRules#MAX_PODS}. They have no AI and little health: they are there to be
 *       destroyed.</li>
 *   <li><b>Heal</b> ({@code ability_2}, 60 ticks): pods pulse; at the end of the channel she heals per
 *       pod still alive. No pods, no heal.</li>
 *   <li><b>Bark</b> — she takes damage only from an attacker within
 *       {@value SovereignRules#BARK_REACH} blocks that she can see (the lash's reach), so a bow from
 *       beyond her vine or a blow from under an overhang glances off. Pods and sporelings are not
 *       shielded.</li>
 *   <li><b>Infect</b> ({@code ability}, 30 ticks, hit at 14): a vine lash at her target that
 *       poisons it and raises a sporeling at its feet — her adds — capped per phase (hard cap
 *       {@value SovereignRules#MAX_SPORELINGS}) and inside the shared sporeling dimension cap.
 *       {@code bloomHostSporelings=false} = the lash without the add.</li>
 * </ul>
 *
 * <p>When the fight ends (death or discard, once — {@link FightEndLatch}) every pod and sporeling she
 * grew is removed without drops. Sporelings are never saved; saved pods are re-adopted from the arena
 * after a reload. Server-authoritative; nothing about a player is logged or stored here.
 */
public class BloomSovereign extends NeroBossEntity {

    private final PhaseClock clock = new PhaseClock();
    private final FightEndLatch latch = new FightEndLatch();
    private final List<Integer> pods = new ArrayList<>();
    private final List<Integer> sporelings = new ArrayList<>();

    private int podTick;
    private long barkCueAt;

    public BloomSovereign(EntityType<? extends BloomSovereign> type, Level level) {
        super(type, level, CreatureTier.APEX);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return NeroCreatureEntity.createCreatureAttributes(CreatureTier.APEX)
                .add(Attributes.MAX_HEALTH, 340.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.0D)
                .add(Attributes.ATTACK_DAMAGE, 8.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.ARMOR_TOUGHNESS, 4.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    /** The boss id, as used by {@link BossSummons} and the Apex Altar. */
    public static Identifier bossId() {
        return Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "bloom_sovereign");
    }

    // --- summoning (the Apex Altar's entry point) ------------------------------------------

    /** Adds the Sovereign to {@link BossSummons}' summonable set. Idempotent. */
    public static void registerSummon() {
        BossSummons.register(bossId(), BloomSovereign::create);
    }

    /**
     * Summons a Sovereign at {@code pos} as a <b>contribution-tracked</b> fight. Delegates to
     * {@link BossSummons#summon} (fight bound before the first hit, population caps respected).
     *
     * @param initiator the summoning player, recorded as a zero-damage participant only; may be null
     */
    public static Optional<NeroBossEntity> summon(ServerLevel level, BlockPos pos, @Nullable UUID initiator) {
        registerSummon();
        return BossSummons.summon(level, pos, bossId(), initiator);
    }

    @Nullable
    static NeroBossEntity create(ServerLevel level, BlockPos pos) {
        Identifier id = bossId();
        if (!BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
            return null;
        }
        Entity entity = BuiltInRegistries.ENTITY_TYPE.getValue(id).create(level, EntitySpawnReason.EVENT);
        return entity instanceof BloomSovereign boss ? boss : null;
    }

    // --- goals and phases -------------------------------------------------------------------

    /** Rooted: looking and targeting only. Her attacks are phase goals. */
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 24.0F));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, false));
    }

    @Override
    protected BossController createController() {
        return new BossController(this, this, bossId(),
                Component.translatable("entity.nerocreatures.bloom_sovereign"),
                BossEvent.BossBarColor.GREEN, SovereignRules.ARENA_RADIUS, this.phases());
    }

    private List<BossPhase> phases() {
        return List.of(
                BossPhase.of("budding",
                        Component.translatable("boss.nerocreatures.bloom_sovereign.budding"), 1.0F,
                        () -> this.phaseGoals(1)),
                new BossPhase("full_bloom",
                        Component.translatable("boss.nerocreatures.bloom_sovereign.full_bloom"),
                        SovereignRules.PHASE_2, () -> this.phaseGoals(2), this::onPhaseEntered),
                new BossPhase("wilting_heart",
                        Component.translatable("boss.nerocreatures.bloom_sovereign.wilting_heart"),
                        SovereignRules.PHASE_3, () -> this.phaseGoals(3), this::onPhaseEntered));
    }

    private List<BossPhase.PhaseGoal> phaseGoals(int phase) {
        return List.of(BossPhase.goal(1, new HealGoal()), BossPhase.goal(2, new InfectGoal(phase)));
    }

    private void onPhaseEntered(ServerLevel level) {
        this.podTick = 0;
        ModSounds.BLOOM_SOVEREIGN_PHASE.play(this);
        level.sendParticles(ModParticles.SPORE_PUFF.get(), this.getX(), this.getY() + 3.0D, this.getZ(),
                60, 2.0D, 1.0D, 2.0D, 0.0D);
    }

    // --- per tick ---------------------------------------------------------------------------

    @Override
    protected void onFirstServerTick(ServerLevel level) {
        super.onFirstServerTick(level);
        this.adoptPods(level);
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
        this.clock.tick(phase);
        this.visuals().setPhase(phase);
        if (this.clock.busy()) {
            return;
        }
        BlockPos anchor = this.controller.anchor();
        if (anchor != null && --this.podTick <= 0) {
            this.podTick = SovereignRules.podInterval(phase);
            this.growPods(level, anchor, phase);
        }
    }

    // --- pods -------------------------------------------------------------------------------

    /** Live pods, pruning the dead ones and syncing their phase pose. */
    int livePods(ServerLevel level) {
        int phase = Math.max(1, this.controller.phaseNumber());
        this.pods.removeIf(id -> !(level.getEntity(id) instanceof BloomHost pod && pod.isAlive() && pod.isPod()));
        for (int id : this.pods) {
            if (level.getEntity(id) instanceof BloomHost pod) {
                pod.visuals().setPhase(phase);
            }
        }
        return this.pods.size();
    }

    private int liveSporelings(ServerLevel level) {
        this.sporelings.removeIf(id -> !(level.getEntity(id) instanceof BloomHost add && add.isAlive() && add.isSporeling()));
        return this.sporelings.size();
    }

    private void growPods(ServerLevel level, BlockPos anchor, int phase) {
        int wanted = SovereignRules.podsToGrow(phase, this.livePods(level));
        int slots = SovereignRules.MAX_PODS * 2;
        int offset = this.random.nextInt(slots);
        for (int i = 0; i < slots && wanted > 0 && this.pods.size() < SovereignRules.MAX_PODS; i++) {
            double angle = ((i + offset) % slots) * (Math.PI * 2.0D / slots);
            BlockPos column = BlockPos.containing(
                    anchor.getX() + 0.5D + Math.cos(angle) * SovereignRules.POD_RING,
                    anchor.getY(),
                    anchor.getZ() + 0.5D + Math.sin(angle) * SovereignRules.POD_RING);
            BlockPos ground = findGround(level, column);
            if (ground == null) {
                continue;
            }
            BloomHost pod = SovereignBrood.pod(level, ground.getX() + 0.5D, ground.getY(), ground.getZ() + 0.5D,
                    SovereignRules.POD_HEALTH);
            if (pod == null) {
                return;
            }
            pod.visuals().setPhase(phase);
            if (level.addFreshEntity(pod)) {
                this.pods.add(pod.getId());
                wanted--;
                level.sendParticles(ModParticles.SPORE_PUFF.get(), pod.getX(), pod.getY() + 0.5D, pod.getZ(),
                        12, 0.3D, 0.3D, 0.3D, 0.0D);
            }
        }
    }

    /** Two blocks of air over a sturdy floor near the anchor height, with no pod already there. */
    @Nullable
    private static BlockPos findGround(ServerLevel level, BlockPos column) {
        for (int dy = 3; dy >= -4; dy--) {
            BlockPos at = column.above(dy);
            BlockPos below = at.below();
            if (level.isLoaded(at) && level.getBlockState(at).isAir() && level.getBlockState(at.above()).isAir()
                    && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                AABB space = new AABB(at.getX(), at.getY(), at.getZ(), at.getX() + 1.0D, at.getY() + 2.0D, at.getZ() + 1.0D);
                return level.getEntitiesOfClass(LivingEntity.class, space).isEmpty() ? at : null;
            }
        }
        return null;
    }

    /** After a load: adopt saved pods in the arena (up to the cap); discard any beyond it. */
    private void adoptPods(ServerLevel level) {
        BlockPos anchor = this.controller.anchor();
        if (anchor == null || this.controller.isDefeated()) {
            return;
        }
        double r = SovereignRules.POD_RING + 3.0D;
        AABB box = new AABB(anchor.getX() - r, anchor.getY() - 5.0D, anchor.getZ() - r,
                anchor.getX() + r + 1.0D, anchor.getY() + 5.0D, anchor.getZ() + r + 1.0D);
        for (BloomHost pod : level.getEntitiesOfClass(BloomHost.class, box, host -> host.isPod() && host.isAlive())) {
            if (this.pods.contains(pod.getId())) {
                continue;
            }
            if (this.pods.size() < SovereignRules.MAX_PODS) {
                this.pods.add(pod.getId());
            } else {
                pod.discard();
            }
        }
    }

    /** Removes every pod and sporeling she grew, without drops. Runs once per fight. */
    private void clearBrood(ServerLevel level) {
        for (int id : this.pods) {
            if (level.getEntity(id) instanceof BloomHost pod && pod.isPod()) {
                pod.discard();
            }
        }
        for (int id : this.sporelings) {
            if (level.getEntity(id) instanceof BloomHost add && add.isSporeling()) {
                add.discard();
            }
        }
        this.pods.clear();
        this.sporelings.clear();
    }

    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        if (this.level() instanceof ServerLevel level && this.latch.claim(FightEndLatch.Exit.DEATH)) {
            this.clearBrood(level);
        }
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        FightEndLatch.Exit exit = reason == Entity.RemovalReason.UNLOADED_TO_CHUNK
                || reason == Entity.RemovalReason.UNLOADED_WITH_PLAYER
                || reason == Entity.RemovalReason.CHANGED_DIMENSION
                ? FightEndLatch.Exit.UNLOAD : FightEndLatch.Exit.DISCARD;
        if (this.level() instanceof ServerLevel level && this.latch.claim(exit)) {
            this.clearBrood(level);
        }
        super.remove(reason);
    }

    // --- bark -------------------------------------------------------------------------------

    /**
     * Her bark: damage from an attacker she cannot answer (beyond the lash's reach, or out of her
     * sight) glances off. Damage with no attacker is already refused by {@link NeroBossEntity};
     * damage that bypasses invulnerability always lands.
     */
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
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && source.getEntity() instanceof LivingEntity attacker
                && !SovereignRules.barkLetsThrough(this.distanceTo(attacker), this.hasLineOfSight(attacker))) {
            long now = level.getGameTime();
            if (now - this.barkCueAt >= SovereignRules.BARK_CUE_TICKS || now < this.barkCueAt) {
                this.barkCueAt = now;
                level.playSound(null, this.blockPosition(), SoundEvents.MOSS_BREAK, SoundSource.HOSTILE, 1.4F, 1.6F);
                level.sendParticles(ModParticles.SPORE_PUFF.get(), this.getX(), this.getY() + 2.0D, this.getZ(),
                        8, 1.0D, 1.0D, 1.0D, 0.0D);
            }
            return false;
        }
        return super.hurtServer(level, source, amount);
    }

    // --- abilities --------------------------------------------------------------------------

    /** Heal channel ({@code ability_2}, 60 ticks): heals per pod alive at the end. */
    final class HealGoal extends Goal {

        private int cooldown = SovereignRules.HEAL_COOLDOWN / 2;
        private int tick;
        private boolean running;

        HealGoal() {
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.cooldown > 0) {
                this.cooldown--;
                return false;
            }
            if (BloomSovereign.this.clock.busy() || !(BloomSovereign.this.level() instanceof ServerLevel level)) {
                return false;
            }
            return SovereignRules.shouldHeal(BloomSovereign.this.getMaxHealth(), BloomSovereign.this.getHealth(),
                    BloomSovereign.this.livePods(level));
        }

        @Override
        public boolean canContinueToUse() {
            return this.running && BloomSovereign.this.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            this.running = true;
            this.tick = 0;
            BloomSovereign.this.visuals().playPose(VisualPose.ABILITY_2, SovereignRules.HEAL_CLIP_TICKS);
            ModSounds.BLOOM_SOVEREIGN_HEAL.play(BloomSovereign.this);
        }

        @Override
        public void stop() {
            this.running = false;
            this.cooldown = BossController.scaleCooldown(SovereignRules.HEAL_COOLDOWN);
        }

        @Override
        public void tick() {
            if (!(BloomSovereign.this.level() instanceof ServerLevel level)) {
                this.running = false;
                return;
            }
            if ((this.tick % 10) == 0) {
                for (int id : BloomSovereign.this.pods) {
                    if (level.getEntity(id) instanceof BloomHost pod) {
                        level.sendParticles(ModParticles.BLOOM_MOTE.get(), pod.getX(), pod.getY() + 0.8D, pod.getZ(),
                                3, 0.2D, 0.3D, 0.2D, 0.0D);
                    }
                }
            }
            if (++this.tick >= SovereignRules.HEAL_CLIP_TICKS) {
                float amount = SovereignRules.healAmount(BloomSovereign.this.getMaxHealth(),
                        BloomSovereign.this.getHealth(), BloomSovereign.this.livePods(level));
                if (amount > 0.0F) {
                    BloomSovereign.this.heal(amount);
                    level.sendParticles(ModParticles.BLOOM_MOTE.get(), BloomSovereign.this.getX(),
                            BloomSovereign.this.getY() + 3.5D, BloomSovereign.this.getZ(), 4, 0.8D, 0.4D, 0.8D, 0.0D);
                }
                this.running = false;
            }
        }
    }

    /** Infect ({@code ability}, 30 ticks, hit at 14): vine lash, poison, and a sporeling add. */
    final class InfectGoal extends Goal {

        private final int phase;
        private int cooldown;
        private int tick;
        private boolean running;

        InfectGoal(int phase) {
            this.phase = phase;
            this.cooldown = BossController.scaleCooldown(SovereignRules.infectCooldown(phase)) / 2;
            this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (this.cooldown > 0) {
                this.cooldown--;
                return false;
            }
            LivingEntity target = BloomSovereign.this.getTarget();
            return !BloomSovereign.this.clock.busy() && target != null && target.isAlive()
                    && BloomSovereign.this.distanceTo(target) <= SovereignRules.INFECT_RANGE
                    && BloomSovereign.this.hasLineOfSight(target);
        }

        @Override
        public boolean canContinueToUse() {
            return this.running && BloomSovereign.this.isAlive();
        }

        @Override
        public boolean requiresUpdateEveryTick() {
            return true;
        }

        @Override
        public void start() {
            this.running = true;
            this.tick = 0;
            BloomSovereign.this.visuals().playPose(VisualPose.ABILITY, SovereignRules.INFECT_CLIP_TICKS);
        }

        @Override
        public void stop() {
            this.running = false;
            this.cooldown = BossController.scaleCooldown(SovereignRules.infectCooldown(this.phase));
        }

        @Override
        public void tick() {
            if (!(BloomSovereign.this.level() instanceof ServerLevel level)) {
                this.running = false;
                return;
            }
            LivingEntity target = BloomSovereign.this.getTarget();
            if (target != null) {
                BloomSovereign.this.getLookControl().setLookAt(target, 30.0F, 30.0F);
            }
            if (this.tick == SovereignRules.INFECT_HIT_TICK && target != null && target.isAlive()
                    && BloomSovereign.this.distanceTo(target) <= SovereignRules.INFECT_RANGE + 2.0D
                    && BloomSovereign.this.hasLineOfSight(target)) {
                this.lash(level, target);
            }
            if (++this.tick >= SovereignRules.INFECT_CLIP_TICKS) {
                this.running = false;
            }
        }

        private void lash(ServerLevel level, LivingEntity target) {
            Vec3 from = BloomSovereign.this.position().add(0.0D, BloomSovereign.this.getBbHeight() * 0.7D, 0.0D);
            Vec3 to = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
            for (int i = 0; i <= 10; i++) {
                Vec3 at = from.lerp(to, i / 10.0D);
                level.sendParticles(ModParticles.SPORE_MOTE.get(), at.x, at.y, at.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
            }
            ModSounds.BLOOM_SOVEREIGN_INFECT.play(BloomSovereign.this);
            DamageSource source = BloomSovereign.this.damageSources().mobAttack(BloomSovereign.this);
            if (!target.hurtServer(level, source, SovereignRules.infectDamage(this.phase))) {
                return;
            }
            target.addEffect(new MobEffectInstance(MobEffects.POISON, SovereignRules.INFECT_POISON_TICKS, 0),
                    BloomSovereign.this);
            if (SovereignRules.mayRaiseSporeling(this.phase, BloomSovereign.this.liveSporelings(level),
                    NeroCreaturesConfig.BLOOM_HOST_SPORELINGS.get())) {
                BloomHost add = SovereignBrood.sporeling(level, target.getX(), target.getY(), target.getZ());
                if (add != null) {
                    BloomSovereign.this.sporelings.add(add.getId());
                    add.setTarget(target);
                }
            }
        }
    }

    // --- sounds -----------------------------------------------------------------------------

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return ModSounds.BLOOM_SOVEREIGN_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ModSounds.BLOOM_SOVEREIGN_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.BLOOM_SOVEREIGN_DEATH.get();
    }

    @Override
    public CreatureSound summonSound() {
        return ModSounds.BLOOM_SOVEREIGN_SUMMON;
    }
}
