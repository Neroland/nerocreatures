package za.co.neroland.nerocreatures.client.bestiary;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.jetbrains.annotations.Nullable;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
//? if >=1.21.9 {
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
//?}
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.bestiary.BestiaryCatalog;
import za.co.neroland.nerocreatures.bestiary.BestiaryCatalog.Entry;
import za.co.neroland.nerocreatures.bestiary.BestiaryCatalog.Habitat;
import za.co.neroland.nerocreatures.bestiary.BestiaryCatalog.Kind;
import za.co.neroland.nerocreatures.bestiary.BestiaryRules;
import za.co.neroland.nerocreatures.bestiary.BestiaryRules.StudyKind;
import za.co.neroland.nerocreatures.bestiary.BestiaryRules.Tier;
import za.co.neroland.nerocreatures.client.bestiary.BestiaryLayout.Mode;
import za.co.neroland.nerocreatures.client.bestiary.BestiaryLayout.Rect;
import za.co.neroland.nerocreatures.entity.visual.CreatureVisualsHolder;
import za.co.neroland.nerocreatures.network.BestiaryCountsPayload;
import za.co.neroland.nerocreatures.trait.TraitDefinition;
import za.co.neroland.nerocreatures.trait.TraitPool;

/**
 * The Bestiary (decision 010 sections 3-7): a plain client screen with a creature list grouped by
 * habitat on the left and a detail page on the right (one pane in a small window), plus a trait
 * glossary page. Tiers come from Core's client gate cache at once, and the asking player's own
 * counts from one {@code bestiary_counts} payload when it arrives. Every control has a visible label
 * or a tooltip, and the tooltip also shows when the control has keyboard focus.
 *
 * <p>The viewport draws one client-only display instance of the open species, never added to a
 * level; any failure falls back to the portrait and is logged once with the species id only.
 */
public final class BestiaryScreen extends Screen {

    // --- palette (section 3) -------------------------------------------------------------------
    static final int PANEL = 0xFF0D1322;
    static final int PANEL_HI = 0xFF141D31;
    static final int PANEL_EDGE = 0xFF26344F;
    static final int EDGE_HI = 0xFF3B5072;
    static final int TROUGH = 0xFF090E19;
    static final int LINE_IDLE = 0xFF2F3B52;
    static final int TITLE = 0xFFE6F2FF;
    static final int BODY = 0xFFC9D6EA;
    static final int SUBTLE = 0xFF93A4BF;
    static final int CYAN = 0xFF52D9F2;
    static final int AMBER = 0xFFFFB74D;
    static final int GREEN = 0xFF5BD69A;
    static final int DANGER = 0xFFFF5A5A;
    static final int RADIANT = 0xFFFFF6D6;

    static final Identifier WIDGETS = tex("textures/gui/bestiary/widgets.png");
    static final Identifier PANEL_TEX = tex("textures/gui/bestiary/panel.png");
    private static final int W = 128;

    private static final String K = "gui.nerocreatures.bestiary.";

    /** Focusable areas, in Tab order (section 7). */
    enum Area {
        TABS, LIST, CONTROLS, TEXT, CLOSE
    }

    private BestiaryLayout layout;
    private int page;
    @Nullable
    private String selected;
    private boolean singleDetail;
    private Area focus = Area.LIST;
    private int focusIndex;
    private int listScroll;
    private int textScroll;
    private int traitsScroll;
    private int listContentHeight;
    private int textContentHeight;
    private int traitsContentHeight;

    private final Map<String, LivingEntity> displays = new HashMap<>();
    private final Set<String> failed = new HashSet<>();
    private float yaw;
    private boolean paused;
    private boolean radiantOn;
    private boolean draggingModel;
    private int dragScroll = -1;
    private double dragStartY;
    private int dragStartScroll;
    private int ticks;
    private boolean opened;
    private int versionAtOpen;
    private boolean requestedEvent;
    @Nullable
    private Object dimension;

    /** Tooltip candidates gathered while drawing; the hovered one wins, else the focused one. */
    private List<Component> hoverTip;
    private List<Component> focusTip;
    private int focusTipX;
    private int focusTipY;
    private ItemStack hoverItem = ItemStack.EMPTY;

    /** Click targets inside the text region, rebuilt every frame. */
    private final List<Hit> textHits = new ArrayList<>();

    private record Hit(Rect rect, Runnable action) {
    }

    public BestiaryScreen() {
        super(Component.translatable(K + "title"));
        this.page = ClientBestiary.lastPage;
        this.listScroll = ClientBestiary.listScroll;
        this.textScroll = ClientBestiary.textScroll;
        this.traitsScroll = ClientBestiary.traitsScroll;
        this.selected = ClientBestiary.lastOpen;
        if (this.selected == null) {
            for (Entry entry : BestiaryCatalog.entries()) {
                if (ClientBestiary.tier(entry.id()) != Tier.NONE) {
                    this.selected = entry.id();
                    break;
                }
            }
        }
    }

