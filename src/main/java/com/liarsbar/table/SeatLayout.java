package com.liarsbar.table;

import java.util.ArrayList;
import java.util.List;

/**
 * Posisi kursi di sekeliling meja bundar. Meja punya tiga ukuran (2-4, 5-7,
 * 8-10 kursi); kursi yang tidak dipakai tidak dipasang, jadi kursi yang ada
 * selalu tersebar rata.
 *
 * <p>Kursi 0 di sisi selatan, lalu berurutan <b>berlawanan jarum jam</b>
 * dilihat dari atas (urutan giliran game asli: giliran pindah ke pemain di
 * kanan). Koordinat Minecraft: x timur, z selatan.
 */
public final class SeatLayout {

    public enum Size {
        SMALL(1.5f),
        MEDIUM(2.1f),
        LARGE(2.7f);

        /** Jari-jari daun meja (blok). */
        public final float radius;

        Size(float radius) {
            this.radius = radius;
        }

        public static Size forSeats(int seats) {
            return seats <= 4 ? SMALL : seats <= 7 ? MEDIUM : LARGE;
        }
    }

    /** Jarak titik duduk dari tepi meja. */
    public static final double SEAT_GAP = 0.45;
    /** Tinggi daun meja dari lantai. */
    public static final float TABLE_HEIGHT = 0.8f;

    /** Satu kursi: posisi relatif pusat meja dan yaw Minecraft yang menghadap pusat. */
    public record Seat(double dx, double dz, float yaw) {
    }

    private SeatLayout() {
    }

    public static List<Seat> seats(int count) {
        double ring = Size.forSeats(count).radius + SEAT_GAP;
        List<Seat> seats = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            double theta = Math.toRadians(-90 + 360.0 * i / count);
            double dx = ring * Math.cos(theta);
            double dz = -ring * Math.sin(theta);
            seats.add(new Seat(dx, dz, yawToward(-dx, -dz)));
        }
        return seats;
    }

    /** Yaw Minecraft untuk menghadap arah (dx, dz): 0 = selatan (+z), 90 = barat, 180 = utara. */
    public static float yawToward(double dx, double dz) {
        return (float) Math.toDegrees(Math.atan2(-dx, dz));
    }
}
