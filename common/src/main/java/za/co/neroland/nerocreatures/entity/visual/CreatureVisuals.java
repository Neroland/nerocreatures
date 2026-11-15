package za.co.neroland.nerocreatures.entity.visual;

import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;

/**
 * The visual contract every NeroCreatures entity carries: the small set of server-decided values a
 * model needs to show what the creature is doing. Backed by two {@code SynchedEntityData} ints (see
 * {@link VisualsBits} for the layout), so it costs two data ids per class root and nothing is sent
 * unless a value changes.
 *
 * <ul>
 *   <li>{@code pose} ({@link VisualPose}) and {@code abilityTicks}: which clip plays and how far into
 *       it. A timed pose returns to {@code IDLE} by itself after its duration.</li>
 *   <li>{@code phase} 0-3: boss phase stance.</li>
 *   <li>{@code stage} 0-3: part-visibility stage (nodules, digestion, antlers, wool, plates, shed
 *       parts, barnacles).</li>
 *   <li>{@code variant} 0-7: colour family or skin.</li>
 *   <li>{@code form} 0-7: body form of one entity type (bloom host: 0 host, 1 sporeling, 2 pod).</li>
 *   <li>flags {@code radiant}, {@code alpha}, {@code saddled}, {@code panniers}.</li>
 * </ul>
 *
 * <h2>Wiring in a class root (once per root, not per species)</h2>
 * <pre>{@code
 * private static final CreatureVisuals.Ids VISUALS_IDS = CreatureVisuals.defineIds(NeroCreatureEntity.class);
 * private final CreatureVisuals visuals = new CreatureVisuals(this, VISUALS_IDS);
 *
 * @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
 *     super.defineSynchedData(builder);
 *     CreatureVisuals.define(builder, VISUALS_IDS);
 * }
 * @Override public CreatureVisuals visuals() { return visuals; }      // CreatureVisualsHolder
 * // in tick(), server side:  visuals.tick();
 * // save: output.putInt(CreatureVisuals.SAVE_KEY, visuals.persistentBits());
 * // load: visuals.restorePersistentBits(input.getIntOr(CreatureVisuals.SAVE_KEY, 0));
 * }</pre>
 * The {@code visuals} field initialiser runs after {@code defineSynchedData} (the data is defined in
 * the {@code Entity} constructor), which is fine: the component only holds the accessors.
 *
 * <p><b>Server-authoritative.</b> Setters do nothing on the client. Species code sets values on the
 * server; the renderer reads {@link #snapshot(float)}.
 *
 * <p><b>Single source of truth.</b> Where another system already owns a value (the trait set owns
 * Radiant, the alpha system owns alpha, the mount owns saddle and panniers), that system writes the
 * flag here whenever its own value changes and on load; this component is the synced mirror. The
 * persistent bits are saved as a convenience for species state (stage, variant, form) that has no
 * other home.
 */
public final class CreatureVisuals {

    /** NBT / value-output key for {@link #persistentBits()}. */
    public static final String SAVE_KEY = "nerocreatures:visuals";

    /** A held (untimed) pose stops counting {@code abilityTicks} here, so it stops syncing. */
    public static final int HELD_COUNT_CAP = 40;

    /** The two data ids of one class root. */
    public record Ids(EntityDataAccessor<Integer> state, EntityDataAccessor<Integer> timer) {
    }

    private final Entity entity;
    private final Ids ids;

    public CreatureVisuals(Entity entity, Ids ids) {
        this.entity = entity;
        this.ids = ids;
    }

    /**
     * Allocates the two ids. Call exactly once per class root, in a {@code static final} field of
     * that root (data ids are allocated per class hierarchy).
     */
    public static Ids defineIds(Class<? extends Entity> root) {
        return new Ids(SynchedEntityData.defineId(root, EntityDataSerializers.INT),
                SynchedEntityData.defineId(root, EntityDataSerializers.INT));
    }

    /** Defines the default values. Call from the root's {@code defineSynchedData}. */
    public static void define(SynchedEntityData.Builder builder, Ids ids) {
        builder.define(ids.state(), 0);
        builder.define(ids.timer(), 0);
    }

    // --- reads (both sides) -------------------------------------------------------

    private int state() {
        return entity.getEntityData().get(ids.state());
    }

    private int timerValue() {
        return entity.getEntityData().get(ids.timer());
    }

    public VisualPose pose() {
        return VisualPose.byId(VisualsBits.pose(state()));
    }

    /** Whole ticks since the current pose was entered (capped for held poses). */
    public int abilityTicks() {
        return VisualsBits.elapsed(timerValue());
    }

    /** The current pose's duration in ticks, or 0 when held. */
    public int abilityDuration() {
        return VisualsBits.duration(timerValue());
    }

    public int phase() {
        return VisualsBits.phase(state());
    }

