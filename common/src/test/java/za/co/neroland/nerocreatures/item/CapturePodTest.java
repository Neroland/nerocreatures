package za.co.neroland.nerocreatures.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;

import za.co.neroland.nerocreatures.block.CapturedCreature;
import za.co.neroland.nerocreatures.trait.TraitPool;
import za.co.neroland.nerocreatures.trait.TraitSet;

/** Capture Pod: the round trip, who may be captured, release rules, and duplication. */
class CapturePodTest {

    // --- round trip ---------------------------------------------------------------------------

    /** Save data shaped like a tamed pet's, with every identity key the entity really writes. */
    private static CompoundTag tamedPetSave(TraitSet traits) {
        CompoundTag data = new CompoundTag();
        data.putIntArray("UUID", new int[] {11, 22, 33, 44});
        data.putIntArray("Owner", new int[] {55, 66, 77, 88});
        data.putIntArray("LoveCause", new int[] {1, 2, 3, 4});
        data.putString("Command", "follow");
        data.putBoolean("Sitting", true);
        data.putBoolean("nerocreatures:husbanded", true);
        data.putString("CustomName", "\"Moth\"");
        data.put("Pos", new CompoundTag());
        data.put(TraitSet.NBT_KEY, traits.toTag());
        data.putBoolean(CaptureRules.WAS_TAMED_KEY, true);
        return data;
    }

    @Test
    void roundTripKeepsTraitsAndStoresNoIdentity() {
        TraitSet traits = TraitSet.of(TraitPool.HARDY, TraitPool.SWIFT);
        CapturedCreature captured = CapturedCreature.of("nerocreatures:lumen_moth", tamedPetSave(traits),
                Optional.of(traits), false);

        CapturedCreature back = CapturedCreature.fromTag(captured.toTag()).orElseThrow();

        assertTrue(back.understood());
        assertEquals("nerocreatures:lumen_moth", back.species());
        assertEquals(Optional.of(traits), back.traits());
        // The entity's own trait copy (what the released creature reads) survives too.
        assertEquals(Optional.of(traits), TraitSet.fromTag(back.entity().get(TraitSet.NBT_KEY)));
        assertTrue(back.entity().contains("nerocreatures:husbanded"));
        assertTrue(back.entity().contains(CaptureRules.WAS_TAMED_KEY), "the tamed flag is a boolean only");
        for (String key : new String[] {"UUID", "Owner", "LoveCause", "Pos", "Sitting"}) {
            assertFalse(back.entity().contains(key), key);
        }
        String stored = captured.toTag().toString();
        assertFalse(stored.contains("55,66,77,88") || stored.contains("55, 66, 77, 88"), "owner ints stored");
        assertFalse(stored.contains("11,22,33,44") || stored.contains("11, 22, 33, 44"), "uuid ints stored");
    }

    @Test
    void aPodWrittenWithIdentityIsStrippedOnRead() {
        // A pod from an earlier build or another mod: the raw record bypasses CapturedCreature.of.
        CompoundTag raw = tamedPetSave(TraitSet.of(TraitPool.HARDY, TraitPool.SWIFT));
        raw.putIntArray("last_hurt_by_player", new int[] {9, 8, 7, 6});
        raw.put("neoforge:attachments", new CompoundTag());
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf("nerocreatures.nest:minecraft:overworld/1/2/3"));
        tags.add(StringTag.valueOf("keep_me"));
        raw.put("Tags", tags);
        CapturedCreature unsafe = new CapturedCreature(CapturedCreature.CURRENT_VERSION, "nerocreatures:lumen_moth",
                raw, Optional.empty(), false);

        CapturedCreature read = CapturedCreature.fromTag(unsafe.toTag()).orElseThrow();

