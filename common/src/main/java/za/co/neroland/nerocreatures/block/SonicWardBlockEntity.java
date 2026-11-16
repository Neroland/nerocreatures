package za.co.neroland.nerocreatures.block;

import java.util.List;
import java.util.function.IntSupplier;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;
import za.co.neroland.nerocreatures.entity.boss.NeroBossEntity;
import za.co.neroland.nerocreatures.registry.ModSounds;
import za.co.neroland.nerocreatures.sound.CreatureSound;

/**
 * The Sonic Ward. Burns {@code #nerocreatures:sonic_ward_fuel}. While burning:
 *
 * <ul>
 *   <li>NeroCreatures' spawner gives hostile rules weight 0 inside the radius and skips any hostile group
 *       member that would land inside ({@link HabitatFields#blocksHostile}).</li>
 *   <li>Every pulse, up to {@value #MAX_REPELLED} hostile NeroCreatures near the edge are dealt with: one
 *       inside is walked out past the edge and drops a target that is inside; one outside whose path
 *       ends inside stops. Bosses are never touched; nothing is hurt or teleported; no other mod's mobs
 *       are affected.</li>
 * </ul>
 */
public class SonicWardBlockEntity extends FieldBlockEntity {

    public static final Identifier ID = SonicWardBlock.ID;

    public static final TagKey<Item> FUEL =
            TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "sonic_ward_fuel"));

    public static final int DEFAULT_FUEL_TICKS = 6_000;

    /** Hostiles handled per pulse at most. */
    public static final int MAX_REPELLED = 24;

    /** How far past the edge the sweep looks. */
    private static final double SWEEP_MARGIN = 8.0D;

    private static IntSupplier fuelTicks = () -> DEFAULT_FUEL_TICKS;

    public SonicWardBlockEntity(BlockPos pos, BlockState state) {
        super(type(), pos, state);
    }

    public static BlockEntityType<SonicWardBlockEntity> type() {
        return lookup(ID);
    }

    /** Wires {@code sonicWardFuelTicks}; once at common init. */
    public static void configure(IntSupplier fuel) {
        if (fuel != null) {
            fuelTicks = fuel;
        }
    }

    @Override
    protected String titleKey() {
        return "container.nerocreatures.sonic_ward";
    }

    @Override
    protected FieldRules.Kind kind() {
        return FieldRules.Kind.WARD;
    }

    @Override
    protected int ticksPerItem() {
        return fuelTicks.getAsInt();
    }

    /** The hum is a seamless three-second loop: replayed every three one-second pulses while fuelled. */
    @Override
    protected CreatureSound burningSound() {
        return ModSounds.SONIC_WARD_HUM;
    }

    @Override
    protected int soundPulses() {
        return 3;
    }

    @Override
    protected String fuelFor(ItemStack stack) {
        return stack.is(FUEL) ? "" : null;
    }

    @Override
    protected void onBurningPulse(ServerLevel level, BlockPos pos) {
        FieldRules.Field field = new FieldRules.Field(FieldRules.Kind.WARD, pos.getX() + 0.5D, pos.getY() + 0.5D,
                pos.getZ() + 0.5D, "", 1.0D, Long.MAX_VALUE);
        double reach = FieldRules.WARD_RADIUS + SWEEP_MARGIN;
        AABB box = new AABB(pos).inflate(reach, FieldRules.VERTICAL_REACH, reach);
        List<NeroCreatureEntity> hostiles = level.getEntitiesOfClass(NeroCreatureEntity.class, box,
                creature -> creature.isAlive() && !za.co.neroland.nerocreatures.gallery.GalleryDisplays.isDisplay(creature) && FieldRules.wardRepels(HabitatFields.isHostile(creature),
                        creature instanceof NeroBossEntity, false));
        int handled = 0;
        for (NeroCreatureEntity creature : hostiles) {
            if (++handled > MAX_REPELLED) {
                break;
            }
            LivingEntity target = creature.getTarget();
            if (target != null && field.covers(target.getX(), target.getY(), target.getZ())) {
                creature.setTarget(null);
            }
            if (field.covers(creature.getX(), creature.getY(), creature.getZ())) {
                double[] out = FieldRules.pushTarget(field.x(), field.z(), creature.getX(), creature.getZ());
                creature.getNavigation().moveTo(out[0], creature.getY(), out[1], 1.2D);
            } else {
                BlockPos heading = creature.getNavigation().getTargetPos();
                if (heading != null && field.covers(heading.getX() + 0.5D, heading.getY(), heading.getZ() + 0.5D)) {
                    creature.getNavigation().stop();
                }
            }
        }
    }
}
