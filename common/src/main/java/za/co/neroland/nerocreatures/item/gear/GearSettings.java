package za.co.neroland.nerocreatures.item.gear;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;

/**
 * The gear's tunables. Defaults are the {@link GearRules} constants; the integrator points each at
 * its {@code NeroCreaturesConfig} key at init (contract section 7 names). Read on the server only.
 */
public final class GearSettings {

    public static volatile IntSupplier mirrorShieldCooldownTicks = () -> GearRules.MIRROR_SHIELD_COOLDOWN;
    public static volatile BooleanSupplier lurkerCloakSilences = () -> true;
    public static volatile IntSupplier blinkCharmCooldownTicks = () -> GearRules.BLINK_COOLDOWN;
    public static volatile IntSupplier blinkCharmRange = () -> GearRules.BLINK_RANGE;
    public static volatile IntSupplier rimeFlaskCooldownTicks = () -> GearRules.RIME_FLASK_COOLDOWN;
    public static volatile IntSupplier heatGlandGreavesLavaTicks = () -> GearRules.GREAVES_LAVA_TICKS;
    public static volatile IntSupplier heatGlandGreavesCooldownTicks = () -> GearRules.GREAVES_COOLDOWN;
    public static volatile IntSupplier gravitonBootsGravityPercent = () -> GearRules.BOOTS_GRAVITY_PERCENT;
    public static volatile IntSupplier resonantWhistleCooldownTicks = () -> GearRules.WHISTLE_COOLDOWN;
    public static volatile IntSupplier resonantWhistleRange = () -> GearRules.WHISTLE_RANGE;
    public static volatile IntSupplier chitinBulwarkCooldownTicks = () -> GearRules.BULWARK_COOLDOWN;
    public static volatile IntSupplier bloomAmuletCooldownTicks = () -> GearRules.AMULET_COOLDOWN;

    private GearSettings() {
    }
}
