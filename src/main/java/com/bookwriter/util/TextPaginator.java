package com.bookwriter.util;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for splitting large text into pages suitable for Minecraft books.
 * Uses smart word-wrapping to avoid cutting words across pages.
 */
public class TextPaginator {
    // Minecraft Java Edition limits
    public static final int MAX_CHARS_PER_PAGE = 1023;
    public static final int MAX_PAGES = 100;

    /**
     * Splits the input text into pages, respecting character limits and word boundaries.
     *
     * @param text The text to paginate
     * @return A list of strings, each representing one page of content
     */
    public static List<String> paginate(String text) {
        List<String> pages = new ArrayList<>();

        if (text == null || text.isEmpty()) {
            pages.add("");
            return pages;
        }

        // Normalize line endings
        text = text.replace("\r\n", "\n").replace("\r", "\n");

        StringBuilder currentPage = new StringBuilder();
        String[] words = splitIntoTokens(text);

        for (String word : words) {
            // Handle case where a single word is longer than max page size
            if (word.length() > MAX_CHARS_PER_PAGE) {
                // Flush current page if it has content
                if (currentPage.length() > 0) {
                    pages.add(currentPage.toString());
                    currentPage = new StringBuilder();
                }
                // Split the oversized word into chunks
                int start = 0;
                while (start < word.length()) {
                    int end = Math.min(start + MAX_CHARS_PER_PAGE, word.length());
                    pages.add(word.substring(start, end));
                    start = end;

                    if (pages.size() >= MAX_PAGES) {
                        return pages;
                    }
                }
                continue;
            }

            // Check if adding this word would exceed the page limit
            if (currentPage.length() + word.length() > MAX_CHARS_PER_PAGE) {
                // Save current page and start a new one
                if (currentPage.length() > 0) {
                    pages.add(currentPage.toString());
                    currentPage = new StringBuilder();

                    if (pages.size() >= MAX_PAGES) {
                        return pages;
                    }
                }
            }

            currentPage.append(word);
        }

        // Add the last page if it has content
        if (currentPage.length() > 0) {
            pages.add(currentPage.toString());
        }

        // Ensure at least one page exists
        if (pages.isEmpty()) {
            pages.add("");
        }

        return pages;
    }

    /**
     * Splits text into tokens, preserving whitespace as separate tokens.
     * This allows us to maintain formatting while splitting at word boundaries.
     */
    private static String[] splitIntoTokens(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inWhitespace = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            boolean isWhitespace = Character.isWhitespace(c);

            if (i == 0) {
                inWhitespace = isWhitespace;
            }

            if (isWhitespace == inWhitespace) {
                current.append(c);
            } else {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current = new StringBuilder();
                }
                current.append(c);
                inWhitespace = isWhitespace;
            }
        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens.toArray(new String[0]);
    }
}
