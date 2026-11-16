package za.co.neroland.nerocreatures.link;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import net.minecraft.resources.Identifier;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerolandcore.link.LinkActionResult;

import za.co.neroland.nerocreatures.bestiary.BestiaryCatalog;
import za.co.neroland.nerocreatures.bestiary.BestiaryCatalog.Entry;
import za.co.neroland.nerocreatures.bestiary.BestiaryRules.Tier;
import za.co.neroland.nerocreatures.boss.AltarUses;
import za.co.neroland.nerocreatures.boss.BossContributionState;
import za.co.neroland.nerocreatures.data.CreatureOwnershipState;
import za.co.neroland.nerocreatures.data.OwnedCreature;
import za.co.neroland.nerocreatures.entity.tame.PetCommand;

/**
 * Link schema 2: only the asking player's data, schema-1 members kept, no coordinates anywhere, the
 * per-player rate limit, and strict command parsing.
 */
class CreatureLinkSchemaTest {

    private static final UUID ALICE = UUID.fromString("00000000-0000-4000-8000-00000000a11c");
    private static final UUID BOB = UUID.fromString("00000000-0000-4000-8000-000000000b0b");
    private static final Identifier MOTH = Identifier.fromNamespaceAndPath("nerocreatures", "lumen_moth");
    private static final Identifier LOPER = Identifier.fromNamespaceAndPath("nerocreatures", "dune_loper");
    private static final Identifier HOUND = Identifier.fromNamespaceAndPath("nerocreatures", "strata_hound");
    private static final Identifier DRONE = Identifier.fromNamespaceAndPath("nerocreatures", "terraforming_drone");
    private static final Identifier MATRIARCH = Identifier.fromNamespaceAndPath("nerocreatures", "rime_matriarch");
    private static final Identifier LUNA = Identifier.fromNamespaceAndPath("nerospace", "luna");
    private static final Identifier MARS = Identifier.fromNamespaceAndPath("nerospace", "mars");

    /** Members the app's parser never reads and no section may carry. */
    private static final Set<String> FORBIDDEN = Set.of("x", "y", "z", "pos", "position", "block_pos", "blockpos",
            "coords", "coordinates", "location", "owner", "owner_id", "player", "players", "player_id", "player_name",
            "names", "participant_ids", "roster");

    private static final Pattern COORDINATE_TRIPLE = Pattern.compile("-?\\d+(\\.\\d+)?\\s*[, ]\\s*-?\\d+(\\.\\d+)?\\s*[, ]\\s*-?\\d+(\\.\\d+)?");

    // --- fixtures --------------------------------------------------------------------

    private record Bestiary(boolean online, Map<String, Tier> tiers, Map<String, Long> kills, Map<String, Integer> owned)
            implements CreatureLinkJson.BestiaryData {
        @Override
        public Tier tier(Entry entry) {
            return this.tiers.getOrDefault(entry.id(), Tier.NONE);
        }

        @Override
        public long kills(Entry entry) {
            return this.kills.getOrDefault(entry.id(), 0L);
        }

        @Override
        public int owned(Entry entry) {
            return this.owned.getOrDefault(entry.id(), 0);
        }

        @Override
        public int goal(Entry entry) {
            return 5;
        }

        @Override
        public JsonArray spawns(Entry entry) {
            JsonArray out = new JsonArray();
            JsonObject row = new JsonObject();
            row.addProperty("biome_tag", "nerocreatures:habitat/" + entry.habitat().id());
            row.addProperty("weight", 10);
            row.addProperty("group_min", 1);
            row.addProperty("group_max", 3);
            out.add(row);
            return out;
        }
    }

    private static Bestiary alicesBestiary() {
        return new Bestiary(true,
                new HashMap<>(Map.of("lumen_moth", Tier.STUDIED, "echo_shrike", Tier.SCANNED, "rime_matriarch", Tier.SCANNED)),
                new HashMap<>(Map.of("echo_shrike", 3L, "lunar_stalker", 2L)), new HashMap<>(Map.of("lumen_moth", 1)));
    }

