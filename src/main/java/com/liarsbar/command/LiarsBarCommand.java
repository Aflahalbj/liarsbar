package com.liarsbar.command;

import com.liarsbar.game.deck.DeckSimulator;
import com.liarsbar.game.deck.LiarsDeckRules;
import com.liarsbar.table.TableInstance;
import com.liarsbar.table.TableManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/** Root semua perintah mod: {@code /liarsbar ...} (OP level 2). */
public final class LiarsBarCommand {

    private static final int MAX_SIM_GAMES = 2000;
    private static final SimpleCommandExceptionType NO_TABLE =
            new SimpleCommandExceptionType(Component.translatable("liarsbar.command.no_table"));
    private static final SimpleCommandExceptionType NO_STRUCTURE =
            new SimpleCommandExceptionType(Component.translatable("liarsbar.command.bar.missing"));
    private static final SimpleCommandExceptionType NOT_SEATED =
            new SimpleCommandExceptionType(Component.translatable("liarsbar.command.not_seated"));

    private LiarsBarCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("liarsbar")
                .requires(source -> source.hasPermission(2))
                // bar: bangun bar noir (struktur) berpusat di posisi pemanggil, lalu meja di tengahnya.
                .then(Commands.literal("bar").executes(ctx -> bar(ctx.getSource())))
                // create [fast]: meja baru di posisi pemanggil.
                .then(Commands.literal("create")
                        .executes(ctx -> create(ctx.getSource(), false))
                        .then(Commands.argument("fast", BoolArgumentType.bool())
                                .executes(ctx -> create(ctx.getSource(), BoolArgumentType.getBool(ctx, "fast")))))
                .then(withTable(Commands.literal("join"), (ctx, table) -> join(ctx.getSource(), table)))
                .then(Commands.literal("leave").executes(ctx -> leave(ctx.getSource())))
                .then(Commands.literal("bot")
                        .then(withTable(Commands.literal("fill"),
                                (ctx, table) -> bots(ctx.getSource(), table, LiarsDeckRules.MAX_PLAYERS)))
                        .then(withTable(Commands.literal("clear"), (ctx, table) -> clearBots(ctx.getSource(), table)))
                        .then(Commands.literal("add")
                                .then(Commands.argument("count", IntegerArgumentType.integer(1, LiarsDeckRules.MAX_PLAYERS))
                                        .executes(ctx -> bots(ctx.getSource(), resolve(ctx, -1),
                                                IntegerArgumentType.getInteger(ctx, "count"))))))
                .then(withTable(Commands.literal("start"), (ctx, table) -> start(ctx.getSource(), table)))
                .then(withTable(Commands.literal("stop"), (ctx, table) -> {
                    table.abort();
                    ctx.getSource().sendSuccess(() -> Component.translatable("liarsbar.command.stopped", table.id()), true);
                    return 1;
                }))
                .then(withTable(Commands.literal("remove"), (ctx, table) -> {
                    TableManager.remove(table);
                    ctx.getSource().sendSuccess(() -> Component.translatable("liarsbar.command.removed", table.id()), true);
                    return 1;
                }))
                .then(Commands.literal("list").executes(ctx -> list(ctx.getSource())))
                .then(Commands.literal("fast")
                        .then(Commands.argument("on", BoolArgumentType.bool())
                                .executes(ctx -> {
                                    boolean on = BoolArgumentType.getBool(ctx, "on");
                                    TableInstance table = resolve(ctx, -1);
                                    table.setFast(on);
                                    ctx.getSource().sendSuccess(() -> Component.translatable(
                                            on ? "liarsbar.command.fast.on" : "liarsbar.command.fast.off", table.id()), true);
                                    return 1;
                                })))
                // dev: alat uji (kursi sendiri dimainkan bot, percepat semua durasi).
                .then(Commands.literal("dev")
                        .then(Commands.literal("autoplay").executes(ctx -> autoplay(ctx.getSource())))
                        // dev roulette <kursi> click|bang [full|short|load]: putar sekuens roulette di meja yang di lobby.
                        .then(Commands.literal("roulette")
                                .then(Commands.argument("seat", IntegerArgumentType.integer(0, LiarsDeckRules.MAX_PLAYERS - 1))
                                        .then(Commands.literal("click")
                                                .executes(ctx -> devRoulette(ctx, false, true))
                                                .then(Commands.literal("full").executes(ctx -> devRoulette(ctx, false, true)))
                                                .then(Commands.literal("short").executes(ctx -> devRoulette(ctx, false, false)))
                                                .then(Commands.literal("load").executes(ctx -> devRoulette(ctx, false, true, true))))
                                        .then(Commands.literal("bang")
                                                .executes(ctx -> devRoulette(ctx, true, true))
                                                .then(Commands.literal("full").executes(ctx -> devRoulette(ctx, true, true)))
                                                .then(Commands.literal("short").executes(ctx -> devRoulette(ctx, true, false)))
                                                .then(Commands.literal("load").executes(ctx -> devRoulette(ctx, true, true, true))))))
                        .then(Commands.literal("room").executes(ctx -> {
                            resolve(ctx, -1).devRoom(8, 5);
                            ctx.getSource().sendSuccess(() -> Component.translatable("liarsbar.command.room"), true);
                            return 1;
                        }))
                        .then(Commands.literal("timescale")
                                .then(Commands.argument("scale", FloatArgumentType.floatArg(0.05f, 4f))
                                        .executes(ctx -> timescale(ctx, -1))
                                        .then(Commands.argument("id", IntegerArgumentType.integer(1))
                                                .executes(ctx -> timescale(ctx, IntegerArgumentType.getInteger(ctx, "id")))))))
                // sim <pemain> [game]: mainkan game penuh dengan bot di semua kursi (uji rules engine).
                .then(Commands.literal("sim")
                        .then(Commands.argument("players", IntegerArgumentType.integer(
                                        LiarsDeckRules.MIN_PLAYERS, LiarsDeckRules.MAX_PLAYERS))
                                .executes(ctx -> simulate(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "players"), 200))
                                .then(Commands.argument("games", IntegerArgumentType.integer(1, MAX_SIM_GAMES))
                                        .executes(ctx -> simulate(ctx.getSource(),
                                                IntegerArgumentType.getInteger(ctx, "players"),
                                                IntegerArgumentType.getInteger(ctx, "games")))))));
    }

    private interface TableCommand {
        int run(CommandContext<CommandSourceStack> ctx, TableInstance table) throws CommandSyntaxException;
    }

    /** {@code literal} dan {@code literal <id>}: tanpa id = meja tempat duduk / terdekat. */
    private static LiteralArgumentBuilder<CommandSourceStack> withTable(
            LiteralArgumentBuilder<CommandSourceStack> literal, TableCommand command) {
        return literal
                .executes(ctx -> command.run(ctx, resolve(ctx, -1)))
                .then(Commands.argument("id", IntegerArgumentType.integer(1))
                        .executes(ctx -> command.run(ctx, resolve(ctx, IntegerArgumentType.getInteger(ctx, "id")))));
    }

    private static TableInstance resolve(CommandContext<CommandSourceStack> ctx, int id) throws CommandSyntaxException {
        if (id > 0) {
            TableInstance table = TableManager.get(id);
            if (table == null) {
                throw NO_TABLE.create();
            }
            return table;
        }
        if (ctx.getSource().getEntity() instanceof ServerPlayer player) {
            TableInstance seated = TableManager.tableOf(player.getUUID());
            if (seated != null) {
                return seated;
            }
            TableInstance near = TableManager.nearest(player, TableManager.WATCH_RANGE);
            if (near != null) {
                return near;
            }
        }
        throw NO_TABLE.create();
    }

    private static int devRoulette(CommandContext<CommandSourceStack> ctx, boolean bang, boolean full)
            throws CommandSyntaxException {
        return devRoulette(ctx, bang, full, false);
    }

    private static int devRoulette(CommandContext<CommandSourceStack> ctx, boolean bang, boolean full, boolean load)
            throws CommandSyntaxException {
        TableInstance table = resolve(ctx, -1);
        int seat = IntegerArgumentType.getInteger(ctx, "seat");
        boolean ok = table.devRoulette(seat, bang, full, load);
        ctx.getSource().sendSuccess(() -> Component.translatable(ok ? "liarsbar.command.roulette"
                : "liarsbar.command.roulette.busy", seat), false);
        return ok ? 1 : 0;
    }

    private static int timescale(CommandContext<CommandSourceStack> ctx, int id) throws CommandSyntaxException {
        float scale = FloatArgumentType.getFloat(ctx, "scale");
        resolve(ctx, id).setTimeScale(scale);
        ctx.getSource().sendSuccess(() -> Component.translatable("liarsbar.command.timescale", scale), true);
        return 1;
    }

    private static int bar(CommandSourceStack source) throws CommandSyntaxException {
        Vec3 pos = source.getPosition();
        ServerLevel level = source.getLevel();
        BlockPos floor = floor(level, BlockPos.containing(pos.x, pos.y + 0.5, pos.z));
        if (!com.liarsbar.block.BarStructure.place(level, floor)) {
            throw NO_STRUCTURE.create();
        }
        Vec3 center = new Vec3(floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5);
        TableInstance table = TableManager.create(level, center, false);
        // Pemanggil dipindah ke depan meja (sisi selatan), menghadap back bar di utara.
        if (source.getEntity() instanceof ServerPlayer player) {
            player.teleportTo(level, center.x, center.y, center.z + 3.5, 180f, 10f);
        }
        source.sendSuccess(() -> Component.translatable("liarsbar.command.bar", table.id()), true);
        return table.id();
    }

    private static int create(CommandSourceStack source, boolean fast) {
        Vec3 pos = source.getPosition();
        BlockPos floor = floor(source.getLevel(), BlockPos.containing(pos.x, pos.y + 0.5, pos.z));
        Vec3 center = new Vec3(floor.getX() + 0.5, floor.getY(), floor.getZ() + 0.5);
        TableInstance table = TableManager.create(source.getLevel(), center, fast);
        source.sendSuccess(() -> Component.translatable("liarsbar.command.created", table.id()), true);
        return table.id();
    }

    /**
     * Blok kosong pertama di atas lantai padat di sekitar {@code start}: naik keluar dari blok padat,
     * lalu turun sampai menginjak lantai (paling jauh 4 blok ke tiap arah).
     */
    private static BlockPos floor(ServerLevel level, BlockPos start) {
        BlockPos pos = start;
        for (int i = 0; i < 4 && !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty(); i++) {
            pos = pos.above();
        }
        for (int i = 0; i < 4 && level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty(); i++) {
            pos = pos.below();
        }
        return pos;
    }

    private static int join(CommandSourceStack source, TableInstance table) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TableInstance current = TableManager.tableOf(player.getUUID());
        if (current != null && current != table) {
            current.leave(player);
        }
        String key = switch (table.join(player)) {
            case OK -> "liarsbar.command.joined";
            case FULL -> "liarsbar.command.join.full";
            case RUNNING -> "liarsbar.command.join.running";
            case ALREADY -> "liarsbar.command.join.already";
        };
        source.sendSuccess(() -> Component.translatable(key, table.id()), false);
        return 1;
    }

    private static int leave(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TableInstance table = TableManager.tableOf(player.getUUID());
        if (table == null) {
            throw NOT_SEATED.create();
        }
        table.leave(player);
        source.sendSuccess(() -> Component.translatable("liarsbar.command.left", table.id()), false);
        return 1;
    }

    private static int bots(CommandSourceStack source, TableInstance table, int count) {
        int added = table.addBots(count);
        source.sendSuccess(() -> Component.translatable("liarsbar.command.bots.added", added, table.seatCount()), true);
        return added;
    }

    private static int clearBots(CommandSourceStack source, TableInstance table) {
        int removed = table.removeBots();
        source.sendSuccess(() -> Component.translatable("liarsbar.command.bots.removed", removed), true);
        return removed;
    }

    private static int start(CommandSourceStack source, TableInstance table) {
        String key = switch (table.start()) {
            case OK -> "liarsbar.command.started";
            case RUNNING -> "liarsbar.command.start.running";
            case NOT_ENOUGH -> "liarsbar.command.start.not_enough";
        };
        source.sendSuccess(() -> Component.translatable(key, table.id(), LiarsDeckRules.MIN_PLAYERS), true);
        return 1;
    }

    private static int list(CommandSourceStack source) {
        if (TableManager.all().isEmpty()) {
            source.sendSuccess(() -> Component.translatable("liarsbar.command.list.empty"), false);
            return 0;
        }
        for (TableInstance table : TableManager.all()) {
            source.sendSuccess(() -> Component.literal(table.describe()), false);
        }
        return TableManager.all().size();
    }

    private static int autoplay(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        TableInstance table = TableManager.tableOf(player.getUUID());
        if (table == null) {
            throw NOT_SEATED.create();
        }
        boolean on = table.toggleAutoplay(player);
        source.sendSuccess(() -> Component.translatable(
                on ? "liarsbar.command.autoplay.on" : "liarsbar.command.autoplay.off"), false);
        return 1;
    }

    private static int simulate(CommandSourceStack source, int players, int games) {
        MinecraftServer server = source.getServer();
        long seed = System.nanoTime();
        source.sendSuccess(() -> Component.translatable("liarsbar.command.sim.started", games, players), false);
        // Di luar thread server supaya tick tidak tersendat.
        CompletableFuture.supplyAsync(() -> DeckSimulator.run(players, games, seed))
                .whenComplete((summary, error) -> server.execute(() -> {
                    if (error != null) {
                        source.sendFailure(Component.translatable("liarsbar.command.sim.failed",
                                String.valueOf(error.getMessage())));
                        return;
                    }
                    source.sendSuccess(() -> Component.translatable("liarsbar.command.sim.result",
                            summary.players(), summary.games(),
                            String.format(Locale.ROOT, "%.1f", summary.avgRounds()),
                            summary.minRounds(), summary.maxRounds(),
                            String.format(Locale.ROOT, "%.1f", summary.avgTurnsPerRound()),
                            String.format(Locale.ROOT, "%.0f", summary.forcedCallShare() * 100)), false);
                }));
        return games;
    }
}
