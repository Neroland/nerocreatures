package za.co.neroland.nerocreatures.client;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/**
 * What the renderer needs to know about one creature beyond its model: texture set, render type,
 * scale, shadow, per-variant look and render-culling box. Generated models carry theirs as
 * {@code ART} (written by {@code tools/gen_entity_models.py} from the spec and the textures present);
 * the hand-written models that predate the art pipeline use {@link #legacy}.
 *
 * @param id          registry path; textures are {@code textures/entity/<id>[_glow|_radiant|_v<n>].png}
 * @param translucent spec {@code render_type} is {@code translucent}
 * @param scale       uniform renderer scale on top of the model geometry
 * @param shadow      shadow radius in blocks
 * @param glow        a {@code <id>_glow.png} exists, so the glow layer is drawn
 * @param radiant     a {@code <id>_radiant.png} exists, so the Radiant flag switches texture
 * @param variants    spec {@code variants}: body tint, glow tint and texture per synced variant
 * @param cull        the box the model can reach, for render culling (spec {@code renderer.cull}, or
 *                    measured from the geometry and clips)
 */
public record CreatureArt(String id, boolean translucent, float scale, float shadow, boolean glow,
        boolean radiant, Variants variants, Cull cull) {

    /** No variant look and the vanilla culling box. */
    public CreatureArt(String id, boolean translucent, float scale, float shadow, boolean glow, boolean radiant) {
        this(id, translucent, scale, shadow, glow, radiant, Variants.NONE, Cull.NONE);
    }

    /** A pre-pipeline creature: base texture only, cutout. */
    public static CreatureArt legacy(String id, float scale, float shadow) {
        return new CreatureArt(id, false, scale, shadow, false, false);
    }

    public Identifier texture() {
        return entityTexture(id);
    }

    public Identifier glowTexture() {
        return entityTexture(id + "_glow");
    }

    public Identifier radiantTexture() {
        return entityTexture(id + "_radiant");
    }

    /** {@code <id>_v<variant>.png}; only meaningful where {@link Variants#texture} says it exists. */
    public Identifier variantTexture(int variant) {
        return entityTexture(id + "_v" + variant);
    }

    /** Channel-wise product of two ARGB colours (-1 is white). */
    public static int multiplyArgb(int a, int b) {
        int out = 0;
        for (int shift = 0; shift < 32; shift += 8) {
            int x = (a >>> shift) & 0xFF;
            int y = (b >>> shift) & 0xFF;
            out |= ((x * y + 127) / 255) << shift;
        }
        return out;
    }

    private static Identifier entityTexture(String name) {
        return Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "textures/entity/" + name + ".png");
    }

    /**
     * The look of each synced {@code variant} 0-7 (spec {@code variants}).
     *
     * @param bodyTints ARGB multiplied into the base texture per variant, -1 for none (null: none at all)
     * @param glowTints RGB for the glow layer per variant, -1 for the default (null: none at all)
     * @param textures  bit {@code n} set when variant {@code n} has its own {@code <id>_v<n>.png}
     */
    public record Variants(int[] bodyTints, int[] glowTints, int textures) {

        public static final Variants NONE = new Variants(null, null, 0);

        public int bodyTint(int variant) {
            return bodyTints == null || variant < 0 || variant >= bodyTints.length ? -1 : bodyTints[variant];
        }

        public int glowTint(int variant) {
            return glowTints == null || variant < 0 || variant >= glowTints.length ? -1 : glowTints[variant];
        }

        public boolean texture(int variant) {
            return variant >= 0 && variant < 8 && (textures & (1 << variant)) != 0;
        }
    }

    /**
     * The space the model can reach around the entity's position, in blocks before the entity's own
     * scale: {@code radius} horizontally (any yaw), {@code up} above the feet and {@code down} below.
     * The renderer also draws the creature while this box is in view, so a model larger than its
     * hitbox does not pop out at the screen edges. {@link #NONE}: the vanilla hitbox only.
     */
    public record Cull(float radius, float up, float down) {

        public static final Cull NONE = new Cull(0.0F, 0.0F, 0.0F);

        public boolean active() {
            return radius > 0.0F || up > 0.0F || down > 0.0F;
        }
    }
}
