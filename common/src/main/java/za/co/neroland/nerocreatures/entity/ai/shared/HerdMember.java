package za.co.neroland.nerocreatures.entity.ai.shared;

import net.minecraft.world.phys.Vec3;

/**
 * A prey creature that shares alarms with its herd. Implemented by the creature (Mossback, Rime
 * Yak, Prism Stag, Lumen Moth...). {@link PreyFleeGoal} raises the alarm on herd mates found in its
 * own bounded query, so one sighting moves the whole herd without each member scanning.
 *
 * <p>Implementations keep the alarm in two plain fields; nothing is saved (an alarm is seconds
 * long).
 */
public interface HerdMember {

    /** Raises (or extends) an alarm about a threat at {@code threat} until {@code untilGameTime}. */
    void raiseAlarm(Vec3 threat, long untilGameTime);

    /** Game time the current alarm ends; {@link Long#MIN_VALUE} when never alarmed. */
    long alarmUntil();

    /** Where the alarming threat was. */
    Vec3 alarmThreat();
}
