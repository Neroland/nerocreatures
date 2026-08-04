package za.co.neroland.nerocreatures.entity.mechanical;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;
import za.co.neroland.nerocreatures.registry.ModItems;

/**
 * <b>Rogue Android</b> — the heavy frame: a walking security system that has stopped taking orders.
 *
 * <h2>The shield, and the window behind it</h2>
 *
 * <p>A frame carries a hit-counting shield rather than a damage-absorbing one, because a hit counter
 * is legible: the player can see progress being made without a bar. The cycle is
 *
 * <ol>
 *   <li><b>Shielded.</b> Each incoming hit is absorbed whole — no damage, no knockback — and burns
 *       one of {@value #SHIELD_CHARGES} charges, with a spark and a clang so the hit still
 *       <em>reads</em> as landing.</li>
 *   <li><b>Break.</b> The charge that empties the shield shatters it: a louder break, a burst of
 *       sparks, and the frame is staggered.</li>
 *   <li><b>Stagger.</b> For {@value #STAGGER_TICKS} ticks the frame moves at half speed and takes
 *       {@value #STAGGER_DAMAGE_MULTIPLIER}&times; damage. This is the whole fight: everything you
 *       do outside this window is a countdown to it.</li>
 *   <li><b>Recharge.</b> When the window closes the shield comes back at full charges, and the
 *       countdown starts again.</li>
 * </ol>
 *
 * <p>{@code /kill} and the void still work while shielded — anything tagged
 * {@code minecraft:bypasses_invulnerability} skips the shield entirely, exactly as it does for the
 * burrowed Asteroid Worm, so nothing here can produce an unkillable entity.
 *
 * <h2>Salvage</h2>
 *
 * <p>Finish a frame <b>during</b> the stagger window with anything that is not fire and you recover
 * one extra {@code salvaged_circuitry} — the boards survive a clean shutdown and do not survive
 * being burned out. This is a bonus roll in the {@code wiki/Drop-Map.md} sense and is scaled by
 * {@code dropRateMultiplier}; the loot table itself is untouched. It is a nod to the full salvage
 * system, which is deferred.
 *
 * <p>{@link CreatureTier#GREATER}. Drops {@code salvaged_circuitry} and, less often, an
 * {@code android_core}. Natural spawns are rare, in Core's {@code neroland:space/dark_biomes}; the
 * intended source is deliberate placement — see {@link AndroidSpawner}.
 *
 * <p>Server-authoritative: shield state changes only on the server, and the feedback the client sees
 * is ordinary broadcast particles and sounds. No player data is involved (POPIA/GDPR).
 */
public class RogueAndroid extends AbstractAndroid {

    /** Hits the shield absorbs before it breaks. */
    private static final int SHIELD_CHARGES = 4;

    /** Length of the vulnerable window after a break, in ticks. */
    private static final int STAGGER_TICKS = 60;

    /** Damage multiplier applied while staggered. */
    private static final float STAGGER_DAMAGE_MULTIPLIER = 1.5F;

    /** Horizontal movement retained per tick while staggered. */
    private static final double STAGGER_DRAG = 0.5D;

    /** Extra {@code salvaged_circuitry} for a clean (non-fire) kill during the window. */
    private static final int SALVAGE_BONUS = 1;

    /** Charges left before the shield breaks. Zero means the frame is currently exposed. */
    private int shieldCharges = SHIELD_CHARGES;

    /** Ticks left in the vulnerable window. Zero means the shield is up or recharging. */
    private int staggerTicks;

    public RogueAndroid(EntityType<? extends RogueAndroid> type, Level level) {
        super(type, level, CreatureTier.GREATER);
    }

