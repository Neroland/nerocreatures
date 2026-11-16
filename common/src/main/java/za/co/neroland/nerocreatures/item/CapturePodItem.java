package za.co.neroland.nerocreatures.item;

import java.util.Optional;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.block.CapturedCreature;
import za.co.neroland.nerocreatures.block.NestHome;
import za.co.neroland.nerocreatures.compat.CompanionBridge;
import za.co.neroland.nerocreatures.data.CreatureOwnershipState;
import za.co.neroland.nerocreatures.data.OwnedCreature;
import za.co.neroland.nerocreatures.data.OwnershipCaps;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;
import za.co.neroland.nerocreatures.entity.boss.NeroBossEntity;
import za.co.neroland.nerocreatures.entity.mount.MountCreature;
import za.co.neroland.nerocreatures.entity.tame.LivestockCreature;
import za.co.neroland.nerocreatures.entity.tame.PetCommand;
import za.co.neroland.nerocreatures.entity.tame.TameableCreature;
import za.co.neroland.nerocreatures.registry.ModSounds;

/**
 * {@code capture_pod}: a reusable pod that holds one small tamed or passive creature.
 *
 * <ul>
 *   <li><b>Capture:</b> use an empty pod on a creature in {@code #nerocreatures:capturable} that
 *       passes {@link CaptureRules#mayCapture}. Hostiles, bosses, alphas, mounts and display
 *       creatures are refused, and a tamed creature can only be captured by its own owner (the
 *       entity's owner and the ownership store must agree). Its ownership row is removed.</li>
 *   <li><b>Release:</b> use a filled pod on a block. Vanilla spawn-egg placement and
 *       {@code mayUseItemAt} (claims, spawn protection, world border); never into a wall or an
 *       unloaded chunk. A creature that was tamed is re-owned by the <em>releaser</em> (pet cap
 *       respected); livestock come out husbanded; a wild one comes out wild but persistent.</li>
 *   <li><b>No owner identity is stored</b>: the capture goes through {@link CapturedCreature},
 *       which strips UUID, owner, love cause, leash, mount and position. Only a boolean
 *       {@link CaptureRules#WAS_TAMED_KEY} flag is added. The nest binding (a coordinate) is cleared
 *       before the snapshot.</li>
 *   <li>Stacks to 1 (registration), so one pod is always one creature. The step order lives in
 *       {@link CaptureFlow} and is duplication-tested.</li>
 * </ul>
 *
 * <p>Server-authoritative; the client only swings. Nothing is logged.
 */
public class CapturePodItem extends Item {

