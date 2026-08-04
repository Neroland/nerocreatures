package za.co.neroland.nerocreatures.boss;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;

import org.jetbrains.annotations.Nullable;

import za.co.neroland.nerolandcore.event.ThresholdEvents;

import za.co.neroland.nerocreatures.NeroCreaturesCommon;
import za.co.neroland.nerocreatures.config.NeroCreaturesConfig;
import za.co.neroland.nerocreatures.link.CreatureLinkEvents;

/**
 * The reusable multi-phase state machine every NeroCreatures boss is built on. One controller is
 * attached to one boss entity and owns everything that makes a boss a boss rather than a large mob:
 *
 * <ul>
 *   <li><b>Phases.</b> An ordered list of {@link BossPhase}s with descending health thresholds. The
 *       controller swaps the boss's goal set on every transition (adding this phase's goals to the
 *       {@code goalSelector} and removing the previous phase's), runs the phase's one-off entry
 *       action and publishes a threshold crossing. Phases only ever advance.</li>
 *   <li><b>Boss bar.</b> A {@link ServerBossEvent} whose progress tracks health and whose title
 *       carries the current phase, shown to players within {@value #BAR_RADIUS} blocks and taken
 *       away again when they leave, die or the fight ends.</li>
 *   <li><b>Arena awareness.</b> An anchor position and radius captured when the boss arrives. Pull
 *       the boss beyond that radius — off a cliff, up a tower, into a tunnel — and it drops its
 *       target, walks home and <b>regenerates fast</b> on the way; drag it twice as far and it is
 *       simply put back. Kiting a boss out of its arena is not a strategy, it is a reset.</li>
 *   <li><b>Config scaling.</b> {@code bossHpMultiplier} and {@code bossDifficultyMultiplier} are
 *       applied once, to the boss's attribute <em>base values</em>, at spawn. Base values are saved
 *       with the entity, so this survives a reload without being re-applied (which would compound
 *       it) and a config change affects newly spawned bosses only.</li>
 *   <li><b>Contribution.</b> For a <b>summoned</b> fight only, damage per player is accumulated in
 *       {@link BossContributionState} and paid out by {@link BossRewards} on death. A naturally
 *       spawned boss records nothing at all and drops its plain loot table.</li>
 * </ul>
 *
 * <h2>Threshold events</h2>
 *
 * <p>Every transition and the defeat are published on Core's {@link ThresholdEvents} bus, channel
 * {@code nerocreatures:boss_pressure}, so NeroEvents (or anything else depending only on Core) can
 * react to a fight escalating without importing NeroCreatures. The contract, documented for
 * consumers in {@code wiki/Bosses.md}:
 *
 * <ul>
 *   <li><b>scope</b> — the <b>dimension id</b> the fight is happening in, as a string. It is a
 *       <em>place</em>. Core's Javadoc forbids encoding a person into a scope and this controller
 *       never does: no player id, name or position is ever put on the bus.</li>
 *   <li><b>value / threshold / rising</b> — on entering phase <i>n</i> (1-based), {@code value} and
 *       {@code threshold} are both <i>n</i> and {@code rising} is {@code true}. On defeat,
 *       {@code value} is {@code 0}, {@code threshold} is the boss's phase count and {@code rising}
 *       is {@code false}: the pressure has been resolved. <b>A defeat is the only crossing this
 *       channel publishes with {@code rising == false}</b>, so a consumer can tell "the fight got
 *       worse" from "the fight is over" with one boolean.</li>
 * </ul>
 *
 * <h2>Cost</h2>
 *
 * <p>{@link #tick} runs once per boss per server tick and is a handful of comparisons. The two
 * things that cost more than that are budgeted: the boss-bar audience is recomputed every
 * {@value #BAR_REFRESH_TICKS} ticks (one bounded player-list walk), and the walk-home navigation
 * call happens at most every {@value #RETURN_REPATH_TICKS} ticks and only while the boss is
 * actually outside its arena.
 *
 * <p>Server-authoritative throughout: the client is told a boss-bar and nothing else. No player
 * identity is logged on any path here (POPIA/GDPR) — contribution is stored by
 * {@link BossContributionState}, which documents its own controls.
 */
public final class BossController {

