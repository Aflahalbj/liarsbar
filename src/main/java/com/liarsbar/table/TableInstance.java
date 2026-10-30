package com.liarsbar.table;

import com.liarsbar.entity.BotEntity;
import com.liarsbar.entity.ModEntities;
import com.liarsbar.entity.SeatEntity;
import com.liarsbar.entity.TableEntity;
import com.liarsbar.game.Rng;
import com.liarsbar.game.bot.DeckBot;
import com.liarsbar.game.deck.GameEvent;
import com.liarsbar.game.deck.LiarsDeckGame;
import com.liarsbar.game.deck.LiarsDeckRules;
import com.liarsbar.game.deck.Phase;
import com.liarsbar.game.deck.RuleViolation;
import com.liarsbar.network.HandS2C;
import com.liarsbar.network.ModNetwork;
import com.liarsbar.network.TableActionC2S;
import com.liarsbar.network.TableEventS2C;
import com.liarsbar.network.TableStateS2C;
import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Satu meja di server: kursi (pemain/bot), rules engine, timer giliran, dan
 * jadwal siaran. Engine menghitung hasil seketika; meja menyiarkan tiap
 * kejadian pada waktunya ({@link TableTiming}) dan baru memanggil
 * {@link LiarsDeckGame#pullTrigger()} di momen tembakan, jadi tidak ada yang
 * bisa tahu hasilnya lebih dulu.
 */
public final class TableInstance {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Satu kursi. Urutan {@link #members} = urutan giliran = kursi engine. */
    static final class Member {
        /** Null untuk bot. */
        final UUID player;
        String name;
        final boolean bot;
        /** Pemain manusia keluar/terputus; sejak tick {@link #awayAt}. */
        boolean away;
        long awayAt;
        /** Keluar dengan sengaja: bot langsung mengambil alih, tanpa masa tunggu. */
        boolean leftOnPurpose;
        /** Sudah dicatat di log bahwa bot mengambil alih kursi ini. */
        boolean takeoverLogged;
        /** Dev: kursi pemain ini dimainkan bot (uji otomatis). */
        boolean autoplay;
        SeatEntity seat;
        BotEntity body;
        DeckBot brain;
        long actAt = -1;

        Member(UUID player, String name, boolean bot) {
            this.player = player;
            this.name = name;
            this.bot = bot;
        }
    }

    private record Scheduled(long at, long order, Runnable action) {
    }

    final int id;
    final ServerLevel level;
    final Vec3 center;
    boolean fast;
    float timeScale = 1f;

    final List<Member> members = new ArrayList<>();
    final PublicState state;
    private TableEntity tableEntity;
    private LiarsDeckGame game;
    private final LiarsDeckRules rules = LiarsDeckRules.STANDARD;
    private final Rng rng = new Rng(System.nanoTime());
    private final PriorityQueue<Scheduled> queue = new PriorityQueue<>(
            (a, b) -> a.at != b.at ? Long.compare(a.at, b.at) : Long.compare(a.order, b.order));
    private long order;
    private long tick;
    /** Roulette yang sudah terjadi di game ini (yang pertama selalu versi penuh). */
    private int roulettes;

    TableInstance(int id, ServerLevel level, Vec3 center, boolean fast) {
        this.id = id;
        this.level = level;
        this.center = center;
        this.fast = fast;
        this.state = new PublicState(id);
        state.fast = fast;
        state.chambers = rules.chambers();
        tableEntity = ModEntities.TABLE.get().create(level);
        tableEntity.setTableId(id);
        tableEntity.moveTo(center.x, center.y, center.z, 0, 0);
        level.addFreshEntity(tableEntity);
        relayout();
    }

    public int id() {
        return id;
    }

    public ServerLevel level() {
        return level;
    }

    public Vec3 center() {
        return center;
    }

    public boolean inLobby() {
        return game == null;
    }

    public int seatCount() {
        return members.size();
    }

    // =========================================================== kursi

    public enum JoinResult {
        OK, FULL, RUNNING, ALREADY
    }

    public JoinResult join(ServerPlayer player) {
        Member existing = member(player.getUUID());
        if (existing != null) {
            if (existing.away) {
                rejoin(player);
                return JoinResult.OK;
            }
            return JoinResult.ALREADY;
        }
        if (!inLobby()) {
            return JoinResult.RUNNING;
        }
        if (members.size() >= LiarsDeckRules.MAX_PLAYERS) {
            return JoinResult.FULL;
        }
        members.add(new Member(player.getUUID(), player.getGameProfile().getName(), false));
        relayout();
        TableVoice.update(this);
        return JoinResult.OK;
    }

    /** Tambah bot sampai {@code count} atau meja penuh. Hanya di lobby. */
    public int addBots(int count) {
        if (!inLobby()) {
            return 0;
        }
        int added = 0;
        while (added < count && members.size() < LiarsDeckRules.MAX_PLAYERS) {
            int number = 1;
            while (hasName("Bot " + number)) {
                number++;
            }
            members.add(new Member(null, "Bot " + number, true));
            added++;
        }
        relayout();
        return added;
    }

    public int removeBots() {
        if (!inLobby()) {
            return 0;
        }
        int removed = 0;
        for (Member m : new ArrayList<>(members)) {
            if (m.bot) {
                despawn(m);
                members.remove(m);
                removed++;
            }
        }
        relayout();
        return removed;
    }

    /** Pemain keluar dengan sengaja ({@code /liarsbar leave}). Di tengah game, bot langsung mengambil alih. */
    public void leave(ServerPlayer player) {
        Member m = member(player.getUUID());
        if (m == null) {
            return;
        }
        dismount(player);
        if (inLobby()) {
            despawn(m);
            members.remove(m);
            relayout();
        } else {
            m.away = true;
            m.awayAt = tick;
            m.leftOnPurpose = true;
            state.seats.get(members.indexOf(m)).away = true;
            LOGGER.info("[table {}] {} left seat {}, a bot takes over", id, m.name, members.indexOf(m));
            broadcastState();
        }
        TableVoice.update(this);
    }

    /** Pemain terputus. Di tengah game kursinya ditunggu {@link TableTiming#DISCONNECT_GRACE}, lalu dimainkan bot. */
    public void onDisconnect(UUID player) {
        Member m = member(player);
        if (m == null) {
            return;
        }
        if (inLobby()) {
            despawn(m);
            members.remove(m);
            relayout();
        } else {
            m.away = true;
            m.awayAt = tick;
            m.leftOnPurpose = false;
            state.seats.get(members.indexOf(m)).away = true;
            LOGGER.info("[table {}] {} disconnected from seat {}, waiting {} ticks", id, m.name, members.indexOf(m),
                    scaled(TableTiming.DISCONNECT_GRACE));
            broadcastState();
        }
        TableVoice.update(this);
    }

    /** Pemain yang tadi keluar/terputus kembali ke kursinya sendiri. */
    public void rejoin(ServerPlayer player) {
        Member m = member(player.getUUID());
        if (m == null) {
            return;
        }
        m.away = false;
        m.leftOnPurpose = false;
        m.takeoverLogged = false;
        m.actAt = -1;
        int seat = members.indexOf(m);
        LOGGER.info("[table {}] {} is back in seat {}", id, m.name, seat);
        state.seats.get(seat).away = false;
        // Saat event login entity pemain belum siap ditunggangkan: pasang ke kursi beberapa tick kemudian.
        UUID id = player.getUUID();
        at(tick + 5, () -> {
            ServerPlayer back = level.getServer().getPlayerList().getPlayer(id);
            if (back != null && !m.away) {
                mount(back, m);
            }
        });
        sendHand(m);
        broadcastState();
        TableVoice.update(this);
    }

    public boolean toggleAutoplay(ServerPlayer player) {
        Member m = member(player.getUUID());
        if (m == null) {
            return false;
        }
        m.autoplay = !m.autoplay;
        if (m.autoplay && m.brain == null) {
            m.brain = new DeckBot(rules, 0.5);
        }
        m.actAt = -1;
        return m.autoplay;
    }

    public void setFast(boolean fast) {
        this.fast = fast;
        state.fast = fast;
        broadcastState();
    }

    public void setTimeScale(float scale) {
        this.timeScale = scale;
    }

    private boolean hasName(String name) {
        for (Member m : members) {
            if (m.name.equals(name)) {
                return true;
            }
        }
        return false;
    }

    Member member(UUID player) {
        for (Member m : members) {
            if (player.equals(m.player)) {
                return m;
            }
        }
        return null;
    }

    public int seatOf(UUID player) {
        Member m = member(player);
        return m == null ? -1 : members.indexOf(m);
    }

    /** Pemain ini duduk di meja ini dan tidak sedang keluar. */
    public boolean isSeated(UUID player) {
        Member m = member(player);
        return m != null && !m.away;
    }

    public boolean isAlive(UUID player) {
        int seat = seatOf(player);
        return seat >= 0 && state.seats.get(seat).alive;
    }

    List<UUID> humanPlayers() {
        List<UUID> players = new ArrayList<>();
        for (Member m : members) {
            if (m.player != null) {
                players.add(m.player);
            }
        }
        return players;
    }

    /** Susun ulang kursi untuk jumlah anggota sekarang (hanya di lobby; selama game kursi tidak bergerak). */
    private void relayout() {
        int count = Math.max(2, members.size());
        tableEntity.setRadius(SeatLayout.Size.forSeats(count).radius);
        List<SeatLayout.Seat> seats = SeatLayout.seats(count);
        for (int i = 0; i < members.size(); i++) {
            Member m = members.get(i);
            SeatLayout.Seat pos = seats.get(i);
            if (m.seat == null || m.seat.isRemoved()) {
                m.seat = ModEntities.SEAT.get().create(level);
                m.seat.setTableId(id);
                m.seat.moveTo(center.x + pos.dx(), center.y, center.z + pos.dz(), pos.yaw(), 0);
                level.addFreshEntity(m.seat);
            } else {
                m.seat.moveTo(center.x + pos.dx(), center.y, center.z + pos.dz(), pos.yaw(), 0);
            }
            if (m.bot) {
                if (m.body == null || m.body.isRemoved()) {
                    m.body = ModEntities.BOT.get().create(level);
                    m.body.setTableId(id);
                    m.body.setSkin(rng.nextInt(BotEntity.SKIN_NAMES.length));
                    m.body.setCustomName(Component.literal(m.name));
                    m.body.setCustomNameVisible(true);
                    m.body.moveTo(m.seat.getX(), m.seat.getY(), m.seat.getZ(), pos.yaw(), 0);
                    level.addFreshEntity(m.body);
                }
                m.body.setSeat(i);
                if (m.body.getVehicle() != m.seat) {
                    m.body.startRiding(m.seat, true);
                }
            } else if (!m.away) {
                ServerPlayer player = level.getServer().getPlayerList().getPlayer(m.player);
                if (player != null && player.getVehicle() != m.seat) {
                    mount(player, m);
                }
            }
        }
        rebuildSeats();
        broadcastState();
    }

    private void rebuildSeats() {
        state.seats.clear();
        for (Member m : members) {
            PublicState.SeatInfo info = new PublicState.SeatInfo(m.name, m.player, m.bot);
            info.away = m.away;
            state.seats.add(info);
        }
    }

    private void mount(ServerPlayer player, Member m) {
        if (player.level() != level) {
            return;
        }
        TableManager.allowDismount = true;
        try {
            player.stopRiding();
            float yaw = m.seat.getYRot();
            player.teleportTo(level, m.seat.getX(), m.seat.getY(), m.seat.getZ(), yaw, 10);
            if (!player.startRiding(m.seat, true)) {
                LOGGER.warn("[table {}] could not seat {}", id, m.name);
            }
        } finally {
            TableManager.allowDismount = false;
        }
    }

    /** Turunkan dari kursi meja ini dan berdirikan di belakang kursinya (bukan di titik duduk yang di bawah lantai). */
    void dismount(ServerPlayer player) {
        if (!(player.getVehicle() instanceof SeatEntity seat)) {
            return;
        }
        TableManager.allowDismount = true;
        try {
            player.stopRiding();
        } finally {
            TableManager.allowDismount = false;
        }
        double dx = seat.getX() - center.x, dz = seat.getZ() - center.z;
        double len = Math.max(0.01, Math.sqrt(dx * dx + dz * dz));
        double x = seat.getX() + dx / len * 0.9, z = seat.getZ() + dz / len * 0.9;
        player.teleportTo(level, x, center.y, z, seat.getYRot(), 0);
    }

    private void despawn(Member m) {
        TableManager.allowDismount = true;
        try {
            if (m.body != null) {
                m.body.discard();
            }
            if (m.seat != null) {
                m.seat.ejectPassengers();
                m.seat.discard();
            }
        } finally {
            TableManager.allowDismount = false;
        }
    }

    void dispose() {
        for (Member m : members) {
            despawn(m);
        }
        if (tableEntity != null) {
            tableEntity.discard();
        }
        queue.clear();
        TableVoice.remove(this);
    }

    // ============================================================ game

    public enum StartResult {
        OK, RUNNING, NOT_ENOUGH
    }

    public StartResult start() {
        if (!inLobby()) {
            return StartResult.RUNNING;
        }
        if (members.size() < LiarsDeckRules.MIN_PLAYERS) {
            return StartResult.NOT_ENOUGH;
        }
        // Anggota yang keluar saat lobby sudah dihapus; kursi dikunci selama game.
        game = new LiarsDeckGame(members.size(), rules, rng.nextInt(Integer.MAX_VALUE) * 31L + tick);
        for (Member m : members) {
            if (m.bot || m.autoplay) {
                m.brain = new DeckBot(rules, rng.nextDouble());
            }
            m.actAt = -1;
        }
        rebuildSeats();
        state.resetToLobby();
        roulettes = 0;
        fullFlags.clear();
        LOGGER.info("[table {}] game starts with {} seats", id, members.size());
        schedule(game.start());
        TableVoice.update(this);
        return StartResult.OK;
    }

    /**
     * Dev: putar sekuens roulette untuk kursi {@code seat} di meja yang sedang di lobby (tanpa game):
     * kejadian yang sama dengan game sungguhan, dengan waktu dari {@link TableTiming}.
     */
    public boolean devRoulette(int seat, boolean bang, boolean full, boolean load) {
        if (!inLobby() || seat < 0 || seat >= members.size() || !queue.isEmpty()) {
            return false;
        }
        state.resetToLobby();
        GameEvent.RouletteStarted started = new GameEvent.RouletteStarted(List.of(seat));
        fullFlags.put(started, load ? TableEventS2C.FULL | TableEventS2C.LOAD : full ? TableEventS2C.FULL : 0);
        broadcast(started);
        int pullNumber = state.seats.get(seat).pulls + 1;
        long t = tick + scaled(TableTiming.pull(full, load));
        at(t, () -> {
            broadcast(new GameEvent.TriggerPulled(seat, pullNumber, bang));
            if (bang) {
                broadcast(new GameEvent.PlayerEliminated(seat));
            }
        });
        at(t + scaled(bang ? TableTiming.AFTER_BANG : TableTiming.AFTER_CLICK) + 40, () -> {
            state.resetToLobby();
            broadcastState();
        });
        return true;
    }

    /** Dev: ruang bar sederhana yang gelap di sekitar meja (lantai, dinding, langit-langit), untuk uji cahaya. */
    public void devRoom(int half, int height) {
        net.minecraft.core.BlockPos c = net.minecraft.core.BlockPos.containing(center);
        var floor = net.minecraft.world.level.block.Blocks.DARK_OAK_PLANKS.defaultBlockState();
        var wall = net.minecraft.world.level.block.Blocks.SPRUCE_PLANKS.defaultBlockState();
        var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        for (int x = -half; x <= half; x++) {
            for (int z = -half; z <= half; z++) {
                for (int y = -1; y <= height; y++) {
                    boolean edge = Math.abs(x) == half || Math.abs(z) == half;
                    var state = y == -1 || y == height ? floor : edge ? wall : air;
                    level.setBlock(c.offset(x, y, z), state, 3);
                }
            }
        }
    }

    /** Hentikan game yang sedang jalan, kembali ke lobby. */
    public void abort() {
        if (game == null) {
            return;
        }
        queue.clear();
        finish();
    }

    void tick() {
        tick++;
        while (!queue.isEmpty() && queue.peek().at <= tick) {
            queue.poll().action.run();
        }
        if (tick % 20 == 0) {
            keepSeated();
        }
        if (game == null || state.stage != PublicState.Stage.TURN || state.current < 0) {
            return;
        }
        int seat = state.current;
        Member m = members.get(seat);
        if (botControlled(m)) {
            if (m.actAt < 0) {
                m.actAt = tick + scaled(TableTiming.BOT_THINK_MIN
                        + rng.nextInt(TableTiming.BOT_THINK_MAX - TableTiming.BOT_THINK_MIN + 1));
            }
            if (tick >= m.actAt) {
                botAct(seat, m);
            }
        } else if (tick >= state.deadline) {
            LOGGER.info("[table {}] seat {} timed out", id, seat);
            perform(() -> game.timeout(seat), seat);
        }
    }

    /** Pemain yang seharusnya duduk tapi tidak di kursinya (mis. tersentak saat login) dinaikkan lagi. */
    private void keepSeated() {
        for (Member m : members) {
            if (m.player == null || m.away || m.seat == null || m.seat.isRemoved()) {
                continue;
            }
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(m.player);
            if (player != null && player.getVehicle() != m.seat) {
                LOGGER.info("[table {}] reseating {}", id, m.name);
                mount(player, m);
            }
        }
    }

    private boolean botControlled(Member m) {
        if (m.bot || m.autoplay) {
            return true;
        }
        if (!m.away || (!m.leftOnPurpose && tick - m.awayAt < scaled(TableTiming.DISCONNECT_GRACE))) {
            return false;
        }
        if (!m.takeoverLogged) {
            m.takeoverLogged = true;
            LOGGER.info("[table {}] a bot plays seat {} for {}", id, members.indexOf(m), m.name);
        }
        return true;
    }

    private void botAct(int seat, Member m) {
        if (m.brain == null) {
            m.brain = new DeckBot(rules, rng.nextDouble());
        }
        DeckBot.Decision decision = m.brain.decide(game.viewFor(seat), rng);
        if (decision instanceof DeckBot.Decision.Play play) {
            perform(() -> game.play(seat, play.cardIds()), seat);
        } else {
            perform(() -> game.callLiar(seat), seat);
        }
    }

    /** Aksi pemain manusia dari client. Hanya di giliran yang sudah disiarkan. */
    void onAction(ServerPlayer player, TableActionC2S action) {
        int seat = seatOf(player.getUUID());
        if (game == null || seat < 0 || state.stage != PublicState.Stage.TURN || state.current != seat) {
            return;
        }
        boolean ok = switch (action.action()) {
            case PLAY -> perform(() -> game.play(seat, action.cardIds()), seat);
            case CALL_LIAR -> perform(() -> game.callLiar(seat), seat);
        };
        if (!ok) {
            LOGGER.debug("[table {}] rejected {} from {}", id, action, player.getGameProfile().getName());
        }
    }

    private boolean perform(Supplier<List<GameEvent>> action, int seat) {
        List<GameEvent> events;
        try {
            events = action.get();
        } catch (RuleViolation e) {
            return false;
        }
        // Tangan si pelaku berubah: kirim ke dia saja.
        sendHand(members.get(seat));
        schedule(events);
        return true;
    }

    private void schedule(List<GameEvent> events) {
        long t = tick;
        for (GameEvent event : events) {
            at(t, () -> broadcast(event));
            if (event instanceof GameEvent.RoundStarted started) {
                // Ronde pertama dapat pembagian kartu versi penuh (sinematik); sesudahnya versi pendek.
                boolean full = !fast && started.round() == 1;
                fullFlags.put(event, full ? TableEventS2C.FULL : 0);
                int alive = (int) started.handSizes().stream().filter(n -> n > 0).count();
                t += scaled(TableTiming.deal(full, alive));
            } else if (event instanceof GameEvent.CardsPlayed) {
                t += scaled(TableTiming.THROW);
            } else if (event instanceof GameEvent.LiarCalled) {
                t += scaled(TableTiming.CALL_TO_REVEAL);
            } else if (event instanceof GameEvent.Revealed) {
                t += scaled(TableTiming.REVEAL_TO_ROULETTE);
            } else if (event instanceof GameEvent.RouletteStarted started) {
                int flags = rouletteFlags(started);
                roulettes++;
                fullFlags.put(event, flags);
                t += scaled(TableTiming.pull((flags & TableEventS2C.FULL) != 0, (flags & TableEventS2C.LOAD) != 0));
                at(t, this::pullTrigger);
            } else if (event instanceof GameEvent.GameWon) {
                at(t + scaled(TableTiming.WIN_HOLD), this::finish);
            }
        }
    }

    /** Flag {@link TableEventS2C} (versi penuh/isi) tiap kejadian: ditentukan saat dijadwalkan, dikirim bersama kejadiannya. */
    private final java.util.Map<GameEvent, Integer> fullFlags = new java.util.IdentityHashMap<>();

    /**
     * Penembak yang belum pernah menarik pelatuk mengisi revolvernya dulu (peluru berdiri di samping pistolnya
     * sejak game mulai): versi isi, sekali per pemain, juga di mode cepat. Selebihnya {@link #fullRoulette}.
     */
    private int rouletteFlags(GameEvent.RouletteStarted started) {
        for (int seat : started.shooters()) {
            if (game.pulls(seat) == 0) {
                return TableEventS2C.FULL | TableEventS2C.LOAD;
            }
        }
        return fullRoulette(started) ? TableEventS2C.FULL : 0;
    }

    /**
     * Momen penting dapat versi sinematik penuh: roulette pertama game ini, penembak sudah menarik 4 kali
     * atau lebih (peluang mati 1/2 ke atas), atau duel terakhir. Mode cepat selalu versi pendek.
     */
    private boolean fullRoulette(GameEvent.RouletteStarted started) {
        if (fast) {
            return false;
        }
        if (roulettes == 0 || game.aliveCount() == 2) {
            return true;
        }
        for (int seat : started.shooters()) {
            if (game.pulls(seat) >= 4) {
                return true;
            }
        }
        return false;
    }

    /** Momen tembakan: hasilnya baru ditentukan di sini. */
    private void pullTrigger() {
        if (game == null || game.phase() != Phase.ROULETTE) {
            return;
        }
        List<GameEvent> events = game.pullTrigger();
        schedule(events);
        if (game.phase() == Phase.ROUND_OVER) {
            boolean died = events.stream().anyMatch(e -> e instanceof GameEvent.PlayerEliminated);
            at(tick + scaled(died ? TableTiming.AFTER_BANG : TableTiming.AFTER_CLICK), () -> {
                if (game != null && game.phase() == Phase.ROUND_OVER) {
                    schedule(game.nextRound());
                }
            });
        }
    }

    private void finish() {
        game = null;
        queue.clear();
        // Yang keluar di tengah game baru dilepas sekarang.
        for (Member m : new ArrayList<>(members)) {
            if (m.away) {
                despawn(m);
                members.remove(m);
            }
            m.autoplay = false;
        }
        state.resetToLobby();
        relayout();
        TableVoice.update(this);
        LOGGER.info("[table {}] back to lobby", id);
    }

    private void broadcast(GameEvent event) {
        state.apply(event, tick, scaled(TableTiming.turn(fast)));
        Integer flags = fullFlags.remove(event);
        if (flags != null && event instanceof GameEvent.RouletteStarted) {
            state.rouletteFull = (flags & TableEventS2C.FULL) != 0;
        }
        LOGGER.info("[table {}] {}", id, event);
        if (event instanceof GameEvent.TurnStarted turn) {
            members.get(turn.seat()).actAt = -1;
        } else if (event instanceof GameEvent.RoundStarted) {
            for (Member m : members) {
                sendHand(m);
            }
        } else if (event instanceof GameEvent.PlayerEliminated) {
            TableVoice.update(this);
        }
        TableEventS2C packet = new TableEventS2C(id, event, flags == null ? 0 : flags);
        for (ServerPlayer watcher : TableManager.watchers(this)) {
            ModNetwork.send(watcher, packet);
        }
        broadcastState();
    }

    void broadcastState() {
        for (ServerPlayer watcher : TableManager.watchers(this)) {
            sendState(watcher);
        }
    }

    void sendState(ServerPlayer player) {
        ModNetwork.send(player, TableStateS2C.of(state, tick, seatOf(player.getUUID())));
    }

    private void sendHand(Member m) {
        if (m.player == null || m.away || game == null) {
            return;
        }
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(m.player);
        if (player != null) {
            ModNetwork.send(player, new HandS2C(id, game.hand(members.indexOf(m))));
        }
    }

    private void at(long when, Runnable action) {
        queue.add(new Scheduled(when, order++, action));
    }

    private int scaled(int ticks) {
        return TableTiming.scaled(ticks, timeScale);
    }

    public String describe() {
        return "#" + id + " " + (inLobby() ? "lobby" : state.stage.name().toLowerCase()) + " " + members.size() + "/"
                + LiarsDeckRules.MAX_PLAYERS;
    }
}
