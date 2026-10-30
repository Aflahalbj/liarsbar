package com.liarsbar.client.fx;

import com.liarsbar.LiarsBar;
import com.liarsbar.client.ClientTable;
import com.liarsbar.client.cinematic.RouletteDirector;
import com.mojang.logging.LogUtils;
import foundry.veil.api.client.render.VeilRenderSystem;
import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.api.client.render.post.PostProcessingManager;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.slf4j.Logger;

/**
 * Grading layar meja (pipeline post Veil {@code liarsbar:table}), aktif selama ada meja:
 * bloom, S-curve + black point, split-tone, grain, vignette dasar, letterbox 2,39:1 saat kamera
 * sinematik. Saat roulette ditambah vignette berdenyut, chromatic aberration, desaturasi, kilat DOR,
 * dan gelap. Tanpa depth of field (Art Bible: blur tidak boleh menutupi kesalahan).
 */
public final class PostFx {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final ResourceLocation PIPELINE = new ResourceLocation(LiarsBar.MOD_ID, "table");
    private static final ResourceLocation SHADER = new ResourceLocation(LiarsBar.MOD_ID, "table");

    private static RouletteDirector.Fx fx = RouletteDirector.Fx.NONE;
    private static boolean warned;
    /** Grading (AnimDev "postcfg"): kontras S-curve, campuran kurva, black point, grain, bloom, ambang bloom, vignette dasar. */
    private static float contrast = 1.3f, curve = 0.42f, blackPoint = 0.005f, grain = 0.018f, bloom = 0.55f,
            bloomThreshold = 0.9f, baseVignette = 0.3f, exposure = 1.45f;

    public static void tune(float[] v) {
        float[] cur = {contrast, curve, blackPoint, grain, bloom, bloomThreshold, baseVignette, exposure};
        System.arraycopy(v, 0, cur, 0, Math.min(v.length, cur.length));
        contrast = cur[0];
        curve = cur[1];
        blackPoint = cur[2];
        grain = cur[3];
        bloom = cur[4];
        bloomThreshold = cur[5];
        baseVignette = cur[6];
        exposure = cur[7];
    }

    private PostFx() {
    }

    public static RouletteDirector.Fx current() {
        return fx;
    }

    /** Sebelum level digambar: hitung efek frame ini dan pasang/lepas pipeline. */
    public static void frame(float partial, Matrix4f projection, Matrix4f view, Vec3 camera) {
        fx = ClientTable.state() == null ? RouletteDirector.Fx.NONE : RouletteDirector.fx(partial);
        com.liarsbar.client.ui.ScreenSpace.capture(projection, view, camera);
        PostProcessingManager manager = VeilRenderSystem.renderer().getPostProcessingManager();
        PostPipeline pipeline = manager.getPipeline(PIPELINE);
        if (pipeline == null) {
            if (!warned) {
                warned = true;
                LOGGER.warn("Post pipeline {} not loaded", PIPELINE);
            }
            return;
        }
        // Bloom bohlam/api moncong selalu ada di meja; efek lain hanya saat diperlukan.
        boolean want = (fx.active() || ClientTable.state() != null) && com.liarsbar.client.test.DevToggles.on("post");
        if (want && !manager.isActive(PIPELINE)) {
            manager.add(PIPELINE);
        } else if (!want && manager.isActive(PIPELINE)) {
            manager.remove(PIPELINE);
        }
    }

    public static void preProcess(ResourceLocation name, PostPipeline pipeline, PostPipeline.Context context) {
        if (!PIPELINE.equals(name)) {
            return;
        }
        ShaderProgram shader = context.getShader(SHADER);
        if (shader == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float w = mc.getWindow().getWidth();
        float h = mc.getWindow().getHeight();
        shader.setFloat("Vignette", fx.vignette());
        shader.setFloat("Pulse", fx.pulse());
        shader.setFloat("Aberration", fx.aberration());
        shader.setFloat("Desaturate", fx.desaturate());
        shader.setFloat("Flash", fx.flash());
        shader.setFloat("Darken", fx.darken());
        shader.setFloat("Grain", grain + fx.grain());
        shader.setFloat("Bloom", com.liarsbar.client.test.DevToggles.on("bloom") ? bloom + 0.8f * fx.flash() : 0);
        shader.setFloat("BloomThreshold", bloomThreshold);
        shader.setFloat("Contrast", contrast);
        shader.setFloat("Curve", com.liarsbar.client.test.DevToggles.on("grade") ? curve : 0);
        shader.setFloat("BlackPoint", com.liarsbar.client.test.DevToggles.on("grade") ? blackPoint : 0);
        shader.setFloat("BaseVignette", baseVignette);
        shader.setFloat("Exposure", exposure);
        shader.setFloat("Letterbox", com.liarsbar.client.cinematic.CameraDirector.cinematicWeight());
        shader.setVector("Texel", 1f / Math.max(1, w), 1f / Math.max(1, h));
        shader.setFloat("Seed", (ClientTable.now() % 997) * 0.731f);
    }
}
