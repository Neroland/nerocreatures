package za.co.neroland.nerocreatures.entity.boss;

/**
 * The Facet Colossus's facet rules as pure logic (unit-tested without a world).
 *
 * <p>The Colossus wears a ring of {@value #FACETS} facet plates. Exactly one is <b>vulnerable</b> (the
 * lit one); every other plate is armour. The vulnerable facet <b>rotates</b>: on a phase-scaled timer
 * the server picks a different facet, never the same one twice in a row.
 *
 * <h2>Geometry</h2>
 *
 * <p>Angles are Minecraft yaw degrees (yaw 0 faces +z, the direction vector is
 * {@code (-sin yaw, cos yaw)}). Facet {@code i} faces {@code bodyYaw + ring + 60 * i}, where
 * {@code ring} is the ring angle. The ring angle is a function of the <b>level game time</b> and the
 * phase ({@link #ringAngle}), so it is the same number on every server tick and needs no saving.
 * A hit lands on facet {@code i} when the attacker stands within {@value #HIT_HALF_ANGLE} degrees of
 * that facet's facing (deliberately wider than half a plate, 30 degrees, so a player aiming at the lit
 * plate is never cheated by a tick of drift).
 *
 * <p>While the beam fires the ring stops and the vulnerable facet turns to the front
 * ({@link #beamRingAngle}): the only way to hit it then is to stand in front of the Colossus, which
 * is where the beam goes.
 *
 * <h2>Phases</h2>
 *
 * <ul>
 *   <li>Phase 1: one vulnerable facet, rotating every {@code rotationTicks(1)}.</li>
 *   <li>Phase 2 and 3: two <b>decoy</b> facets as well ({@link #decoyMask}). A decoy takes no damage
 *       and answers with a capped retaliation spark.</li>
 *   <li>Phase 3: for {@link #exposedTicks} ticks after every rotation the <b>core</b> is exposed and
 *       takes half damage from any side.</li>
 * </ul>
 */
public final class FacetRules {

    /** Facet plates on the ring. */
    public static final int FACETS = 6;

    /** Degrees between neighbouring facets. */
    public static final double FACET_STEP = 360.0D / FACETS;

    /** Half-width of the arc in which a hit counts as striking a facet, in degrees. */
    public static final double HIT_HALF_ANGLE = 40.0D;

    /** Ticks after which every ring speed has turned a whole number of times (keeps the maths exact). */
    public static final long RING_PERIOD_TICKS = 4800L;

    /** Damage multiplier for a hit on the exposed core (phase 3, between rotations). */
    public static final float CORE_DAMAGE_FACTOR = 0.5F;

    /** Ticks between two retaliation sparks from decoy hits (per Colossus). */
    public static final int RETALIATION_COOLDOWN_TICKS = 10;

    /** What a hit struck. */
    public enum Hit {
        /** The lit, vulnerable facet: full damage. */
        VULNERABLE,
        /** The exposed core (phase 3, between rotations): {@link #CORE_DAMAGE_FACTOR} damage. */
        CORE,
        /** A decoy facet (phase 2+): no damage, a retaliation spark. */
        DECOY,
        /** Any other plate: no damage. */
        ARMOURED,
        /** Damage with no source position (fall, void, commands): lands as normal. */
        UNSOURCED
    }

    private FacetRules() {
    }

    /** Ticks between facet rotations for a phase (1-3), before the difficulty scaling. */
    public static int rotationTicks(int phase) {
        return switch (phase) {
            case 2 -> 120;
            case 3 -> 100;
            default -> 160;
        };
    }

    /**
     * The next vulnerable facet: always a different one from {@code current}.
     *
     * @param roll any int (a random draw); only its value modulo 5 matters
     */
    public static int nextFacet(int current, int roll) {
        int step = 1 + Math.floorMod(roll, FACETS - 1);
        return Math.floorMod(Math.floorMod(current, FACETS) + step, FACETS);
    }

    /** Ring spin in degrees per tick for a phase; matches the art (level, faster, slow drift). */
    public static double spinPerTick(int phase) {
        return switch (phase) {
            case 2 -> 3.0D;
            case 3 -> 0.75D;
            default -> 1.5D;
        };
    }

    /** The ring angle at a game time, in degrees {@code [0, 360)}. */
    public static double ringAngle(long gameTime, int phase) {
        return wrap360(Math.floorMod(gameTime, RING_PERIOD_TICKS) * spinPerTick(phase));
    }

    /** The ring angle that turns {@code facet} to the front (the beam stance). */
    public static double beamRingAngle(int facet) {
        return wrap360(-FACET_STEP * Math.floorMod(facet, FACETS));
    }

    /** The world yaw facet {@code facet} faces. */
    public static double facetYaw(double bodyYaw, double ringAngle, int facet) {
        return wrap360(bodyYaw + ringAngle + FACET_STEP * Math.floorMod(facet, FACETS));
    }

    /** The yaw pointing from the Colossus towards an offset {@code (dx, dz)}. */
    public static double attackYaw(double dx, double dz) {
        return wrap360(Math.toDegrees(Math.atan2(-dx, dz)));
    }

    /** Absolute angular difference, {@code [0, 180]}. */
    public static double angleBetween(double a, double b) {
        double d = wrap360(a - b);
        return d > 180.0D ? 360.0D - d : d;
    }

