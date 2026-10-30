package com.liarsbar.client.anim;

import com.liarsbar.LiarsBar;
import com.liarsbar.client.ClientTable;
import com.liarsbar.client.cinematic.RouletteDirector;
import com.liarsbar.entity.BotEntity;
import com.liarsbar.table.PublicState;
import com.mojang.logging.LogUtils;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

import java.lang.ref.WeakReference;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Animasi semua yang duduk di meja yang dilihat (pemain dan boneka bot), dari
 * {@link RouletteDirector#act}: duduk tenang/gelisah, roulette, lega, tewas,
 * mayat. Pergantian dibaurkan; animasi yang dirancang menyambung (tahan ->
 * lega/tewas) hanya dibaurkan sebentar.
 */
public final class SeatAnimator {

    private static final Logger LOGGER = LogUtils.getLogger();
    /** Di atas lapisan animasi mod lain. */
    private static final int PRIORITY = 3100;

    private static final class Entry {
        final WeakReference<AbstractClientPlayer> owner;
        final ModifierLayer<IAnimation> layer = new ModifierLayer<>();
        final LookLayer look = new LookLayer();
        final GripLayer grip = new GripLayer();
        String animation;
        RouletteDirector.Phase phase;

        Entry(AbstractClientPlayer owner) {
            this.owner = new WeakReference<>(owner);
        }
    }

    private static final Map<AbstractClientPlayer, Entry> ENTRIES = new WeakHashMap<>();
    /** AnimDev: animasi yang dipaksakan per kursi, beku di tick tertentu (atau null). */
    private static final Map<Integer, String> FORCED = new java.util.HashMap<>();
    private static final Map<Integer, Integer> FORCED_TICK = new java.util.HashMap<>();

    private SeatAnimator() {
    }

    /** Kursi entity ini di meja yang dilihat (-1: bukan anggota meja). */
    public static int seatOf(Entity entity) {
        PublicState state = ClientTable.state();
        if (state == null) {
            return -1;
        }
        if (entity instanceof BotPuppet puppet) {
            return puppet.bot.tableId() == state.tableId ? puppet.bot.seat() : -1;
        }
        if (entity instanceof BotEntity bot) {
            return bot.tableId() == state.tableId ? bot.seat() : -1;
        }
        if (entity instanceof Player player) {
            for (int i = 0; i < state.seats.size(); i++) {
                if (player.getUUID().equals(state.seats.get(i).player)) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Set<AbstractClientPlayer> seen = new HashSet<>();
        for (Player p : mc.level.players()) {
            if (!(p instanceof AbstractClientPlayer player)) {
                continue;
            }
            int seat = seatOf(player);
            if (seat >= 0 && player instanceof BotPuppet == false) {
                // Pemain yang sedang keluar dari kursinya tidak dianimasikan meja.
                if (!(player.getVehicle() instanceof com.liarsbar.entity.SeatEntity)) {
                    seat = -1;
                }
            }
            Entry entry = ENTRIES.get(player);
            if (entry != null && entry.owner.get() != player) {
                ENTRIES.remove(player);
                entry = null;
            }
            if (seat < 0) {
                if (entry != null) {
                    entry.look.target(0, 0, 0);
                    entry.grip.seat = -1;
                }
                if (entry != null && entry.animation != null) {
                    entry.animation = null;
                    entry.phase = null;
                    entry.layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(8, Ease.INOUTSINE), null);
                }
                continue;
            }
            seen.add(player);
            if (entry == null) {
                entry = new Entry(player);
                PlayerAnimationAccess.getPlayerAnimLayer(player).addAnimLayer(PRIORITY, entry.layer);
                PlayerAnimationAccess.getPlayerAnimLayer(player).addAnimLayer(PRIORITY + 100, entry.look);
                PlayerAnimationAccess.getPlayerAnimLayer(player).addAnimLayer(PRIORITY + 200, entry.grip);
                ENTRIES.put(player, entry);
            }
            entry.grip.seat = seat;
            String forced = FORCED.get(seat);
            if (forced != null) {
                if (!forced.equals(entry.animation)) {
                    entry.animation = forced;
                    entry.phase = null;
                    play(entry, forced, FORCED_TICK.getOrDefault(seat, 0), 0, true);
                }
                continue;
            }
            RouletteDirector.Act act = RouletteDirector.act(seat, 0);
            if (com.liarsbar.client.test.DevToggles.on("look")) {
                attention(entry.look, seat, act);
            } else {
                entry.look.target(0, 0, 0);
            }
            if (act.animation().equals(entry.animation) && act.phase() == entry.phase) {
                continue;
            }
            int fade = fade(entry.phase, act.phase());
            entry.animation = act.animation();
            entry.phase = act.phase();
            play(entry, act.animation(), Math.max(0, (int) act.tick()), fade, false);
        }
    }

    /** Dari GunLayer tiap frame: kerangka induk lengan (ruang model -> dunia) untuk IK genggaman. */
    public static void capture(AbstractClientPlayer player, org.joml.Matrix4f parentToWorld,
                               net.minecraft.client.model.PlayerModel<?> model) {
        Entry entry = ENTRIES.get(player);
        if (entry != null && entry.owner.get() == player) {
            entry.grip.capture(parentToWorld, model.rightArm, model.leftArm);
        }
    }

    /** AnimDev: seberapa jauh titik genggam kanan kursi ini di luar jangkauan lengan (blok model). */
    public static float gripShort(int seat) {
        for (Entry entry : ENTRIES.values()) {
            if (entry.grip.seat == seat) {
                return entry.grip.rightShort();
            }
        }
        return -1;
    }

    private static long kickBang = Long.MIN_VALUE;

    /**
     * Ke mana kursi ini memandang: sekilas ke yang sedang giliran. Selama cutscene LIAR!/reveal dan
     * roulette kepala dan badan tetap menghadap meja. Saat DOR semua yang hidup tersentak menjauh.
     */
    private static void attention(LookLayer look, int seat, RouletteDirector.Act act) {
        PublicState state = ClientTable.state();
        // Selama pembagian: dealer menoleh ke kursi tujuan lemparan, yang lain melirik tangan dealer.
        float[] deal = state == null || !state.seats.get(seat).alive ? null
                : com.liarsbar.client.cinematic.DealDirector.look(seat, 0);
        if (deal != null && (act.phase() == RouletteDirector.Phase.IDLE || act.phase() == RouletteDirector.Phase.DEAL)) {
            look.target(deal[0], deal[1], deal[2]);
            return;
        }
        if (state == null || act.phase() != RouletteDirector.Phase.IDLE || !state.seats.get(seat).alive) {
            look.target(0, 0, 0);
            return;
        }
        int focus = -1;
        float weight = 0;
        boolean cutscene = RouletteDirector.active() || state.stage == PublicState.Stage.CALLED
                || state.stage == PublicState.Stage.REVEAL;
        if (!cutscene && state.stage == PublicState.Stage.TURN && state.current >= 0) {
            focus = state.current;
            weight = 0.45f;
        }
        RouletteDirector.Frame me = RouletteDirector.frame(seat);
        RouletteDirector.Frame them = focus >= 0 && focus != seat ? RouletteDirector.frame(focus) : null;
        if (me == null || them == null) {
            look.target(0, 0, 0);
        } else {
            net.minecraft.world.phys.Vec3 d = them.eye().add(0, -0.15, 0).subtract(me.eye());
            double r = d.dot(me.right());
            double f = d.dot(me.forward());
            float yawLeft = (float) Math.atan2(-r, f);
            float pitchDown = (float) Math.atan2(-d.y, Math.hypot(r, f)) - 0.18f;
            look.target(yawLeft, pitchDown, weight);
        }
        long bang = RouletteDirector.bangAt();
        int shooter = RouletteDirector.bangSeat();
        RouletteDirector.Frame gun = shooter >= 0 && shooter != seat ? RouletteDirector.frame(shooter) : null;
        if (bang != Long.MIN_VALUE && bang != kickBang && ClientTable.now() - bang < 3 && gun != null && me != null) {
            // Sentakan menjauh dari arah tembakan (sisi yang berlawanan dengan penembak).
            net.minecraft.world.phys.Vec3 d = gun.eye().subtract(me.eye());
            float side = d.dot(me.right()) > 0 ? 1 : -1;
            float strength = (float) (1.0 / (1.0 + d.length() * 0.35));
            look.kick(strength, side);
        }
    }

    /** Dipanggil sekali per tick sesudah semua kursi: sentakan hanya sekali per tembakan. */
    public static void endTick() {
        long bang = RouletteDirector.bangAt();
        if (bang != Long.MIN_VALUE && kickBang != bang) {
            kickBang = bang;
        }
    }

    /** Pergantian yang sudah dirancang menyambung cukup dibaurkan sebentar. */
    private static int fade(RouletteDirector.Phase from, RouletteDirector.Phase to) {
        if (from == null) {
            return to == RouletteDirector.Phase.DEAD ? 0 : 6;
        }
        return switch (to) {
            case CLICK, BANG -> 2;
            case DEAD -> from == RouletteDirector.Phase.BANG ? 3 : 0;
            case ROULETTE -> 5;
            case LIAR -> 4;
            case DEAL -> from == RouletteDirector.Phase.DEAL ? 8 : 6;
            case THROW -> 4;
            case IDLE -> from == RouletteDirector.Phase.CLICK ? 4 : from == RouletteDirector.Phase.LIAR ? 14
                    : from == RouletteDirector.Phase.THROW || from == RouletteDirector.Phase.DEAL ? 8 : 10;
        };
    }

    private static void play(Entry entry, String name, int startTick, int fade, boolean frozen) {
        KeyframeAnimation animation = PlayerAnimationRegistry.getAnimation(new ResourceLocation(LiarsBar.MOD_ID, name));
        if (animation == null) {
            LOGGER.warn("Table animation {} not found", name);
            entry.layer.setAnimation(null);
            return;
        }
        KeyframeAnimationPlayer player = new KeyframeAnimationPlayer(animation, Math.min(startTick, animation.endTick));
        IAnimation anim = frozen ? new Frozen(player) : player;
        if (fade <= 0) {
            entry.layer.setAnimation(anim);
        } else {
            entry.layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(fade, Ease.INOUTSINE), anim);
        }
    }

    /** AnimDev: tahan kursi {@code seat} di animasi {@code name} tick {@code tick} (null = kembali normal). */
    public static void force(int seat, String name, int tick) {
        if (name == null) {
            FORCED.remove(seat);
            FORCED_TICK.remove(seat);
        } else {
            FORCED.put(seat, name);
            FORCED_TICK.put(seat, tick);
        }
        for (Entry entry : ENTRIES.values()) {
            entry.animation = null;
        }
    }

    public static void clearForced() {
        FORCED.clear();
        FORCED_TICK.clear();
        for (Entry entry : ENTRIES.values()) {
            entry.animation = null;
        }
    }

    /** Animasi yang tidak berjalan (pose beku di satu tick), untuk pratinjau. */
    private static final class Frozen implements IAnimation {
        private final KeyframeAnimationPlayer inner;

        Frozen(KeyframeAnimationPlayer inner) {
            this.inner = inner;
        }

        @Override
        public boolean isActive() {
            return inner.isActive();
        }

        @Override
        public void tick() {
        }

        @Override
        public dev.kosmx.playerAnim.core.util.Vec3f get3DTransform(String modelName,
                dev.kosmx.playerAnim.api.TransformType type, float tickDelta, dev.kosmx.playerAnim.core.util.Vec3f value0) {
            return inner.get3DTransform(modelName, type, 0, value0);
        }

        @Override
        public void setupAnim(float tickDelta) {
            inner.setupAnim(0);
        }
    }
}
