package com.liarsbar.client.fx;

import com.liarsbar.LiarsBar;
import com.liarsbar.client.ClientTable;
import com.liarsbar.client.cinematic.RouletteDirector;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import foundry.veil.api.client.render.deferred.light.PointLight;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Api moncong dan asap laras. Posisi moncong tiap kursi dicatat saat revolvernya
 * digambar ({@link com.liarsbar.client.render.GunLayer}), diubah ke koordinat dunia
 * dengan matriks pandangan frame yang sama. Saat DOR: kilat (lampu Veil singkat +
 * sprite emissive), lalu kepulan asap yang naik pelan, melebar, dan memudar.
 */
public final class MuzzleFx {

    private static final ResourceLocation SMOKE = new ResourceLocation(LiarsBar.MOD_ID, "textures/fx/smoke.png");
    private static final ResourceLocation FLASH = new ResourceLocation(LiarsBar.MOD_ID, "textures/fx/muzzle_flash.png");

    private record Puff(Vec3 pos, Vec3 vel, long born, int life, float size, float spin) {
    }

    /** Revolver yang terlepas dari tangan penembak yang tewas: jatuh, memantul kecil, lalu diam. */
    private static final class Dropped {
        Vec3 pos;
        Vec3 prev;
        Vec3 vel;
        final Quaternionf rot;
        final Quaternionf rest;
        final Vector3f spinAxis;
        float spin;
        final double ground;
        boolean landed;
        int bounces;

        Dropped(Vec3 pos, Vec3 vel, Quaternionf start, Quaternionf rest, Vector3f spinAxis, float spin, double ground) {
            this.pos = pos;
            this.prev = pos;
            this.vel = vel;
            this.rot = new Quaternionf(start);
            this.rest = rest;
            this.spinAxis = spinAxis;
            this.spin = spin;
            this.ground = ground;
        }
    }

    private static final Map<Integer, Dropped> DROPPED = new HashMap<>();
    private static net.minecraft.world.item.ItemStack gunStack;

    private static final Map<Integer, Vec3> MUZZLES = new HashMap<>();
    /** Titik genggam revolver terakhir yang digambar di tangan, per kursi (awal jatuhnya saat DOR). */
    private static final Map<Integer, Vec3> GRIPS = new HashMap<>();
    private static final List<Puff> PUFFS = new ArrayList<>();
    private static final Matrix4f VIEW_INV = new Matrix4f();
    private static final Matrix3f VIEW_ROT = new Matrix3f();

    public static Vec3 cameraPos() {
        return cameraPos;
    }

    /** Putaran pandangan kamera frame ini (ruang dunia -> ruang render). */
    public static Matrix3f viewRotation() {
        return VIEW_ROT;
    }
    /** Kebalikan putaran pandangan frame ini (ruang render -> arah dunia). */
    public static Matrix4f viewInverse() {
        return VIEW_INV;
    }

    private static Vec3 cameraPos = Vec3.ZERO;
    private static long handledBang = Long.MIN_VALUE;
    private static Vec3 flashAt;
    private static long flashTick;
    private static PointLight flashLight;
    private static final RandomSource RANDOM = RandomSource.create();

    private MuzzleFx() {
    }

    /** Posisi moncong dalam ruang pose render (kamera di titik asal, sudah diputar pandangan). */
    public static void recordMuzzle(int seat, Vector3f posePoint) {
        Vector3f world = VIEW_INV.transformPosition(new Vector3f(posePoint));
        MUZZLES.put(seat, cameraPos.add(world.x, world.y, world.z));
    }

    public static void recordGrip(int seat, Vec3 at) {
        GRIPS.put(seat, at);
    }

    public static Vec3 muzzle(int seat) {
        return MUZZLES.get(seat);
    }

