package com.liarsbar.game.deck;

import java.util.ArrayList;
import java.util.List;

/**
 * Deck Liar's Deck. Satu deck = 6 King, 6 Queen, 6 Ace, 2 Joker (20 kartu),
 * persis aslinya. Meja besar memakai beberapa deck sekaligus:
 * ceil(pemain hidup / 4), dihitung ulang tiap ronde.
 *
 * <pre>
 *   hidup 2-4  -> 1 deck (20)
 *   hidup 5-8  -> 2 deck (40)
 *   hidup 9-10 -> 3 deck (60)
 * </pre>
 */
public final class DeckBuilder {

    public static final int PER_RANK = 6;
    public static final int JOKERS = 2;
    public static final int DECK_SIZE = PER_RANK * 3 + JOKERS;
    public static final int PLAYERS_PER_DECK = 4;

    private DeckBuilder() {
    }

    public static int decksFor(int alivePlayers) {
        if (alivePlayers < 1) {
            throw new IllegalArgumentException("alivePlayers=" + alivePlayers);
        }
        return (alivePlayers + PLAYERS_PER_DECK - 1) / PLAYERS_PER_DECK;
    }

    /** {@code decks} deck, belum dikocok, id 0 .. n-1. */
    public static List<Card> build(int decks) {
        List<Card> cards = new ArrayList<>(DECK_SIZE * decks);
        int id = 0;
        for (int d = 0; d < decks; d++) {
            for (Rank rank : Rank.TABLE_RANKS) {
                for (int i = 0; i < PER_RANK; i++) {
                    cards.add(new Card(id++, rank));
                }
            }
            for (int i = 0; i < JOKERS; i++) {
                cards.add(new Card(id++, Rank.JOKER));
            }
        }
        return cards;
    }
}
