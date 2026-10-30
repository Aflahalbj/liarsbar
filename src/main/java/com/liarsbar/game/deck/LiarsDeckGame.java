package com.liarsbar.game.deck;

import com.liarsbar.game.Revolver;
import com.liarsbar.game.Rng;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntPredicate;

/**
 * Rules engine Liar's Deck (Basic ruleset), tanpa dependensi Minecraft dan
 * deterministik dari seed-nya. Tiap aksi mengembalikan daftar
 * {@link GameEvent}; server yang mengatur jeda (cutscene) di antaranya,
 * misalnya memanggil {@link #pullTrigger()} baru di momen tembakan.
 *
 * <p>Kursi 0 .. n-1 adalah urutan giliran: giliran berpindah ke kursi +1
 * (pemain di kanan; di meja, kursi berurutan berlawanan jarum jam dilihat dari
 * atas, seperti game aslinya). Pemain mati dan pemain yang kartunya sudah
 * habis dilewati.
 *
 * <p>Aturan (terverifikasi ke game asli, lihat laporan Fase 0):
 * <ul>
 *   <li>Tiap ronde: deck dikocok ulang (jumlah deck dari pemain hidup), tiap
 *       pemain hidup dapat 5 kartu, sisanya tidak dibagi, table card acak K/Q/A.</li>
 *   <li>Giliran: main 1-3 kartu tertutup (diakui table card) atau LIAR ke
 *       permainan sebelumnya. LIAR tidak bisa di giliran pertama ronde.</li>
 *   <li>Reveal: hanya kartu permainan terakhir. Ada kartu bohong -> yang
 *       menaruh menembak; jujur semua -> penuduh menembak.</li>
 *   <li>Kalau tinggal satu pemain yang masih pegang kartu, dia otomatis
 *       menuduh permainan terakhir (termasuk 1v1 saat kartu terakhir dimainkan).</li>
 *   <li>Setelah roulette selalu ronde baru, dimulai oleh yang barusan menembak;
 *       kalau dia mati, pemain hidup berikutnya.</li>
 *   <li>Revolver tiap pemain tidak di-reset antar ronde. Terakhir hidup menang.</li>
 * </ul>
 */
public final class LiarsDeckGame {

    /** Permainan terakhir yang bisa dituduh. */
    private record LastPlay(int seat, List<Card> cards) {
    }

    /**
     * Semua yang boleh diketahui satu kursi (untuk bot): tangan sendiri dan
     * info publik. Posisi peluru dan kartu orang lain tidak ada di sini.
     */
    public record SeatView(int seat, List<Card> hand, Rank tableRank, int decks, boolean canCallLiar,
                           int lastPlayCount, int cardsPlayedThisRound, List<Integer> handSizes, int aliveCount,
                           int pulls, int chambers) {
    }

    private final int players;
    private final LiarsDeckRules rules;
    private final Rng rng;
    private final Revolver[] revolvers;
    private final boolean[] alive;
    private final List<List<Card>> hands;

    private Phase phase = Phase.NOT_STARTED;
    private int round;
    private Rank tableRank;
    private int decks;
    private int current = -1;
    private LastPlay lastPlay;
    private int cardsPlayedThisRound;
    private List<Integer> shooters = List.of();
    private int nextStarter = -1;
    private int winner = -1;

    public LiarsDeckGame(int players, LiarsDeckRules rules, long seed) {
        if (players < LiarsDeckRules.MIN_PLAYERS || players > LiarsDeckRules.MAX_PLAYERS) {
            throw new IllegalArgumentException("players=" + players);
        }
        this.players = players;
        this.rules = rules;
        this.rng = new Rng(seed);
        this.revolvers = new Revolver[players];
        this.alive = new boolean[players];
        this.hands = new ArrayList<>(players);
        for (int seat = 0; seat < players; seat++) {
            revolvers[seat] = Revolver.random(rules.chambers(), rng);
            alive[seat] = true;
            hands.add(new ArrayList<>(rules.handSize()));
        }
    }

    // ================================================================ aksi

    /** Pemain pertama ronde pertama diacak. */
    public List<GameEvent> start() {
        requirePhase(Phase.NOT_STARTED);
        return startRound(rng.nextInt(players));
    }

