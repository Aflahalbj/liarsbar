package com.liarsbar.client.fx;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackRepository;
import org.slf4j.Logger;

/**
 * Renderer deferred Veil (dibutuhkan lampu gantung dan api moncong) hanya aktif
 * kalau resource pack bawaan Veil {@code veil:deferred} dipilih. Dinyalakan
 * sekali saat game baru mulai, kalau belum dipilih.
 */
public final class DeferredPack {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PACK = "veil:deferred";
    private static boolean checked;

    private DeferredPack() {
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (checked || mc.getOverlay() != null) {
            return;
        }
        checked = true;
        PackRepository repo = mc.getResourcePackRepository();
        Pack deferred = repo.getPack(PACK);
        if (deferred != null && !repo.getSelectedPacks().contains(deferred)) {
            LOGGER.info("Enabling {} for table lights", PACK);
            repo.addPack(PACK);
            mc.options.updateResourcePacks(repo);
        }
    }
}
