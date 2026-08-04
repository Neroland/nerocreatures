package za.co.neroland.nerocreatures.entity.tame;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;

import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.registry.ModItems;

/**
 * <b>Glacite Wisp</b> — the ice-lattice fauna of the frozen worlds.
 *
 * <p>A knot of glacite shards that never quite settles onto the ground: it carries a fraction of
 * normal gravity and a generous safe-fall distance, so it drifts down slopes and off ledges rather
 * than walking them. Tiny, harmless and entirely uninterested in you until you offer it
 * {@code stalker_sinew}, which is the one thing on a frozen moon worth having.
 *
 * <p>Wild wisps spawn rarely in Core's {@code neroland:space/moon_biomes} and, more rarely,
 * {@code crystalline_biomes} — the two tags a frozen crystal world sits in.
 *
 * <h2>Perk — Frost Cushion</h2>
 *
 * <p>While its owner is within eight blocks <em>and falling</em>, the wisp lends them Slow Falling.
 * That is the whole perk: no damage, no armour, no speed, nothing that shows up in a fight. It makes
 * exploring a low-gravity crater field pleasant, which is exactly the amount of power a pet in this
 * mod is allowed to have.
 *
 * <p>Cost: one check every two seconds, gated on the owner already moving downwards, so a wisp
 * standing next to a stationary player does no work at all.
 */
public class GlaciteWisp extends TameableCreature {

    /** How long a lent Slow Falling lasts, in ticks. Comfortably longer than the perk interval. */
    private static final int SLOW_FALL_TICKS = 100;

    /** Fraction of normal gravity — enough to drift, not enough to float away. */
    private static final double DRIFT_GRAVITY = 0.03D;

    /** Blocks the wisp itself may fall without harm. */
    private static final double DRIFT_SAFE_FALL = 16.0D;

    public GlaciteWisp(EntityType<? extends GlaciteWisp> type, Level level) {
        super(type, level, CreatureTier.LESSER);
    }

    /**
     * {@link CreatureTier#LESSER} and then some: a wisp is the frailest thing in the mod. Its two
     * distinctive attributes are movement ones — the drift is a pair of plain attribute values, not
     * custom movement code, which is what keeps it identical across all six build cells.
     */
    public static AttributeSupplier.Builder createAttributes() {
        return TameableCreature.createPetAttributes(CreatureTier.LESSER)
                .add(Attributes.MAX_HEALTH, 12.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.30D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 20.0D)
                .add(Attributes.GRAVITY, DRIFT_GRAVITY)
                .add(Attributes.SAFE_FALL_DISTANCE, DRIFT_SAFE_FALL);
    }

    @Override
    public Item tamingFood() {
        return ModItems.STALKER_SINEW.get();
    }

    @Override
    protected void applyOwnerPerk(ServerLevel level, Player owner) {
        if (owner.onGround() || owner.isFallFlying() || owner.getDeltaMovement().y() >= 0.0D) {
            return;
        }
        // Refreshed rather than gated on hasEffect: a longer instance replaces a shorter one, so the
        // effect never visibly blinks while the owner is still falling.
        owner.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, SLOW_FALL_TICKS, 0,
                true, false, true));
        level.sendParticles(ParticleTypes.SNOWFLAKE, this.getX(), this.getY() + 0.4D, this.getZ(),
                4, 0.3D, 0.2D, 0.3D, 0.0D);
    }

    // Sounds are mapped vanilla events, not shipped audio: the amethyst set is the right register for
    // something made of resonating crystal.

    @Override
    protected SoundEvent getAmbientSound() {
        return SoundEvents.AMETHYST_BLOCK_CHIME;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.AMETHYST_CLUSTER_BREAK;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.GLASS_BREAK;
    }
}
