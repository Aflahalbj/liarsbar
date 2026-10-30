package com.liarsbar.client;

import com.liarsbar.entity.SeatEntity;
import com.liarsbar.game.deck.Card;
import com.liarsbar.game.deck.GameEvent;
import com.liarsbar.game.deck.LiarsDeckRules;
import com.liarsbar.game.deck.Rank;
import com.liarsbar.network.HandS2C;
import com.liarsbar.network.ModNetwork;
import com.liarsbar.network.TableActionC2S;
import com.liarsbar.network.TableEventS2C;
import com.liarsbar.network.TableStateS2C;
import com.liarsbar.table.PublicState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Salinan client dari meja yang sedang dilihat: state publik, kartu di
 * tangan sendiri, pilihan kartu, dan banner kejadian terakhir. Presentasi
 * Fase 2 masih sederhana (teks + suara vanilla); sinematiknya menyusul.
 */
public final class ClientTable {

    /** Banner di tengah layar. */
    public record Banner(Component text, int color, long start, int duration) {
    }

    /** Klaim terakhir (untuk subtitle yang menempel di pembicara). */
    public record Claim(int seat, int count, Rank rank, long at) {
    }

    /** Awal ronde (untuk judul ronde singkat). */
    public record RoundTitle(int round, Rank rank, long at) {
    }

    private static Claim claim;
    private static RoundTitle roundTitle;

    public static Claim claim() {
        return claim;
    }

    public static RoundTitle roundTitle() {
        return roundTitle;
    }

    private static PublicState state;
    private static int mySeat = -1;
    private static List<Card> hand = List.of();
    private static final Set<Integer> selected = new LinkedHashSet<>();
    private static int cursor;
    private static long now;
    private static Banner banner;
    /** Berapa kali tiap kursi beraksi (main kartu / menuduh) sejak meja ini dilihat. */
    private static final int[] actions = new int[LiarsDeckRules.MAX_PLAYERS];

    private ClientTable() {
    }

    public static long now() {
        return now;
    }

    public static PublicState state() {
        return state;
    }

    public static int mySeat() {
        return mySeat;
    }

    public static List<Card> hand() {
        return hand;
    }

    public static Set<Integer> selected() {
        return selected;
    }

    public static int cursor() {
        return cursor;
    }

    /** Kursor terakhir digerakkan keyboard/roda (kartunya tetap naik walau tidak di-hover mouse). */
    private static boolean keyCursor;

    public static boolean keyCursor() {
        return keyCursor;
    }

    /** Mouse digerakkan: kartu hanya naik selama di-hover. */
    static void mouseMoved() {
        keyCursor = false;
    }

    public static Banner banner() {
        return banner;
    }

    public static int actionsOf(int seat) {
        return seat < 0 || seat >= actions.length ? 0 : actions[seat];
    }

    /** Duduk di meja (bukan penonton). */
    public static boolean seated() {
        Minecraft mc = Minecraft.getInstance();
        return state != null && mySeat >= 0 && mc.player != null && mc.player.getVehicle() instanceof SeatEntity;
    }

    /** Duduk dan game sedang berjalan. */
    public static boolean inGame() {
        return seated() && state.stage != PublicState.Stage.LOBBY;
    }

    public static boolean myTurn() {
        return inGame() && state.stage == PublicState.Stage.TURN && state.current == mySeat
                && state.seats.get(mySeat).alive;
    }

    /** Sisa tick giliran sekarang. */
    public static int ticksLeft() {
        return state == null ? 0 : (int) Math.max(0, state.deadline - now);
    }

    static void tick() {
        now++;
        if (banner != null && now > banner.start + banner.duration) {
            banner = null;
        }
        // Vanilla menampilkan "tekan Shift untuk turun" saat naik kursi, padahal turun dicegah selama di meja.
        Minecraft mc = Minecraft.getInstance();
        boolean onSeat = mc.player != null && mc.player.getVehicle() instanceof SeatEntity;
        if (onSeat && !wasOnSeat) {
            mc.gui.setOverlayMessage(Component.empty(), false);
        }
        wasOnSeat = onSeat;
    }

    private static boolean wasOnSeat;

    static void clear() {
        state = null;
        mySeat = -1;
        hand = List.of();
        selected.clear();
        banner = null;
        claim = null;
        roundTitle = null;
    }

    // =========================================================== aksi

