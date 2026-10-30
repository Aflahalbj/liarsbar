package com.liarsbar.client.fx;

import com.liarsbar.LiarsBar;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraftforge.event.AddPackFindersEvent;
import net.minecraftforge.fml.ModList;

import java.nio.file.Path;

/**
 * Resource pack bawaan {@code liarsbar:noir} (selalu aktif, terkunci di paling atas, jadi di atas
 * {@code veil:deferred}): mengganti shader lampu Veil dengan versi noir. Lampu bawaan Veil menambah
 * ambient 0,4 dan mengoles warna lampu rata ke semua permukaan, hasilnya ruang gelap terlihat keruh.
 */
public final class NoirPack {

    public static final String ID = LiarsBar.MOD_ID + ":noir";

    private NoirPack() {
    }

    public static void onAddPackFinders(AddPackFindersEvent event) {
        if (event.getPackType() != PackType.CLIENT_RESOURCES) {
            return;
        }
        Path root = ModList.get().getModFileById(LiarsBar.MOD_ID).getFile().findResource("resourcepacks/noir");
        Pack.ResourcesSupplier resources = name -> new PathPackResources(name, root, true);
        Pack.Info info = Pack.readPackInfo(ID, resources);
        if (info == null) {
            return;
        }
        Pack pack = Pack.create(ID, Component.literal("Liar's Table noir"), true, resources, info,
                PackType.CLIENT_RESOURCES, Pack.Position.TOP, true, PackSource.BUILT_IN);
        event.addRepositorySource(consumer -> consumer.accept(pack));
    }
}
