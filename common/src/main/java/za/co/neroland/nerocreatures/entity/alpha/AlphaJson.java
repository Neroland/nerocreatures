package za.co.neroland.nerocreatures.entity.alpha;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

/**
 * Lenient parsers for the alpha data files. Pure (Gson only), unit-tested. A bad entry is skipped,
 * never thrown: data must not be able to break spawning.
 */
public final class AlphaJson {

    private static final List<String> OPERATIONS = List.of("add_value", "add_multiplied_base", "add_multiplied_total");

    private AlphaJson() {
    }

    /** Parses one modifier file; {@code null} if it has nothing usable. */
    public static AlphaModifier modifier(String id, JsonObject json) {
        int weight = Math.max(1, intOr(json, "weight", 1));
        List<AlphaModifier.AttributeBonus> attributes = attributes(json.get("attributes"));
        List<AlphaModifier.EffectBonus> effects = new ArrayList<>();
        JsonElement fx = json.get("effects");
        if (fx != null && fx.isJsonArray()) {
            for (JsonElement e : fx.getAsJsonArray()) {
                if (e.isJsonObject()) {
                    String effect = stringOr(e.getAsJsonObject(), "effect", "");
                    int amp = Math.max(0, Math.min(4, intOr(e.getAsJsonObject(), "amplifier", 0)));
                    if (!effect.isEmpty()) {
                        effects.add(new AlphaModifier.EffectBonus(effect, amp));
                    }
                }
            }
        }
        if (attributes.isEmpty() && effects.isEmpty()) {
            return null;
        }
        return new AlphaModifier(id, weight, List.copyOf(attributes), List.copyOf(effects));
    }

    /** Parses the settings file; missing fields fall back to {@link AlphaSettings#DEFAULTS}. */
    public static AlphaSettings settings(JsonObject json) {
        AlphaSettings d = AlphaSettings.DEFAULTS;
        double chance = Math.max(0.0D, Math.min(1.0D, doubleOr(json, "chance", d.chance())));
        int cap = Math.max(0, Math.min(AlphaSettings.ABSOLUTE_MAX_PER_DIMENSION,
                intOr(json, "max_per_dimension", d.maxPerDimension())));
        double scale = Math.max(0.0D, Math.min(1.0D, doubleOr(json, "scale_bonus", d.scaleBonus())));
        List<AlphaModifier.AttributeBonus> base = json.has("base_attributes")
                ? List.copyOf(attributes(json.get("base_attributes"))) : d.base();
        return new AlphaSettings(chance, cap, scale, base);
    }

    /** Parses a drop file's {@code drops} array. */
    public static List<AlphaDrop> drops(JsonObject json) {
        List<AlphaDrop> out = new ArrayList<>();
        JsonElement arr = json.get("drops");
        if (arr == null || !arr.isJsonArray()) {
            return out;
        }
        for (JsonElement e : arr.getAsJsonArray()) {
            if (!e.isJsonObject()) {
                continue;
            }
            JsonObject o = e.getAsJsonObject();
            String item = stringOr(o, "item", "");
            if (item.isEmpty()) {
                continue;
            }
            out.add(new AlphaDrop(item, Math.max(1, Math.min(64, intOr(o, "count", 1))),
                    Math.max(1, intOr(o, "weight", 1))));
        }
        return out;
    }

    private static List<AlphaModifier.AttributeBonus> attributes(JsonElement element) {
        List<AlphaModifier.AttributeBonus> out = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            return out;
        }
        JsonArray arr = element.getAsJsonArray();
        for (JsonElement e : arr) {
            if (!e.isJsonObject()) {
                continue;
            }
            JsonObject o = e.getAsJsonObject();
            String attribute = stringOr(o, "attribute", "");
            String op = stringOr(o, "operation", "add_multiplied_base").toLowerCase(Locale.ROOT);
            double amount = doubleOr(o, "amount", 0.0D);
            if (attribute.isEmpty() || !OPERATIONS.contains(op) || amount == 0.0D || !Double.isFinite(amount)) {
                continue;
            }
            out.add(new AlphaModifier.AttributeBonus(attribute, amount, op));
        }
        return out;
    }

    private static String stringOr(JsonObject o, String key, String fallback) {
        JsonElement e = o.get(key);
        return e != null && e.isJsonPrimitive() ? e.getAsString() : fallback;
    }

    private static int intOr(JsonObject o, String key, int fallback) {
        JsonElement e = o.get(key);
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsInt() : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static double doubleOr(JsonObject o, String key, double fallback) {
        JsonElement e = o.get(key);
        try {
            return e != null && e.isJsonPrimitive() ? e.getAsDouble() : fallback;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
