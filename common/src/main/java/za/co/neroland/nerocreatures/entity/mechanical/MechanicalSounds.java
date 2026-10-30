package za.co.neroland.nerocreatures.entity.mechanical;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * The vanilla sound events the mechanical creatures use. They are the copper golem's, which Minecraft
 * only has from 1.21.9; older lines fall back to the nearest vanilla equivalents (the iron golem's
 * hurt/death/step and the grindstone for the spin-up whirr). Sound only, no behaviour difference.
 */
final class MechanicalSounds {

    private MechanicalSounds() {
    }

    static SoundEvent hurt() {
        //? if >=1.21.9 {
        return SoundEvents.COPPER_GOLEM_HURT;
        //?} else {
        /*return SoundEvents.IRON_GOLEM_HURT;
        *///?}
    }

    static SoundEvent death() {
        //? if >=1.21.9 {
        return SoundEvents.COPPER_GOLEM_DEATH;
        //?} else {
        /*return SoundEvents.IRON_GOLEM_DEATH;
        *///?}
    }

    static SoundEvent step() {
        //? if >=1.21.9 {
        return SoundEvents.COPPER_GOLEM_STEP;
        //?} else {
        /*return SoundEvents.IRON_GOLEM_STEP;
        *///?}
    }

    static SoundEvent spin() {
        //? if >=1.21.9 {
        return SoundEvents.COPPER_GOLEM_SPIN;
        //?} else {
        /*return SoundEvents.GRINDSTONE_USE;
        *///?}
    }
}
