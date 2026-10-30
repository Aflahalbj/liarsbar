package com.liarsbar.table;

import com.liarsbar.game.deck.Card;
import com.liarsbar.game.deck.GameEvent;
import com.liarsbar.game.deck.Rank;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Semua yang boleh diketahui siapa pun tentang satu meja: dikirim ke semua
 * pemain di meja dan penonton. Diperbarui dari {@link GameEvent} hanya saat
 * kejadian itu disiarkan (lihat {@link TableInstance}), bukan saat engine
 * menghitungnya, jadi hasil tembakan tidak pernah bocor lewat sini sebelum
 * momen tembakan. Kartu di tangan tidak pernah ada di sini.
 */
public final class PublicState {

    public enum Stage {
        LOBBY, DEALING, TURN, CALLED, REVEAL, ROULETTE, SHOT, FINISHED
    }

    public static final class SeatInfo {
        public String name;
        /** Null untuk bot. */
        public UUID player;
        public boolean bot;
        /** Pemain manusia sedang tidak ada (keluar/terputus); kursinya dimainkan bot. */
        public boolean away;
        public boolean alive = true;
        public int cards;
        public int pulls;

        public SeatInfo(String name, UUID player, boolean bot) {
            this.name = name;
            this.player = player;
            this.bot = bot;
        }
    }

    public int tableId;
    public boolean fast;
    public Stage stage = Stage.LOBBY;
    public final List<SeatInfo> seats = new ArrayList<>();
    public int chambers = 6;

    public int round;
    public int decks;
    public Rank tableRank;
    /** Kursi yang sedang giliran (-1: tidak ada). */
    public int current = -1;
    public boolean canCallLiar;
    /** Tick server saat giliran habis (hanya di server; client menerima sisa tick). */
    public long deadline;
    /** Lama giliran penuh (untuk lingkaran timer). */
    public int turnLength;

    public int claimSeat = -1;
    public int claimCount;
    public int pile;

    public int caller = -1;
    public int accused = -1;
    public boolean forced;
    public List<Card> revealed = List.of();
    public boolean lie;

    public List<Integer> shooters = List.of();
    /** Roulette yang sedang berjalan memakai versi sinematik penuh. */
    public boolean rouletteFull;
    public int shotSeat = -1;
    public boolean shotFired;

    public int winner = -1;

    public PublicState(int tableId) {
        this.tableId = tableId;
    }

    /** Terapkan satu kejadian yang sedang disiarkan. */
    public void apply(GameEvent event, long now, int turnTicks) {
        if (event instanceof GameEvent.RoundStarted e) {
            stage = Stage.DEALING;
            round = e.round();
            decks = e.decks();
            tableRank = e.tableRank();
            for (int i = 0; i < seats.size(); i++) {
                seats.get(i).cards = e.handSizes().get(i);
            }
            current = -1;
            canCallLiar = false;
            claimSeat = -1;
            claimCount = 0;
            pile = 0;
            caller = -1;
            accused = -1;
            forced = false;
            revealed = List.of();
            lie = false;
            shooters = List.of();
            shotSeat = -1;
        } else if (event instanceof GameEvent.TurnStarted e) {
            stage = Stage.TURN;
            current = e.seat();
            canCallLiar = e.canCallLiar();
            deadline = now + turnTicks;
            turnLength = turnTicks;
        } else if (event instanceof GameEvent.CardsPlayed e) {
            seats.get(e.seat()).cards = e.cardsLeft();
            claimSeat = e.seat();
            claimCount = e.count();
            pile += e.count();
            current = -1;
            canCallLiar = false;
        } else if (event instanceof GameEvent.LiarCalled e) {
            stage = Stage.CALLED;
            caller = e.caller();
            accused = e.accused();
            forced = e.forced();
            current = -1;
            canCallLiar = false;
        } else if (event instanceof GameEvent.Revealed e) {
            stage = Stage.REVEAL;
            revealed = e.cards();
            lie = e.lie();
        } else if (event instanceof GameEvent.RouletteStarted e) {
            stage = Stage.ROULETTE;
            shooters = e.shooters();
        } else if (event instanceof GameEvent.TriggerPulled e) {
            stage = Stage.SHOT;
            seats.get(e.seat()).pulls = e.pullNumber();
            shotSeat = e.seat();
            shotFired = e.fired();
        } else if (event instanceof GameEvent.PlayerEliminated e) {
            SeatInfo seat = seats.get(e.seat());
            seat.alive = false;
            seat.cards = 0;
        } else if (event instanceof GameEvent.GameWon e) {
            stage = Stage.FINISHED;
            winner = e.seat();
        }
    }

