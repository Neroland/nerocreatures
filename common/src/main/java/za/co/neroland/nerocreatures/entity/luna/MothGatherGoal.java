package za.co.neroland.nerocreatures.entity.luna;

import java.util.EnumSet;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.LightLayer;

import za.co.neroland.nerocreatures.entity.ai.shared.CadenceTimer;

/**
 * Wild Lumen Moths gather at light sources after dark (movement only; decision 006 lists no world
 * change). Every {@value MothRules#GATHER_INTERVAL} ticks an untamed, unleashed moth samples
 * {@value MothRules#GATHER_SAMPLES} random loaded spots within {@value MothRules#GATHER_RADIUS}
 * blocks, reads their block light (one light-engine read each, no block scan), and flutters around
 * the brightest one at or above {@value MothRules#MIN_ATTRACTING_LIGHT} for a while.
 *
 * <p>Far moths skip the search through the far-tick gate.
 */
public class MothGatherGoal extends Goal {

    private static final int REFLUTTER_TICKS = 20;

    private final LumenMoth moth;
    private final CadenceTimer cadence = new CadenceTimer(MothRules.GATHER_INTERVAL);
    private final int[] levels = new int[MothRules.GATHER_SAMPLES];
    private final BlockPos[] samples = new BlockPos[MothRules.GATHER_SAMPLES];

    private BlockPos light;
    private long until;
    private int reflutter;

    public MothGatherGoal(LumenMoth moth) {
        this.moth = moth;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        if (this.moth.isTame() || this.moth.isLeashed() || !(this.moth.level() instanceof ServerLevel level)) {
            return false;
        }
        if (this.moth.farTick().skip(this.moth) || !this.cadence.ready(level.getGameTime(), this.moth.getId())) {
            return false;
        }
        BlockPos origin = this.moth.blockPosition();
        if (!level.isDarkOutside() && level.canSeeSky(origin)) {
            return false;
        }
        int r = MothRules.GATHER_RADIUS;
        for (int i = 0; i < MothRules.GATHER_SAMPLES; i++) {
            BlockPos pos = origin.offset(this.moth.getRandom().nextInt(2 * r + 1) - r,
                    this.moth.getRandom().nextInt(9) - 4, this.moth.getRandom().nextInt(2 * r + 1) - r);
            this.samples[i] = pos;
            this.levels[i] = level.isLoaded(pos) ? level.getBrightness(LightLayer.BLOCK, pos) : 0;
        }
        int best = MothRules.bestLightIndex(this.levels, MothRules.MIN_ATTRACTING_LIGHT);
        if (best < 0) {
            return false;
        }
        this.light = this.samples[best].immutable();
        this.until = level.getGameTime() + MothRules.GATHER_HOVER_TICKS;
        return true;
    }

    @Override
    public void start() {
        this.reflutter = 0;
    }

    @Override
    public boolean canContinueToUse() {
        return this.light != null && !this.moth.isTame() && !this.moth.isLeashed()
                && this.moth.level().getGameTime() < this.until;
    }

    @Override
    public void tick() {
        if (--this.reflutter > 0 || this.light == null) {
            return;
        }
        this.reflutter = REFLUTTER_TICKS;
        // A loose orbit around the light: a new point within 2 blocks of it every second.
        double x = this.light.getX() + 0.5D + (this.moth.getRandom().nextDouble() - 0.5D) * 4.0D;
        double y = this.light.getY() + 0.5D + this.moth.getRandom().nextDouble() * 1.5D;
        double z = this.light.getZ() + 0.5D + (this.moth.getRandom().nextDouble() - 0.5D) * 4.0D;
        this.moth.getNavigation().moveTo(x, y, z, 1.0D);
    }

    @Override
    public void stop() {
        this.light = null;
        this.moth.getNavigation().stop();
    }
}
