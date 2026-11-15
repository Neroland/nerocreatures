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
 * <p><b>Egg rows</b> ({@link Kind#EGG}, decision 004) reuse the same shape: {@code entity} is a random
 * incubation id (not an entity), {@code type} the species that will hatch, {@code dimension} the
 * incubator's dimension, and {@code hatchAt} the game time it hatches. No position is stored for
 * eggs either; the incubator block entity holds the incubation id, never the player.
 *
 * @param entity    the owned creature's entity UUID (a game-internal id, not a player id), or an
 *                  egg row's random incubation id
 * @param kind      pet, drone, mount or egg: what is capped and erased differently
 * @param type      the entity-type id, e.g. {@code nerocreatures:glacite_wisp}
 * @param dimension the dimension id the creature was last registered in
 * @param hatchAt   egg rows only: game time the egg hatches; {@code 0} for every other kind (and
 *                  then not written to disk, so pet and drone rows encode exactly as in 0.4.x)
 */
public record OwnedCreature(UUID entity, Kind kind, Identifier type, Identifier dimension, long hatchAt) {

    /** A row with no hatch time: every kind except {@link Kind#EGG}. */
    public OwnedCreature(UUID entity, Kind kind, Identifier type, Identifier dimension) {
        this(entity, kind, type, dimension, 0L);
    }

    /** What an owned creature is, which decides both its cap and what erasure does to it. */
    public enum Kind {

        /** A tamed alien pet — capped by {@code maxPetsPerPlayer}; erasure returns it to the wild. */
        PET,

        /** A deployed terraforming drone — capped by {@code maxDronesPerPlayer}; erasure recalls it. */
        DRONE,

        /**
         * A tamed mount (0.5.0) — capped by {@code maxMountsPerPlayer} and not by the pet cap; erasure
         * unsaddles it, drops its gear and returns it to the wild. Append-only: never remove.
         */
        MOUNT,

        /**
         * An egg incubating for a player (0.5.0) — capped by {@code maxIncubatingEggsPerPlayer};
         * erasure simply drops the row and the egg hatches unattributed. Append-only: never remove.
         */
        EGG;

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
            Identifier.CODEC.fieldOf("dimension").forGetter(OwnedCreature::dimension),
            // Optional with a 0 default: absent in every 0.4.x row, and omitted again on encode for
            // every non-egg row, so old saves load and re-save byte-for-byte the same shape.
            Codec.LONG.optionalFieldOf("hatch_at", 0L).forGetter(OwnedCreature::hatchAt)
    ).apply(instance, OwnedCreature::new));

    /** Describes a live entity as a store row. */
    public static OwnedCreature of(Entity entity, Kind kind) {
        return new OwnedCreature(entity.getUUID(), kind,
                EntityType.getKey(entity.getType()), entity.level().dimension().identifier());
    }

    /** An egg row: a fresh random incubation id, the species that will hatch and its hatch time. */
    public static OwnedCreature egg(Identifier species, Identifier dimension, long hatchAt) {
        return new OwnedCreature(UUID.randomUUID(), Kind.EGG, species, dimension, hatchAt);
    }

    /** The dimension this row points at, as a level key. */
    public ResourceKey<Level> dimensionKey() {
        return ResourceKey.create(Registries.DIMENSION, this.dimension);
    }
}