    /**
     * {@link CreatureTier#GREATER}. The health pool is modest for the band on purpose — the shield,
     * not the health bar, is what makes the frame take a while.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return NeroCreatureEntity.createCreatureAttributes(CreatureTier.GREATER)
                .add(Attributes.MAX_HEALTH, 70.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.20D)
                .add(Attributes.ATTACK_DAMAGE, 10.0D)
                .add(Attributes.ARMOR, 10.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 0.8D)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        this.goalSelector.addGoal(7, new WaterAvoidingRandomStrollGoal(this, 0.7D));
        this.goalSelector.addGoal(8, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // --- Shield -------------------------------------------------------------

    /** Whether the shield still has charges. */
    public boolean isShielded() {
        return this.shieldCharges > 0;
    }

    /** Whether the frame is inside the vulnerable window after a break. */
    public boolean isStaggered() {
        return this.staggerTicks > 0;
    }

    @Override
    public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurtServer(level, source, amount);
        }
        if (this.shieldCharges > 0) {
            this.shieldCharges--;
            if (this.shieldCharges == 0) {
                this.breakShield(level);
            } else {
                this.absorbHit(level);
            }
            return false;
        }
        float scaled = this.isStaggered() ? amount * STAGGER_DAMAGE_MULTIPLIER : amount;
        return super.hurtServer(level, source, scaled);
    }

    /** A hit that the shield ate: one spark burst and a clang. */
    private void absorbHit(ServerLevel level) {
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(),
                this.getY() + this.getBbHeight() * 0.6D, this.getZ(), 6, 0.3D, 0.3D, 0.3D, 0.02D);
        this.playSound(SoundEvents.ANVIL_LAND, 0.4F, 1.6F);
    }

    /** The charge that emptied the shield: a break, and the window opens. */
    private void breakShield(ServerLevel level) {
        this.staggerTicks = STAGGER_TICKS;
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(),
                this.getY() + this.getBbHeight() * 0.6D, this.getZ(), 24, 0.5D, 0.5D, 0.5D, 0.08D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(),
                this.getY() + this.getBbHeight() * 0.6D, this.getZ(), 8, 0.3D, 0.3D, 0.3D, 0.01D);
        this.playSound(SoundEvents.GLASS_BREAK, 1.0F, 0.7F);
    }

    /**
     * Runs the stagger window and the recharge. Cheap by construction: one counter, and while the
     * window is open one multiply on the existing movement vector — no navigation calls, no
     * attribute modifiers to add and remove.
     */
    @Override
    protected void customServerAiStep(ServerLevel level) {
        super.customServerAiStep(level);
        if (this.staggerTicks <= 0) {
            return;
        }
        this.setDeltaMovement(this.getDeltaMovement().multiply(STAGGER_DRAG, 1.0D, STAGGER_DRAG));
        if (--this.staggerTicks == 0) {
            this.shieldCharges = SHIELD_CHARGES;
            this.playSound(SoundEvents.COPPER_GOLEM_SPIN, 0.6F, 1.2F);
        }
    }

    // --- Loot ---------------------------------------------------------------

    /**
     * The salvage nod: a frame shut down cleanly while exposed yields one extra board. Fire kills
     * yield nothing extra — the boards burn.
     */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        super.dropCustomDeathLoot(level, source, recentlyHit);
        if (!this.isStaggered() || source.is(DamageTypeTags.IS_FIRE)) {
            return;
        }
        int bonus = (int) Math.floor(SALVAGE_BONUS * NeroCreaturesConfig.DROP_RATE_MULTIPLIER.get());
        if (bonus > 0) {
            this.spawnAtLocation(level, new ItemStack(ModItems.SALVAGED_CIRCUITRY.get(), bonus));
        }
    }

    // --- Persistence --------------------------------------------------------

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("ShieldCharges", this.shieldCharges);
        output.putInt("StaggerTicks", this.staggerTicks);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.shieldCharges = Math.clamp(input.getIntOr("ShieldCharges", SHIELD_CHARGES),
                0, SHIELD_CHARGES);
        this.staggerTicks = Math.clamp(input.getIntOr("StaggerTicks", 0), 0, STAGGER_TICKS);
    }

    // --- Sounds -------------------------------------------------------------

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.COPPER_GOLEM_STEP;
    }
}