    /** Core threshold channel this controller publishes on. */
    public static final Identifier PRESSURE_CHANNEL =
            Identifier.fromNamespaceAndPath(NeroCreaturesCommon.MOD_ID, "boss_pressure");

    /** How far from the boss a player is shown its bar, in blocks. */
    private static final double BAR_RADIUS = 48.0D;

    /** Ticks between recomputing who can see the boss bar. */
    private static final int BAR_REFRESH_TICKS = 20;

    /** Ticks between "walk home" navigation calls while outside the arena. */
    private static final int RETURN_REPATH_TICKS = 20;

    /** Fraction of maximum health regenerated per {@link #RETURN_REPATH_TICKS} while returning. */
    private static final float RETURN_REGEN_FRACTION = 0.02F;

    /**
     * Multiple of the arena radius beyond which the boss stops walking and is simply put back. A
     * boss dragged into a place it cannot path out of would otherwise stand there regenerating
     * forever.
     */
    private static final double HARD_LEASH_MULTIPLE = 2.0D;

    private final Mob boss;
    private final BossGoals goals;
    private final Identifier bossId;
    private final Component bossName;
    private final List<BossPhase> phases;
    private final double arenaRadius;
    private final ServerBossEvent bar;

    /** Goals installed by the current phase, so exactly those can be removed again. */
    private final List<Goal> phaseGoals = new ArrayList<>();

    /** Where the fight belongs. Null until the boss has spawned or been loaded. */
    @Nullable
    private BlockPos anchor;

    /** Index into {@link #phases}; {@code -1} until the fight starts. */
    private int phaseIndex = -1;

    /**
     * The contribution fight this boss belongs to, or {@code null} for a naturally spawned boss.
     * Non-null is the <b>only</b> thing that makes damage get recorded — see the class docs.
     */
    @Nullable
    private UUID fightId;

    private boolean defeated;
    private int barTick;
    private int returnTick;

    /**
     * @param boss        the entity this controller drives
     * @param goals       the seam through which phase goals are installed on that entity — normally
     *                    the boss itself (see {@link BossGoals} for why this is not just
     *                    {@code boss.goalSelector})
     * @param bossId      the boss's registry-style id, used by the summon API and in logs
     * @param bossName    the boss's display name for the bar
     * @param colour      the boss bar colour
     * @param arenaRadius how far from its anchor the boss may be fought, in blocks
     * @param phases      the phase list, in descending health-threshold order; must not be empty
     */
    public BossController(Mob boss, BossGoals goals, Identifier bossId, Component bossName,
            BossEvent.BossBarColor colour, double arenaRadius, List<BossPhase> phases) {
        if (phases.isEmpty()) {
            throw new IllegalArgumentException("A boss must have at least one phase: " + bossId);
        }
        this.boss = boss;
        this.goals = goals;
        this.bossId = bossId;
        this.bossName = bossName;
        this.phases = List.copyOf(phases);
        this.arenaRadius = arenaRadius;
        this.bar = new ServerBossEvent(UUID.randomUUID(), bossName, colour,
                BossEvent.BossBarOverlay.PROGRESS);
        this.bar.setDarkenScreen(true);
        this.bar.setPlayBossMusic(true);
    }

    // --- lifecycle ----------------------------------------------------------

    /**
     * Starts the fight: captures the arena anchor, applies the config multipliers to the boss's
     * attribute base values and enters the first phase. Called from the boss's {@code finalizeSpawn}
     * — that is, exactly once, whether the boss was spawned naturally, summoned, placed by an egg or
     * placed by a command.
     */
    public void onSpawn(ServerLevel level, BlockPos anchor) {
        this.anchor = anchor;
        this.applyConfigScaling();
        this.enterPhase(level, 0, true);
    }

    /**
     * Marks this fight as contribution-tracked and binds it to a store row. Called by
     * {@link BossSummons} and by nothing else: contribution exists for summoned fights only, so a
     * player who stumbles onto a wild boss is never recorded anywhere.
     */
    void trackContribution(UUID fight) {
        this.bindFight(fight);
    }

    /**
     * The single place a fight id is adopted, whether it arrived from a fresh summon or from a
     * saved boss being read back in. Keeping both routes here is what lets {@link BossFights} be a
     * reliable live index rather than a "fights summoned since the last restart" list.
     */
    private void bindFight(UUID fight) {
        this.fightId = fight;
        BossFights.track(fight, this);
    }

