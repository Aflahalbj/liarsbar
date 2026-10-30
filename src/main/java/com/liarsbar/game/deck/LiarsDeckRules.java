package com.liarsbar.game.deck;

/**
 * Angka-angka aturan. {@link #STANDARD} = Liar's Deck asli (Basic ruleset).
 *
 * @param handSize      kartu per pemain tiap ronde
 * @param maxPlay       kartu maksimal sekali main
 * @param chambers      chamber revolver (satu peluru)
 */
public record LiarsDeckRules(int handSize, int maxPlay, int chambers) {

    public static final LiarsDeckRules STANDARD = new LiarsDeckRules(5, 3, 6);

    public static final int MIN_PLAYERS = 2;
    public static final int MAX_PLAYERS = 10;
}
