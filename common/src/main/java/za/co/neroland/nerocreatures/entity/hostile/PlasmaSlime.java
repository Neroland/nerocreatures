package za.co.neroland.nerocreatures.entity.hostile;

import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attribute;
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
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerocreatures.entity.ai.EnergyAuraGoal;
import za.co.neroland.nerocreatures.entity.ai.SplitOnDamageBehaviour;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;
import za.co.neroland.nerocreatures.registry.ModEntities;

/**
 * <b>Plasma Slime</b> — a charged blob that punishes standing next to it and refuses to die once.
 *
 * <p>Two mechanics, both from the shared library:
 *
 * <ul>
 *   <li>{@link EnergyAuraGoal} — a slow, low-damage field. It is pressure, not a damage-per-tick
 *       blender: one pulse every two seconds, capped victims, and other mobs are excluded so a
 *       group of slimes does not melt itself.</li>
 *   <li>{@link SplitOnDamageBehaviour} — killing a large slime leaves smaller ones. Size 1 is
 *       terminal and every split is clipped to the room left in the population caps, so a slime
 *       field can never outgrow the spawn engine's budget.</li>
 * </ul>
 *
 * <p>Size is a small integer, {@value #MIN_SIZE}–{@value #MAX_SIZE}. It drives health, damage and
 * the {@code minecraft:scale} attribute — using the vanilla scale attribute means the hitbox and
 * the rendered model resize together, and the value syncs to the client for free, so the renderer
 * needs no slime-specific code at all.
 *
 * <p>Spawns in Core's {@code neroland:space/crystalline_biomes} and, more rarely,
 * {@code dark_biomes}. Drops {@code plasma_cell}.
 *
 * <p>Server-authoritative: splitting, sizing and aura damage all happen on the server. No player
 * data is involved (POPIA/GDPR).
 */
public class PlasmaSlime extends NeroCreatureEntity implements Enemy {

    /** Terminal size: a size-1 slime splits into nothing. */
    public static final int MIN_SIZE = 1;

    /** The largest a naturally spawned slime gets. */
    public static final int MAX_SIZE = 3;

    /** Children produced when a slime above the terminal size dies. */
    private static final int CHILDREN_PER_SPLIT = 2;

    /** Aura radius in blocks. */
    private static final double AURA_RADIUS = 2.5D;

    /** Damage per aura pulse. */
    private static final float AURA_DAMAGE = 1.0F;

    /** Ticks between aura pulses. 40 = once every two seconds. */
    private static final int AURA_INTERVAL_TICKS = 40;

    /**
     * Size, kept in a plain field rather than synched data: everything it feeds (health, damage,
     * scale) is either server-only or already synced by vanilla, so a second sync would be dead
     * weight on the wire.
     */
    private int size = MAX_SIZE;

    public PlasmaSlime(EntityType<? extends PlasmaSlime> type, Level level) {
        super(type, level, CreatureTier.LESSER);
    }

    /**
     * {@link CreatureTier#LESSER} chaff. The listed values are for a <b>full-size</b> slime;
     * {@link #setSize(int)} scales them down for the children.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return NeroCreatureEntity.createCreatureAttributes(CreatureTier.LESSER)
                .add(Attributes.MAX_HEALTH, 18.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.24D)
                .add(Attributes.ATTACK_DAMAGE, 3.0D)
                .add(Attributes.FOLLOW_RANGE, 20.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new EnergyAuraGoal(this, AURA_RADIUS, AURA_DAMAGE, AURA_INTERVAL_TICKS));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** This slime's size tier, {@value #MIN_SIZE}–{@value #MAX_SIZE}. */
    public int size() {
        return this.size;
    }

    /**
     * Sets the size tier and re-derives everything that follows from it. Health is set to the new
     * maximum, so a freshly split child arrives at full health rather than inheriting a fraction of
     * a bar it no longer has.
     */
    public void setSize(int size) {
        this.size = Mth.clamp(size, MIN_SIZE, MAX_SIZE);
        float fraction = (float) this.size / MAX_SIZE;

        setBase(Attributes.MAX_HEALTH, 18.0D * fraction);
        setBase(Attributes.ATTACK_DAMAGE, 3.0D * fraction);
        // The vanilla scale attribute resizes the hitbox AND the rendered model, and syncs itself.
        setBase(Attributes.SCALE, 0.5D + 0.5D * fraction);
        // Smaller blobs are quicker — the classic slime read.
        setBase(Attributes.MOVEMENT_SPEED, 0.24D + 0.06D * (1.0F - fraction));

        this.setHealth(this.getMaxHealth());
        this.xpReward = this.tier().experienceReward() * this.size;
    }

    private void setBase(Holder<Attribute> attribute, double value) {
        AttributeInstance instance = this.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(value);
        }
    }

    /**
     * Splits on death. {@link SplitOnDamageBehaviour} owns the budget: size 1 produces nothing, the
     * per-split cap applies, and the children are clipped to the room left in the per-chunk and
     * per-dimension caps.
     */
    @Override
    public void die(DamageSource cause) {
        super.die(cause);
        if (this.level() instanceof ServerLevel level && this.size > MIN_SIZE) {
            SplitOnDamageBehaviour.split(level, this, ModEntities.PLASMA_SLIME.get(),
                    this.size, CHILDREN_PER_SPLIT,
                    (child, childSize) -> child.setSize(childSize));
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(),
                    this.getY() + this.getBbHeight() * 0.5D, this.getZ(), 16, 0.4D, 0.3D, 0.4D, 0.05D);
        }
    }

    // --- Persistence --------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Size", this.size);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        int stored = input.getIntOr("Size", MAX_SIZE);
        // setSize() refills health, so restore the saved health afterwards rather than healing a
        // wounded slime every time its chunk reloads.
        float health = this.getHealth();
        this.setSize(stored);
        this.setHealth(Math.min(health, this.getMaxHealth()));
    }

    // --- Sounds -------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.SLIME_SQUISH;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.SLIME_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.SLIME_DEATH;
    }
}
