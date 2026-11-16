package za.co.neroland.nerocreatures.block;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntSupplier;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import za.co.neroland.nerocreatures.entity.tame.LivestockCreature;

/**
 * The Feeding Trough's stock and pulse. On each pulse ({@code feedingTroughIntervalTicks}) it looks at
 * up to {@value TroughRules#MAX_SCANNED} livestock within {@value TroughRules#RADIUS} blocks and puts one
 * eligible pair in love with their species' breeding food (rules: {@link TroughRules}). Love has no
 * player cause (vanilla allows none), so nothing about a player is recorded.
 */
public class FeedingTroughBlockEntity extends InventoryHabitatBlockEntity {

    public static final Identifier ID = FeedingTroughBlock.ID;

    private static IntSupplier intervalTicks = () -> TroughRules.DEFAULT_INTERVAL_TICKS;

    public FeedingTroughBlockEntity(BlockPos pos, BlockState state) {
        super(type(), pos, state);
    }

    public static BlockEntityType<FeedingTroughBlockEntity> type() {
        return lookup(ID);
    }

    /** Wires {@code feedingTroughIntervalTicks}; called once at common init. */
    public static void configure(IntSupplier interval) {
        if (interval != null) {
            intervalTicks = interval;
        }
    }

    @Override
    protected String titleKey() {
        return "container.nerocreatures.feeding_trough";
    }

    @Override
    void serverTick(ServerLevel level, BlockPos pos, BlockState state) {
        if (!TroughRules.pulseDue(level.getGameTime(), intervalTicks.getAsInt(), pos.asLong())) {
            return;
        }
        if (this.stock.isEmpty()) {
            return;
        }
        AABB box = new AABB(pos).inflate(TroughRules.RADIUS, TroughRules.VERTICAL, TroughRules.RADIUS);
        List<LivestockCreature> near = level.getEntitiesOfClass(LivestockCreature.class, box,
                e -> e.isAlive() && !za.co.neroland.nerocreatures.gallery.GalleryDisplays.isDisplay(e));
        if (near.size() < 2) {
            return;
        }
        List<TroughRules.Candidate<LivestockCreature>> candidates = new ArrayList<>(near.size());
        Map<String, LivestockCreature> sample = new HashMap<>();
        for (LivestockCreature animal : near) {
            String species = EntityType.getKey(animal.getType()).toString();
            sample.putIfAbsent(species, animal);
            candidates.add(new TroughRules.Candidate<>(animal, species, animal.isBaby(), animal.isHusbanded(),
                    animal.getAge() == 0 && animal.canFallInLove()));
        }
        List<LivestockCreature> pair = TroughRules.pickPair(candidates,
                species -> this.count(sample.get(species)::isSpeciesFood) >= 2);
        if (pair.size() != 2) {
            return;
        }
        for (LivestockCreature animal : pair) {
            if (this.consumeOne(animal::isSpeciesFood).isEmpty()) {
                return;
            }
            animal.setInLove(null);
        }
        this.setChanged();
    }
}
