package it.pepita.core.world;

import java.util.Map;

/** Font a blocchi 5x7 per scrivere nel mondo. */
public final class BlockFont {
    private BlockFont() {}

    private static final Map<Character, String[]> GLYPHS = Map.ofEntries(
            Map.entry('P', new String[]{"####.", "#...#", "#...#", "####.", "#....", "#....", "#...."}),
            Map.entry('E', new String[]{"#####", "#....", "#....", "####.", "#....", "#....", "#####"}),
            Map.entry('I', new String[]{"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####"}),
            Map.entry('T', new String[]{"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."}),
            Map.entry('A', new String[]{".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"}),
            Map.entry('M', new String[]{"#...#", "##.##", "#.#.#", "#.#.#", "#...#", "#...#", "#...#"}),
            Map.entry('N', new String[]{"#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"}),
            Map.entry('R', new String[]{"####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"}),
            Map.entry('S', new String[]{".####", "#....", "#....", ".###.", "....#", "....#", "####."}),
            Map.entry('O', new String[]{".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."}),
            Map.entry(' ', new String[]{".....", ".....", ".....", ".....", ".....", ".....", "....."})
    );

    public static String[] glyph(char c) {
        return GLYPHS.getOrDefault(Character.toUpperCase(c), GLYPHS.get(' '));
    }

    /** Larghezza in blocchi di una parola (5 per lettera + 1 di spazio). */
    public static int width(String s) {
        return s.length() * 6 - 1;
    }
}
