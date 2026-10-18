package com.starfall.game;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * The player's progress, saved on the phone (SharedPreferences) like GameSettings:
 * gold, which heroes are unlocked, and how many duplicates of each hero were pulled.
 *
 * Every duplicate gives +1% HP, ATK and SPD, up to +20%.
 * load() is safe to call from anywhere, as often as you like: it only reads the file once.
 */
public final class PlayerData {

    public static final int HERO_COUNT = 5;
    public static final int MAX_DUPES = 20;          // +20% is the cap
    /** Gold a brand-new save starts with (enough for a 10x pull and a few 1x pulls while testing). */
    public static final int START_GOLD = 10000;

    // results of addCopy()
    public static final int NEW = 0, DUPLICATE = 1, MAXED = 2;

    private static final String PREFS = "starfall_data";

    private static int gold = START_GOLD;
    private static final boolean[] owned = new boolean[HERO_COUNT];
    private static final int[] dupes = new int[HERO_COUNT];
    private static int selected = 0;
    private static boolean loaded;

    private PlayerData() {}

    public static synchronized void load(Context c) {
        if (loaded) return;
        SharedPreferences p = c.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        gold = Math.max(0, p.getInt("gold", START_GOLD));
        for (int i = 0; i < HERO_COUNT; i++) {
            owned[i] = p.getBoolean("owned_" + i, i == 0);          // Rin is unlocked from the start
            dupes[i] = Math.max(0, Math.min(MAX_DUPES, p.getInt("dupes_" + i, 0)));
        }
        selected = Math.max(0, Math.min(HERO_COUNT - 1, p.getInt("selected", 0)));
        loaded = true;
    }

    private static void save(Context c) {
        SharedPreferences.Editor e = c.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit();
        e.putInt("gold", gold).putInt("selected", selected);
        for (int i = 0; i < HERO_COUNT; i++) {
            e.putBoolean("owned_" + i, owned[i]).putInt("dupes_" + i, dupes[i]);
        }
        e.apply();
    }

    // ---- gold ----
    public static synchronized int gold() { return gold; }

    public static synchronized void addGold(Context c, int amount) {
        load(c);
        gold += Math.max(0, amount);
        save(c);
    }

    /** @return true and takes the gold if the player has enough, otherwise false and nothing changes. */
    public static synchronized boolean trySpend(Context c, int amount) {
        load(c);
        if (gold < amount) return false;
        gold -= amount;
        save(c);
        return true;
    }

    // ---- heroes ----
    public static synchronized boolean isOwned(int hero) { return owned[hero]; }

    /** Duplicates of this hero, 0..20. This is also the bonus in percent. */
    public static synchronized int dupes(int hero) { return dupes[hero]; }

    public static synchronized int selectedHero() { return selected; }

    /** Adds one copy of a hero. @return NEW, DUPLICATE (bonus went up) or MAXED (already +20%). */
    public static synchronized int addCopy(Context c, int hero) {
        load(c);
        int result;
        if (!owned[hero]) {
            owned[hero] = true;
            result = NEW;
        } else if (dupes[hero] >= MAX_DUPES) {
            result = MAXED;
        } else {
            dupes[hero]++;
            result = DUPLICATE;
        }
        save(c);
        return result;
    }
}
