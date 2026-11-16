package za.co.neroland.nerocreatures.client.bestiary;

import java.util.HashMap;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;

import za.co.neroland.nerolandcore.progression.ClientGates;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.bestiary.BestiaryCatalog;
import za.co.neroland.nerocreatures.bestiary.BestiaryClientHooks;
import za.co.neroland.nerocreatures.bestiary.BestiaryProgress;
import za.co.neroland.nerocreatures.bestiary.BestiaryRules.Tier;
import za.co.neroland.nerocreatures.network.BestiaryCountsPayload;
import za.co.neroland.nerocreatures.network.BestiaryEventPayload;
import za.co.neroland.nerocreatures.network.BestiaryRequestPayload;
import za.co.neroland.nerocreatures.network.CreatureNetwork;
import za.co.neroland.nerocreatures.registry.ModItems;
import za.co.neroland.nerocreatures.registry.ModSounds;

/**
 * The client half of the Bestiary: the asking player's own counts (as the server last sent them),
 * tier lookups over Core's gate cache, scan feedback, the open-Bestiary key and the screen's session
 * memory (open entry, page, scroll). Nothing here is written to disk, and {@link #clear()} drops it
 * all when the player leaves a world.
 *
 * <p>Client-only: referenced only from the loaders' client entry points and client classes.
 */
public final class ClientBestiary {

    public static final Identifier CATEGORY_ID = Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "nerocreatures");

    //? if >=1.21.9 {
    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(CATEGORY_ID);
    //?} else {
    /*public static final String CATEGORY = "key.category." + NeroCreaturesCommon.MOD_ID + ".nerocreatures";
    *///?}

    /** Opens the Bestiary when a Field Scanner is in the inventory. Unbound by default. */
    public static final KeyMapping OPEN_BESTIARY = new KeyMapping("key.nerocreatures.open_bestiary",
            InputConstants.UNKNOWN.getValue(), CATEGORY);

    private static final Map<String, BestiaryCountsPayload.Entry> COUNTS = new HashMap<>();
    private static boolean countsReceived;
    private static int countsVersion;

    // Session memory for the screen; never saved.
    @Nullable
    static String lastOpen;
    static int lastPage;
    static int listScroll;
    static int textScroll;
    static int traitsScroll;

    /** Ticks the HUD shows "Scan lost" after a LOST event. */
    static int lostTicks;

    private ClientBestiary() {
    }

    /** Installs the common-to-client seams. Called once from each loader's client setup. */
    public static void install() {
        BestiaryClientHooks.openScreen = ClientBestiary::openScreen;
        BestiaryClientHooks.counts = ClientBestiary::onCounts;
        BestiaryClientHooks.event = ClientBestiary::onEvent;
        BestiaryClientHooks.clear = ClientBestiary::clear;
    }

    /** Client tick: the key, and the HUD's scan prediction. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        while (OPEN_BESTIARY.consumeClick()) {
            if (mc.player == null) {
                continue;
            }
            if (hasScanner(mc.player)) {
                openScreen();
            } else {
                actionBar(Component.translatable("message.nerocreatures.bestiary.need_scanner"));
            }
        }
        ScannerHud.tick(mc);
        if (lostTicks > 0) {
            lostTicks--;
        }
    }

    static boolean hasScanner(Player player) {
        return player.getInventory().contains(stack -> stack.is(ModItems.FIELD_SCANNER.get()));
    }

    public static void openScreen() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        //? if >=1.21.9 {
        mc.setScreenAndShow(new BestiaryScreen());
        //?} else {
        /*mc.setScreen(new BestiaryScreen());
        *///?}
    }

    /** Asks the server for this player's counts. */
    static void request() {
        CreatureNetwork.sendToServer(new BestiaryRequestPayload(BestiaryRequestPayload.SCHEMA));
    }

    private static void onCounts(BestiaryCountsPayload payload) {
        if (payload.schema() != BestiaryCountsPayload.SCHEMA) {
            return;
        }
        COUNTS.clear();
        for (BestiaryCountsPayload.Entry entry : payload.entries()) {
            if (BestiaryCatalog.get(entry.species()).isPresent()) {
                COUNTS.put(entry.species(), entry);
            }
        }
        countsReceived = true;
        countsVersion++;
    }

    private static void onEvent(BestiaryEventPayload payload) {
        if (payload.schema() != BestiaryEventPayload.SCHEMA) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) {
            return;
        }
        Component name = speciesName(payload.species());
        switch (payload.kind()) {
            case SCANNED -> {
                player.playSound(ModSounds.FIELD_SCANNER_SCAN.get(), 0.8F, 1.0F);
                actionBar(Component.translatable("message.nerocreatures.bestiary.new_entry", name));
                stopScanning(mc);
            }
            case STUDIED -> {
                player.playSound(ModSounds.FIELD_SCANNER_SCAN.get(), 0.9F, 1.4F);
                actionBar(Component.translatable("message.nerocreatures.bestiary.studied", name));
            }
            case NOTHING_NEW -> {
                player.playSound(ModSounds.FIELD_SCANNER_SCAN.get(), 0.4F, 1.0F);
                actionBar(Component.translatable("message.nerocreatures.bestiary.nothing_new", name));
                stopScanning(mc);
            }
            case LOST -> lostTicks = 30;
            default -> {
            }
        }
        if (BestiaryGfx.screen(mc) instanceof BestiaryScreen screen) {
            screen.onEvent();
        }
    }

    private static void stopScanning(Minecraft mc) {
        if (mc.player != null && mc.gameMode != null && mc.player.isUsingItem()
                && mc.player.getUseItem().is(ModItems.FIELD_SCANNER.get())) {
            mc.gameMode.releaseUsingItem(mc.player);
        }
        ScannerHud.reset();
    }

    static void actionBar(Component message) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        //? if >=26.1 {
        player.sendOverlayMessage(message);
        //?} else {
        /*player.displayClientMessage(message, true);
        *///?}
    }

    static Component speciesName(String species) {
        return Component.translatable("entity.nerocreatures." + species);
    }

    /** The tier: the counts payload wins when it has the species, else Core's gate cache. */
    public static Tier tier(String species) {
        BestiaryCountsPayload.Entry entry = COUNTS.get(species);
        if (entry != null) {
            return entry.tier() >= 2 ? Tier.STUDIED : Tier.SCANNED;
        }
        if (ClientGates.isOpen(BestiaryProgress.studiedGate(species))) {
            return Tier.STUDIED;
        }
        return ClientGates.isOpen(BestiaryProgress.scannedGate(species)) ? Tier.SCANNED : Tier.NONE;
    }

    @Nullable
    static BestiaryCountsPayload.Entry counts(String species) {
        return COUNTS.get(species);
    }

    static boolean countsReceived() {
        return countsReceived;
    }

    static int countsVersion() {
        return countsVersion;
    }

    /** Drops everything on leaving a world. */
    public static void clear() {
        COUNTS.clear();
        countsReceived = false;
        lastOpen = null;
        lastPage = 0;
        listScroll = 0;
        textScroll = 0;
        traitsScroll = 0;
        lostTicks = 0;
        ScannerHud.reset();
    }

}