    private static CreatureLinkJson.Live loaded(String command) {
        return new CreatureLinkJson.Live(true, 12.0F, 20.0F, command, List.of("hardy", "radiant"), true, 0.8D, false,
                240L, -1L);
    }

    private static final class World {
        final CreatureOwnershipState ownership = new CreatureOwnershipState();
        final BossContributionState contribution = new BossContributionState();
        final AltarUses.Table altars = new AltarUses.Table();
        final UUID aliceMoth = UUID.randomUUID();
        final UUID aliceLoper = UUID.randomUUID();
        final UUID bobHound = UUID.randomUUID();
        final UUID bobDrone = UUID.randomUUID();
        final UUID sharedFight = UUID.randomUUID();
        final UUID bobsFight = UUID.randomUUID();
        final OwnedCreature aliceEgg = OwnedCreature.egg(MOTH, LUNA, 24_000L);

        World() {
            this.ownership.add(ALICE, new OwnedCreature(this.aliceMoth, OwnedCreature.Kind.PET, MOTH, LUNA));
            this.ownership.add(ALICE, new OwnedCreature(this.aliceLoper, OwnedCreature.Kind.MOUNT, LOPER, MARS));
            this.ownership.add(ALICE, this.aliceEgg);
            this.ownership.add(BOB, new OwnedCreature(this.bobHound, OwnedCreature.Kind.PET, HOUND, MARS));
            this.ownership.add(BOB, new OwnedCreature(this.bobDrone, OwnedCreature.Kind.DRONE, DRONE, MARS));
            this.contribution.record(this.sharedFight, MATRIARCH, ALICE, 30.0F);
            this.contribution.record(this.sharedFight, MATRIARCH, BOB, 90.0F);
            this.contribution.record(this.bobsFight, MATRIARCH, BOB, 50.0F);
            this.altars.noteSummon(ALICE, LUNA, 123_456_789L, 2_000L, this.sharedFight, MATRIARCH.toString());
            this.altars.noteSummon(BOB, MARS, 987_654_321L, 3_000L, this.bobsFight, MATRIARCH.toString());
            this.altars.noteSummon(BOB, MARS, 555L, 3_000L, null, MATRIARCH.toString());
        }

        JsonObject pets(UUID player) {
            return CreatureLinkJson.pets(this.ownership, player, row -> row.kind() == OwnedCreature.Kind.EGG
                    ? CreatureLinkJson.Live.egg(250L)
                    : row.kind() == OwnedCreature.Kind.DRONE ? CreatureLinkJson.Live.UNLOADED : loaded("guard"),
                    true, kind -> 3);
        }

        JsonObject bosses(UUID player) {
            List<CreatureLinkJson.Fight> fights = List.of(
                    new CreatureLinkJson.Fight(this.sharedFight, MATRIARCH, "nerospace:glacira", 2, 3, 0.42F),
                    new CreatureLinkJson.Fight(this.bobsFight, MATRIARCH, "nerospace:glacira", 1, 3, 0.9F));
            List<AltarUses.Row> rows = this.altars.rows(player, 1_000L,
                    (dimension, pos, fight, readyAt) -> new AltarUses.Probe(true, false, readyAt, false));
            return CreatureLinkJson.bosses(fights, this.contribution, player, rows, true);
        }
    }