    /** Drops this fight from the live index. Idempotent; safe to call on a natural fight. */
    private void unbindFight() {
        if (this.fightId != null) {
            BossFights.forget(this.fightId);
        }
    }

    /** Whether this fight is recording damage contribution (i.e. it was summoned). */
    public boolean tracksContribution() {
        return this.fightId != null;
    }

    /** The contribution fight id, or {@code null} for a natural fight. */
    @Nullable
    public UUID fightId() {
        return this.fightId;
    }

    /** The boss's id, as used by {@link BossSummons#summon}. */
    public Identifier bossId() {
        return this.bossId;
    }

    /** The current phase number, 1-based; {@code 0} before the fight has started. */
    public int phaseNumber() {
        return this.phaseIndex + 1;
    }

    /** How many phases this boss has. */
    public int phaseCount() {
        return this.phases.size();
    }

    /** The arena centre, or {@code null} if the fight has not started. */
    @Nullable
    public BlockPos anchor() {
        return this.anchor;
    }

    /** Whether this boss has already been defeated (its fight is over). */
    public boolean isDefeated() {
        return this.defeated;
    }

    /** The boss's remaining health as a {@code 0.0}–{@code 1.0} fraction of its maximum. */
    public float healthFraction() {
        float max = this.boss.getMaxHealth();
        return max <= 0.0F ? 0.0F : Math.clamp(this.boss.getHealth() / max, 0.0F, 1.0F);
    }

    /**
     * The id of the dimension this fight is happening in, or an empty string if the boss is not in a
     * server level. A <b>place</b> — the same thing the threshold channel publishes as its scope.
     */
    public String dimensionId() {
        return this.boss.level() instanceof ServerLevel level
                ? level.dimension().identifier().toString()
                : "";
    }

    // --- per-tick -----------------------------------------------------------

    /** Called once per server tick from the boss's {@code customServerAiStep}. */
    public void tick(ServerLevel level) {
        if (this.defeated || !this.boss.isAlive()) {
            return;
        }
        this.updateBar(level);
        this.updatePhase(level);
        this.enforceArena(level);
    }

