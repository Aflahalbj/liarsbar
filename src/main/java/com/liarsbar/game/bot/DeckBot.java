package com.liarsbar.game.bot;

import com.liarsbar.game.Rng;
import com.liarsbar.game.deck.Card;
import com.liarsbar.game.deck.DeckBuilder;
import com.liarsbar.game.deck.LiarsDeckGame.SeatView;
import com.liarsbar.game.deck.LiarsDeckRules;

import java.util.ArrayList;
import java.util.List;

/**
 * Bot Liar's Deck sederhana, hanya memakai {@link SeatView} (tangan sendiri +
 * info publik), jadi tidak bisa curang. Menuduh kalau peluang permainan
 * terakhir bohong cukup besar; selain itu main kartu jujur, dan menggertak
 * kalau tidak punya kartu jujur.
 */
public final class DeckBot {

    public sealed interface Decision {
        record Play(List<Integer> cardIds) implements Decision {
        }

        record CallLiar() implements Decision {
        }
    }

    private static final int TRUTHS_PER_DECK = DeckBuilder.PER_RANK + DeckBuilder.JOKERS;

    private final LiarsDeckRules rules;
    /** 0 = hati-hati, 1 = nekat (lebih sering menuduh dan menggertak). */
    private final double nerve;

    public DeckBot(LiarsDeckRules rules, double nerve) {
        this.rules = rules;
        this.nerve = nerve;
    }

    public Decision decide(SeatView view, Rng rng) {
        List<Card> truths = new ArrayList<>();
        List<Card> lies = new ArrayList<>();
        for (Card card : view.hand()) {
            (card.rank().truthfulOn(view.tableRank()) ? truths : lies).add(card);
        }

        if (view.canCallLiar()) {
            double threshold = (truths.isEmpty() ? 0.42 : 0.55) - 0.15 * nerve + (rng.nextDouble() - 0.5) * 0.2;
            if (lieChance(view, truths.size()) > threshold) {
                return new Decision.CallLiar();
            }
        }

        if (!truths.isEmpty()) {
            int count = Math.min(truths.size(), 1 + rng.nextInt(rules.maxPlay()));
            List<Integer> ids = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                ids.add(truths.get(i).id());
            }
            // Kadang menyelipkan satu kartu bohong di antara yang jujur.
            if (!lies.isEmpty() && ids.size() < rules.maxPlay() && rng.nextDouble() < 0.15 * nerve) {
                ids.add(lies.get(0).id());
            }
            return new Decision.Play(ids);
        }
        int count = Math.min(lies.size(), rng.nextDouble() < 0.75 ? 1 : 2);
        List<Integer> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids.add(lies.get(i).id());
        }
        return new Decision.Play(ids);
    }

    /**
     * Peluang permainan terakhir mengandung kartu bohong, kalau kartunya
     * dianggap diambil acak dari kartu yang tidak terlihat (hipergeometrik).
     * Pemain sungguhan cenderung menaruh kartu jujur, jadi angka ini
     * dikurangi (dikalibrasi supaya satu ronde sekitar 4-6 giliran, seperti
     * di video gameplay aslinya).
     */
    static double lieChance(SeatView view, int myTruths) {
        int unseen = DeckBuilder.DECK_SIZE * view.decks() - view.hand().size();
        int unknownTruths = TRUTHS_PER_DECK * view.decks() - myTruths;
        double allTrue = 1.0;
        for (int i = 0; i < view.lastPlayCount(); i++) {
            allTrue *= Math.max(0, unknownTruths - i) / (double) Math.max(1, unseen - i);
        }
        return (1.0 - allTrue) * 0.6;
    }
}