    /** Kembali ke lobby: semua hidup lagi, revolver baru. */
    public void resetToLobby() {
        stage = Stage.LOBBY;
        round = 0;
        decks = 0;
        tableRank = null;
        current = -1;
        canCallLiar = false;
        claimSeat = -1;
        claimCount = 0;
        pile = 0;
        caller = -1;
        accused = -1;
        revealed = List.of();
        shooters = List.of();
        shotSeat = -1;
        winner = -1;
        for (SeatInfo seat : seats) {
            seat.alive = true;
            seat.cards = 0;
            seat.pulls = 0;
        }
    }

    // ================================================================ jaringan

    public void write(FriendlyByteBuf buf, long now) {
        buf.writeVarInt(tableId);
        buf.writeBoolean(fast);
        buf.writeEnum(stage);
        buf.writeVarInt(chambers);
        buf.writeVarInt(seats.size());
        for (SeatInfo seat : seats) {
            buf.writeUtf(seat.name, 64);
            buf.writeBoolean(seat.player != null);
            if (seat.player != null) {
                buf.writeUUID(seat.player);
            }
            buf.writeBoolean(seat.bot);
            buf.writeBoolean(seat.away);
            buf.writeBoolean(seat.alive);
            buf.writeVarInt(seat.cards);
            buf.writeVarInt(seat.pulls);
        }
        buf.writeVarInt(round);
        buf.writeVarInt(decks);
        writeRank(buf, tableRank);
        buf.writeVarInt(current + 1);
        buf.writeBoolean(canCallLiar);
        buf.writeVarInt((int) Math.max(0, deadline - now));
        buf.writeVarInt(turnLength);
        buf.writeVarInt(claimSeat + 1);
        buf.writeVarInt(claimCount);
        buf.writeVarInt(pile);
        buf.writeVarInt(caller + 1);
        buf.writeVarInt(accused + 1);
        buf.writeBoolean(forced);
        writeCards(buf, revealed);
        buf.writeBoolean(lie);
        buf.writeVarInt(shooters.size());
        for (int shooter : shooters) {
            buf.writeVarInt(shooter);
        }
        buf.writeBoolean(rouletteFull);
        buf.writeVarInt(shotSeat + 1);
        buf.writeBoolean(shotFired);
        buf.writeVarInt(winner + 1);
    }

    /** {@code now} = tick lokal penerima; {@link #deadline} jadi now + sisa tick. */
    public static PublicState read(FriendlyByteBuf buf, long now) {
        PublicState s = new PublicState(buf.readVarInt());
        s.fast = buf.readBoolean();
        s.stage = buf.readEnum(Stage.class);
        s.chambers = buf.readVarInt();
        int count = buf.readVarInt();
        for (int i = 0; i < count; i++) {
            String name = buf.readUtf(64);
            UUID player = buf.readBoolean() ? buf.readUUID() : null;
            SeatInfo seat = new SeatInfo(name, player, buf.readBoolean());
            seat.away = buf.readBoolean();
            seat.alive = buf.readBoolean();
            seat.cards = buf.readVarInt();
            seat.pulls = buf.readVarInt();
            s.seats.add(seat);
        }
        s.round = buf.readVarInt();
        s.decks = buf.readVarInt();
        s.tableRank = readRank(buf);
        s.current = buf.readVarInt() - 1;
        s.canCallLiar = buf.readBoolean();
        s.deadline = now + buf.readVarInt();
        s.turnLength = buf.readVarInt();
        s.claimSeat = buf.readVarInt() - 1;
        s.claimCount = buf.readVarInt();
        s.pile = buf.readVarInt();
        s.caller = buf.readVarInt() - 1;
        s.accused = buf.readVarInt() - 1;
        s.forced = buf.readBoolean();
        s.revealed = readCards(buf);
        s.lie = buf.readBoolean();
        int shooters = buf.readVarInt();
        List<Integer> list = new ArrayList<>(shooters);
        for (int i = 0; i < shooters; i++) {
            list.add(buf.readVarInt());
        }
        s.shooters = List.copyOf(list);
        s.rouletteFull = buf.readBoolean();
        s.shotSeat = buf.readVarInt() - 1;
        s.shotFired = buf.readBoolean();
        s.winner = buf.readVarInt() - 1;
        return s;
    }

    public static void writeRank(FriendlyByteBuf buf, Rank rank) {
        buf.writeByte(rank == null ? -1 : rank.ordinal());
    }

    public static Rank readRank(FriendlyByteBuf buf) {
        int ordinal = buf.readByte();
        return ordinal < 0 ? null : Rank.values()[ordinal];
    }

    public static void writeCards(FriendlyByteBuf buf, List<Card> cards) {
        buf.writeVarInt(cards.size());
        for (Card card : cards) {
            buf.writeVarInt(card.id());
            writeRank(buf, card.rank());
        }
    }

    public static List<Card> readCards(FriendlyByteBuf buf) {
        int count = buf.readVarInt();
        List<Card> cards = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            cards.add(new Card(buf.readVarInt(), readRank(buf)));
        }
        return List.copyOf(cards);
    }
}