    public int stage() {
        return VisualsBits.stage(state());
    }

    public int variant() {
        return VisualsBits.variant(state());
    }

    public int form() {
        return VisualsBits.form(state());
    }

    public boolean radiant() {
        return VisualsBits.flag(state(), VisualsBits.FLAG_RADIANT);
    }

    public boolean alpha() {
        return VisualsBits.flag(state(), VisualsBits.FLAG_ALPHA);
    }

    public boolean saddled() {
        return VisualsBits.flag(state(), VisualsBits.FLAG_SADDLED);
    }

    public boolean panniers() {
        return VisualsBits.flag(state(), VisualsBits.FLAG_PANNIERS);
    }

    /** Everything the renderer needs for this frame. */
    public VisualsSnapshot snapshot(float partialTick) {
        return VisualsSnapshot.of(state(), timerValue(), partialTick);
    }

    // --- writes (server only; no-ops on the client) --------------------------------

    /**
     * Enters {@code pose} for {@code durationTicks} ticks, then returns to {@code IDLE} by itself.
     * A damaging ability's telegraph is part of its duration (at least 10 ticks before the hit).
     * Re-entering the same pose restarts it.
     */
    public void playPose(VisualPose pose, int durationTicks) {
        if (!server()) {
            return;
        }
        setState(VisualsBits.withPose(state(), pose.ordinal()));
        setTimer(VisualsBits.timer(0, Math.max(0, durationTicks)));
    }

    /** Holds {@code pose} until changed (e.g. {@code REST} while sitting). {@code IDLE} clears. */
    public void holdPose(VisualPose pose) {
        if (!server() || (pose() == pose && abilityDuration() == 0)) {
            return;
        }
        setState(VisualsBits.withPose(state(), pose.ordinal()));
        setTimer(0);
    }

    public void setPhase(int phase) {
        if (server()) {
            setState(VisualsBits.withPhase(state(), phase));
        }
    }

    public void setStage(int stage) {
        if (server()) {
            setState(VisualsBits.withStage(state(), stage));
        }
    }

    public void setVariant(int variant) {
        if (server()) {
            setState(VisualsBits.withVariant(state(), variant));
        }
    }

    public void setForm(int form) {
        if (server()) {
            setState(VisualsBits.withForm(state(), form));
        }
    }

    public void setRadiant(boolean on) {
        setFlag(VisualsBits.FLAG_RADIANT, on);
    }

    public void setAlpha(boolean on) {
        setFlag(VisualsBits.FLAG_ALPHA, on);
    }

    public void setSaddled(boolean on) {
        setFlag(VisualsBits.FLAG_SADDLED, on);
    }

    public void setPanniers(boolean on) {
        setFlag(VisualsBits.FLAG_PANNIERS, on);
    }

    /**
     * Advances the pose timer. Call once per server tick from the class root's {@code tick()}.
     * Writes (and so syncs) only while a timed pose runs or a held pose is in its first
     * {@value #HELD_COUNT_CAP} ticks.
     */
    public void tick() {
        if (!server()) {
            return;
        }
        int state = state();
        if (VisualsBits.pose(state) == VisualPose.IDLE.ordinal()) {
            return;
        }
        int timer = timerValue();
        int next = nextTimer(timer);
        if (next < 0) {
            setState(VisualsBits.withPose(state, VisualPose.IDLE.ordinal()));
            setTimer(0);
        } else if (next != timer) {
            setTimer(next);
        }
    }

    /**
     * The pure timer step: the next timer value, the same value when nothing changes, or -1 when a
     * timed pose has finished. Package-private for tests.
     */
    static int nextTimer(int timer) {
        int elapsed = VisualsBits.elapsed(timer);
        int duration = VisualsBits.duration(timer);
        if (duration > 0) {
            return elapsed + 1 >= duration ? -1 : VisualsBits.timer(elapsed + 1, duration);
        }
        return elapsed >= HELD_COUNT_CAP ? timer : VisualsBits.timer(elapsed + 1, 0);
    }

    /** The persistent bits (stage, variant, form, flags) for saving under {@link #SAVE_KEY}. */
    public int persistentBits() {
        return VisualsBits.persistent(state());
    }

    /** Restores saved persistent bits on load (server). Pose and timer start fresh. */
    public void restorePersistentBits(int saved) {
        if (server()) {
            setState(VisualsBits.restorePersistent(state(), saved));
        }
    }

    private void setFlag(int flag, boolean on) {
        if (server()) {
            setState(VisualsBits.withFlag(state(), flag, on));
        }
    }

    private void setState(int value) {
        entity.getEntityData().set(ids.state(), value);
    }

    private void setTimer(int value) {
        entity.getEntityData().set(ids.timer(), value);
    }

    private boolean server() {
        return !entity.level().isClientSide();
    }
}
