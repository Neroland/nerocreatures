package za.co.neroland.nerocreatures.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleType;

import za.co.neroland.nerolandcore.registry.RegistrationProvider;
import za.co.neroland.nerolandcore.registry.RegistrationProvider.RegistryEntry;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/**
 * NeroCreatures' 16 custom particle types ({@code art/particles/MANIFEST.md}). All are
 * simple types with no options, so the registration is the same on every Minecraft line. Each has a
 * definition in {@code assets/nerocreatures/particles/<id>.json} and is drawn by the client's one
 * {@code CreatureParticle} provider, tuned per id by {@code ParticleStyle}.
 */
public final class ModParticles {

    public static final RegistrationProvider<ParticleType<?>> PARTICLES =
            RegistrationProvider.get(Registries.PARTICLE_TYPE, NeroCreaturesCommon.MOD_ID);

    private static final List<RegistryEntry<SimpleParticleType>> ALL = new ArrayList<>();

    public static final RegistryEntry<SimpleParticleType> LUMEN_MOTE = simple("lumen_mote");
    public static final RegistryEntry<SimpleParticleType> ECHO_RING = simple("echo_ring");
    public static final RegistryEntry<SimpleParticleType> NODULE_GLINT = simple("nodule_glint");
    public static final RegistryEntry<SimpleParticleType> DUST_SWIRL = simple("dust_swirl");
    public static final RegistryEntry<SimpleParticleType> STATIC_SPARK = simple("static_spark");
    public static final RegistryEntry<SimpleParticleType> SLAG_EMBER = simple("slag_ember");
    public static final RegistryEntry<SimpleParticleType> RIME_MOTE = simple("rime_mote");
    public static final RegistryEntry<SimpleParticleType> SPORE_MOTE = simple("spore_mote");
    public static final RegistryEntry<SimpleParticleType> SPORE_PUFF = simple("spore_puff");
    public static final RegistryEntry<SimpleParticleType> BLOOM_MOTE = simple("bloom_mote");
    public static final RegistryEntry<SimpleParticleType> PRISM_GLINT = simple("prism_glint");
    public static final RegistryEntry<SimpleParticleType> MAGNET_SPARK = simple("magnet_spark");
    public static final RegistryEntry<SimpleParticleType> GRAVITY_RIPPLE = simple("gravity_ripple");
    public static final RegistryEntry<SimpleParticleType> VOID_TRAIL = simple("void_trail");
    public static final RegistryEntry<SimpleParticleType> VOID_BLINK = simple("void_blink");
    public static final RegistryEntry<SimpleParticleType> SCAN_BEAM = simple("scan_beam");

    private ModParticles() {
    }

    private static RegistryEntry<SimpleParticleType> simple(String id) {
        // SimpleParticleType's constructor is protected on every line; an empty subclass is the
        // portable way to make one without a loader helper.
        RegistryEntry<SimpleParticleType> entry = PARTICLES.register(id, key -> new SimpleParticleType(false) { });
        ALL.add(entry);
        return entry;
    }

    /** Forces this class to initialise (and therefore its types to register on Fabric). */
    public static void init() {
    }

    /** Every particle type, in manifest order (the client registers one provider per entry). */
    public static List<RegistryEntry<SimpleParticleType>> all() {
        return Collections.unmodifiableList(ALL);
    }
}
