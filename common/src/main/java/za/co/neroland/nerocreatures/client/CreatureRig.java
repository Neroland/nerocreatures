package za.co.neroland.nerocreatures.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

/**
 * A creature's animation data, generated from {@code art/models/<id>.json} by
 * {@code tools/gen_entity_models.py}: the part tree, procedural motion ({@code swing}, {@code wave},
 * {@code look}, {@code spin}), keyframe clips, part-visibility groups and glow rules. Plain Java, no
 * game classes.
 *
 * <p>{@link #evaluate} is a line-for-line port of the reference evaluator in {@code tools/art/spec.py}
 * ({@code evaluate()}, {@code sample()}, {@code ease()}, {@code clip_time()},
 * {@code apply_glow_rules()}), in double precision, so the in-game pose matches the preview. The one
 * addition is {@code look}, which the reference leaves out (the preview has no head angle); with zero
 * head angles it adds nothing.
 *
 * <p>Layering, as in the reference: procedural motion; the held layer ({@code idle}, or the boss
 * {@code phase_N} stance, with {@code move} by walk speed); the pose layer, where an action clip
 * replaces (or, for {@code "blend": "add"} tracks, adds to) only the channels it keys, so a stance
 * survives an ability, cross-faded over {@link #BLEND_TICKS} from whatever was showing before (the held
 * layer, or the previous action clip frozen where it stopped); {@code hurt} on top; glow rules last.
 */
public final class CreatureRig {

    /** Radians of walk cycle per unit of walk position, as vanilla limbs use. */
    public static final double WALK_FREQUENCY = 0.6662;
    /** Cross-fade length between poses. */
    public static final double BLEND_TICKS = 4.0;
    /** Length of the hurt clip. */
    public static final double HURT_TICKS = 6.0;
    /** Glow intensity when no clip says otherwise ({@code art/palette.json} rules.glow.intensity.dim). */
    public static final double GLOW_DIM = 0.35;

    public static final int LINEAR = 0;
    public static final int SMOOTH = 1;

    /** Condition bits for the synced pose ({@code VisualPose} ordinals 0-3). */
    public static final int POSE_IDLE = 1;
    public static final int POSE_ABILITY = 1 << 1;
    public static final int POSE_ABILITY_2 = 1 << 2;
    public static final int POSE_REST = 1 << 3;

    // Clip slots.
    static final int IDLE = 0;
    static final int MOVE = 1;
    static final int ABILITY = 2;
    static final int ABILITY_2 = 3;
    static final int REST = 4;
    static final int HURT = 5;
    static final int PHASE_1 = 6;
    private static final String[] CLIP_NAMES = {
        "idle", "move", "ability", "ability_2", "rest", "hurt", "phase_1", "phase_2", "phase_3"};

    /** A walk-cycle swing on the part's x axis. */
    record Swing(int part, double phase, double amplitude) {
    }

    /** An age-driven sine on one axis (0 x, 1 y, 2 z). */
    record Wave(int part, int axis, double frequency, double phase, double amplitude, boolean fadeWithWalk) {
    }

    /** The share of head yaw and pitch a part takes, each clamped to {@code limit} degrees. */
    record Look(int part, double yaw, double pitch, double limit) {
    }

    /** A continuous turn of {@code speed} degrees per tick on one axis while {@code when} holds. */
    record Spin(int part, int axis, double speed, double phase, @Nullable Condition when) {
    }

    /** One channel of one part: {@code keys} is {@code t, x, y, z, ease} repeated. */
    record Channel(int part, boolean rotation, boolean additive, double[] keys) {
    }

    /** {@code glow} is {@code t, v, ease} repeated, or null. */
    record Clip(double length, boolean loop, Channel[] channels, double[] glow) {
    }

    /**
     * A visual-state condition: bit masks of allowed stage, variant, form, phase and pose values
     * (0 = any) and the {@code AnimInput.FLAG_*} flags that must all be set.
     */
    public record Condition(int stages, int variants, int forms, int flags, int phases, int poses) {

