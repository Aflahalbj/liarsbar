package com.liarsbar.game.deck;

import com.liarsbar.game.deck.GameEvent.CardsPlayed;
import com.liarsbar.game.deck.GameEvent.GameWon;
import com.liarsbar.game.deck.GameEvent.LiarCalled;
import com.liarsbar.game.deck.GameEvent.PlayerEliminated;
import com.liarsbar.game.deck.GameEvent.Revealed;
import com.liarsbar.game.deck.GameEvent.RoundStarted;
import com.liarsbar.game.deck.GameEvent.RouletteStarted;
import com.liarsbar.game.deck.GameEvent.TriggerPulled;
import com.liarsbar.game.deck.GameEvent.TurnStarted;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.liarsbar.game.deck.Rank.ACE;
import static com.liarsbar.game.deck.Rank.JOKER;
import static com.liarsbar.game.deck.Rank.KING;
import static com.liarsbar.game.deck.Rank.QUEEN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LiarsDeckGameTest {

    private int nextId;

    /** Tangan berisi kartu baru dengan id unik di tes ini. */
    private List<Card> hand(Rank... ranks) {
        List<Card> cards = new ArrayList<>();
        for (Rank rank : ranks) {
            cards.add(new Card(nextId++, rank));
        }
        return cards;
    }

    private static List<Integer> ids(List<Card> cards) {
        return cards.stream().map(Card::id).toList();
    }

    private static List<Integer> ids(Card... cards) {
        return ids(List.of(cards));
    }

    private static LiarsDeckGame game(int players) {
        return new LiarsDeckGame(players, LiarsDeckRules.STANDARD, 42);
    }

    private static <T extends GameEvent> T find(List<GameEvent> events, Class<T> type) {
        for (GameEvent event : events) {
            if (type.isInstance(event)) {
                return type.cast(event);
            }
        }
        throw new AssertionError("no " + type.getSimpleName() + " in " + events);
    }

    private static boolean has(List<GameEvent> events, Class<? extends GameEvent> type) {
        return events.stream().anyMatch(type::isInstance);
    }

    // ===================================================== pembagian kartu

    @Test
    void everyPlayerGetsFiveCardsFromTheScaledDeck() {
        for (int players = 2; players <= 10; players++) {
            LiarsDeckGame game = game(players);
            List<GameEvent> events = game.start();
            RoundStarted started = find(events, RoundStarted.class);
            int decks = DeckBuilder.decksFor(players);
            assertEquals(decks, started.decks());
            assertTrue(Rank.TABLE_RANKS.contains(started.tableRank()), "table card is K/Q/A");
            Set<Integer> seen = new HashSet<>();
            for (int seat = 0; seat < players; seat++) {
                List<Card> hand = game.hand(seat);
                assertEquals(5, hand.size(), players + " players, seat " + seat);
                for (Card card : hand) {
                    assertTrue(seen.add(card.id()), "card dealt twice");
                    assertTrue(card.id() < 20 * decks, "card from the scaled deck");
                }
            }
            TurnStarted turn = find(events, TurnStarted.class);
            assertEquals(started.starter(), turn.seat());
            assertFalse(turn.canCallLiar(), "no LIAR on the first turn");
        }
    }

    @Test
    void deckCountIsRecomputedWhenPlayersDie() {
        LiarsDeckGame game = game(5);
        game.rigRound(0, KING, List.of(hand(QUEEN), hand(KING, KING), hand(KING), hand(KING), hand(KING)));
        assertEquals(2, game.decks());
        game.rigRevolver(0, 0);
        game.play(0, ids(game.hand(0)));
        game.callLiar(1);
        game.pullTrigger();
        List<GameEvent> events = game.nextRound();
        assertEquals(1, find(events, RoundStarted.class).decks(), "4 alive -> 1 deck");
    }

    // ======================================================== aksi tidak sah

    @Test
    void rejectsIllegalActionsWithoutChangingState() {
        LiarsDeckGame game = game(3);
        List<Card> h0 = hand(KING, KING, KING, KING, QUEEN);
        List<Card> h1 = hand(ACE, ACE);
        game.rigRound(0, KING, List.of(h0, h1, hand(ACE)));

        assertThrows(RuleViolation.class, () -> game.play(1, ids(h1.get(0))), "out of turn");
        assertThrows(RuleViolation.class, () -> game.play(0, List.of()), "zero cards");
        assertThrows(RuleViolation.class, () -> game.play(0, ids(h0.subList(0, 4))), "four cards");
        assertThrows(RuleViolation.class, () -> game.play(0, ids(h1.get(0))), "not own card");
        assertThrows(RuleViolation.class, () -> game.play(0, List.of(h0.get(0).id(), h0.get(0).id())), "duplicate");
        assertThrows(RuleViolation.class, () -> game.callLiar(0), "LIAR on first turn");
        assertThrows(RuleViolation.class, game::pullTrigger, "no roulette pending");
        assertThrows(RuleViolation.class, game::nextRound, "round not over");

        assertEquals(5, game.hand(0).size());
        assertEquals(0, game.currentSeat());
        assertEquals(Phase.TURN, game.phase());
    }

    // ========================================================== urutan giliran

    @Test
    void turnPassesToTheNextSeatAndWrapsAround() {
        LiarsDeckGame game = game(4);
        game.rigRound(3, KING, List.of(hand(KING, KING), hand(KING, KING), hand(KING, KING), hand(KING, KING)));
        List<GameEvent> events = game.play(3, ids(game.hand(3).get(0)));
        TurnStarted turn = find(events, TurnStarted.class);
        assertEquals(0, turn.seat());
        assertTrue(turn.canCallLiar());
    }

    @Test
    void playersWithoutCardsAreSkipped() {
        LiarsDeckGame game = game(4);
        game.rigRound(0, KING, List.of(hand(KING), hand(KING, KING), hand(KING, KING), hand(KING, KING)));
        game.play(0, ids(game.hand(0)));            // kursi 0 habis
        game.play(1, ids(game.hand(1).get(0)));
        game.play(2, ids(game.hand(2).get(0)));
        List<GameEvent> events = game.play(3, ids(game.hand(3).get(0)));
        assertEquals(1, find(events, TurnStarted.class).seat(), "seat 0 has no cards, skipped");
    }

    // ================================================================ reveal

    @Test
    void caughtLiarShoots() {
        LiarsDeckGame game = game(3);
        game.rigRound(0, KING, List.of(hand(KING, QUEEN, ACE), hand(KING, KING), hand(KING)));
        game.play(0, ids(game.hand(0).subList(0, 2)));   // KING + QUEEN -> bohong
        List<GameEvent> events = game.callLiar(1);
        assertEquals(new LiarCalled(1, 0, false), find(events, LiarCalled.class));
        assertTrue(find(events, Revealed.class).lie());
        assertEquals(List.of(0), find(events, RouletteStarted.class).shooters());
        assertEquals(Phase.ROULETTE, game.phase());
    }

    @Test
    void wrongAccuserShoots() {
        LiarsDeckGame game = game(3);
        game.rigRound(0, KING, List.of(hand(KING, JOKER, ACE), hand(KING, KING), hand(KING)));
        game.play(0, ids(game.hand(0).subList(0, 2)));   // KING + JOKER -> jujur
        List<GameEvent> events = game.callLiar(1);
        Revealed revealed = find(events, Revealed.class);
        assertFalse(revealed.lie(), "Joker counts as the table card");
        assertEquals(2, revealed.cards().size());
        assertEquals(List.of(1), find(events, RouletteStarted.class).shooters());
    }

    @Test
    void onlyTheLastPlayIsRevealed() {
        LiarsDeckGame game = game(3);
        game.rigRound(0, ACE, List.of(hand(QUEEN, QUEEN), hand(ACE, ACE), hand(KING, KING)));
        game.play(0, ids(game.hand(0).get(0)));          // bohong, tapi bukan permainan terakhir
        game.play(1, ids(game.hand(1).get(0)));          // jujur
        List<GameEvent> events = game.callLiar(2);
        Revealed revealed = find(events, Revealed.class);
        assertEquals(1, revealed.accused());
        assertEquals(1, revealed.cards().size());
        assertFalse(revealed.lie());
        assertEquals(List.of(2), find(events, RouletteStarted.class).shooters());
    }

    // ========================================================= tuduhan paksa

    @Test
    void lastPlayerHoldingCardsIsForcedToCall() {
        LiarsDeckGame game = game(3);
        game.rigRound(0, KING, List.of(hand(KING), hand(QUEEN), hand(KING, KING)));
        game.play(0, ids(game.hand(0)));                 // kursi 0 habis, masih ada 1 dan 2
        List<GameEvent> events = game.play(1, ids(game.hand(1)));  // kursi 1 habis: tinggal kursi 2
        assertEquals(new LiarCalled(2, 1, true), find(events, LiarCalled.class));
        assertTrue(find(events, Revealed.class).lie());
        assertEquals(List.of(1), find(events, RouletteStarted.class).shooters());
        assertFalse(has(events, TurnStarted.class));
    }

    @Test
    void headsUpLastCardIsCalledAutomatically() {
        LiarsDeckGame game = game(2);
        game.rigRound(0, QUEEN, List.of(hand(QUEEN, JOKER), hand(ACE, ACE, ACE, ACE, ACE)));
        List<GameEvent> events = game.play(0, ids(game.hand(0)));
        assertEquals(new LiarCalled(1, 0, true), find(events, LiarCalled.class));
        assertFalse(find(events, Revealed.class).lie());
        assertEquals(List.of(1), find(events, RouletteStarted.class).shooters(), "honest -> caller shoots");
    }

    // ============================================================== roulette

    @Test
    void survivorStartsTheNextRoundAndHisCounterCarriesOver() {
        LiarsDeckGame game = game(3);
        game.rigRevolver(1, 5);
        game.rigRound(0, KING, List.of(hand(KING, KING), hand(KING, KING), hand(KING, KING)));
        game.play(0, ids(game.hand(0).get(0)));
        game.callLiar(1);                                  // salah tuduh -> kursi 1 menembak
        List<GameEvent> shot = game.pullTrigger();
        assertEquals(new TriggerPulled(1, 1, false), find(shot, TriggerPulled.class));
        assertEquals(Phase.ROUND_OVER, game.phase());
        List<GameEvent> events = game.nextRound();
        assertEquals(1, find(events, RoundStarted.class).starter(), "shooter starts the next round");
        assertEquals(1, game.pulls(1), "revolver is not reset between rounds");

        // Ronde kedua: kursi 1 menembak lagi, tarikan ke-2.
        game.rigRound(1, KING, List.of(hand(KING, KING), hand(QUEEN, QUEEN), hand(KING, KING)));
        game.play(1, ids(game.hand(1).get(0)));
        game.callLiar(2);
        assertEquals(new TriggerPulled(1, 2, false), find(game.pullTrigger(), TriggerPulled.class));
    }

    @Test
    void whenTheShooterDiesTheNextLivingPlayerStarts() {
        LiarsDeckGame game = game(4);
        game.rigRevolver(2, 0);
        game.rigRound(1, KING, List.of(hand(KING), hand(KING, KING), hand(ACE, ACE), hand(KING, KING)));
        game.play(1, ids(game.hand(1).get(0)));
        game.play(2, ids(game.hand(2).get(0)));            // bohong
        game.callLiar(3);
        List<GameEvent> shot = game.pullTrigger();
        assertEquals(new PlayerEliminated(2), find(shot, PlayerEliminated.class));
        List<GameEvent> events = game.nextRound();
        assertEquals(3, find(events, RoundStarted.class).starter());
        assertEquals(0, game.hand(2).size(), "dead players get no cards");
        assertEquals(List.of(5, 5, 0, 5), find(events, RoundStarted.class).handSizes());
    }

    @Test
    void deadPlayersAreSkippedInTurnOrder() {
        LiarsDeckGame game = game(3);
        game.rigRevolver(1, 0);
        game.rigRound(1, KING, List.of(hand(KING, KING), hand(ACE, ACE), hand(KING, KING)));
        game.play(1, ids(game.hand(1).get(0)));
        game.callLiar(2);
        game.pullTrigger();                                // kursi 1 mati
        game.rigRound(0, KING, List.of(hand(KING, KING), List.of(), hand(KING, KING)));
        List<GameEvent> events = game.play(0, ids(game.hand(0).get(0)));
        assertEquals(2, find(events, TurnStarted.class).seat());
    }

    @Test
    void lastPlayerStandingWins() {
        LiarsDeckGame game = game(2);
        game.rigRevolver(0, 0);
        game.rigRound(0, KING, List.of(hand(ACE, ACE), hand(KING, KING)));
        game.play(0, ids(game.hand(0).get(0)));
        game.callLiar(1);
        List<GameEvent> shot = game.pullTrigger();
        assertEquals(new GameWon(1), find(shot, GameWon.class));
        assertEquals(Phase.GAME_OVER, game.phase());
        assertEquals(1, game.winner());
        assertThrows(RuleViolation.class, game::nextRound);
        assertThrows(RuleViolation.class, () -> game.callLiar(1));
    }

    @Test
    void sixthPullAlwaysKills() {
        LiarsDeckGame game = game(2);
        game.rigRevolver(0, 5);
        for (int pull = 1; pull <= 6; pull++) {
            game.rigRound(0, KING, List.of(hand(ACE, ACE), hand(KING, KING)));
            game.play(0, ids(game.hand(0).get(0)));
            game.callLiar(1);
            TriggerPulled result = find(game.pullTrigger(), TriggerPulled.class);
            assertEquals(pull, result.pullNumber());
            assertEquals(pull == 6, result.fired());
        }
        assertEquals(Phase.GAME_OVER, game.phase());
    }

    // ================================================================ timer

    @Test
    void timeoutPlaysOneCard() {
        LiarsDeckGame game = game(3);
        game.rigRound(0, KING, List.of(hand(KING, QUEEN, ACE), hand(KING), hand(KING)));
        List<GameEvent> events = game.timeout(0);
        assertEquals(1, find(events, CardsPlayed.class).count());
        assertEquals(2, game.hand(0).size());
        assertEquals(1, game.currentSeat());
        assertThrows(RuleViolation.class, () -> game.timeout(0), "not his turn any more");
    }

    // ============================================================ determinisme

    @Test
    void sameSeedSameGame() {
        List<String> a = new ArrayList<>();
        List<String> b = new ArrayList<>();
        DeckSimulator.play(7, 99, 0.05, (game, events) -> a.add(events.toString()));
        DeckSimulator.play(7, 99, 0.05, (game, events) -> b.add(events.toString()));
        assertEquals(a, b);
        List<String> c = new ArrayList<>();
        DeckSimulator.play(7, 100, 0.05, (game, events) -> c.add(events.toString()));
        assertFalse(a.equals(c), "different seed, different game");
    }

    @Test
    void handsAreCopies() {
        LiarsDeckGame game = game(2);
        game.start();
        List<Card> hand = game.hand(0);
        assertThrows(UnsupportedOperationException.class, () -> hand.remove(0));
        assertInstanceOf(List.class, hand);
    }
}
