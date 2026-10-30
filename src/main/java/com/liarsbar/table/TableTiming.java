package com.liarsbar.table;

/**
 * Semua durasi sekuens meja, dalam tick (20/detik). Dipakai server (berapa
 * lama menunggu sebelum langkah berikutnya) dan client (timer di HUD, durasi
 * presentasi), supaya keduanya selalu cocok.
 *
 * <p>Versi pendek dipakai mode cepat. {@code scale} hanya untuk uji dev
 * ({@code /liarsbar dev timescale}).
 */
public final class TableTiming {

    /**
     * Ronde baru, kartu dibagi sampai giliran pertama dimulai: dealer mengumpulkan kartu ronde lalu,
     * mengocok (riffle di felt, diselingi bridge; versi penuh tiga riffle, pendek dua), merapikan dan
     * mengangkat dek ke tangan kiri, melempar kartu satu per satu berkeliling meja (5 putaran), lalu semua
     * mengambil kartunya. Lihat {@link #deal}.
     */
    public static final int GATHER = 16;
    /** Satu riffle: belah, kartu berjatuhan berselang-seling, didorong menyatu, jeda. */
    public static final int RIFFLE = 26;
    /** Bridge di antara dua riffle: dek dilengkungkan di antara kedua tangan lalu berjatuhan. */
    public static final int BRIDGE = 12;
    /** Dek dirapikan diketuk ke felt lalu diangkat ke tangan kiri. */
    public static final int SQUARE = 8;
    public static final int SHUFFLE_FULL = riffles(true) * RIFFLE + (riffles(true) - 1) * BRIDGE + SQUARE;
    public static final int SHUFFLE_SHORT = riffles(false) * RIFFLE + (riffles(false) - 1) * BRIDGE + SQUARE;
    /** Lemparan kartu terlama sampai berhenti meluncur di felt. */
    public static final int FLIGHT = 12;
    /** Tangan kiri meraih paket, mengangkatnya, dan membuka kipas. Harus sama dengan TIMING['pickup'] di tools/table_anims.py. */
    public static final int PICKUP = 16;
    /**
     * Kartu dimainkan sampai giliran berikutnya: tangan kanan mencabut kartu dari kipas tangan kiri, melemparnya
     * ke tengah, kartu mendarat dan berhenti meluncur. Harus sama dengan TIMING['throw'] di tools/table_anims.py.
     */
    public static final int THROW = 30;
    public static final int TURN_NORMAL = 30 * 20;
    public static final int TURN_FAST = 15 * 20;
    /** Bot "berpikir" sebelum beraksi. */
    public static final int BOT_THINK_MIN = 30;
    public static final int BOT_THINK_MAX = 90;
    /** LIAR! sampai kartu dibuka. */
    public static final int CALL_TO_REVEAL = 64;
    /** Kartu dibuka sampai penembak mengangkat pistol. */
    public static final int REVEAL_TO_ROULETTE = 64;
    /**
     * Roulette mulai sampai pelatuk ditarik. Versi penuh (ambil pistol, buka dan putar silinder, angkat,
     * kokang, tahan) dipakai di momen penting; versi pendek (ambil, angkat, kokang, tahan) sesudahnya.
     * Versi isi (tarikan pertama penembak: ambil peluru yang berdiri di samping pistolnya, buka silinder,
     * masukkan peluru, putar, tutup, lalu seperti versi penuh) sekali per pemain.
     * Harus sama dengan TIMING di tools/table_anims.py (dicek RouletteTimelineTest).
     */
    public static final int PULL_LOAD = 232;
    public static final int PULL_FULL = 212;
    public static final int PULL_SHORT = 72;
    /** Setelah tembakan sampai ronde baru: lega + meletakkan pistol / badan jatuh dari kursi. */
    public static final int AFTER_CLICK = 56;
    public static final int AFTER_BANG = 100;
    /** Pemenang diumumkan sampai meja kembali ke lobby. */
    public static final int WIN_HOLD = 200;
    /** Pemain keluar di tengah game: berapa lama kursinya ditunggu sebelum bot mengambil alih. */
    public static final int DISCONNECT_GRACE = 30 * 20;

    private TableTiming() {
    }

    public static int turn(boolean fast) {
        return fast ? TURN_FAST : TURN_NORMAL;
    }

    public static int pull(boolean full) {
        return full ? PULL_FULL : PULL_SHORT;
    }

    public static int pull(boolean full, boolean load) {
        return load ? PULL_LOAD : pull(full);
    }

    /** Jumlah riffle saat mengocok (di antaranya bridge). */
    public static int riffles(boolean full) {
        return full ? 3 : 2;
    }

    public static int shuffle(boolean full) {
        return full ? SHUFFLE_FULL : SHUFFLE_SHORT;
    }

    /** Jumlah lemparan dealer: satu kartu per lemparan, berkeliling meja sampai tiap kursi hidup memegang 5. */
    public static int tosses(int alive) {
        return alive * HAND;
    }

    /** Kartu per pemain (LiarsDeckRules.STANDARD.handSize(); tabel ini tidak bergantung pada paket game). */
    public static final int HAND = 5;

    /** Jarak antar lemparan (tick); meja besar lebih cepat supaya total tetap wajar. */
    public static int tossInterval(int alive) {
        return alive <= 4 ? 6 : alive <= 6 ? 5 : alive <= 8 ? 4 : 3;
    }

    /** Tick pertama lemparan pertama (sesudah mengumpulkan dan mengocok). */
    public static int dealStart(boolean full) {
        return GATHER + shuffle(full);
    }

    /** Tick mulainya semua kursi mengambil paketnya (lemparan terakhir sudah berhenti meluncur). */
    public static int pickupStart(boolean full, int alive) {
        return dealStart(full) + (tosses(alive) - 1) * tossInterval(alive) + FLIGHT;
    }

    /** Lama seluruh pembagian kartu untuk {@code alive} kursi hidup. */
    public static int deal(boolean full, int alive) {
        return pickupStart(full, alive) + PICKUP;
    }

    /** {@code ticks} dikali skala dev, minimal 1 tick. */
    public static int scaled(int ticks, float scale) {
        return Math.max(1, Math.round(ticks * scale));
    }
}
