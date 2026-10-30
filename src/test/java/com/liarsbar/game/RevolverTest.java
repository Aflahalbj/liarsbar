package com.liarsbar.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevolverTest {

    @Test
    void firesExactlyOnTheBulletChamber() {
        for (int bullet = 0; bullet < 6; bullet++) {
            Revolver revolver = new Revolver(6, bullet);
            for (int pull = 0; pull < bullet; pull++) {
                assertFalse(revolver.pull(), "bullet " + bullet + " pull " + pull);
            }
            assertTrue(revolver.pull(), "bullet " + bullet);
            assertEquals(bullet + 1, revolver.pulls());
        }
    }

    @Test
    void sixthPullAlwaysFires() {
        Revolver revolver = new Revolver(6, 5);
        for (int i = 0; i < 5; i++) {
            assertFalse(revolver.pull());
        }
        assertTrue(revolver.pull());
        assertThrows(IllegalStateException.class, revolver::pull);
    }

    /** Posisi peluru rata di keenam chamber (chi-square, 5 derajat bebas, p = 0,001 -> 20,52). */
    @Test
    void bulletPositionIsUniform() {
        Rng rng = new Rng(1234);
        int samples = 60_000;
        int[] counts = new int[6];
        for (int i = 0; i < samples; i++) {
            counts[Revolver.random(6, rng).bulletForTest()]++;
        }
        double expected = samples / 6.0;
        double chi = 0;
        for (int count : counts) {
            chi += (count - expected) * (count - expected) / expected;
        }
        System.out.printf("bullet chamber counts %s, chi-square %.2f%n", java.util.Arrays.toString(counts), chi);
        assertTrue(chi < 20.52, "chi-square " + chi);
    }
}
