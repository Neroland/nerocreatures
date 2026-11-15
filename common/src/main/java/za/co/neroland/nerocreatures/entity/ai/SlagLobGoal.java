package za.co.neroland.nerocreatures.entity.ai;

import java.util.EnumSet;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

import za.co.neroland.nerocreatures.entity.hostile.MagmaWader;
import za.co.neroland.nerocreatures.entity.visual.VisualPose;
import za.co.neroland.nerocreatures.registry.ModSounds;

/**
 * The Magma Wader's telegraphed slag lob. The goal owns only the wind-up: it stops, rears back on
 * its hind legs ({@code ability} pose, {@value SlagLobRules#WINDUP_TICKS} ticks of telegraph), then
 * hands the throw to {@link MagmaWader#launchLob}. Flight and landing are ticked by the wader, so a
 * lob in the air still lands if the goal is pre-empted.
 *
 * <p>Cost: {@code canUse} is a counter, a cached target read, one squared distance and the mob's
 * cached line-of-sight check; no scans.
 */
public class SlagLobGoal extends Goal {

    private final MagmaWader wader;
    private int cooldown = SlagLobRules.COOLDOWN_MIN_TICKS / 2;
    private int windup;
    private boolean thrown;

    public SlagLobGoal(MagmaWader wader) {
        this.wader = wader;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        if (this.cooldown > 0) {
            this.cooldown--;
            return false;
        }
        LivingEntity target = this.wader.getTarget();
        if (target == null || !target.isAlive() || !SlagLobRules.mayLaunch(this.wader.lobsInFlight())) {
            return false;
        }
        return SlagLobRules.inRange(this.wader.distanceTo(target))
                && this.wader.getSensing().hasLineOfSight(target);
    }

    @Override
    public boolean canContinueToUse() {
        LivingEntity target = this.wader.getTarget();
        return !this.thrown && target != null && target.isAlive() && this.wader.isAlive();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        this.windup = SlagLobRules.WINDUP_TICKS;
        this.thrown = false;
        this.wader.getNavigation().stop();
        this.wader.visuals().playPose(VisualPose.ABILITY, SlagLobRules.POSE_TICKS);
        ModSounds.MAGMA_WADER_SLAG.play(this.wader);
    }

    @Override
    public void tick() {
        LivingEntity target = this.wader.getTarget();
        if (target == null) {
            return;
        }
        this.wader.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (--this.windup > 0) {
            return;
        }
        this.wader.launchLob(target);
        this.thrown = true;
    }

    @Override
    public void stop() {
        this.cooldown = SlagLobRules.cooldown(this.wader.getRandom().nextDouble());
        this.thrown = false;
    }
}
