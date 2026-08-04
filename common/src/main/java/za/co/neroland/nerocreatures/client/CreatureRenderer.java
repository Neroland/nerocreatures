package za.co.neroland.nerocreatures.client;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;

/**
 * The one renderer every NeroCreature uses. Each creature differs only in its model, its texture and
 * a fine-tuning scale/shadow, so there is nothing to specialise — those three are constructor
 * arguments and {@link ClientEntityRenderers} supplies them.
 *
 * <p>Notably absent: any per-creature render state. The two creatures with visual state solve it
 * through vanilla instead — the Asteroid Worm reports {@code isInvisible()} while burrowed (so
 * vanilla simply does not draw it), and the Plasma Slime drives the {@code minecraft:scale}
 * attribute (which vanilla applies to both the hitbox and the model). Keeping the client dumb is
 * what lets this mod stay server-authoritative.
 */
public class CreatureRenderer extends MobRenderer<Mob, LivingEntityRenderState, EntityModel<LivingEntityRenderState>> {

    private final Identifier texture;
    private final float scale;

    /**
     * @param context the loader-supplied renderer context
     * @param model   the creature's (already baked) model
     * @param texture the creature's entity texture
     * @param scale   uniform fine-tuning scale applied on top of the model geometry
     * @param shadow  shadow radius in blocks
     */
    public CreatureRenderer(EntityRendererProvider.Context context,
            EntityModel<LivingEntityRenderState> model, Identifier texture, float scale, float shadow) {
        super(context, model, shadow);
        this.texture = texture;
        this.scale = scale;
    }

    @Override
    protected void scale(LivingEntityRenderState state, PoseStack poseStack) {
        poseStack.scale(this.scale, this.scale, this.scale);
    }

    @Override
    public LivingEntityRenderState createRenderState() {
        return new LivingEntityRenderState();
    }

    @Override
    public Identifier getTextureLocation(LivingEntityRenderState state) {
        return this.texture;
    }
}
