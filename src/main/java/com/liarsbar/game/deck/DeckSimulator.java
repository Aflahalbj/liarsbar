package com.liarsbar.game.deck;

import com.liarsbar.game.Rng;
import com.liarsbar.game.bot.DeckBot;

import java.util.List;

/**
 * Memainkan game penuh dengan bot di semua kursi, untuk uji ketahanan engine
 * dan perkiraan pacing (berapa ronde roulette per game). Dipakai unit test dan
 * {@code /liarsbar sim}.
 */
public final class DeckSimulator {

    /** Batas aman: game normal jauh di bawah ini. */
    public static final int MAX_ACTIONS = 100_000;

    public record GameResult(int players, int rounds, int turns, int timeouts, int forcedCalls, int winner) {
    }

    public record Summary(int players, int games, double avgRounds, int minRounds, int maxRounds,
                          double avgTurnsPerRound, double forcedCallShare) {
    }

    private DeckSimulator() {
    }

    /**
     * Satu game. {@code timeoutChance}: peluang tiap giliran dibiarkan habis
     * waktunya (menguji jalur timeout). {@code observer} boleh null.
     */
    public static GameResult play(int players, long seed, double timeoutChance, Observer observer) {
        LiarsDeckRules rules = LiarsDeckRules.STANDARD;
        LiarsDeckGame game = new LiarsDeckGame(players, rules, seed);
        Rng botRng = new Rng(seed ^ 0x5DEECE66DL);
        DeckBot[] bots = new DeckBot[players];
        for (int seat = 0; seat < players; seat++) {
            bots[seat] = new DeckBot(rules, botRng.nextDouble());
        }
        int turns = 0;
        int timeouts = 0;
        int forced = 0;
        int rounds = 0;
        List<GameEvent> events = game.start();
        for (int actions = 0; ; actions++) {
            if (actions > MAX_ACTIONS) {
                throw new IllegalStateException("game did not finish: players=" + players + " seed=" + seed);
            }
            for (GameEvent event : events) {
                if (event instanceof GameEvent.LiarCalled called && called.forced()) {
                    forced++;
                }
                if (event instanceof GameEvent.RouletteStarted) {
                    rounds++;
                }
            }
            if (observer != null) {
                observer.after(game, events);
            }
            switch (game.phase()) {
                case TURN -> {
                    int seat = game.currentSeat();
                    turns++;
                    if (botRng.nextDouble() < timeoutChance) {
                        timeouts++;
                        events = game.timeout(seat);
                    } else {
                        DeckBot.Decision decision = bots[seat].decide(game.viewFor(seat), botRng);
                        events = decision instanceof DeckBot.Decision.Play p
                                ? game.play(seat, p.cardIds())
                                : game.callLiar(seat);
                    }
                }
                case ROULETTE -> events = game.pullTrigger();
                case ROUND_OVER -> events = game.nextRound();
                case GAME_OVER -> {
                    return new GameResult(players, rounds, turns, timeouts, forced, game.winner());
                }
                default -> throw new IllegalStateException("unexpected phase " + game.phase());
            }
        }
    }

    public static Summary run(int players, int games, long baseSeed) {
        long totalRounds = 0;
        long totalTurns = 0;
        long totalForced = 0;
        int min = Integer.MAX_VALUE;
        int max = 0;
        for (int i = 0; i < games; i++) {
            GameResult result = play(players, baseSeed + i * 0x9E3779B97F4A7C15L, 0.02, null);
            totalRounds += result.rounds();
            totalTurns += result.turns();
            totalForced += result.forcedCalls();
            min = Math.min(min, result.rounds());
            max = Math.max(max, result.rounds());
        }
        return new Summary(players, games, totalRounds / (double) games, min, max,
                totalTurns / (double) totalRounds, totalForced / (double) totalRounds);
    }

    /** Dipanggil setelah tiap aksi (untuk cek invarian di tes). */
    @FunctionalInterface
    public interface Observer {
        void after(LiarsDeckGame game, List<GameEvent> events);
    }
}
