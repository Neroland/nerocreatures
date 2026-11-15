package za.co.neroland.nerocreatures.entity.greenxertz;

import java.util.ArrayList;
import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.Npc;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;
import za.co.neroland.nerocreatures.entity.boss.NeroBossEntity;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;
import za.co.neroland.nerocreatures.world.CreatureOwners;
import za.co.neroland.nerocreatures.registry.ModSounds;

/**
 * <b>Bloom Host</b> (Greenxertz, hostile) — a stooped walker carrying one huge lopsided flower cap.
 * One entity type with three synced {@code form}s (decision 001): {@code host} (0), {@code sporeling}
 * (1: the cap alone on stub legs; the model is authored small) and {@code pod} (2: Bloom Sovereign's
 * spore pod, configured by the boss through {@link #becomePod()}; rooted, never raises).
 *
 * <h2>Ability: sporelings (decision 006, key {@code bloomHostSporelings}, read directly)</h2>
 *
 * <p>A host hunts players and, every 2 s at most, the wild mobs of {@code #nerocreatures:sporeling_hosts}
 * near it. A mob it kills itself rises as a short-lived sporeling that fights beside it: a spore strand
 * runs from the corpse to the host and the host's {@code ability} pose plays. The rules and caps are in
 * {@link BloomRules}: never from players, owned, named, persistent, boss or villager-type mobs; at most
 * 3 live per host and 24 per dimension; 30 s of life; no drops and no experience; never saved. Sporelings
 * only bite (no status effect on players). Killing the host is the counter: it drops {@code bloom_heart}.
 */
public class BloomHost extends NeroCreatureEntity implements Enemy {

