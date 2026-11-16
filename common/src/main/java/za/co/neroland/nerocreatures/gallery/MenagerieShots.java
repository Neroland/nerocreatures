package za.co.neroland.nerocreatures.gallery;

import java.util.ArrayList;
import java.util.List;

/**
 * The capture harness's shot list, built from {@link MenagerieLayout} so the camera always matches the
 * scene. Pure: offsets from the gallery origin, with {@code y} relative to the floor layer {@code F}.
 *
 * <p>Sets (Menagerie pass, 160 shots): {@code overview} 3, {@code pavilions} 11, {@code creatures} 38,
 * {@code abilities} 46, {@code alphas} 16, {@code radiant} 14, {@code bosses} 16, {@code rows} 16. The
 * {@code grid} pass is separate (9 shots).
 */
public final class MenagerieShots {

    private MenagerieShots() {
    }

    /**
     * One framed capture.
     *
     * @param set    the set it belongs to
     * @param name   output file name, without extension
     * @param cam    camera offset {x, y, z}
     * @param target look-at offset {x, y, z}
     * @param pre    server commands run just before the shot (a demo trigger), or empty
     * @param delay  ticks to wait after {@code pre}
     * @param after  ticks to wait after the shot so the next one starts from idle
     */
    public record Shot(String set, String name, double[] cam, double[] target, List<String> pre, int delay,
            int after) {
    }

    public static final List<String> SETS = List.of("overview", "pavilions", "creatures", "abilities", "alphas",
            "radiant", "bosses", "rows");

    private static Shot shot(String set, String name, double cx, double cy, double cz, double tx, double ty,
            double tz) {
        return new Shot(set, name, new double[] {cx, cy, cz}, new double[] {tx, ty, tz}, List.of(), 0, 0);
    }

    /** The whole Menagerie pass, in set order. */
    public static List<Shot> menagerie() {
        List<Shot> out = new ArrayList<>();
        for (String set : SETS) {
            out.addAll(set(set));
        }
        return out;
    }

    /** One named set, or an empty list. */
    public static List<Shot> set(String set) {
        return switch (set) {
            case "overview" -> overview();
            case "pavilions" -> pavilions();
            case "creatures" -> creatures();
            case "abilities" -> abilities();
            case "alphas" -> alphas();
            case "radiant" -> radiant();
            case "bosses" -> bosses();
            case "rows" -> rows();
            default -> List.of();
        };
    }

    private static List<Shot> overview() {
        return List.of(
                shot("overview", "overview_top", 0.5, 70, 0.6, 0.5, 0, 0.5),
                shot("overview", "gallery_overview", -62, 34, 62, 0, 2, -6),
                shot("overview", "overview_street", -52, 3, 0.5, 52, 2, 0.5));
    }

    private static double streetZ(MenagerieLayout.Row row) {
        return switch (row) {
            case B -> -30.5;
            case C, D -> 0.5;
            case E -> 31.5;
        };
    }

    private static List<Shot> pavilions() {
        List<Shot> out = new ArrayList<>();
        for (String id : List.of("luna", "mars", "cindara", "glacira", "greenxertz", "nyxara", "asteroid",
                "wrecks_machines", "wrecks_crews", "deep_space")) {
            MenagerieLayout.Bay bay = MenagerieLayout.bay(id).orElseThrow();
            int inner = bay.innerX();
            int outer = bay.outerX();
            double step = inner < 0 ? 1.5 : -1.5;
            out.add(shot("pavilions", "pavilion_" + id, inner + 0.5 + step, 3.2, streetZ(bay.row()),
                    outer + 0.5, 1.8, bay.row().z(6) + 0.5));
        }
        out.add(shot("pavilions", "pavilion_leviathan", 42.5, 1.6, 3.5, 42.5, 6.5, 10.5));
        return out;
    }

    /** Portrait framing for an exhibit. {@code v} is the unit z step toward its street. */
    static Shot portrait(String set, String name, String entity, double ex, double ez, double y0, int v,
            double scale) {
        double[] size = MenagerieLayout.size(entity);
        double w = size[0] * scale;
        double h = size[1] * scale;
        double m = Math.max(w, h);
        return new Shot(set, name, new double[] {ex + 0.9 * m, y0 + 0.75 * h, ez + v * (1.4 + 1.5 * m)},
                new double[] {ex, y0 + 0.5 * h, ez}, List.of(), 0, 0);
    }

    private static Shot slotPortrait(String set, String name, MenagerieLayout.Slot slot) {
        double scale = slot.isAlpha() ? 1.3 : 1.0;
        return portrait(set, name, slot.entity(), slot.entityX(), slot.entityZ(), 1 + slot.hover(),
                slot.row().toStreet(), scale);
    }

    private static Shot bossPortrait(String set, String name, MenagerieLayout.BossPose pose) {
        return portrait(set, name, pose.boss(), pose.x() + 0.5, pose.z() + 0.5, 2, 1, 1.0);
    }

    private static List<Shot> creatures() {
        List<Shot> out = new ArrayList<>();
        for (MenagerieLayout.Slot slot : MenagerieLayout.baseSlots()) {
            out.add(slotPortrait("creatures", "creature_" + slot.entity(), slot));
        }
        for (MenagerieLayout.BossPose pose : MenagerieLayout.BOSS_POSES) {
            if (pose.phase() == 1) {
                out.add(bossPortrait("creatures", "creature_" + pose.boss(), pose));
            }
        }
        return out;
    }

    private static List<Shot> abilities() {
        List<Shot> out = new ArrayList<>();
        for (MenagerieLayout.Slot slot : MenagerieLayout.baseSlots()) {
            out.add(withDemo(slotPortrait("abilities", "ability_" + slot.suffix(), slot), slot.suffix()));
        }
        for (MenagerieLayout.BossPose pose : MenagerieLayout.BOSS_POSES) {
            out.add(withDemo(bossPortrait("abilities", "ability_" + pose.suffix(), pose), pose.suffix()));
        }
        return out;
    }

