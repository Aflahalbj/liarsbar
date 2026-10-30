package com.liarsbar.game.deck;

/**
 * Aksi yang tidak sah (bukan gilirannya, kartu bukan miliknya, dst.). Server
 * menolak paketnya; state game tidak berubah.
 */
public final class RuleViolation extends RuntimeException {

    public RuleViolation(String message) {
        super(message);
    }
}
