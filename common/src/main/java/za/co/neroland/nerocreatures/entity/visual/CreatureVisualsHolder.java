package za.co.neroland.nerocreatures.entity.visual;

/**
 * Implemented by every NeroCreatures entity root ({@code NeroCreatureEntity}, {@code TameableCreature},
 * and through them every species), so renderers, the gallery and shared goals reach the visuals
 * without knowing the class root.
 */
public interface CreatureVisualsHolder {

    /** This entity's visual state; never null. */
    CreatureVisuals visuals();
}