        for (String key : new String[] {"UUID", "Owner", "LoveCause", "last_hurt_by_player", "neoforge:attachments",
                "Command"}) {
            assertFalse(read.entity().contains(key), key);
        }
        String tagsAfter = String.valueOf(read.entity().get("Tags"));
        assertFalse(tagsAfter.contains("nerocreatures.nest:"), "a nest binding is a coordinate: " + tagsAfter);
        assertTrue(tagsAfter.contains("keep_me"), tagsAfter);
        assertTrue(read.entity().contains("CustomName"));
    }

    // --- capture eligibility ------------------------------------------------------------------

    private static CaptureRules.Candidate wildMoth() {
        return new CaptureRules.Candidate(true, false, false, false, false, false, true, 0.6F, 0.6F, false,
                false, false, false);
    }

    private static CaptureRules.Candidate with(CaptureRules.Candidate c, String what) {
        return new CaptureRules.Candidate(
                !what.equals("dead") && c.alive(), what.equals("display") || c.display(),
                what.equals("hostile") || c.hostile(), what.equals("boss") || c.boss(),
                what.equals("alpha") || c.alpha(), what.equals("mount") || c.mount(),
                !what.equals("untagged") && c.inTag(), what.equals("big") ? 1.4F : c.width(), c.height(),
                what.equals("leashed") || c.attached(),
                what.startsWith("tamed") || c.tamed(),
                what.equals("tamed_mine") || what.equals("tamed_erased") || c.ownerIsUser(),
                what.equals("tamed_mine") || c.storeAgrees());
    }

    @Test
    void passiveWildCreatureCanBeCaptured() {
        assertEquals(CaptureRules.Verdict.OK, CaptureRules.mayCapture(wildMoth()));
    }

    @Test
    void refusesHostilesBossesAlphasMountsDisplaysAndTheUntagged() {
        CaptureRules.Candidate moth = wildMoth();
        assertEquals(CaptureRules.Verdict.DEAD, CaptureRules.mayCapture(with(moth, "dead")));
        assertEquals(CaptureRules.Verdict.DISPLAY, CaptureRules.mayCapture(with(moth, "display")));
        assertEquals(CaptureRules.Verdict.HOSTILE, CaptureRules.mayCapture(with(moth, "hostile")));
        assertEquals(CaptureRules.Verdict.BOSS, CaptureRules.mayCapture(with(moth, "boss")));
        assertEquals(CaptureRules.Verdict.ALPHA, CaptureRules.mayCapture(with(moth, "alpha")));
        assertEquals(CaptureRules.Verdict.MOUNT, CaptureRules.mayCapture(with(moth, "mount")));
        assertEquals(CaptureRules.Verdict.NOT_CAPTURABLE, CaptureRules.mayCapture(with(moth, "untagged")));
        assertEquals(CaptureRules.Verdict.TOO_BIG, CaptureRules.mayCapture(with(moth, "big")));
        assertEquals(CaptureRules.Verdict.ATTACHED, CaptureRules.mayCapture(with(moth, "leashed")));
    }

    @Test
    void onlyTheOwnerCapturesAPetAndOnlyWhenTheStoreAgrees() {
        CaptureRules.Candidate moth = wildMoth();
        assertEquals(CaptureRules.Verdict.NOT_YOURS, CaptureRules.mayCapture(with(moth, "tamed_other")));
        assertEquals(CaptureRules.Verdict.NOT_YOURS, CaptureRules.mayCapture(with(moth, "tamed_erased")));
        assertEquals(CaptureRules.Verdict.OK, CaptureRules.mayCapture(with(moth, "tamed_mine")));
    }

    // --- release ------------------------------------------------------------------------------

    @Test
    void releaseRules() {
        assertEquals(CaptureRules.Release.OK, CaptureRules.mayRelease(true, true, true, true, true, false, 99, 0));
        assertEquals(CaptureRules.Release.EMPTY, CaptureRules.mayRelease(false, true, true, true, true, false, 0, 4));
        assertEquals(CaptureRules.Release.UNKNOWN, CaptureRules.mayRelease(true, false, true, true, true, false, 0, 4));
        assertEquals(CaptureRules.Release.NOT_ALLOWED, CaptureRules.mayRelease(true, true, false, true, true, false, 0, 4));
        assertEquals(CaptureRules.Release.UNLOADED, CaptureRules.mayRelease(true, true, true, false, true, false, 0, 4));
        assertEquals(CaptureRules.Release.BLOCKED, CaptureRules.mayRelease(true, true, true, true, false, false, 0, 4));
    }

    @Test
    void aTamedReleaseRespectsTheReleasersPetCap() {
        assertEquals(CaptureRules.Release.OK, CaptureRules.mayRelease(true, true, true, true, true, true, 3, 4));
        assertEquals(CaptureRules.Release.PET_CAP, CaptureRules.mayRelease(true, true, true, true, true, true, 4, 4));
        assertEquals(CaptureRules.Release.PET_CAP, CaptureRules.mayRelease(true, true, true, true, true, true, 0, 0));
    }

    @Test
    void aNewerCaptureIsNeverReleased() {
        CapturedCreature future = new CapturedCreature(CapturedCreature.CURRENT_VERSION + 1, "nerocreatures:lumen_moth",
                new CompoundTag(), Optional.empty(), false);
        assertFalse(future.understood());
    }

    // --- duplication --------------------------------------------------------------------------

    /** A fake pod: one slot, plus whether it is still in hand. */
    private static final class FakePod implements CaptureFlow.Pod<String> {
        boolean held = true;
        int count = 1;
        String contents;

        @Override
        public boolean heldAndSingle() {
            return this.held && this.count == 1;
        }

        @Override
        public boolean filled() {
            return this.contents != null;
        }

        @Override
        public String contents() {
            return this.contents;
        }

        @Override
        public void fill(String snapshot) {
            this.contents = snapshot;
        }

        @Override
        public void empty() {
            this.contents = null;
        }
    }

    /** A fake creature in the world. */
    private static final class FakeCreature implements CaptureFlow.Target<String> {
        boolean present = true;
        boolean removable = true;
        boolean diesWhileSnapshotting;

        @Override
        public boolean present() {
            return this.present;
        }

        @Override
        public String snapshot() {
            if (this.diesWhileSnapshotting) {
                this.present = false;
                return null;
            }
            return "moth";
        }

        @Override
        public boolean remove() {
            if (!this.removable) {
                return false;
            }
            this.present = false;
            return true;
        }
    }

    /** Creatures in the world plus creatures in pods: must stay exactly 1. */
    private static int copies(FakePod pod, FakeCreature creature) {
        return (creature.present ? 1 : 0) + (pod.filled() ? 1 : 0);
    }

    @Test
    void captureMovesTheCreatureIntoThePod() {
        FakePod pod = new FakePod();
        FakeCreature moth = new FakeCreature();
        assertEquals(CaptureFlow.Outcome.CAPTURED, CaptureFlow.capture(pod, moth));
        assertEquals("moth", pod.contents);
        assertEquals(1, copies(pod, moth));
    }

    @Test
    void podDroppedMidCaptureCapturesNothing() {
        FakePod pod = new FakePod();
        pod.held = false; // dropped (or swapped) between the click and the server handling it
        FakeCreature moth = new FakeCreature();
        assertEquals(CaptureFlow.Outcome.POD_REFUSED, CaptureFlow.capture(pod, moth));
        assertTrue(moth.present);
        assertEquals(1, copies(pod, moth));
    }

    @Test
    void aStackOfPodsOrAFilledPodRefuses() {
        FakePod stacked = new FakePod();
        stacked.count = 2;
        assertEquals(CaptureFlow.Outcome.POD_REFUSED, CaptureFlow.capture(stacked, new FakeCreature()));
        FakePod full = new FakePod();
        full.contents = "other";
        FakeCreature moth = new FakeCreature();
        assertEquals(CaptureFlow.Outcome.POD_REFUSED, CaptureFlow.capture(full, moth));
        assertEquals("other", full.contents);
        assertTrue(moth.present);
    }

    @Test
    void creatureThatDiesDuringCaptureLeavesThePodEmpty() {
        FakePod pod = new FakePod();
        FakeCreature dead = new FakeCreature();
        dead.present = false;
        assertEquals(CaptureFlow.Outcome.GONE, CaptureFlow.capture(pod, dead));
        assertFalse(pod.filled());

        FakePod pod2 = new FakePod();
        FakeCreature dying = new FakeCreature();
        dying.diesWhileSnapshotting = true;
        assertEquals(CaptureFlow.Outcome.GONE, CaptureFlow.capture(pod2, dying));
        assertFalse(pod2.filled());
    }

    @Test
    void aFailedRemovalRollsThePodBack() {
        FakePod pod = new FakePod();
        FakeCreature stuck = new FakeCreature();
        stuck.removable = false;
        assertEquals(CaptureFlow.Outcome.GONE, CaptureFlow.capture(pod, stuck));
        assertNull(pod.contents);
        assertEquals(1, copies(pod, stuck));
    }

    @Test
    void releaseIntoAWallKeepsThePodFull() {
        FakePod pod = new FakePod();
        pod.contents = "moth";
        int[] spawned = {0};
        assertEquals(CaptureFlow.Outcome.KEPT, CaptureFlow.release(pod, s -> false));
        assertEquals("moth", pod.contents);
        assertEquals(CaptureFlow.Outcome.RELEASED, CaptureFlow.release(pod, s -> {
            spawned[0]++;
            return true;
        }));
        assertNull(pod.contents);
        assertEquals(1, spawned[0]);
        // A second release of the now-empty pod spawns nothing.
        assertEquals(CaptureFlow.Outcome.POD_REFUSED, CaptureFlow.release(pod, s -> {
            spawned[0]++;
            return true;
        }));
        assertEquals(1, spawned[0]);
    }

    @Test
    void podDroppedBeforeReleaseSpawnsNothing() {
        FakePod pod = new FakePod();
        pod.contents = "moth";
        pod.held = false;
        int[] spawned = {0};
        assertEquals(CaptureFlow.Outcome.POD_REFUSED, CaptureFlow.release(pod, s -> {
            spawned[0]++;
            return true;
        }));
        assertEquals(0, spawned[0]);
        assertEquals("moth", pod.contents);
    }
}
