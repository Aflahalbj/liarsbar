package com.liarsbar.client.test;

import java.util.HashMap;
import java.util.Map;

/** Saklar fitur untuk mengukur biaya render (AnimDev "toggle NAMA"). Semua menyala di permainan biasa. */
public final class DevToggles {

    private static final Map<String, Boolean> OFF = new HashMap<>();

    private DevToggles() {
    }

    public static boolean on(String name) {
        return !OFF.getOrDefault(name, false);
    }

    public static boolean toggle(String name) {
        boolean off = !OFF.getOrDefault(name, false);
        OFF.put(name, off);
        return !off;
    }
}
