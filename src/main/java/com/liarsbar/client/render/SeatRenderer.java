package com.liarsbar.client.render;

import com.liarsbar.LiarsBar;
import com.liarsbar.client.ClientTable;
import com.liarsbar.client.cinematic.RouletteDirector;
import com.liarsbar.client.cinematic.RouletteTimeline;
import com.liarsbar.entity.SeatEntity;
import com.liarsbar.entity.TableEntity;
import com.liarsbar.table.PublicState;
import com.liarsbar.table.SeatLayout;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.util.Mth;

import java.util.List;

/**
 * Kursi bar: empat kaki berpalang, jok kulit, sandaran dua tiang dengan dua
 * bilah. Ruang lokal: penunggang menghadap -Z, sandaran di +Z, kanan
 * penunggang +X. Kalau pemiliknya tewas jatuh ke samping, kursinya terguling
 * ke sisi lain (berporos di kaki kanan), memantul kecil, lalu diam; kalau
 * pemiliknya ambruk ke meja, kursinya terdorong sedikit ke belakang.
 */
public class SeatRenderer extends EntityRenderer<SeatEntity> {

    /** Jok kulit: wol cokelat vanilla digelapkan ke cokelat tua kemerahan. */
    private static final int LEATHER_TINT = 0xFFA06A58;

