package za.co.neroland.nerocreatures.entity.humanoid;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import org.jetbrains.annotations.Nullable;

/**
 * The Space Pirate kit table: what a pirate is carrying, which is the <b>only</b> thing that
 * separates one pirate from another.
 *
 * <p>NeroCreatures registers a single {@code space_pirate} entity type rather than a melee type and
 * a ranged type. Two reasons:
 *
 * <ul>
 *   <li><b>Goals are registered before equipment exists.</b> A mob's goals are wired in its
 *       constructor, but its kit is chosen in {@code finalizeSpawn} — after. Two entity types would
 *       not change that ordering; the goals would still have to be gated on the kit. Given that,
 *       one type with kit-gated goals is the same mechanism with half the registry surface.</li>
 *   <li><b>Everything else about them is identical</b> — model, size, drops, spawn rule, sounds. Two
 *       types would mean two spawn eggs, two loot tables, two lang keys and two bestiary entries
 *       describing the same creature holding a different weapon.</li>
 * </ul>
 *
 * <p>Tiers are ordinary difficulty bands: {@link #RECRUIT_TIER} is what a naturally spawned band
 * carries, {@link #RAIDER_TIER} is what a deliberate raid brings (see {@link PirateSpawner}). Armour
 * pieces are vanilla items, so they show up correctly in every resource pack and can be worn by the
 * player who takes them.
 */
public enum PirateLoadout {

    /** A cutlass and a bit of hide. The bulk of any natural band. */
    RECRUIT_BLADE(PirateLoadout.RECRUIT_TIER, false, Items.STONE_SWORD, Items.LEATHER_HELMET, null),

    /** Recruit with a crossbow — the one hanging back and making you move. */
    RECRUIT_CROSSBOW(PirateLoadout.RECRUIT_TIER, true, Items.CROSSBOW, Items.LEATHER_HELMET, null),

    /** A veteran boarder: iron blade, real helmet, plate over the chest. */
    RAIDER_BLADE(PirateLoadout.RAIDER_TIER, false, Items.IRON_SWORD, Items.IRON_HELMET,
            Items.IRON_CHESTPLATE),

    /** A veteran marksman. Same protection, longer reach. */
    RAIDER_CROSSBOW(PirateLoadout.RAIDER_TIER, true, Items.CROSSBOW, Items.IRON_HELMET,
            Items.CHAINMAIL_CHESTPLATE);

    /** Kit carried by naturally spawned pirates. */
    public static final int RECRUIT_TIER = 1;

    /** Kit carried by a deliberately summoned raid. */
    public static final int RAIDER_TIER = 2;

    /** Share of a band that carries a ranged weapon. */
    private static final float RANGED_SHARE = 0.4F;

    /**
     * Chance that one worn piece survives the fight and drops. Low on purpose: pirate gear is a
     * lucky find, not an armour vending machine.
     */
    public static final float GEAR_DROP_CHANCE = 0.06F;

    private final int tier;
    private final boolean ranged;
    private final Item mainHand;
    @Nullable
    private final Item head;
    @Nullable
    private final Item chest;

    PirateLoadout(int tier, boolean ranged, Item mainHand, @Nullable Item head, @Nullable Item chest) {
        this.tier = tier;
        this.ranged = ranged;
        this.mainHand = mainHand;
        this.head = head;
        this.chest = chest;
    }

    /** Which difficulty band this kit belongs to. */
    public int tier() {
        return this.tier;
    }

    /** Whether the carrier fights at range. Gates the pirate's ranged goal. */
    public boolean ranged() {
        return this.ranged;
    }

    /**
     * Picks a kit for {@code tier}. Unknown tiers clamp to the nearest known one, so a caller (a
     * future NeroEvents raid, say) can ask for "tier 5" and get the toughest kit that exists rather
     * than an exception.
     */
    public static PirateLoadout roll(RandomSource random, int tier) {
        boolean raider = tier >= RAIDER_TIER;
        boolean ranged = random.nextFloat() < RANGED_SHARE;
        if (raider) {
            return ranged ? RAIDER_CROSSBOW : RAIDER_BLADE;
        }
        return ranged ? RECRUIT_CROSSBOW : RECRUIT_BLADE;
    }

    /** Reads a saved kit name, falling back to the plain recruit kit for anything unrecognised. */
    public static PirateLoadout byName(String name) {
        for (PirateLoadout loadout : values()) {
            if (loadout.name().equals(name)) {
                return loadout;
            }
        }
        return RECRUIT_BLADE;
    }

    /**
     * Puts this kit on {@code mob} and sets its drop chances. Called from
     * {@code SpacePirate.finalizeSpawn} and from {@link PirateSpawner}; safe to call again (it
     * overwrites the slots it owns and leaves the rest alone).
     */
    public void applyTo(Mob mob) {
        mob.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(this.mainHand));
        mob.setDropChance(EquipmentSlot.MAINHAND, GEAR_DROP_CHANCE);
        equip(mob, EquipmentSlot.HEAD, this.head);
        equip(mob, EquipmentSlot.CHEST, this.chest);
    }

    private static void equip(Mob mob, EquipmentSlot slot, @Nullable Item item) {
        mob.setItemSlot(slot, item == null ? ItemStack.EMPTY : new ItemStack(item));
        mob.setDropChance(slot, item == null ? 0.0F : GEAR_DROP_CHANCE);
    }
}
