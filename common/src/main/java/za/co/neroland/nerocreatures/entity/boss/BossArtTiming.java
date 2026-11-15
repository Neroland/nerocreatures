package za.co.neroland.nerocreatures.entity.boss;

/**
 * Art-timing rules for boss abilities (decision 007 §4), as pure logic.
 *
 * <p>Cinder Tyrant slam: the {@code ability} pose runs {@value #SLAM_POSE_TICKS} ticks and starts
 * {@value #SLAM_POSE_LEAD} ticks before the slam lands, so both arms are up for the telegraph and come
 * down on the impact frame.
 */
public final class BossArtTiming {

    public static final int SLAM_POSE_TICKS = 32;
    public static final int SLAM_POSE_LEAD = 12;

    private BossArtTiming() {
    }

    /**
     * Whether the slam pose starts now.
     *
     * @param ticksToSlam ticks left until the slam lands ({@code telegraph} at the goal's start, then
     *                    one less per goal tick)
     * @param atStart     this is the goal's {@code start()} call (a telegraph no longer than the lead
     *                    starts the pose at once)
     */
    public static boolean slamPoseStarts(int ticksToSlam, boolean atStart) {
        return atStart ? ticksToSlam <= SLAM_POSE_LEAD : ticksToSlam == SLAM_POSE_LEAD;
    }
}
