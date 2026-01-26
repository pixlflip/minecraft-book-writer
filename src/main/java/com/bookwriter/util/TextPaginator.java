package com.bookwriter.util;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class for splitting large text into pages suitable for Minecraft books.
 * Uses the game's TextRenderer to accurately calculate pixel widths for proper line wrapping.
 */
public class TextPaginator {
    // Minecraft book visual limits
    public static final int MAX_LINE_WIDTH_PIXELS = 114;
    public static final int MAX_LINES_PER_PAGE = 14;
    public static final int MAX_PAGES = 100;

    /**
     * Splits the input text into pages, respecting pixel width and line limits.
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

        TextRenderer textRenderer = MinecraftClient.getInstance().textRenderer;

        // Use a state holder class to work around lambda limitations
        PaginationState state = new PaginationState();

        String[] tokens = splitIntoTokens(text);

        for (String token : tokens) {
            if (token.equals("\n")) {
                // Explicit newline - commit current line and start new one
                commitLine(state, pages);
            } else if (token.trim().isEmpty() && token.contains(" ")) {
                // Whitespace token
                String testLine = state.currentLine.toString() + token;
                if (textRenderer.getWidth(testLine) <= MAX_LINE_WIDTH_PIXELS) {
                    state.currentLine.append(token);
                }
                // If whitespace doesn't fit, we just skip it (natural word wrap point)
            } else {
                // Regular word token
                addWord(token, state, pages, textRenderer);
            }

            if (pages.size() >= MAX_PAGES) {
                return pages;
            }
        }

        // Don't forget the last line and page
        if (state.currentLine.length() > 0) {
            state.currentPage.append(state.currentLine);
        }
        if (state.currentPage.length() > 0) {
            pages.add(state.currentPage.toString());
        }

        // Ensure at least one page exists
        if (pages.isEmpty()) {
            pages.add("");
        }

        return pages;
    }

    private static void addWord(String word, PaginationState state, List<String> pages, TextRenderer textRenderer) {
        String testLine = state.currentLine.toString() + word;

        if (textRenderer.getWidth(testLine) <= MAX_LINE_WIDTH_PIXELS) {
            // Word fits on current line
            state.currentLine.append(word);
        } else {
            // Word doesn't fit - need to wrap
            // First commit current line if it has content
            if (state.currentLine.length() > 0) {
                commitLine(state, pages);
                if (pages.size() >= MAX_PAGES) return;
            }

            // Now try to add the word to the new empty line
            if (textRenderer.getWidth(word) <= MAX_LINE_WIDTH_PIXELS) {
                // Word fits on a fresh line
                state.currentLine.append(word);
            } else {
                // Word is too long even for an empty line - break it character by character
                for (char c : word.toCharArray()) {
                    String test = state.currentLine.toString() + c;
                    if (textRenderer.getWidth(test) > MAX_LINE_WIDTH_PIXELS) {
                        commitLine(state, pages);
                        if (pages.size() >= MAX_PAGES) return;
                    }
                    state.currentLine.append(c);
                }
            }
        }
    }

    private static void commitLine(PaginationState state, List<String> pages) {
        if (state.currentLineCount >= MAX_LINES_PER_PAGE) {
            // Page is full, start new page
            pages.add(state.currentPage.toString());
            state.currentPage = new StringBuilder();
            state.currentLineCount = 0;
        }

        state.currentPage.append(state.currentLine);
        state.currentPage.append("\n");
        state.currentLineCount++;
        state.currentLine = new StringBuilder();
    }

    /**
     * State holder for pagination to avoid lambda capture issues
     */
    private static class PaginationState {
        StringBuilder currentPage = new StringBuilder();
        StringBuilder currentLine = new StringBuilder();
        int currentLineCount = 0;
    }

    /**
     * Splits text into tokens: words, whitespace runs, and newlines.
     */
    private static String[] splitIntoTokens(String text) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inWhitespace = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);

            // Treat newlines as their own tokens
            if (c == '\n') {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current = new StringBuilder();
                }
                tokens.add("\n");
                inWhitespace = false;
                continue;
            }

            boolean isWhitespace = Character.isWhitespace(c);

            if (current.length() == 0) {
                inWhitespace = isWhitespace;
                current.append(c);
            } else if (isWhitespace == inWhitespace) {
                current.append(c);
            } else {
                tokens.add(current.toString());
                current = new StringBuilder();
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