        public boolean test(AnimInput in) {
            return test(in, in.pose);
        }

        boolean test(AnimInput in, int pose) {
            return allows(stages, in.stage) && allows(variants, in.variant) && allows(forms, in.form)
                    && allows(phases, in.phase) && allows(poses, pose) && (in.flags & flags) == flags;
        }

        private static boolean allows(int mask, int value) {
            return mask == 0 || (value >= 0 && value < 31 && (mask & (1 << value)) != 0);
        }
    }

    /** Part {@code part} is shown only while {@code when} holds. */
    record Visibility(int part, Condition when) {
    }

    /** Clamp ({@code min}/{@code max}, NaN = none) then flicker every {@code flicker} ticks (0 = none). */
    record GlowRule(@Nullable Condition when, double min, double max, int flicker, double low) {
    }

    private final String[] names;
    private final int[] parents;
    private final Swing[] swings;
    private final Wave[] waves;
    private final Look[] looks;
    private final Spin[] spins;
    private final Clip[] clips;
    private final Visibility[] visibility;
    private final GlowRule[] glowRules;

    private CreatureRig(Builder b) {
        this.names = b.names.toArray(new String[0]);
        this.parents = b.parents.stream().mapToInt(Integer::intValue).toArray();
        this.swings = b.swings.toArray(new Swing[0]);
        this.waves = b.waves.toArray(new Wave[0]);
        this.looks = b.looks.toArray(new Look[0]);
        this.spins = b.spins.toArray(new Spin[0]);
        this.clips = new Clip[CLIP_NAMES.length];
        for (int i = 0; i < CLIP_NAMES.length; i++) {
            ClipBuilder c = b.clips.get(CLIP_NAMES[i]);
            if (c != null) {
                this.clips[i] = new Clip(c.length, c.loop, c.channels.toArray(new Channel[0]), c.glow);
            }
        }
        this.visibility = b.visibility.toArray(new Visibility[0]);
        this.glowRules = b.glowRules.toArray(new GlowRule[0]);
    }

    public static Builder builder() {
        return new Builder();
    }

    /** A condition for {@link Builder#spin}, {@link Builder#visibleWhen} and {@link Builder#glowRule}. */
    public static Condition when(int stages, int variants, int forms, int flags, int phases, int poses) {
        return new Condition(stages, variants, forms, flags, phases, poses);
    }

    public int partCount() {
        return names.length;
    }

    public String partName(int index) {
        return names[index];
    }

    /** The parent's index, or -1 for a part directly under the model root. Parents come first. */
    public int parentOf(int index) {
        return parents[index];
    }

