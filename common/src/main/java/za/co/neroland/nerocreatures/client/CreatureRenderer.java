package za.co.neroland.nerocreatures.client;

import com.mojang.blaze3d.vertex.PoseStack;

import org.jetbrains.annotations.Nullable;

//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
*///?}
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;

/**
 * The one renderer every NeroCreature uses. Creatures differ in their model and their
 * {@link CreatureArt} (texture set, render type, scale, shadow).
 *
 * <p>Visual state comes only from the synced {@code CreatureVisuals} (server-authoritative):
 * <ul>
 *   <li><b>Glow</b> — when the creature has a glow texture, {@link CreatureGlowLayer} draws it
 *       emissive at the clip-driven intensity, tinted white-gold for Radiant and crimson (pulsing)
 *       for alphas.</li>
 *   <li><b>Radiant</b> — swaps to {@code <id>_radiant.png} when one exists. Cosmetic only.</li>
 *   <li><b>Alpha</b> — size comes from the {@code minecraft:scale} attribute the alpha system applies
 *       (vanilla scales the model and hitbox), so {@link #ALPHA_MODEL_SCALE} is 1; the base texture
 *       multiplied to {@link #ALPHA_BODY_TINT} (1.21.4+; 1.21.1 has no model-tint hook).</li>
 * </ul>
 */
//? if >=1.21.2 {
public class CreatureRenderer extends MobRenderer<Mob, CreatureRenderState, CreatureModel> {
//?} else {
/*// 1.21.1 renderers read the entity directly; there is no render-state object yet.
public class CreatureRenderer extends MobRenderer<Mob, CreatureModel> {
*///?}

    /**
     * Extra model-only scale for alphas: 1, because {@code entity/alpha/AlphaSystem} already applies
     * the {@code minecraft:scale} attribute ({@code scale_bonus} in {@code alpha/settings.json},
     * +0.3), which vanilla applies to the model and hitbox. Anything above 1 here would double-scale.
     */
    public static final float ALPHA_MODEL_SCALE = 1.0F;
    /** Alphas' base texture is multiplied to 85% brightness. */
    public static final int ALPHA_BODY_TINT = 0xFFD9D9D9;

    private final CreatureArt art;
    private final Identifier texture;
    private final Identifier radiantTexture;

    @SuppressWarnings("this-escape") // idiomatic Minecraft constructor wiring
    public CreatureRenderer(EntityRendererProvider.Context context, CreatureModel model, CreatureArt art) {
        super(context, model, art.shadow());
        this.art = art;
        this.texture = art.texture();
        this.radiantTexture = art.radiant() ? art.radiantTexture() : art.texture();
        if (art.glow()) {
            this.addLayer(new CreatureGlowLayer(this, art.glowTexture()));
        }
    }

    public CreatureArt art() {
        return this.art;
    }

    private static RenderType translucentType(Identifier texture) {
        //? if >=1.21.11 {
        return RenderTypes.entityTranslucent(texture);
        //?} else {
        /*return RenderType.entityTranslucent(texture);
        *///?}
    }

    private float scaleFor(boolean alpha) {
        return this.art.scale() * (alpha ? ALPHA_MODEL_SCALE : 1.0F);
    }

    //? if >=1.21.2 {
    @Override
    public CreatureRenderState createRenderState() {
        return new CreatureRenderState();
    }

    @Override
    public void extractRenderState(Mob entity, CreatureRenderState state, float partialTick) {
        super.extractRenderState(entity, state, partialTick);
        AnimInput in = state.anim.reset();
        in.age = state.ageInTicks;
        in.walkPos = state.walkAnimationPos;
        in.walkSpeed = state.walkAnimationSpeed;
        in.headYaw = state.yRot;
        in.headPitch = state.xRot;
        CreatureModel.fillVisuals(in, entity, partialTick);
        CreatureRig rig = this.getModel().rig();
        state.glow = rig == null ? (float) CreatureRig.GLOW_DIM : (float) rig.evaluate(in, null, null);
    }

    @Override
    protected void scale(CreatureRenderState state, PoseStack poseStack) {
        float s = scaleFor(state.anim.flag(AnimInput.FLAG_ALPHA));
        poseStack.scale(s, s, s);
    }

    @Override
    public Identifier getTextureLocation(CreatureRenderState state) {
        return state.anim.flag(AnimInput.FLAG_RADIANT) ? this.radiantTexture : this.texture;
    }

    @Override
    protected int getModelTint(CreatureRenderState state) {
        return state.anim.flag(AnimInput.FLAG_ALPHA) ? ALPHA_BODY_TINT : super.getModelTint(state);
    }

    @Override
    @Nullable
    protected RenderType getRenderType(CreatureRenderState state, boolean bodyVisible, boolean translucent,
            boolean glowing) {
        if (this.art.translucent() && bodyVisible) {
            return translucentType(this.getTextureLocation(state));
        }
        return super.getRenderType(state, bodyVisible, translucent, glowing);
    }
    //?} else {
    /*@Override
    protected void scale(Mob entity, PoseStack poseStack, float partialTick) {
        AnimInput in = new AnimInput();
        CreatureModel.fillVisuals(in, entity, partialTick);
        float s = scaleFor(in.flag(AnimInput.FLAG_ALPHA));
        poseStack.scale(s, s, s);
    }

    @Override
    public Identifier getTextureLocation(Mob entity) {
        AnimInput in = new AnimInput();
        CreatureModel.fillVisuals(in, entity, 0.0F);
        return in.flag(AnimInput.FLAG_RADIANT) ? this.radiantTexture : this.texture;
    }

    @Override
    @Nullable
    protected RenderType getRenderType(Mob entity, boolean bodyVisible, boolean translucent, boolean glowing) {
        if (this.art.translucent() && bodyVisible) {
            return translucentType(this.getTextureLocation(entity));
        }
        return super.getRenderType(entity, bodyVisible, translucent, glowing);
    }
    *///?}
}
