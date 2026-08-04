package za.co.neroland.nerocreatures.entity.mechanical;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;

import za.co.neroland.nerocreatures.entity.base.CreatureTier;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;

/**
 * Shared base for the Rogue Androids — the mod's machines.
 *
 * <p>It carries the one thing that makes a machine read as a machine rather than as a metal animal:
 * <b>biological effects do not apply</b>. Poison, wither and hunger are refused; fire is not, and
 * that is deliberate, because fire is what the salvage rule (see {@link RogueAndroid}) plays off —
 * burning a frame down destroys the boards you were trying to recover.
 *
 * <p>Everything else is ordinary {@link NeroCreatureEntity} plumbing, so both androids still count
 * against the population caps and still despawn like any other natural spawn.
 */
public abstract class AbstractAndroid extends NeroCreatureEntity implements Enemy {

    protected AbstractAndroid(EntityType<? extends AbstractAndroid> type, Level level, CreatureTier tier) {
        super(type, level, tier);
    }

    /**
     * Refuses the effects that only make sense against something alive. Kept to the three that are
     * unambiguous: an android can still be slowed, weakened or set alight.
     */
    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        if (effect.getEffect().is(MobEffects.POISON)
                || effect.getEffect().is(MobEffects.WITHER)
                || effect.getEffect().is(MobEffects.HUNGER)) {
            return false;
        }
        return super.canBeAffected(effect);
    }

    // --- Sounds -------------------------------------------------------------
    // Mapped vanilla events: the copper golem's servo set is the closest thing vanilla has to a
    // walking machine, and NeroCreatures ships no audio of its own.

    @Override
    protected SoundEvent getHurtSound(DamageSource damageSource) {
        return SoundEvents.COPPER_GOLEM_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.COPPER_GOLEM_DEATH;
    }
}
