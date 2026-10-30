package com.liarsbar.client.anim;

import com.liarsbar.LiarsBar;
import com.liarsbar.entity.BarNpcEntity;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Satu {@link NpcPuppet} untuk tiap {@link BarNpcEntity} di dunia client, plus animasinya: pengunjung bergantian
 * minum / ngobrol / ketawa (tiap NPC punya fase sendiri supaya tidak serempak), bartender mengelap, mengambil
 * botol dari rak, atau menuang saat berhenti. Saat berjalan seluruh badan memakai langkah vanilla.
 */
public final class NpcPuppets {

    private static final int PRIORITY = 3000;
    /** Panjang satu "giliran" emote pengunjung (tick). */
    private static final int PATRON_SEGMENT = 240;
    private static final String[] PATRON_ANIMS = {"npc_drink", "npc_talk", "npc_laugh", "npc_drink", "npc_talk"};

    private static final class Puppet {
        final NpcPuppet body;
        final ModifierLayer<IAnimation> layer = new ModifierLayer<>();
        final NpcRig rig;
        String animation;

        Puppet(NpcPuppet body) {
            this.body = body;
            this.rig = new NpcRig(body);
        }
    }

    private static final Map<BarNpcEntity, Puppet> PUPPETS = new HashMap<>();
    private static ClientLevel level;
    /** Id entity client-only: negatif, di bawah rentang boneka bot. */
    private static int nextId = -2_000_000;

    /** AnimDev "npcanim": animasi yang dipaksakan ke semua NPC, beku di tick tertentu (null = normal). */
    static String forced;
    static int forcedTick;
    /** AnimDev "npccam": kamera mengikuti NPC berkulit {@code camSkin} ({dist, azimut, elevasi, fov, tinggi tatap}). */
    private static int camSkin = -1;
    private static double[] cam;

    private NpcPuppets() {
    }

    public static void force(String name, int tick) {
        forced = name;
        forcedTick = tick;
    }

    public static void follow(int skin, double[] params) {
        camSkin = skin;
        cam = params;
    }

    /** Kamera uji di sekitar mata NPC (azimut 0 = di depannya, 90 = di kanannya). */
    private static void followCam(NpcPuppet body) {
        net.minecraft.world.phys.Vec3 eye = body.getEyePosition();
        float yaw = body.yBodyRot;
        net.minecraft.world.phys.Vec3 fwd = net.minecraft.world.phys.Vec3.directionFromRotation(0, yaw);
        net.minecraft.world.phys.Vec3 right = new net.minecraft.world.phys.Vec3(-fwd.z, 0, fwd.x);
        double az = Math.toRadians(cam[1]), el = Math.toRadians(cam[2]);
        net.minecraft.world.phys.Vec3 target = eye.add(0, cam[4], 0);
        net.minecraft.world.phys.Vec3 dir = fwd.scale(Math.cos(az)).add(right.scale(Math.sin(az)));
        net.minecraft.world.phys.Vec3 pos = target.add(dir.scale(cam[0] * Math.cos(el))).add(0, cam[0] * Math.sin(el), 0);
        com.liarsbar.client.cinematic.DevCamera.set(new com.liarsbar.client.cinematic.DevCamera.Override(pos, target,
                (float) cam[3]));
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != level) {
            PUPPETS.clear();
            level = mc.level;
        }
        if (level == null) {
            return;
        }
        List<BarNpcEntity> npcs = new ArrayList<>();
        for (Entity e : level.entitiesForRendering()) {
            if (e instanceof BarNpcEntity npc && !npc.isRemoved()) {
                npcs.add(npc);
            }
        }
        for (BarNpcEntity npc : npcs) {
            Puppet puppet = PUPPETS.get(npc);
            if (puppet == null) {
                puppet = new Puppet(new NpcPuppet(level, npc));
                level.addPlayer(nextId--, puppet.body);
                PlayerAnimationAccess.getPlayerAnimLayer(puppet.body).addAnimLayer(PRIORITY, puppet.layer);
                // IK tangan yang memegang gelas/botol di atas animasinya.
                PlayerAnimationAccess.getPlayerAnimLayer(puppet.body).addAnimLayer(PRIORITY + 100, puppet.rig);
                PUPPETS.put(npc, puppet);
            }
            animate(npc, puppet, level.getGameTime());
            if (cam != null && npc.skin() == camSkin) {
                followCam(puppet.body);
            }
        }
        PUPPETS.entrySet().removeIf(entry -> {
            if (entry.getKey().isRemoved() || !npcs.contains(entry.getKey())) {
                level.removeEntity(entry.getValue().body.getId(), Entity.RemovalReason.DISCARDED);
                return true;
            }
            return false;
        });
    }

    private static void animate(BarNpcEntity npc, Puppet puppet, long time) {
        if (forced != null) {
            KeyframeAnimation anim = PlayerAnimationRegistry.getAnimation(new ResourceLocation(LiarsBar.MOD_ID, forced));
            puppet.animation = null;
            puppet.layer.setAnimation(anim == null ? null : new KeyframeAnimationPlayer(anim, forcedTick));
            puppet.rig.play(forced, time - forcedTick, anim == null ? 0 : anim.endTick + 1);
            return;
        }
        String want = switch (npc.act()) {
            case BarNpcEntity.ACT_SIT -> {
                // Fase per NPC dari id-nya; segmen berikutnya dipilih dari daftar dengan langkah yang berbeda per NPC.
                long phase = time + Math.floorMod(npc.getUUID().hashCode(), PATRON_SEGMENT);
                long segment = phase / PATRON_SEGMENT;
                int step = 1 + Math.floorMod(npc.getUUID().hashCode() >> 8, 3);
                yield PATRON_ANIMS[(int) Math.floorMod(segment * step + npc.skin(), PATRON_ANIMS.length)];
            }
            case BarNpcEntity.ACT_WIPE -> "npc_wipe";
            case BarNpcEntity.ACT_POUR -> "npc_pour";
            case BarNpcEntity.ACT_FETCH -> "npc_fetch";
            default -> null;
        };
        if (Objects.equals(want, puppet.animation)) {
            return;
        }
        puppet.animation = want;
        KeyframeAnimation animation = want == null ? null
                : PlayerAnimationRegistry.getAnimation(new ResourceLocation(LiarsBar.MOD_ID, want));
        IAnimation next = animation == null ? null : new KeyframeAnimationPlayer(animation);
        puppet.layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(12, Ease.INOUTSINE), next);
        puppet.rig.play(want, time, animation == null ? 0 : animation.endTick + 1);
    }

    /** Rig IK + properti boneka NPC ini (null kalau bukan boneka yang dikelola). */
    public static NpcRig rig(NpcPuppet body) {
        for (Puppet p : PUPPETS.values()) {
            if (p.body == body) {
                return p.rig;
            }
        }
        return null;
    }

    /** AnimDev "npcprobe": jarak terukur dari render terakhir tiap NPC. */
    public static void probe(org.slf4j.Logger log) {
        for (Puppet p : PUPPETS.values()) {
            NpcRig r = p.rig;
            log.info("[animdev] npcprobe skin={} anim={} t={} rim-mulut={} ujungbotol-bibirgelas={}", p.body.npc.skin(),
                    r.anim(), String.format("%.1f", r.age()), fmt(r.probeMouth), fmt(r.probePour));
        }
    }

    private static String fmt(float v) {
        return Float.isNaN(v) ? "-" : String.format("%.3f", v);
    }

    /** Bersihkan saat dunia ditutup. */
    public static void clear() {
        PUPPETS.clear();
        level = null;
    }
}
