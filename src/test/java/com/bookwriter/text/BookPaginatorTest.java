package com.bookwriter.text;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookPaginatorTest {
    /** Same algorithm as Minecraft's TextHandler.wrapLines, with every character one unit wide. */
    static BookPaginator.LineWrapper wrapper(int width) {
        return wrapper(width, false);
    }

    static BookPaginator.LineWrapper wrapper(int width, boolean zeroWidthZ) {
        return (text, consumer) -> {
            int i = 0;
            while (i < text.length()) {
                int lastSpace = -1;
                int end = -1;
                int used = 0;
                boolean nonEmpty = false;
                for (int k = i; k < text.length(); k++) {
                    char c = text.charAt(k);
                    if (c == '\n') {
                        end = k;
                        break;
                    }
                    if (c == ' ') {
                        lastSpace = k;
                    }
                    int charWidth = zeroWidthZ && c == 'z' ? 0 : 1;
                    used += charWidth;
                    if (nonEmpty && used > width) {
                        end = lastSpace != -1 ? lastSpace : k;
                        break;
                    }
                    nonEmpty |= charWidth != 0;
                }
                if (end == -1) {
                    consumer.accept(i, text.length());
                    return;
                }
                consumer.accept(i, end);
                char c = text.charAt(end);
                i = c == '\n' || c == ' ' ? end + 1 : end;
            }
        };
    }

    static int lineCount(String page, BookPaginator.LineWrapper wrapper) {
        int[] count = {0};
        wrapper.wrap(page, (start, end) -> count[0]++);
        // The game counts a trailing line break as one more (empty) line.
        return count[0] + (page.endsWith("\n") ? 1 : 0);
    }

    static List<String> texts(List<BookPaginator.Page> pages) {
        return pages.stream().map(BookPaginator.Page::text).toList();
    }

    @Test
    void emptyTextHasNoPages() {
        assertTrue(BookPaginator.paginate("", wrapper(10)).isEmpty());
        assertTrue(BookPaginator.paginate(null, wrapper(10)).isEmpty());
    }

    @Test
    void shortTextIsOnePageUnchanged() {
        assertEquals(List.of("Hello world\nsecond line"), texts(BookPaginator.paginate("Hello world\nsecond line", wrapper(20))));
    }

    @Test
    void softWrapsDoNotBecomeNewlines() {
        String text = "one two three four five six";
        List<BookPaginator.Page> pages = BookPaginator.paginate(text, wrapper(9));
        assertEquals(List.of(text), texts(pages));
    }

    /** Before 1.21.6 the editor refuses pages of 1024 characters, so those builds paginate at 1023. */
    @Test
    void honoursALowerPageLengthLimit() {
        String text = "x".repeat(5000);
        BookPaginator.LineWrapper wrapper = wrapper(100000, true);
        List<BookPaginator.Page> pages = BookPaginator.paginate(text, wrapper, 1023);
        assertTrue(pages.size() >= 5);
        int total = 0;
        for (BookPaginator.Page page : pages) {
            assertTrue(page.text().length() <= 1023, "page too long: " + page.text().length());
            total += page.text().length();
        }
        assertTrue(total == text.length(), "characters lost or duplicated");
    }

    @Test
    void everyPageFitsTheGameLimits() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 2000; i++) {
            text.append("word").append(i).append(i % 37 == 0 ? "\n\n" : " ");
        }
        BookPaginator.LineWrapper wrapper = wrapper(19);
        List<BookPaginator.Page> pages = BookPaginator.paginate(text.toString().strip(), wrapper);

        assertTrue(pages.size() > 10);
        for (BookPaginator.Page page : pages) {
            assertTrue(lineCount(page.text(), wrapper) <= BookPaginator.MAX_LINES_PER_PAGE, "too many lines: " + page.text());
            assertTrue(page.text().length() <= BookPaginator.MAX_PAGE_LENGTH);
            assertFalse(page.text().isBlank());
            assertFalse(page.text().startsWith("\n"));
            assertFalse(page.text().endsWith("\n"));
        }
    }

    @Test
    void noTextIsLostOrReordered() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 1500; i++) {
            text.append("w").append(i).append(i % 11 == 0 ? "\n" : " ");
        }
        String source = text.toString().strip();
        List<BookPaginator.Page> pages = BookPaginator.paginate(source, wrapper(15));

        String joined = String.join(" ", texts(pages));
        assertEquals(source.replaceAll("\\s+", " "), joined.replaceAll("\\s+", " "));
    }

    @Test
    void pageStartsPointIntoTheSource() {
        String source = "aaaa bbbb cccc dddd eeee ffff gggg hhhh iiii jjjj kkkk llll mmmm nnnn oooo pppp qqqq";
        List<BookPaginator.Page> pages = BookPaginator.paginate(source, wrapper(4));
        assertEquals(2, pages.size());
        assertEquals(0, pages.get(0).start());
        assertEquals("oooo pppp qqqq", source.substring(pages.get(1).start()));
        assertEquals("oooo pppp qqqq", pages.get(1).text());
    }

    @Test
    void longWordsAreBrokenAcrossLines() {
        String word = "x".repeat(100);
        List<BookPaginator.Page> pages = BookPaginator.paginate(word, wrapper(5));
        assertEquals(List.of("x".repeat(70), "x".repeat(30)), texts(pages));
    }

    @Test
    void blankLinesAreKeptInsideAPageButNotAtItsEdges() {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < 13; i++) {
            lines.add("line" + i);
        }
        // 13 lines, then a paragraph break that lands exactly on the page boundary.
        String source = String.join("\n", lines) + "\n\n\nnext page";
        List<BookPaginator.Page> pages = BookPaginator.paginate(source, wrapper(20));
        assertEquals(List.of(String.join("\n", lines), "next page"), texts(pages));

        assertEquals(List.of("a\n\nb"), texts(BookPaginator.paginate("a\n\nb", wrapper(20))));
    }

    @Test
    void pagesOverTheCharacterLimitAreSplitEarly() {
        // 14 lines of 100 characters would be 1,413 characters with separators.
        String source = String.join("\n", java.util.Collections.nCopies(14, "y".repeat(100)));
        List<BookPaginator.Page> pages = BookPaginator.paginate(source, wrapper(100));
        assertEquals(2, pages.size());
        assertEquals(10 * 100 + 9, pages.get(0).text().length());
    }

    @Test
    void aSingleOverlongLineIsForceSplit() {
        // Zero-width characters never wrap, so this is one 3,000 character "line".
        String source = "z".repeat(3000);
        List<BookPaginator.Page> pages = BookPaginator.paginate(source, wrapper(10, true));
        assertEquals(3, pages.size());
        assertEquals(source, String.join("", texts(pages)));
        assertEquals(2048, pages.get(2).start());
    }
}