    private static Identifier tex(String path) {
        return Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, path);
    }

    @Override
    protected void init() {
        this.layout = BestiaryLayout.of(this.width, this.height);
        if (!this.opened) {
            this.opened = true;
            this.versionAtOpen = ClientBestiary.countsVersion();
            ClientBestiary.request();
            Minecraft mc = Minecraft.getInstance();
            this.dimension = mc.level == null ? null : mc.level.dimension();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void tick() {
        this.ticks++;
        if (!this.paused && !this.draggingModel) {
            this.yaw = (this.yaw + 0.9F) % 360.0F; // 18 degrees a second
        }
        for (LivingEntity display : this.displays.values()) {
            display.tickCount = this.ticks;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !mc.player.isAlive() || mc.level == null
                || this.dimension != null && !this.dimension.equals(mc.level.dimension())) {
            this.onClose();
        }
    }

    /** A bestiary_event arrived while open: ask for fresh counts. */
    void onEvent() {
        ClientBestiary.request();
        this.requestedEvent = true;
    }

    @Override
    public void removed() {
        ClientBestiary.lastOpen = this.selected;
        ClientBestiary.lastPage = this.page;
        ClientBestiary.listScroll = this.listScroll;
        ClientBestiary.textScroll = this.textScroll;
        ClientBestiary.traitsScroll = this.traitsScroll;
        this.displays.clear();
        super.removed();
    }

    private boolean loading() {
        return ClientBestiary.countsVersion() == this.versionAtOpen;
    }

    // ============================================================================================
    // rendering
    // ============================================================================================

    //? if >=26.1 {
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
    //?} else {
    /*@Override
    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
    *///?}
        this.hoverTip = null;
        this.focusTip = null;
        this.hoverItem = ItemStack.EMPTY;
        this.textHits.clear();
        BestiaryLayout l = this.layout;
        g.fillGradient(0, 0, this.width, this.height, 0xC0040812, 0xE0020409);
        Rect p = l.panel();
        g.fill(p.x() - 3, p.y() - 2, p.right() + 3, p.bottom() + 4, 0x66000000);
        BestiaryGfx.nineSlice(g, PANEL_TEX, p.x(), p.y(), p.w(), p.h(), 0, 0, 24, 24, 8, 24, 24);
        g.fill(p.x() + 4, p.y() + 26, p.right() - 4, p.y() + 27, PANEL_EDGE);
        g.fill(p.x() + 4, p.bottom() - 19, p.right() - 4, p.bottom() - 18, PANEL_EDGE);
        drawHeader(g, mouseX, mouseY);
        if (this.page == 1) {
            drawTraits(g, mouseX, mouseY);
        } else if (l.twoPane()) {
            drawList(g, mouseX, mouseY);
            drawDetail(g, mouseX, mouseY);
        } else if (this.singleDetail && this.selected != null) {
            drawDetail(g, mouseX, mouseY);
        } else {
            drawList(g, mouseX, mouseY);
        }
        drawFooter(g);
        if (!this.hoverItem.isEmpty()) {
            BestiaryGfx.itemTooltip(this, g, this.font, this.hoverItem, mouseX, mouseY);
        } else if (this.hoverTip != null) {
            BestiaryGfx.tooltip(this, g, this.font, wrap(this.hoverTip), mouseX, mouseY);
        } else if (this.focusTip != null) {
            BestiaryGfx.tooltip(this, g, this.font, wrap(this.focusTip), this.focusTipX, this.focusTipY);
        }
    }

    //? if >=26.1 {
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // The screen paints its own dim and panel; no vanilla blur behind it.
    }
    //?} else {
    /*@Override
    public void renderBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // The screen paints its own dim and panel; no vanilla blur behind it.
    }
    *///?}

    private List<FormattedCharSequence> wrap(List<Component> lines) {
        List<FormattedCharSequence> out = new ArrayList<>();
        for (Component line : lines) {
            out.addAll(this.font.split(line, 220));
        }
        return out;
    }

    /** Registers a control: hover tooltip, and the focus tooltip when it has keyboard focus. */
    private void control(Rect r, int mouseX, int mouseY, boolean focused, @Nullable List<Component> tip) {
        if (tip == null) {
            return;
        }
        if (r.contains(mouseX, mouseY)) {
            this.hoverTip = tip;
        }
        if (focused) {
            this.focusTip = tip;
            this.focusTipX = r.x();
            this.focusTipY = r.bottom() + 4;
        }
    }

    private static List<Component> tip(Component... lines) {
        return List.of(lines);
    }

    private void sprite(GuiGraphicsExtractor g, int x, int y, int u, int v, int w, int h) {
        BestiaryGfx.blit(g, WIDGETS, x, y, u, v, w, h, W, W);
    }

    private void focusOutline(GuiGraphicsExtractor g, Rect r) {
        BestiaryGfx.outline(g, r.x() - 1, r.y() - 1, r.w() + 2, r.h() + 2, 0xFFFFFFFF);
    }

    /** Trims to {@code width} with an ellipsis; a trimmed string gets its full text as a tooltip. */
    private String trimmed(Component text, int width, List<Component> fullTip, Rect at, int mouseX, int mouseY) {
        String s = text.getString();
        if (this.font.width(s) <= width) {
            return s;
        }
        if (at.contains(mouseX, mouseY) && this.hoverTip == null) {
            this.hoverTip = fullTip;
        }
        return this.font.plainSubstrByWidth(s, Math.max(0, width - this.font.width("…"))) + "…";
    }

    // --- header ---------------------------------------------------------------------------------

    private void drawHeader(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Rect p = this.layout.panel();
        Component title = Component.translatable(K + "title");
        g.text(this.font, title, p.x() + 10, p.y() + 12, TITLE, true);
        String[] tabs = {"creatures", "traits"};
        for (int i = 0; i < tabs.length; i++) {
            Component label = Component.translatable(K + "tab." + tabs[i]);
            Rect r = tabRect(i);
            boolean active = this.page == i;
            boolean hover = r.contains(mouseX, mouseY);
            int u = active ? 40 : hover ? 52 : 28;
            BestiaryGfx.nineSlice(g, WIDGETS, r.x(), r.y(), r.w(), r.h(), u, 56, 12, 14, 3, W, W);
            g.text(this.font, label, r.x() + 8, r.y() + 4, active ? CYAN : BODY, false);
            boolean focused = this.focus == Area.TABS && this.focusIndex == i;
            if (focused) {
                focusOutline(g, r);
            }
            control(r, mouseX, mouseY, focused, tip(Component.translatable(K + "tab." + tabs[i] + ".tip")));
        }
        Rect c = this.layout.close();
        sprite(g, c.x(), c.y(), c.contains(mouseX, mouseY) ? 14 : 0, 56, 14, 14);
        if (this.focus == Area.CLOSE) {
            focusOutline(g, c);
        }
        control(c, mouseX, mouseY, this.focus == Area.CLOSE, tip(Component.translatable(K + "close")));
        int total = BestiaryCatalog.entries().size();
        int scanned = 0;
        int studied = 0;
        for (Entry entry : BestiaryCatalog.entries()) {
            Tier tier = ClientBestiary.tier(entry.id());
            scanned += tier != Tier.NONE ? 1 : 0;
            studied += tier == Tier.STUDIED ? 1 : 0;
        }
        boolean wide = p.w() >= 440;
        boolean complete = studied == total;
        Component scText = wide ? Component.translatable(K + "header.scanned", scanned, total)
                : Component.literal(scanned + "/" + total);
        Component stText = wide ? Component.translatable(K + "header.studied", studied, total)
                : Component.literal(studied + "/" + total);
        int right = c.x() - 8;
        if (complete && p.w() >= 600) {
            Component done = Component.translatable(K + "header.complete");
            right -= this.font.width(done);
            g.text(this.font, done, right, p.y() + 12, AMBER, true);
            sprite(g, right - 10, p.y() + 12, 55, 94, 7, 7);
            right -= 20;
        }
        int stX = right - this.font.width(stText);
        g.text(this.font, stText, stX, p.y() + 12, AMBER, true);
        sprite(g, stX - 10, p.y() + 12, 55, 94, 7, 7);
        control(new Rect(stX - 10, p.y() + 10, right - stX + 10, 12), mouseX, mouseY, false,
                tip(Component.translatable(K + "header.studied.tip")));
        int scRight = stX - 20;
        int scX = scRight - this.font.width(scText);
        g.text(this.font, scText, scX, p.y() + 12, complete ? AMBER : CYAN, true);
        sprite(g, scX - 10, p.y() + 12, 48, 94, 7, 7);
        control(new Rect(scX - 10, p.y() + 10, scRight - scX + 10, 12), mouseX, mouseY, false,
                tip(Component.translatable(K + "header.scanned.tip")));
    }

    private Rect tabRect(int index) {
        Rect p = this.layout.panel();
        int x = p.x() + 10 + this.font.width(Component.translatable(K + "title")) + 12;
        for (int i = 0; i < index; i++) {
            x += this.font.width(Component.translatable(K + "tab." + (i == 0 ? "creatures" : "traits"))) + 16 + 2;
        }
        int w = this.font.width(Component.translatable(K + "tab." + (index == 0 ? "creatures" : "traits"))) + 16;
        return new Rect(x, p.y() + 8, w, 14);
    }

    private void drawFooter(GuiGraphicsExtractor g) {
        Rect p = this.layout.panel();
        String key;
        if (this.page == 1) {
            key = "footer.traits";
        } else {
            key = switch (this.focus) {
                case CONTROLS -> "footer.viewport";
                case TEXT -> "footer.text";
                default -> p.w() < 440 ? "footer.list_short" : "footer.list";
            };
        }
        g.text(this.font, Component.translatable(K + key), p.x() + 10, p.bottom() - 14, SUBTLE, false);
    }

    // --- list -----------------------------------------------------------------------------------

    /** A tile's position in content coordinates (before scrolling). */
    private record TilePos(Entry entry, int x, int y) {
    }

    private List<TilePos> tiles() {
        Rect list = this.layout.list();
        int cols = this.layout.columns();
        int x0 = list.x() + 4;
        int y = list.y() + 4 + (emptyBanner() ? 30 : 0);
        List<TilePos> out = new ArrayList<>();
        for (Habitat habitat : Habitat.values()) {
            List<Entry> entries = BestiaryCatalog.of(habitat);
            for (int i = 0; i < entries.size(); i++) {
                out.add(new TilePos(entries.get(i), x0 + (i % cols) * 30, y + 16 + (i / cols) * 30));
            }
            int rows = (entries.size() + cols - 1) / cols;
            y += 16 + rows * 30 + 4;
        }
        this.listContentHeight = y - list.y();
        return out;
    }

    private static boolean anyDiscovered() {
        for (Entry entry : BestiaryCatalog.entries()) {
            if (ClientBestiary.tier(entry.id()) != Tier.NONE) {
                return true;
            }
        }
        return false;
    }

    /** Single pane, nothing scanned: the empty text sits above the list (section 6). */
    private boolean emptyBanner() {
        return !this.layout.twoPane() && !anyDiscovered();
    }

    private void drawList(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Rect list = this.layout.list();
        g.fill(list.x(), list.y(), list.right(), list.bottom(), TROUGH);
        BestiaryGfx.outline(g, list.x(), list.y(), list.w(), list.h(), PANEL_EDGE);
        List<TilePos> tiles = tiles();
        this.listScroll = Mth.clamp(this.listScroll, 0, Math.max(0, this.listContentHeight - list.h()));
        int off = this.listScroll;
        g.enableScissor(list.x() + 1, list.y() + 1, list.right() - 1, list.bottom() - 1);
        int cols = this.layout.columns();
        int rw = this.layout.rowWidth();
        int x0 = list.x() + 4;
        boolean banner = emptyBanner();
        if (banner) {
            g.text(this.font, Component.translatable(K + "empty.title"), x0, list.y() + 4 - off, TITLE, true);
            g.text(this.font, this.font.plainSubstrByWidth(Component.translatable(K + "empty.short").getString(), rw),
                    x0, list.y() + 16 - off, SUBTLE, false);
        }
        boolean inList = list.contains(mouseX, mouseY);
        int y = list.y() + 4 + (banner ? 30 : 0);
        for (Habitat habitat : Habitat.values()) {
            List<Entry> entries = BestiaryCatalog.of(habitat);
            int discovered = 0;
            int studied = 0;
            for (Entry entry : entries) {
                Tier tier = ClientBestiary.tier(entry.id());
                discovered += tier != Tier.NONE ? 1 : 0;
                studied += tier == Tier.STUDIED ? 1 : 0;
            }
            int hy = y - off;
            sprite(g, x0, hy + 1, 8 * habitat.ordinal(), 104, 8, 8);
            Component name = Component.translatable("habitat.nerocreatures." + habitat.id());
            String count = discovered + "/" + entries.size();
            int nameRoom = rw - 12 - this.font.width(count) - 4;
            Rect headerRect = new Rect(x0, hy, rw, 14);
            g.text(this.font, trimmed(name, nameRoom, tip(name), headerRect, mouseX, mouseY), x0 + 12, hy + 1, BODY, true);
            g.text(this.font, count, x0 + rw - this.font.width(count), hy + 1, SUBTLE, true);
            g.fill(x0, hy + 11, x0 + rw, hy + 13, LINE_IDLE);
            g.fill(x0, hy + 11, x0 + rw * discovered / entries.size(), hy + 13, CYAN);
            g.fill(x0, hy + 11, x0 + rw * studied / entries.size(), hy + 13, AMBER);
            if (inList) {
                control(headerRect, mouseX, mouseY, false, tip(Component.translatable(K + "habitat.tip", name,
                        discovered, entries.size(), studied)));
            }
            int rows = (entries.size() + cols - 1) / cols;
            y += 16 + rows * 30 + 4;
        }
        for (TilePos tp : tiles) {
            drawTile(g, tp, off, mouseX, mouseY, inList);
        }
        g.disableScissor();
        drawScrollbar(g, list.x() + list.w() - 8, list.y() + 2, list.h() - 4, this.listContentHeight, list.h(),
                this.listScroll, mouseX, mouseY);
    }

    private void drawTile(GuiGraphicsExtractor g, TilePos tp, int off, int mouseX, int mouseY, boolean inList) {
        Entry entry = tp.entry();
        int x = tp.x();
        int y = tp.y() - off;
        Rect r = new Rect(x, y, 28, 28);
        Tier tier = ClientBestiary.tier(entry.id());
        boolean open = entry.id().equals(this.selected);
        boolean hover = inList && r.contains(mouseX, mouseY);
        boolean focused = this.focus == Area.LIST && open && this.page == 0;
        if (tier == Tier.NONE) {
            sprite(g, x, y, 84, 0, 28, 28);
            BestiaryGfx.blit(g, tex("textures/gui/bestiary/silhouette/" + entry.id() + ".png"), x + 2, y + 2, 0, 0,
                    24, 24, 24, 24);
            if (open || hover) {
                BestiaryGfx.outline(g, x, y, 28, 28, open ? CYAN : EDGE_HI);
            }
        } else {
            sprite(g, x, y, open ? 56 : hover ? 28 : 0, 0, 28, 28);
            BestiaryGfx.blit(g, tex("textures/gui/bestiary/portrait/" + entry.id() + ".png"), x + 2, y + 2, 0, 0, 24,
                    24, 24, 24);
        }
        if (entry.boss()) {
            sprite(g, x, y, 0, 28, 28, 28);
            sprite(g, x + 2, y + 2, 62, 94, 7, 7);
        }
        if (tier == Tier.STUDIED) {
            sprite(g, x, y, 28, 28, 28, 28);
            sprite(g, x + 19, y + 19, 55, 94, 7, 7);
        } else if (tier == Tier.SCANNED) {
            sprite(g, x + 19, y + 19, 48, 94, 7, 7);
        }
        if (focused) {
            sprite(g, x, y, 56, 28, 28, 28);
        }
        if (!hover && !focused) {
            return;
        }
        List<Component> tip;
        Component lives = Component.translatable(K + "lives_in",
                Component.translatable("habitat.nerocreatures." + entry.habitat().id()));
        if (tier == Tier.NONE) {
            tip = entry.boss()
                    ? tip(Component.translatable(K + "unknown_boss"), lives, Component.translatable(K + "tile.boss_hint"))
                    : tip(Component.translatable(K + "unknown"), lives, Component.translatable(K + "tile.hint"));
        } else if (tier == Tier.SCANNED) {
            List<Component> lines = new ArrayList<>();
            lines.add(ClientBestiary.speciesName(entry.id()));
            lines.add(Component.translatable(K + "tier.scanned").withColor(CYAN));
            Component goal = goalLine(entry);
            if (goal != null) {
                lines.add(goal);
            }
            tip = lines;
        } else {
            tip = tip(ClientBestiary.speciesName(entry.id()), Component.translatable(K + "tier.studied").withColor(AMBER));
        }
        control(r, mouseX, mouseY, focused, tip);
    }

    private void drawScrollbar(GuiGraphicsExtractor g, int x, int y, int h, int content, int view, int scroll, int mouseX,
            int mouseY) {
        if (content <= view) {
            return;
        }
        BestiaryGfx.nineSlice(g, WIDGETS, x, y, 6, h, 106, 56, 6, 12, 2, W, W);
        int thumbH = Math.max(16, h * view / content);
        int thumbY = y + (h - thumbH) * scroll / Math.max(1, content - view);
        boolean hover = new Rect(x, thumbY, 6, thumbH).contains(mouseX, mouseY);
        BestiaryGfx.nineSlice(g, WIDGETS, x, thumbY, 6, thumbH, hover ? 118 : 112, 56, 6, 12, 2, W, W);
    }

    // --- detail ---------------------------------------------------------------------------------

    @Nullable
    private Entry selectedEntry() {
        return this.selected == null ? null : BestiaryCatalog.get(this.selected).orElse(null);
    }

    private void drawDetail(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        BestiaryLayout l = this.layout;
        Entry entry = selectedEntry();
        if (entry == null) {
            if (!anyDiscovered()) {
                drawEmpty(g);
            }
            return;
        }
        Tier tier = ClientBestiary.tier(entry.id());
        if (l.mode() == Mode.SINGLE) {
            Rect back = l.back();
            sprite(g, back.x(), back.y(), back.contains(mouseX, mouseY) ? 78 : 64, 56, 14, 14);
            sprite(g, back.x() + 1, back.y() + 1, 72, 70, 12, 12);
            control(back, mouseX, mouseY, false, tip(Component.translatable(K + "back.tip")));
        }
        drawViewport(g, entry, tier, mouseX, mouseY);
        Component name = tier == Tier.NONE ? Component.translatable(entry.boss() ? K + "unknown_boss" : K + "unknown")
                : ClientBestiary.speciesName(entry.id());
        int nameRoom = l.detail().right() - l.nameX();
        int chipY = l.mode() == Mode.SINGLE ? l.nameY() - 2 : l.nameY() + 12;
        int chipX = l.nameX();
        List<Chip> chips = chips(entry, tier);
        if (l.mode() == Mode.SINGLE) {
            int chipsW = 0;
            for (Chip chip : chips) {
                chipsW += this.font.width(chip.text()) + 8 + 4;
            }
            chipX = l.detail().right() - chipsW + 4;
            nameRoom = chipX - l.nameX() - 6;
        }
        Rect nameRect = new Rect(l.nameX(), l.nameY(), Math.max(0, nameRoom), 10);
        g.text(this.font, trimmed(name, nameRoom, tip(name), nameRect, mouseX, mouseY), l.nameX(), l.nameY(), TITLE, true);
        for (Chip chip : chips) {
            int w = this.font.width(chip.text()) + 8;
            Rect r = new Rect(chipX, chipY, w, 11);
            g.fill(r.x(), r.y(), r.right(), r.bottom(), mix(PANEL, chip.colour(), 0.18F));
            BestiaryGfx.outline(g, r.x(), r.y(), r.w(), r.h(), (chip.colour() & 0x00FFFFFF) | 0xB0000000);
            g.text(this.font, chip.text(), r.x() + 4, r.y() + 2, chip.colour(), false);
            control(r, mouseX, mouseY, false, chip.tip());
            chipX += w + 4;
        }
        if (tier != Tier.NONE) {
            drawRecords(g, entry, tier, mouseX, mouseY);
        }
        drawText(g, entry, tier, mouseX, mouseY);
    }

    private void drawEmpty(GuiGraphicsExtractor g) {
        Rect d = this.layout.detail();
        Component title = Component.translatable(K + "empty.title");
        int cy = d.y() + d.h() / 2 - 16;
        g.text(this.font, title, d.x() + (d.w() - this.font.width(title)) / 2, cy, TITLE, true);
        List<FormattedCharSequence> lines = this.font.split(Component.translatable(K + "empty.body"), Math.max(40, d.w() - 40));
        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            g.text(this.font, line, d.x() + (d.w() - this.font.width(line)) / 2, cy + 14 + i * 10, SUBTLE, false);
        }
    }

    private record Chip(Component text, int colour, List<Component> tip) {
    }

    private List<Chip> chips(Entry entry, Tier tier) {
        List<Chip> out = new ArrayList<>();
        Kind kind = entry.kind();
        int colour = switch (kind) {
            case TAMEABLE, DRONE, MOUNT, LIVESTOCK -> GREEN;
            case AMBIENT, NEUTRAL -> SUBTLE;
            default -> DANGER;
        };
        out.add(new Chip(Component.translatable(K + "kind." + kind.key()), colour,
                tip(Component.translatable(K + "kind." + kind.key() + ".tip"))));
        if (tier == Tier.NONE) {
            out.add(new Chip(Component.translatable(K + "tier.none"), SUBTLE, tip(Component.translatable(K + "tier.none.tip"))));
        } else if (tier == Tier.SCANNED) {
            Component goal = goalLine(entry);
            out.add(new Chip(Component.translatable(K + "tier.scanned"), CYAN,
                    goal == null ? tip(Component.translatable(K + "tier.scanned")) : tip(goal)));
        } else {
            out.add(new Chip(Component.translatable(K + "tier.studied"), AMBER, tip(Component.translatable(K + "tier.studied.tip"))));
        }
        return out;
    }

    static int mix(int a, int b, float t) {
        int ar = a >> 16 & 0xFF;
        int ag = a >> 8 & 0xFF;
        int ab = a & 0xFF;
        int br = b >> 16 & 0xFF;
        int bg = b >> 8 & 0xFF;
        int bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t);
        int gg = (int) (ag + (bg - ag) * t);
        int bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | r << 16 | gg << 8 | bl;
    }

    /** The goal line (section 4.3), or {@code null} for an unknown study kind. */
    @Nullable
    private Component goalLine(Entry entry) {
        BestiaryCountsPayload.Entry counts = ClientBestiary.counts(entry.id());
        StudyKind study = BestiaryRules.studyKind(entry.kind());
        int goal;
        int progress;
        if (counts != null) {
            if (counts.studyKind() < 0 || counts.studyKind() >= StudyKind.values().length) {
                return null;
            }
            study = StudyKind.values()[counts.studyKind()];
            goal = counts.studyGoal();
            progress = counts.studyProgress();
        } else {
            goal = study == StudyKind.KILLS ? (entry.elite() ? BestiaryRules.KILLS_ELITE : BestiaryRules.KILLS_COMMON) : 1;
            progress = ClientBestiary.tier(entry.id()) == Tier.STUDIED ? goal : 0;
        }
        return Component.translatable(K + "goal." + study.name().toLowerCase(Locale.ROOT), progress, goal);
    }

    // --- viewport -------------------------------------------------------------------------------

    private void drawViewport(GuiGraphicsExtractor g, Entry entry, Tier tier, int mouseX, int mouseY) {
        Rect v = this.layout.viewport();
        g.fillGradient(v.x(), v.y(), v.right(), v.bottom(), mix(TROUGH, entry.habitat().accent(), 0.14F), TROUGH);
        BestiaryGfx.outline(g, v.x(), v.y(), v.w(), v.h(), PANEL_EDGE);
        int t = 5;
        g.fill(v.x(), v.y(), v.x() + t, v.y() + 1, CYAN);
        g.fill(v.x(), v.y(), v.x() + 1, v.y() + t, CYAN);
        g.fill(v.right() - t, v.y(), v.right(), v.y() + 1, CYAN);
        g.fill(v.right() - 1, v.y(), v.right(), v.y() + t, CYAN);
        g.fill(v.x(), v.bottom() - 1, v.x() + t, v.bottom(), CYAN);
        g.fill(v.x(), v.bottom() - t, v.x() + 1, v.bottom(), CYAN);
        g.fill(v.right() - t, v.bottom() - 1, v.right(), v.bottom(), CYAN);
        g.fill(v.right() - 1, v.bottom() - t, v.right(), v.bottom(), CYAN);
        if (tier == Tier.NONE) {
            drawPicture(g, "silhouette", entry, v);
            return;
        }
        if (this.failed.contains(entry.id())) {
            drawPicture(g, "portrait", entry, v);
        } else {
            drawModel(g, entry, v);
        }
        String[] icons = {"left", "pause", "right"};
        for (int i = 0; i < 3; i++) {
            Rect r = controlRect(i);
            boolean hover = r.contains(mouseX, mouseY);
            boolean focused = this.focus == Area.CONTROLS && this.focusIndex == i;
            sprite(g, r.x(), r.y(), hover ? 78 : 64, 56, 14, 14);
            int iconU = i == 0 ? 0 : i == 2 ? 36 : this.paused ? 24 : 12;
            sprite(g, r.x() + 1, r.y() + 1, iconU, 70, 12, 12);
            if (focused) {
                focusOutline(g, r);
            }
            String key = i == 1 ? (this.paused ? "control.play" : "control.pause") : "control." + icons[i];
            control(r, mouseX, mouseY, focused, tip(Component.translatable(K + key)));
        }
        if (entry.radiant()) {
            Rect r = controlRect(3);
            boolean focused = this.focus == Area.CONTROLS && this.focusIndex == 3;
            sprite(g, r.x(), r.y(), this.radiantOn ? 92 : r.contains(mouseX, mouseY) ? 78 : 64, 56, 14, 14);
            sprite(g, r.x() + 1, r.y() + 1, 48, 70, 12, 12);
            if (focused) {
                focusOutline(g, r);
            }
            control(r, mouseX, mouseY, focused,
                    tip(Component.translatable(K + (this.radiantOn ? "control.radiant_off" : "control.radiant_on"))));
        }
    }

    private Rect controlRect(int index) {
        Rect v = this.layout.viewport();
        int view = v.w();
        return switch (index) {
            case 0 -> new Rect(v.x() + 3, v.y() + view - 17, 14, 14);
            case 1 -> new Rect(v.x() + 18, v.y() + view - 17, 14, 14);
            case 2 -> new Rect(v.x() + 33, v.y() + view - 17, 14, 14);
            default -> new Rect(v.x() + view - 17, v.y() + view - 17, 14, 14);
        };
    }

    private int controlCount() {
        Entry entry = selectedEntry();
        if (entry == null || ClientBestiary.tier(entry.id()) == Tier.NONE) {
            return 0;
        }
        return entry.radiant() ? 4 : 3;
    }

    /** Silhouette or portrait at the largest whole multiple of 24 that fits {@code V - 16}. */
    private void drawPicture(GuiGraphicsExtractor g, String kind, Entry entry, Rect v) {
        int mult = Math.max(1, (v.w() - 16) / 24);
        int size = 24 * mult;
        int x = v.x() + (v.w() - size) / 2;
        int y = v.y() + (v.h() - size) / 2;
        BestiaryGfx.blitScaled(g, tex("textures/gui/bestiary/" + kind + "/" + entry.id() + ".png"), x, y, size, size, 0,
                0, 24, 24, 24, 24);
    }

    private void drawModel(GuiGraphicsExtractor g, Entry entry, Rect v) {
        try {
            LivingEntity display = display(entry);
            if (display == null) {
                fail(entry, null);
                drawPicture(g, "portrait", entry, v);
                return;
            }
            if (display instanceof CreatureVisualsHolder holder) {
                holder.visuals().setRadiant(this.radiantOn);
            }
            float big = Math.max(display.getBbWidth(), display.getBbHeight());
            float scale = 0.62F * v.w() / Math.max(0.1F, big);
            int feet = v.y() + Math.round(0.84F * v.h());
            BestiaryGfx.entity(g, display, v.x() + 1, v.y() + 1, v.right() - 1, v.bottom() - 1, this.yaw, 10.0F, scale,
                    feet);
        } catch (RuntimeException | LinkageError e) {
            fail(entry, e);
            drawPicture(g, "portrait", entry, v);
        }
    }

    private void fail(Entry entry, @Nullable Throwable error) {
        if (this.failed.add(entry.id())) {
            NeroCreaturesCommon.LOGGER.warn("[NeroCreatures] Bestiary model unavailable for {} ({})", entry.id(),
                    error == null ? "no instance" : error.getClass().getSimpleName());
        }
        this.displays.remove(entry.id());
    }

    @Nullable
    private LivingEntity display(Entry entry) {
        LivingEntity cached = this.displays.get(entry.id());
        if (cached != null) {
            return cached;
        }
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return null;
        }
        EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(
                Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, entry.id()));
        Entity made = type.create(level, EntitySpawnReason.LOAD);
        if (!(made instanceof LivingEntity living)) {
            return null;
        }
        living.tickCount = this.ticks;
        this.displays.put(entry.id(), living);
        return living;
    }

    // --- records box ----------------------------------------------------------------------------

    private void drawRecords(GuiGraphicsExtractor g, Entry entry, Tier tier, int mouseX, int mouseY) {
        Rect r = this.layout.records();
        if (r.h() <= 0 || r.w() <= 0) {
            return;
        }
        g.fill(r.x(), r.y(), r.right(), r.bottom(), PANEL_HI);
        BestiaryGfx.outline(g, r.x(), r.y(), r.w(), r.h(), PANEL_EDGE);
        boolean side = this.layout.mode() == Mode.SIDE;
        int x = r.x() + 5;
        int w = r.w() - 10;
        int y = r.y();
        if (side) {
            g.text(this.font, Component.translatable(K + "records"), x, y + 4, SUBTLE, true);
            y += 12;
        }
        int tierColour = tier == Tier.STUDIED ? AMBER : CYAN;
        sprite(g, x, y + 5, tier == Tier.STUDIED ? 55 : 48, 94, 7, 7);
        g.text(this.font, Component.translatable(K + "tier." + tier.key()), x + 10, y + 4, tierColour, true);
        BestiaryCountsPayload.Entry counts = ClientBestiary.counts(entry.id());
        if (counts == null && loading()) {
            boolean late = this.ticks > 60;
            g.text(this.font, Component.translatable(late ? K + "records.unavailable" : K + "records.loading"), x, y + 16,
                    SUBTLE, false);
            return;
        }
        int goal = counts != null ? Math.max(1, counts.studyGoal()) : 1;
        int progress = counts != null ? counts.studyProgress() : tier == Tier.STUDIED ? 1 : 0;
        g.fill(x, y + 14, x + w, y + 17, LINE_IDLE);
        g.fill(x, y + 14, x + w * Math.min(progress, goal) / goal, y + 17, tierColour);
        int lineY = y + 21;
        Component goalLine = goalLine(entry);
        if (goalLine != null) {
            List<FormattedCharSequence> lines = this.font.split(goalLine, Math.max(20, w));
            int max = side ? 3 : 1;
            for (int i = 0; i < Math.min(max, lines.size()); i++) {
                g.text(this.font, lines.get(i), x, lineY, SUBTLE, false);
                lineY += 10;
            }
        }
        if (counts == null) {
            return;
        }
        List<Component[]> rows = new ArrayList<>();
        List<Integer> icons = new ArrayList<>();
        if ((counts.tracked() & BestiaryCountsPayload.TRACK_KILLS) != 0) {
            rows.add(new Component[] {Component.translatable(K + "records.defeated"),
                    Component.literal(String.valueOf(counts.kills())), Component.translatable(K + "records.defeated.tip")});
            icons.add(entry.boss() ? 36 : 0);
        }
        if ((counts.tracked() & BestiaryCountsPayload.TRACK_OWNED) != 0) {
            rows.add(new Component[] {Component.translatable(K + "records.owned"),
                    Component.literal(String.valueOf(counts.owned())), Component.translatable(K + "records.owned.tip")});
            icons.add(9);
        }
        int rowY = side ? lineY + 3 : r.y() + 34;
        int colW = side ? w : w / 2;
        for (int i = 0; i < rows.size(); i++) {
            int cx = x + (side ? 0 : (i % 2) * colW);
            int cy = rowY + (side ? i : i / 2) * 11;
            if (cy + 9 > r.bottom()) {
                break;
            }
            sprite(g, cx, cy, icons.get(i), 94, 9, 9);
            g.text(this.font, rows.get(i)[0], cx + 13, cy + 1, SUBTLE, false);
            Component value = rows.get(i)[1];
            g.text(this.font, value, cx + colW - 4 - this.font.width(value), cy + 1, TITLE, true);
            control(new Rect(cx, cy, colW - 4, 10), mouseX, mouseY, false, tip(rows.get(i)[2]));
        }
    }

    // --- text region ----------------------------------------------------------------------------

    private void drawText(GuiGraphicsExtractor g, Entry entry, Tier tier, int mouseX, int mouseY) {
        Rect r = this.layout.text();
        if (r.h() <= 0) {
            return;
        }
        int inner = r.w() - 10;
        boolean inside = r.contains(mouseX, mouseY);
        g.enableScissor(r.x(), r.y(), r.right(), r.bottom());
        int y0 = r.y() - this.textScroll;
        int y = y0;
        Component habitatName = Component.translatable("habitat.nerocreatures." + entry.habitat().id());
        y = sectionLabel(g, r.x(), y, inner, Component.translatable(K + "section.habitat"));
        sprite(g, r.x(), y + 1, 8 * entry.habitat().ordinal(), 104, 8, 8);
        g.text(this.font, habitatName, r.x() + 12, y, BODY, true);
        y += 10;
        if (entry.overworld()) {
            y = paragraph(g, r.x(), y, inner, Component.translatable("bestiary.nerocreatures." + entry.id() + ".also"), SUBTLE);
        }
        y += 5;
        if (tier == Tier.NONE) {
            y = paragraph(g, r.x(), y, inner, Component.translatable(K + "not_scanned_yet", habitatName), SUBTLE);
        } else {
            y = sectionLabel(g, r.x(), y, inner, Component.translatable(K + "section.ability"));
            y = paragraph(g, r.x(), y, inner, Component.translatable("bestiary.nerocreatures." + entry.id() + ".ability"), BODY) + 5;
            y = sectionLabel(g, r.x(), y, inner, Component.translatable(K + "section.weakness"));
            if (tier == Tier.STUDIED) {
                y = paragraph(g, r.x(), y, inner, Component.translatable("bestiary.nerocreatures." + entry.id() + ".weakness"),
                        BODY) + 5;
            } else {
                sprite(g, r.x(), y - 1, 60, 70, 12, 12);
                int before = y;
                y = paragraph(g, r.x() + 14, y, inner - 14, Component.translatable(K + "weakness_locked"), SUBTLE) + 5;
                Component goal = goalLine(entry);
                if (inside && goal != null) {
                    control(new Rect(r.x(), before, inner, y - before), mouseX, mouseY, false, tip(goal));
                }
            }
            y = sectionLabel(g, r.x(), y, inner, Component.translatable(K + "section.drops"));
            if (entry.drops().isEmpty()) {
                y = paragraph(g, r.x(), y, inner, Component.translatable(K + "drops_nothing"), SUBTLE) + 5;
            } else {
                for (String drop : entry.drops()) {
                    Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(drop));
                    ItemStack stack = item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item);
                    g.fill(r.x(), y, r.x() + 18, y + 18, TROUGH);
                    BestiaryGfx.outline(g, r.x(), y, 18, 18, PANEL_EDGE);
                    if (!stack.isEmpty()) {
                        g.item(stack, r.x() + 1, y + 1);
                        if (inside && new Rect(r.x(), y, 18, 18).contains(mouseX, mouseY)) {
                            this.hoverItem = stack;
                        }
                        g.text(this.font, this.font.plainSubstrByWidth(stack.getHoverName().getString(), inner - 23),
                                r.x() + 23, y + 1, BODY, true);
                    }
                    sprite(g, r.x() + 22, y + 8, 84, 70, 12, 12);
                    String path = drop.substring(drop.indexOf(':') + 1);
                    Component use = Component.translatable("bestiary.nerocreatures.drop." + path + ".use");
                    int lineY = y + 10;
                    for (FormattedCharSequence line : this.font.split(use, Math.max(20, inner - 36))) {
                        g.text(this.font, line, r.x() + 36, lineY, SUBTLE, false);
                        lineY += 9;
                    }
                    y = Math.max(y + 21, lineY + 2);
                }
                y += 5;
            }
            if (entry.radiant()) {
                y = sectionLabel(g, r.x(), y, inner, Component.translatable(K + "section.traits"));
                y = paragraph(g, r.x(), y, inner, Component.translatable(K + "traits_note"), SUBTLE);
                Component button = Component.translatable(K + "open_glossary");
                Rect b = new Rect(r.x(), y + 1, this.font.width(button) + 8, 12);
                boolean focused = this.focus == Area.TEXT;
                BestiaryGfx.nineSlice(g, WIDGETS, b.x(), b.y(), b.w(), b.h(), b.contains(mouseX, mouseY) ? 78 : 64, 56,
                        14, 14, 3, W, W);
                g.text(this.font, button, b.x() + 4, b.y() + 2, CYAN, false);
                if (focused) {
                    focusOutline(g, b);
                }
                control(b, inside ? mouseX : -1, inside ? mouseY : -1, focused,
                        tip(Component.translatable(K + "open_glossary.tip")));
                if (inside) {
                    this.textHits.add(new Hit(b, () -> setPage(1)));
                }
                y += 19;
            }
            if (entry.alpha()) {
                y = sectionLabel(g, r.x(), y, inner, Component.translatable(K + "section.alphas"));
                sprite(g, r.x(), y + 1, 69, 94, 7, 7);
                y = paragraph(g, r.x() + 10, y, inner - 10, Component.translatable(K + "alpha_note"), BODY) + 5;
            }
            if ("bloom_host".equals(entry.id())) {
                y = sectionLabel(g, r.x(), y, inner, Component.translatable(K + "section.forms"));
                y = paragraph(g, r.x(), y, inner, Component.translatable(K + "forms_note"), BODY) + 5;
            }
            if (entry.boss()) {
                y = sectionLabel(g, r.x(), y, inner, Component.translatable(K + "section.boss"));
                Item sigil = BuiltInRegistries.ITEM.getValue(
                        Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, entry.sigil().orElse("apex_trophy")));
                y = paragraph(g, r.x(), y, inner, Component.translatable(K + "boss_note", new ItemStack(sigil).getHoverName()),
                        BODY);
                y = paragraph(g, r.x(), y, inner, Component.translatable(K + "boss_trophy"), BODY) + 5;
            }
        }
        g.disableScissor();
        this.textContentHeight = y - y0;
        int max = Math.max(0, this.textContentHeight - r.h());
        if (this.textScroll > max) {
            this.textScroll = max;
        }
        drawScrollbar(g, r.right() - 6, r.y(), r.h(), this.textContentHeight, r.h(), this.textScroll, mouseX, mouseY);
    }

    private int sectionLabel(GuiGraphicsExtractor g, int x, int y, int inner, Component label) {
        g.text(this.font, label, x, y, SUBTLE, true);
        int lx = x + this.font.width(label) + 4;
        if (lx < x + inner) {
            g.fill(lx, y + 3, x + inner, y + 4, LINE_IDLE);
        }
        return y + 11;
    }

    private int paragraph(GuiGraphicsExtractor g, int x, int y, int width, Component text, int colour) {
        int at = y;
        for (FormattedCharSequence line : this.font.split(text, Math.max(20, width))) {
            g.text(this.font, line, x, at, colour, colour != SUBTLE);
            at += 10;
        }
        return at;
    }

    // --- traits page ----------------------------------------------------------------------------

    private void drawTraits(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        Rect body = this.layout.body();
        TraitPool pool = TraitPool.get();
        g.enableScissor(body.x(), body.y(), body.right(), body.bottom());
        int y0 = body.y() + 2 - this.traitsScroll;
        Component intro = pool.mutationChance() > 0.0D
                ? Component.translatable(K + "traits.intro", percent(pool.mutationChance()))
                : Component.translatable(K + "traits.intro_no_mutation");
        int y = paragraph(g, body.x() + 2, y0, body.w() - 12, intro, BODY) + 6;
        int cardCols = body.w() >= 440 ? 2 : 1;
        int cw = (body.w() - 8 - (cardCols - 1) * 6) / cardCols;
        List<TraitDefinition> traits = pool.traits();
        for (int i = 0; i < traits.size(); i++) {
            TraitDefinition def = traits.get(i);
            int cx = body.x() + 2 + (i % cardCols) * (cw + 6);
            int cy = y + (i / cardCols) * 38;
            g.fill(cx, cy, cx + cw, cy + 34, PANEL_HI);
            BestiaryGfx.outline(g, cx, cy, cw, 34, PANEL_EDGE);
            g.fill(cx + 5, cy + 5, cx + 21, cy + 21, TROUGH);
            int icon = iconIndex(def.id());
            if (icon >= 0) {
                sprite(g, cx + 7, cy + 7, icon * 12, 82, 12, 12);
            }
            boolean radiant = TraitPool.RADIANT.equals(def.id());
            g.text(this.font, Component.translatable("trait.nerocreatures." + def.id()), cx + 27, cy + 5,
                    radiant ? RADIANT : TITLE, true);
            List<FormattedCharSequence> lines = this.font.split(effectText(def), Math.max(20, cw - 32));
            for (int line = 0; line < Math.min(2, lines.size()); line++) {
                g.text(this.font, lines.get(line), cx + 27, cy + 16 + line * 9, SUBTLE, false);
            }
            if (radiant) {
                control(new Rect(cx, cy, cw, 34), mouseX, mouseY, false, tip(Component.translatable(K + "traits.radiant.tip")));
            }
        }
        int rows = (traits.size() + cardCols - 1) / cardCols;
        y += rows * 38;
        g.disableScissor();
        this.traitsContentHeight = y - y0;
        this.traitsScroll = Mth.clamp(this.traitsScroll, 0, Math.max(0, this.traitsContentHeight - body.h()));
        drawScrollbar(g, body.right() - 6, body.y(), body.h(), this.traitsContentHeight, body.h(), this.traitsScroll, mouseX,
                mouseY);
    }

    private static int iconIndex(String id) {
        String[] order = {"hardy", "swift", "keen", "bountiful", "calm", "thrifty", "stout", "radiant"};
        for (int i = 0; i < order.length; i++) {
            if (order[i].equals(id)) {
                return i;
            }
        }
        return -1;
    }

    static String percent(double fraction) {
        double pct = fraction * 100.0D;
        return Math.abs(pct - Math.rint(pct)) < 1.0E-6 ? String.valueOf(Math.round(pct))
                : String.format(Locale.ROOT, "%.1f", pct);
    }

    private static Component effectText(TraitDefinition def) {
        String key = K + "trait." + def.id();
        return switch (def.effect().type()) {
            case ATTRIBUTE -> Component.translatable(key, percent(def.effect().amount()));
            case ABILITY_RANGE -> Component.translatable(key, percent(def.effect().multiplier() - 1.0D));
            case PRODUCT_YIELD -> Component.translatable(key, percent(def.effect().extraRollChance()));
            case COOLDOWN -> Component.translatable(key, percent(1.0D - def.effect().multiplier()));
            case CARRY_SLOTS -> Component.translatable(key, String.valueOf(def.effect().slots()));
            default -> Component.translatable(key);
        };
    }

    // ============================================================================================
    // input
    // ============================================================================================

    private void setPage(int page) {
        this.page = page;
        this.focus = page == 1 ? Area.TEXT : Area.LIST;
        this.focusIndex = 0;
    }

    private void open(Entry entry) {
        if (!entry.id().equals(this.selected)) {
            this.selected = entry.id();
            this.textScroll = 0;
            this.radiantOn = false;
        }
        if (!this.layout.twoPane()) {
            this.singleDetail = true;
        }
    }

    //? if <1.21.9 {
    /*private record MouseButtonEvent(double x, double y, int button) {
    }

    private record KeyEvent(int key, int scancode, int modifiers) {
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        MouseButtonEvent event = new MouseButtonEvent(mouseX, mouseY, button);
    *///?} else {
    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
    //?}
        if (event.button() != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        double mx = event.x();
        double my = event.y();
        BestiaryLayout l = this.layout;
        if (l.close().contains(mx, my)) {
            this.onClose();
            return true;
        }
        for (int i = 0; i < 2; i++) {
            if (tabRect(i).contains(mx, my)) {
                setPage(i);
                return true;
            }
        }
        if (this.page == 1) {
            return startScrollDrag(mx, my, 2, l.body(), this.traitsContentHeight, this.traitsScroll);
        }
        boolean listVisible = l.twoPane() || !this.singleDetail;
        boolean detailVisible = l.twoPane() || this.singleDetail;
        if (listVisible && l.list().contains(mx, my)) {
            if (startScrollDrag(mx, my, 0, l.list(), this.listContentHeight, this.listScroll)) {
                return true;
            }
            for (TilePos tp : tiles()) {
                if (new Rect(tp.x(), tp.y() - this.listScroll, 28, 28).contains(mx, my)) {
                    this.focus = Area.LIST;
                    open(tp.entry());
                    return true;
                }
            }
            return true;
        }
        if (!detailVisible) {
            return false;
        }
        if (l.mode() == Mode.SINGLE && l.back().contains(mx, my)) {
            this.singleDetail = false;
            return true;
        }
        for (int i = 0; i < controlCount(); i++) {
            if (controlRect(i).contains(mx, my)) {
                this.focus = Area.CONTROLS;
                this.focusIndex = i;
                pressControl(i);
                return true;
            }
        }
        if (l.viewport().contains(mx, my) && controlCount() > 0) {
            this.draggingModel = true;
            return true;
        }
        for (Hit hit : List.copyOf(this.textHits)) {
            if (hit.rect().contains(mx, my)) {
                hit.action().run();
                return true;
            }
        }
        return startScrollDrag(mx, my, 1, l.text(), this.textContentHeight, this.textScroll);
    }

    /** Thumb drag or track click on a region's scrollbar (the strip on its right edge). */
    private boolean startScrollDrag(double mx, double my, int target, Rect region, int content, int scroll) {
        if (content <= region.h()) {
            return false;
        }
        Rect bar = new Rect(region.right() - 8, region.y(), 8, region.h());
        if (!bar.contains(mx, my)) {
            return false;
        }
        int view = region.h();
        int thumbH = Math.max(16, region.h() * view / content);
        int thumbY = region.y() + (region.h() - thumbH) * scroll / Math.max(1, content - view);
        if (my >= thumbY && my < thumbY + thumbH) {
            this.dragScroll = target;
            this.dragStartY = my;
            this.dragStartScroll = scroll;
        } else {
            scrollBy(target, my < thumbY ? -(view - 20) : view - 20);
        }
        return true;
    }

    private void scrollBy(int target, int amount) {
        switch (target) {
            case 0 -> this.listScroll = Mth.clamp(this.listScroll + amount, 0,
                    Math.max(0, this.listContentHeight - this.layout.list().h()));
            case 1 -> this.textScroll = Mth.clamp(this.textScroll + amount, 0,
                    Math.max(0, this.textContentHeight - this.layout.text().h()));
            default -> this.traitsScroll = Mth.clamp(this.traitsScroll + amount, 0,
                    Math.max(0, this.traitsContentHeight - this.layout.body().h()));
        }
    }

    @Override
    //? if >=1.21.9 {
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
    //?} else {
    /*public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        MouseButtonEvent event = new MouseButtonEvent(mouseX, mouseY, button);
    *///?}
        if (this.draggingModel) {
            this.paused = true;
            this.yaw = (float) ((this.yaw + dragX) % 360.0D);
            return true;
        }
        if (this.dragScroll >= 0) {
            Rect region = this.dragScroll == 0 ? this.layout.list()
                    : this.dragScroll == 1 ? this.layout.text() : this.layout.body();
            int content = this.dragScroll == 0 ? this.listContentHeight
                    : this.dragScroll == 1 ? this.textContentHeight : this.traitsContentHeight;
            int view = region.h();
            int thumbH = Math.max(16, region.h() * view / Math.max(1, content));
            double perPixel = (content - view) / (double) Math.max(1, region.h() - thumbH);
            int wanted = this.dragStartScroll + (int) Math.round((event.y() - this.dragStartY) * perPixel);
            int current = this.dragScroll == 0 ? this.listScroll : this.dragScroll == 1 ? this.textScroll : this.traitsScroll;
            scrollBy(this.dragScroll, wanted - current);
            return true;
        }
        return false;
    }

    @Override
    //? if >=1.21.9 {
    public boolean mouseReleased(MouseButtonEvent event) {
    //?} else {
    /*public boolean mouseReleased(double mouseX, double mouseY, int button) {
    *///?}
        this.draggingModel = false;
        this.dragScroll = -1;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int notches = (int) Math.signum(-scrollY);
        if (notches == 0) {
            return false;
        }
        if (this.page == 1) {
            scrollBy(2, notches * 20);
            return true;
        }
        BestiaryLayout l = this.layout;
        if ((l.twoPane() || !this.singleDetail) && l.list().contains(mouseX, mouseY)) {
            scrollBy(0, notches * 30);
            return true;
        }
        if ((l.twoPane() || this.singleDetail) && l.text().contains(mouseX, mouseY)) {
            scrollBy(1, notches * 20);
            return true;
        }
        return false;
    }

    private void pressControl(int index) {
        switch (index) {
            case 0 -> this.yaw = (this.yaw - 15.0F + 360.0F) % 360.0F;
            case 1 -> this.paused = !this.paused;
            case 2 -> this.yaw = (this.yaw + 15.0F) % 360.0F;
            default -> {
                Entry entry = selectedEntry();
                if (entry != null && entry.radiant()) {
                    this.radiantOn = !this.radiantOn;
                }
            }
        }
    }

    @Override
    //? if >=1.21.9 {
    public boolean keyPressed(KeyEvent event) {
    //?} else {
    /*public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        KeyEvent event = new KeyEvent(keyCode, scanCode, modifiers);
    *///?}
        int key = event.key();
        boolean ctrl = (event.modifiers() & 2) != 0;
        boolean shift = (event.modifiers() & 1) != 0;
        if (key == InputConstants.KEY_ESCAPE) {
            this.onClose();
            return true;
        }
        //? if >=1.21.9 {
        boolean closeKey = Minecraft.getInstance().options.keyInventory.matches(event)
                || ClientBestiary.OPEN_BESTIARY.matches(event);
        //?} else {
        /*boolean closeKey = Minecraft.getInstance().options.keyInventory.matches(keyCode, scanCode)
                || ClientBestiary.OPEN_BESTIARY.matches(keyCode, scanCode);
        *///?}
        if (closeKey) {
            this.onClose();
            return true;
        }
        if (key == InputConstants.KEY_TAB && ctrl) {
            setPage(1 - this.page);
            return true;
        }
        if (key == InputConstants.KEY_1 || key == InputConstants.KEY_2) {
            setPage(key == InputConstants.KEY_1 ? 0 : 1);
            return true;
        }
        if (key == InputConstants.KEY_TAB) {
            cycleFocus(shift ? -1 : 1);
            return true;
        }
        if (this.page == 1 && this.focus != Area.TABS && this.focus != Area.CLOSE) {
            return scrollKeys(key, 2, this.layout.body().h());
        }
        Entry entry = selectedEntry();
        if (this.page == 0 && entry != null && ClientBestiary.tier(entry.id()) != Tier.NONE) {
            if (key == InputConstants.KEY_COMMA || key == InputConstants.KEY_PERIOD) {
                pressControl(key == InputConstants.KEY_COMMA ? 0 : 2);
                return true;
            }
            if (key == InputConstants.KEY_P) {
                pressControl(1);
                return true;
            }
            if (key == InputConstants.KEY_R && entry.radiant()) {
                pressControl(3);
                return true;
            }
        }
        if (!this.layout.twoPane() && this.singleDetail && key == InputConstants.KEY_BACKSPACE) {
            this.singleDetail = false;
            this.focus = Area.LIST;
            return true;
        }
        boolean confirm = key == InputConstants.KEY_RETURN || key == InputConstants.KEY_SPACE
                || key == InputConstants.KEY_NUMPADENTER;
        switch (this.focus) {
            case TABS -> {
                if (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT) {
                    this.focusIndex = 1 - this.focusIndex;
                    return true;
                }
                if (confirm) {
                    setPage(this.focusIndex);
                    return true;
                }
            }
            case LIST -> {
                if (moveSelection(key)) {
                    return true;
                }
                if (confirm && entry != null) {
                    open(entry);
                    return true;
                }
            }
            case CONTROLS -> {
                int count = controlCount();
                if (count > 0 && (key == InputConstants.KEY_LEFT || key == InputConstants.KEY_RIGHT)) {
                    this.focusIndex = Math.floorMod(this.focusIndex + (key == InputConstants.KEY_LEFT ? -1 : 1), count);
                    return true;
                }
                if (confirm && count > 0) {
                    pressControl(this.focusIndex);
                    return true;
                }
            }
            case TEXT -> {
                if (confirm && entry != null && entry.radiant() && ClientBestiary.tier(entry.id()) != Tier.NONE) {
                    setPage(1);
                    return true;
                }
                if (scrollKeys(key, 1, this.layout.text().h())) {
                    return true;
                }
            }
            case CLOSE -> {
                if (confirm) {
                    this.onClose();
                    return true;
                }
            }
            default -> {
            }
        }
        return false;
    }

    private boolean scrollKeys(int key, int target, int view) {
        if (key == InputConstants.KEY_UP || key == InputConstants.KEY_DOWN) {
            scrollBy(target, key == InputConstants.KEY_UP ? -10 : 10);
            return true;
        }
        if (key == InputConstants.KEY_PAGEUP || key == InputConstants.KEY_PAGEDOWN) {
            scrollBy(target, (key == InputConstants.KEY_PAGEUP ? -1 : 1) * Math.max(10, view - 20));
            return true;
        }
        if (key == InputConstants.KEY_HOME || key == InputConstants.KEY_END) {
            scrollBy(target, key == InputConstants.KEY_HOME ? -1_000_000 : 1_000_000);
            return true;
        }
        return false;
    }

    private void cycleFocus(int dir) {
        List<Area> order = new ArrayList<>();
        order.add(Area.TABS);
        if (this.page == 0) {
            boolean listVisible = this.layout.twoPane() || !this.singleDetail;
            boolean detailVisible = this.layout.twoPane() || this.singleDetail;
            if (listVisible) {
                order.add(Area.LIST);
            }
            if (detailVisible && controlCount() > 0) {
                order.add(Area.CONTROLS);
            }
            if (detailVisible && selectedEntry() != null) {
                order.add(Area.TEXT);
            }
        } else {
            order.add(Area.TEXT);
        }
        order.add(Area.CLOSE);
        int at = order.indexOf(this.focus);
        this.focus = order.get(Math.floorMod((at < 0 ? 0 : at) + dir, order.size()));
        this.focusIndex = this.focus == Area.TABS ? this.page : 0;
        if (this.focus == Area.LIST && this.selected == null) {
            this.selected = BestiaryCatalog.entries().get(0).id();
        }
    }

    /** Arrow keys move by tile geometry across habitat sections; the focused tile scrolls into view. */
    private boolean moveSelection(int key) {
        if (key != InputConstants.KEY_LEFT && key != InputConstants.KEY_RIGHT && key != InputConstants.KEY_UP
                && key != InputConstants.KEY_DOWN) {
            return false;
        }
        List<TilePos> tiles = tiles();
        int index = 0;
        for (int i = 0; i < tiles.size(); i++) {
            if (tiles.get(i).entry().id().equals(this.selected)) {
                index = i;
            }
        }
        TilePos current = tiles.get(index);
        TilePos next = current;
        if (key == InputConstants.KEY_LEFT) {
            next = tiles.get(Math.max(0, index - 1));
        } else if (key == InputConstants.KEY_RIGHT) {
            next = tiles.get(Math.min(tiles.size() - 1, index + 1));
        } else {
            boolean down = key == InputConstants.KEY_DOWN;
            int bestRowY = down ? Integer.MAX_VALUE : Integer.MIN_VALUE;
            for (TilePos tp : tiles) {
                if (down ? tp.y() > current.y() && tp.y() < bestRowY : tp.y() < current.y() && tp.y() > bestRowY) {
                    bestRowY = tp.y();
                }
            }
            int bestDx = Integer.MAX_VALUE;
            for (TilePos tp : tiles) {
                if (tp.y() == bestRowY && Math.abs(tp.x() - current.x()) < bestDx) {
                    bestDx = Math.abs(tp.x() - current.x());
                    next = tp;
                }
            }
        }
        this.selected = next.entry().id();
        this.textScroll = 0;
        this.radiantOn = false;
        Rect list = this.layout.list();
        int top = next.y() - list.y() - 4;
        if (top - 16 < this.listScroll) {
            this.listScroll = Math.max(0, top - 16);
        } else if (top + 32 > this.listScroll + list.h()) {
            this.listScroll = top + 32 - list.h();
        }
        return true;
    }
}
