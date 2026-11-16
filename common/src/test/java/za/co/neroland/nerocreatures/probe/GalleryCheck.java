package za.co.neroland.nerocreatures.probe;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;

import za.co.neroland.nerocreatures.bestiary.BestiaryProgress;
import za.co.neroland.nerocreatures.entity.alpha.AlphaSystem;
import za.co.neroland.nerocreatures.entity.base.NeroCreatureEntity;
import za.co.neroland.nerocreatures.entity.tame.TameableCreature;
import za.co.neroland.nerocreatures.gallery.GalleryDemos;
import za.co.neroland.nerocreatures.gallery.GalleryDisplays;
import za.co.neroland.nerocreatures.gallery.MenagerieBuilder;
import za.co.neroland.nerocreatures.gallery.MenagerieLayout;
import za.co.neroland.nerocreatures.spawn.CreatureCensus;

/**
 * DEV-ONLY (test source set), driven by {@link BudgetProbe}: builds the Menagerie headlessly with no
 * player, checks its counts against the layout and the budget, checks that displays stay out of the
 * census, the alpha cap, scans and ownership, runs demos up to the running cap, clears it and checks
 * that only tagged entities went (two untagged creatures inside the box must survive), then does the
 * same for the grid. Numbers only; nothing about a player.
 */
final class GalleryCheck {

    /** What the probe run gives this check. */
    interface Host {
        ServerLevel level();

        StringBuilder out();

        void fail();
    }

    private enum Stage { START, BUILDING, SETTLE, DEMOS, CLEARING, GRID, GRID_CLEARING, DONE }

    private final Host host;
    private BlockPos origin;
    private final List<Mob> sentinels = new ArrayList<>();
    private Stage stage = Stage.START;
    private int wait;
    private boolean jobDone;

    GalleryCheck(Host host) {
        this.host = host;
    }

    /** One probe tick; true once the whole check has finished. Builds and clears run over ticks. */
    boolean tick() {
        switch (this.stage) {
            case START -> {
                build();
                this.stage = Stage.BUILDING;
            }
            case BUILDING -> {
                if (this.jobDone) {
                    this.wait = 120;
                    this.stage = Stage.SETTLE;
                }
            }
            case SETTLE -> {
                if (--this.wait <= 0) {
                    check();
                    demos();
                    this.wait = 200;
                    this.stage = Stage.DEMOS;
                }
            }
            case DEMOS -> {
                if (--this.wait <= 0) {
                    demosDone();
                    clear();
                    this.stage = Stage.CLEARING;
                }
            }
            case CLEARING -> {
                if (this.jobDone) {
                    verifyCleared(false);
                    grid();
                    this.stage = Stage.GRID;
                }
            }
            case GRID -> {
                if (this.jobDone) {
                    this.jobDone = false;
                    MenagerieBuilder.startClear(this.host.level(), this.origin, MenagerieLayout.GRID_CLEAR_BOX,
                            r -> this.jobDone = true);
                    this.stage = Stage.GRID_CLEARING;
                }
            }
            case GRID_CLEARING -> {
                if (this.jobDone) {
                    verifyCleared(true);
                    this.stage = Stage.DONE;
                }
            }
            case DONE -> {
                return true;
            }
        }
        return false;
    }

    private void line(String text) {
        this.host.out().append("  ").append(text).append('\n');
    }

    private void expect(boolean ok, String what) {
        line((ok ? "ok   " : "FAIL ") + what);
        if (!ok) {
            this.host.fail();
        }
    }

    private AABB box(MenagerieLayout.Box b) {
        return new AABB(this.origin.getX() + b.minX(), this.origin.getY() + b.minY() - 1, this.origin.getZ() + b.minZ(),
                this.origin.getX() + b.maxX() + 1, this.origin.getY() + b.maxY() + 4, this.origin.getZ() + b.maxZ() + 1);
    }

