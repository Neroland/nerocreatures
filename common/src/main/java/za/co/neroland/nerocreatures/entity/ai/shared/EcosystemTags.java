package za.co.neroland.nerocreatures.entity.ai.shared;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/**
 * Entity-type tags that drive the ecosystem goals. Data, not code: packs can add their own
 * grazers, moths and predators. Ids not yet registered are listed with {@code "required": false},
 * so an empty or partial tag is normal.
 */
public final class EcosystemTags {

    /** Grazers: hunted by Lunar Stalkers and Frost Lurkers. */
    public static final TagKey<EntityType<?>> PREY_GRAZERS = create("prey/grazers");
    /** Moths: hunted by Echo Shrikes. */
    public static final TagKey<EntityType<?>> PREY_MOTHS = create("prey/moths");
    /** Everything prey herds flee from. */
    public static final TagKey<EntityType<?>> PREDATORS = create("predators");

    private EcosystemTags() {
    }

    private static TagKey<EntityType<?>> create(String path) {
        return TagKey.create(Registries.ENTITY_TYPE, Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, path));
    }
}
