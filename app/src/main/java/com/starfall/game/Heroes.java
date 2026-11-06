package com.starfall.game;

/** The five playable heroes (the same order as the artwork on the recruitment banner). */
public final class Heroes {
    public static final String[] NAMES  = {"Rin", "Caster", "Gilgamesh", "Lancer", "Shionne"};
    public static final String[] TITLES = {"Sword Class", "Arcane Mage", "Archer Class", "Spear Class", "Ranger Class"};
    /** Image names in res/drawable-nodpi: rin_idle_0 ... rin_idle_5 and so on. */
    public static final String[] FRAMES = {"rin_idle_", "caster_idle_", "gilgamesh_idle_", "lancer_idle_", "shionne_idle_"};
    /** Accent colour of each hero (card borders). */
    public static final int[] COLORS = {0xFFE57373, 0xFFB388FF, 0xFFFFD54F, 0xFF64B5F6, 0xFFF48FB1};

    private Heroes() {}
}
