package com.liarsbar.client.render;

import com.liarsbar.client.ClientTable;
import com.liarsbar.client.cinematic.RouletteDirector;
import com.liarsbar.client.ui.TableHud;
import com.liarsbar.game.deck.Rank;
import com.liarsbar.table.PublicState;
import com.liarsbar.table.SeatLayout;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

/**
 * HUD diegetik di atas meja (Art Bible): stand kuningan rendah 3 sisi berisi table card, jam pasir yang menghitung giliran (dibalik
 * tiap giliran), dan goresan kapur di bibir meja depan tiap kursi (satu per tarikan pelatuk).
 * Digambar dalam pass entity meja, jadi diterangi lampu Veil. Koordinat lokal meja (pusat, y = lantai).
 */
public final class TableProps {

    private static final float CARD_W = 0.11f, CARD_H = 0.165f;
    private static final int BRASS = 0xFFD2A866;
    private static final int CHALK = 0xFFE8E4D8;

    private static int lastTurnSeat = -2;
    private static long flipAt = Long.MIN_VALUE;

    private TableProps() {
    }

    private static TextureAtlasSprite block(String name) {
        return Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS)
                .getSprite(new ResourceLocation("minecraft", "block/" + name));
    }

    public static void render(PoseStack pose, MultiBufferSource buffers, Vec3 tablePos, float r, float top, int light,
                              PublicState state, float partial) {
        if (state.stage == PublicState.Stage.LOBBY) {
            return;
        }
        stand(pose, buffers, top, light, state.tableRank);
        hourglass(pose, buffers, top, light, state, partial);
        chalk(pose, buffers, r, top, light, state);
    }

    /** Kartu berdiri di bidang XY (pangkal bawah-tengah di titik asal), muka ke +z, punggung ke -z bila diminta. */
    private static void card(VertexConsumer vc, PoseStack pose, int light, int color, float w, float h, boolean both) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float hw = w / 2;
        Geo.vertex(vc, m, n, 0, 0, 1, light, color, -hw, h, 0, 0, 0);
        Geo.vertex(vc, m, n, 0, 0, 1, light, color, -hw, 0, 0, 0, 1);
        Geo.vertex(vc, m, n, 0, 0, 1, light, color, hw, 0, 0, 1, 1);
        Geo.vertex(vc, m, n, 0, 0, 1, light, color, hw, h, 0, 1, 0);
        if (both) {
            Geo.vertex(vc, m, n, 0, 0, -1, light, color, hw, h, -0.001f, 0, 0);
            Geo.vertex(vc, m, n, 0, 0, -1, light, color, hw, 0, -0.001f, 0, 1);
            Geo.vertex(vc, m, n, 0, 0, -1, light, color, -hw, 0, -0.001f, 1, 1);
            Geo.vertex(vc, m, n, 0, 0, -1, light, color, -hw, h, -0.001f, 1, 0);
        }
    }

    // ------------------------------------------------------------ table card

    /** Stand kuningan rendah di tengah meja: tiga kartu table card bersandar menghadap keluar (terbaca dari tiap kursi). */
    private static void stand(PoseStack pose, MultiBufferSource buffers, float top, int light, Rank rank) {
        if (rank == null) {
            return;
        }
        TextureAtlasSprite gold = block("gold_block");
        float s = 0.62f;
        Geo.tiledBox(buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS)), pose, gold, light, BRASS,
                -0.07f, top, -0.07f, 0.07f, top + 0.012f, 0.07f, 1f);
        Geo.tiledBox(buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS)), pose, gold, light, BRASS,
                -0.008f, top, -0.008f, 0.008f, top + 0.11f * s + 0.02f, 0.008f, 1f);
        for (int k = 0; k < 3; k++) {
            pose.pushPose();
            pose.translate(0, top + 0.012f, 0);
            pose.mulPose(Axis.YP.rotationDegrees(k * 120f));
            pose.translate(0, 0, 0.045f);
            pose.mulPose(Axis.XP.rotationDegrees(-18));
            card(buffers.getBuffer(RenderType.entityCutout(TableHud.cardTexture(rank))), pose, light, 0xFFFFFFFF,
                    CARD_W * s * 1.6f, CARD_H * s * 1.6f, false);
            pose.pushPose();
            pose.translate(0, 0, -0.0015f);
            pose.mulPose(Axis.YP.rotationDegrees(180));
            card(buffers.getBuffer(RenderType.entityCutout(TableHud.cardTexture(null))), pose, light, 0xFFFFFFFF,
                    CARD_W * s * 1.6f, CARD_H * s * 1.6f, false);
            pose.popPose();
            pose.popPose();
        }
    }

    // ------------------------------------------------------------ jam pasir

    /** Waktu sejak jam pasir terakhir dibalik (tick), dan isi bawah 0..1 (sisa giliran). */
    private static void hourglass(PoseStack pose, MultiBufferSource buffers, float top, int light, PublicState state,
                                  float partial) {
        int turnSeat = state.stage == PublicState.Stage.TURN ? state.current : -1;
        if (turnSeat != lastTurnSeat) {
            if (turnSeat >= 0) {
                flipAt = ClientTable.now();
            }
            lastTurnSeat = turnSeat;
        }
        float left = state.turnLength <= 0 || turnSeat < 0 ? 0 : Mth.clamp(ClientTable.ticksLeft() / (float) state.turnLength, 0, 1);
        float flip = Mth.clamp((ClientTable.now() + partial - flipAt) / 12f, 0, 1);
        flip = flip * flip * (3 - 2 * flip);
        pose.pushPose();
        pose.translate(0.42f, top, 0.12f);
        float h = 0.2f * 1.3f, rb = 0.055f * 1.3f;
        // Dibalik: berputar 180 derajat di sekitar tengahnya selama 12 tick awal giliran.
        pose.translate(0, h / 2, 0);
        pose.mulPose(Axis.XP.rotationDegrees(180 * (1 - flip) + (turnSeat < 0 ? 0 : 0)));
        pose.translate(0, -h / 2, 0);
        VertexConsumer atlas = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
        TextureAtlasSprite gold = block("gold_block"), wood = block("dark_oak_planks"), sand = block("sand");
        Geo.tiledBox(atlas, pose, wood, light, 0xFFFFFFFF, -rb, 0, -rb, rb, 0.016f, rb, 1f);
        Geo.tiledBox(atlas, pose, wood, light, 0xFFFFFFFF, -rb, h - 0.016f, -rb, rb, h, rb, 1f);
        for (int k = 0; k < 3; k++) {
            float a = k * Mth.TWO_PI / 3 + 0.5f, px = Mth.cos(a) * rb * 0.82f, pz = Mth.sin(a) * rb * 0.82f;
            Geo.tiledBox(atlas, pose, gold, light, BRASS, px - 0.005f, 0.016f, pz - 0.005f, px + 0.005f, h - 0.016f,
                    pz + 0.005f, 1f);
        }
        // Kaca bertingkat (lebar di ujung, menyempit ke leher) dan pasir di dalamnya: bawah = yang sudah jatuh.
        float neck = 0.007f, wide = rb * 0.66f, inner = h - 0.032f;
        float[] widths = {wide, wide * 0.86f, wide * 0.6f, wide * 0.32f, neck};
        int steps = widths.length;
        float stepH = (inner / 2) / steps;
        float below = 1 - left, above = left;
        for (int k = 0; k < steps; k++) {
            float w = widths[k];
            float yb = 0.016f + k * stepH, yt = h - 0.016f - (k + 1) * stepH;
            // Pasir bawah mengisi dari dasar; pasir atas mengisi dari leher ke atas.
            if (below > 0.01f && k < Math.ceil(below * steps)) {
                float fill = Math.min(1, below * steps - k);
                Geo.tiledBox(atlas, pose, sand, light, 0xFFFFE0B0, -w * 0.9f, yb, -w * 0.9f, w * 0.9f, yb + stepH * fill,
                        w * 0.9f, 1f);
            }
            if (above > 0.01f && steps - 1 - k < Math.ceil(above * steps)) {
                Geo.tiledBox(atlas, pose, sand, light, 0xFFFFE0B0, -w * 0.9f, yt, -w * 0.9f, w * 0.9f, yt + stepH, w * 0.9f, 1f);
            }
        }
        if (above > 0.01f && below < 0.99f) {
            Geo.tiledBox(atlas, pose, sand, light, 0xFFFFE0B0, -0.002f, 0.016f, -0.002f, 0.002f, h / 2, 0.002f, 1f);
        }
        TextureAtlasSprite glass = block("glass");
        VertexConsumer clear = buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        for (int k = 0; k < steps; k++) {
            float w = widths[k];
            float yb = 0.016f + k * stepH, yt = h - 0.016f - (k + 1) * stepH;
            Geo.tiledBox(clear, pose, glass, light, 0x50D8E8F0, -w, yb, -w, w, yb + stepH, w, 1f);
            Geo.tiledBox(clear, pose, glass, light, 0x50D8E8F0, -w, yt, -w, w, yt + stepH, w, 1f);
        }
        pose.popPose();
    }

    // ------------------------------------------------------------ kapur

    /** Goresan kapur di bibir walnut depan tiap kursi: satu per tarikan, yang kelima melintang (hitungan lima). */
    private static void chalk(PoseStack pose, MultiBufferSource buffers, float r, float top, int light, PublicState state) {
        List<SeatLayout.Seat> seats = SeatLayout.seats(state.seats.size());
        TextureAtlasSprite white = block("white_concrete");
        float y = top + 0.0235f;
        for (int i = 0; i < seats.size(); i++) {
            int pulls = state.seats.get(i).pulls;
            if (pulls <= 0) {
                continue;
            }
            SeatLayout.Seat seat = seats.get(i);
            float len = (float) Math.sqrt(seat.dx() * seat.dx() + seat.dz() * seat.dz());
            float ux = (float) seat.dx() / len, uz = (float) seat.dz() / len;
            pose.pushPose();
            // Lokal: +z = keluar ke kursi, x = sepanjang bibir. Pusat goresan di tengah bibir walnut.
            float mid = r - TableRenderer.RIM / 2;
            pose.translate(ux * mid, y, uz * mid);
            pose.mulPose(Axis.YP.rotation((float) Math.atan2(ux, uz)));
            Matrix4f m = pose.last().pose();
            Matrix3f n = pose.last().normal();
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
            for (int k = 0; k < Math.min(pulls, 6); k++) {
                boolean cross = k == 4;
                float x = cross ? -0.07f + 0.06f : -0.12f + k * 0.045f + (k > 4 ? 0.05f : 0);
                float angle = cross ? 1.05f : (k * 0.37f % 0.2f) - 0.1f;
                float hl = cross ? 0.1f : 0.06f, hw = 0.0085f;
                float ca = Mth.cos(angle), sa = Mth.sin(angle);
                // Garis kapur: persegi panjang tipis diputar di bidang bibir.
                float[][] c = {{-hw, -hl}, {hw, -hl}, {hw, hl}, {-hw, hl}};
                int[] order = {0, 3, 2, 1};
                for (int j : order) {
                    float px = x + c[j][0] * ca - c[j][1] * sa, pz = c[j][0] * sa + c[j][1] * ca;
                    Geo.vertex(vc, m, n, 0, 1, 0, light, CHALK, px, 0, pz, white.getU(j == 1 || j == 2 ? 2 : 0),
                            white.getV(j >= 2 ? 2 : 0));
                }
            }
            pose.popPose();
        }
    }
}
