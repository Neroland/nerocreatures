package za.co.neroland.nerocreatures.client.particle;

import net.minecraft.core.particles.SimpleParticleType;

import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerocreatures.registry.ModParticles;

/** The client side of {@link ModParticles}: one {@link CreatureParticle.Provider} per type, through a loader sink. */
public final class CreatureParticles {

    /** Each loader's provider registration call ({@code registerSpriteSet} or Fabric's registry). */
    public interface Sink {
        void register(SimpleParticleType type, String id);
    }

    private CreatureParticles() {
    }

    public static void registerAll(Sink sink) {
        for (RegistryEntry<SimpleParticleType> entry : ModParticles.all()) {
            SimpleParticleType type = entry.get();
            String id = net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.getKey(type).getPath();
            sink.register(type, id);
        }
    }
}
