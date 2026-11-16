package za.co.neroland.nerocreatures.telemetry;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Removes personal data from free text before it leaves the machine in a crash report (POPIA/GDPR).
 *
 * <p>Exception messages and breadcrumbs are free text. Vanilla and loader code routinely put an
 * entity's {@code toString()} into them, which carries the entity's custom name, its level (the world
 * name) and its coordinates, and profile or codec errors can carry player names and UUIDs. Everything
 * that leaves through {@link NeroCreaturesTelemetry} passes through {@link #scrub} first, which
 * replaces:
 *
 * <ul>
 *   <li>home-directory paths (the OS account name) and save-folder names (the world name);</li>
 *   <li>UUIDs, dashed, undashed or as NBT int arrays;</li>
 *   <li>entity names in {@code Type['name'/id, ...]} and quoted player, owner or profile names, and
 *       {@code name=...} fields;</li>
 *   <li>level and world names ({@code l='...'}, {@code ServerLevel[...]}, {@code world '...'});</li>
 *   <li>coordinates: {@code x=/y=/z=} fields, chunk positions and bare number triples.</li>
 * </ul>
 *
 * <p>Pure string work with no Sentry types, so it is unit-tested on its own. Over-redaction is the
 * accepted failure mode: a report that loses a harmless number is still useful, one that carries a
 * player's name is not allowed.
 */
public final class TelemetryScrubber {

    private record Rule(Pattern pattern, String replacement) {
    }

    private static final List<Rule> RULES = List.of(
            // OS account names in Windows/macOS/Linux home-directory paths.
            new Rule(Pattern.compile("(?i)(?:[A-Z]:)?[/\\\\](?:Users|home)[/\\\\][^/\\\\:;,'\"\\r\\n]+"), "/~"),
            // The world folder name in save paths.
            new Rule(Pattern.compile("(?i)\\bsaves[/\\\\][^/\\\\:;,'\"\\r\\n]+"), "saves/<world>"),
            // UUIDs: NBT int arrays, dashed, undashed.
            new Rule(Pattern.compile("\\[I;\\s*-?\\d+\\s*,\\s*-?\\d+\\s*,\\s*-?\\d+\\s*,\\s*-?\\d+\\s*]"), "<uuid>"),
            new Rule(Pattern.compile("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b"),
                    "<uuid>"),
            new Rule(Pattern.compile("\\b[0-9a-fA-F]{32}\\b"), "<uuid>"),
            // Entity.toString(): Type['custom name'/123, ...].
            new Rule(Pattern.compile("\\['[^'\\r\\n]*'/"), "['<name>'/"),
            // Level names: l='world', ServerLevel[world], ClientLevel[...].
            new Rule(Pattern.compile("\\bl='[^'\\r\\n]*'"), "l='<level>'"),
            new Rule(Pattern.compile("\\b(Server|Client)Level\\[[^\\]\\r\\n]*]"), "$1Level[<level>]"),
            new Rule(Pattern.compile("(?i)\\b(world|level|save)(\\s*[:=]?\\s*)'[^'\\r\\n]*'"), "$1$2'<world>'"),
            // Player names: quoted after a role word, and name= fields (GameProfile and friends).
            new Rule(Pattern.compile("(?i)\\b(player|owner|user|profile|by)(\\s*[:=]?\\s*)'[^'\\r\\n]*'"),
                    "$1$2'<name>'"),
            new Rule(Pattern.compile("(?i)\\b(name|player|owner|user)\\s*=\\s*[^,}\\]\\s]+"), "$1=<name>"),
            // Coordinates: x=/y=/z= fields (Entity, BlockPos, Vec3), chunk positions, bare triples.
            new Rule(Pattern.compile("\\b([xyzXYZ])\\s*=\\s*-?\\d+(?:\\.\\d+)?(?:[dDfF])?"), "$1=<n>"),
            new Rule(Pattern.compile("\\bcpos=\\[[^\\]\\r\\n]*]"), "cpos=<pos>"),
            new Rule(Pattern.compile("-?\\d+(?:\\.\\d+)?\\s*,\\s*-?\\d+(?:\\.\\d+)?\\s*,\\s*-?\\d+(?:\\.\\d+)?"), "<pos>"));

    private TelemetryScrubber() {
    }

    /** The text with every personal-data pattern above replaced by a neutral marker. */
    public static String scrub(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = text;
        for (Rule rule : RULES) {
            out = rule.pattern().matcher(out).replaceAll(rule.replacement());
        }
        return out;
    }
}
