package com.liarsbar.game;

/**
 * Revolver milik satu pemain: {@code chambers} chamber, satu peluru. Posisi
 * peluru diacak sekali saat game dimulai dan tidak pernah diacak ulang; tiap
 * tarikan memajukan silinder satu chamber. Penghitungnya tidak di-reset antar
 * ronde, jadi tarikan terakhir (ke-6) pasti meletus.
 *
 * <p>Posisi peluru adalah info rahasia server: tidak ada getter publik.
 */
public final class Revolver {

    private final int chambers;
    private final int bullet;
    private int pulls;

    public Revolver(int chambers, int bullet) {
        if (chambers < 1 || bullet < 0 || bullet >= chambers) {
            throw new IllegalArgumentException("chambers=" + chambers + " bullet=" + bullet);
        }
        this.chambers = chambers;
        this.bullet = bullet;
    }

    public static Revolver random(int chambers, Rng rng) {
        return new Revolver(chambers, rng.nextInt(chambers));
    }

    /** Tarik pelatuk sekali. True = meletus. */
    public boolean pull() {
        if (pulls >= chambers) {
            throw new IllegalStateException("revolver already fired");
        }
        boolean fired = pulls == bullet;
        pulls++;
        return fired;
    }

    /** Berapa kali pelatuk sudah ditarik (angka "n" di HUD "(n|6)"). */
    public int pulls() {
        return pulls;
    }

    public int chambers() {
        return chambers;
    }

    /** Hanya untuk tes statistik di paket ini. */
    int bulletForTest() {
        return bullet;
    }
}
