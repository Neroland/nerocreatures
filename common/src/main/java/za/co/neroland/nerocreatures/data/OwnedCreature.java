package za.co.neroland.nerocreatures.data;

import java.util.Locale;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * One row of {@link CreatureOwnershipState}: a single creature a player owns.
 *
 * <p><b>The whole schema is here, and it is deliberately this small.</b> A row is the entity's own
 * game UUID, what kind of thing it is, its entity-type id and the id of the dimension it was last
 * registered in — enough to find the creature again for a cap check, a recall or an erasure, and
 * nothing more. In particular there are <b>no coordinates and no position history</b>: knowing where
 * a player's pets have been is player-location data, and NeroCreatures does not keep it
 * (POPIA/GDPR). The dimension id is stored purely so erasure knows which level to look in rather
 * than having to walk every level in the save.
 *
 * @param entity    the owned creature's entity UUID (a game-internal id, not a player id)
 * @param kind      pet or drone — the two things that are capped and erased differently
 * @param type      the entity-type id, e.g. {@code nerocreatures:glacite_wisp}
 * @param dimension the dimension id the creature was last registered in
 */
public record OwnedCreature(UUID entity, Kind kind, Identifier type, Identifier dimension) {

    /** What an owned creature is, which decides both its cap and what erasure does to it. */
    public enum Kind {

        /** A tamed alien pet — capped by {@code maxPetsPerPlayer}; erasure returns it to the wild. */
        PET,

        /** A deployed terraforming drone — capped by {@code maxDronesPerPlayer}; erasure recalls it. */
        DRONE;

        /** Lenient string codec: an unrecognised value reads back as {@link #PET}. */
        public static final Codec<Kind> CODEC = Codec.STRING.xmap(Kind::fromKey, Kind::key);

        public String key() {
            return this.name().toLowerCase(Locale.ROOT);
        }

        static Kind fromKey(String key) {
            for (Kind kind : values()) {
                if (kind.key().equalsIgnoreCase(key)) {
                    return kind;
                }
            }
            return PET;
        }
    }

    /**
     * UUIDs are stored as plain strings. The error message deliberately does not echo the value: a
     * malformed row is reported as malformed, not quoted back into the log.
     */
    private static final Codec<UUID> UUID_CODEC = Codec.STRING.comapFlatMap(
            text -> {
                try {
                    return DataResult.success(UUID.fromString(text));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Malformed UUID in the NeroCreatures ownership store");
                }
            },
            UUID::toString);

    public static final Codec<OwnedCreature> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            UUID_CODEC.fieldOf("entity").forGetter(OwnedCreature::entity),
            Kind.CODEC.optionalFieldOf("kind", Kind.PET).forGetter(OwnedCreature::kind),
            Identifier.CODEC.fieldOf("type").forGetter(OwnedCreature::type),
            Identifier.CODEC.fieldOf("dimension").forGetter(OwnedCreature::dimension)
    ).apply(instance, OwnedCreature::new));

    /** Describes a live entity as a store row. */
    public static OwnedCreature of(Entity entity, Kind kind) {
        return new OwnedCreature(entity.getUUID(), kind,
                EntityType.getKey(entity.getType()), entity.level().dimension().identifier());
    }

    /** The dimension this row points at, as a level key. */
    public ResourceKey<Level> dimensionKey() {
        return ResourceKey.create(Registries.DIMENSION, this.dimension);
    }
}
