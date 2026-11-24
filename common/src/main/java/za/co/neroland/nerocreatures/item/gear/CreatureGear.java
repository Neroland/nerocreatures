package za.co.neroland.nerocreatures.item.gear;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.compat.CreatureAttributeModifier;
import za.co.neroland.nerocreatures.data.CreatureOwnershipState;
import za.co.neroland.nerocreatures.data.OwnedCreature;
import za.co.neroland.nerocreatures.entity.ai.shared.SoundHearing;
import za.co.neroland.nerocreatures.entity.tame.PetCommand;
import za.co.neroland.nerocreatures.entity.tame.TameableCreature;
import za.co.neroland.nerocreatures.registry.ModSounds;

/**
 * The server half of the creature gear: the passive effects (one pass over online players per tick,
 * most work on a 5-tick cadence) and the three use actions. Hooks:
 * {@link #init()} once at common init (registers the Lurker Cloak silencer with
 * {@link SoundHearing}), and {@link #serverTick(MinecraftServer)} from the server tick.
 *
 * <p>Everything is bounded: the Mirror Shield scans a {@value GearRules#REFLECT_RADIUS}-block box
 * only for a player holding one off cooldown; the whistle reads the user's own ownership rows and
 * only loaded entities in the user's own level; the blink never enters a block, an unloaded chunk or
 * the world border. No player identity is stored or logged; the greaves' charge lives in a weak map
 * keyed by the player object for the session only.
 */
public final class CreatureGear {

    /** Ticks between the cadence-based passive checks. */
    public static final int CADENCE_TICKS = 5;

