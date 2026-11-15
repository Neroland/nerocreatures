package za.co.neroland.nerocreatures.client;

import com.mojang.blaze3d.vertex.PoseStack;

import org.jetbrains.annotations.Nullable;

//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
*///?}
//? if <1.21.2 {
/*import net.minecraft.client.renderer.MultiBufferSource;
*///?}
//? if <1.21.2 {
/*import net.minecraft.client.renderer.culling.Frustum;
*///?}
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;

/**
 * The one renderer every NeroCreature uses. Creatures differ in their model and their
 * {@link CreatureArt} (texture set, render type, scale, shadow, variant look, culling box).
 *
 * <p>Visual state comes only from the synced {@code CreatureVisuals} (server-authoritative):
 * <ul>
 *   <li><b>Glow</b> — when the creature has a glow texture, {@link CreatureGlowLayer} draws it
 *       emissive at the clip-driven intensity (after the spec's glow rules), tinted by the variant's
 *       glow colour, white-gold for Radiant and crimson (pulsing) for alphas.</li>
 *   <li><b>Variant</b> — the spec's {@code variants}: a base-texture tint and/or its own
 *       {@code <id>_v<n>.png}.</li>
 *   <li><b>Radiant</b> — swaps to {@code <id>_radiant.png} when one exists. Cosmetic only.</li>
 *   <li><b>Alpha</b> — size comes from the {@code minecraft:scale} attribute the alpha system applies
 *       (vanilla scales the model and hitbox), so {@link #ALPHA_MODEL_SCALE} is 1; the base texture
 *       multiplied to {@link #ALPHA_BODY_TINT}.</li>
 * </ul>
 *
 * <p><b>Culling:</b> a creature is drawn when vanilla's hitbox test passes or when its
 * {@link CreatureArt.Cull} box (the reach of the model, measured by the generator) is within view
 * distance and in the frustum. Never a blanket {@code true}.
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
    private final Identifier[] variantTextures = new Identifier[8];

    @SuppressWarnings("this-escape") // idiomatic Minecraft constructor wiring
    public CreatureRenderer(EntityRendererProvider.Context context, CreatureModel model, CreatureArt art) {
        super(context, model, art.shadow());
        this.art = art;
        this.texture = art.texture();
        this.radiantTexture = art.radiant() ? art.radiantTexture() : art.texture();
        for (int v = 0; v < this.variantTextures.length; v++) {
            this.variantTextures[v] = art.variants().texture(v) ? art.variantTexture(v) : this.texture;
        }
        if (art.glow()) {
            this.addLayer(new CreatureGlowLayer(this, art));
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

    /** Radiant texture, else the variant's own texture, else the base texture. */
    private Identifier textureFor(AnimInput in) {
        return in.flag(AnimInput.FLAG_RADIANT) ? this.radiantTexture : this.variantTextures[in.variant & 7];
    }

    /** ARGB multiplied into the base texture: the variant's tint, darkened for alphas; -1 for none. */
    int bodyTint(AnimInput in) {
        int tint = this.art.variants().bodyTint(in.variant);
        if (in.flag(AnimInput.FLAG_ALPHA)) {
            tint = tint == -1 ? ALPHA_BODY_TINT : CreatureArt.multiplyArgb(tint, ALPHA_BODY_TINT);
        }
        return tint;
    }

    /** The model's reach around the entity's feet ({@link CreatureArt.Cull}), times its scale. */
    private AABB reach(Mob entity) {
        CreatureArt.Cull cull = this.art.cull();
        double s = entity.getScale();
        double r = cull.radius() * s;
        return new AABB(entity.getX() - r, entity.getY() - cull.down() * s, entity.getZ() - r,
                entity.getX() + r, entity.getY() + cull.up() * s, entity.getZ() + r);
    }

    // Vanilla culls with this box (frustum test after its own distance check): the hitbox grown to
    // the model's reach. Never "always visible".
    //? if >=26.3 {
    @Override
    protected AABB getBoundingBoxForCulling(Mob entity, float partialTick) {
        AABB box = super.getBoundingBoxForCulling(entity, partialTick);
        return this.art.cull().active() ? box.minmax(reach(entity)) : box;
    }
    //?} else if >=1.21.2 {
    /*@Override
    protected AABB getBoundingBoxForCulling(Mob entity) {
        AABB box = super.getBoundingBoxForCulling(entity);
        return this.art.cull().active() ? box.minmax(reach(entity)) : box;
    }
    *///?} else {
    /*@Override
    public boolean shouldRender(Mob entity, Frustum frustum, double camX, double camY, double camZ) {
        if (super.shouldRender(entity, frustum, camX, camY, camZ)) {
            return true;
        }
        // The model's reach box, still within view distance and in the frustum; never a blanket true.
        return this.art.cull().active() && entity.shouldRender(camX, camY, camZ) && frustum.isVisible(reach(entity));
    }
    *///?}

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
        return textureFor(state.anim);
    }

    @Override
    protected int getModelTint(CreatureRenderState state) {
        int base = super.getModelTint(state);
        int tint = bodyTint(state.anim);
        return tint == -1 ? base : CreatureArt.multiplyArgb(base, tint);
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
    public void render(Mob entity, float entityYaw, float partialTick, PoseStack poseStack,
            MultiBufferSource buffers, int light) {
        AnimInput in = new AnimInput();
        CreatureModel.fillVisuals(in, entity, partialTick);
        this.getModel().setPendingBodyTint(bodyTint(in));
        super.render(entity, entityYaw, partialTick, poseStack, buffers, light);
        this.getModel().setPendingBodyTint(-1);
    }

    @Override
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
        return textureFor(in);
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