    static void moveCursor(int step) {
        if (hand.isEmpty()) {
            return;
        }
        cursor = Math.floorMod(cursor + step, hand.size());
        keyCursor = true;
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.6f, 0.3f);
    }

    /** Kursor mouse menunjuk kartu ke-{@code index}. */
    static void pointAt(int index) {
        if (index < 0 || index >= hand.size() || index == cursor) {
            return;
        }
        cursor = index;
        play(SoundEvents.UI_BUTTON_CLICK.value(), 1.8f, 0.15f);
    }

    static void toggleSelected() {
        if (hand.isEmpty() || !myTurn()) {
            return;
        }
        int id = hand.get(cursor).id();
        if (!selected.remove(id)) {
            if (selected.size() >= LiarsDeckRules.STANDARD.maxPlay()) {
                return;
            }
            selected.add(id);
        }
        play(SoundEvents.BOOK_PAGE_TURN, 1.4f, 0.6f);
    }

    static void throwSelected() {
        if (!myTurn() || selected.isEmpty()) {
            return;
        }
        ModNetwork.toServer(new TableActionC2S(TableActionC2S.Action.PLAY, new ArrayList<>(selected)));
        // Kartu langsung dicabut dari kipas di orang pertama; kartu dunia menyusul saat server menjawab.
        com.liarsbar.client.render.FirstPersonCards.beginThrow(selected);
        com.liarsbar.client.cinematic.ThrowDirector.localThrow();
        selected.clear();
    }

    static void callLiar() {
        if (!myTurn() || !state.canCallLiar) {
            return;
        }
        ModNetwork.toServer(new TableActionC2S(TableActionC2S.Action.CALL_LIAR, List.of()));
    }

    // ========================================================= jaringan

    public static void onState(TableStateS2C msg) {
        PublicState next = msg.state(now);
        if (next == null) {
            clear();
            return;
        }
        if (state == null || state.tableId != next.tableId) {
            hand = List.of();
            selected.clear();
            java.util.Arrays.fill(actions, 0);
        }
        state = next;
        mySeat = msg.mySeat();
        if (next.stage == PublicState.Stage.LOBBY) {
            hand = List.of();
            selected.clear();
        }
    }

    public static void onHand(HandS2C msg) {
        if (state != null && msg.tableId() != state.tableId) {
            return;
        }
        hand = msg.cards();
        Set<Integer> ids = new LinkedHashSet<>();
        for (Card card : hand) {
            ids.add(card.id());
        }
        selected.retainAll(ids);
        cursor = hand.isEmpty() ? 0 : Math.min(cursor, hand.size() - 1);
    }

    public static void onEvent(TableEventS2C msg) {
        if (state == null || msg.tableId() != state.tableId) {
            return;
        }
        GameEvent event = msg.event();
        com.liarsbar.client.cinematic.RouletteDirector.onEvent(msg);
        com.liarsbar.client.cinematic.LiarDirector.onEvent(msg);
        // Dealer membaca kartu ronde lalu (tumpukan, kipas) sebelum ThrowDirector mengosongkannya.
        com.liarsbar.client.cinematic.DealDirector.onEvent(msg);
        com.liarsbar.client.cinematic.ThrowDirector.onEvent(msg);
        if (event instanceof GameEvent.RoundStarted e) {
            roundTitle = new RoundTitle(e.round(), e.tableRank(), now);
            claim = null;
            selected.clear();
        } else if (event instanceof GameEvent.TurnStarted e) {
            if (e.seat() == mySeat) {
                play(SoundEvents.NOTE_BLOCK_PLING.value(), 1.2f, 0.5f);
            }
        } else if (event instanceof GameEvent.CardsPlayed e) {
            actions[e.seat()]++;
            claim = new Claim(e.seat(), e.count(), e.claim(), now);
        } else if (event instanceof GameEvent.LiarCalled e) {
            if (!e.forced()) {
                actions[e.caller()]++;
            }
            // Sekuens LIAR! (LiarDirector + stempel di TableHud) menggantikan banner teks.
            banner = null;
        } else if (event instanceof GameEvent.Revealed e) {
            // Kartu yang dibuka berbicara sendiri (dibalik satu per satu: emas = jujur, terbakar = bohong).
            play(SoundEvents.ITEM_FRAME_ROTATE_ITEM, 0.7f, 1f);
        } else if (event instanceof GameEvent.RouletteStarted) {
            // Sekuens roulette berbicara sendiri (RouletteDirector): tanpa banner, tanpa suara penanda.
            banner = null;
        } else if (event instanceof GameEvent.TriggerPulled e) {
            if (e.fired()) {
                // Diumumkan sesudah badannya jatuh dari kursi.
                banner = new Banner(about("liarsbar.event.bang", e.seat()), 0xFFFF3030, now + 60, 70);
            }
        } else if (event instanceof GameEvent.PlayerEliminated e) {
            // Banner DOR sudah menyebut namanya; HUD menandai kursinya mati. Yang mati tidak memegang kartu lagi.
            if (e.seat() == mySeat) {
                hand = List.of();
                selected.clear();
            }
        } else if (event instanceof GameEvent.GameWon e) {
            show(about("liarsbar.event.won", e.seat()), 0xFFFFD45C, 200);
            play(SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
        }
    }

    // =========================================================== bantuan

    public static Component name(int seat) {
        if (state == null || seat < 0 || seat >= state.seats.size()) {
            return Component.literal("?");
        }
        if (seat == mySeat) {
            return Component.translatable("liarsbar.hud.you");
        }
        return Component.literal(state.seats.get(seat).name);
    }

    /**
     * Kalimat tentang satu kursi. Untuk kursi sendiri dipakai varian {@code key.you}
     * (bahasa Inggris butuh "You are", bukan "You is"). Nama selalu argumen pertama.
     */
    public static Component about(String key, int seat, Object... rest) {
        Object[] args = new Object[rest.length + 1];
        args[0] = name(seat);
        System.arraycopy(rest, 0, args, 1, rest.length);
        return Component.translatable(seat == mySeat && seat >= 0 ? key + ".you" : key, args);
    }

    public static Component rankName(Rank rank) {
        return Component.translatable("liarsbar.rank." + rank.name().toLowerCase());
    }

    public static Component rankTable(Rank rank) {
        return Component.translatable("liarsbar.table." + rank.name().toLowerCase());
    }

    private static void show(Component text, int color, int duration) {
        banner = new Banner(text, color, now, duration);
    }

    private static void play(SoundEvent sound, float pitch, float volume) {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume));
    }
}
