package com.bookwriter.text;

/**
 * Prepares arbitrary pasted or downloaded text for a Minecraft book.
 *
 * <p>Pure Java (no Minecraft classes) so it can be unit tested.
 */
public final class TextCleaner {
    private TextCleaner() {
    }

    /**
     * @param text the raw text
     * @param tidy when true, also fixes up the usual copy/paste mess: exotic spaces, invisible
     *             characters, trailing whitespace and long runs of blank lines
     * @return text that only contains characters a book accepts, without leading blank lines or
     *         trailing whitespace
     */
    public static String clean(String text, boolean tidy) {
        if (text == null || text.isEmpty()) {
            return "";
        }

        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '\r' -> {
                    // \r\n and lone \r both become a single \n
                    if (i + 1 >= text.length() || text.charAt(i + 1) != '\n') {
                        out.append('\n');
                    }
                }
                case '\n', ' ', ' ', '\u000B', '\f' -> out.append('\n');
                case '\t' -> out.append("  ");
                // The section sign starts a formatting code and is rejected by the game; drop the
                // whole code (the sign and the letter after it).
                case '§' -> i++;
                case '\u007F' -> {
                }
                default -> {
                    if (c < ' ') {
                        continue;
                    }
                    if (tidy) {
                        if (isInvisible(c)) {
                            continue;
                        }
                        if (isExoticSpace(c)) {
                            c = ' ';
                        }
                    }
                    out.append(c);
                }
            }
        }

        String result = out.toString();
        if (tidy) {
            result = result.replaceAll("[ ]+\n", "\n").replaceAll("\n{3,}", "\n\n");
        }
        return result.replaceAll("^\\s*\n", "").stripTrailing();
    }

    private static boolean isInvisible(char c) {
        return c == '­' || (c >= '​' && c <= '‏') || c == '⁠' || c == '﻿';
    }

    private static boolean isExoticSpace(char c) {
        return c == ' ' || c == ' ' || (c >= ' ' && c <= ' ') || c == ' ' || c == ' ' || c == '　';
    }
}