    void build() {
        ServerLevel level = this.host.level();
        this.origin = new BlockPos(0, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, 0, 0), 0);
        this.host.out().append("\nGallery check (headless, no player)\n");
        // Two untagged creatures inside the clear box stand in for a player's own animals.
        for (String id : List.of("mossback", "lumen_moth")) {
            EntityType<?> type = GalleryDisplays.type(id);
            Entity created = type == null ? null : type.create(level, EntitySpawnReason.COMMAND);
            if (created instanceof Mob mob) {
                mob.snapTo(this.origin.getX() + (id.equals("mossback") ? 20.5D : -20.5D), this.origin.getY() + 1,
                        this.origin.getZ() + 40.5D, 0.0F, 0.0F);
                mob.setNoAi(true);
                mob.setPersistenceRequired();
                if (level.addFreshEntity(mob)) {
                    this.sentinels.add(mob);
                }
            }
        }
        this.jobDone = false;
        MenagerieBuilder.startMenagerie(level, this.origin, this::built);
    }

    private void built(MenagerieBuilder.Stats s) {
        this.jobDone = true;
        line(String.format(Locale.ROOT, "Menagerie built over %d ticks, %.1f ms of work, longest tick %.1f ms:"
                + " %d creatures, %d boss poses, %d items, %d blocks placed, %d mobs, %d entities", s.ticks(),
                s.micros() / 1000.0D, s.maxTickMicros() / 1000.0D, s.creatures(), s.bossPoses(), s.items(), s.blocks(),
                s.mobs(), s.entities()));
        line(String.format(Locale.ROOT, "slowest build step #%d: %.1f ms", s.slowestStep(),
                s.slowestStepMicros() / 1000.0D));
        expect(s.maxTickMicros() <= 150_000L, "no single tick of build work over 150 ms");
        expect(s.missing().isEmpty(), "self-check: nothing missing " + s.missing());
        expect(!s.overBudget(), "within budget (mobs <= 96, entities <= 260, blocks <= 30000)");
        expect(s.creatures() == 34 && s.bossPoses() == 12, "34 creatures and 12 boss poses on show");
        expect(s.mobs() == MenagerieLayout.menagerieMobs(), "mob displays = layout (" + MenagerieLayout.menagerieMobs() + ")");
        expect(s.entities() == MenagerieLayout.menagerieEntities(), "entities = layout ("
                + MenagerieLayout.menagerieEntities() + ")");
    }

    void check() {
        ServerLevel level = this.host.level();
        AABB all = box(MenagerieLayout.CLEAR_BOX);
        List<Entity> tagged = level.getEntitiesOfClass(Entity.class, all, GalleryDisplays::isDisplay);
        long mobs = tagged.stream().filter(e -> e instanceof Mob).count();
        expect(tagged.size() == MenagerieLayout.menagerieEntities(), "tagged entities loaded: " + tagged.size());
        expect(mobs == MenagerieLayout.menagerieMobs(), "tagged mobs loaded: " + mobs);
        int untagged = level.getEntities(net.minecraft.world.level.entity.EntityTypeTest.forClass(Entity.class),
                e -> (e instanceof NeroCreatureEntity || e instanceof TameableCreature) && !GalleryDisplays.isDisplay(e))
                .size();
        int census = CreatureCensus.inDimension(level);
        expect(census == untagged, "census counts no display (census " + census + ", untagged creatures " + untagged + ")");
        int alphas = AlphaSystem.countLoaded(level, 100);
        expect(alphas == 0, "alpha cap counts no gallery alpha (" + alphas + " counted, 16 on show)");
        long scannable = tagged.stream().filter(e -> !BestiaryProgress.isGallery(e)).count();
        expect(scannable == 0, "every display is refused by the scanner");
        long owned = tagged.stream().filter(e -> e instanceof TameableCreature t && t.isTame()).count();
        expect(owned == 0, "no display is tamed or owned");
        long items = level.getEntitiesOfClass(ItemEntity.class, all, e -> true).size();
        expect(items == 0, "no item entity in the gallery (" + items + ")");
        long paddock = tagged.stream().filter(GalleryDisplays::isPaddock).count();
        expect(paddock == 4, "4 paddock mounts");
    }

    void demos() {
        ServerLevel level = this.host.level();
        List<String> slots = List.of("lumen_moth", "lode_tortoise", "magma_wader", "spore_drifter", "rime_yak");
        List<GalleryDemos.Result> results = new ArrayList<>();
        for (String slot : slots) {
            results.add(GalleryDemos.trigger(level, this.origin, slot));
        }
        line("demo triggers: " + results);
        expect(results.subList(0, 4).stream().allMatch(r -> r == GalleryDemos.Result.STARTED)
                && results.get(4) == GalleryDemos.Result.BUSY, "4 demos run at once, the 5th is refused");
        expect(GalleryDemos.running() <= MenagerieLayout.MAX_RUNNING_DEMOS, "running demos <= 4");
        expect(GalleryDemos.trigger(level, this.origin, "lumen_moth") == GalleryDemos.Result.COOLDOWN,
                "a replay inside the cooldown is refused");
    }

    void demosDone() {
        ServerLevel level = this.host.level();
        expect(GalleryDemos.running() == 0, "every demo ended by itself");
        long items = level.getEntitiesOfClass(Entity.class, box(MenagerieLayout.CLEAR_BOX),
                e -> e instanceof ItemEntity || e instanceof ExperienceOrb).size();
        expect(items == 0, "demos dropped nothing and gave no XP");
        BlockPos light = this.origin.offset(-42, 3, MenagerieLayout.Row.C.z(4));
        expect(level.getBlockState(light).isAir(), "the Lumen Moth demo removed its light prop");
    }

    void clear() {
        ServerLevel level = this.host.level();
        GalleryDemos.stopAt(level, this.origin);
        this.jobDone = false;
        long start = level.getGameTime();
        MenagerieBuilder.startClear(level, this.origin, MenagerieLayout.CLEAR_BOX, r -> {
            this.jobDone = true;
            line("clear over " + (level.getGameTime() - start + 1) + " ticks: " + r[0] + " blocks to air, " + r[1]
                    + " entities removed");
        });
    }

    void verifyCleared(boolean grid) {
        ServerLevel level = this.host.level();
        MenagerieLayout.Box b = grid ? MenagerieLayout.GRID_CLEAR_BOX : MenagerieLayout.CLEAR_BOX;
        long tagged = level.getEntitiesOfClass(Entity.class, box(b), GalleryDisplays::isDisplay).size();
        expect(tagged == 0, (grid ? "grid" : "Menagerie") + " clear removed every tagged entity (" + tagged + " left)");
        long alive = this.sentinels.stream().filter(m -> m.isAlive() && !m.isRemoved()).count();
        expect(alive == this.sentinels.size(), "untagged creatures in the box survive clear (" + alive + "/"
                + this.sentinels.size() + ")");
        int blocks = 0;
        BlockPos.MutableBlockPos c = new BlockPos.MutableBlockPos();
        for (int x = b.minX(); x <= b.maxX(); x++) {
            for (int z = b.minZ(); z <= b.maxZ(); z++) {
                for (int y = b.minY(); y <= b.maxY(); y++) {
                    c.set(this.origin.getX() + x, this.origin.getY() + y, this.origin.getZ() + z);
                    if (!level.getBlockState(c).isAir()) {
                        blocks++;
                    }
                }
            }
        }
        expect(blocks == 0, "clear box is air from F up (" + blocks + " blocks left)");
    }

    void grid() {
        this.jobDone = false;
        MenagerieBuilder.startGrid(this.host.level(), this.origin, s -> {
            this.jobDone = true;
            line(String.format(Locale.ROOT, "Grid built over %d ticks, %.1f ms of work, longest tick %.1f ms:"
                    + " %d mobs, %d items, %d blocks, %d entities", s.ticks(), s.micros() / 1000.0D,
                    s.maxTickMicros() / 1000.0D, s.mobs(), s.items(), s.blocks(), s.entities()));
            expect(s.missing().isEmpty() && !s.overBudget() && s.mobs() == MenagerieLayout.gridMobs(),
                    "grid: nothing missing, within budget, " + MenagerieLayout.gridMobs() + " mobs");
        });
    }
}
