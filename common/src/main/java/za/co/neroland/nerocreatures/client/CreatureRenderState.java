package za.co.neroland.nerocreatures.client;

//? if >=1.21.2 {
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
//?}

/**
 * The render state every NeroCreatures renderer fills (1.21.2+): vanilla's living state plus the
 * {@link AnimInput} the model evaluates and the glow layer's intensity. On 1.21.1 there is no render
 * state; the model and the glow layer read the entity instead, and this class is only a holder.
 *
 * <p>Everything a model needs must be in here: from 1.21.9 the model is posed when the submitted
 * frame is drawn, not when it is extracted.
 */
//? if >=1.21.2 {
public class CreatureRenderState extends LivingEntityRenderState {
//?} else {
/*public class CreatureRenderState {
*///?}

    /** Clocks, walk cycle, head angles and synced visuals for this frame. */
    public final AnimInput anim = new AnimInput();
    /** Glow intensity 0..1 from the rig's clips (dim when nothing plays). */
    public float glow;
}
