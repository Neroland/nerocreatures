package za.co.neroland.nerocreatures.platform;

import java.util.List;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLEnvironment;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/**
 * NeoForge implementation of {@link PlatformInfo}. Registered via
 * {@code META-INF/services/za.co.neroland.nerocreatures.platform.PlatformInfo}.
 */
public final class NeoForgePlatformInfo implements PlatformInfo {
    //? if <1.20.5 {
    /*@Override
    public net.minecraft.world.entity.ai.attributes.Attribute gravityAttribute() {
        return net.neoforged.neoforge.common.NeoForgeMod.ENTITY_GRAVITY.value();
    }
    *///?}


    @Override
    public String getPlatformName() {
        return "NeoForge";
    }

    @Override
    public boolean isDevelopmentEnvironment() {
        // 26.1.x exposes these as methods (the old `FMLEnvironment.production` / `.dist` fields are gone).
        //? if >=1.21.9 {
        return !FMLEnvironment.isProduction();
        //?} else {
        /*return !FMLEnvironment.production;
        *///?}
    }

    @Override
    public boolean isClient() {
        //? if >=1.21.9 {
        return FMLEnvironment.getDist() == Dist.CLIENT;
        //?} else {
        /*return FMLEnvironment.dist == Dist.CLIENT;
        *///?}
    }

    @Override
    public String getModVersion() {
        return ModList.get().getModContainerById(NeroCreaturesCommon.MOD_ID)
                .map(c -> c.getModInfo().getVersion().toString())
                .orElse("unknown");
    }

    @Override
    public List<String> getLoadedModIds() {
        return ModList.get().getMods().stream()
                .map(m -> m.getModId() + " " + m.getVersion())
                .sorted()
                .toList();
    }

    /** Fires {@code EntityMobGriefingEvent} (defaults to the {@code mobGriefing} game rule). */
    @Override
    public boolean canMobGrief(ServerLevel level, Mob actor) {
        //? if >=1.20.6 {
        return EventHooks.canEntityGrief(level, actor);
        //?} else {
        /*return EventHooks.getMobGriefingEvent(level, actor);
        *///?}
    }

    /**
     * Fires {@code BlockEvent.EntityPlaceEvent} for the position as a pre-check (claim mods cancel
     * it). Only called for loaded positions, so the snapshot never loads a chunk.
     */
    @Override
    public boolean mayPlace(ServerLevel level, Mob actor, BlockPos pos, @Nullable BlockState newState) {
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);
        return !EventHooks.onBlockPlace(actor, snapshot, Direction.UP);
    }
}
