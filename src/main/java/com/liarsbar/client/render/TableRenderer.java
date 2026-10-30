package com.liarsbar.client.render;

import com.liarsbar.LiarsBar;
import com.liarsbar.client.ClientTable;
import com.liarsbar.client.cinematic.RouletteDirector;
import com.liarsbar.client.fx.TableLights;
import com.liarsbar.item.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.Mth;
import com.liarsbar.client.ui.TableHud;
import com.liarsbar.entity.TableEntity;
import com.liarsbar.game.deck.Card;
import com.liarsbar.table.PublicState;
import com.liarsbar.table.SeatLayout;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import java.util.List;

/**
 * Meja bundar noir (Art Bible): felt hijau tua, bibir walnut, lampu gantung per kursi
 * dengan kap enamel. Giliran ditandai lampu kursi (TableLights), bukan penanda di meja.
 * Di daun meja: tumpukan kartu tertutup dan kartu yang dibuka.
 */
public class TableRenderer extends EntityRenderer<TableEntity> {

    private static final ResourceLocation ENAMEL = new ResourceLocation("minecraft", "textures/block/green_concrete.png");
    private static final ResourceLocation WHITE = new ResourceLocation("minecraft", "textures/block/white_concrete.png");
    /** Felt #1E3B33 dari wol cyan vanilla, walnut #3A2416 dari dark oak kupas, enamel kap hijau tua. */
    private static final int FELT_TINT = 0xFFFF6E59;
    private static final int WALNUT_TINT = 0xFFCCA399;
    private static final int ENAMEL_TINT = 0xFF5C6BD4;
    private static ItemStack gunStack;
    private static final int SEGMENTS = 48;

    public TableRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    private static ResourceLocation tex(String path) {
        return new ResourceLocation(LiarsBar.MOD_ID, path);
    }

    @Override
    public ResourceLocation getTextureLocation(TableEntity entity) {
        return InventoryMenu.BLOCK_ATLAS;
    }

    @Override
    public boolean shouldRender(TableEntity entity, Frustum frustum, double x, double y, double z) {
        // Lampu menggantung tinggi di atas meja: jangan dibuang frustum hanya karena daun mejanya di luar layar.
        return true;
    }

    @Override
    public void render(TableEntity entity, float yaw, float partialTick, PoseStack pose, MultiBufferSource buffers, int light) {
        buffers = EmissivePass.wrap(buffers);
        float r = entity.radius();
        float top = SeatLayout.TABLE_HEIGHT;
        PublicState state = ClientTable.state();
        boolean mine = state != null && state.tableId == entity.tableId();
        table(pose, buffers, r, top, light);
        if (mine) {
            TableProps.render(pose, buffers, entity.getPosition(partialTick), r, top, light, state, partialTick);
            TableCards.render(pose, buffers, entity.getPosition(partialTick), top, light, state, partialTick);
            revealed(pose, buffers, r, top, light, state);
            if (com.liarsbar.client.test.DevToggles.on("tableguns")) {
                tableGuns(entity, pose, buffers, r, top, light, state, partialTick);
            }
            if (com.liarsbar.client.test.DevToggles.on("blood")) {
                blood(pose, buffers, r, top, light, state, partialTick);
            }
            if (com.liarsbar.client.test.DevToggles.on("lamps")) {
                lamps(entity, pose, buffers, light, state, partialTick);
            }
            if (BarRoom.active() && com.liarsbar.client.test.DevToggles.on("haze")) {
                com.liarsbar.client.fx.Atmosphere.render(entity, pose, buffers, state, partialTick);
            }
        }
        super.render(entity, yaw, partialTick, pose, buffers, light);
    }

    /** Lebar bibir walnut di tepi daun meja; felt di dalamnya. */
    public static final float RIM = 0.17f;
    /** Sel felt (2 texel): cukup kecil supaya celah di tepi lingkaran tertutup bibir walnut. */
    private static final float CELL = 0.125f;

