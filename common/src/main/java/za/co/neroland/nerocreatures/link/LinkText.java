package za.co.neroland.nerocreatures.link;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * English text for the link module, read straight from this mod's own {@code en_us.json} on the
 * classpath. A dedicated server has no client language to resolve keys with, and a companion app
 * wants words, so the Bestiary's ability, weakness and drop-use lines, and creature and item names,
 * come from here. Loaded once; a missing file or key answers empty and the caller falls back.
 */
public final class LinkText {

    private static final String PATH = "/assets/nerocreatures/lang/en_us.json";

    private static volatile Map<String, String> lines;

    private LinkText() {
    }

    /** The English line for a key, if this mod has one. */
    public static Optional<String> get(String key) {
        return Optional.ofNullable(lines().get(key));
    }

    /** {@code entity.nerocreatures.<path>}, else the title-cased path. */
    public static String speciesName(String path) {
        return get("entity.nerocreatures." + path).orElseGet(() -> CreatureLinkAccess.readablePath(path));
    }

    /** {@code item.nerocreatures.<path>}, else the title-cased path. */
    public static String itemName(String path) {
        return get("item.nerocreatures." + path).orElseGet(() -> CreatureLinkAccess.readablePath(path));
    }

    /** Cuts a line to {@code max} characters, on a word where it can, with an ellipsis. */
    public static String clip(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        int cut = text.lastIndexOf(' ', max - 1);
        if (cut < max / 2) {
            cut = max - 1;
        }
        return text.substring(0, cut).trim() + "…";
    }

    private static Map<String, String> lines() {
        Map<String, String> loaded = lines;
        if (loaded == null) {
            loaded = load();
            lines = loaded;
        }
        return loaded;
    }

    private static Map<String, String> load() {
        Map<String, String> out = new HashMap<>();
        try (InputStream in = LinkText.class.getResourceAsStream(PATH)) {
            if (in == null) {
                return out;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonElement root = JsonParser.parseReader(reader);
                if (root != null && root.isJsonObject()) {
                    JsonObject object = root.getAsJsonObject();
                    for (Map.Entry<String, JsonElement> entry : object.entrySet()) {
                        if (entry.getValue().isJsonPrimitive()) {
                            out.put(entry.getKey(), entry.getValue().getAsString());
                        }
                    }
                }
            }
        } catch (java.io.IOException | RuntimeException e) {
            // Names fall back to title-cased ids; nothing here is worth failing a snapshot for.
            return out;
        }
        return out;
    }
}
