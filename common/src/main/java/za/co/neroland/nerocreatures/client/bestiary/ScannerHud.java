package za.co.neroland.nerocreatures.client.bestiary;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.bestiary.BestiaryRules;
import za.co.neroland.nerocreatures.bestiary.BestiaryRules.ScanProgress;
import za.co.neroland.nerocreatures.bestiary.BestiaryRules.Tier;
import za.co.neroland.nerocreatures.bestiary.ScanTargets;
import za.co.neroland.nerocreatures.registry.ModItems;
import za.co.neroland.nerocreatures.registry.ModSounds;

/**
 * The Field Scanner's HUD indicator (decision 010 section 2): a reticle, up to two lines of text
 * and the scan bar, laid out in GUI pixels around the crosshair so it is the same at every GUI
 * scale. The client runs the same ray and tick rule as the server only to draw the bar; it never
 * decides a result.
 */
public final class ScannerHud {

    /** The HUD layer id each loader registers the indicator under. */
    public static final Identifier LAYER_ID = Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "scanner_hud");

    static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID,
            "textures/gui/scanner_hud.png");

    private static final int TEX = 64;
    private static final int HINT_TICKS = 100;

    private static final ScanProgress PROGRESS = new ScanProgress();
    private static ScanTargets.Target target = new ScanTargets.Target(ScanTargets.Kind.NONE, null, null);
    private static boolean using;
    private static int heldTicks = -1;

    private ScannerHud() {
    }

    static void reset() {
        PROGRESS.reset();
        using = false;
    }

    /** Client tick: track how long the scanner has been held and predict the scan bar. */
    static void tick(Minecraft mc) {
        Player player = mc.player;
        if (player == null || mc.level == null) {
            heldTicks = -1;
            reset();
            return;
        }
        boolean held = holding(player);
        heldTicks = held ? heldTicks + 1 : -1;
        if (!held) {
            reset();
            target = new ScanTargets.Target(ScanTargets.Kind.NONE, null, null);
            return;
        }
        target = ScanTargets.find(player);
        boolean nowUsing = player.isUsingItem() && player.getUseItem().is(ModItems.FIELD_SCANNER.get());
        if (!nowUsing) {
            reset();
            return;
        }
        using = true;
        int before = PROGRESS.ticks();
        ScanProgress.Step step = PROGRESS.tick(mc.level.getGameTime(), target.entityId());
        if (step == ScanProgress.Step.PROGRESS && PROGRESS.ticks() != before && PROGRESS.ticks() % 8 == 0) {
            float pitch = 0.8F + 0.6F * PROGRESS.ticks() / (float) BestiaryRules.SCAN_TICKS;
            player.playSound(ModSounds.FIELD_SCANNER_SCAN.get(), 0.25F, pitch);
        }
    }

    private static boolean holding(Player player) {
        return player.getMainHandItem().is(ModItems.FIELD_SCANNER.get())
                || player.getOffhandItem().is(ModItems.FIELD_SCANNER.get());
    }

    /** Draws the indicator. Called from each loader's HUD layer. */
    public static void render(GuiGraphicsExtractor graphics) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || BestiaryGfx.screen(mc) != null || BestiaryGfx.hudHidden(mc) || player.isSpectator()
                || !holding(player)) {
            return;
        }
        Font font = mc.font;
        int cx = graphics.guiWidth() / 2;
        int cy = graphics.guiHeight() / 2;
        Component line1 = null;
        int colour1 = 0xFFE6F2FF;
        Component line2 = null;
        int colour2 = 0xFF93A4BF;
        int reticleU = 0;
        int reticleV = 0;
        boolean bar = false;
        if (ClientBestiary.lostTicks > 0) {
            reticleV = 25;
            line1 = Component.translatable("hud.nerocreatures.scanner.lost");
            colour1 = 0xFFFF5A5A;
            line2 = Component.translatable("hud.nerocreatures.scanner.lost_hint");
        } else if (target.kind() == ScanTargets.Kind.GALLERY) {
            reticleV = 25;
            line1 = Component.translatable("hud.nerocreatures.scanner.gallery");
            colour1 = 0xFFFF5A5A;
            line2 = Component.translatable("hud.nerocreatures.scanner.gallery_hint");
        } else if (target.kind() == ScanTargets.Kind.NOT_LISTED && using) {
            reticleV = 25;
            line1 = Component.translatable("hud.nerocreatures.scanner.not_listed");
            colour1 = 0xFFFF5A5A;
        } else if (target.kind() == ScanTargets.Kind.VALID && target.entry() != null) {
            reticleU = 25;
            Tier tier = ClientBestiary.tier(target.entry().id());
            bar = using;
            if (tier == Tier.NONE) {
                line1 = Component.translatable("gui.nerocreatures.bestiary.unknown");
            } else {
                line1 = ClientBestiary.speciesName(target.entry().id());
                colour1 = tier == Tier.STUDIED ? 0xFFFFB74D : 0xFF52D9F2;
                line2 = Component.translatable("gui.nerocreatures.bestiary.tier." + tier.key());
            }
            if (using) {
                line2 = Component.translatable("hud.nerocreatures.scanner.scanning",
                        PROGRESS.ticks() * 100 / BestiaryRules.SCAN_TICKS);
                colour2 = 0xFF93A4BF;
            }
        } else if (using) {
            line2 = Component.translatable("hud.nerocreatures.scanner.no_target");
        } else if (heldTicks >= 0 && heldTicks < HINT_TICKS) {
            line2 = Component.translatable("hud.nerocreatures.scanner.hint");
            colour2 = 0xFFC9D6EA;
        }
        BestiaryGfx.blit(graphics, TEXTURE, cx - 12, cy - 12, reticleU, reticleV, 25, 25, TEX, TEX);
        if (line1 != null) {
            graphics.text(font, line1, cx - font.width(line1) / 2, cy + 16, colour1, true);
        }
        if (bar) {
            BestiaryGfx.blit(graphics, TEXTURE, cx - 26, cy + 27, 0, 50, 52, 7, TEX, TEX);
            int fill = 50 * Math.min(PROGRESS.ticks(), BestiaryRules.SCAN_TICKS) / BestiaryRules.SCAN_TICKS;
            if (fill > 0) {
                BestiaryGfx.blit(graphics, TEXTURE, cx - 25, cy + 28, 0, 57, fill, 5, TEX, TEX);
            }
        }
        if (line2 != null) {
            int y = bar ? cy + 38 : (line1 != null ? cy + 27 : cy + 16);
            graphics.text(font, line2, cx - font.width(line2) / 2, y, colour2, true);
        }
    }

    /** Whether a stack is a Field Scanner (used by loaders' HUD conditions). */
    public static boolean isScanner(ItemStack stack) {
        return stack.is(ModItems.FIELD_SCANNER.get());
    }
}
