package com.liarsbar.game.deck;

/**
 * Satu kartu fisik. {@code id} unik di dalam satu ronde (0 .. jumlah kartu - 1),
 * dipakai client untuk memilih kartu yang dimainkan tanpa perlu tahu isinya
 * dikirim balik.
 */
public record Card(int id, Rank rank) {
}
