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
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Writes a data-access export to a file instead of chat. Chat output from the server console or RCON
 * is copied into the server log, and chat is no file transfer (a long export would be cut off), so
 * {@code /nerocreatures export} writes the whole JSON here and tells the invoker only the path.
 *
 * <p>The file goes under the world folder, {@code data/nerocreatures/exports/}, with a name that holds
 * the time and a random suffix, never the player. Where the file system supports POSIX permissions the
 * folder is owner-only ({@code rwx------}) and the file owner read/write ({@code rw-------}); elsewhere
 * (Windows) the file inherits the world folder's access rules, which already guard every save file.
 * The operator hands the file to the player and deletes it.
 *
 * <p><b>Lifecycle (POPIA/GDPR).</b> An export is a one-off copy of a personal record, so it never
 * outlives its purpose for long: {@link #deleteFor} removes every export about a player when that
 * player is erased (the usual order for a departing player is export, then erase), and
 * {@link #deleteExpired} removes any export older than {@link #lifetimeDays} &mdash; seven days, or
 * Core's {@code dataRetentionDays} when that is shorter &mdash; from the daily retention sweep and on
 * every new export.
 */
public final class CreatureExportFile {

    /** Folder under the world root that holds export files. */
    public static final String FOLDER = "data/nerocreatures/exports";

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").withZone(ZoneOffset.UTC);

    /** The longest an export file is kept, whatever the retention setting. */
    public static final int DEFAULT_LIFETIME_DAYS = 7;

    private static final long MILLIS_PER_DAY = 24L * 60L * 60L * 1000L;

    private CreatureExportFile() {
    }

    /**
     * How long an export file is kept: {@link #DEFAULT_LIFETIME_DAYS}, or Core's {@code dataRetentionDays}
     * when that is set and shorter (never under one day). {@code 0} (retention off) keeps the default.
     */
    public static int lifetimeDays(int retentionDays) {
        if (retentionDays <= 0) {
            return DEFAULT_LIFETIME_DAYS;
        }
        return Math.max(1, Math.min(DEFAULT_LIFETIME_DAYS, retentionDays));
    }

    /**
     * Erasure: deletes every export file that is about {@code player} (its text holds their UUID, as
     * the {@code player} member does). A file that cannot be read is left for the expiry sweep.
     *
     * @return how many files were deleted
     */
    public static int deleteFor(Path worldRoot, UUID player) throws IOException {
        String id = player.toString();
        int deleted = 0;
        for (Path file : exportFiles(worldRoot)) {
            String text;
            try {
                text = Files.readString(file, StandardCharsets.UTF_8);
            } catch (IOException e) {
                continue;
            }
            if (text.contains(id) && Files.deleteIfExists(file)) {
                deleted++;
            }
        }
        return deleted;
    }

    /**
     * Retention: deletes every export file last written more than {@code lifetimeDays} days before
     * {@code now}.
     *
     * @return how many files were deleted
     */
    public static int deleteExpired(Path worldRoot, long now, int lifetimeDays) throws IOException {
        long cutoff = now - Math.max(1, lifetimeDays) * MILLIS_PER_DAY;
        int deleted = 0;
        for (Path file : exportFiles(worldRoot)) {
            if (Files.getLastModifiedTime(file).toMillis() < cutoff && Files.deleteIfExists(file)) {
                deleted++;
            }
        }
        return deleted;
    }

    /** The export files currently in the folder (none if it does not exist). */
    private static List<Path> exportFiles(Path worldRoot) throws IOException {
        Path folder = worldRoot.resolve(FOLDER);
        List<Path> files = new ArrayList<>();
        if (!Files.isDirectory(folder)) {
            return files;
        }
        try (Stream<Path> list = Files.list(folder)) {
            list.filter(Files::isRegularFile)
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        return name.startsWith("export-") && name.endsWith(".json");
                    })
                    .forEach(files::add);
        }
        return files;
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
