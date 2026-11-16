package za.co.neroland.nerocreatures.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** POPIA/GDPR access: an export is written whole to an owner-only file, never to chat or the log. */
class CreatureExportFileTest {

    @TempDir
    Path world;

    @Test
    void theWholeExportIsWrittenUnderTheWorldWithoutThePlayerInTheName() throws Exception {
        UUID subject = UUID.randomUUID();
        StringBuilder json = new StringBuilder("{\"player\":\"").append(subject).append("\",\"rows\":[");
        while (json.length() < 100_000) {
            json.append("\"row\",");
        }
        json.append("\"last\"]}");

        Path file = CreatureExportFile.write(this.world, json.toString(), 1_800_000_000_000L);

        assertTrue(file.startsWith(this.world.resolve(CreatureExportFile.FOLDER)));
        assertEquals(json.toString(), Files.readString(file, StandardCharsets.UTF_8), "never truncated");
        assertFalse(file.getFileName().toString().contains(subject.toString()), "the name names nobody");
        assertTrue(file.getFileName().toString().startsWith("export-20270115-"), file.getFileName().toString());

        PosixFileAttributeView view = Files.getFileAttributeView(file, PosixFileAttributeView.class);
        if (view != null) {
            assertEquals("rw-------", PosixFilePermissions.toString(view.readAttributes().permissions()));
        }
    }

    @Test
    void twoExportsInOneSecondDoNotOverwriteEachOther() throws Exception {
        Path first = CreatureExportFile.write(this.world, "{}", 1_800_000_000_000L);
        Path second = CreatureExportFile.write(this.world, "{\"b\":1}", 1_800_000_000_000L);
        assertNotEquals(first, second);
        assertEquals("{}", Files.readString(first, StandardCharsets.UTF_8));
    }

    @Test
    void erasureDeletesOnlyThatPlayersExports() throws Exception {
        UUID erased = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Path theirs = CreatureExportFile.write(this.world, "{\"player\":\"" + erased + "\"}", 1_800_000_000_000L);
        Path again = CreatureExportFile.write(this.world, "{\"player\":\"" + erased + "\",\"n\":2}", 1_800_000_000_000L);
        Path kept = CreatureExportFile.write(this.world, "{\"player\":\"" + other + "\"}", 1_800_000_000_000L);

        assertEquals(2, CreatureData.eraseExports(this.world, erased));
        assertFalse(Files.exists(theirs));
        assertFalse(Files.exists(again));
        assertTrue(Files.exists(kept), "another player's export stays");
        assertEquals(0, CreatureExportFile.deleteFor(this.world.resolve("nowhere"), erased), "no folder is fine");
    }

    @Test
    void exportsExpireAfterSevenDaysOrTheShorterRetention() throws Exception {
        long now = 1_800_000_000_000L;
        long day = 24L * 60L * 60L * 1000L;
        Path old = CreatureExportFile.write(this.world, "{}", now);
        Path recent = CreatureExportFile.write(this.world, "{}", now);
        Files.setLastModifiedTime(old, FileTime.fromMillis(now - 8 * day));
        Files.setLastModifiedTime(recent, FileTime.fromMillis(now - 2 * day));

        assertEquals(7, CreatureExportFile.lifetimeDays(0), "retention off keeps the short default");
        assertEquals(7, CreatureExportFile.lifetimeDays(365));
        assertEquals(1, CreatureExportFile.lifetimeDays(1));
        assertEquals(3, CreatureExportFile.lifetimeDays(3));

        assertEquals(1, CreatureExportFile.deleteExpired(this.world, now, CreatureExportFile.lifetimeDays(0)));
        assertFalse(Files.exists(old));
        assertTrue(Files.exists(recent));
        assertEquals(1, CreatureExportFile.deleteExpired(this.world, now, CreatureExportFile.lifetimeDays(1)),
                "a one-day retention removes the two-day-old export too");
        assertFalse(Files.exists(recent));
    }
}
