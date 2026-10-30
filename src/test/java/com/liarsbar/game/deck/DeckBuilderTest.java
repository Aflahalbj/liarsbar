package com.liarsbar.game.deck;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeckBuilderTest {

    @Test
    void decksScaleWithAlivePlayers() {
        int[] expected = {0, 0, 1, 1, 1, 2, 2, 2, 2, 3, 3};
        for (int alive = 2; alive <= 10; alive++) {
            assertEquals(expected[alive], DeckBuilder.decksFor(alive), "alive " + alive);
        }
    }

    @Test
    void eachDeckIsSixKingsSixQueensSixAcesTwoJokers() {
        for (int decks = 1; decks <= 3; decks++) {
            List<Card> cards = DeckBuilder.build(decks);
            assertEquals(20 * decks, cards.size());
            Map<Rank, Integer> counts = new EnumMap<>(Rank.class);
            Set<Integer> ids = new HashSet<>();
            for (Card card : cards) {
                counts.merge(card.rank(), 1, Integer::sum);
                ids.add(card.id());
            }
            assertEquals(6 * decks, counts.get(Rank.KING));
            assertEquals(6 * decks, counts.get(Rank.QUEEN));
            assertEquals(6 * decks, counts.get(Rank.ACE));
            assertEquals(2 * decks, counts.get(Rank.JOKER));
            assertEquals(cards.size(), ids.size(), "ids unique");
        }
    }
}
