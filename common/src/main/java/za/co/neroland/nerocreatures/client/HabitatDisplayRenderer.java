package za.co.neroland.nerocreatures.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
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

import za.co.neroland.nerocreatures.block.HabitatDisplay;

/**
 * One block-entity renderer for every habitat block that shows an item: the Incubator's egg, the
 * Terrarium's creature (its spawn egg until the art pass gives it a model) and the Trophy Plaque's trophy.
 * Where and how big comes from the block entity's {@link HabitatDisplay}.
 *
 * <p>Culled: {@link #shouldRender} draws only a block that shows something and is within
 * {@value #VIEW_DISTANCE} blocks of the camera; vanilla's section visibility does the frustum part. It
 * never returns a blanket {@code true}.
 */
//? if >=1.21.10 {
public class HabitatDisplayRenderer<T extends BlockEntity & HabitatDisplay>
        implements BlockEntityRenderer<T, HabitatDisplayRenderer.State> {
//?} else {
/*public class HabitatDisplayRenderer<T extends BlockEntity & HabitatDisplay> implements BlockEntityRenderer<T> {
*///?}

    private static final int VIEW_DISTANCE = 32;

    //? if >=1.21.10 {
    /** What one frame needs. */
    public static final class State extends BlockEntityRenderState {
        final ItemStackRenderState item = new ItemStackRenderState();
        float yaw;
        float height;
        float scale;
        float depth;
    }

    private final ItemModelResolver items;

    public HabitatDisplayRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.itemModelResolver();
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(T display, State state, float partialTick, Vec3 camera,
            @Nullable ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderer.super.extractRenderState(display, state, partialTick, camera, crumbling);
        state.item.clear();
        ItemStack stack = display.displayStack();
        if (!stack.isEmpty()) {
            this.items.updateForTopItem(state.item, stack, ItemDisplayContext.FIXED, display.getLevel(), null, 0);
        }
        state.yaw = yaw(display, partialTick);
        state.height = display.displayHeight();
        state.scale = display.displayScale();
        state.depth = display.displayDepth();
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.item.isEmpty()) {
            return;
        }
        pose.pushPose();
        place(pose, state.yaw, state.height, state.scale, state.depth);
        state.item.submit(pose, collector, state.lightCoords, OverlayTexture.NO_OVERLAY, 0);
        pose.popPose();
    }
    //?} else if >=1.21.5 {
    /*private final ItemRenderer items;

    public HabitatDisplayRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(T display, float partialTick, PoseStack pose, MultiBufferSource buffers,
            int light, int overlay, Vec3 camera) {
        this.draw(display, partialTick, pose, buffers, light);
    }
    *///?} else {
    /*private final ItemRenderer items;

    public HabitatDisplayRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(T display, float partialTick, PoseStack pose, MultiBufferSource buffers,
            int light, int overlay) {
        this.draw(display, partialTick, pose, buffers, light);
    }
    *///?}

    //? if <1.21.10 {
    /*private void draw(T display, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        ItemStack stack = display.displayStack();
        if (stack.isEmpty()) {
            return;
        }
        pose.pushPose();
        place(pose, yaw(display, partialTick), display.displayHeight(), display.displayScale(),
                display.displayDepth());
        this.items.renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                display.getLevel(), 0);
        pose.popPose();
    }
    *///?}

    /** Only a block that shows something, near the camera; never a blanket true. */
    @Override
    public boolean shouldRender(T display, Vec3 camera) {
        return !display.displayStack().isEmpty()
                && Vec3.atCenterOf(display.getBlockPos()).closerThan(camera, VIEW_DISTANCE);
    }

    @Override
    public int getViewDistance() {
        return VIEW_DISTANCE;
    }

    private static void place(PoseStack pose, float yaw, float height, float scale, float depth) {
        pose.translate(0.5D, height, 0.5D);
        //? if >=26.3 {
        pose.rotate(Axis.YP.rotationDegrees(yaw));
        //?} else {
        /*pose.mulPose(Axis.YP.rotationDegrees(yaw));
        *///?}
        pose.translate(0.0D, 0.0D, depth);
        pose.scale(scale, scale, scale);
    }

    private static float yaw(HabitatDisplay display, float partialTick) {
        if (!display.displaySpins()) {
            return display.displayYaw();
        }
        Level level = display instanceof BlockEntity blockEntity ? blockEntity.getLevel() : null;
        long time = level == null ? 0L : level.getGameTime();
        return ((time % 360L) + partialTick) * 1.5F % 360.0F;
    }
}
