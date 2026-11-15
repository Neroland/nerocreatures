package za.co.neroland.nerocreatures.sound;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/**
 * One registered NeroCreatures sound event plus the pitch range its manifest row asks for
 * ({@code tools/sound/manifest.json}). Volume and hearing range live in {@code sounds.json}
 * ({@code volume}, {@code attenuation_distance}); the event is fixed-range so the server sends it
 * exactly as far as the client can hear it.
 *
 * <p>The {@code play} helpers are server-side: they broadcast to every nearby client (no player is
 * excluded) and do nothing on a client level, so callers never need a side check.
 */
public final class CreatureSound {

    private final RegistryEntry<SoundEvent> event;
    private final float pitchMin;
    private final float pitchMax;

    private CreatureSound(RegistryEntry<SoundEvent> event, float pitchMin, float pitchMax) {
        this.event = event;
        this.pitchMin = pitchMin;
        this.pitchMax = pitchMax;
    }

    /** Registers {@code nerocreatures:<id>} with a fixed hearing range of {@code range} blocks. */
    public static CreatureSound register(RegistrationProvider<SoundEvent> sounds, String id, int range,
            float pitchMin, float pitchMax) {
        Identifier key = Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, id);
        return new CreatureSound(sounds.register(id, k -> SoundEvent.createFixedRangeEvent(key, range)),
                pitchMin, pitchMax);
    }

    /** The registered event (for {@code getAmbientSound} and friends). */
    public SoundEvent get() {
        return this.event.get();
    }

    /** A pitch drawn uniformly from the manifest range. */
    public float pitch(RandomSource random) {
        return this.pitchMin + random.nextFloat() * (this.pitchMax - this.pitchMin);
    }

    /** Plays at the entity, in the entity's own sound category (hostile / neutral / player...). */
    public void play(Entity entity) {
        Level level = entity.level();
        if (!level.isClientSide()) {
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), this.get(), entity.getSoundSource(),
                    1.0F, this.pitch(level.getRandom()));
        }
    }

    /** Plays at a block position in the given category. */
    public void play(Level level, BlockPos pos, SoundSource source) {
        if (!level.isClientSide()) {
            level.playSound(null, pos, this.get(), source, 1.0F, this.pitch(level.getRandom()));
        }
    }

    /** Plays at an exact position in the given category. */
    public void play(Level level, double x, double y, double z, SoundSource source) {
        if (!level.isClientSide()) {
            level.playSound(null, x, y, z, this.get(), source, 1.0F, this.pitch(level.getRandom()));
        }
    }
}
