package za.co.neroland.nerocreatures.data;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.Set;

/**
 * Writes a data-access export to a file instead of chat. Chat output from the server console or RCON
 * is copied into the server log, and chat is no file transfer (a long export would be cut off), so
 * {@code /nerocreatures export} writes the whole JSON here and tells the invoker only the path.
 *
 * <p>The file goes under the world folder, {@code data/nerocreatures/exports/}, with a name that holds
 * the time and a random suffix, never the player. Where the file system supports POSIX permissions the
 * folder is owner-only ({@code rwx------}) and the file owner read/write ({@code rw-------}); elsewhere
 * (Windows) the file inherits the world folder's access rules, which already guard every save file.
 * The operator hands the file to the player and deletes it; nothing here deletes it for them, since
 * the access request is not complete until they have.
 */
public final class CreatureExportFile {

    /** Folder under the world root that holds export files. */
    public static final String FOLDER = "data/nerocreatures/exports";

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    private CreatureExportFile() {
    }

    /**
     * Writes one export.
     *
     * @param worldRoot the world folder
     * @param json      the full export text (never truncated)
     * @param now       epoch millis, for the file name
     * @return the written file
     */
    public static Path write(Path worldRoot, String json, long now) throws IOException {
        Path folder = worldRoot.resolve(FOLDER);
        Files.createDirectories(folder);
        restrict(folder, PosixFilePermissions.fromString("rwx------"));
        byte[] random = new byte[4];
        new SecureRandom().nextBytes(random);
        Path file = folder.resolve("export-" + STAMP.format(Instant.ofEpochMilli(now)) + "-"
                + HexFormat.of().formatHex(random) + ".json");
        Files.createFile(file);
        restrict(file, PosixFilePermissions.fromString("rw-------"));
        Files.writeString(file, json, StandardCharsets.UTF_8, StandardOpenOption.TRUNCATE_EXISTING);
        return file;
    }

    /** Owner-only permissions where the file system has POSIX permissions; a no-op elsewhere. */
    private static void restrict(Path path, Set<PosixFilePermission> permissions) throws IOException {
        PosixFileAttributeView view = Files.getFileAttributeView(path, PosixFileAttributeView.class);
        if (view != null) {
            view.setPermissions(permissions);
        }
    }
}
