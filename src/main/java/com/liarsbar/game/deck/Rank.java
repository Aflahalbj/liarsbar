package com.liarsbar.game.deck;

import java.util.List;

/** Jenis kartu Liar's Deck. Joker = wild (selalu dihitung jujur). */
public enum Rank {
    KING,
    QUEEN,
    ACE,
    JOKER;

    /** Jenis yang bisa jadi table card. */
    public static final List<Rank> TABLE_RANKS = List.of(KING, QUEEN, ACE);

    /** Kartu ini jujur kalau diakui sebagai {@code tableRank}. */
    public boolean truthfulOn(Rank tableRank) {
        return this == JOKER || this == tableRank;
    }
}
