package com.bookwriter.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.ToIntFunction;

/** Font-aware pagination, independent of the Minecraft runtime for regression testing. */
public final class TextPaginator {
    public static final int MAX_LINE_WIDTH_PIXELS = 114;
    public static final int MAX_LINES_PER_PAGE = 14;
    public static final int MAX_CHARS_PER_PAGE = 1024;
    public static final int MAX_PAGES = 100;
    public static final int MAX_INPUT_CHARS = 1_000_000;

    private TextPaginator() {}

    /** Overflow is explicit; callers must not silently apply a partial result. */
    public record Result(List<String> pages, boolean overflow) {
        public Result {
            pages = List.copyOf(pages);
        }
    }

    public static Result paginate(String text, ToIntFunction<String> width, int pageLimit) {
        Objects.requireNonNull(width, "width");
        if (pageLimit < 0 || pageLimit > MAX_PAGES) {
            throw new IllegalArgumentException("Page limit must be between 0 and 100");
        }
        if (text == null || text.isEmpty()) {
            return new Result(pageLimit == 0 ? List.of() : List.of(""), false);
        }
        if (text.length() > MAX_INPUT_CHARS) {
            throw new IllegalArgumentException("Input exceeds the clipboard safety limit");
        }
        text = text.replace("\r\n", "\n").replace('\r', '\n');
        State state = new State(width, pageLimit);
        for (int start = 0; start < text.length() && !state.overflow;) {
            int cp = text.codePointAt(start);
            if (cp == '\n') {
                state.commitLine(false);
                start++;
            } else if (Character.isWhitespace(cp)) {
                state.addSpace(new String(Character.toChars(cp)));
                start += Character.charCount(cp);
            } else {
                int end = start + Character.charCount(cp);
                while (end < text.length() && !Character.isWhitespace(text.codePointAt(end))) {
                    end += Character.charCount(text.codePointAt(end));
                }
                state.addWord(text.substring(start, end));
                start = end;
            }
        }
        if (!state.overflow) {
            // The last line, including a deliberate trailing blank line, uses the same limits.
            state.commitLine(false);
            state.flushPage();
        }
        return new Result(state.pages, state.overflow);
    }

    private static final class State {
        private final ToIntFunction<String> width;
        private final int pageLimit;
        private final List<String> pages = new ArrayList<>();
        private final List<String> lines = new ArrayList<>();
        private final StringBuilder line = new StringBuilder();
        private int pageChars;
        private boolean overflow;

        private State(ToIntFunction<String> width, int pageLimit) {
            this.width = width;
            this.pageLimit = pageLimit;
        }

        private boolean fits(String value) {
            return value.length() <= MAX_CHARS_PER_PAGE && width.applyAsInt(value) <= MAX_LINE_WIDTH_PIXELS;
        }

        private void addSpace(String space) {
            if (fits(line + space)) {
                line.append(space);
            } else {
                // Commit the word before dropping a separator at a natural wrap point.
                commitLine(true);
            }
        }

        private void addWord(String word) {
            if (fits(line + word)) {
                line.append(word);
                return;
            }
            if (!line.isEmpty()) {
                commitLine(true);
            }
            if (overflow) return;
            if (fits(word)) {
                line.append(word);
                return;
            }
            // Never split a supplementary Unicode character between lines or pages.
            for (int offset = 0; offset < word.length() && !overflow;) {
                int cp = word.codePointAt(offset);
                String character = new String(Character.toChars(cp));
                if (!fits(character)) {
                    throw new IllegalArgumentException("A glyph is wider than a book line");
                }
                if (!fits(line + character)) {
                    commitLine(true);
                }
                if (!overflow) line.append(character);
                offset += Character.charCount(cp);
            }
        }

        private void commitLine(boolean wrapped) {
            if (overflow) return;
            String value = wrapped ? line.toString().stripTrailing() : line.toString();
            int addedChars = value.length() + (lines.isEmpty() ? 0 : 1);
            if (lines.size() == MAX_LINES_PER_PAGE || pageChars + addedChars > MAX_CHARS_PER_PAGE) {
                flushPage();
                addedChars = value.length();
            }
            if (pages.size() >= pageLimit) {
                overflow = true;
                return;
            }
            lines.add(value);
            pageChars += addedChars;
            line.setLength(0);
        }

        private void flushPage() {
            if (!lines.isEmpty()) {
                pages.add(String.join("\n", lines));
                lines.clear();
                pageChars = 0;
            }
        }
    }
}