    public static void onRenderStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            VIEW_INV.set(event.getPoseStack().last().pose()).invert();
            event.getPoseStack().last().pose().get3x3(VIEW_ROT);
            cameraPos = event.getCamera().getPosition();
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            renderDropped(event.getPoseStack(), event.getCamera(), event.getPartialTick());
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            render(event.getPoseStack(), event.getCamera(), event.getPartialTick());
        }
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        long now = ClientTable.now();
        long bang = RouletteDirector.bangAt();
        if (bang != handledBang && bang != Long.MIN_VALUE && now - bang < 5) {
            handledBang = bang;
            dropGuns();
            Vec3 at = shooterMuzzle();
            if (at != null) {
                flashAt = at;
                flashTick = now;
                light(at);
                for (int i = 0; i < 7; i++) {
                    Vec3 vel = new Vec3((RANDOM.nextFloat() - 0.5) * 0.012, 0.006 + RANDOM.nextFloat() * 0.01,
                            (RANDOM.nextFloat() - 0.5) * 0.012);
                    PUFFS.add(new Puff(at, vel, now + i * 4L, 60 + RANDOM.nextInt(40), 0.06f + RANDOM.nextFloat() * 0.04f,
                            RANDOM.nextFloat() * 6.28f));
                }
            }
        }
        if (flashLight != null) {
            float age = now - flashTick;
            if (age > 4) {
                TableLights.removeLight(flashLight);
                flashLight = null;
            } else {
                flashLight.setBrightness(Math.max(0, 3.5f * (1 - age / 4f)));
            }
        }
        PUFFS.removeIf(p -> now - p.born > p.life);
        var state = ClientTable.state();
        if (state == null || state.stage == com.liarsbar.table.PublicState.Stage.LOBBY) {
            DROPPED.clear();
        }
        for (Dropped d : DROPPED.values()) {
            d.prev = d.pos;
            if (d.landed) {
                d.rot.slerp(d.rest, 0.35f);
                continue;
            }
            d.vel = d.vel.add(0, -0.04, 0).scale(0.98);
            d.pos = d.pos.add(d.vel);
            d.rot.premul(new Quaternionf().fromAxisAngleRad(d.spinAxis, d.spin));
            if (d.pos.y <= d.ground) {
                d.pos = new Vec3(d.pos.x, d.ground, d.pos.z);
                if (d.bounces++ < 1 && Math.abs(d.vel.y) > 0.12) {
                    d.vel = new Vec3(d.vel.x * 0.4, -d.vel.y * 0.25, d.vel.z * 0.4);
                    d.spin *= 0.4f;
                    play(d.pos, 0.7f);
                } else {
                    d.landed = true;
                    d.vel = Vec3.ZERO;
                    play(d.pos, 0.45f);
                }
            }
        }
        if (mc.level == null) {
            PUFFS.clear();
            MUZZLES.clear();
        }
    }

    /** Revolver penembak yang tewas terlepas: terlempar sedikit keluar dari tangan, berputar, jatuh. */
    private static void dropGuns() {
        var state = ClientTable.state();
        var table = RouletteDirector.tableEntity();
        if (state == null || table == null) {
            return;
        }
        for (int seat : state.shooters) {
            RouletteDirector.Frame f = RouletteDirector.frame(seat);
            if (f == null || !RouletteDirector.act(seat, 0).phase().equals(RouletteDirector.Phase.BANG)) {
                continue;
            }
            Vec3 start = GRIPS.getOrDefault(seat, f.eye().add(f.right().scale(0.32)).add(0, -0.02, 0));
            Vec3 vel = f.right().scale(0.07).add(0, 0.05, 0).add(f.forward().scale(0.01));
            Vec3 center = table.position();
            double r = table.radius();
            double flat = Math.hypot(start.x - center.x, start.z - center.z);
            double ground = center.y + (flat < r - 0.1 ? com.liarsbar.table.SeatLayout.TABLE_HEIGHT + 0.03 : 0.04);
            Quaternionf rest = new Quaternionf(RouletteDirector.lying(f))
                    .premul(new Quaternionf().fromAxisAngleDeg(0, 1, 0, 40 + RANDOM.nextFloat() * 80));
            DROPPED.put(seat, new Dropped(start, vel, RouletteDirector.temple(f), rest,
                    new Vector3f(RANDOM.nextFloat() - 0.5f, 0.3f, RANDOM.nextFloat() - 0.5f).normalize(),
                    0.25f + RANDOM.nextFloat() * 0.2f, ground));
        }
    }

    private static void play(Vec3 at, float volume) {
        Minecraft.getInstance().getSoundManager().play(new net.minecraft.client.resources.sounds.SimpleSoundInstance(
                com.liarsbar.sound.ModSounds.GUN_TABLE.get(), net.minecraft.sounds.SoundSource.PLAYERS, volume,
                0.8f + RANDOM.nextFloat() * 0.2f, RANDOM, at.x, at.y, at.z));
    }

    private static Vec3 shooterMuzzle() {
        var state = ClientTable.state();
        if (state == null) {
            return null;
        }
        for (int seat : state.shooters) {
            Vec3 at = MUZZLES.get(seat);
            if (at != null) {
                return at;
            }
            RouletteDirector.Frame f = RouletteDirector.frame(seat);
            if (f != null) {
                return f.eye().add(f.right().scale(0.12)).add(0, 0.02, 0);
            }
        }
        return null;
    }

    private static void light(Vec3 at) {
        if (flashLight != null) {
            TableLights.removeLight(flashLight);
        }
        flashLight = new PointLight();
        flashLight.setPosition(at.x, at.y, at.z).setColor(1.0f, 0.78f, 0.45f).setRadius(7.0f).setBrightness(3.5f);
        TableLights.addLight(flashLight);
    }

    private static void render(PoseStack pose, Camera camera, float partial) {
        if (PUFFS.isEmpty() && flashAt == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = camera.getPosition();
        Quaternionf facing = camera.rotation();
        float now = ClientTable.now() + partial;
        if (flashAt != null) {
            float age = now - flashTick;
            if (age < 3.0f) {
                VertexConsumer vc = buffers.getBuffer(RenderType.eyes(FLASH));
                float size = 0.42f * (1 - age / 3.6f);
                quad(pose, vc, flashAt.subtract(cam), facing, size, RANDOM.nextFloat() * 6.28f, 0xFFFFFFFF,
                        LightTexture.FULL_BRIGHT);
                buffers.endBatch(RenderType.eyes(FLASH));
            } else {
                flashAt = null;
            }
        }
        if (!PUFFS.isEmpty()) {
            VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(SMOKE));
            for (Puff p : PUFFS) {
                float age = now - p.born;
                if (age < 0) {
                    continue;
                }
                float u = age / p.life;
                Vec3 at = p.pos.add(p.vel.scale(age)).add(0, 0.0004 * age * age * 0.1, 0);
                float size = p.size * (1 + 3.5f * (float) Math.sqrt(u));
                int alpha = (int) (Mth.clamp(Math.min(age / 4f, 1) * (1 - u), 0, 1) * 150);
                int light = LightTexture.pack(10, 10);
                quad(pose, vc, at.subtract(cam), facing, size, p.spin + age * 0.01f,
                        (alpha << 24) | 0xC8C8C8, light);
            }
            buffers.endBatch(RenderType.entityTranslucent(SMOKE));
        }
    }

    private static void renderDropped(PoseStack pose, Camera camera, float partial) {
        if (DROPPED.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (gunStack == null) {
            gunStack = new net.minecraft.world.item.ItemStack(com.liarsbar.item.ModItems.REVOLVER.get());
        }
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        Vec3 cam = camera.getPosition();
        for (Dropped d : DROPPED.values()) {
            Vec3 at = d.prev.lerp(d.pos, partial);
            int light = net.minecraft.client.renderer.LevelRenderer.getLightColor(mc.level,
                    net.minecraft.core.BlockPos.containing(at.x, at.y + 0.1, at.z));
            pose.pushPose();
            com.liarsbar.client.render.RevolverPose.set(new com.liarsbar.client.render.RevolverPose(0, 0, 0, 0, -1, d.rot,
                    at.add(0, 0.02, 0)), null);
            try {
                mc.getItemRenderer().renderStatic(gunStack, net.minecraft.world.item.ItemDisplayContext.NONE, light,
                        OverlayTexture.NO_OVERLAY, pose, buffers, mc.level, 0);
            } finally {
                com.liarsbar.client.render.RevolverPose.clear();
            }
            pose.popPose();
        }
        buffers.endBatch();
    }

    private static void quad(PoseStack pose, VertexConsumer vc, Vec3 at, Quaternionf facing, float size, float spin,
                             int argb, int light) {
        pose.pushPose();
        pose.translate(at.x, at.y, at.z);
        pose.mulPose(facing);
        pose.mulPose(com.mojang.math.Axis.ZP.rotation(spin));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        int a = argb >>> 24, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        vc.vertex(m, -size, -size, 0).color(r, g, b, a).uv(0, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 0, 1).endVertex();
        vc.vertex(m, size, -size, 0).color(r, g, b, a).uv(1, 1).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 0, 1).endVertex();
        vc.vertex(m, size, size, 0).color(r, g, b, a).uv(1, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 0, 1).endVertex();
        vc.vertex(m, -size, size, 0).color(r, g, b, a).uv(0, 0).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 0, 1).endVertex();
        pose.popPose();
    }

    public static void clear() {
        DROPPED.clear();
        PUFFS.clear();
        MUZZLES.clear();
        flashAt = null;
        if (flashLight != null) {
            TableLights.removeLight(flashLight);
            flashLight = null;
        }
    }
}
