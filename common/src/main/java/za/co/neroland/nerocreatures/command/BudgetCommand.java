package za.co.neroland.nerocreatures.command;

import java.util.Locale;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import za.co.neroland.nerocreatures.budget.BudgetSampler;
import za.co.neroland.nerocreatures.budget.CreatureBudget;

/**
 * {@code /nerocreatures budget}: how much server tick time NeroCreatures' creatures cost, per
 * species and in total, measured over a sampling window.
 *
 * <pre>
 * /nerocreatures budget                 start a 10 s window, or show progress / the last result
 * /nerocreatures budget start [seconds] start a fresh window (1-60 s)
 * /nerocreatures budget stop            end the window now and show the result
 * </pre>
 *
 * <p>Operator-only through the {@code /nerocreatures} root's permission gate. Output goes to the
 * invoker alone ({@code sendSuccess(..., false)}) and names species ids and numbers only, never a
 * player or a position (POPIA/GDPR).
 */
final class BudgetCommand {

    /** Rows printed; the rest are summed into the total line anyway. */
    private static final int MAX_ROWS = 40;

    private BudgetCommand() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> node() {
        return Commands.literal("budget")
                .executes(ctx -> CreatureCommands.runSafely(ctx.getSource(), "budget", () -> status(ctx.getSource())))
                .then(Commands.literal("start")
                        .executes(ctx -> CreatureCommands.runSafely(ctx.getSource(), "budget start",
                                () -> start(ctx.getSource(), CreatureBudget.DEFAULT_SECONDS)))
                        .then(Commands.argument("seconds", IntegerArgumentType.integer(1, CreatureBudget.MAX_SECONDS))
                                .executes(ctx -> CreatureCommands.runSafely(ctx.getSource(), "budget start",
                                        () -> start(ctx.getSource(), IntegerArgumentType.getInteger(ctx, "seconds"))))))
                .then(Commands.literal("stop")
                        .executes(ctx -> CreatureCommands.runSafely(ctx.getSource(), "budget stop", () -> stop(ctx.getSource()))));
    }

    private static int status(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        if (server == null) {
            source.sendFailure(Component.translatable("command.nerocreatures.no_server"));
            return 0;
        }
        if (CreatureBudget.running(server)) {
            long left = CreatureBudget.ticksLeft(server);
            source.sendSuccess(() -> Component.translatable("command.nerocreatures.budget.running",
                    String.format(Locale.ROOT, "%.1f", left / 20.0D)), false);
            return 1;
        }
        if (CreatureBudget.hasResult()) {
            return print(source, CreatureBudget.report(server));
        }
        return start(source, CreatureBudget.DEFAULT_SECONDS);
    }

    private static int start(CommandSourceStack source, int seconds) {
        MinecraftServer server = source.getServer();
        if (server == null) {
            source.sendFailure(Component.translatable("command.nerocreatures.no_server"));
            return 0;
        }
        CreatureBudget.start(server, seconds);
        source.sendSuccess(() -> Component.translatable("command.nerocreatures.budget.started", seconds), false);
        return 1;
    }

    private static int stop(CommandSourceStack source) {
        MinecraftServer server = source.getServer();
        if (server == null) {
            source.sendFailure(Component.translatable("command.nerocreatures.no_server"));
            return 0;
        }
        CreatureBudget.stop(server);
        if (!CreatureBudget.hasResult()) {
            source.sendFailure(Component.translatable("command.nerocreatures.budget.none"));
            return 0;
        }
        return print(source, CreatureBudget.report(server));
    }

    private static int print(CommandSourceStack source, BudgetSampler.Report report) {
        source.sendSuccess(() -> Component.translatable("command.nerocreatures.budget.header",
                report.ticks(), String.format(Locale.ROOT, "%.1f", report.ticks() / 20.0D)), false);
        int shown = 0;
        for (BudgetSampler.Row row : report.rows()) {
            if (shown++ >= MAX_ROWS) {
                break;
            }
            source.sendSuccess(() -> Component.translatable("command.nerocreatures.budget.row",
                    row.name(), ms(row.msPerTick()), String.format(Locale.ROOT, "%.1f", row.callsPerTick())), false);
        }
        source.sendSuccess(() -> Component.translatable("command.nerocreatures.budget.total",
                ms(report.totalMsPerTick()), report.totalCalls()), false);
        return report.rows().size();
    }

    private static String ms(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
