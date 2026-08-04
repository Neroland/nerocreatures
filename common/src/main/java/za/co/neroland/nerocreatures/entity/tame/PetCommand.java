package za.co.neroland.nerocreatures.entity.tame;

import java.util.Locale;

/**
 * What a tamed pet has most recently been told to do. Shift-interacting with your own pet cycles
 * through these <b>in declaration order</b>, so the loop a player experiences is
 * <em>sit &rarr; stay &rarr; follow &rarr; sit</em> — a freshly tamed pet starts on
 * {@link #FOLLOW}, and the first shift-click sits it down.
 *
 * <p>Three states rather than vanilla's two exist because "sit here forever" and "hold this spot
 * but keep watching my back" are genuinely different orders, and a pet that guards a doorway is the
 * one thing players ask a tamed mob for that vanilla's sit/follow pair cannot express.
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
    FOLLOW;

    /** The next command in the cycle, wrapping at the end. */
    public PetCommand next() {
        PetCommand[] all = values();
        return all[(this.ordinal() + 1) % all.length];
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
