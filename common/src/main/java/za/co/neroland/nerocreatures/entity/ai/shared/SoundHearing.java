package za.co.neroland.nerocreatures.entity.ai.shared;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.gameevent.DynamicGameEventListener;
import net.minecraft.world.level.gameevent.EntityPositionSource;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.gameevent.GameEventListener;
import net.minecraft.world.level.gameevent.PositionSource;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

/**
 * A lightweight vanilla {@link GameEventListener} that remembers the loudest recent noise.
 *
 * <p>This is the listener half of sound hunting. It is not a {@code VibrationSystem}: vanilla
 * delivers each game event in range to {@link #handleGameEvent}, which only does arithmetic and
 * stores four fields. The world scan is vanilla's own section-based dispatch; nothing here walks
 * entities. {@link SoundHuntGoal} reads the stored noise on its own cadence.
 *
 * <p>Install on a {@code NeroCreatureEntity} with {@code installHearing(new SoundHearing(...))} in
 * the constructor; the base entity registers it with the level through
 * {@code updateDynamicGameEventListener}.
 *
 * <p>Stores an entity <em>network id</em> only for the current session (never saved), so no player
 * identity is persisted (POPIA/GDPR).
 */
public final class SoundHearing implements GameEventListener {

    private static final List<Predicate<Entity>> SILENCERS = new CopyOnWriteArrayList<>();

    private final Mob owner;
    private final PositionSource source;
    private final DynamicGameEventListener<SoundHearing> dynamic;
    private final int radius;
    private final int memoryTicks;
    private final boolean hearOtherNoises;

    private long heardAt = Long.MIN_VALUE;
    private int loudness;
    private int sourceEntityId = -1;
    private Vec3 position = Vec3.ZERO;

    /**
     * @param owner           the listening creature
     * @param radius          hearing radius in blocks for the loudest noise (vanilla caps
     *                        dispatch radius; keep this at 32 or less)
     * @param memoryTicks     how long a noise is remembered
     * @param hearOtherNoises whether projectile impacts count (as {@link SoundHuntRules.Noise#OTHER})
     */
    public SoundHearing(Mob owner, int radius, int memoryTicks, boolean hearOtherNoises) {
        this.owner = owner;
        this.radius = Math.max(1, Math.min(32, radius));
        this.memoryTicks = Math.max(1, memoryTicks);
        this.hearOtherNoises = hearOtherNoises;
        this.source = new EntityPositionSource(owner, owner.getEyeHeight());
        this.dynamic = new DynamicGameEventListener<>(this);
    }

    /**
     * Registers a silencer, e.g. a cloak that hides its wearer from sound hunters. Called once at
     * init by whatever owns the gear; the predicate must be cheap and side-effect free.
     */
    public static void addSilencer(Predicate<Entity> silencer) {
        SILENCERS.add(silencer);
    }

    /** True if any registered silencer hides {@code entity}. */
    public static boolean isSilenced(Entity entity) {
        for (Predicate<Entity> silencer : SILENCERS) {
            if (silencer.test(entity)) {
                return true;
            }
        }
        return false;
    }

    /** The vanilla wrapper the base entity hands to {@code updateDynamicGameEventListener}. */
    public DynamicGameEventListener<SoundHearing> dynamicListener() {
        return this.dynamic;
    }

    @Override
    public PositionSource getListenerSource() {
        return this.source;
    }

    @Override
    public int getListenerRadius() {
        return this.radius;
    }

    @Override
    public boolean handleGameEvent(ServerLevel level, Holder<GameEvent> event, GameEvent.Context context,
            Vec3 sourcePosition) {
        if (!this.owner.isAlive() || this.owner.isNoAi()) {
            return false;
        }
        SoundHuntRules.Noise noise = classify(event);
        if (noise == null) {
            return false;
        }
        Entity cause = context.sourceEntity();
        if (cause == this.owner || (cause != null && cause.getType() == this.owner.getType())) {
            return false;
        }
        if (cause instanceof Player player && (player.isSpectator() || player.isCreative())) {
            return false;
        }
        boolean sprinting = cause != null && cause.isSprinting();
        boolean sneaking = cause != null && cause.isSteppingCarefully();
        boolean silenced = cause != null && isSilenced(cause);
        int loud = SoundHuntRules.loudness(noise, sprinting, sneaking, silenced);
        long now = level.getGameTime();
        double distSqr = this.owner.position().distanceToSqr(sourcePosition);
        if (!SoundHuntRules.heard(loud, distSqr, this.radius)
                || !SoundHuntRules.replaces(loud, this.loudness, this.heardAt, now, this.memoryTicks)) {
            return false;
        }
        this.heardAt = now;
        this.loudness = loud;
        this.sourceEntityId = cause != null ? cause.getId() : -1;
        this.position = sourcePosition;
        return true;
    }

    @Nullable
    private SoundHuntRules.Noise classify(Holder<GameEvent> event) {
        if (event.is(GameEvent.STEP.key())) {
            return SoundHuntRules.Noise.STEP;
        }
        if (event.is(GameEvent.HIT_GROUND.key())) {
            return SoundHuntRules.Noise.LAND;
        }
        if (event.is(GameEvent.BLOCK_DESTROY.key())) {
            return SoundHuntRules.Noise.MINE;
        }
        if (this.hearOtherNoises && event.is(GameEvent.PROJECTILE_LAND.key())) {
            return SoundHuntRules.Noise.OTHER;
        }
        return null;
    }

    /** True if a noise is remembered at {@code now}. */
    public boolean hasFreshNoise(long now) {
        return !SoundHuntRules.expired(this.heardAt, now, this.memoryTicks);
    }

    /** Game time the remembered noise was heard, or {@link Long#MIN_VALUE}. */
    public long heardAt() {
        return this.heardAt;
    }

    /** Where the remembered noise came from. */
    public Vec3 position() {
        return this.position;
    }

    /** The noisy entity, if it is still loaded in this level. */
    @Nullable
    public Entity sourceEntity(ServerLevel level) {
        return this.sourceEntityId < 0 ? null : level.getEntity(this.sourceEntityId);
    }

    /** Forgets the remembered noise (after it was investigated). */
    public void forget() {
        this.heardAt = Long.MIN_VALUE;
        this.loudness = 0;
        this.sourceEntityId = -1;
    }

    public int memoryTicks() {
        return this.memoryTicks;
    }
}
