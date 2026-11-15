package za.co.neroland.nerocreatures.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

//? if >=1.21.10 {
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
//?} else {
/*import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
*///?}
//? if >=26.1 {
import net.minecraft.client.renderer.state.level.CameraRenderState;
//?} else if >=1.21.10 {
/*import net.minecraft.client.renderer.state.CameraRenderState;
*///?}

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.block.ApexAltarBlock;
import za.co.neroland.nerocreatures.block.ApexAltarBlockEntity;
import za.co.neroland.nerocreatures.item.SigilItem;

/**
 * Draws the summoning sigil turning slowly above a locked Apex Altar (fight live or cooling down).
 *
 * <p>Culled: {@link #shouldRender} draws only a locked altar within {@value #VIEW_DISTANCE} blocks of
 * the camera; vanilla's section visibility does the frustum part. It never returns a blanket
 * {@code true}. Everything it shows comes from the block state ({@link ApexAltarBlock#SIGIL}), which
 * vanilla already syncs, so the block entity sends no update packets at all.
 */
//? if >=1.21.10 {
public class ApexAltarRenderer implements BlockEntityRenderer<ApexAltarBlockEntity, ApexAltarRenderer.State> {
//?} else {
/*public class ApexAltarRenderer implements BlockEntityRenderer<ApexAltarBlockEntity> {
*///?}

    private static final int VIEW_DISTANCE = 48;

    //? if >=1.21.10 {
    /** What one frame needs. */
    public static final class State extends BlockEntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        float spin;
    }

    private final ItemModelResolver items;

    public ApexAltarRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(ApexAltarBlockEntity altar, State state, float partialTick, Vec3 camera,
            @Nullable ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(altar, state, partialTick, camera, crumbling);
        state.item.clear();
        ItemStack stack = sigilFor(altar.getBlockState());
        if (!stack.isEmpty()) {
            this.items.updateForTopItem(state.item, stack, ItemDisplayContext.FIXED, altar.getLevel(), null, 0);
        }
        state.spin = spin(altar.getLevel(), partialTick);
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.item.isEmpty()) {
            return;
        }
        pose.pushPose();
        place(pose, state.spin);
        state.item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
    //?} else if >=1.21.5 {
    /*private final ItemRenderer items;

    public ApexAltarRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(ApexAltarBlockEntity altar, float partialTick, PoseStack pose, MultiBufferSource buffers,
            int light, int overlay, Vec3 camera) {
        ItemStack stack = sigilFor(altar.getBlockState());
        if (stack.isEmpty()) {
            return;
        }
        pose.pushPose();
        place(pose, spin(altar.getLevel(), partialTick));
        this.items.renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                altar.getLevel(), 0);
        pose.popPose();
    }
    *///?} else {
    /*private final ItemRenderer items;

    public ApexAltarRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(ApexAltarBlockEntity altar, float partialTick, PoseStack pose, MultiBufferSource buffers,
            int light, int overlay) {
        ItemStack stack = sigilFor(altar.getBlockState());
        if (stack.isEmpty()) {
            return;
        }
        pose.pushPose();
        place(pose, spin(altar.getLevel(), partialTick));
        this.items.renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                altar.getLevel(), 0);
        pose.popPose();
    }
    *///?}

    /** Only a locked altar near the camera; never a blanket true. */
    @Override
    public boolean shouldRender(ApexAltarBlockEntity altar, Vec3 camera) {
        BlockState state = altar.getBlockState();
        return state.hasProperty(ApexAltarBlock.SIGIL)
                && state.getValue(ApexAltarBlock.SIGIL) != ApexAltarBlock.Sigil.NONE
                && Vec3.atCenterOf(altar.getBlockPos()).closerThan(camera, VIEW_DISTANCE);
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    private static void place(PoseStack pose, float spin) {
        pose.translate(0.5D, 1.25D, 0.5D);
        //? if >=26.3 {
        pose.rotate(Axis.YP.rotationDegrees(spin));
        //?} else {
        /*pose.mulPose(Axis.YP.rotationDegrees(spin));
        *///?}
        pose.scale(0.6F, 0.6F, 0.6F);
    }

    private static float spin(@Nullable Level level, float partialTick) {
        long time = level == null ? 0L : level.getGameTime();
        return ((time % 360L) + partialTick) * 2.0F % 360.0F;
    }

    /** The sigil item for the altar's glow, or empty when idle (or before the item is registered). */
    private static ItemStack sigilFor(BlockState state) {
        if (!state.hasProperty(ApexAltarBlock.SIGIL)) {
            return ItemStack.EMPTY;
        }
        SigilItem.Kind kind = SigilItem.Kind.forGlow(state.getValue(ApexAltarBlock.SIGIL));
        if (kind == null) {
            return ItemStack.EMPTY;
        }
        Identifier id = Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, kind.itemPath());
        if (!BuiltInRegistries.ITEM.containsKey(id)) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.getValue(id));
        return stack.is(Items.AIR) ? ItemStack.EMPTY : stack;
    }
}
