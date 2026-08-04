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
 * <b>Xertz Forager</b> — the quartz-forest critter of the lush green worlds.
 *
 * <p>A low, quick, four-legged grazer with a fused quartz crest that it uses to root through
 * undergrowth. It lives where the crystal and the greenery meet, and it is the only creature in the
 * mod that will follow a player around for a {@code plasma_cell} without ever being a threat to
 * anything.
 *
 * <p>Wild foragers spawn in Core's {@code neroland:space/crystalline_biomes} — the tag a lush,
 * crystal-grown world belongs to.
 *
 * <h2>Perk — Quartz Glow</h2>
 *
 * <p>In dim light, and only in dim light, a forager's crest lights up and lends its owner Night
 * Vision while they stay within eight blocks. It is a lantern with legs: no combat stat, nothing
 * that changes a fight, and it switches off the moment the owner steps into decent light — so it
 * reads as the pet reacting to the dark rather than as a permanent buff.
 *
 * <p>Cost: one light-level read and one effect refresh every two seconds, and only while the pet is
 * tamed and its owner is close.
 */
public class XertzForager extends TameableCreature {

    /** Block-and-sky light at or below which the crest lights up. Vanilla's "dark enough" is 7. */
    private static final int GLOW_LIGHT_THRESHOLD = 7;

    /**
     * How long a lent Night Vision lasts, in ticks. Refreshed every perk tick, and long enough that
     * the refresh always beats vanilla's end-of-effect flicker (which starts at 200 ticks left).
     */
    private static final int NIGHT_VISION_TICKS = 400;

    public XertzForager(EntityType<? extends XertzForager> type, Level level) {
        super(type, level, CreatureTier.LESSER);
    }

    /** {@link CreatureTier#LESSER}: a little sturdier and a little quicker than the wisp. */
    public static AttributeSupplier.Builder createAttributes() {
        return TameableCreature.createPetAttributes(CreatureTier.LESSER)
                .add(Attributes.MAX_HEALTH, 16.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.32D)
                .add(Attributes.ATTACK_DAMAGE, 2.0D)
                .add(Attributes.FOLLOW_RANGE, 20.0D);
    }

    @Override
    public Item tamingFood() {
        return ModItems.PLASMA_CELL.get();
    }

    @Override
    protected void applyOwnerPerk(ServerLevel level, Player owner) {
        if (level.getMaxLocalRawBrightness(owner.blockPosition()) > GLOW_LIGHT_THRESHOLD) {
            return;
        }
        // Refreshed rather than gated on hasEffect: a longer instance replaces a shorter one, so the
        // vision never blinks while the owner is still in the dark.
        owner.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, NIGHT_VISION_TICKS, 0,
                true, false, true));
        level.sendParticles(ParticleTypes.GLOW, this.getX(), this.getY() + this.getBbHeight(),
                this.getZ(), 2, 0.2D, 0.1D, 0.2D, 0.0D);
    }

    // Mapped vanilla events: the fox set is the right size and temperament for a small forager.

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
}