    /** The facet an attack yaw strikes, or -1 when it falls in no facet's hit arc. */
    public static int facetStruck(double attackYaw, double bodyYaw, double ringAngle) {
        int best = -1;
        double bestDiff = Double.MAX_VALUE;
        for (int i = 0; i < FACETS; i++) {
            double diff = angleBetween(attackYaw, facetYaw(bodyYaw, ringAngle, i));
            if (diff <= HIT_HALF_ANGLE && diff < bestDiff) {
                best = i;
                bestDiff = diff;
            }
        }
        return best;
    }

    /** Whether decoy facets exist in a phase. */
    public static boolean decoysActive(int phase) {
        return phase >= 2;
    }

    /**
     * Two distinct decoy facets, neither the vulnerable one, as a bit mask (bit {@code i} = facet
     * {@code i}).
     */
    public static int decoyMask(int vulnerable, int roll) {
        int v = Math.floorMod(vulnerable, FACETS);
        int[] others = new int[FACETS - 1];
        int n = 0;
        for (int i = 0; i < FACETS; i++) {
            if (i != v) {
                others[n++] = i;
            }
        }
        int r = Math.floorMod(roll, 20);
        int a = r % others.length;
        int b = (a + 1 + (r / others.length) % (others.length - 1)) % others.length;
        return (1 << others[a]) | (1 << others[b]);
    }

    /** How long the core stays exposed after a rotation in a phase (0 = never). */
    public static int exposedTicks(int phase) {
        return phase >= 3 ? 30 : 0;
    }

    /**
     * Classifies a hit.
     *
     * @param hasSource   the damage has a source position
     * @param attackYaw   yaw from the Colossus to the source ({@link #attackYaw})
     * @param bodyYaw     the Colossus's body yaw
     * @param ringAngle   the current ring angle
     * @param vulnerable  the vulnerable facet
     * @param decoys      decoy bit mask (0 = none)
     * @param coreExposed the core is currently exposed
     */
    public static Hit classify(boolean hasSource, double attackYaw, double bodyYaw, double ringAngle,
            int vulnerable, int decoys, boolean coreExposed) {
        if (!hasSource) {
            return Hit.UNSOURCED;
        }
        // The lit facet gets the generous arc on its own, so a hit near a plate edge goes the player's way.
        if (angleBetween(attackYaw, facetYaw(bodyYaw, ringAngle, vulnerable)) <= HIT_HALF_ANGLE) {
            return Hit.VULNERABLE;
        }
        if (coreExposed) {
            return Hit.CORE;
        }
        int struck = facetStruck(attackYaw, bodyYaw, ringAngle);
        if (struck >= 0 && (decoys & (1 << struck)) != 0) {
            return Hit.DECOY;
        }
        return Hit.ARMOURED;
    }

    /** The damage multiplier for a classified hit. */
    public static float damageFactor(Hit hit) {
        return switch (hit) {
            case VULNERABLE, UNSOURCED -> 1.0F;
            case CORE -> CORE_DAMAGE_FACTOR;
            case DECOY, ARMOURED -> 0.0F;
        };
    }

    /**
     * The {@code variant} the model reads: the lit facet (0-5), or 0 while the beam fires (the ability
     * clip aligns plate 0 to the front).
     */
    public static int visualVariant(boolean beaming, int vulnerable) {
        return beaming ? 0 : Math.floorMod(vulnerable, FACETS);
    }

    /**
     * The decoy mask in the model's facet numbering: as-is, or turned with the lit facet while the beam
     * fires (the model then shows the vulnerable facet as plate 0, see {@link #visualVariant}).
     */
    public static int visualDecoys(boolean beaming, int vulnerable, int decoys) {
        int mask = decoys & ((1 << FACETS) - 1);
        if (!beaming || mask == 0) {
            return mask;
        }
        int shift = Math.floorMod(vulnerable, FACETS);
        int out = 0;
        for (int i = 0; i < FACETS; i++) {
            if ((mask & (1 << i)) != 0) {
                out |= 1 << Math.floorMod(i - shift, FACETS);
            }
        }
        return out;
    }

    /**
     * The synced {@code form} for a two-facet decoy mask: the first decoy facet + 1, or 0 for none. "First"
     * is the one from which the other lies 1-3 facets further round (the lower index on a tie), so the
     * pair always fits {@link #decoyStage}.
     */
    public static int decoyForm(int mask) {
        int[] pair = decoyPair(mask);
        return pair == null ? 0 : pair[0] + 1;
    }

    /** The synced {@code stage} for a decoy mask: the cyclic gap to the second decoy − 1 (0-2); 0 for none. */
    public static int decoyStage(int mask) {
        int[] pair = decoyPair(mask);
        return pair == null ? 0 : pair[1] - 1;
    }

    /** The decoy mask a client reads back from {@code form} and {@code stage} (0 = none). */
    public static int decoyMaskOf(int form, int stage) {
        if (form < 1 || form > FACETS || stage < 0 || stage > 2) {
            return 0;
        }
        int first = form - 1;
        return (1 << first) | (1 << ((first + stage + 1) % FACETS));
    }

    /** {first facet, cyclic gap 1-3}, or null unless exactly two facets are set. */
    private static int[] decoyPair(int mask) {
        int m = mask & ((1 << FACETS) - 1);
        if (Integer.bitCount(m) != 2) {
            return null;
        }
        int a = Integer.numberOfTrailingZeros(m);
        int b = 31 - Integer.numberOfLeadingZeros(m);
        int gap = b - a;
        return gap <= FACETS / 2 ? new int[] {a, gap} : new int[] {b, FACETS - gap};
    }

    private static double wrap360(double degrees) {
        double d = degrees % 360.0D;
        return d < 0.0D ? d + 360.0D : d;
    }
}
