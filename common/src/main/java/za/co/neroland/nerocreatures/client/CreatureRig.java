package za.co.neroland.nerocreatures.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A creature's animation data, generated from {@code art/models/<id>.json} by
 * {@code tools/gen_entity_models.py}: the part tree, procedural motion ({@code swing}, {@code wave},
 * {@code look}), keyframe clips and part-visibility groups. Plain Java, no game classes.
 *
 * <p>{@link #evaluate} is a line-for-line port of the reference evaluator in {@code tools/art/spec.py}
 * ({@code evaluate()}, {@code sample()}, {@code ease()}, {@code clip_time()}), in double precision,
 * so the in-game pose matches the preview. The one addition is {@code look}, which the reference
 * leaves out (the preview has no head angle); with zero head angles it adds nothing.
 */
public final class CreatureRig {

    /** Radians of walk cycle per unit of walk position, as vanilla limbs use. */
    public static final double WALK_FREQUENCY = 0.6662;
    /** Cross-fade length of an action clip. */
    public static final double BLEND_TICKS = 4.0;
    /** Length of the hurt clip. */
    public static final double HURT_TICKS = 6.0;
    /** Glow intensity when no clip says otherwise ({@code art/palette.json} rules.glow.intensity.dim). */
    public static final double GLOW_DIM = 0.35;

    public static final int LINEAR = 0;
    public static final int SMOOTH = 1;

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

    /** One channel of one part: {@code keys} is {@code t, x, y, z, ease} repeated. */
    record Channel(int part, boolean rotation, double[] keys) {
    }

    /** {@code glow} is {@code t, v, ease} repeated, or null. */
    record Clip(double length, boolean loop, Channel[] channels, double[] glow) {
    }

    /** Bit masks of allowed values (0 = any) and the flags that must all be set. */
    record Visibility(int part, int stages, int variants, int forms, int flags) {

        boolean test(AnimInput in) {
            return (stages == 0 || (stages & (1 << in.stage)) != 0)
                    && (variants == 0 || (variants & (1 << in.variant)) != 0)
                    && (forms == 0 || (forms & (1 << in.form)) != 0)
                    && (in.flags & flags) == flags;
        }
    }

    private final String[] names;
    private final int[] parents;
    private final Swing[] swings;
    private final Wave[] waves;
    private final Look[] looks;
    private final Clip[] clips;
    private final Visibility[] visibility;

    private CreatureRig(Builder b) {
        this.names = b.names.toArray(new String[0]);
        this.parents = b.parents.stream().mapToInt(Integer::intValue).toArray();
        this.swings = b.swings.toArray(new Swing[0]);
        this.waves = b.waves.toArray(new Wave[0]);
        this.looks = b.looks.toArray(new Look[0]);
        this.clips = new Clip[CLIP_NAMES.length];
        for (int i = 0; i < CLIP_NAMES.length; i++) {
            ClipBuilder c = b.clips.get(CLIP_NAMES[i]);
            if (c != null) {
                this.clips[i] = new Clip(c.length, c.loop, c.channels.toArray(new Channel[0]), c.glow);
            }
        }
        this.visibility = b.visibility.toArray(new Visibility[0]);
    }

    public static Builder builder() {
        return new Builder();
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

    /** True when part {@code index} is shown for this input's stage, variant, form and flags. */
    public boolean visible(int index, AnimInput in) {
        for (Visibility v : visibility) {
            if (v.part() == index && !v.test(in)) {
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

    private static void add(Clip clip, double t, double w, double[] rot, double[] pos) {
        if (w <= 0.0 || rot == null) {
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
     * Pose for one frame. Fills {@code rot} (degrees) and {@code pos} (units), three per part, with
     * the values to ADD to each part's rest pose, and returns the glow intensity 0..1. Pass null
     * arrays to compute only the glow.
     */
    public double evaluate(AnimInput in, double[] rot, double[] pos) {
        double age = in.age;
        double walkPos = in.walkPos;
        double walk = Math.max(0.0, Math.min(1.0, in.walkSpeed));
        if (rot != null) {
            java.util.Arrays.fill(rot, 0.0);
            java.util.Arrays.fill(pos, 0.0);
            for (Swing s : swings) {
                double angle = Math.cos(walkPos * WALK_FREQUENCY + Math.toRadians(s.phase()));
                rot[s.part() * 3] += angle * s.amplitude() * walk;
            }
            for (Wave w : waves) {
                double strength = w.fadeWithWalk() ? (1.0 - walk) : 1.0;
                double value = Math.sin(age * w.frequency() + Math.toRadians(w.phase()));
                rot[w.part() * 3 + w.axis()] += value * w.amplitude() * strength;
            }
        }

        double glow = GLOW_DIM;
        Clip action = switch (in.pose) {
            case 1 -> clips[ABILITY];
            case 2 -> clips[ABILITY_2];
            case 3 -> clips[REST];
            default -> null;
        };
        double weight = action != null ? Math.min(1.0, in.poseTicks / BLEND_TICKS) : 0.0;

        Clip held = in.phase >= 1 && in.phase <= 3 && clips[PHASE_1 + in.phase - 1] != null
                ? clips[PHASE_1 + in.phase - 1] : clips[IDLE];
        if (held != null) {
            add(held, clipTime(held, age), (1.0 - walk) * (1.0 - weight), rot, pos);
            if (held.glow() != null) {
                glow = sample(held.glow(), 3, 0, clipTime(held, age));
            }
        }
        Clip move = clips[MOVE];
        if (move != null) {
            double cycle = pyMod(walkPos * WALK_FREQUENCY, 2.0 * Math.PI) / (2.0 * Math.PI);
            add(move, cycle * move.length(), walk * (1.0 - weight), rot, pos);
        }
        if (action != null) {
            double t = clipTime(action, in.poseTicks);
            add(action, t, weight, rot, pos);
            if (action.glow() != null) {
                glow = glow + (sample(action.glow(), 3, 0, t) - glow) * weight;
            }
        }
        Clip hurt = clips[HURT];
        if (in.hurtTicks >= 0.0 && hurt != null) {
            double t = Math.min(in.hurtTicks, HURT_TICKS);
            add(hurt, t, 1.0, rot, pos);
            if (hurt.glow() != null) {
                glow = sample(hurt.glow(), 3, 0, t);
            }
        }
        if (rot != null) {
            for (Look l : looks) {
                int base = l.part() * 3;
                rot[base + 1] += clamp(in.headYaw * l.yaw(), l.limit());
                rot[base] += clamp(in.headPitch * l.pitch(), l.limit());
            }
        }
        return Math.max(0.0, Math.min(1.0, glow));
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
        private final Map<String, ClipBuilder> clips = new HashMap<>();
        private final List<Visibility> visibility = new ArrayList<>();
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

        /** Starts a clip; the following {@link #rot}, {@link #pos} and {@link #glow} calls belong to it. */
        public Builder clip(String name, double length, boolean loop) {
            current = new ClipBuilder(length, loop);
            clips.put(name, current);
            return this;
        }

        /** Rotation keys in degrees: {@code t, x, y, z, ease} repeated. */
        public Builder rot(String part, double... keys) {
            current.channels.add(new Channel(index(part), true, keys));
            return this;
        }

        /** Offset keys in units: {@code t, x, y, z, ease} repeated. */
        public Builder pos(String part, double... keys) {
            current.channels.add(new Channel(index(part), false, keys));
            return this;
        }

        /** Glow keys: {@code t, v, ease} repeated. */
        public Builder glow(double... keys) {
            current.glow = keys;
            return this;
        }

        public Builder visibleWhen(String part, int stages, int variants, int forms, int flags) {
            visibility.add(new Visibility(index(part), stages, variants, forms, flags));
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