    /** Species a pod may take; data-driven. Size is also capped in code ({@link CaptureRules}). */
    public static final TagKey<EntityType<?>> CAPTURABLE = TagKey.create(Registries.ENTITY_TYPE,
            Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "capturable"));

    public CapturePodItem(Item.Properties properties) {
        super(properties);
    }

    /** A filled pod glints. */
    @Override
    public boolean isFoil(ItemStack stack) {
        return CapturedCreature.isFilled(stack) || super.isFoil(stack);
    }

    // --- capture -------------------------------------------------------------------------------

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target,
            InteractionHand hand) {
        if (!(target instanceof Mob mob) || !(target instanceof NeroCreatureEntity || target instanceof TameableCreature)) {
            return InteractionResult.PASS;
        }
        if (!(target.level() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        if (CapturedCreature.isFilled(stack)) {
            notify(player, "full");
            return InteractionResult.SUCCESS_SERVER;
        }
        if (CaptureRules.creativeCopy(player.getAbilities().instabuild, player.getItemInHand(hand) == stack)) {
            notify(player, "creative");
            return InteractionResult.SUCCESS_SERVER;
        }
        CaptureRules.Verdict verdict = CaptureRules.mayCapture(candidate(level, player, mob));
        if (verdict != CaptureRules.Verdict.OK || !(mob instanceof TameableCreature creature)) {
            notify(player, verdict == CaptureRules.Verdict.OK ? "not_capturable" : verdict.key());
            return InteractionResult.SUCCESS_SERVER;
        }
        CaptureFlow.Outcome outcome = CaptureFlow.capture(new StackPod(player, hand, stack),
                new CreatureTarget(level, player, creature));
        if (outcome == CaptureFlow.Outcome.CAPTURED) {
            ModSounds.CAPTURE_POD_CAPTURE.play(level, player.getX(), player.getY(), player.getZ(), SoundSource.PLAYERS);
            notifyRaw(player, Component.translatable("message.nerocreatures.capture_pod.captured",
                    creature.getType().getDescription()));
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    /** What the rules need to know, read on the server. */
    static CaptureRules.Candidate candidate(ServerLevel level, Player player, Mob mob) {
        boolean tamed = mob instanceof TameableCreature t && t.isTame();
        UUID owner = mob instanceof TameableCreature t ? t.ownerId() : null;
        boolean ownerIsUser = owner != null && owner.equals(player.getUUID());
        boolean storeAgrees = ownerIsUser && CreatureOwnershipState.get(level.getServer())
                .loadVerdict(owner, mob.getUUID()) != CreatureOwnershipState.LoadVerdict.RELEASE;
        return new CaptureRules.Candidate(
                mob.isAlive() && !mob.isRemoved(),
                mob.isNoAi() || mob.isInvulnerable() || za.co.neroland.nerocreatures.gallery.GalleryDisplays.isDisplay(mob),
                mob instanceof Enemy,
                mob instanceof NeroBossEntity,
                mob instanceof NeroCreatureEntity creature && creature.isAlpha(),
                mob instanceof MountCreature,
                mob instanceof TameableCreature && mob.getType().builtInRegistryHolder().is(CAPTURABLE),
                mob.getBbWidth(), mob.getBbHeight(),
                mob.isPassenger() || mob.isVehicle() || mob.isLeashed(),
                tamed, ownerIsUser, storeAgrees);
    }

    // --- release -------------------------------------------------------------------------------

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        if (player == null || !CapturedCreature.isFilled(stack)) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level)) {
            return InteractionResult.SUCCESS;
        }
        BlockPos clicked = context.getClickedPos();
        Direction face = context.getClickedFace();
        BlockPos pos = level.getBlockState(clicked).getCollisionShape(level, clicked).isEmpty()
                ? clicked : clicked.relative(face);
        boolean mayUse = player.mayUseItemAt(pos, face, stack);
        CaptureFlow.Outcome outcome = CaptureFlow.release(new StackPod(player, context.getHand(), stack),
                captured -> this.spawn(level, player, pos, mayUse, captured));
        if (outcome == CaptureFlow.Outcome.RELEASED) {
            ModSounds.CAPTURE_POD_RELEASE.play(level, pos, SoundSource.PLAYERS);
        } else if (outcome == CaptureFlow.Outcome.KEPT && CapturedCreature.read(stack).isEmpty()) {
            notify(player, CaptureRules.Release.UNKNOWN.key());
        }
        return InteractionResult.SUCCESS_SERVER;
    }

    /** Puts the captured creature into the world, or refuses (with a message) and changes nothing. */
    private boolean spawn(ServerLevel level, Player player, BlockPos pos, boolean mayUse, CapturedCreature captured) {
        Optional<EntityType<?>> type = captured.understood() ? entityType(captured.species())
                : Optional.empty();
        Entity created = type.isPresent() ? type.get().create(level, EntitySpawnReason.SPAWN_ITEM_USE) : null;
        if (!(created instanceof TameableCreature creature)) {
            notify(player, CaptureRules.Release.UNKNOWN.key());
            return false;
        }
        CreatureCapture.load(creature, captured.entity(), level);
        creature.snapTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, player.getYRot() + 180.0F, 0.0F);
        boolean wasTamed = captured.entity().contains(CaptureRules.WAS_TAMED_KEY);
        OwnedCreature.Kind kind = creature.ownershipKind();
        CreatureOwnershipState state = CreatureOwnershipState.get(level.getServer());
        CaptureRules.Release verdict = CaptureRules.mayRelease(true, true, mayUse,
                level.isLoaded(pos) && level.getWorldBorder().isWithinBounds(pos),
                level.noCollision(creature), wasTamed, state.count(player.getUUID(), kind),
                OwnershipCaps.capFor(kind));
        if (verdict != CaptureRules.Release.OK) {
            if (verdict == CaptureRules.Release.PET_CAP) {
                notifyRaw(player, Component.translatable("message.nerocreatures." + kind.key() + "_cap",
                        OwnershipCaps.capFor(kind)));
            } else {
                notify(player, verdict.key());
            }
            return false;
        }
        if (wasTamed) {
            creature.tame(player);
            creature.setCommand(PetCommand.FOLLOW);
        }
        creature.setPersistenceRequired();
        if (creature instanceof LivestockCreature livestock) {
            livestock.markHusbanded();
        }
        if (!level.addFreshEntity(creature)) {
            return false;
        }
        if (wasTamed) {
            state.add(player.getUUID(), OwnedCreature.of(creature, kind));
            CompanionBridge.hooks().onPetTamed(creature, player);
        }
        return true;
    }

    /** The registered entity type for an id, if any (never the registry's default). */
    private static Optional<EntityType<?>> entityType(String id) {
        Identifier key = Identifier.tryParse(id);
        return key == null ? Optional.empty() : BuiltInRegistries.ENTITY_TYPE.getOptional(key);
    }

    // --- adapters ------------------------------------------------------------------------------

    /** The pod as {@link CaptureFlow} sees it: the exact stack, still in the same hand. */
    private record StackPod(Player player, InteractionHand hand, ItemStack stack)
            implements CaptureFlow.Pod<CapturedCreature> {

        @Override
        public boolean heldAndSingle() {
            return this.player.getItemInHand(this.hand) == this.stack && this.stack.getCount() == 1;
        }

        @Override
        public boolean filled() {
            return CapturedCreature.isFilled(this.stack);
        }

        @Override
        public CapturedCreature contents() {
            return CapturedCreature.read(this.stack).orElse(null);
        }

        @Override
        public void fill(CapturedCreature snapshot) {
            snapshot.write(this.stack);
        }

        @Override
        public void empty() {
            CapturedCreature.clear(this.stack);
        }
    }

    /** The creature as {@link CaptureFlow} sees it. */
    private record CreatureTarget(ServerLevel level, Player player, TameableCreature creature)
            implements CaptureFlow.Target<CapturedCreature> {

        @Override
        public boolean present() {
            return this.creature.isAlive() && !this.creature.isRemoved() && this.creature.level() == this.level;
        }

        @Override
        public CapturedCreature snapshot() {
            NestHome.clear(this.creature); // a nest binding is a coordinate: never carried
            CompoundTag data = CreatureCapture.save(this.creature, this.level);
            if (this.creature.isTame()) {
                data.putBoolean(CaptureRules.WAS_TAMED_KEY, true);
            }
            return CapturedCreature.of(EntityType.getKey(this.creature.getType()).toString(), data,
                    Optional.ofNullable(this.creature.traits()), this.creature.isBaby());
        }

        @Override
        public boolean remove() {
            if (this.creature.isTame()) {
                UUID owner = this.creature.ownerId();
                if (owner != null) {
                    CreatureOwnershipState.get(this.level.getServer()).remove(owner, this.creature.getUUID());
                }
                CompanionBridge.hooks().onPetReleased(this.creature);
            }
            this.creature.discard();
            return this.creature.isRemoved();
        }
    }

    private static void notify(Player player, String key) {
        notifyRaw(player, Component.translatable("message.nerocreatures.capture_pod." + key));
    }

    /** Action-bar feedback to the user only. */
    private static void notifyRaw(Player player, Component message) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(message, true);
        }
    }
}