    /** {@code seat} menaruh kartu {@code cardIds} (1..3, milik sendiri, tidak dobel). */
    public List<GameEvent> play(int seat, List<Integer> cardIds) {
        requireTurn(seat);
        if (cardIds.isEmpty() || cardIds.size() > rules.maxPlay()) {
            throw new RuleViolation("must play 1.." + rules.maxPlay() + " cards, got " + cardIds.size());
        }
        Set<Integer> unique = new HashSet<>(cardIds);
        if (unique.size() != cardIds.size()) {
            throw new RuleViolation("duplicate card ids " + cardIds);
        }
        List<Card> hand = hands.get(seat);
        List<Card> chosen = new ArrayList<>(cardIds.size());
        for (int id : cardIds) {
            Card card = find(hand, id);
            if (card == null) {
                throw new RuleViolation("card " + id + " not in hand of seat " + seat);
            }
            chosen.add(card);
        }
        hand.removeAll(chosen);
        lastPlay = new LastPlay(seat, List.copyOf(chosen));
        cardsPlayedThisRound += chosen.size();

        List<GameEvent> events = new ArrayList<>();
        events.add(new GameEvent.CardsPlayed(seat, chosen.size(), tableRank, hand.size()));

        List<Integer> holders = seatsWhere(s -> alive[s] && !hands.get(s).isEmpty());
        if (holders.size() == 1 && holders.get(0) != seat) {
            // Tinggal satu yang pegang kartu: dia wajib menuduh, otomatis.
            resolveCall(holders.get(0), true, events);
        } else if (holders.isEmpty() || holders.size() == 1) {
            // Tidak terjangkau: begitu pemegang kartu tinggal satu, tuduhan paksa sudah terjadi.
            throw new IllegalStateException("no valid next turn after seat " + seat + " played");
        } else {
            current = nextSeat(seat, s -> alive[s] && !hands.get(s).isEmpty());
            events.add(new GameEvent.TurnStarted(current, true));
        }
        return events;
    }

    /** {@code seat} (yang sedang giliran) menuduh permainan sebelumnya. */
    public List<GameEvent> callLiar(int seat) {
        requireTurn(seat);
        if (lastPlay == null) {
            throw new RuleViolation("nothing to call on the first turn of a round");
        }
        List<GameEvent> events = new ArrayList<>();
        resolveCall(seat, false, events);
        return events;
    }

    /** Timer giliran habis: satu kartu acak dari tangannya dimainkan. */
    public List<GameEvent> timeout(int seat) {
        requireTurn(seat);
        Card card = rng.pick(hands.get(seat));
        return play(seat, List.of(card.id()));
    }

    /** Momen tembakan: semua penembak menarik pelatuk. */
    public List<GameEvent> pullTrigger() {
        requirePhase(Phase.ROULETTE);
        List<GameEvent> events = new ArrayList<>();
        for (int seat : shooters) {
            Revolver revolver = revolvers[seat];
            int pullNumber = revolver.pulls() + 1;
            boolean fired = revolver.pull();
            events.add(new GameEvent.TriggerPulled(seat, pullNumber, fired));
            if (fired) {
                alive[seat] = false;
                hands.get(seat).clear();
                events.add(new GameEvent.PlayerEliminated(seat));
            }
        }
        List<Integer> living = seatsWhere(s -> alive[s]);
        if (living.isEmpty()) {
            throw new IllegalStateException("everyone died");
        }
        int shooter = shooters.get(0);
        shooters = List.of();
        if (living.size() == 1) {
            winner = living.get(0);
            phase = Phase.GAME_OVER;
            events.add(new GameEvent.GameWon(winner));
        } else {
            nextStarter = alive[shooter] ? shooter : nextSeat(shooter, s -> alive[s]);
            phase = Phase.ROUND_OVER;
        }
        return events;
    }

    /** Ronde baru setelah roulette, dimulai oleh yang barusan menembak (atau penerusnya). */
    public List<GameEvent> nextRound() {
        requirePhase(Phase.ROUND_OVER);
        return startRound(nextStarter);
    }

    // ============================================================= internal

    private List<GameEvent> startRound(int starter) {
        round++;
        decks = DeckBuilder.decksFor(aliveCount());
        List<Card> deck = DeckBuilder.build(decks);
        rng.shuffle(deck);
        tableRank = rng.pick(Rank.TABLE_RANKS);
        int top = 0;
        for (int i = 0; i < players; i++) {
            int seat = (starter + i) % players;
            List<Card> hand = hands.get(seat);
            hand.clear();
            if (alive[seat]) {
                hand.addAll(deck.subList(top, top + rules.handSize()));
                top += rules.handSize();
            }
        }
        return beginRound(starter);
    }

    private List<GameEvent> beginRound(int starter) {
        lastPlay = null;
        cardsPlayedThisRound = 0;
        current = starter;
        phase = Phase.TURN;
        List<GameEvent> events = new ArrayList<>();
        events.add(new GameEvent.RoundStarted(round, tableRank, starter, decks, handSizes()));
        events.add(new GameEvent.TurnStarted(starter, false));
        return events;
    }

