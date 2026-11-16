package za.co.neroland.nerocreatures.entity.asteroid;

import java.util.ArrayList;
import java.util.List;

/**
 * The pure rules of the Magnet Crab (Asteroid, tameable). Unit-tested; no game types.
 *
 * <ul>
 *   <li><b>Wild shell stealing</b> ({@code magnetCrabSteals}, {@code Change.ITEM}): a wild
 *       crab takes metal items ({@code #nerocreatures:magnetic}) into its shell, at most
 *       {@value #SHELL_SLOTS} stacks of at most {@value #MAX_PER_SLOT}, and drops them all on death.</li>
 *   <li><b>Tamed item magnet</b> ({@code magnetCrabCollects}, {@code Change.ITEM}): every
 *       {@value #MAGNET_PULSE_TICKS} ticks it moves at most {@value #ITEMS_PER_PULSE} ground item within
 *       {@value #MAGNET_RADIUS} blocks to its online owner (owner within {@value #DELIVERY_RANGE} blocks).
 *       Only items whose thrower is the owner or absent, never items with an owner-only pickup target,
 *       never items a Slag Beetle has claimed.</li>
 * </ul>
 */
public final class MagnetCrabRules {

    /** Shell stacks (and the {@code stage} 0-3 plate count). */
    public static final int SHELL_SLOTS = 3;
    /** Items per shell stack. */
    public static final int MAX_PER_SLOT = 16;
    /** Wild: search radius for metal items, in blocks. */
    public static final double STEAL_RADIUS = 5.0D;
    /** Wild: ticks between searches. */
    public static final int STEAL_SCAN_TICKS = 20;
    /** Wild: squared reach at which it grabs. */
    public static final double GRAB_REACH_SQR = 2.25D;
    /** Wild: ticks it chases one item before giving up. */
    public static final int CHASE_TICKS = 100;

    /** Tamed: pull radius around the crab (6 blocks). */
    public static final double MAGNET_RADIUS = 6.0D;
    /** Tamed: ticks between pulses. */
    public static final int MAGNET_PULSE_TICKS = 10;
    /** Tamed: items moved per pulse (rate cap: 2 item entities a second). */
    public static final int ITEMS_PER_PULSE = 1;
    /** Tamed: item entities examined per pulse at most. */
    public static final int MAX_CANDIDATES = 8;
    /** Tamed: the owner must be this close to the crab, in blocks. */
    public static final double DELIVERY_RANGE = 24.0D;
    /** Tamed: items already this close to the owner are left alone (full inventory never loops). */
    public static final double OWNER_SKIP_RADIUS = 2.0D;

    private MagnetCrabRules() {
    }

    /** How many of a {@code stackCount} stack a crab with {@code slotsUsed} full slots may take. */
    public static int takeCount(int stackCount, int slotsUsed) {
        if (stackCount <= 0 || slotsUsed >= SHELL_SLOTS) {
            return 0;
        }
        return Math.min(stackCount, MAX_PER_SLOT);
    }

    /** Grab clip length ({@code ability}) and the tick the claws snap shut (the item is taken then). */
    public static final int GRAB_CLIP_TICKS = 20;
    public static final int GRAB_SNAP_TICK = 12;
    /** Tamed: the held pull pose stays on this long after the last delivery. */
    public static final int PULL_LINGER_TICKS = 40;

    /** The tamed pull's sound replays at most this often while the magnet is delivering (2 s). */
    public static final int MAGNET_SOUND_TICKS = 40;

    /** Whether the magnet loop sound is due again ({@code lastSound} = game time it last played). */
    public static boolean magnetSoundDue(long lastSound, long now) {
        return now - lastSound >= MAGNET_SOUND_TICKS || now < lastSound;
    }

    /**
     * The shell plate visual ({@code stage}) by stored item count: 0 none, then one plate per started
     * {@value #MAX_PER_SLOT} items, at most 3.
     */
    public static int stage(int storedItems) {
        if (storedItems <= 0) {
            return 0;
        }
        return Math.min(3, (storedItems + MAX_PER_SLOT - 1) / MAX_PER_SLOT);
    }

    /** Whether the pull pose should still be held at {@code now} after a delivery at {@code lastPull}. */
    public static boolean stillPulling(long now, long lastPull) {
        return now - lastPull < PULL_LINGER_TICKS;
    }

    /** Wild steal gate. {@code allowed} is the {@code CreatureGriefing} decision. */
    public static boolean maySteal(boolean tame, int slotsUsed, boolean metal, boolean plainStack,
            boolean reserved, boolean allowed) {
        return !tame && slotsUsed < SHELL_SLOTS && metal && plainStack && !reserved && allowed;
    }

    /**
     * Thrower and pickup-target rule. {@code throwerIsOwner}: the thrower resolved to the
     * crab's owner; {@code hasThrower}: the item records a thrower at all; {@code hasTarget}: an
     * owner-only pickup target is set.
     */
    public static boolean claimFree(boolean throwerIsOwner, boolean hasThrower, boolean hasTarget) {
        if (hasTarget) {
            return false;
        }
        return !hasThrower || throwerIsOwner;
    }

    /** Tamed collect gate, in check order. {@code allowed} is the {@code CreatureGriefing} decision. */
    public static boolean mayCollect(boolean tame, boolean reservedForBeetle, boolean claimFree, boolean allowed) {
        return tame && !reservedForBeetle && claimFree && allowed;
    }

    /** Whether the owner is in range for a delivery this pulse. */
    public static boolean ownerInRange(boolean online, double ownerDistSqr) {
        return online && ownerDistSqr <= DELIVERY_RANGE * DELIVERY_RANGE;
    }

    /** Whether another item may still be moved this pulse. */
    public static boolean underRate(int movedThisPulse) {
        return movedThisPulse < ITEMS_PER_PULSE;
    }

    /** Encodes the shell as {@code id*count;id*count} (item ids and counts only). */
    public static String encode(List<String> ids, List<Integer> counts) {
        StringBuilder out = new StringBuilder();
        int n = Math.min(SHELL_SLOTS, Math.min(ids.size(), counts.size()));
        for (int i = 0; i < n; i++) {
            if (out.length() > 0) {
                out.append(';');
            }
            out.append(ids.get(i)).append('*').append(clampCount(counts.get(i)));
        }
        return out.toString();
    }

    /** One decoded shell slot. */
    public record Slot(String id, int count) {
    }

    /** Decodes {@link #encode}; malformed entries are skipped, at most {@value #SHELL_SLOTS} slots. */
    public static List<Slot> decode(String encoded) {
        List<Slot> slots = new ArrayList<>(SHELL_SLOTS);
        if (encoded == null || encoded.isEmpty()) {
            return slots;
        }
        for (String part : encoded.split(";")) {
            if (slots.size() >= SHELL_SLOTS) {
                break;
            }
            int star = part.lastIndexOf('*');
            if (star <= 0 || star == part.length() - 1) {
                continue;
            }
            try {
                int count = Integer.parseInt(part.substring(star + 1));
                if (count > 0) {
                    slots.add(new Slot(part.substring(0, star), clampCount(count)));
                }
            } catch (NumberFormatException ignored) {
                // skip a malformed slot; the rest of the shell survives
            }
        }
        return slots;
    }

    private static int clampCount(int count) {
        return Math.max(1, Math.min(64, count));
    }
}