    /** Progress every tick; audience only every {@value #BAR_REFRESH_TICKS} ticks. */
    private void updateBar(ServerLevel level) {
        float max = this.boss.getMaxHealth();
        this.bar.setProgress(max <= 0.0F ? 0.0F : Math.clamp(this.boss.getHealth() / max, 0.0F, 1.0F));
        if (++this.barTick < BAR_REFRESH_TICKS) {
            return;
        }
        this.barTick = 0;
        double radiusSqr = BAR_RADIUS * BAR_RADIUS;
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && player.distanceToSqr(this.boss) <= radiusSqr) {
                this.bar.addPlayer(player);
            } else {
                this.bar.removePlayer(player);
            }
        }
        // Anyone who changed dimension is not in level.players() any more and would otherwise keep
        // the bar; drop the ones the bar knows about that are no longer here.
        for (ServerPlayer tracked : List.copyOf(this.bar.getPlayers())) {
            if (tracked.isRemoved() || tracked.level() != level
                    || tracked.distanceToSqr(this.boss) > radiusSqr) {
                this.bar.removePlayer(tracked);
            }
        }
    }

    /** Advances to the deepest phase the boss's health has reached. Never rewinds. */
    private void updatePhase(ServerLevel level) {
        float max = this.boss.getMaxHealth();
        if (max <= 0.0F) {
            return;
        }
        float fraction = this.boss.getHealth() / max;
        int target = this.phaseIndex;
        for (int i = this.phaseIndex + 1; i < this.phases.size(); i++) {
            if (fraction <= this.phases.get(i).healthFraction()) {
                target = i;
            }
        }
        if (target > this.phaseIndex) {
            this.enterPhase(level, target, true);
        }
    }

    /**
     * Keeps the fight where it started. Outside the arena the boss stops fighting, walks home and
     * heals; far outside it, it is put back.
     */
    private void enforceArena(ServerLevel level) {
        if (this.anchor == null) {
            return;
        }
        double x = this.anchor.getX() + 0.5D;
        double y = this.anchor.getY();
        double z = this.anchor.getZ() + 0.5D;
        double distanceSqr = this.boss.distanceToSqr(x, y, z);
        if (distanceSqr <= this.arenaRadius * this.arenaRadius) {
            this.returnTick = 0;
            return;
        }
        this.boss.setTarget(null);
        double hardLeash = this.arenaRadius * HARD_LEASH_MULTIPLE;
        if (distanceSqr > hardLeash * hardLeash) {
            this.boss.getNavigation().stop();
            this.boss.snapTo(x, y, z, this.boss.getYRot(), this.boss.getXRot());
            this.boss.setHealth(this.boss.getMaxHealth());
            return;
        }
        if (++this.returnTick < RETURN_REPATH_TICKS) {
            return;
        }
        this.returnTick = 0;
        this.boss.getNavigation().moveTo(x, y, z, 1.0D);
        this.boss.heal(this.boss.getMaxHealth() * RETURN_REGEN_FRACTION);
    }

    // --- damage & death -----------------------------------------------------

    /**
     * Records a player's damage against this fight. A no-op unless the fight was summoned, so a
     * natural boss kill stores nothing about anybody.
     *
     * @param applied the damage that actually landed, already clamped by the caller to the health
     *                the boss had
     */
    public void recordDamage(ServerLevel level, DamageSource source, float applied) {
        if (this.fightId == null || applied <= 0.0F) {
            return;
        }
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        BossContributionState.get(level.getServer())
                .record(this.fightId, this.bossId, player.getUUID(), applied);
    }

    /**
     * Ends the fight: pays out the contribution rewards (summoned fights only), publishes the defeat
     * crossing and takes the bar away. The plain loot table has already dropped by the time this
     * runs — this is what a summon adds on top, never a replacement for it.
     */
    public void onDeath(ServerLevel level) {
        if (this.defeated) {
            return;
        }
        this.defeated = true;
        List<BossContributionState.Share> shares = List.of();
        if (this.fightId != null) {
            shares = BossContributionState.get(level.getServer()).resolve(this.fightId);
            BossRewards.payOut(level, this.boss.position(), shares);
        }
        this.fire(level, 0L, this.phases.size(), false);
        // The companion-app side of the same news: a broadcast event carrying no player data, plus
        // one private alert per recorded participant of a summoned fight.
        CreatureLinkEvents.bossDefeated(level, this.bossId, this.phases.size(),
                this.fightId != null, shares);
        this.unbindFight();
        this.bar.removeAllPlayers();
        this.bar.setVisible(false);
    }

    /**
     * Takes the bar away when the boss leaves the world for any other reason — despawn, a chunk
     * being unloaded, {@code /kill}. Without this the bar would stay on the screens of everyone who
     * could see it.
     */
    public void onRemoved(boolean keepContribution) {
        this.bar.removeAllPlayers();
        this.bar.setVisible(false);
        if (!keepContribution && !this.defeated && this.fightId != null
                && this.boss.level() instanceof ServerLevel level) {
            // The fight cannot be won any more, so nothing should be holding damage totals for it.
            BossContributionState.get(level.getServer()).discard(this.fightId);
        }
        // Out of the live index either way: an unloaded boss still has a fight, but it has no live
        // state to report until its chunk comes back and its save data is read again.
        this.unbindFight();
    }

    // --- phases -------------------------------------------------------------

    /**
     * Installs a phase. {@code runEntry} is {@code false} exactly once — when a saved boss is loaded
     * back in and its phase has to be re-installed. Entry actions (an enrage, a summon, a roar) are
     * one-off events in a fight, not properties of a phase, so replaying them on every chunk load
     * would compound an enrage every time the player walked away and came back.
     */
    private void enterPhase(ServerLevel level, int index, boolean runEntry) {
        for (Goal goal : this.phaseGoals) {
            this.goals.removePhaseGoal(goal);
        }
        this.phaseGoals.clear();
        this.phaseIndex = Math.clamp(index, 0, this.phases.size() - 1);
        BossPhase phase = this.phases.get(this.phaseIndex);
        for (BossPhase.PhaseGoal entry : phase.goals().get()) {
            this.goals.addPhaseGoal(entry.priority(), entry.goal());
            this.phaseGoals.add(entry.goal());
        }
        this.bar.setName(Component.translatable("boss.nerocreatures.bar", this.bossName, phase.title()));
        if (runEntry) {
            phase.onEnter().accept(level);
            this.fire(level, this.phaseNumber(), this.phaseNumber(), true);
            // Same news, second audience: a companion-app broadcast. Boss, phase and dimension —
            // nothing player-shaped, exactly like the threshold crossing above it.
            CreatureLinkEvents.bossPhase(level, this.bossId, this.phaseNumber(), this.phases.size(),
                    this.fightId != null);
        }
    }

    /**
     * Publishes one crossing. The scope is the dimension id — a place. Nothing player-shaped ever
     * goes on this bus (POPIA/GDPR; Core's {@code ThresholdCrossing} contract).
     */
    private void fire(ServerLevel level, long value, long threshold, boolean rising) {
        ThresholdEvents.fire(new ThresholdEvents.ThresholdCrossing(
                PRESSURE_CHANNEL, level.dimension().identifier().toString(), value, threshold, rising));
    }

    // --- config scaling -----------------------------------------------------

    /**
     * Applies {@code bossHpMultiplier} and {@code bossDifficultyMultiplier} to the boss's attribute
     * base values, once, at spawn. Base values persist in entity data, so this is not re-applied on
     * load — which is the point: applying it twice would square the multiplier.
     */
    private void applyConfigScaling() {
        double health = NeroCreaturesConfig.BOSS_HP_MULTIPLIER.get();
        if (health != 1.0D) {
            scale(Attributes.MAX_HEALTH, health);
        }
        double difficulty = NeroCreaturesConfig.BOSS_DIFFICULTY_MULTIPLIER.get();
        if (difficulty != 1.0D) {
            scale(Attributes.ATTACK_DAMAGE, difficulty);
        }
        this.boss.setHealth(this.boss.getMaxHealth());
    }

    private void scale(Holder<Attribute> attribute, double factor) {
        AttributeInstance instance = this.boss.getAttribute(attribute);
        if (instance != null) {
            instance.setBaseValue(instance.getBaseValue() * factor);
        }
    }

    /** How much a phase should scale a per-phase timer by. Higher difficulty = shorter cooldowns. */
    public static int scaleCooldown(int ticks) {
        double difficulty = NeroCreaturesConfig.BOSS_DIFFICULTY_MULTIPLIER.get();
        return Math.max(5, (int) Math.round(ticks / Math.max(0.1D, difficulty)));
    }

    // --- persistence --------------------------------------------------------

    /** Writes the fight state onto the boss's entity data. */
    public void save(ValueOutput output) {
        output.putInt("BossPhase", this.phaseIndex);
        if (this.anchor != null) {
            output.putInt("ArenaX", this.anchor.getX());
            output.putInt("ArenaY", this.anchor.getY());
            output.putInt("ArenaZ", this.anchor.getZ());
        }
        if (this.fightId != null) {
            output.putString("BossFight", this.fightId.toString());
        }
    }

    /**
     * Restores the fight state. Re-installs the saved phase's goal set <b>without</b> re-running its
     * entry action, and re-opens the bar; the audience rebuilds itself on the next refresh.
     */
    public void load(ValueInput input) {
        int savedPhase = input.getIntOr("BossPhase", -1);
        if (input.getIntOr("ArenaX", Integer.MIN_VALUE) != Integer.MIN_VALUE) {
            this.anchor = new BlockPos(input.getIntOr("ArenaX", 0), input.getIntOr("ArenaY", 0),
                    input.getIntOr("ArenaZ", 0));
        }
        String storedFight = input.getStringOr("BossFight", "");
        if (!storedFight.isEmpty()) {
            try {
                this.bindFight(UUID.fromString(storedFight));
            } catch (IllegalArgumentException ignored) {
                // A malformed id means the fight cannot be attributed; the boss stays a plain fight
                // rather than the load failing.
                this.fightId = null;
            }
        }
        this.bar.setVisible(true);
        if (savedPhase >= 0 && this.boss.level() instanceof ServerLevel level) {
            this.enterPhase(level, savedPhase, false);
        }
    }
}
