package com.liarsbar.game.deck;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Game penuh dengan bot di semua kursi, 2-10 pemain: tidak boleh macet,
 * invarian dicek setelah tiap aksi, dan statistik pacing dicetak.
 */
class SimulationTest {

    private static final int CHECKED_GAMES = 1_000;
    private static final int STAT_GAMES = 10_000;

    @Test
    void invariantsHoldAfterEveryAction() {
        for (int players = 2; players <= 10; players++) {
            int forced = 0;
            int timeouts = 0;
            for (int i = 0; i < CHECKED_GAMES; i++) {
                int n = players;
                DeckSimulator.GameResult result = DeckSimulator.play(n, 1000L * n + i, 0.05,
                        (game, events) -> checkInvariants(game));
                assertTrue(result.winner() >= 0 && result.winner() < players);
                assertTrue(result.rounds() >= players - 1, "at least one roulette per death");
                forced += result.forcedCalls();
                timeouts += result.timeouts();
            }
            // Jalur tuduhan paksa dan timeout harus ikut teruji, bukan cuma jalur biasa.
            assertTrue(forced > 0, players + " players: no forced call happened");
            assertTrue(timeouts > 0, players + " players: no timeout happened");
        }
    }

    private static void checkInvariants(LiarsDeckGame game) {
        int handSize = game.rules().handSize();
        Set<Integer> ids = new HashSet<>();
        int alive = 0;
        for (int seat = 0; seat < game.players(); seat++) {
            int size = game.hand(seat).size();
            assertTrue(size <= handSize, "hand too big");
            if (game.isAlive(seat)) {
                alive++;
            } else {
                assertEquals(0, size, "dead seat holds cards");
            }
            for (Card card : game.hand(seat)) {
                assertTrue(ids.add(card.id()), "card in two hands");
                assertTrue(card.id() < DeckBuilder.DECK_SIZE * game.decks(), "card outside the deck");
            }
            assertTrue(game.pulls(seat) <= game.rules().chambers());
        }
        assertEquals(alive, game.aliveCount());
        if (game.phase() == Phase.TURN || game.phase() == Phase.ROULETTE) {
            // Kematian hanya terjadi di akhir ronde, jadi selama ronde jumlah deck = decksFor(hidup).
            assertEquals(DeckBuilder.decksFor(alive), game.decks(), "deck count");
        }
        switch (game.phase()) {
            case TURN -> {
                int seat = game.currentSeat();
                assertTrue(game.isAlive(seat), "dead player's turn");
                assertTrue(!game.hand(seat).isEmpty(), "turn without cards");
                long holders = 0;
                for (int s = 0; s < game.players(); s++) {
                    if (game.isAlive(s) && !game.hand(s).isEmpty()) {
                        holders++;
                    }
                }
                assertTrue(holders >= 2 || game.lastPlaySeat() < 0, "a lone holder must have been forced to call");
            }
            case ROULETTE -> assertEquals(1, game.shooters().size());
            case GAME_OVER -> assertEquals(1, game.aliveCount());
            default -> {
            }
        }
    }

    @Test
    void pacingStatistics() {
        System.out.println();
        System.out.println("players | games | avg roulette rounds | min | max | turns/round | forced calls");
        long start = System.nanoTime();
        for (int players = 2; players <= 10; players++) {
            DeckSimulator.Summary s = DeckSimulator.run(players, STAT_GAMES, 7_000_000L + players);
            System.out.printf("%7d | %5d | %19.1f | %3d | %3d | %11.1f | %11.0f%%%n", s.players(), s.games(),
                    s.avgRounds(), s.minRounds(), s.maxRounds(), s.avgTurnsPerRound(), s.forcedCallShare() * 100);
            assertTrue(s.avgRounds() >= players - 1);
        }
        System.out.printf("(%.1f s)%n", (System.nanoTime() - start) / 1e9);
    }
}
