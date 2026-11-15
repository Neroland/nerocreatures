package za.co.neroland.nerocreatures.entity.nyxara;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;
import za.co.neroland.nerocreatures.entity.nyxara.ShardbackRules.Phase;
import za.co.neroland.nerocreatures.registry.ModSounds;

/**
 * <b>Shardback</b> (Nyxara; hostile) — a low charging quadruped behind a fan of five mirror plates.
 * Drops {@code mirror_scale} for the Mirror Shield.
 *
 * <ul>
 *   <li><b>Mirror fan</b> ({@link ShardbackRules#inFrontArc}). While the fan is up, damage whose
 *       source lies in the front arc is ignored and projectiles from that arc are reflected back the
 *       way they came ({@link ProjectileDeflection#REVERSE}), capped by a {@link ShardbackRules.ReflectBudget}.
 *       Hits from behind land normally; so does environmental damage with no source position.</li>
 *   <li><b>Charge</b> ({@link ShardbackChargeGoal}). A 20-tick wind-up (plates fold flat, it paws:
 *       the telegraph), a straight run that hits at most once, then a recovery. Plates stay folded
 *       ({@code form} {@value ShardbackRules#FORM_FOLDED}) for the whole charge, and it is
 *       vulnerable from every side while they are.</li>
 * </ul>
 *
 * <p>No world change (no griefing gate). Nothing is saved: a reloaded Shardback is calm with its fan up.
 * Visuals: {@code ability} = charge (wind-up and run); {@code form} 1 = plates folded.
 */
public class Shardback extends NeroCreatureEntity implements Enemy {

    private final ShardbackRules.ReflectBudget reflects = new ShardbackRules.ReflectBudget();
    private Phase phase = Phase.ROAM;

    public Shardback(EntityType<? extends Shardback> type, Level level) {
        super(type, level, CreatureTier.COMMON);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return NeroCreatureEntity.createCreatureAttributes(CreatureTier.COMMON)
                .add(Attributes.MAX_HEALTH, 30.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.25D)
                .add(Attributes.ATTACK_DAMAGE, 5.0D)
                .add(Attributes.ARMOR, 4.0D)
                .add(Attributes.FOLLOW_RANGE, 24.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.6D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new ShardbackChargeGoal(this));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.1D, false));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    protected void onFirstServerTick(ServerLevel level) {
        super.onFirstServerTick(level);
        this.visuals().setForm(0);
    }

    // --- charge state (driven by ShardbackChargeGoal) ------------------------------

    /** The current charge phase (server). */
    public Phase phase() {
        return this.phase;
    }

    /** Enters a phase; folds or raises the plates to match. */
    void enterPhase(Phase next) {
        this.phase = next;
        this.visuals().setForm(ShardbackRules.exposed(next) ? ShardbackRules.FORM_FOLDED : 0);
    }

    /** Plates folded: synced, so the client's deflection check agrees with the server's. */
    public boolean platesFolded() {
        return this.visuals().form() == ShardbackRules.FORM_FOLDED;
    }

    private boolean fromFront(double x, double z) {
        return ShardbackRules.inFrontArc(this.yBodyRot, x - this.getX(), z - this.getZ(),
                ShardbackRules.FRONT_HALF_ANGLE);
    }

    // --- the mirror fan ---------------------------------------------------------------

    @Override
    public ProjectileDeflection deflection(Projectile projectile) {
        if (!ShardbackRules.reflects(this.platesFolded(), this.fromFront(projectile.getX(), projectile.getZ()))) {
            return super.deflection(projectile);
        }
        if (this.level() instanceof ServerLevel level) {
            if (!this.reflects.tryUse(level.getGameTime())) {
                // Over the cap: still blocked by the fan (hurt ignores it), but nothing flies back.
                return super.deflection(projectile);
            }
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, projectile.getX(), projectile.getY(), projectile.getZ(),
                    6, 0.15D, 0.15D, 0.15D, 0.05D);
            ModSounds.SHARDBACK_DEFLECT.play(this);
        }
        return ProjectileDeflection.REVERSE;
    }

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
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && this.fanBlocks(source)) {
            level.playSound(null, this.blockPosition(), SoundEvents.AMETHYST_BLOCK_HIT, SoundSource.HOSTILE, 1.0F, 1.2F);
            return false;
        }
        return super.hurtServer(level, source, amount);
    }

    /** True when the raised fan stops this hit (source in the front arc, plates up). */
    private boolean fanBlocks(DamageSource source) {
        Vec3 at = source.getSourcePosition();
        boolean hasSource = at != null;
        boolean front = hasSource && this.fromFront(at.x, at.z);
        return !ShardbackRules.vulnerable(ShardbackRules.exposed(this.phase), hasSource, front);
    }

    // --- sounds (mapped vanilla until the sound pass) -------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return ModSounds.SHARDBACK_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return ModSounds.SHARDBACK_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return ModSounds.SHARDBACK_DEATH.get();
    }
}