    public static final TagKey<EntityType<?>> SPORELING_HOSTS = TagKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "sporeling_hosts"));
    private static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath("c", "bosses"));

    /** Live sporelings per dimension (ids only, never saved). */
    static final DimensionTally SPORELINGS = new DimensionTally();

    private static final int PREY_SCAN_INTERVAL = 40;
    private static final double SPORELING_HEALTH = 6.0D;
    private static final double SPORELING_DAMAGE = 2.0D;
    private static final double SPORELING_SPEED = 0.3D;

    /** This host's live sporelings, by runtime entity id. Not saved. */
    private final List<Integer> raised = new ArrayList<>(BloomRules.PER_HOST);
    /** A sporeling's age in ticks. */
    private int life;
    /** The form the current hitbox was sized for (both sides; -1 = not sized yet). */
    private int sizedForm = -1;

    public BloomHost(EntityType<? extends BloomHost> type, Level level) {
        super(type, level, CreatureTier.COMMON);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return NeroCreatureEntity.createCreatureAttributes(CreatureTier.COMMON)
                .add(Attributes.MAX_HEALTH, 28.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.23D)
                .add(Attributes.ATTACK_DAMAGE, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.ARMOR, 2.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, false));
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, LivingEntity.class,
                PREY_SCAN_INTERVAL, true, false, this::isSporelingPrey));
    }

    /** 1.21.1 target predicate. */
    private boolean isSporelingPrey(LivingEntity target) {
        return this.form() == BloomRules.FORM_HOST
                && BloomRules.eligible(victimFacts(target), NeroCreaturesConfig.BLOOM_HOST_INFECTS_LIVESTOCK.get());
    }

    /** 1.21.2+ target selector. */
    private boolean isSporelingPrey(LivingEntity target, ServerLevel level) {
        return this.isSporelingPrey(target);
    }

    // --- forms --------------------------------------------------------------------

    public int form() {
        return BloomRules.form(this.visuals().form());
    }

    public boolean isSporeling() {
        return this.form() == BloomRules.FORM_SPORELING;
    }

    public boolean isPod() {
        return this.form() == BloomRules.FORM_POD;
    }

    /** Turns this (fresh) entity into a sporeling: small, weak, short-lived, never saved. */
    void becomeSporeling() {
        this.visuals().setForm(BloomRules.FORM_SPORELING);
        setBase(this.getAttribute(Attributes.SCALE), BloomRules.SPORELING_SCALE);
        setBase(this.getAttribute(Attributes.MAX_HEALTH), SPORELING_HEALTH);
        setBase(this.getAttribute(Attributes.ATTACK_DAMAGE), SPORELING_DAMAGE);
        setBase(this.getAttribute(Attributes.MOVEMENT_SPEED), SPORELING_SPEED);
        this.setHealth((float) SPORELING_HEALTH);
        this.xpReward = 0;
        this.life = 0;
        this.refreshDimensions();
    }

    /**
     * Stage 3 hook for Bloom Sovereign: turns this fresh entity into a rooted spore pod. A pod never
     * moves and never raises sporelings; the boss owns its health, caps and removal.
     */
    public void becomePod() {
        this.visuals().setForm(BloomRules.FORM_POD);
        this.xpReward = 0;
        this.refreshDimensions();
    }

    /** Sporelings and pods are smaller than the host: the hitbox follows the synced form. */
    @Override
    public EntityDimensions getDefaultDimensions(Pose pose) {
        EntityDimensions base = super.getDefaultDimensions(pose);
        // Can run inside the Entity constructor, before the visuals component exists.
        int form = this.visuals() == null ? BloomRules.FORM_HOST : this.form();
        EntityDimensions host = this.getType().getDimensions();
        float[] scale = BloomRules.dimensionScale(form, host.width(), host.height());
        return scale[0] == 1.0F && scale[1] == 1.0F ? base : base.scale(scale[0], scale[1]);
    }

    private static void setBase(@Nullable AttributeInstance attribute, double value) {
        if (attribute != null) {
            attribute.setBaseValue(value);
        }
    }

    /** Pods are rooted. */
    @Override
    protected boolean isImmobile() {
        return super.isImmobile() || this.isPod();
    }

    // --- sporeling life ------------------------------------------------------------

    @Override
    public void tick() {
        super.tick();
        int form = this.form();
        if (form != this.sizedForm) {
            // The form is synced: the client resizes when it arrives, the server after a load.
            this.sizedForm = form;
            this.refreshDimensions();
        }
        if (this.xpReward != 0 && !BloomRules.dropsLoot(form)) {
            // xpReward is not saved: a reloaded pod or sporeling would otherwise give the host's experience.
            this.xpReward = 0;
        }
        if (this.level() instanceof ServerLevel level && this.isSporeling() && this.isAlive()) {
            if (BloomRules.expired(++this.life)) {
                level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, this.getX(), this.getY() + 0.3D, this.getZ(),
                        10, 0.25D, 0.2D, 0.25D, 0.0D);
                this.discard();
            }
        }
    }

    /** Sporelings and Bloom Sovereign pods drop nothing and give no experience ({@link BloomRules#dropsLoot}). */
    @Override
    protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {
        if (BloomRules.dropsLoot(this.form())) {
            super.dropAllDeathLoot(level, source);
        }
    }

    /** Sporelings are never saved: an unload ends them, so the dimension cap needs no store. */
    @Override
    public boolean shouldBeSaved() {
        return !this.isSporeling() && super.shouldBeSaved();
    }

    @Override
    public void remove(Entity.RemovalReason reason) {
        if (this.level() instanceof ServerLevel level && this.isSporeling()) {
            SPORELINGS.remove(dimensionKey(level), this.getId());
        }
        super.remove(reason);
    }

    // --- raising -------------------------------------------------------------------

    //? if >=1.21.10 {
    @Override
    public boolean killedEntity(ServerLevel level, LivingEntity victim, DamageSource source) {
        boolean result = super.killedEntity(level, victim, source);
    //?} else {
    /*@Override
    public boolean killedEntity(ServerLevel level, LivingEntity victim) {
        boolean result = super.killedEntity(level, victim);
    *///?}
        this.tryRaise(level, victim);
        return result;
    }

    private void tryRaise(ServerLevel level, LivingEntity victim) {
        String dimension = dimensionKey(level);
        this.raised.removeIf(id -> !isLiveSporeling(level, id));
        int dimensionLive = SPORELINGS.count(dimension, id -> isLiveSporeling(level, id));
        if (!BloomRules.mayRaise(NeroCreaturesConfig.BLOOM_HOST_SPORELINGS.get(),
                NeroCreaturesConfig.BLOOM_HOST_INFECTS_LIVESTOCK.get(), this.form(), victimFacts(victim),
                this.raised.size(), dimensionLive)) {
            return;
        }
        if (!(this.getType().create(level, EntitySpawnReason.MOB_SUMMONED) instanceof BloomHost sporeling)) {
            return;
        }
        if (!SPORELINGS.tryAdd(dimension, sporeling.getId(), BloomRules.PER_DIMENSION, id -> isLiveSporeling(level, id))) {
            return;
        }
        sporeling.becomeSporeling();
        sporeling.snapTo(victim.getX(), victim.getY(), victim.getZ(), victim.getYRot(), 0.0F);
        if (!level.addFreshEntity(sporeling)) {
            SPORELINGS.remove(dimension, sporeling.getId());
            return;
        }
        this.raised.add(sporeling.getId());
        this.visuals().playPose(VisualPose.ABILITY, BloomRules.RAISE_POSE_TICKS);
        this.strand(level, victim.position());
        ModSounds.BLOOM_HOST_RAISE.play(level, victim.blockPosition(), this.getSoundSource());
    }

    /** The spore strand from corpse to host (decision 007 readability fix). */
    private void strand(ServerLevel level, Vec3 from) {
        Vec3 to = this.position().add(0.0D, this.getBbHeight() * 0.8D, 0.0D);
        for (int i = 0; i <= BloomRules.STRAND_POINTS; i++) {
            Vec3 at = from.add(0.0D, 0.3D, 0.0D).lerp(to, (double) i / BloomRules.STRAND_POINTS);
            level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, at.x, at.y, at.z, 1, 0.02D, 0.02D, 0.02D, 0.0D);
        }
    }

    private static boolean isLiveSporeling(ServerLevel level, int id) {
        return level.getEntity(id) instanceof BloomHost host && host.isAlive() && host.isSporeling();
    }

    private static String dimensionKey(ServerLevel level) {
        return level.dimension().identifier().toString();
    }

    /** Server-side facts about a mob, for {@link BloomRules#eligible}. */
    static BloomRules.Victim victimFacts(LivingEntity victim) {
        boolean persistent = victim instanceof Mob mob && (mob.isPersistenceRequired() || mob.isLeashed());
        // Bred livestock: a baby, an animal in love, or one on its breeding cooldown (positive age).
        boolean bred = victim instanceof AgeableMob ageable
                && (ageable.getAge() != 0 || victim instanceof Animal animal && animal.isInLove());
        return new BloomRules.Victim(
                victim instanceof Player,
                CreatureOwners.ownerUuid(victim) != null,
                victim.hasCustomName(),
                persistent,
                victim instanceof NeroBossEntity || victim.getType().builtInRegistryHolder().is(BOSSES),
                victim instanceof Merchant || victim instanceof Npc,
                victim.getType().builtInRegistryHolder().is(SPORELING_HOSTS) && !(victim instanceof BloomHost),
                bred);
    }

    // --- sounds (mapped vanilla until the sound pass) -----------------------------

    @Override
    @Nullable
    protected SoundEvent getAmbientSound() {
        return this.isSporeling() ? null : ModSounds.BLOOM_HOST_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ModSounds.BLOOM_HOST_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.BLOOM_HOST_DEATH.get();
    }
}
