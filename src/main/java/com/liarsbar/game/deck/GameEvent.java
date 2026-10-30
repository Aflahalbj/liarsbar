package com.liarsbar.game.deck;

import java.util.List;

/**
 * Kejadian yang dihasilkan {@link LiarsDeckGame}. Semua isinya info publik
 * pada saat kejadian itu terjadi (kartu di tangan tidak pernah ada di sini;
 * ambil lewat {@link LiarsDeckGame#hand(int)} dan kirim hanya ke pemiliknya).
 * Kursi = indeks urutan giliran (0 .. pemain-1).
 */
public sealed interface GameEvent {

    /** Ronde baru: kartu sudah dibagi, table card sudah dipilih. {@code handSizes} per kursi (0 = mati). */
    record RoundStarted(int round, Rank tableRank, int starter, int decks, List<Integer> handSizes) implements GameEvent {
    }

    /** Giliran {@code seat}. {@code canCallLiar} false di giliran pertama ronde. */
    record TurnStarted(int seat, boolean canCallLiar) implements GameEvent {
    }

    /** {@code seat} menaruh {@code count} kartu tertutup dan mengaku semuanya {@code claim}. */
    record CardsPlayed(int seat, int count, Rank claim, int cardsLeft) implements GameEvent {
    }

    /**
     * {@code caller} menuduh {@code accused}. {@code forced}: tinggal {@code caller}
     * yang masih pegang kartu, jadi tuduhan dilakukan otomatis.
     */
    record LiarCalled(int caller, int accused, boolean forced) implements GameEvent {
    }

    /** Kartu permainan terakhir dibuka. {@code lie}: ada kartu yang bukan table card/Joker. */
    record Revealed(int accused, List<Card> cards, boolean lie) implements GameEvent {
    }

    /** Siapa saja yang harus menarik pelatuk (hasilnya belum ditentukan). */
    record RouletteStarted(List<Integer> shooters) implements GameEvent {
    }

    /** Hasil satu tarikan. {@code pullNumber} 1..6 = tarikan ke berapa untuk revolver ini. */
    record TriggerPulled(int seat, int pullNumber, boolean fired) implements GameEvent {
    }

    record PlayerEliminated(int seat) implements GameEvent {
    }

    record GameWon(int seat) implements GameEvent {
    }
}
