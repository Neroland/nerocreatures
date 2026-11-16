package za.co.neroland.nerocreatures.item.gear;

import org.jetbrains.annotations.Nullable;

import net.minecraft.world.entity.EquipmentSlot;

/**
 * The creature gear, one per signature drop. Each {@link CreatureGearItem} carries one
 * kind; {@link CreatureGear} finds gear by kind, so nothing here needs a registry reference.
 *
 * <p>Passive gear works while worn in its {@link #slot()} (when it has one) or held in either hand.
 * Active gear works on use.
 */
public enum GearKind {
    /** Reflects one incoming projectile per cooldown while held. */
    MIRROR_SHIELD("mirror_shield", null, false),
    /** Silent to sound hunters while worn, unless sprinting. */
    LURKER_CLOAK("lurker_cloak", EquipmentSlot.CHEST, false),
    /** Short raycast teleport on use. */
    BLINK_CHARM("blink_charm", null, true),
    /** Drink: fire resistance and put out. Reusable on a cooldown. */
    RIME_FLASK("rime_flask", null, true),
    /** Holds the wearer up on lava for a few seconds, then recharges. */
    HEAT_GLAND_GREAVES("heat_gland_greaves", EquipmentSlot.LEGS, false),
    /** Lower gravity and a safer fall while worn; sneak to fall normally. */
    GRAVITON_BOOTS("graviton_boots", EquipmentSlot.FEET, false),
    /** Calls your own loaded pets and mounts in this dimension to you. */
    RESONANT_WHISTLE("resonant_whistle", null, true),
    /** Knockback resistance while held; braces (absorption) once per cooldown at low health. */
    CHITIN_BULWARK("chitin_bulwark", null, false),
    /** A regeneration pulse once per cooldown when hurt. */
    BLOOM_AMULET("bloom_amulet", null, false);

    private final String id;
    @Nullable
    private final EquipmentSlot slot;
    private final boolean active;

    GearKind(String id, @Nullable EquipmentSlot slot, boolean active) {
        this.id = id;
        this.slot = slot;
        this.active = active;
    }

    /** The item id path. */
    public String id() {
        return this.id;
    }

    /** The body slot it is worn in, or null for hand-held gear. */
    @Nullable
    public EquipmentSlot slot() {
        return this.slot;
    }

    /** Whether it works on use rather than passively. */
    public boolean active() {
        return this.active;
    }
}
