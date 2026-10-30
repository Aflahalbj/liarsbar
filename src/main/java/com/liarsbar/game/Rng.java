package com.liarsbar.game;

import java.util.List;
import java.util.SplittableRandom;

/**
 * Sumber acak rules engine. Satu instance per game, di-seed server, jadi
 * urutan aksi yang sama dengan seed yang sama selalu menghasilkan game yang
 * sama persis (kocokan, table card, posisi peluru).
 */
public final class Rng {

    private final SplittableRandom random;

    public Rng(long seed) {
        this.random = new SplittableRandom(seed);
    }

    /** 0 (inklusif) .. {@code bound} (eksklusif). */
    public int nextInt(int bound) {
        return random.nextInt(bound);
    }

    /** 0..1. */
    public double nextDouble() {
        return random.nextDouble();
    }

    /** Fisher-Yates di tempat. */
    public <T> void shuffle(List<T> list) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
    }

    public <T> T pick(List<T> list) {
        return list.get(random.nextInt(list.size()));
    }
}
