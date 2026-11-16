package za.co.neroland.nerocreatures.entity.alpha;

import java.io.Reader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;

import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;

/**
 * Reads the alpha data from the server's data packs, lazily and once per resource manager.
 *
 * <p>No loader reload-listener wiring is needed: {@code /reload} replaces the server's resource
 * manager, and the next lookup notices the new instance and re-reads. The data is tiny (a handful
 * of files) and is only read when an alpha roll succeeds or an alpha dies.
 *
 * <p>Layout, all under {@code data/<namespace>/nerocreatures/alpha/}:
 * <ul>
 *   <li>{@code settings.json} (only the {@code nerocreatures} namespace is read)</li>
 *   <li>{@code modifiers/<name>.json}, any namespace</li>
 *   <li>{@code drops/<entity path>.json} in the entity's namespace, plus
 *       {@code nerocreatures:drops/default.json}</li>
 * </ul>
 */
public final class AlphaData {

    private static final String ROOT = "nerocreatures/alpha";

    /** One loaded view of the data. */
    public record Snapshot(AlphaSettings settings, List<AlphaModifier> modifiers, Map<String, List<AlphaDrop>> drops) {

        /** Drops for {@code entityId}, or the default list. */
        public List<AlphaDrop> dropsFor(String entityId) {
            List<AlphaDrop> own = this.drops.get(entityId);
            return own != null && !own.isEmpty() ? own : this.drops.getOrDefault("default", List.of());
        }
    }

    private static final Snapshot EMPTY = new Snapshot(AlphaSettings.DEFAULTS, List.of(), Map.of());

    private static Object loadedFor;
    private static Snapshot snapshot = EMPTY;

    private AlphaData() {
    }

    /** The current data for {@code server}. Server thread only. */
    public static synchronized Snapshot get(MinecraftServer server) {
        ResourceManager manager = server.getResourceManager();
        if (manager != loadedFor) {
            snapshot = load(manager);
            loadedFor = manager;
        }
        return snapshot;
    }

    private static Snapshot load(ResourceManager manager) {
        AlphaSettings settings = AlphaSettings.DEFAULTS;
        List<AlphaModifier> modifiers = new ArrayList<>();
        Map<String, List<AlphaDrop>> drops = new HashMap<>();
        Map<Identifier, Resource> found = manager.listResources(ROOT, id -> id.getPath().endsWith(".json"));
        for (Map.Entry<Identifier, Resource> entry : found.entrySet()) {
            Identifier id = entry.getKey();
            String rest = id.getPath().substring(ROOT.length() + 1, id.getPath().length() - ".json".length());
            JsonElement json = read(id, entry.getValue());
            if (json == null || !json.isJsonObject()) {
                continue;
            }
            if (rest.equals("settings") && id.getNamespace().equals(NeroCreaturesCommon.MOD_ID)) {
                settings = AlphaJson.settings(json.getAsJsonObject());
            } else if (rest.startsWith("modifiers/")) {
                AlphaModifier modifier = AlphaJson.modifier(
                        id.getNamespace() + ":" + rest.substring("modifiers/".length()), json.getAsJsonObject());
                if (modifier != null) {
                    modifiers.add(modifier);
                }
            } else if (rest.startsWith("drops/")) {
                String path = rest.substring("drops/".length());
                String key = path.equals("default") && id.getNamespace().equals(NeroCreaturesCommon.MOD_ID)
                        ? "default" : id.getNamespace() + ":" + path;
                drops.put(key, List.copyOf(AlphaJson.drops(json.getAsJsonObject())));
            }
        }
        // Stable roll order regardless of pack iteration order.
        modifiers.sort((a, b) -> a.id().compareTo(b.id()));
        return new Snapshot(settings, List.copyOf(modifiers), Map.copyOf(drops));
    }

    private static JsonElement read(Identifier id, Resource resource) {
        try (Reader reader = resource.openAsReader()) {
            return JsonParser.parseReader(reader);
        } catch (Exception e) {
            // Resource id only: no player data in logs.
            NeroCreaturesCommon.LOGGER.warn("[NeroCreatures] Skipping unreadable alpha data {}: {}", id, // log-privacy: ok resource id
                    e.getClass().getSimpleName());
            return null;
        }
    }
}
