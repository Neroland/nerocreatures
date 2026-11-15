package za.co.neroland.nerocreatures.client.particle;

import java.util.Map;

/**
 * Per-particle tuning from {@code art/particles/MANIFEST.md}: lifetime, quad size (start and end),
 * gravity, per-tick velocity friction, full-bright, and whether frames play once by age. These are
 * starting values for tuning in game; the provider reads only this table.
 */
public record ParticleStyle(int lifeMin, int lifeMax, float sizeStart, float sizeEnd, float sizeJitter, float gravity,
        float friction, boolean fullBright, float sway) {

    private static final ParticleStyle DEFAULT = new ParticleStyle(10, 14, 0.12F, 0.12F, 0.0F, 0.0F, 0.96F, true, 0.0F);

    private static final Map<String, ParticleStyle> STYLES = Map.ofEntries(
            Map.entry("lumen_mote", new ParticleStyle(24, 36, 0.10F, 0.10F, 0.04F, -0.01F, 0.92F, true, 0.0F)),
            Map.entry("echo_ring", new ParticleStyle(12, 16, 0.25F, 0.60F, 0.0F, 0.0F, 0.0F, true, 0.0F)),
            Map.entry("nodule_glint", new ParticleStyle(10, 14, 0.12F, 0.12F, 0.0F, 0.0F, 0.0F, true, 0.0F)),
            Map.entry("dust_swirl", new ParticleStyle(14, 20, 0.15F, 0.15F, 0.07F, -0.004F, 0.90F, false, 0.0F)),
            Map.entry("static_spark", new ParticleStyle(5, 8, 0.10F, 0.10F, 0.06F, 0.0F, 0.80F, true, 0.0F)),
            Map.entry("slag_ember", new ParticleStyle(16, 28, 0.08F, 0.08F, 0.04F, 0.03F, 0.96F, true, 0.0F)),
            Map.entry("rime_mote", new ParticleStyle(30, 50, 0.08F, 0.08F, 0.04F, 0.003F, 0.96F, false, 0.01F)),
            Map.entry("spore_mote", new ParticleStyle(30, 40, 0.06F, 0.04F, 0.03F, 0.02F, 0.96F, true, 0.004F)),
            Map.entry("spore_puff", new ParticleStyle(12, 16, 0.20F, 0.35F, 0.0F, 0.0F, 0.90F, true, 0.0F)),
            Map.entry("bloom_mote", new ParticleStyle(20, 26, 0.12F, 0.12F, 0.04F, -0.006F, 0.96F, true, 0.0F)),
            Map.entry("prism_glint", new ParticleStyle(8, 12, 0.12F, 0.12F, 0.06F, 0.0F, 0.90F, true, 0.0F)),
            Map.entry("magnet_spark", new ParticleStyle(8, 10, 0.14F, 0.14F, 0.0F, 0.0F, 0.90F, true, 0.0F)),
            Map.entry("gravity_ripple", new ParticleStyle(10, 14, 0.50F, 0.20F, 0.0F, 0.01F, 0.90F, true, 0.0F)),
            Map.entry("void_trail", new ParticleStyle(10, 16, 0.12F, 0.12F, 0.06F, 0.0F, 0.80F, true, 0.0F)),
            Map.entry("void_blink", new ParticleStyle(8, 10, 0.50F, 0.30F, 0.0F, 0.0F, 0.0F, true, 0.0F)),
            Map.entry("scan_beam", new ParticleStyle(10, 12, 0.30F, 0.30F, 0.20F, 0.0F, 0.0F, true, 0.0F)));

    public static ParticleStyle of(String id) {
        return STYLES.getOrDefault(id, DEFAULT);
    }

    /** Ids with a tuned style (tests compare this with the registered particle list). */
    public static java.util.Set<String> ids() {
        return STYLES.keySet();
    }
}
