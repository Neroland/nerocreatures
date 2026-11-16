package za.co.neroland.nerocreatures.gallery;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerocreatures.entity.mount.MountCreature;
import za.co.neroland.nerocreatures.entity.visual.CreatureVisualsHolder;

/**
 * Gallery display entities: the one predicate every system asks ({@link #isDisplay}), the frozen
 * display recipe, item stands, labels, the anchor and its cached lookup, and the two exceptions to
 * "a display does nothing" (the paddock ride and its snap-back).
 *
 * <p>A display is never tamed, owned, bred, saddled through the item path, captured or named; it has
 * no loot and no experience; it writes no statistic, gate, ownership row or link event. The gallery
 * stores nothing about any player.
 *
 * <p>Server thread only, except {@link #isDisplay}, which only reads an entity's tags.
 */
public final class GalleryDisplays {

    /** Temporary tag that finds a stand the summon command just made. */
    private static final String NEW_TAG = "nerocreatures_gallery_new";
    /** Equipment slots locked on every stand (place, take, replace for every slot). */
    private static final int ALL_SLOTS_DISABLED = 4144959;
    /** Anchor lookup cache life, ticks. */
    private static final int ANCHOR_CACHE_TICKS = 100;

    private static final Map<ResourceKey<Level>, AnchorCache> ANCHORS = new HashMap<>();

    private GalleryDisplays() {
    }

    // --- the predicate ---------------------------------------------------------------------------

    /** The entity's scoreboard tags. */
    public static Collection<String> tags(Entity entity) {
        //? if >=26.1 {
        return entity.entityTags();
        //?} else {
        /*return entity.getTags();
        *///?}
    }

    /** True for anything the gallery placed. Every exclusion rule calls this. */
    public static boolean isDisplay(@Nullable Entity entity) {
        return entity != null && tags(entity).contains(MenagerieLayout.TAG);
    }

    /** True for a rideable paddock mount. */
    public static boolean isPaddock(@Nullable Entity entity) {
        return entity != null && tags(entity).contains(MenagerieLayout.PADDOCK_TAG);
    }

    /** The slot suffix of a display, or {@code ""}. */
    public static String slotOf(Entity entity) {
        for (String tag : tags(entity)) {
            if (tag.startsWith(MenagerieLayout.SLOT_TAG_PREFIX)) {
                return tag.substring(MenagerieLayout.SLOT_TAG_PREFIX.length());
            }
        }
        return "";
    }

    // --- creation --------------------------------------------------------------------------------

    /** The entity type for a path in this mod's namespace, or null. */
    @Nullable
    public static EntityType<?> type(String path) {
        Identifier id = Identifier.fromNamespaceAndPath("nerocreatures", path);
        return BuiltInRegistries.ENTITY_TYPE.containsKey(id) ? BuiltInRegistries.ENTITY_TYPE.getValue(id) : null;
    }

    /**
     * A frozen display: created and added, <b>never {@code finalizeSpawn}</b> (no alpha roll, no boss
     * fight, no species set-up that touches the world), then NoAI, silent, invulnerable, persistent,
     * faced, and tagged. Hovering exhibits are pinned with no gravity.
     */
    @Nullable
    public static Mob spawn(ServerLevel level, String entity, double x, double y, double z, float yaw,
            double hover, String slot) {
        EntityType<?> type = type(entity);
        if (type == null) {
            return null;
        }
        Entity created = type.create(level, EntitySpawnReason.COMMAND);
        if (!(created instanceof Mob mob)) {
            return null;
        }
        mob.snapTo(x, y + hover, z, yaw, 0.0F);
        mob.setYBodyRot(yaw);
        mob.setYHeadRot(yaw);
        mob.setNoAi(true);
        mob.setSilent(true);
        //? if >=26.3 {
        mob.setPermanentlyInvulnerable(true);
        //?} else {
        /*mob.setInvulnerable(true);
        *///?}
        mob.setPersistenceRequired();
        mob.setTarget(null);
        if (hover > 0.0D) {
            mob.setNoGravity(true);
        }
        mob.addTag(MenagerieLayout.TAG);
        mob.addTag(MenagerieLayout.SLOT_TAG_PREFIX + slot);
        return level.addFreshEntity(mob) ? mob : null;
    }

