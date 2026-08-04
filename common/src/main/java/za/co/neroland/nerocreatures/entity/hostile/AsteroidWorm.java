package za.co.neroland.nerocreatures.entity.hostile;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerocreatures.entity.ai.BurrowGoal;
import za.co.neroland.nerocreatures.entity.ai.Burrower;
import za.co.neroland.nerocreatures.entity.ai.EmergeAttackGoal;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;

/**
 * <b>Asteroid Worm</b> — the ambush predator of the rubble fields.
 *
 * <p>The worm spends most of a fight underground. {@link BurrowGoal} takes it down and stalks the
 * target from below; {@link EmergeAttackGoal} brings it back up with a telegraphed strike. This
 * class owns the half of that contract the goals must not guess at ({@link Burrower}): while
 * burrowed the worm takes no damage, cannot be picked as a target, and is invisible on the client.
 *
 * <h2>Why one entity and not a chain of segments</h2>
 *
 * <p>A "real" segmented worm is a chain of server entities with per-part hitboxes and a
 * follow-the-leader solver — historically the single most bug-prone thing in a mob mod, and the
 * plan calls for a conservative first cut. So on the <b>server</b> the worm is exactly one entity
 * with one deliberately long, low hitbox; the segments exist only in the <b>client model</b>, which
 * undulates a stack of body cubes. Everything a player can hit or be hit by is the one box. If a
 * future release wants true per-segment hitboxes, that is an additive change to this class and the
 * renderer, not a rewrite of the goals.
 *
 * <p>Spawns in Core's {@code neroland:space/asteroid_biomes}. Drops {@code worm_chitin} and
 * {@code ore_slurry}.
 *
 * <p>Server-authoritative: the burrow flag is set only by the two goals, only on the server, and is
 * mirrored to the client purely so the renderer can hide the body. No player data is involved
 * (POPIA/GDPR).
 */
public class AsteroidWorm extends NeroCreatureEntity implements Enemy, Burrower {

    /**
     * Mirrored to the client so the renderer can hide a submerged worm. Server-authoritative: the
     * client never writes it.
     */
    private static final EntityDataAccessor<Boolean> DATA_BURROWED =
            SynchedEntityData.defineId(AsteroidWorm.class, EntityDataSerializers.BOOLEAN);

    /** Distance (blocks) at which the worm stops travelling underground and prepares to surface. */
    private static final double EMERGE_RANGE = 6.0D;

    /** Reach (blocks) of the surfacing strike. */
    private static final double STRIKE_RANGE = 4.0D;

    /** Ticks of "the ground is moving" warning before the strike lands. */
    private static final int TELEGRAPH_TICKS = 20;

    /** Navigation speed multiplier while submerged — rock is no obstacle to a worm. */
    private static final double BURROWED_SPEED = 1.25D;

    public AsteroidWorm(EntityType<? extends AsteroidWorm> type, Level level) {
        super(type, level, CreatureTier.GREATER);
    }

    /**
     * {@link CreatureTier#GREATER}: an elite. Heavily armoured, hits hard, and deliberately slow
     * above ground — the whole point is that it is dangerous when it chooses the moment, not when
     * you do.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return NeroCreatureEntity.createCreatureAttributes(CreatureTier.GREATER)
                .add(Attributes.MAX_HEALTH, 70.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.22D)
                .add(Attributes.ATTACK_DAMAGE, 9.0D)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.7D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_BURROWED, false);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // Emerge outranks burrow: once the worm is under and in range, surfacing wins.
        this.goalSelector.addGoal(1, new EmergeAttackGoal<>(this, STRIKE_RANGE, TELEGRAPH_TICKS));
        this.goalSelector.addGoal(2, new BurrowGoal<>(this, EMERGE_RANGE, BURROWED_SPEED));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(7, new RandomStrollGoal(this, 0.7D));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // --- Burrower contract --------------------------------------------------

    @Override
    public boolean isBurrowed() {
        return this.entityData.get(DATA_BURROWED);
    }

    @Override
    public void setBurrowed(boolean burrowed) {
        this.entityData.set(DATA_BURROWED, burrowed);
        // A submerged worm must not be shoved around by the collision it is no longer part of.
        this.setNoGravity(burrowed);
        if (!burrowed) {
            this.setDeltaMovement(this.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D));
        }
    }

    /**
     * Nothing reaches a worm under the rock — except the sources vanilla says nothing can be
     * protected from ({@code /kill}, the void). Without that exception a burrowed worm in an
     * unloadable spot would be genuinely unkillable.
     */
    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (this.isBurrowed() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        return super.hurtServer(level, source, amount);
    }

    /** Other mobs and auto-targeting must not lock onto something they cannot reach. */
    @Override
    public boolean canBeSeenAsEnemy() {
        return !this.isBurrowed() && super.canBeSeenAsEnemy();
    }

    /**
     * The renderer's cue: an invisible entity is not drawn, so a submerged worm leaves nothing but
     * the telegraph particles above it — no floating body.
     */
    @Override
    public boolean isInvisible() {
        return this.isBurrowed() || super.isInvisible();
    }

    /** A worm under the rock makes no walking noise and kicks up no step particles. */
    @Override
    public boolean isSilent() {
        return this.isBurrowed() || super.isSilent();
    }

    // --- Persistence --------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putBoolean("Burrowed", this.isBurrowed());
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.setBurrowed(input.getBooleanOr("Burrowed", false));
    }

    // --- Sounds -------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.RAVAGER_AMBIENT;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.RAVAGER_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.RAVAGER_DEATH;
    }
}
