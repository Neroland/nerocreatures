package za.co.neroland.nerocreatures.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The crash-report scrubber removes names, UUIDs, coordinates and world names (POPIA/GDPR). */
class TelemetryScrubberTest {

    /** A real 26.x Entity.toString() shape, with a custom name, world name and coordinates. */
    private static final String ENTITY = "TameableCreature['Sir Fluffington'/1234, "
            + "uuid='3f2b9c1e-8a7d-4e21-9b3c-5d6e7f809a1b', l='ServerLevel[Northwind Survival]', "
            + "x=-1234.56, y=64.00, z=789.01, cpos=[-78, 49], tl=4021, v=true, removed=null]";

    @Test
    void entityToStringLosesNameUuidLevelAndCoordinates() {
        String out = TelemetryScrubber.scrub("java.lang.IllegalStateException: Ticking entity " + ENTITY);
        for (String leak : new String[] {"Sir Fluffington", "3f2b9c1e", "Northwind Survival", "1234.56", "789.01",
                "-78"}) {
            assertFalse(out.contains(leak), () -> leak + " survived: " + out);
        }
        assertTrue(out.contains("TameableCreature['<name>'/1234"), out);
        assertTrue(out.contains("Ticking entity"), "the useful part stays: " + out);
    }

    @Test
    void uuidsInEveryShapeAreRedacted() {
        String out = TelemetryScrubber.scrub("owner 3F2B9C1E-8A7D-4E21-9B3C-5D6E7F809A1B, id 3f2b9c1e8a7d4e219b3c5d6e7f809a1b,"
                + " nbt Owner:[I;-1234567, 89, -1011, 1213]");
        assertFalse(out.matches("(?s).*[0-9a-fA-F]{8}-[0-9a-fA-F]{4}.*"), out);
        assertFalse(out.contains("3f2b9c1e8a7d"), out);
        assertFalse(out.contains("-1234567"), out);
    }

    @Test
    void playerNamesAndProfilesAreRedacted() {
        String out = TelemetryScrubber.scrub("Failed to decode GameProfile{id=null, name=Builder_123, properties={}} "
                + "for player 'Kestrel42' (owner: 'Moriah')");
        assertFalse(out.contains("Builder_123"), out);
        assertFalse(out.contains("Kestrel42"), out);
        assertFalse(out.contains("Moriah"), out);
    }

    @Test
    void coordinatesAndWorldNamesAreRedacted() {
        String out = TelemetryScrubber.scrub("BlockPos{x=100, y=-20, z=3000} at 12.5, 70.0, -8.25 in world 'My Base'"
                + " C:\\Users\\someone\\AppData\\Roaming\\.minecraft\\saves\\Secret Base\\level.dat");
        for (String leak : new String[] {"100", "3000", "12.5", "-8.25", "My Base", "someone", "Secret", "Base"}) {
            assertFalse(out.contains(leak), () -> leak + " survived: " + out);
        }
    }

    @Test
    void harmlessTextIsUntouched() {
        String plain = "java.lang.NullPointerException: Cannot invoke \"Object.hashCode()\" because \"key\" is null";
        assertEquals(plain, TelemetryScrubber.scrub(plain));
        assertEquals("", TelemetryScrubber.scrub(""));
        assertEquals(null, TelemetryScrubber.scrub(null));
    }
}
