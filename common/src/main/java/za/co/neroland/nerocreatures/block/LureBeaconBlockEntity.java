package za.co.neroland.nerocreatures.block;

import java.util.Map;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

import za.co.neroland.nerocreatures.breeding.SpeciesProfile;
import za.co.neroland.nerocreatures.breeding.SpeciesProfiles;

/**
 * The Lure Beacon. Bait is any species' breeding food ({@code species.json} food tag, so packs retag it):
 * the first bait in the stock decides which one species is lured while it burns. Only that species'
 * spawn rule is re-weighted, only within {@value FieldRules#LURE_RADIUS} blocks, by at most
 * {@value FieldRules#MAX_LURE_MULTIPLIER}x, and every census cap still applies.
 */
public class LureBeaconBlockEntity extends FieldBlockEntity {

    public static final Identifier ID = LureBeaconBlock.ID;

    public static final int DEFAULT_BAIT_TICKS = 6_000;

    public static final double DEFAULT_MULTIPLIER = 4.0D;

    private static IntSupplier baitTicks = () -> DEFAULT_BAIT_TICKS;
    private static DoubleSupplier weightMultiplier = () -> DEFAULT_MULTIPLIER;

    public LureBeaconBlockEntity(BlockPos pos, BlockState state) {
        super(type(), pos, state);
    }

    public static BlockEntityType<LureBeaconBlockEntity> type() {
        return lookup(ID);
    }

    /** Wires {@code lureBeaconBaitTicks} and {@code lureBeaconWeightMultiplier}; once at common init. */
    public static void configure(IntSupplier bait, DoubleSupplier multiplier) {
        if (bait != null) {
            baitTicks = bait;
        }
        if (multiplier != null) {
            weightMultiplier = multiplier;
        }
    }

    @Override
    protected String titleKey() {
        return "container.nerocreatures.lure_beacon";
    }

    @Override
    protected FieldRules.Kind kind() {
        return FieldRules.Kind.LURE;
    }

    @Override
    protected int ticksPerItem() {
        return baitTicks.getAsInt();
    }

    @Override
    protected double multiplier() {
        return FieldRules.clampMultiplier(weightMultiplier.getAsDouble());
    }

    @Override
    protected String fuelFor(ItemStack stack) {
        return baitSpecies(stack);
    }

    /** The species (creature id path) a bait item lures, in {@code species.json} order; null if none. */
    public static String baitSpecies(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return null;
        }
        for (Map.Entry<String, SpeciesProfile> row : SpeciesProfiles.get().species().entrySet()) {
            Identifier tag = Identifier.tryParse(row.getValue().foodTagId(row.getKey()));
            if (tag != null && stack.is(TagKey.<Item>create(Registries.ITEM, tag))) {
                return row.getKey();
            }
        }
        return null;
    }
}
