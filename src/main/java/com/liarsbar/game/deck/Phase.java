package com.liarsbar.game.deck;

/** Keadaan {@link LiarsDeckGame}; aksi yang sah bergantung pada ini. */
public enum Phase {
    /** Belum {@link LiarsDeckGame#start()}. */
    NOT_STARTED,
    /** Menunggu pemain giliran: main kartu atau LIAR. */
    TURN,
    /** Penembak sudah ditentukan; server memanggil {@link LiarsDeckGame#pullTrigger()} di momen tembakan. */
    ROULETTE,
    /** Semua selamat atau ada yang mati tapi game belum selesai; menunggu {@link LiarsDeckGame#nextRound()}. */
    ROUND_OVER,
    GAME_OVER
}