    /**
     * An item stand, made by the vanilla {@code summon} command so the marker flag and the locked
     * equipment slots (both private fields) are set the same way on every line: a marker has no
     * hitbox, so nobody can click the item off it.
     *
     * @param visible a visible stand with arms (gear), else an invisible stand wearing the item
     */
    @Nullable
    public static ArmorStand stand(ServerLevel level, double x, double y, double z, float yaw, ItemStack stack,
            boolean visible, String... extraTags) {
        ArmorStand stand = summonStand(level, x, y, z, yaw, visible, extraTags);
        if (stand == null) {
            return null;
        }
        if (!stack.isEmpty()) {
            stand.setItemSlot(visible ? slotFor(stack) : EquipmentSlot.HEAD, stack);
        }
        return stand;
    }

    /** The equipment slot a gear item goes in: its own slot when it has one, else the main hand. */
    private static EquipmentSlot slotFor(ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (path.endsWith("_boots")) {
            return EquipmentSlot.FEET;
        }
        if (path.endsWith("_greaves")) {
            return EquipmentSlot.LEGS;
        }
        if (path.endsWith("_cloak")) {
            return EquipmentSlot.CHEST;
        }
        if (path.endsWith("_shield") || path.endsWith("_bulwark")) {
            return EquipmentSlot.OFFHAND;
        }
        return EquipmentSlot.MAINHAND;
    }

    @Nullable
    private static ArmorStand summonStand(ServerLevel level, double x, double y, double z, float yaw,
            boolean visible, String... extraTags) {
        StringBuilder tags = new StringBuilder("\"" + MenagerieLayout.TAG + "\",\"" + NEW_TAG + "\"");
        for (String tag : extraTags) {
            tags.append(",\"").append(tag).append('"');
        }
        String nbt = "{Marker:1b,NoGravity:1b,Invulnerable:1b,Silent:1b,DisabledSlots:" + ALL_SLOTS_DISABLED
                + (visible ? ",ShowArms:1b,NoBasePlate:1b" : ",Invisible:1b")
                + ",Rotation:[" + yaw + "f,0f],Tags:[" + tags + "]}";
        command(level, String.format(java.util.Locale.ROOT, "summon minecraft:armor_stand %.3f %.3f %.3f %s",
                x, y, z, nbt));
        AABB box = new AABB(x - 0.5D, y - 0.5D, z - 0.5D, x + 0.5D, y + 2.5D, z + 0.5D);
        List<ArmorStand> found = level.getEntitiesOfClass(ArmorStand.class, box,
                e -> tags(e).contains(NEW_TAG));
        ArmorStand stand = found.isEmpty() ? null : found.get(0);
        for (ArmorStand extra : found) {
            extra.removeTag(NEW_TAG);
        }
        return stand;
    }

    /** A floating label: an invisible, weightless, tagged marker stand with a visible name. */
    public static void label(ServerLevel level, double x, double y, double z, Component name) {
        ArmorStand stand = summonStand(level, x, y, z, 0.0F, false, MenagerieLayout.LABEL_TAG);
        if (stand != null) {
            stand.setCustomName(name);
            stand.setCustomNameVisible(true);
        }
    }

    /** The anchor: one marker at the origin, tagged with its layout. Names nobody. */
    public static void anchor(ServerLevel level, BlockPos origin, boolean grid) {
        summonStand(level, origin.getX() + 0.5D, origin.getY() + 1.0D, origin.getZ() + 0.5D, 0.0F, false,
                MenagerieLayout.ANCHOR_TAG,
                grid ? MenagerieLayout.LAYOUT_GRID_TAG : MenagerieLayout.LAYOUT_MENAGERIE_TAG);
        ANCHORS.remove(level.dimension());
    }

