package za.co.neroland.nerocreatures.client;

import net.minecraft.resources.Identifier;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/**
 * What the renderer needs to know about one creature beyond its model: texture set, render type,
 * scale and shadow. Generated models carry theirs as {@code ART} (written by
 * {@code tools/gen_entity_models.py} from the spec and the textures present); the hand-written
 * models that predate the art pipeline use {@link #legacy}.
 *
 * @param id          registry path; textures are {@code textures/entity/<id>[_glow|_radiant].png}
 * @param translucent spec {@code render_type} is {@code translucent}
 * @param scale       uniform renderer scale on top of the model geometry
 * @param shadow      shadow radius in blocks
 * @param glow        a {@code <id>_glow.png} exists, so the glow layer is drawn
 * @param radiant     a {@code <id>_radiant.png} exists, so the Radiant flag switches texture
 */
public record CreatureArt(String id, boolean translucent, float scale, float shadow, boolean glow,
        boolean radiant) {

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

    private static Identifier entityTexture(String name) {
        return Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "textures/entity/" + name + ".png");
    }
}