    private static final Identifier BOOTS_GRAVITY_ID =
            Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "graviton_boots_gravity");
    private static final Identifier BOOTS_FALL_ID =
            Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "graviton_boots_fall");
    private static final Identifier BULWARK_ID =
            Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "chitin_bulwark");

    /** Greaves charge per player, this session only. Weak keys: a player who leaves is forgotten. */
    private static final Map<Player, int[]> GREAVES_CHARGE = new WeakHashMap<>();

    private static boolean initialised;

    private CreatureGear() {
    }

    /** Registers the Lurker Cloak with the sound hunters. Idempotent. */
    public static synchronized void init() {
        if (initialised) {
            return;
        }
        initialised = true;
        SoundHearing.addSilencer(CreatureGear::silencedByCloak);
    }

    /** The Lurker Cloak silencer: a wearer is silent unless sprinting. */
    static boolean silencedByCloak(Entity entity) {
        return entity instanceof Player player && GearRules.cloakSilences(
                GearSettings.lurkerCloakSilences.getAsBoolean(), find(player, GearKind.LURKER_CLOAK) != null,
                player.isSprinting());
    }

    // --- finding gear -----------------------------------------------------------------------------

    /** The gear stack of {@code kind} the player is wearing (its slot) or holding, or null. */
    @Nullable
    public static ItemStack find(Player player, GearKind kind) {
        EquipmentSlot slot = kind.slot();
        if (slot != null) {
            ItemStack worn = player.getItemBySlot(slot);
            if (CreatureGearItem.is(worn, kind)) {
                return worn;
            }
        }
        ItemStack off = player.getOffhandItem();
        if (CreatureGearItem.is(off, kind)) {
            return off;
        }
        ItemStack main = player.getMainHandItem();
        return CreatureGearItem.is(main, kind) ? main : null;
    }

    // --- cooldowns (vanilla item cooldowns, shown on the item) ----------------------------------

    public static boolean onCooldown(Player player, ItemStack stack) {
        //? if >=1.21.2 {
        return player.getCooldowns().isOnCooldown(stack);
        //?} else {
        /*return player.getCooldowns().isOnCooldown(stack.getItem());
        *///?}
    }

    static void startCooldown(Player player, ItemStack stack, int ticks) {
        //? if >=1.21.2 {
        player.getCooldowns().addCooldown(stack, GearRules.cooldown(ticks));
        //?} else {
        /*player.getCooldowns().addCooldown(stack.getItem(), GearRules.cooldown(ticks));
        *///?}
    }

    private static ServerLevel levelOf(ServerPlayer player) {
        //? if >=1.21.6 {
        return player.level();
        //?} else {
        /*return player.serverLevel();
        *///?}
    }

    // --- tick ---------------------------------------------------------------------------------

    /** One pass over online players. Call once per server tick. */
    public static void serverTick(MinecraftServer server) {
        boolean cadence = server.getTickCount() % CADENCE_TICKS == 0;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!player.isAlive() || player.isSpectator()) {
                continue;
            }
            ServerLevel level = levelOf(player);
            tickMirrorShield(level, player);
            tickGreaves(player);
            if (cadence) {
                tickBoots(player);
                tickBulwark(player);
                tickAmulet(player);
            }
        }
    }

    /** Reflects the first incoming projectile, then cools down. */
    static void tickMirrorShield(ServerLevel level, ServerPlayer player) {
        ItemStack shield = find(player, GearKind.MIRROR_SHIELD);
        if (shield == null || onCooldown(player, shield)) {
            return;
        }
        AABB box = player.getBoundingBox().inflate(GearRules.REFLECT_RADIUS);
        Vec3 centre = box.getCenter();
        for (Projectile projectile : level.getEntitiesOfClass(Projectile.class, box, Entity::isAlive)) {
            Vec3 v = projectile.getDeltaMovement();
            Vec3 to = centre.subtract(projectile.position());
            boolean own = projectile.getOwner() == player;
            if (!GearRules.reflectsType(net.minecraft.world.entity.EntityType.getKey(projectile.getType()).toString())
                    || !GearRules.mayReflect(true, false, own,
                            GearRules.isIncoming(v.x, v.y, v.z, to.x, to.y, to.z))) {
                continue;
            }
            projectile.setDeltaMovement(v.scale(-GearRules.REFLECT_SPEED));
            if (GearRules.reflectTakesOwnership(projectile.getOwner() instanceof Player)) {
                projectile.setOwner(player);
            }
            markMoved(projectile);
            ModSounds.MIRROR_SHIELD_REFLECT.play(level, player.getX(), player.getY(), player.getZ(), SoundSource.PLAYERS);
            startCooldown(player, shield, GearSettings.mirrorShieldCooldownTicks.getAsInt());
            return;
        }
    }

    /** Lava walking for a few seconds, then a recharge cooldown. */
    static void tickGreaves(ServerPlayer player) {
        ItemStack greaves = find(player, GearKind.HEAT_GLAND_GREAVES);
        if (greaves == null) {
            return;
        }
        int max = GearSettings.heatGlandGreavesLavaTicks.getAsInt();
        int[] charge = GREAVES_CHARGE.computeIfAbsent(player, p -> new int[] {max});
        boolean inLava = player.isInLava();
        int before = charge[0];
        boolean support = GearRules.greavesSupport(true, inLava, before);
        charge[0] = GearRules.nextCharge(before, inLava, onCooldown(player, greaves), max);
        if (support) {
            player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 40, 0, true, false));
            player.clearFire();
            Vec3 v = player.getDeltaMovement();
            if (v.y < GearRules.GREAVES_LIFT) {
                player.setDeltaMovement(v.x, GearRules.GREAVES_LIFT, v.z);
                markMoved(player);
            }
        }
        if (GearRules.greavesExhausted(before, charge[0])) {
            startCooldown(player, greaves, GearSettings.heatGlandGreavesCooldownTicks.getAsInt());
        }
    }

    /** Lower gravity and a safer fall while active; sneak to fall normally. */
    static void tickBoots(ServerPlayer player) {
        boolean active = GearRules.bootsActive(find(player, GearKind.GRAVITON_BOOTS) != null,
                player.isShiftKeyDown(), player.getAbilities().flying);
        toggle(player.getAttribute(Attributes.GRAVITY), BOOTS_GRAVITY_ID, active,
                GearRules.gravityModifier(GearSettings.gravitonBootsGravityPercent.getAsInt()),
                AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        toggle(player.getAttribute(Attributes.SAFE_FALL_DISTANCE), BOOTS_FALL_ID, active,
                GearRules.BOOTS_SAFE_FALL, AttributeModifier.Operation.ADD_VALUE);
    }

    /** Knockback resistance while held; absorption once per cooldown at low health. */
    static void tickBulwark(ServerPlayer player) {
        ItemStack bulwark = find(player, GearKind.CHITIN_BULWARK);
        toggle(player.getAttribute(Attributes.KNOCKBACK_RESISTANCE), BULWARK_ID, bulwark != null,
                GearRules.BULWARK_KNOCKBACK_RESISTANCE, AttributeModifier.Operation.ADD_VALUE);
        if (bulwark != null && GearRules.bulwarkBraces(true, player.getHealth(), player.getMaxHealth(),
                onCooldown(player, bulwark))) {
            player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, GearRules.BULWARK_ABSORPTION_TICKS, 1));
            levelOf(player).playSound(null, player.getX(), player.getY(), player.getZ(),
                    SoundEvents.ARMOR_EQUIP_NETHERITE.value(), SoundSource.PLAYERS, 0.8F, 0.8F);
            startCooldown(player, bulwark, GearSettings.chitinBulwarkCooldownTicks.getAsInt());
        }
    }

    /** A regeneration pulse once per cooldown when hurt. */
    static void tickAmulet(ServerPlayer player) {
        ItemStack amulet = find(player, GearKind.BLOOM_AMULET);
        if (amulet != null && GearRules.amuletBlooms(true, player.getHealth(), player.getMaxHealth(),
                onCooldown(player, amulet), player.hasEffect(MobEffects.REGENERATION))) {
            player.addEffect(new MobEffectInstance(MobEffects.REGENERATION, GearRules.AMULET_REGEN_TICKS, 0));
            startCooldown(player, amulet, GearSettings.bloomAmuletCooldownTicks.getAsInt());
        }
    }

    /** Without this a client never sees a server-side velocity change. */
    private static void markMoved(Entity entity) {
        //? if >=26.3 {
        entity.syncVelocity = true;
        //?} else {
        /*entity.hurtMarked = true;
        *///?}
    }

    /** Adds or removes one transient attribute modifier. */
    private static void toggle(@Nullable AttributeInstance attribute, Identifier id, boolean on, double amount,
            AttributeModifier.Operation operation) {
        if (attribute == null) {
            return;
        }
        boolean has = CreatureAttributeModifier.has(attribute, id);
        if (on && !has) {
            attribute.addTransientModifier(CreatureAttributeModifier.create(id, amount, operation));
        } else if (!on && has) {
            CreatureAttributeModifier.remove(attribute, id);
        }
    }

    // --- use actions --------------------------------------------------------------------------

    /** Blink Charm: a short raycast teleport; never into a block, an unloaded chunk or past the border. */
    static void blink(ServerLevel level, ServerPlayer player, ItemStack stack) {
        int range = GearRules.clamp(GearSettings.blinkCharmRange.getAsInt(), GearRules.BLINK_RANGE_MIN,
                GearRules.BLINK_RANGE_MAX);
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        HitResult hit = level.clip(new ClipContext(eye, eye.add(look.scale(range)), ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, player));
        double hitDistance = hit.getType() == HitResult.Type.MISS ? range + GearRules.BLINK_WALL_MARGIN
                : hit.getLocation().distanceTo(eye);
        for (double distance : GearRules.blinkSteps(hitDistance, range)) {
            Vec3 offset = look.scale(distance);
            Vec3 feet = player.position().add(offset);
            BlockPos pos = BlockPos.containing(feet);
            if (!GearRules.blinkSpotAllowed(level.isLoaded(pos), level.getWorldBorder().isWithinBounds(pos),
                    level.noCollision(player, player.getBoundingBox().move(offset)))) {
                continue;
            }
            player.teleportTo(feet.x, feet.y, feet.z);
            player.resetFallDistance();
            ModSounds.BLINK_CHARM_USE.play(level, feet.x, feet.y, feet.z, SoundSource.PLAYERS);
            startCooldown(player, stack, GearSettings.blinkCharmCooldownTicks.getAsInt());
            return;
        }
        player.sendSystemMessage(Component.translatable("message.nerocreatures.blink_charm.no_room"), true);
    }

    /** Rime Flask: fire resistance and put out. Reusable on a cooldown. */
    static void drinkRimeFlask(ServerLevel level, ServerPlayer player, ItemStack stack) {
        player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, GearRules.RIME_FLASK_FIRE_RESIST_TICKS, 0));
        player.clearFire();
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.BOTTLE_EMPTY,
                SoundSource.PLAYERS, 0.8F, 1.2F);
        startCooldown(player, stack, GearSettings.rimeFlaskCooldownTicks.getAsInt());
    }

    /**
     * Resonant Whistle: calls the user's own pets and mounts (ownership store rows) that are loaded in
     * the user's own level, within range, nearest first, at most {@value GearRules#WHISTLE_MAX_CALLED}.
     * Sitting, ridden and riding creatures stay put. Never cross-dimension; never anyone else's.
     */
    static void whistle(ServerLevel level, ServerPlayer player, ItemStack stack) {
        UUID user = player.getUUID();
        Identifier here = level.dimension().identifier();
        List<TameableCreature> found = new ArrayList<>();
        List<GearRules.WhistleCandidate> candidates = new ArrayList<>();
        for (OwnedCreature row : CreatureOwnershipState.get(level.getServer()).owned(user)) {
            if (row.kind() != OwnedCreature.Kind.PET && row.kind() != OwnedCreature.Kind.MOUNT) {
                continue;
            }
            boolean sameLevel = here.equals(row.dimension());
            Entity entity = sameLevel ? level.getEntity(row.entity()) : null;
            if (!(entity instanceof TameableCreature creature) || !creature.isAlive()) {
                continue;
            }
            boolean busy = creature.command() == PetCommand.SIT || creature.command() == PetCommand.STAY || creature.isVehicle() || creature.isPassenger();
            candidates.add(new GearRules.WhistleCandidate(found.size(), true, user.equals(creature.ownerId()),
                    creature.distanceToSqr(player), busy));
            found.add(creature);
        }
        int called = 0;
        for (int index : GearRules.whistlePicks(candidates, GearSettings.resonantWhistleRange.getAsInt())) {
            if (callTo(level, player, found.get(index))) {
                called++;
            }
        }
        ModSounds.RESONANT_WHISTLE_USE.play(level, player.getX(), player.getY(), player.getZ(), SoundSource.PLAYERS);
        player.sendSystemMessage(Component.translatable("message.nerocreatures.resonant_whistle.called", called), true);
        startCooldown(player, stack, GearSettings.resonantWhistleCooldownTicks.getAsInt());
    }

    /** Moves one creature to a free, floored spot next to the player; false if there is none. */
    private static boolean callTo(ServerLevel level, ServerPlayer player, TameableCreature creature) {
        BlockPos base = player.blockPosition();
        for (int dy = 0; dy <= 1; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if (Math.abs(dx) < 1 && Math.abs(dz) < 1) {
                        continue;
                    }
                    BlockPos pos = base.offset(dx, dy, dz);
                    BlockPos below = pos.below();
                    if (!level.isLoaded(pos)
                            || level.getBlockState(below).getCollisionShape(level, below).isEmpty()) {
                        continue;
                    }
                    double x = pos.getX() + 0.5D;
                    double z = pos.getZ() + 0.5D;
                    if (!level.noCollision(creature, creature.getType().getSpawnAABB(x, pos.getY(), z))) {
                        continue;
                    }
                    creature.getNavigation().stop();
                    creature.teleportTo(x, pos.getY(), z);
                    return true;
                }
            }
        }
        return false;
    }
}