    /** Index of a part by name, or -1. */
    public int indexOf(String name) {
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(name)) {
                return i;
            }
        }
        return -1;
    }

    /** True when part {@code index} is shown for this input's stage, variant, form, phase, pose and flags. */
    public boolean visible(int index, AnimInput in) {
        for (Visibility v : visibility) {
            if (v.part() == index && !v.when().test(in)) {
                return false;
            }
        }
        return true;
    }

    boolean hasVisibilityRules() {
        return visibility.length > 0;
    }

    // --- reference evaluator port ---------------------------------------------------------------

    static double ease(double fraction, int mode) {
        return mode == SMOOTH ? fraction * fraction * (3.0 - 2.0 * fraction) : fraction;
    }

    /** Python's float {@code %}: the result takes the sign of the divisor. */
    static double pyMod(double a, double m) {
        double r = a % m;
        if (r != 0.0 && (r < 0.0) != (m < 0.0)) {
            r += m;
        }
        return r;
    }

    static double clipTime(Clip clip, double clock) {
        return clip.loop() ? pyMod(clock, clip.length()) : Math.min(clock, clip.length());
    }

    /** One component of a channel at tick {@code t}; {@code stride} 5 for vectors, 3 for glow. */
    static double sample(double[] keys, int stride, int component, double t) {
        int count = keys.length / stride;
        int value = 1 + component;
        if (t <= keys[0]) {
            return keys[value];
        }
        for (int k = 0; k + 1 < count; k++) {
            int a = k * stride;
            int b = a + stride;
            if (t <= keys[b]) {
                double f = ease((t - keys[a]) / (keys[b] - keys[a]), (int) keys[b + stride - 1]);
                return keys[a + value] + (keys[b + value] - keys[a + value]) * f;
            }
        }
        return keys[(count - 1) * stride + value];
    }

    /** Adds {@code clip} at tick {@code t} times {@code w} into the arrays. */
    private static void add(Clip clip, double t, double w, double[] rot, double[] pos) {
        if (w <= 0.0) {
            return;
        }
        for (Channel channel : clip.channels()) {
            double[] target = channel.rotation() ? rot : pos;
            int base = channel.part() * 3;
            for (int i = 0; i < 3; i++) {
                target[base + i] += sample(channel.keys(), 5, i, t) * w;
            }
        }
    }

    /**
     * Writes an action clip over a copy of the held layer: keyed channels are replaced (or added to,
     * for additive tracks); returns the layer's glow. A null clip leaves the held layer as it is.
     */
    private static double layer(@Nullable Clip clip, double clock, double heldGlow, double[] rot, double[] pos) {
        if (clip == null) {
            return heldGlow;
        }
        double t = clipTime(clip, clock);
        for (Channel channel : clip.channels()) {
            double[] target = channel.rotation() ? rot : pos;
            int base = channel.part() * 3;
            for (int i = 0; i < 3; i++) {
                double value = sample(channel.keys(), 5, i, t);
                target[base + i] = (channel.additive() ? target[base + i] : 0.0) + value;
            }
        }
        return clip.glow() != null ? sample(clip.glow(), 3, 0, t) : heldGlow;
    }

    @Nullable
    private Clip action(int pose) {
        return switch (pose) {
            case 1 -> clips[ABILITY];
            case 2 -> clips[ABILITY_2];
            case 3 -> clips[REST];
            default -> null;
        };
    }

    private static int poseOf(int pose) {
        return pose >= 1 && pose <= 3 ? pose : 0;
    }

    /**
     * Pose for one frame. Fills {@code rot} (degrees) and {@code pos} (units), three per part, with
     * the values to ADD to each part's rest pose, and returns the glow intensity 0..1. Pass null
     * arrays to compute only the glow (the pose is then worked in scratch arrays).
     */
    public double evaluate(AnimInput in, @Nullable double[] rotOut, @Nullable double[] posOut) {
        int n = names.length * 3;
        double[] rot = rotOut != null && posOut != null ? rotOut : new double[n];
        double[] pos = rotOut != null && posOut != null ? posOut : new double[n];
        double age = in.age;
        double walkPos = in.walkPos;
        double walk = Math.max(0.0, Math.min(1.0, in.walkSpeed));
        Arrays.fill(rot, 0.0);
        Arrays.fill(pos, 0.0);
        for (Swing s : swings) {
            double angle = Math.cos(walkPos * WALK_FREQUENCY + Math.toRadians(s.phase()));
            rot[s.part() * 3] += angle * s.amplitude() * walk;
        }
        for (Wave w : waves) {
            double strength = w.fadeWithWalk() ? (1.0 - walk) : 1.0;
            double value = Math.sin(age * w.frequency() + Math.toRadians(w.phase()));
            rot[w.part() * 3 + w.axis()] += value * w.amplitude() * strength;
        }

        int pose = poseOf(in.pose);
        int source = poseOf(in.fromPose);
        double weight;
        if (pose != 0) {
            weight = Math.min(1.0, in.poseTicks / BLEND_TICKS);
        } else if (in.since >= 0.0) {
            weight = Math.min(1.0, in.since / BLEND_TICKS);
        } else {
            weight = 1.0;
        }

        for (Spin s : spins) {
            Condition when = s.when();
            double now = when == null || when.test(in, pose) ? 1.0 : 0.0;
            double before = when == null || when.test(in, source) ? 1.0 : 0.0;
            double share = before + (now - before) * weight;
            if (share > 0.0) {
                double angle = pyMod(age * s.speed() + s.phase(), 360.0);
                rot[s.part() * 3 + s.axis()] += angle * share;
            }
        }

        double[] heldRot = new double[n];
        double[] heldPos = new double[n];
        double heldGlow = GLOW_DIM;
        Clip held = in.phase >= 1 && in.phase <= 3 && clips[PHASE_1 + in.phase - 1] != null
                ? clips[PHASE_1 + in.phase - 1] : clips[IDLE];
        if (held != null) {
            add(held, clipTime(held, age), 1.0 - walk, heldRot, heldPos);
            if (held.glow() != null) {
                heldGlow = sample(held.glow(), 3, 0, clipTime(held, age));
            }
        }
        Clip move = clips[MOVE];
        if (move != null) {
            double cycle = pyMod(walkPos * WALK_FREQUENCY, 2.0 * Math.PI) / (2.0 * Math.PI);
            add(move, cycle * move.length(), walk, heldRot, heldPos);
        }

        double[] curRot = heldRot.clone();
        double[] curPos = heldPos.clone();
        double glow = layer(action(pose), in.poseTicks, heldGlow, curRot, curPos);
        if (weight < 1.0) {
            double[] srcRot = heldRot.clone();
            double[] srcPos = heldPos.clone();
            double srcGlow = layer(action(source), in.fromTicks, heldGlow, srcRot, srcPos);
            for (int i = 0; i < n; i++) {
                curRot[i] = srcRot[i] + (curRot[i] - srcRot[i]) * weight;
                curPos[i] = srcPos[i] + (curPos[i] - srcPos[i]) * weight;
            }
            glow = srcGlow + (glow - srcGlow) * weight;
        }
        for (int i = 0; i < n; i++) {
            rot[i] += curRot[i];
            pos[i] += curPos[i];
        }

        Clip hurt = clips[HURT];
        if (in.hurtTicks >= 0.0 && hurt != null) {
            double t = Math.min(in.hurtTicks, HURT_TICKS);
            add(hurt, t, 1.0, rot, pos);
            if (hurt.glow() != null) {
                glow = sample(hurt.glow(), 3, 0, t);
            }
        }
        glow = applyGlowRules(in, glow);
        for (Look l : looks) {
            int base = l.part() * 3;
            rot[base + 1] += clamp(in.headYaw * l.yaw(), l.limit());
            rot[base] += clamp(in.headPitch * l.pitch(), l.limit());
        }
        return Math.max(0.0, Math.min(1.0, glow));
    }

    private double applyGlowRules(AnimInput in, double glow) {
        for (GlowRule rule : glowRules) {
            Condition when = rule.when();
            if (when != null && !when.test(in)) {
                continue;
            }
            if (!Double.isNaN(rule.min())) {
                glow = Math.max(glow, rule.min());
            }
            if (!Double.isNaN(rule.max())) {
                glow = Math.min(glow, rule.max());
            }
            if (rule.flicker() > 0 && Math.floorMod((long) Math.floor(in.age / rule.flicker()), 2L) == 1L) {
                glow = Math.min(glow, rule.low());
            }
        }
        return glow;
    }

    private static double clamp(double value, double limit) {
        return Math.max(-limit, Math.min(limit, value));
    }

    // --- builder (the generated code's API) ------------------------------------------------------

    /** Fluent builder used by the generated {@code RIG} constants. */
    public static final class Builder {
        private final List<String> names = new ArrayList<>();
        private final List<Integer> parents = new ArrayList<>();
        private final List<Swing> swings = new ArrayList<>();
        private final List<Wave> waves = new ArrayList<>();
        private final List<Look> looks = new ArrayList<>();
        private final List<Spin> spins = new ArrayList<>();
        private final Map<String, ClipBuilder> clips = new HashMap<>();
        private final List<Visibility> visibility = new ArrayList<>();
        private final List<GlowRule> glowRules = new ArrayList<>();
        private ClipBuilder current;

        private Builder() {
        }

        private int index(String name) {
            int i = names.indexOf(name);
            if (i < 0) {
                throw new IllegalArgumentException("unknown part " + name);
            }
            return i;
        }

        /** Declares a part; {@code parent} null for a part under the model root. Parents first. */
        public Builder part(String name, String parent) {
            parents.add(parent == null ? -1 : index(parent));
            names.add(name);
            return this;
        }

        public Builder swing(String part, double phase, double amplitude) {
            swings.add(new Swing(index(part), phase, amplitude));
            return this;
        }

        /** {@code axis} is {@code 'x'}, {@code 'y'} or {@code 'z'}. */
        public Builder wave(String part, char axis, double frequency, double phase, double amplitude,
                boolean fadeWithWalk) {
            waves.add(new Wave(index(part), axis - 'x', frequency, phase, amplitude, fadeWithWalk));
            return this;
        }

        public Builder look(String part, double yaw, double pitch, double limit) {
            looks.add(new Look(index(part), yaw, pitch, limit));
            return this;
        }

        /** A continuous turn, {@code speed} degrees per tick (signed), always on. */
        public Builder spin(String part, char axis, double speed, double phase) {
            return spin(part, axis, speed, phase, null);
        }

        /** A continuous turn while {@code when} holds (faded across a pose change when it names poses). */
        public Builder spin(String part, char axis, double speed, double phase, @Nullable Condition when) {
            spins.add(new Spin(index(part), axis - 'x', speed, phase, when));
            return this;
        }

        /** Starts a clip; the following {@link #rot}, {@link #pos} and {@link #glow} calls belong to it. */
        public Builder clip(String name, double length, boolean loop) {
            current = new ClipBuilder(length, loop);
            clips.put(name, current);
            return this;
        }

        /** Rotation keys in degrees: {@code t, x, y, z, ease} repeated. */
        public Builder rot(String part, double... keys) {
            current.channels.add(new Channel(index(part), true, false, keys));
            return this;
        }

        /** Offset keys in units: {@code t, x, y, z, ease} repeated. */
        public Builder pos(String part, double... keys) {
            current.channels.add(new Channel(index(part), false, false, keys));
            return this;
        }

        /** As {@link #rot}, added to the held layer instead of replacing it ({@code "blend": "add"}). */
        public Builder rotAdd(String part, double... keys) {
            current.channels.add(new Channel(index(part), true, true, keys));
            return this;
        }

        /** As {@link #pos}, added to the held layer instead of replacing it ({@code "blend": "add"}). */
        public Builder posAdd(String part, double... keys) {
            current.channels.add(new Channel(index(part), false, true, keys));
            return this;
        }

        /** Glow keys: {@code t, v, ease} repeated. */
        public Builder glow(double... keys) {
            current.glow = keys;
            return this;
        }

        public Builder visibleWhen(String part, int stages, int variants, int forms, int flags) {
            return visibleWhen(part, new Condition(stages, variants, forms, flags, 0, 0));
        }

        public Builder visibleWhen(String part, Condition when) {
            visibility.add(new Visibility(index(part), when));
            return this;
        }

        /** A glow rule; {@code min}/{@code max} NaN when absent, {@code flicker} 0 when absent. */
        public Builder glowRule(@Nullable Condition when, double min, double max, int flicker, double low) {
            glowRules.add(new GlowRule(when, min, max, flicker, low));
            return this;
        }

        public CreatureRig build() {
            return new CreatureRig(this);
        }
    }

    private static final class ClipBuilder {
        final double length;
        final boolean loop;
        final List<Channel> channels = new ArrayList<>();
        double[] glow;

        ClipBuilder(double length, boolean loop) {
            this.length = length;
            this.loop = loop;
        }
    }
}
