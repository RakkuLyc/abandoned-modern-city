package com.abandonedmoderncity.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The rows of {@code amcs/buildings/index.txt} ({@code type|file|ground|front|model|banners|neutral|seed|hard}, see
 * the header of the file): blank lines and lines starting with {@code #} are skipped, each other line is split at
 * {@code |} and checked, so that a malformed line stops the loading with its number and what is wrong instead of a
 * bare number or index error later.
 */
final class IndexRows {
    /** What {@code /amcs spawn} accepts as a name (a Brigadier word). */
    private static final Pattern NAME = Pattern.compile("[0-9A-Za-z_.+-]+");

    private IndexRows() {
    }

    /** Every structure row of the index, in order. */
    static List<String[]> read(BufferedReader in) throws IOException {
        List<String[]> rows = new ArrayList<>();
        String line;
        int number = 0;
        while ((line = in.readLine()) != null) {
            number++;
            if (!line.isBlank() && !line.startsWith("#")) {
                rows.add(check(line, number));
            }
        }
        return rows;
    }

    /** The fields of one row, or an {@link IllegalStateException} naming the line and the field that is wrong. */
    static String[] check(String line, int number) {
        String[] f = line.trim().split("\\|");
        if (f.length < 2 || f[0].isBlank() || f[1].isBlank()) {
            throw bad(number, "?", "needs at least a type and a file name");
        }
        String name = f[1].trim();
        if (!NAME.matcher(name).matches()) {
            throw bad(number, name, "the file name may only use letters, digits and _ . + - (no accents or spaces)");
        }
        if (f.length > 2) {
            number(f[2], number, name, "ground");
        }
        if (f.length > 3 && !f[3].isBlank()) {
            number(f[3], number, name, "front");
        }
        if (f.length > 5 && !f[5].isBlank()) {
            String[] a = f[5].trim().split("[@,:=]");
            if (a.length != 5) {
                throw bad(number, name, "banners '" + f[5].trim() + "' is not <source>@<dx>,<dz>:<from>=<to>");
            }
            number(a[1], number, name, "banners dx");
            number(a[2], number, name, "banners dz");
        }
        if (f.length > 7 && !f[7].isBlank()) {
            number(f[7], number, name, "seed");
        }
        if (f.length > 8 && !f[8].isBlank()) {
            for (String c : f[8].split(";")) {
                String[] xyz = c.trim().split(",");
                if (xyz.length != 3) {
                    throw bad(number, name, "hard cell '" + c.trim() + "' is not x,y,z");
                }
                for (String v : xyz) {
                    number(v, number, name, "hard cell");
                }
            }
        }
        return f;
    }

    private static void number(String value, int number, String name, String field) {
        try {
            Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw bad(number, name, field + " '" + value.trim() + "' is not a whole number");
        }
    }

    private static IllegalStateException bad(int number, String name, String what) {
        return new IllegalStateException("amcs/buildings/index.txt line " + number + " (" + name + "): " + what);
    }
}