    private static Shot withDemo(Shot shot, String slot) {
        MenagerieLayout.Demo demo = MenagerieLayout.demo(slot).orElseThrow();
        return new Shot(shot.set(), shot.name(), shot.cam(), shot.target(),
                List.of("nerocreatures gallery demo " + slot), demo.shoot(),
                demo.ticks() - demo.shoot() + MenagerieLayout.DEMO_COOLDOWN_EXTRA);
    }

    private static List<Shot> alphas() {
        List<Shot> out = new ArrayList<>();
        for (MenagerieLayout.Slot alpha : MenagerieLayout.alphaSlots()) {
            MenagerieLayout.Slot base = MenagerieLayout.slot(alpha.entity()).orElse(alpha);
            double[] size = MenagerieLayout.size(alpha.entity());
            double w = size[0] * 1.3;
            double h = size[1] * 1.3;
            double m = Math.max(w, h);
            double y0 = 1 + alpha.hover();
            double mx = (alpha.entityX() + base.entityX()) / 2.0;
            double mz = alpha.entityZ();
            int v = alpha.row().toStreet();
            out.add(new Shot("alphas", "alpha_" + alpha.entity(), new double[] {mx, y0 + 0.8 * h,
                    mz + v * (3.0 + 1.5 * m)}, new double[] {mx, y0 + 0.5 * h, mz}, List.of(), 0, 0));
        }
        return out;
    }

    private static List<Shot> radiant() {
        List<Shot> out = new ArrayList<>();
        for (MenagerieLayout.TraitDisplay t : MenagerieLayout.TRAIT_WALL) {
            out.add(portrait("radiant", "radiant_" + t.entity(), t.entity(), t.x() + 0.5,
                    MenagerieLayout.TRAIT_Z + 0.5, 2 + t.hover(), 1, 1.0));
        }
        out.add(shot("radiant", "trait_wall", 29.5, 3.0, 33.5, 29.5, 2.0, 22.5));
        return out;
    }

    private static List<Shot> bosses() {
        List<Shot> out = new ArrayList<>();
        for (int i = 0; i < MenagerieLayout.BOSSES.size(); i++) {
            String boss = MenagerieLayout.BOSSES.get(i);
            int s = MenagerieLayout.ALCOVE_S[i];
            out.add(shot("bosses", "boss_" + boss, s + 11.5, 6, -31.5, s + 11.5, 3, -44.5));
            for (MenagerieLayout.BossPose pose : MenagerieLayout.BOSS_POSES) {
                if (pose.boss().equals(boss)) {
                    out.add(bossPortrait("bosses", "boss_" + boss + "_p" + pose.phase(), pose));
                }
            }
        }
        return out;
    }

    private static List<Shot> rows() {
        List<Shot> out = new ArrayList<>();
        out.add(shot("rows", "blocks_row", -44.5, 2.6, 30.5, -44.5, 2.0, 23.5));
        for (int b = 0; b < MenagerieLayout.HABITAT_BLOCKS.size(); b++) {
            double x = MenagerieLayout.BLOCKS_FIRST_X + 2 * b + 0.5;
            out.add(shot("rows", "block_" + MenagerieLayout.HABITAT_BLOCKS.get(b), x + 0.6, 2.9, 26.0, x, 2.5,
                    23.5));
        }
        Shot crust = shot("rows", "block_ephemeral_crust", -35.9, 2.9, 26.4, -35.5, 2.5, 23.5);
        out.add(new Shot(crust.set(), crust.name(), crust.cam(), crust.target(),
                List.of("nerocreatures gallery demo " + MenagerieLayout.CRUST), 30, 70 + 40));
        out.add(shot("rows", "gear_row", -45.5, 2.6, -29.5, -45.5, 2.2, -23.5));
        out.add(shot("rows", "drops_row", 47.5, 4.0, -0.5, 47.5, 3.5, -9.5));
        out.add(shot("rows", "hatchery", 47.5, 2.6, -29.5, 47.5, 1.8, -21.5));
        out.add(shot("rows", "kiosk", -51.5, 2.6, 1.5, -51.5, 1.8, 8.5));
        out.add(shot("rows", "paddock_lopers", -28, 4, 33.5, -28, 1.5, 44.5));
        out.add(shot("rows", "paddock_mantas", 28, 4, 33.5, 28, 2.0, 44.5));
        return out;
    }

    /** The grid pass: the 0.4.x shot names re-aimed at the grid rows, plus three new ones. */
    public static List<Shot> grid() {
        List<Shot> out = new ArrayList<>();
        out.add(shot("grid", "creature_lineup", 9.5, 3.0, -34, 46, 1.8, -21));
        out.add(shot("grid", "creature_hostiles", 9.5, 2.4, -28, 26, 1.6, -24));
        out.add(shot("grid", "creature_machines", 9.5, 2.6, -16, 30, 1.8, -12));
        out.add(shot("grid", "boss_closeup", 18.5, 4.0, 2.5, 32.5, 2.5, -3.5));
        out.add(shot("grid", "drops", -17, 6.0, -16, -17, 1.2, -4));
        out.add(shot("grid", "spawn_eggs", -17, 6.0, 16, -17, 1.2, 4));
        out.add(shot("grid", "grid_radiant", 9.5, 3.0, 14, 40, 1.6, 21));
        out.add(shot("grid", "grid_alphas", 9.5, 3.0, 26, 40, 1.8, 33));
        out.add(shot("grid", "grid_overview", -30, 30, 44, 14, 2, 0));
        return out;
    }
}