    /** Runs a server command at permission level 4 in this level, output suppressed. */
    public static void command(ServerLevel level, String command) {
        MinecraftServer server = level.getServer();
        CommandSourceStack source = server.createCommandSourceStack().withLevel(level).withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, command);
    }

    // --- anchors ---------------------------------------------------------------------------------

    /** A gallery's origin (the anchor's block position minus one up) and layout. */
    public record Anchor(BlockPos origin, boolean grid, Entity entity) {
    }

    private record AnchorCache(long gameTime, List<Anchor> anchors) {
    }

    /** Every loaded anchor in the level; cached per level for {@value #ANCHOR_CACHE_TICKS} ticks. */
    public static List<Anchor> anchors(ServerLevel level) {
        long now = level.getGameTime();
        AnchorCache cached = ANCHORS.get(level.dimension());
        if (cached != null && now >= cached.gameTime() && now - cached.gameTime() < ANCHOR_CACHE_TICKS) {
            return cached.anchors();
        }
        List<Anchor> out = new ArrayList<>();
        for (ArmorStand stand : level.getEntities(EntityTypeTest.forClass(ArmorStand.class),
                e -> e.isAlive() && tags(e).contains(MenagerieLayout.ANCHOR_TAG))) {
            boolean grid = tags(stand).contains(MenagerieLayout.LAYOUT_GRID_TAG);
            out.add(new Anchor(stand.blockPosition().below(), grid, stand));
        }
        List<Anchor> frozen = List.copyOf(out);
        ANCHORS.put(level.dimension(), new AnchorCache(now, frozen));
        return frozen;
    }

    /** Forgets the cached anchors of a level (after a build or a clear). */
    public static void forgetAnchors(ServerLevel level) {
        ANCHORS.remove(level.dimension());
    }

    /** The nearest anchor within {@code radius} blocks of {@code pos}, or null. */
    @Nullable
    public static Anchor nearestAnchor(ServerLevel level, Vec3 pos, int radius) {
        Anchor best = null;
        double bestDist = (double) radius * radius;
        for (Anchor anchor : anchors(level)) {
            if (anchor.entity().isRemoved()) {
                continue;
            }
            double d = Vec3.atBottomCenterOf(anchor.origin()).distanceToSqr(pos);
            if (d <= bestDist) {
                bestDist = d;
                best = anchor;
            }
        }
        return best;
    }

    // --- interaction -----------------------------------------------------------------------------

    /**
     * Every species' {@code mobInteract} hands a display here first. A display does nothing, except
     * that a creative player may ride a saddled paddock mount (no owner, no ownership store, no
     * statistic). The pannier menu is refused with an action-bar line.
     */
    public static InteractionResult interact(Mob display, Player player, InteractionHand hand) {
        if (!(display instanceof MountCreature mount) || !isPaddock(display)) {
            return InteractionResult.PASS;
        }
        if (!player.getAbilities().instabuild || player.isSpectator()) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResult.SUCCESS;
        }
        if (player.isSecondaryUseActive()) {
            serverPlayer.sendSystemMessage(Component.translatable("hud.nerocreatures.scanner.gallery"), true);
            return InteractionResult.SUCCESS_SERVER;
        }
        if (!player.getItemInHand(hand).isEmpty() || mount.isVehicle()) {
            return InteractionResult.PASS;
        }
        mount.setNoAi(false);
        mount.setNoGravity(false); // the species' own flight rule decides from here (gliding or flying)
        player.setYRot(mount.getYRot());
        player.setXRot(mount.getXRot());
        player.startRiding(mount);
        return InteractionResult.SUCCESS_SERVER;
    }

    /**
     * Paddock mounts, once per server tick from {@code MountCreature.tick}: a rider who leaves the yard
     * is put off; a riderless mount that is awake snaps back to its post and freezes again.
     */
    public static void tickPaddock(MountCreature mount, ServerLevel level) {
        MenagerieLayout.PaddockMount post = MenagerieLayout.paddock(slotOf(mount)).orElse(null);
        if (post == null) {
            return;
        }
        boolean ridden = mount.getFirstPassenger() != null;
        if (ridden) {
            if (mount.tickCount % 10 != 0) {
                return;
            }
            Anchor anchor = nearestAnchor(level, mount.position(), MenagerieLayout.ANCHOR_RADIUS + 40);
            if (anchor == null || !inYard(post, anchor.origin(), mount.position())) {
                mount.ejectPassengers();
            }
            return;
        }
        if (mount.isNoAi()) {
            return;
        }
        Anchor anchor = nearestAnchor(level, mount.position(), MenagerieLayout.ANCHOR_RADIUS + 40);
        mount.setNoAi(true);
        mount.getNavigation().stop();
        mount.setDeltaMovement(Vec3.ZERO);
        if (anchor != null) {
            BlockPos o = anchor.origin();
            mount.snapTo(o.getX() + post.entityX(), o.getY() + 1 + post.hover(), o.getZ() + post.entityZ(),
                    post.yaw(), 0.0F);
            mount.setYBodyRot(post.yaw());
            mount.setYHeadRot(post.yaw());
        }
        mount.setNoGravity(post.hover() > 0.0D);
    }

    static boolean inYard(MenagerieLayout.PaddockMount post, BlockPos origin, Vec3 pos) {
        double dx = pos.x - origin.getX();
        double dz = pos.z - origin.getZ();
        double dy = pos.y - origin.getY();
        MenagerieLayout.Box yard = post.yard();
        return yard.containsXZ(dx, dz) && dy <= yard.maxY();
    }

    /** True when the entity is a display whose visuals the gallery may drive. */
    public static boolean hasVisuals(Entity entity) {
        return entity instanceof CreatureVisualsHolder;
    }
}
