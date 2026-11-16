package za.co.neroland.nerocreatures.entity.tame;

import java.util.Locale;

/**
 * What a tamed pet has most recently been told to do. Shift-interacting with your own pet cycles
 * through the orders in the fixed loop <em>sit &rarr; stay &rarr; guard &rarr; follow &rarr; sit</em>
 * ({@link #next()}) — a freshly tamed pet starts on {@link #FOLLOW}, and the first shift-click sits it
 * down.
 *
 * <p>Four states rather than vanilla's two exist because "sit here", "stand here and do nothing",
 * "hold this spot but keep watching my back" and "come with me" are genuinely different orders, and a
 * pet that guards a doorway is the one thing players ask a tamed mob for that vanilla's sit/follow
 * pair cannot express.
 *
 * <p><b>Ids are saved and sent to the companion app</b>, so constants are append-only: {@link #STAY}
 * (0.5.0) was added after the three 0.4.x orders and never reorders them. The in-game cycle order is
 * {@code CYCLE}, not the declaration order.
 *
 * <p>The command is <b>server-side state</b>: it is saved in the entity's own data and never synced
 * to clients. What the client needs to draw — the sitting pose — rides on vanilla
 * {@code TamableAnimal}'s already-synced flag, which {@link TameableCreature#setCommand} keeps in
 * step.
 */
public enum PetCommand {

    /** Sit down, stay put, do nothing. Vanilla's ordered-to-sit state. */
    SIT,

    /** Hold this area: no following, but still defends its owner and itself. */
    GUARD,

    /** Follow the owner around. The state a pet is tamed into. */
    FOLLOW,

    /**
     * Stand exactly here (0.5.0): no following, no wandering, no guarding and no fighting, not even
     * back. Unlike {@link #SIT} the pet stays on its feet. Appended, so older ids are unchanged.
     */
    STAY;

    /** The in-game shift-click loop. */
    private static final PetCommand[] CYCLE = {SIT, STAY, GUARD, FOLLOW};

    /** The next command in the in-game cycle, wrapping at the end. */
    public PetCommand next() {
        for (int i = 0; i < CYCLE.length; i++) {
            if (CYCLE[i] == this) {
                return CYCLE[(i + 1) % CYCLE.length];
            }
        }
        return FOLLOW;
    }

    /**
     * Strict parse for a request from outside the game (the companion app): exactly {@code follow},
     * {@code stay} or {@code sit}, lower case. Anything else, including {@code guard} (an in-game
     * order the app does not offer), is empty, so an unknown key is refused instead of silently
     * becoming {@link #FOLLOW}.
     */
    public static java.util.Optional<PetCommand> fromRemoteKey(String key) {
        if (key == null) {
            return java.util.Optional.empty();
        }
        return switch (key) {
            case "follow" -> java.util.Optional.of(FOLLOW);
            case "stay" -> java.util.Optional.of(STAY);
            case "sit" -> java.util.Optional.of(SIT);
            default -> java.util.Optional.empty();
        };
    }

    /** Lower-case id, used for the saved value and the lang key suffix. */
    public String key() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    /** Lenient parse for saved data: anything unrecognised reads back as {@link #FOLLOW}. */
    public static PetCommand fromKey(String key) {
        for (PetCommand command : values()) {
            if (command.key().equalsIgnoreCase(key)) {
                return command;
            }
        }
        return FOLLOW;
    }
}
