package za.co.neroland.nerocreatures.bestiary;

import java.util.Map;
import java.util.WeakHashMap;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import za.co.neroland.nerocreatures.bestiary.BestiaryRules.ScanProgress;
import za.co.neroland.nerocreatures.network.BestiaryEventPayload;
import za.co.neroland.nerocreatures.registry.ModParticles;
import za.co.neroland.nerocreatures.registry.ModSounds;

/**
 * Server side of a held Field Scanner: one {@link ScanProgress} per player, in a transient map
 * weakly keyed by the player object. Never saved, dropped on release, logout and dimension change
 * (a dimension change interrupts the use, and an interrupted use restarts). Not player data.
 */
public final class ScanTracker {

    /** Progress ticks between the scan-beam pulses on the target. */
    private static final int PULSE_TICKS = 8;

    private static final Map<ServerPlayer, ScanProgress> SCANS = new WeakHashMap<>();
    private static final Map<ServerPlayer, Integer> REFUSED = new WeakHashMap<>();

    private ScanTracker() {
    }

    /** One server tick of a held scanner. Returns {@code true} when the scan completed. */
    public static boolean tick(ServerPlayer player) {
        ScanTargets.Target target = ScanTargets.find(player);
        refuse(player, target);
        ScanProgress progress = SCANS.computeIfAbsent(player, p -> new ScanProgress());
        int before = progress.target();
        ScanProgress.Step step = progress.tick(player.level().getGameTime(), target.entityId());
        switch (step) {
            case COMPLETE -> {
                if (target.entry() != null && target.entity() != null) {
                    BestiaryProgress.scan(player, target.entry());
                    pulseLine(player, target.entity());
                    ModSounds.FIELD_SCANNER_SCAN.play(player.level(), player.getX(), player.getY(), player.getZ(),
                            SoundSource.PLAYERS);
                }
                return true;
            }
            case LOST -> {
                if (before >= 0) {
                    BestiaryProgress.send(player, BestiaryEventPayload.Kind.LOST, "");
                }
            }
            case PROGRESS -> {
                if (target.entity() != null && progress.ticks() % PULSE_TICKS == 0 && progress.ticks() > 0) {
                    pulse(player, target.entity());
                }
            }
            default -> {
            }
        }
        return false;
    }

    /** Use released or interrupted: forget the scan. */
    public static void reset(ServerPlayer player) {
        SCANS.remove(player);
        REFUSED.remove(player);
    }

    /** A gallery display or a non-Bestiary creature is reported once per target, not every tick. */
    private static void refuse(ServerPlayer player, ScanTargets.Target target) {
        Entity entity = target.entity();
        if (entity == null || target.kind() == ScanTargets.Kind.VALID) {
            REFUSED.remove(player);
            return;
        }
        Integer last = REFUSED.get(player);
        if (last != null && last == entity.getId()) {
            return;
        }
        REFUSED.put(player, entity.getId());
        BestiaryProgress.send(player, target.kind() == ScanTargets.Kind.GALLERY
                ? BestiaryEventPayload.Kind.REFUSED_GALLERY : BestiaryEventPayload.Kind.REFUSED_NOT_LISTED, "");
    }

    private static void pulse(ServerPlayer player, Entity target) {
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ModParticles.SCAN_BEAM.get(), target.getX(), target.getY() + target.getBbHeight() * 0.5D,
                    target.getZ(), 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private static void pulseLine(ServerPlayer player, Entity target) {
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 from = player.getEyePosition().add(0.0D, -0.3D, 0.0D);
        Vec3 to = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        for (int i = 1; i <= 5; i++) {
            Vec3 at = from.lerp(to, i / 6.0D);
            level.sendParticles(ModParticles.SCAN_BEAM.get(), at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }
}
