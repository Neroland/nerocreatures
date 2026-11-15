package za.co.neroland.nerocreatures.entity.greenxertz;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;

import za.co.neroland.nerocreatures.entity.ai.shared.CadenceTimer;

/**
 * Wild Spore Drifters drift to crops and linger over them. Every {@value #SCAN_INTERVAL} ticks an
 * idle wild drifter samples at most {@value #SAMPLES} loaded columns within {@value #RANGE} blocks
 * (one heightmap read and one block read each; crops do not block motion, so the heightmap top is
 * the crop) and caches the first crop it finds as its target. It flies to just above it and hovers
 * for {@value #LINGER_TICKS} ticks. Tamed drifters follow their owner instead; leashed ones stay
 * where their lead holds them.
 */
public class DriftOverCropsGoal extends Goal {

    static final int SCAN_INTERVAL = 200;
    static final int SAMPLES = 12;
    static final int RANGE = 12;
    static final int LINGER_TICKS = 400;
    private static final int MAX_TRAVEL_TICKS = 300;
    private static final double HOVER_HEIGHT = 2.5D;

    private final SporeDrifter drifter;
    private final CadenceTimer cadence = new CadenceTimer(SCAN_INTERVAL);
    private BlockPos target;
    private int ticks;
    private boolean arrived;

    public DriftOverCropsGoal(SporeDrifter drifter) {
        this.drifter = drifter;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.drifter.isTame() || this.drifter.isLeashed() || this.drifter.isBaby()
                || !(this.drifter.level() instanceof ServerLevel level)
                || !this.cadence.ready(level.getGameTime(), this.drifter.getId())
                || this.drifter.farTick().skip(this.drifter)) {
            return false;
        }
        this.target = this.sample(level);
        return this.target != null;
    }

    private BlockPos sample(ServerLevel level) {
        BlockPos origin = this.drifter.blockPosition();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < SAMPLES; i++) {
            int x = origin.getX() + this.drifter.getRandom().nextInt(RANGE * 2 + 1) - RANGE;
            int z = origin.getZ() + this.drifter.getRandom().nextInt(RANGE * 2 + 1) - RANGE;
            pos.set(x, origin.getY(), z);
            if (!level.isLoaded(pos)) {
                continue;
            }
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
            pos.setY(y);
            if (level.getBlockState(pos).is(BlockTags.CROPS)) {
                return pos.immutable();
            }
        }
        return null;
    }

    @Override
    public void start() {
        this.ticks = 0;
        this.arrived = false;
        this.moveToTarget();
    }

    private void moveToTarget() {
        this.drifter.getNavigation().moveTo(this.target.getX() + 0.5D, this.target.getY() + HOVER_HEIGHT,
                this.target.getZ() + 0.5D, 1.0D);
    }

    @Override
    public boolean canContinueToUse() {
        if (this.target == null || this.drifter.isTame() || this.drifter.isLeashed()) {
            return false;
        }
        return this.arrived ? this.ticks < LINGER_TICKS : this.ticks < MAX_TRAVEL_TICKS;
    }

    @Override
    public void tick() {
        this.ticks++;
        if (!this.arrived && this.drifter.getNavigation().isDone()) {
            double dx = this.target.getX() + 0.5D - this.drifter.getX();
            double dz = this.target.getZ() + 0.5D - this.drifter.getZ();
            if (dx * dx + dz * dz <= 4.0D) {
                this.arrived = true;
                this.ticks = 0;
            } else if (this.ticks % 40 == 0) {
                this.moveToTarget();
            }
        }
    }

    @Override
    public void stop() {
        this.target = null;
        this.drifter.getNavigation().stop();
    }
}