    private static void walk(JsonElement element, String path, List<String> keys, List<String> strings) {
        if (element.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet()) {
                keys.add(entry.getKey());
                walk(entry.getValue(), path + "." + entry.getKey(), keys, strings);
            }
        } else if (element.isJsonArray()) {
            for (JsonElement child : element.getAsJsonArray()) {
                walk(child, path + "[]", keys, strings);
            }
        } else if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
            strings.add(element.getAsString());
        }
    }

    private static void assertNoPlaceAndNoPlayer(JsonObject json, String what) {
        List<String> keys = new ArrayList<>();
        List<String> strings = new ArrayList<>();
        walk(json, what, keys, strings);
        for (String key : keys) {
            assertFalse(FORBIDDEN.contains(key.toLowerCase(java.util.Locale.ROOT)), what + " carries member " + key);
        }
        for (String value : strings) {
            assertFalse(COORDINATE_TRIPLE.matcher(value).find(), what + " carries a coordinate-like value " + value);
            assertFalse(value.contains(ALICE.toString()) || value.contains(BOB.toString()),
                    what + " carries a player id");
        }
    }

    // --- tests -----------------------------------------------------------------------

    @Test
    void aSecondPlayerReadsNothingOfTheFirsts() {
        World world = new World();
        String bobsPets = world.pets(BOB).toString();
        String alicesPets = world.pets(ALICE).toString();
        assertFalse(bobsPets.contains(world.aliceMoth.toString()) || bobsPets.contains(world.aliceLoper.toString())
                || bobsPets.contains(world.aliceEgg.entity().toString()), "Bob sees none of Alice's creatures");
        assertFalse(alicesPets.contains(world.bobHound.toString()) || alicesPets.contains(world.bobDrone.toString()),
                "Alice sees none of Bob's creatures");
        assertEquals(3, world.pets(ALICE).getAsJsonArray("pets").size());
        assertEquals(0, world.pets(ALICE).getAsJsonObject("caps").getAsJsonObject("drones").get("used").getAsInt(),
                "caps count only Alice's own rows");

        JsonObject bosses = world.bosses(ALICE);
        JsonArray fights = bosses.getAsJsonArray("fights");
        JsonObject shared = fights.get(0).getAsJsonObject();
        JsonObject bobs = fights.get(1).getAsJsonObject();
        assertEquals(30.0D, shared.get("your_damage").getAsDouble(), 1e-6, "own damage only");
        assertEquals(0.25D, shared.get("your_share").getAsDouble(), 1e-6, "own share of the aggregate");
        assertEquals(2, shared.get("participants").getAsInt(), "a count, not a roster");
        assertEquals(0.0D, bobs.get("your_damage").getAsDouble(), 1e-6, "a fight Alice is not in shows nothing of hers");
        assertEquals(1, bosses.getAsJsonArray("altars").size(), "only Alice's own altar");
        assertEquals(2, world.bosses(BOB).getAsJsonArray("altars").size());
        assertFalse(bosses.toString().contains("90.0"), "Bob's damage never reaches Alice");

        // The Bestiary for Alice is a function of Alice's data alone.
        Bestiary alice = alicesBestiary();
        String before = CreatureLinkJson.bestiary(alice, true).toString();
        Bestiary bob = new Bestiary(true, new HashMap<>(Map.of("dust_devil", Tier.STUDIED)), new HashMap<>(), new HashMap<>());
        CreatureLinkJson.bestiary(bob, true);
        assertEquals(before, CreatureLinkJson.bestiary(alice, true).toString());

        // Erasure forgets the in-memory altar list.
        assertTrue(world.altars.forget(ALICE));
        assertEquals(0, world.bosses(ALICE).getAsJsonArray("altars").size());
    }

    @Test
    void noCoordinatesOrPlayersInAnySerializedSection() {
        World world = new World();
        assertNoPlaceAndNoPlayer(CreatureLinkJson.bestiary(alicesBestiary(), true), "bestiary");
        assertNoPlaceAndNoPlayer(CreatureLinkJson.bestiary(new Bestiary(false, Map.of(), Map.of(), Map.of()), false),
                "bestiary offline");
        assertNoPlaceAndNoPlayer(world.pets(ALICE), "pets");
        assertNoPlaceAndNoPlayer(world.bosses(ALICE), "bosses");
        assertNoPlaceAndNoPlayer(CreatureLinkEvents.petStatePayload(world.aliceMoth, OwnedCreature.Kind.PET, MOTH,
                "nerospace:luna", CreatureLinkEvents.STATE_DIED, 1L), "pet_state_changed");
        assertNoPlaceAndNoPlayer(CreatureLinkEvents.hurtPayload(world.aliceMoth, OwnedCreature.Kind.PET, MOTH, 1L),
                "creature_hurt");
        assertNoPlaceAndNoPlayer(CreatureLinkEvents.hatchedPayload(MOTH, 1L), "egg_hatched");
        assertNoPlaceAndNoPlayer(CreatureLinkEvents.fightEndedPayload(MATRIARCH, true, 0.25F, 1L), "fight_ended");
        // The altar block position held in memory never reaches the section.
        assertFalse(world.bosses(BOB).toString().contains("987654321"));
    }

    @Test
    void unscannedEntriesCarryOnlyIdHabitatKindAndTier() {
        JsonObject root = CreatureLinkJson.bestiary(alicesBestiary(), true);
        JsonArray creatures = root.getAsJsonArray("creatures");
        assertEquals(BestiaryCatalog.entries().size(), creatures.size());
        for (JsonElement element : creatures) {
            JsonObject row = element.getAsJsonObject();
            if (row.get("tier").getAsInt() == 0) {
                assertEquals(Set.of("id", "habitat", "kind", "tier"), row.keySet(), row.toString());
            }
        }
        JsonObject shrike = byId(creatures, "nerocreatures:echo_shrike");
        assertFalse(shrike.has("weakness"), "the weakness is a Studied reward");
        assertTrue(byId(creatures, "nerocreatures:lumen_moth").has("owned"));
        assertTrue(byId(creatures, "nerocreatures:rime_matriarch").has("sigil"));
        assertEquals(3, shrike.get("killed").getAsInt());
    }

    @Test
    void schemaOneMembersAreKept() {
        World world = new World();
        JsonObject bestiary = CreatureLinkJson.bestiary(alicesBestiary(), true);
        assertEquals(2, bestiary.get("schema_version").getAsInt());
        for (String member : List.of("player_online", "total_kills", "species_killed", "spawns_enabled", "creatures")) {
            assertTrue(bestiary.has(member), "bestiary." + member);
        }
        JsonObject moth = byId(bestiary.getAsJsonArray("creatures"), "nerocreatures:lumen_moth");
        for (String member : List.of("id", "name", "translation_key", "killed", "spawns")) {
            assertTrue(moth.has(member), "bestiary row ." + member);
        }
        assertTrue(moth.get("spawns").isJsonArray());

        JsonObject pets = world.pets(ALICE);
        assertTrue(pets.has("schema_version") && pets.has("player_online") && pets.has("pets"));
        for (String cap : List.of("pets", "drones")) {
            JsonObject entry = pets.getAsJsonObject("caps").getAsJsonObject(cap);
            assertTrue(entry.has("used") && entry.has("max"), "caps." + cap);
        }
        JsonObject row = pets.getAsJsonArray("pets").get(0).getAsJsonObject();
        for (String member : List.of("entity", "kind", "type", "name", "dimension", "status", "health", "max_health",
                "command")) {
            assertTrue(row.has(member), "pets row ." + member);
        }
        assertEquals("guard", row.get("command").getAsString(), "schema-1 command keys are unchanged");

        JsonObject fight = world.bosses(ALICE).getAsJsonArray("fights").get(0).getAsJsonObject();
        for (String member : List.of("fight", "boss", "name", "dimension", "phase", "phase_count", "health",
                "your_damage", "your_share", "participants")) {
            assertTrue(fight.has(member), "fights row ." + member);
        }
    }

    @Test
    void schemaTwoPetMembersFollowTheAppContract() {
        World world = new World();
        JsonArray rows = world.pets(ALICE).getAsJsonArray("pets");
        JsonObject mount = null;
        JsonObject egg = null;
        for (JsonElement element : rows) {
            JsonObject row = element.getAsJsonObject();
            if ("mount".equals(row.get("kind").getAsString())) {
                mount = row;
            } else if ("egg".equals(row.get("kind").getAsString())) {
                egg = row;
            }
        }
        assertTrue(mount != null && egg != null);
        assertTrue(mount.get("saddled").getAsBoolean());
        assertEquals(0.8D, mount.get("stamina").getAsDouble(), 1e-6);
        assertEquals(2, mount.getAsJsonArray("traits").size());
        assertEquals(240L, mount.get("breed_ready_in_s").getAsLong());
        List<String> commands = new ArrayList<>();
        mount.getAsJsonArray("commands").forEach(element -> commands.add(element.getAsString()));
        assertEquals(List.of("follow", "stay", "sit"), commands);
        assertEquals(250L, egg.get("hatch_in_s").getAsLong());
        assertEquals(0, egg.getAsJsonArray("commands").size(), "an egg takes no orders");
        assertFalse(egg.get("can_recall").getAsBoolean());
        JsonObject caps = world.pets(ALICE).getAsJsonObject("caps");
        assertEquals(1, caps.getAsJsonObject("mounts").get("used").getAsInt());
        assertEquals(1, caps.getAsJsonObject("eggs").get("used").getAsInt());
    }

    @Test
    void rateLimitHolds() {
        AtomicLong now = new AtomicLong(1_000_000L);
        CreatureLinkActions actions = new CreatureLinkActions(now::get, () -> 10_000L, () -> 1_000L);
        JsonObject recall = new JsonObject();
        assertNotEquals(CreatureLinkActions.RATE_LIMITED, actions.execute(ALICE, "pet_recall", recall).message());
        assertEquals(CreatureLinkActions.RATE_LIMITED, actions.execute(ALICE, "pet_recall", recall).message(),
                "a second recall within ten seconds is refused");
        LinkActionResult refused = actions.execute(ALICE, "pet_recall", recall);
        assertEquals(LinkActionResult.Error.VALIDATION, refused.error());
        assertNotEquals(CreatureLinkActions.RATE_LIMITED, actions.execute(BOB, "pet_recall", recall).message(),
                "another player's limit is their own");
        now.addAndGet(9_999L);
        assertEquals(CreatureLinkActions.RATE_LIMITED, actions.execute(ALICE, "pet_recall", recall).message(),
                "a refused call does not restart the wait, and the wait is not over yet");
        now.addAndGet(1L);
        assertNotEquals(CreatureLinkActions.RATE_LIMITED, actions.execute(ALICE, "pet_recall", recall).message());

        JsonObject command = new JsonObject();
        command.addProperty("pet", UUID.randomUUID().toString());
        command.addProperty("command", "sit");
        assertNotEquals(CreatureLinkActions.RATE_LIMITED, actions.execute(ALICE, "pet_command", command).message(),
                "orders have their own limit");
        assertEquals(CreatureLinkActions.RATE_LIMITED, actions.execute(ALICE, "pet_command", command).message());
        now.addAndGet(1_000L);
        assertNotEquals(CreatureLinkActions.RATE_LIMITED, actions.execute(ALICE, "pet_command", command).message());

        CreatureLinkRules.RateLimiter limiter = new CreatureLinkRules.RateLimiter();
        assertTrue(limiter.tryAcquire(ALICE, "pet_recall", 0L, 10_000L));
        assertFalse(limiter.tryAcquire(ALICE, "pet_recall", 5_000L, 10_000L));
        limiter.forget(ALICE);
        assertTrue(limiter.tryAcquire(ALICE, "pet_recall", 5_000L, 10_000L), "erasure forgets the limiter entry");
    }

    @Test
    void unknownCommandIsRejected() {
        CreatureLinkActions actions = new CreatureLinkActions(() -> 0L, () -> 0L, () -> 0L);
        for (String bad : new String[] {"dance", "guard", "STAY", "Follow", "", " sit"}) {
            JsonObject params = new JsonObject();
            params.addProperty("pet", UUID.randomUUID().toString());
            params.addProperty("command", bad);
            LinkActionResult result = actions.execute(ALICE, "pet_command", params);
            assertFalse(result.ok());
            assertEquals(LinkActionResult.Error.VALIDATION, result.error(), bad);
            assertEquals(CreatureLinkActions.UNKNOWN_COMMAND, result.message(), bad);
        }
        JsonObject missing = new JsonObject();
        missing.addProperty("pet", UUID.randomUUID().toString());
        assertEquals(CreatureLinkActions.UNKNOWN_COMMAND, actions.execute(ALICE, "pet_command", missing).message());
        JsonObject noPet = new JsonObject();
        noPet.addProperty("command", "sit");
        assertEquals(CreatureLinkActions.BAD_REQUEST, actions.execute(ALICE, "pet_command", noPet).message());
        JsonObject junkPet = new JsonObject();
        junkPet.addProperty("pet", "not-a-uuid");
        assertEquals(CreatureLinkActions.BAD_REQUEST, actions.execute(ALICE, "pet_recall", junkPet).message(),
                "a malformed id never widens into recall-all");
        assertEquals(CreatureLinkActions.BAD_REQUEST, actions.execute(ALICE, "pet_dance", new JsonObject()).message());
        assertTrue(actions.actionIds().containsAll(List.of("pet_recall", "pet_command")));

        assertEquals(PetCommand.STAY, PetCommand.fromRemoteKey("stay").orElseThrow());
        assertEquals(PetCommand.FOLLOW, PetCommand.fromRemoteKey("follow").orElseThrow());
        assertEquals(PetCommand.SIT, PetCommand.fromRemoteKey("sit").orElseThrow());
        assertTrue(PetCommand.fromRemoteKey("guard").isEmpty());
        assertEquals(PetCommand.FOLLOW, PetCommand.fromKey("nonsense"), "saved data stays lenient");
        assertEquals(PetCommand.STAY, PetCommand.fromKey("stay"));
        assertEquals(PetCommand.STAY, PetCommand.SIT.next());
        assertEquals(PetCommand.GUARD, PetCommand.STAY.next());
        assertEquals(PetCommand.FOLLOW, PetCommand.GUARD.next());
        assertEquals(PetCommand.SIT, PetCommand.FOLLOW.next());
        assertEquals(3, PetCommand.STAY.ordinal(), "append-only: older ordinals unchanged");
    }

    @Test
    void pushBodiesNameTheSpeciesOnly() {
        JsonObject died = CreatureLinkEvents.petStatePayload(UUID.randomUUID(), OwnedCreature.Kind.MOUNT, LOPER,
                "nerospace:mars", CreatureLinkEvents.STATE_DIED, 1L).getAsJsonObject("_notify");
        assertEquals("Your Dune Loper died.", died.get("body").getAsString());
        assertEquals("NeroCreatures", died.get("title").getAsString());
        assertEquals("nerolink://server/{server}/nerocreatures", died.get("deepLink").getAsString());
        assertEquals("Your Lumen Moth is badly hurt.", CreatureLinkEvents.hurtPayload(UUID.randomUUID(),
                OwnedCreature.Kind.PET, MOTH, 1L).getAsJsonObject("_notify").get("body").getAsString());
        assertEquals("A Lumen Moth egg has hatched.",
                CreatureLinkEvents.hatchedPayload(MOTH, 1L).getAsJsonObject("_notify").get("body").getAsString());
        assertEquals("The Rime Matriarch fight has ended.", CreatureLinkEvents.fightEndedPayload(MATRIARCH, false,
                0.0F, 1L).getAsJsonObject("_notify").get("body").getAsString());
        assertFalse(CreatureLinkEvents.petStatePayload(UUID.randomUUID(), OwnedCreature.Kind.PET, MOTH, "nerospace:luna",
                CreatureLinkEvents.STATE_DIED, 1L).getAsJsonObject("_notify").toString().contains("luna"),
                "never a dimension in the push text");

        // Routine state changes are not published (they would become generic pushes).
        assertTrue(CreatureLinkEvents.publishesState(CreatureLinkEvents.STATE_DIED));
        for (String routine : List.of(CreatureLinkEvents.STATE_TAMED, CreatureLinkEvents.STATE_RELEASED,
                CreatureLinkEvents.STATE_DEPLOYED, CreatureLinkEvents.STATE_RECALLED)) {
            assertFalse(CreatureLinkEvents.publishesState(routine), routine);
            assertFalse(CreatureLinkEvents.petStatePayload(UUID.randomUUID(), OwnedCreature.Kind.PET, MOTH, "x:y",
                    routine, 1L).has("_notify"));
        }
    }

    @Test
    void badlyHurtIsACrossingDebouncedPerCreature() {
        assertTrue(CreatureLinkRules.isBadlyHurt(0.2F));
        assertFalse(CreatureLinkRules.isBadlyHurt(0.25F));
        assertFalse(CreatureLinkRules.isBadlyHurt(0.0F), "dead is the died event, not hurt");
        CreatureLinkRules.HurtDebounce debounce = new CreatureLinkRules.HurtDebounce();
        UUID moth = UUID.randomUUID();
        assertFalse(debounce.shouldAlert(moth, true, 0L), "sparring with its owner is not news");
        assertTrue(debounce.shouldAlert(moth, false, 0L));
        assertFalse(debounce.shouldAlert(moth, false, CreatureLinkRules.HURT_DEBOUNCE_MS - 1L));
        assertTrue(debounce.shouldAlert(UUID.randomUUID(), false, 1L), "per creature");
        assertTrue(debounce.shouldAlert(moth, false, CreatureLinkRules.HURT_DEBOUNCE_MS));
    }

    @Test
    void altarIndexIsStableAndTheStateFollowsTheCooldown() {
        AltarUses.Table table = new AltarUses.Table();
        assertEquals(1, table.noteSummon(ALICE, LUNA, 10L, 1_000L, null, MATRIARCH.toString()));
        assertEquals(2, table.noteSummon(ALICE, LUNA, 20L, 1_000L, null, MATRIARCH.toString()));
        assertEquals(1, table.noteSummon(ALICE, LUNA, 10L, 5_000L, null, MATRIARCH.toString()), "same altar, same index");
        List<AltarUses.Row> rows = table.rows(ALICE, 1_000L,
                (dimension, pos, fight, readyAt) -> new AltarUses.Probe(pos != 20L, true, readyAt, false));
        assertEquals(1, rows.size(), "a broken altar is dropped");
        assertEquals(AltarUses.State.COOLDOWN, rows.get(0).state());
        assertEquals(200L, rows.get(0).readyInS());
        assertEquals(2, table.noteSummon(ALICE, LUNA, 30L, 0L, null, null), "the smallest free index is reused");
        assertEquals(AltarUses.State.FIGHTING, AltarUses.stateOf(0L, 10L, true));
        assertEquals(AltarUses.State.READY, AltarUses.stateOf(10L, 10L, false));
        List<AltarUses.Row> later = table.rows(ALICE, 5_000L + AltarUses.EXPIRE_AFTER_READY_TICKS + 1L,
                (dimension, pos, fight, readyAt) -> new AltarUses.Probe(true, false, readyAt, false));
        assertTrue(later.isEmpty(), "forgotten an hour after it is ready");
        assertEquals(0, table.players());
    }

    private static JsonObject byId(JsonArray creatures, String id) {
        for (JsonElement element : creatures) {
            if (id.equals(element.getAsJsonObject().get("id").getAsString())) {
                return element.getAsJsonObject();
            }
        }
        throw new AssertionError("no " + id);
    }
}