    private void resolveCall(int caller, boolean forced, List<GameEvent> events) {
        int accused = lastPlay.seat();
        boolean lie = false;
        for (Card card : lastPlay.cards()) {
            if (!card.rank().truthfulOn(tableRank)) {
                lie = true;
                break;
            }
        }
        events.add(new GameEvent.LiarCalled(caller, accused, forced));
        events.add(new GameEvent.Revealed(accused, lastPlay.cards(), lie));
        shooters = List.of(lie ? accused : caller);
        current = -1;
        phase = Phase.ROULETTE;
        events.add(new GameEvent.RouletteStarted(shooters));
    }

    private void requirePhase(Phase wanted) {
        if (phase != wanted) {
            throw new RuleViolation("phase is " + phase + ", expected " + wanted);
        }
    }

    private void requireTurn(int seat) {
        requirePhase(Phase.TURN);
        if (seat != current) {
            throw new RuleViolation("seat " + seat + " acted out of turn (current " + current + ")");
        }
    }

    /** Kursi pertama setelah {@code from} (searah urutan giliran) yang memenuhi {@code test}. */
    private int nextSeat(int from, IntPredicate test) {
        for (int i = 1; i <= players; i++) {
            int seat = (from + i) % players;
            if (test.test(seat)) {
                return seat;
            }
        }
        throw new IllegalStateException("no seat after " + from);
    }

    private List<Integer> seatsWhere(IntPredicate test) {
        List<Integer> seats = new ArrayList<>();
        for (int seat = 0; seat < players; seat++) {
            if (test.test(seat)) {
                seats.add(seat);
            }
        }
        return seats;
    }

    private static Card find(List<Card> hand, int id) {
        for (Card card : hand) {
            if (card.id() == id) {
                return card;
            }
        }
        return null;
    }

    // ============================================================ query

    public Phase phase() {
        return phase;
    }

    public int players() {
        return players;
    }

    public LiarsDeckRules rules() {
        return rules;
    }

    public boolean isAlive(int seat) {
        return alive[seat];
    }

    public int aliveCount() {
        int count = 0;
        for (boolean a : alive) {
            if (a) {
                count++;
            }
        }
        return count;
    }

    /** Kartu di tangan {@code seat}. Rahasia: kirim hanya ke pemiliknya. */
    public List<Card> hand(int seat) {
        return Collections.unmodifiableList(new ArrayList<>(hands.get(seat)));
    }

    public List<Integer> handSizes() {
        List<Integer> sizes = new ArrayList<>(players);
        for (List<Card> hand : hands) {
            sizes.add(hand.size());
        }
        return List.copyOf(sizes);
    }

    /** Kursi yang sedang giliran, -1 di luar fase {@link Phase#TURN}. */
    public int currentSeat() {
        return current;
    }

    public boolean canCallLiar() {
        return phase == Phase.TURN && lastPlay != null;
    }

    public Rank tableRank() {
        return tableRank;
    }

    public int round() {
        return round;
    }

    public int decks() {
        return decks;
    }

    public int pulls(int seat) {
        return revolvers[seat].pulls();
    }

    public int lastPlaySeat() {
        return lastPlay == null ? -1 : lastPlay.seat();
    }

    public int lastPlayCount() {
        return lastPlay == null ? 0 : lastPlay.cards().size();
    }

    public int cardsPlayedThisRound() {
        return cardsPlayedThisRound;
    }

    public List<Integer> shooters() {
        return shooters;
    }

    /** -1 sampai {@link Phase#GAME_OVER}. */
    public int winner() {
        return winner;
    }

    public SeatView viewFor(int seat) {
        return new SeatView(seat, hand(seat), tableRank, decks, canCallLiar() && current == seat, lastPlayCount(),
                cardsPlayedThisRound, handSizes(), aliveCount(), revolvers[seat].pulls(), rules.chambers());
    }

    // ========================================================= untuk tes

    /** Ronde dengan tangan dan table card yang ditentukan (tes). Kursi mati harus bertangan kosong. */
    List<GameEvent> rigRound(int starter, Rank table, List<List<Card>> riggedHands) {
        if (riggedHands.size() != players) {
            throw new IllegalArgumentException("need " + players + " hands");
        }
        round++;
        decks = DeckBuilder.decksFor(aliveCount());
        tableRank = table;
        for (int seat = 0; seat < players; seat++) {
            hands.get(seat).clear();
            hands.get(seat).addAll(riggedHands.get(seat));
        }
        return beginRound(starter);
    }

    /** Ganti revolver {@code seat} dengan peluru di chamber {@code bullet} (0 = tarikan pertama). */
    void rigRevolver(int seat, int bullet) {
        revolvers[seat] = new Revolver(rules.chambers(), bullet);
    }
}