    private static TextureAtlasSprite sprite(String name) {
        return Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS)
                .getSprite(new ResourceLocation("minecraft", "block/" + name));
    }

    /**
     * Meja: daun felt hijau tua (anyaman wol vanilla), bibir walnut yang sedikit menonjol, apron dan kaki
     * tengah kayu gelap. Semua dari sprite atlas blok (16 texel per blok, sama dengan dunia).
     */
    private static void table(PoseStack pose, MultiBufferSource buffers, float r, float top, int light) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float inner = r - RIM, lip = top + 0.022f, under = top - 0.12f;
        TextureAtlasSprite felt = sprite("cyan_wool"), wood = sprite("stripped_dark_oak_log");
        VertexConsumer vc = buffers.getBuffer(RenderType.entitySolid(InventoryMenu.BLOCK_ATLAS));
        // Felt: sel 2 texel yang seluruh sudutnya di dalam lingkaran luar (tepinya tertutup bibir).
        int cells = (int) Math.ceil(r / CELL);
        for (int i = -cells; i < cells; i++) {
            for (int j = -cells; j < cells; j++) {
                float x0 = i * CELL, z0 = j * CELL, x1 = x0 + CELL, z1 = z0 + CELL;
                float far = Math.max(Math.max(x0 * x0, x1 * x1) + Math.max(z0 * z0, z1 * z1), 0);
                if (far > (r - 0.02f) * (r - 0.02f)) {
                    continue;
                }
                float su = Math.floorMod(i, 8) * 2, sv = Math.floorMod(j, 8) * 2;
                float u0 = felt.getU(su), u1 = felt.getU(su + 2), v0 = felt.getV(sv), v1 = felt.getV(sv + 2);
                quad(vc, m, n, 0, 1, 0, light, FELT_TINT,
                        x0, top, z1, u0, v1, x1, top, z1, u1, v1, x1, top, z0, u1, v0, x0, top, z0, u0, v0);
            }
        }
        // Bibir walnut, apron, bawah, kaki: per segmen busur, UV sepanjang keliling (dipecah di batas tekstur).
        for (int k = 0; k < SEGMENTS; k++) {
            double a0 = Math.PI * 2 * k / SEGMENTS, a1 = Math.PI * 2 * (k + 1) / SEGMENTS;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0), c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float cm = (c0 + c1) / 2, sm = (s0 + s1) / 2;
            float arc = (float) (Math.PI * 2 * r / SEGMENTS);
            float start = (k * arc) % 1f;
            float ua = wood.getU(start * 16), ub = wood.getU(Math.min(1f, start + arc) * 16);
            float rimV = wood.getV(RIM * 16), lipV = wood.getV((lip - under) * 16);
            quad(vc, m, n, 0, 1, 0, light, WALNUT_TINT,
                    c0 * inner, lip, s0 * inner, ua, wood.getV0(),
                    c1 * inner, lip, s1 * inner, ub, wood.getV0(),
                    c1 * r, lip, s1 * r, ub, rimV,
                    c0 * r, lip, s0 * r, ua, rimV);
            quad(vc, m, n, -cm, 0, -sm, light, WALNUT_TINT,
                    c0 * inner, lip, s0 * inner, ua, wood.getV0(),
                    c0 * inner, top, s0 * inner, ua, wood.getV(0.35f),
                    c1 * inner, top, s1 * inner, ub, wood.getV(0.35f),
                    c1 * inner, lip, s1 * inner, ub, wood.getV0());
            quad(vc, m, n, cm, 0, sm, light, WALNUT_TINT,
                    c1 * r, lip, s1 * r, ub, wood.getV0(),
                    c1 * r, under, s1 * r, ub, lipV,
                    c0 * r, under, s0 * r, ua, lipV,
                    c0 * r, lip, s0 * r, ua, wood.getV0());
            float lr = 0.22f;
            quad(vc, m, n, cm, 0, sm, light, 0xFFB0A090,
                    c1 * lr, under, s1 * lr, ub, wood.getV0(),
                    c1 * lr, 0, s1 * lr, ub, wood.getV1(),
                    c0 * lr, 0, s0 * lr, ua, wood.getV1(),
                    c0 * lr, under, s0 * lr, ua, wood.getV0());
            // Bawah daun meja: kipas dari pusat (warna kayu gelap, tekstur cukup satu sel).
            quad(vc, m, n, 0, -1, 0, light, 0xFF6A5A50,
                    0, under, 0, ua, wood.getV0(),
                    c0 * r, under, s0 * r, ua, rimV,
                    c1 * r, under, s1 * r, ub, rimV,
                    0, under, 0, ub, wood.getV0());
        }
    }

    /**
     * Kartu yang dibuka (Art Bible): berjajar tertutup, dibalik satu per satu. Kartu jujur (table card atau
     * Joker) mendapat pendar emas di bawahnya; kartu bohong menghitam dan terbakar dengan pendar merah.
     */
    private static void revealed(PoseStack pose, MultiBufferSource buffers, float r, float top, int light, PublicState state) {
        boolean show = state.stage == PublicState.Stage.REVEAL || state.stage == PublicState.Stage.ROULETTE
                || state.stage == PublicState.Stage.SHOT;
        if (!show || state.revealed.isEmpty() || state.accused < 0) {
            return;
        }
        float partial = Minecraft.getInstance().getFrameTime();
        if (state.seats.isEmpty()) {
            return;
        }
        SeatLayout.Seat seat = SeatLayout.seats(state.seats.size()).get(state.accused);
        float len = (float) Math.sqrt(seat.dx() * seat.dx() + seat.dz() * seat.dz());
        float dx = (float) seat.dx() / len, dz = (float) seat.dz() / len;
        List<Card> cards = state.revealed;
        List<com.liarsbar.client.cinematic.ThrowDirector.Lying> from =
                com.liarsbar.client.cinematic.ThrowDirector.revealedCards();
        float since = com.liarsbar.client.cinematic.LiarDirector.sinceReveal(partial);
        for (int i = 0; i < cards.size(); i++) {
            Card card = cards.get(i);
            float age = com.liarsbar.client.cinematic.LiarDirector.cardAge(i, partial);
            boolean truthful = card.rank().truthfulOn(state.tableRank);
            float offset = (i - (cards.size() - 1) / 2f) * 0.3f;
            pose.pushPose();
            float ring = com.liarsbar.client.cinematic.LiarDirector.REVEAL_RING;
            float x = dx * r * ring - dz * offset, z = dz * r * ring + dx * offset, yaw = -seat.yaw();
            // Sebelum dibalik, kartu digeser dari tempat jatuhnya di tumpukan ke barisnya (sambil membesar).
            float slide = since < 0 ? 1 : Mth.clamp((since - i * 1.5f) / 7f, 0, 1);
            slide = slide * slide * (3 - 2 * slide);
            float scale = 1;
            if (slide < 1 && i < from.size()) {
                var l = from.get(i);
                x = Mth.lerp(slide, l.x, x);
                z = Mth.lerp(slide, l.z, z);
                yaw = Mth.rotLerp(slide, l.yaw, yaw);
                scale = Mth.lerp(slide, 1 / com.liarsbar.client.cinematic.DealDirector.REVEAL_SCALE, 1);
            }
            pose.translate(x, top + 0.006f + i * 0.0005f + Mth.sin(slide * Mth.PI) * 0.03f, z);
            // Menghadap ke tengah meja (terbaca oleh pemain lain dan kamera reveal), bukan ke yang dituduh.
            pose.mulPose(Axis.YP.rotationDegrees(yaw));
            pose.scale(scale, 1, scale);
            // Dibalik sepanjang sumbu panjang kartu: terangkat sedikit di tengah putaran.
            float flip = Mth.clamp(age / 4f, 0, 1);
            flip = flip * flip * (3 - 2 * flip);
            pose.translate(0, Mth.sin(flip * Mth.PI) * 0.06f, 0);
            pose.mulPose(Axis.ZP.rotationDegrees(180 * (1 - flip)));
            boolean faceUp = flip >= 0.5f;
            int tint = 0xFFFFFFFF;
            if (faceUp && !truthful) {
                // Terbakar: menghitam dari ujung ke ujung dalam ~1,5 dtk.
                float burn = Mth.clamp((age - 4) / 30f, 0, 1);
                tint = lerpColor(0xFFFFFFFF, 0xFF2A140C, burn);
            }
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TableHud.cardTexture(faceUp ? card.rank() : null)));
            card(vc, pose, light, tint);
            if (faceUp) {
                float k = Mth.clamp((age - 3) / 6f, 0, 1);
                float pulse = 0.75f + 0.25f * Mth.sin((age) * 0.35f);
                int glow = truthful ? scaleColor(0xFFC9A04A, k * pulse * 0.32f) : scaleColor(0xFFD6202A, k * pulse * 0.45f
                        * (1 - Mth.clamp((age - 34) / 20f, 0, 1)));
                VertexConsumer g = buffers.getBuffer(RenderType.eyes(com.liarsbar.client.render.FxTextures.DOT));
                com.liarsbar.client.render.FxTextures.ensure();
                pose.pushPose();
                pose.translate(0, -0.003f, 0);
                pose.scale(2.4f, 1, 2.0f);
                card(g, pose, LightTexture.FULL_BRIGHT, glow & 0x00FFFFFF);
                pose.popPose();
            }
            pose.popPose();
        }
    }

    private static int scaleColor(int argb, float k) {
        k = Mth.clamp(k, 0, 1);
        int r = Math.round(((argb >> 16) & 0xFF) * k), g = Math.round(((argb >> 8) & 0xFF) * k), b = Math.round((argb & 0xFF) * k);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    /** Revolver tiap kursi yang tidak sedang dipegang: tergeletak di meja, di depan kanan pemiliknya. */
    private static void tableGuns(TableEntity entity, PoseStack pose, MultiBufferSource buffers, float r, float top,
                                  int light, PublicState state, float partial) {
        if (gunStack == null) {
            gunStack = new ItemStack(ModItems.REVOLVER.get());
        }
        Minecraft mc = Minecraft.getInstance();
        List<SeatLayout.Seat> seats = SeatLayout.seats(state.seats.size());
        for (int i = 0; i < seats.size(); i++) {
            if (!state.seats.get(i).alive) {
                continue;
            }
            if (RouletteDirector.bulletOnTable(i, partial)) {
                // Satu peluru berdiri di samping revolver yang belum pernah diisi.
                Vec3 bullet = RouletteDirector.tableBulletPos(i);
                if (bullet != null) {
                    BulletRenderer.render(pose, buffers, light, entity.getPosition(partial), bullet, new Vec3(0, 1, 0),
                            seats.get(i).yaw() * Mth.DEG_TO_RAD);
                }
            }
            RouletteDirector.Gun gun = RouletteDirector.gun(i, partial);
            if (!gun.onTable()) {
                continue;
            }
            // Sama persis dengan awal jalur genggam RouletteDirector, supaya tidak meloncat saat diambil.
            Vec3 at = RouletteDirector.tableGunPos(i);
            RouletteDirector.Frame f = RouletteDirector.frame(i);
            if (at == null || f == null) {
                continue;
            }
            pose.pushPose();
            RevolverPose.set(gun.pose().placed(RouletteDirector.lying(f), at), null);
            try {
                mc.getItemRenderer().renderStatic(gunStack, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY,
                        pose, buffers, entity.level(), 0);
            } finally {
                RevolverPose.clear();
            }
            pose.popPose();
        }
    }

    /** Satu lampu gantung di atas pusat meja: kabel ke langit-langit, kap kerucut lebar, bohlam yang berkedip saat ada yang tewas. */
    private static void lamps(TableEntity entity, PoseStack pose, MultiBufferSource buffers, int light,
                              PublicState state, float partial) {
        Vec3 origin = entity.position();
        Vec3 rest = TableLights.lampPos(origin).subtract(origin);
        float[] sw = TableLights.swing(partial);
        // Bingkai pendulum: titik asal di gantungan, diayun, lalu lampu digambar di ujung tali.
        pose.pushPose();
        pose.translate(rest.x, rest.y + TableLights.CORD, rest.z);
        pose.mulPose(Axis.ZP.rotation(sw[0]));
        pose.mulPose(Axis.XP.rotation(-sw[1]));
        pose.translate(0, -TableLights.CORD, 0);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float x = 0, y = 0, z = 0;
        float c = 0.014f;
        float r0 = 0.09f, r1 = TableLights.SHADE_RADIUS, y0 = y + 0.24f, y1 = y - 0.03f;
        float topY = y + TableLights.CORD + y0;
        VertexConsumer cord = buffers.getBuffer(RenderType.entitySolid(WHITE));
        quad(cord, m, n, 0, 0, 1, light, 0xFF141414, x - c, topY, z, 0, 0, x + c, topY, z, 1, 0,
                x + c, y0, z, 1, 1, x - c, y0, z, 0, 1);
        quad(cord, m, n, 1, 0, 0, light, 0xFF141414, x, topY, z - c, 0, 0, x, topY, z + c, 1, 0,
                x, y0, z + c, 1, 1, x, y0, z - c, 0, 1);
        float glowLevel = TableLights.brightness(state, partial);
        int seg = 24;
        // Bagian dalam kap memantulkan bohlam: hangat, terang mengikuti lampu.
        int warm = lerpColor(0xFF000000, 0xFFE8B070, Mth.clamp(glowLevel, 0, 1));
        for (int k = 0; k < seg; k++) {
            double a0 = Math.PI * 2 * k / seg, a1 = Math.PI * 2 * (k + 1) / seg;
            float c0 = (float) Math.cos(a0), s0 = (float) Math.sin(a0);
            float c1 = (float) Math.cos(a1), s1 = (float) Math.sin(a1);
            float u0 = k / (float) seg, u1 = (k + 1) / (float) seg;
            quad(buffers.getBuffer(RenderType.entitySolid(ENAMEL)), m, n, (c0 + c1) / 2, 0.7f, (s0 + s1) / 2, light, ENAMEL_TINT,
                    x + c0 * r0, y0, z + s0 * r0, u0, 0, x + c1 * r0, y0, z + s1 * r0, u1, 0,
                    x + c1 * r1, y1, z + s1 * r1, u1, 0.3f, x + c0 * r1, y1, z + s0 * r1, u0, 0.3f);
            quad(buffers.getBuffer(RenderType.eyes(WHITE)), m, n, -(c0 + c1) / 2, -0.7f, -(s0 + s1) / 2, LightTexture.FULL_BRIGHT, warm,
                    x + c0 * r1 * 0.97f, y1 + 0.003f, z + s0 * r1 * 0.97f, u0, 0.3f,
                    x + c1 * r1 * 0.97f, y1 + 0.003f, z + s1 * r1 * 0.97f, u1, 0.3f,
                    x + c1 * r0 * 0.97f, y0 - 0.003f, z + s1 * r0 * 0.97f, u1, 0,
                    x + c0 * r0 * 0.97f, y0 - 0.003f, z + s0 * r0 * 0.97f, u0, 0);
        }
        // Tutup atas kap dan fiting.
        box(buffers.getBuffer(RenderType.entitySolid(ENAMEL)), m, n, light, ENAMEL_TINT, x - r0, y0, z - r0, x + r0,
                y0 + 0.025f, z + r0);
        box(buffers.getBuffer(RenderType.entitySolid(WHITE)), m, n, light, 0xFF8A7050, x - 0.035f, y0 - 0.07f, z - 0.035f, x + 0.035f, y0, z + 0.035f);
        boolean lit = glowLevel > 0.05f;
        VertexConsumer bulb = buffers.getBuffer(lit ? RenderType.eyes(WHITE) : RenderType.entitySolid(WHITE));
        int glow = lit ? lerpColor(0xFF4A3420, 0xFFFFE2B0, Mth.clamp(glowLevel * 1.2f, 0, 1)) : 0xFF3A3632;
        box(bulb, m, n, lit ? LightTexture.FULL_BRIGHT : light, glow,
                x - 0.055f, y - 0.02f, z - 0.055f, x + 0.055f, y + 0.12f, z + 0.055f);
        pose.popPose();
    }

    private static final ResourceLocation SPLAT = tex("textures/fx/blood_splat.png");
    private static final ResourceLocation SPLAT2 = tex("textures/fx/blood_splat2.png");
    private static final ResourceLocation POOL = tex("textures/fx/blood_pool.png");

    /**
     * Darah kursi yang sudah tewas: cipratan di daun meja di sisi kiri kepala (peluru masuk dari pelipis
     * kanan), dan genangan yang melebar pelan di bawah kepala (lantai kalau jatuh dari kursi, meja kalau
     * ambruk ke meja). Hilang saat meja kembali ke lobby.
     */
    private static void blood(PoseStack pose, MultiBufferSource buffers, float r, float top, int light,
                              PublicState state, float partial) {
        if (state.stage == PublicState.Stage.LOBBY) {
            return;
        }
        List<SeatLayout.Seat> seats = SeatLayout.seats(state.seats.size());
        float now = ClientTable.now() + partial;
        for (int i = 0; i < seats.size(); i++) {
            long died = RouletteDirector.diedAt(i);
            if (state.seats.get(i).alive || died == Long.MIN_VALUE) {
                continue;
            }
            float age = now - died;
            SeatLayout.Seat seat = seats.get(i);
            float len = (float) Math.sqrt(seat.dx() * seat.dx() + seat.dz() * seat.dz());
            float ux = (float) seat.dx() / len, uz = (float) seat.dz() / len;
            float lx = -uz, lz = ux;
            float angle = (float) Math.toDegrees(Math.atan2(lz, lx));
            boolean side = "side".equals(RouletteDirector.variant(i));
            int fall = com.liarsbar.client.cinematic.RouletteTimeline.deathFall("death_" + RouletteDirector.variant(i));
            if (!side && age >= fall) {
                // Cipratan di meja hanya untuk yang ambruk ke meja, muncul tepat saat kepalanya menghantam
                // daun meja (bukan saat DOR). Yang jatuh ke lantai hanya meninggalkan genangan di lantai.
                float splat = Mth.clamp((age - fall) / 2f, 0, 1);
                decal(pose, buffers.getBuffer(RenderType.entityTranslucent(i % 2 == 0 ? SPLAT : SPLAT2)), light,
                        ux * (r - 0.38f) + lx * 0.42f, top + 0.004f + i * 0.0002f, uz * (r - 0.38f) + lz * 0.42f,
                        angle, 0.75f * (0.6f + 0.4f * splat), splat);
            }
            float grow = Mth.clamp((age - fall) / 140f, 0, 1);
            if (grow <= 0) {
                continue;
            }
            float size = 0.18f + 0.85f * (1 - (1 - grow) * (1 - grow) * (1 - grow));
            if (side) {
                float ring = r + (float) SeatLayout.SEAT_GAP;
                decal(pose, buffers.getBuffer(RenderType.entityTranslucent(POOL)), light,
                        ux * (ring + 0.1f) + lx * 0.95f, 0.006f + i * 0.0002f, uz * (ring + 0.1f) + lz * 0.95f,
                        angle + i * 37, size, 1);
            } else {
                decal(pose, buffers.getBuffer(RenderType.entityTranslucent(POOL)), light,
                        ux * (r - 0.3f) + lx * 0.12f, top + 0.005f + i * 0.0002f, uz * (r - 0.3f) + lz * 0.12f,
                        angle + i * 37, size * 0.55f, 1);
            }
        }
    }

    private static void decal(PoseStack pose, VertexConsumer vc, int light, float x, float y, float z, float angle,
                              float size, float alpha) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.mulPose(Axis.YP.rotationDegrees(-angle));
        int a = Math.round(Mth.clamp(alpha, 0, 1) * 255);
        Geo.flat(vc, pose, light, (a << 24) | 0xFFFFFF, -size / 2, -size / 2, size / 2, size / 2, 0);
        pose.popPose();
    }

    private static int lerpColor(int a, int b, float w) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000 | ((int) (ar + (br - ar) * w) << 16) | ((int) (ag + (bg - ag) * w) << 8)
                | (int) (ab + (bb - ab) * w);
    }

    private static void box(VertexConsumer vc, Matrix4f m, Matrix3f n, int light, int color,
                            float x0, float y0, float z0, float x1, float y1, float z1) {
        quad(vc, m, n, 0, -1, 0, light, color, x0, y0, z0, 0, 0, x1, y0, z0, 1, 0, x1, y0, z1, 1, 1, x0, y0, z1, 0, 1);
        quad(vc, m, n, 0, 1, 0, light, color, x0, y1, z1, 0, 0, x1, y1, z1, 1, 0, x1, y1, z0, 1, 1, x0, y1, z0, 0, 1);
        quad(vc, m, n, 0, 0, -1, light, color, x0, y1, z0, 0, 0, x1, y1, z0, 1, 0, x1, y0, z0, 1, 1, x0, y0, z0, 0, 1);
        quad(vc, m, n, 0, 0, 1, light, color, x1, y1, z1, 0, 0, x0, y1, z1, 1, 0, x0, y0, z1, 1, 1, x1, y0, z1, 0, 1);
        quad(vc, m, n, -1, 0, 0, light, color, x0, y1, z1, 0, 0, x0, y1, z0, 1, 0, x0, y0, z0, 1, 1, x0, y0, z1, 0, 1);
        quad(vc, m, n, 1, 0, 0, light, color, x1, y1, z0, 0, 0, x1, y1, z1, 1, 0, x1, y0, z1, 1, 1, x1, y0, z0, 0, 1);
    }

    /** Satu kartu datar 0,22 x 0,33 blok, menghadap ke atas, di titik asal pose. */
    private static void card(VertexConsumer vc, PoseStack pose, int light, int color) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float w = 0.11f, h = 0.165f;
        quad(vc, m, n, 0, 1, 0, light, color,
                -w, 0, -h, 0, 0,
                -w, 0, h, 0, 1,
                w, 0, h, 1, 1,
                w, 0, -h, 1, 0);
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, float nx, float ny, float nz, int light,
                             int color,
                             float x0, float y0, float z0, float u0, float v0,
                             float x1, float y1, float z1, float u1, float v1,
                             float x2, float y2, float z2, float u2, float v2,
                             float x3, float y3, float z3, float u3, float v3) {
        vertex(vc, m, n, nx, ny, nz, light, color, x0, y0, z0, u0, v0);
        vertex(vc, m, n, nx, ny, nz, light, color, x1, y1, z1, u1, v1);
        vertex(vc, m, n, nx, ny, nz, light, color, x2, y2, z2, u2, v2);
        vertex(vc, m, n, nx, ny, nz, light, color, x3, y3, z3, u3, v3);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float nx, float ny, float nz, int light,
                               int color, float x, float y, float z, float u, float v) {
        vc.vertex(m, x, y, z)
                .color((color >> 16) & 0xFF, (color >> 8) & 0xFF, color & 0xFF, (color >>> 24) & 0xFF)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(light)
                .normal(n, nx, ny, nz)
                .endVertex();
    }
}
