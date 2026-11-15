package za.co.neroland.nerocreatures.client;

import com.mojang.blaze3d.vertex.PoseStack;

//? if >=1.21.11 {
import net.minecraft.client.renderer.rendertype.RenderTypes;
//?} else {
/*import net.minecraft.client.renderer.RenderType;
*///?}
//? if >=1.21.9 {
import net.minecraft.client.renderer.SubmitNodeCollector;
//?} else {
/*import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
*///?}
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
//? if <1.21.2 {
/*import net.minecraft.world.entity.Mob;
*///?}

/**
 * The emissive glow layer: draws {@code <id>_glow.png} full-bright with the eyes render type
 * (additive), its colour multiplied by the clip-driven intensity (0.35 dim, 1.0 full) and a tint:
 * white normally, white-gold {@code #FFF6D6} for Radiant, crimson {@code #FF2E4D} pulsing at about
 * 1 Hz for alphas. Added only for creatures that have a glow texture.
 */
//? if >=1.21.2 {
public class CreatureGlowLayer extends RenderLayer<CreatureRenderState, CreatureModel> {
//?} else {
/*public class CreatureGlowLayer extends RenderLayer<Mob, CreatureModel> {
*///?}

    public static final int TINT_NORMAL = 0xFFFFFF;
    public static final int TINT_RADIANT = 0xFFF6D6;
    public static final int TINT_ALPHA = 0xFF2E4D;
    private static final int FULL_BRIGHT = 0xF000F0;

    private final Identifier texture;

    //? if >=1.21.2 {
    public CreatureGlowLayer(RenderLayerParent<CreatureRenderState, CreatureModel> parent, Identifier texture) {
    //?} else {
    /*private final AnimInput input = new AnimInput();

    public CreatureGlowLayer(RenderLayerParent<Mob, CreatureModel> parent, Identifier texture) {
    *///?}
        super(parent);
        this.texture = texture;
    }

    /** Opaque ARGB for the layer: tint times intensity (the eyes blend is additive). */
    static int color(double intensity, boolean radiant, boolean alpha, double age) {
        int tint = alpha ? TINT_ALPHA : radiant ? TINT_RADIANT : TINT_NORMAL;
        double k = intensity;
        if (alpha) {
            k *= 0.6 + 0.4 * (0.5 + 0.5 * Math.sin(age * Math.PI / 10.0));
        }
        k = Math.max(0.0, Math.min(1.0, k));
        int r = (int) Math.round(((tint >> 16) & 0xFF) * k);
        int g = (int) Math.round(((tint >> 8) & 0xFF) * k);
        int b = (int) Math.round((tint & 0xFF) * k);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int color(CreatureRenderState state) {
        AnimInput in = state.anim;
        return color(state.glow, in.flag(AnimInput.FLAG_RADIANT), in.flag(AnimInput.FLAG_ALPHA), in.age);
    }

    //? if >=26.3 {
    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, CreatureRenderState state,
            float yRot, float xRot) {
        if (state.isInvisible) {
            return;
        }
        collector.submitModel(this.getParentModel(), state, poseStack, RenderTypes.eyes(this.texture), FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, color(state));
    }
    //?} else if >=1.21.11 {
    /*@Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, CreatureRenderState state,
            float yRot, float xRot) {
        if (state.isInvisible) {
            return;
        }
        collector.submitModel(this.getParentModel(), state, poseStack, RenderTypes.eyes(this.texture), FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, color(state), null);
    }
    *///?} else if >=1.21.9 {
    /*@Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, CreatureRenderState state,
            float yRot, float xRot) {
        if (state.isInvisible) {
            return;
        }
        collector.submitModel(this.getParentModel(), state, poseStack, RenderType.eyes(this.texture), FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY, color(state), null);
    }
    *///?} else if >=1.21.2 {
    /*@Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, CreatureRenderState state,
            float yRot, float xRot) {
        if (state.isInvisible) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(RenderType.eyes(this.texture));
        this.getParentModel().renderToBuffer(poseStack, consumer, FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                color(state));
    }
    *///?} else {
    /*@Override
    public void render(PoseStack poseStack, MultiBufferSource buffers, int light, Mob entity, float limbSwing,
            float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (entity.isInvisible()) {
            return;
        }
        AnimInput in = this.input.reset();
        in.age = ageInTicks;
        in.walkPos = limbSwing;
        in.walkSpeed = limbSwingAmount;
        CreatureModel.fillVisuals(in, entity, partialTick);
        CreatureRig rig = this.getParentModel().rig();
        double glow = rig == null ? CreatureRig.GLOW_DIM : rig.evaluate(in, null, null);
        VertexConsumer consumer = buffers.getBuffer(RenderType.eyes(this.texture));
        this.getParentModel().renderToBuffer(poseStack, consumer, FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                color(glow, in.flag(AnimInput.FLAG_RADIANT), in.flag(AnimInput.FLAG_ALPHA), ageInTicks));
    }
    *///?}
}
