package za.co.neroland.nerocreatures.entity.ai.shared;

/**
 * A creature that can lie hidden and erupt (Frost Lurker under snow and ice).
 *
 * <p>Implementations keep the flag in synced entity data so the renderer can hide the body, and
 * decide what "hidden" means for damage (typically: not targetable by mobs, still hittable by a
 * player who finds it). Called only on the server by {@link AmbushGoal}; must tolerate being set to
 * the value it already has.
 */
public interface Ambusher {

    /** True while lying in wait. */
    boolean isLurking();

    /** Enter or leave the hidden state. */
    void setLurking(boolean lurking);
}
