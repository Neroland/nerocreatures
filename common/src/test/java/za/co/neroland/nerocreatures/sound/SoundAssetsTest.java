package za.co.neroland.nerocreatures.sound;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.jupiter.api.Test;

/**
 * Stage 5 sound wiring, read from the classpath as the client would: every {@code sounds.json} event
 * has an English subtitle and its files, and the creatures that used to borrow vanilla sounds have
 * their own. ({@code ModSounds} and {@code sounds.json} are generated from one manifest;
 * {@code tools/sound/gen_sound_assets.py --check} proves they agree.)
 */
class SoundAssetsTest {

    private static JsonObject json(String path) throws IOException {
        try (InputStream in = SoundAssetsTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " on the classpath");
            return JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }

    @Test
    void everyEventHasASubtitleAndItsFiles() throws IOException {
        JsonObject sounds = json("/assets/nerocreatures/sounds.json");
        JsonObject lang = json("/assets/nerocreatures/lang/en_us.json");
        assertTrue(sounds.size() >= 187, "the full synthesised set");
        for (Map.Entry<String, JsonElement> event : sounds.entrySet()) {
            JsonObject body = event.getValue().getAsJsonObject();
            String subtitle = body.get("subtitle").getAsString();
            assertEquals("subtitles.nerocreatures." + event.getKey(), subtitle, "subtitle key follows the id");
            assertTrue(lang.has(subtitle) && !lang.get(subtitle).getAsString().isBlank(), subtitle + " in en_us");
            assertFalse(body.getAsJsonArray("sounds").isEmpty(), event.getKey() + " has files");
            for (JsonElement sound : body.getAsJsonArray("sounds")) {
                String name = sound.getAsJsonObject().get("name").getAsString();
                String file = "/assets/nerocreatures/sounds/" + name.substring(name.indexOf(':') + 1) + ".ogg";
                try (InputStream in = SoundAssetsTest.class.getResourceAsStream(file)) {
                    assertNotNull(in, file);
                }
            }
        }
    }

    @Test
    void creaturesHaveTheirOwnVoices() throws IOException {
        JsonObject sounds = json("/assets/nerocreatures/sounds.json");
        for (String id : new String[] {"scrap_hulk", "crystal_golem", "lunar_stalker", "void_manta",
                "cinder_tyrant", "rime_matriarch", "bloom_sovereign", "facet_colossus"}) {
            for (String kind : new String[] {"ambient", "hurt", "death"}) {
                assertTrue(sounds.has(id + "." + kind), id + "." + kind);
            }
        }
        for (String boss : new String[] {"cinder_tyrant", "rime_matriarch", "bloom_sovereign", "facet_colossus"}) {
            assertTrue(sounds.has(boss + ".phase") && sounds.has(boss + ".summon"), boss + " stingers");
        }
    }
}
