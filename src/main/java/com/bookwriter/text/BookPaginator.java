package com.bookwriter.text;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits text into book pages.
 *
 * <p>Line wrapping is delegated to a {@link LineWrapper}. In game that is Minecraft's own text
 * wrapper, so pages break exactly where the book screen would break them. Unlike the previous
 * implementation no newlines are inserted at soft wraps, which keeps pasted pages editable: the
 * game simply re-wraps them as you type.
 */
public final class BookPaginator {
    /** Lines that fit on one page of the book edit screen. */
    public static final int MAX_LINES_PER_PAGE = 14;
    /** Characters the game accepts per page. */
    public static final int MAX_PAGE_LENGTH = 1024;
    /** Pages the game accepts per book. */
    public static final int MAX_PAGES = 100;

    private BookPaginator() {
    }

    /** Wraps text into display lines, greedily and independently of what precedes a line. */
    @FunctionalInterface
    public interface LineWrapper {
        /**
         * Reports each display line of {@code text} as a {@code [start, end)} range. The character
         * a line was broken at (a space or newline) belongs to neither line.
         */
        void wrap(String text, LineConsumer consumer);
    }

    @FunctionalInterface
    public interface LineConsumer {
        void accept(int start, int end);
    }

    /**
     * One page of output.
     *
     * @param text  the page contents
     * @param start offset of this page in the paginated text, so that
     *              {@code source.substring(start)} is "everything from this page on"
     */
    public record Page(String text, int start) {
    }

    /** Paginates {@code text} into as many pages as it needs; the caller applies the book limit. */
    public static List<Page> paginate(String text, LineWrapper wrapper) {
        return paginate(text, wrapper, MAX_PAGE_LENGTH);
    }

    /**
     * As {@link #paginate(String, LineWrapper)}, with a different per-page character limit.
     * Before 1.21.6 the book editor only accepts pages shorter than 1024 characters.
     */
    public static List<Page> paginate(String text, LineWrapper wrapper, int maxPageLength) {
        List<Page> pages = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return pages;
        }

        int offset = 0;
        while (offset < text.length()) {
            // Normally a single pass. We only come round again after force-splitting a "line"
            // that is longer than a whole page (possible only with zero-width characters).
            offset = paginateFrom(text, offset, wrapper, pages, maxPageLength);
        }
        return pages;
    }

    private static int paginateFrom(String text, int offset, LineWrapper wrapper, List<Page> pages, int maxPageLength) {
        String rest = text.substring(offset);
        List<int[]> lines = new ArrayList<>();
        wrapper.wrap(rest, (start, end) -> lines.add(new int[] {start, end}));

        int i = 0;
        while (i < lines.size()) {
            // A page never starts with blank lines.
            while (i < lines.size() && isBlank(rest, lines.get(i))) {
                i++;
            }
            if (i >= lines.size()) {
                break;
            }

            int first = i;
            int pageStart = lines.get(first)[0];
            int last = -1;
            while (i < lines.size() && i - first < MAX_LINES_PER_PAGE && lines.get(i)[1] - pageStart <= maxPageLength) {
                last = i++;
            }

            if (last < 0) {
                // Even one line is over the character limit: cut it and re-wrap what is left.
                int cut = pageStart + maxPageLength;
                if (Character.isHighSurrogate(rest.charAt(cut - 1))) {
                    cut--;
                }
                pages.add(new Page(rest.substring(pageStart, cut), offset + pageStart));
                return offset + cut;
            }

            // ...and never ends with them either.
            while (last > first && isBlank(rest, lines.get(last))) {
                last--;
            }
            pages.add(new Page(rest.substring(pageStart, lines.get(last)[1]).stripTrailing(), offset + pageStart));
        }
        return text.length();
    }

    private static boolean isBlank(String text, int[] line) {
        for (int i = line[0]; i < line[1]; i++) {
            if (!Character.isWhitespace(text.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
