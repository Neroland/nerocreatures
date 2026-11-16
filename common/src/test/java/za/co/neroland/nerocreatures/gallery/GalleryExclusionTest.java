package za.co.neroland.nerocreatures.gallery;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import za.co.neroland.nerocreatures.budget.BudgetSampler;
import za.co.neroland.nerocreatures.item.CaptureRules;

/**
 * Gallery displays are not creatures: every system that counts, targets, owns, scans, captures,
 * drops or links a creature asks {@code GalleryDisplays.isDisplay} first, and the demo scripts never
 * reach into ability code. The source checks below keep each gate from being dropped by a refactor;
 * the dev-only budget probe proves the same at runtime (build, count, clear, count again).
 */
class GalleryExclusionTest {

    private static final String PREDICATE = "GalleryDisplays.isDisplay";

    private static Path sourceRoot() {
        Path dir = Path.of("").toAbsolutePath();
        while (dir != null) {
            Path candidate = dir.resolve("common/src/main/java/za/co/neroland/nerocreatures");
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
            dir = dir.getParent();
        }
        return null;
    }

    private static String read(String relative) throws IOException {
        Path root = sourceRoot();
        assertNotNull(root, "source root found from the test working directory");
        return Files.readString(root.resolve(relative), StandardCharsets.UTF_8);
    }

    /** Census, caps, spawning neighbours, scans, capture, ownership, drops and the budget line. */
    @Test
    void everyCountingTargetingOwningAndDroppingPathAsksTheOnePredicate() throws IOException {
        List<String> gated = List.of(
                "spawn/CreatureCensus.java",                 // dimension, chunk and overworld caps
                "entity/alpha/AlphaSystem.java",             // alpha cap (countLoaded)
                "boss/BossSpawns.java",                      // one boss per dimension
                "entity/wrecks/WrecksSpawnGates.java",       // swarm and mimic area caps
                "entity/wrecks/ScrapHulkAssembly.java",      // hulk caps
                "entity/ai/shared/PredatorHuntGoal.java",    // predators never target displays
                "entity/ai/shared/PreyFleeGoal.java",        // prey never flee them, herds never alarm them
                "entity/ai/shared/HerdCohesionGoal.java",
                "entity/ai/PackCoordination.java",
                "entity/hostile/LunarStalker.java",
                "entity/humanoid/PirateCaptain.java",
                "entity/nyxara/PrismStag.java",
                "block/SonicWardBlockEntity.java",           // fields ignore displays
                "block/FeedingTroughBlockEntity.java",       // never bred by a trough
                "bestiary/BestiaryProgress.java",            // scans, kills, tames, studies
                "item/CapturePodItem.java",                  // never captured
                "entity/base/NeroCreatureEntity.java",       // no loot, no XP
                "entity/tame/TameableCreature.java",         // no taming, feeding, breeding, companion goals
                "entity/tame/LivestockCreature.java",
                "entity/mount/MountCreature.java",           // paddock ride only, no owner read
                "entity/greenxertz/BloomHost.java",          // gallery sporeling never expires
                "budget/CreatureBudget.java",                // one "gallery" line, outside the total
                "command/CreatureCommands.java");            // list command counts creatures only
        for (String file : gated) {
            assertTrue(read(file).contains(PREDICATE), file + " asks " + PREDICATE);
        }
        for (String file : List.of("entity/greenxertz/DriftLeviathan.java", "entity/mechanical/TerraformingDrone.java",
                "entity/wrecks/CacheMimic.java", "entity/wrecks/StaticSwarm.java", "entity/glacira/RimeYak.java",
                "entity/greenxertz/SporeDrifter.java", "entity/luna/LodeTortoise.java", "entity/mars/StrataHound.java")) {
            assertTrue(read(file).contains("GalleryDisplays.interact(this, player, hand)"),
                    file + " hands a display's interaction to the gallery");
        }
    }

    @Test
    void theLinkModuleOnlyReadsOwnershipRowsWhichTheGalleryNeverWrites() throws IOException {
        Path root = sourceRoot();
        assertNotNull(root);
        try (Stream<Path> files = Files.list(root.resolve("link"))) {
            for (Path file : files.toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                assertFalse(text.contains("getEntities("), file + " never enumerates loaded entities");
            }
        }
        try (Stream<Path> files = Files.list(root.resolve("gallery"))) {
            for (Path file : files.toList()) {
                String text = Files.readString(file, StandardCharsets.UTF_8);
                for (String forbidden : List.of("CreatureOwnershipState", "CreatureLink", "ProgressionGates",
                        "awardStat", "BossSummons", "BossContributionState", "finalizeSpawn(", ".tame(",
                        "setOwner", "Telemetry", "LOGGER", "LoggerFactory")) {
                    assertFalse(text.contains(forbidden), file.getFileName() + " must not use " + forbidden);
                }
            }
        }
    }

    @Test
    void platesPlayAScriptOfVisualsAndNeverInvokeAbilityCode() throws IOException {
        String demos = read("gallery/GalleryDemos.java");
        for (String forbidden : List.of("entity.ai", "Goal", "import za.co.neroland.nerocreatures.entity.luna",
                "import za.co.neroland.nerocreatures.entity.mars", "import za.co.neroland.nerocreatures.entity.glacira",
                "import za.co.neroland.nerocreatures.entity.greenxertz", "import za.co.neroland.nerocreatures.entity.wrecks",
                "import za.co.neroland.nerocreatures.entity.asteroid", "import za.co.neroland.nerocreatures.entity.hostile",
                "import za.co.neroland.nerocreatures.entity.tame", "import za.co.neroland.nerocreatures.entity.mount",
                "hurt", "heal(", "addEffect", "setDeltaMovement", "teleport", "spawnAtLocation", "addFreshEntity",
                "ItemEntity", "ExperienceOrb", "getInventory", "CreatureGriefing", "awardStat")) {
            assertFalse(demos.contains(forbidden), "GalleryDemos must not use " + forbidden);
        }
        assertTrue(demos.contains("playPose"), "a demo plays a pose");
        assertTrue(demos.contains("instabuild"), "only creative players reach a demo");
    }

    @Test
    void aDisplayIsNeverCaptured() {
        CaptureRules.Candidate display = new CaptureRules.Candidate(true, true, false, false, false, false, true,
                0.6F, 0.5F, false, false, false, false);
        assertEquals(CaptureRules.Verdict.DISPLAY, CaptureRules.mayCapture(display), "a display is refused");
    }

    @Test
    void theBudgetLineForDisplaysStaysOutOfTheTotal() {
        BudgetSampler<Object> sampler = new BudgetSampler<>();
        sampler.record("creature", 2_000_000L);
        sampler.record("gallery", 5_000_000L);
        BudgetSampler.Report report = sampler.report(1, String::valueOf, key -> "gallery".equals(key));
        assertTrue(Math.abs(report.totalMsPerTick() - 2.0D) < 1e-9, "total excludes the gallery line");
        assertTrue(report.rows().stream().anyMatch(r -> r.name().equals("gallery")), "gallery has its own line");
    }
}
