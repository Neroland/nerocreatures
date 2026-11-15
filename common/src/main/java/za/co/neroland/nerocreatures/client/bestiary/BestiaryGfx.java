package za.co.neroland.nerocreatures.client.bestiary;

import java.util.List;

import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * The Bestiary's drawing calls whose shape differs between Minecraft lines, in one place: textured
 * blits (plain before 1.21.2, a render-type function to 1.21.5, a render pipeline from 1.21.6),
 * deferred tooltips, the open screen (moved into {@code Gui} in 26.2) and the live model in the
 * viewport (four different entity-in-GUI paths across 1.21.1-26.3).
 */
public final class BestiaryGfx {

    private BestiaryGfx() {
    }

    /** The open screen, or {@code null}. */
    @Nullable
    public static Screen screen(Minecraft mc) {
        //? if >=26.2 {
        return mc.gui.screen();
        //?} else {
        /*return mc.screen;
        *///?}
    }

    /** F1: the HUD is hidden. From 26.2 the HUD layers already skip themselves when hidden. */
    public static boolean hudHidden(Minecraft mc) {
        //? if >=26.2 {
        return false;
        //?} else {
        /*return mc.options.hideGui;
        *///?}
    }

    /** A 1:1 blit of a region of a {@code texW x texH} texture. */
    public static void blit(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int u, int v, int w, int h,
            int texW, int texH) {
        //? if >=1.21.6 {
        graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, texW, texH);
        //?} else if >=1.21.2 {
        /*graphics.blit(net.minecraft.client.renderer.RenderType::guiTextured, texture, x, y, u, v, w, h, texW, texH);
        *///?} else {
        /*graphics.blit(texture, x, y, u, v, w, h, texW, texH);
        *///?}
    }

    /** Draws region {@code (u, v, rw, rh)} stretched to {@code w x h} (nearest sampling). */
    public static void blitScaled(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int w, int h, int u,
            int v, int rw, int rh, int texW, int texH) {
        if (w <= 0 || h <= 0) {
            return;
        }
        //? if >=1.21.6 {
        graphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, w, h, rw, rh,
                texW, texH);
        //?} else if >=1.21.2 {
        /*graphics.blit(net.minecraft.client.renderer.RenderType::guiTextured, texture, x, y, u, v, w, h, rw, rh, texW,
                texH);
        *///?} else {
        /*graphics.blit(texture, x, y, w, h, u, v, rw, rh, texW, texH);
        *///?}
    }

    /**
     * A nine-slice: corners 1:1, edges and centre stretched (the sheet's edges and centres are flat
     * colour, so stretching and tiling look the same).
     */
    public static void nineSlice(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int w, int h, int u,
            int v, int sw, int sh, int corner, int texW, int texH) {
        int c = Math.min(corner, Math.min(w / 2, h / 2));
        int iw = w - 2 * c;
        int ih = h - 2 * c;
        int siw = sw - 2 * corner;
        int sih = sh - 2 * corner;
        blitScaled(graphics, texture, x, y, c, c, u, v, c, c, texW, texH);
        blitScaled(graphics, texture, x + w - c, y, c, c, u + sw - c, v, c, c, texW, texH);
        blitScaled(graphics, texture, x, y + h - c, c, c, u, v + sh - c, c, c, texW, texH);
        blitScaled(graphics, texture, x + w - c, y + h - c, c, c, u + sw - c, v + sh - c, c, c, texW, texH);
        blitScaled(graphics, texture, x + c, y, iw, c, u + corner, v, siw, c, texW, texH);
        blitScaled(graphics, texture, x + c, y + h - c, iw, c, u + corner, v + sh - c, siw, c, texW, texH);
        blitScaled(graphics, texture, x, y + c, c, ih, u, v + corner, c, sih, texW, texH);
        blitScaled(graphics, texture, x + w - c, y + c, c, ih, u + sw - c, v + corner, c, sih, texW, texH);
        blitScaled(graphics, texture, x + c, y + c, iw, ih, u + corner, v + corner, siw, sih, texW, texH);
    }

    /** A one-pixel rectangle outline, drawn with fills (the same on every line). */
    public static void outline(GuiGraphicsExtractor graphics, int x, int y, int w, int h, int colour) {
        graphics.fill(x, y, x + w, y + 1, colour);
        graphics.fill(x, y + h - 1, x + w, y + h, colour);
        graphics.fill(x, y + 1, x + 1, y + h - 1, colour);
        graphics.fill(x + w - 1, y + 1, x + w, y + h - 1, colour);
    }

    /** Queues a tooltip of pre-wrapped lines, drawn after the screen. */
    public static void tooltip(Screen screen, GuiGraphicsExtractor graphics, Font font, List<FormattedCharSequence> lines,
            int mouseX, int mouseY) {
        //? if >=1.21.6 {
        graphics.setTooltipForNextFrame(font, lines, mouseX, mouseY);
        //?} else {
        /*screen.setTooltipForNextRenderPass(lines);
        *///?}
    }

    /** Queues an item's own tooltip. */
    public static void itemTooltip(Screen screen, GuiGraphicsExtractor graphics, Font font, ItemStack stack, int mouseX,
            int mouseY) {
        //? if >=1.21.6 {
        graphics.setTooltipForNextFrame(font, stack, mouseX, mouseY);
        //?} else {
        /*screen.setTooltipForNextRenderPass(Screen.getTooltipFromItem(Minecraft.getInstance(), stack).stream()
                .map(Component::getVisualOrderText).toList());
        *///?}
    }

    /**
     * Draws {@code entity} in the viewport rectangle {@code (x0, y0)-(x1, y1)}: body turned to
     * {@code yaw} degrees (0 faces the viewer), pitched {@code pitchDeg} down, {@code scale} GUI
     * pixels per block, feet at {@code feetY}. The entity is a client-only display instance that is
     * never added to a level. Scissored to the rectangle on every line.
     */
    public static void entity(GuiGraphicsExtractor graphics, LivingEntity entity, int x0, int y0, int x1, int y1,
            float yaw, float pitchDeg, float scale, int feetY) {
        float centreY = (y0 + y1) / 2.0F;
        Quaternionf pitch = new Quaternionf().rotateX(pitchDeg * ((float) Math.PI / 180.0F));
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).mul(pitch);
        Vector3f translation = new Vector3f(0.0F, (feetY - centreY) / scale, 0.0F);
        //? if >=26.1 {
        graphics.entity(renderState(entity, yaw), scale, translation, rotation, pitch, x0, y0, x1, y1);
        //?} else if >=1.21.11 {
        /*graphics.submitEntityRenderState(renderState(entity, yaw), scale, translation, rotation, pitch, x0, y0, x1, y1);
        *///?} else if >=1.21.6 {
        /*Turn saved = Turn.apply(entity, yaw);
        net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventory(graphics, x0, y0, x1, y1,
                scale, translation, rotation, pitch, entity);
        saved.restore(entity);
        *///?} else {
        /*Turn saved = Turn.apply(entity, yaw);
        graphics.enableScissor(x0, y0, x1, y1);
        net.minecraft.client.gui.screens.inventory.InventoryScreen.renderEntityInInventory(graphics, (x0 + x1) / 2.0F,
                centreY, scale, translation, rotation, pitch, entity);
        graphics.disableScissor();
        saved.restore(entity);
        *///?}
    }

    //? if >=1.21.11 {
    private static net.minecraft.client.renderer.entity.state.EntityRenderState renderState(LivingEntity entity,
            float yaw) {
        net.minecraft.client.renderer.entity.EntityRenderer<? super LivingEntity, ?> renderer = Minecraft.getInstance()
                .getEntityRenderDispatcher().getRenderer(entity);
        net.minecraft.client.renderer.entity.state.EntityRenderState state = renderer.createRenderState(entity, 1.0F);
        state.shadowPieces.clear();
        state.outlineColor = 0;
        if (state instanceof net.minecraft.client.renderer.entity.state.LivingEntityRenderState living) {
            living.bodyRot = 180.0F + yaw;
            living.yRot = 0.0F;
            living.xRot = 0.0F;
        }
        return state;
    }
    //?}

    /** Before 1.21.11 the renderer reads the entity's own rotations: set them, draw, put them back. */
    private record Turn(float bodyRot, float bodyRotO, float yRot, float xRot, float headRot, float headRotO) {
        static Turn apply(LivingEntity entity, float yaw) {
            Turn saved = new Turn(entity.yBodyRot, entity.yBodyRotO, entity.getYRot(), entity.getXRot(), entity.yHeadRot,
                    entity.yHeadRotO);
            entity.yBodyRot = 180.0F + yaw;
            entity.yBodyRotO = entity.yBodyRot;
            entity.setYRot(180.0F + yaw);
            entity.setXRot(0.0F);
            entity.yHeadRot = entity.getYRot();
            entity.yHeadRotO = entity.getYRot();
            return saved;
        }

        void restore(LivingEntity entity) {
            entity.yBodyRot = this.bodyRot;
            entity.yBodyRotO = this.bodyRotO;
            entity.setYRot(this.yRot);
            entity.setXRot(this.xRot);
            entity.yHeadRot = this.headRot;
            entity.yHeadRotO = this.headRotO;
        }
    }
}