    private static net.minecraft.client.renderer.texture.TextureAtlasSprite sprite(String name) {
        return net.minecraft.client.Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS)
                .getSprite(new ResourceLocation("minecraft", "block/" + name));
    }

    private static final float SEAT_Y = 0.40f;
    private static final float HALF = 0.23f;
    private static final float LEG = 0.026f;

    public SeatRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(SeatEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    /** Kursi ke berapa entity ini di meja yang dilihat (-1: bukan meja yang dilihat). */
    private static int seatIndex(SeatEntity entity) {
        PublicState state = ClientTable.state();
        TableEntity table = RouletteDirector.tableEntity();
        if (state == null || table == null || entity.tableId() != state.tableId) {
            return -1;
        }
        List<SeatLayout.Seat> seats = SeatLayout.seats(state.seats.size());
        double best = 0.25;
        int index = -1;
        for (int i = 0; i < seats.size(); i++) {
            double dx = entity.getX() - (table.getX() + seats.get(i).dx());
            double dz = entity.getZ() - (table.getZ() + seats.get(i).dz());
            double d = dx * dx + dz * dz;
            if (d < best) {
                best = d;
                index = i;
            }
        }
        return index;
    }

    /** {guling (derajat, + = terguling ke kanan penunggang), geser ke belakang (blok)}. */
    private static float[] knock(int seat, float partial) {
        if (seat < 0) {
            return new float[]{0, 0};
        }
        RouletteDirector.Act act = RouletteDirector.act(seat, partial);
        boolean side = "side".equals(RouletteDirector.variant(seat));
        if (act.phase() == RouletteDirector.Phase.DEAD) {
            PublicState state = ClientTable.state();
            boolean dead = state != null && seat < state.seats.size() && !state.seats.get(seat).alive;
            return dead ? (side ? new float[]{88, 0.05f} : new float[]{0, 0.16f}) : new float[]{0, 0};
        }
        if (act.phase() != RouletteDirector.Phase.BANG) {
            return new float[]{0, 0};
        }
        float t = act.tick();
        int fall = RouletteTimeline.deathFall("death_" + RouletteDirector.variant(seat));
        if (side) {
            // Mulai miring saat badan tergelincir, jatuh makin cepat, memantul kecil di lantai.
            float u = Mth.clamp((t - (fall - 14)) / 16f, 0, 1);
            float angle = 88 * u * u;
            if (u >= 1) {
                float b = t - (fall + 2);
                angle = 88 - 9 * (float) (Math.exp(-b / 3.0) * Math.abs(Math.sin(b * 0.9)));
            }
            return new float[]{angle, 0.05f * u};
        }
        float u = Mth.clamp((t - 2) / 6f, 0, 1);
        return new float[]{0, 0.16f * (1 - (1 - u) * (1 - u))};
    }

    @Override
    public void render(SeatEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        if (!com.liarsbar.client.test.DevToggles.on("chairs")) {
            return;
        }
        float[] k = knock(seatIndex(entity), partialTick);
        pose.pushPose();
        // Sumbu -Z lokal = arah hadap penunggang.
        pose.mulPose(Axis.YP.rotationDegrees(180 - entity.getYRot()));
        pose.translate(0, 0, k[1]);
        if (k[0] != 0) {
            pose.translate(HALF, 0, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(-k[0]));
            pose.translate(-HALF, 0, 0);
        }
        VertexConsumer wood = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
        int c = 0xFFFFFFFF;
        float s = 1.0f;
        // Kaki, sedikit melebar ke bawah (ditaruh tegak, cukup tipis supaya tidak terlihat kaku).
        for (float x : new float[]{-HALF + LEG, HALF - LEG}) {
            for (float z : new float[]{-HALF + LEG, HALF - LEG}) {
                Geo.tiledBox(wood, pose, sprite("dark_oak_planks"), light, c, x - LEG, 0, z - LEG, x + LEG, SEAT_Y - 0.04f, z + LEG, 1f);
            }
        }
        // Palang di antara kaki.
        Geo.tiledBox(wood, pose, sprite("dark_oak_planks"), light, c, -HALF + LEG, 0.12f, -HALF + LEG * 0.5f, HALF - LEG, 0.15f, -HALF + LEG * 1.5f, 1f);
        Geo.tiledBox(wood, pose, sprite("dark_oak_planks"), light, c, -HALF + LEG, 0.12f, HALF - LEG * 1.5f, HALF - LEG, 0.15f, HALF - LEG * 0.5f, 1f);
        Geo.tiledBox(wood, pose, sprite("dark_oak_planks"), light, c, -HALF + LEG * 0.5f, 0.2f, -HALF + LEG, -HALF + LEG * 1.5f, 0.23f, HALF - LEG, 1f);
        Geo.tiledBox(wood, pose, sprite("dark_oak_planks"), light, c, HALF - LEG * 1.5f, 0.2f, -HALF + LEG, HALF - LEG * 0.5f, 0.23f, HALF - LEG, 1f);
        // Rangka jok.
        Geo.tiledBox(wood, pose, sprite("dark_oak_planks"), light, c, -HALF, SEAT_Y - 0.06f, -HALF, HALF, SEAT_Y - 0.02f, HALF, 1f);
        // Sandaran: dua tiang melengkung sedikit ke belakang + dua bilah.
        pose.pushPose();
        pose.translate(0, SEAT_Y - 0.02f, HALF - LEG);
        pose.mulPose(Axis.XP.rotationDegrees(-8));
        for (float x : new float[]{-HALF + LEG, HALF - LEG}) {
            Geo.tiledBox(wood, pose, sprite("dark_oak_planks"), light, c, x - LEG, 0, -LEG, x + LEG, 0.62f, LEG, 1f);
        }
        Geo.tiledBox(wood, pose, sprite("dark_oak_planks"), light, c, -HALF, 0.52f, -LEG * 1.2f, HALF, 0.64f, LEG * 1.2f, 1f);
        Geo.tiledBox(wood, pose, sprite("dark_oak_planks"), light, c, -HALF + LEG, 0.3f, -LEG * 0.7f, HALF - LEG, 0.36f, LEG * 0.7f, 1f);
        pose.popPose();
        // Jok kulit empuk di atas rangka.
        VertexConsumer leather = buffers.getBuffer(RenderType.entityCutout(InventoryMenu.BLOCK_ATLAS));
        Geo.tiledBox(leather, pose, sprite("brown_wool"), light, LEATHER_TINT, -HALF + 0.015f, SEAT_Y - 0.02f, -HALF + 0.015f, HALF - 0.015f, SEAT_Y + 0.035f,
                HALF - 0.015f, 1.0f / (HALF * 2));
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }
}
