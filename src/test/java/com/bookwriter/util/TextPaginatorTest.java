package com.bookwriter.util;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Random;
import java.util.function.ToIntFunction;
import static org.junit.jupiter.api.Assertions.*;

class TextPaginatorTest {
    private static final ToIntFunction<String> WIDTH = s -> s.codePointCount(0, s.length()) * 6;

    private TextPaginator.Result paginate(String text) {
        return TextPaginator.paginate(text, WIDTH, 100);
    }

    @Test void emptyInputHasOneEditablePage() {
        assertEquals(List.of(""), paginate(null).pages());
        assertEquals(List.of(""), paginate("").pages());
    }

    @Test void finalLineCannotBecomeFifteenthLine() {
        var result = paginate("x\n".repeat(14) + "last");
        assertEquals(2, result.pages().size());
        assertEquals(14, result.pages().getFirst().split("\n", -1).length);
        assertEquals("last", result.pages().getLast());
    }

    @Test void exactFourteenLinesUseOnePage() {
        assertEquals(1, paginate("x\n".repeat(13) + "x").pages().size());
    }

    @Test void preservesExplicitBlankLinesAndNormalizesEndings() {
        assertEquals(List.of("one\n\ntwo\nthree\n"), paginate("one\r\n\r\ntwo\rthree\n").pages());
    }

    @Test void trailingBlankLineUsesPageLimit() {
        var result = TextPaginator.paginate("x\n".repeat(14), WIDTH, 1);
        assertTrue(result.overflow());
        assertEquals(1, result.pages().size());
    }

    @Test void wrapsWordsWithoutJoiningThemWhenSeparatorDoesNotFit() {
        assertEquals(List.of("a".repeat(19) + "\nb"), paginate("a".repeat(19) + " b").pages());
    }

    @Test void preservesIndentationWhenItFits() {
        assertEquals(List.of("  hello\n\tworld"), paginate("  hello\n\tworld").pages());
    }

    @Test void longWordsAreSplitWithoutDroppingCharacters() {
        String text = "a".repeat(1000);
        var result = paginate(text);
        assertEquals(text, String.join("", result.pages()).replace("\n", ""));
        assertLimits(result, WIDTH);
    }

    @Test void supplementaryUnicodeIsNeverSplit() {
        String text = "😀".repeat(1000);
        var result = paginate(text);
        assertEquals(text, String.join("", result.pages()).replace("\n", ""));
        for (String page : result.pages()) {
            for (String line : page.split("\n", -1)) {
                assertFalse(Character.isHighSurrogate(line.charAt(line.length() - 1)));
                assertFalse(Character.isLowSurrogate(line.charAt(0)));
            }
        }
        assertLimits(result, WIDTH);
    }

    @Test void zeroWidthTextStillRespectsCharacterLimit() {
        var result = TextPaginator.paginate("\u0301".repeat(5000), s -> 0, 100);
        assertFalse(result.overflow());
        assertEquals(5000, result.pages().stream().mapToInt(String::length).sum());
        assertLimits(result, s -> 0);
    }

    @Test void exactCapacityDoesNotOverflowButNextLineDoes() {
        String exact = "x\n".repeat(1399) + "x";
        assertFalse(paginate(exact).overflow());
        assertEquals(100, paginate(exact).pages().size());
        assertTrue(paginate(exact + "\nx").overflow());
    }

    @Test void appendCapacityIsExplicit() {
        assertFalse(TextPaginator.paginate("x", WIDTH, 1).overflow());
        assertTrue(TextPaginator.paginate("x\n".repeat(14) + "x", WIDTH, 1).overflow());
        assertTrue(TextPaginator.paginate("x", WIDTH, 0).overflow());
        assertTrue(TextPaginator.paginate("x", WIDTH, 0).pages().isEmpty());
    }

    @Test void rejectsInvalidLimitsOversizedInputAndUnrenderableGlyphs() {
        assertThrows(IllegalArgumentException.class, () -> TextPaginator.paginate("x", WIDTH, -1));
        assertThrows(IllegalArgumentException.class, () -> TextPaginator.paginate("x", WIDTH, 101));
        assertThrows(IllegalArgumentException.class, () -> paginate("x".repeat(TextPaginator.MAX_INPUT_CHARS + 1)));
        assertThrows(IllegalArgumentException.class, () -> TextPaginator.paginate("x", s -> 115, 100));
    }

    @Test void fontWidthsAreMeasuredRatherThanEstimatedByCharacterCount() {
        ToIntFunction<String> proportional = s -> s.codePoints().map(cp -> cp == 'W' ? 9 : 1).sum();
        var result = TextPaginator.paginate("W".repeat(25), proportional, 100);
        assertEquals(List.of("W".repeat(12) + "\n" + "W".repeat(12) + "\nW"), result.pages());
        assertLimits(result, proportional);
    }

    @Test void pageCharacterBudgetIncludesLineSeparators() {
        var result = TextPaginator.paginate("a".repeat(512) + "\n" + "b".repeat(512), s -> 0, 100);
        assertEquals(List.of("a".repeat(512), "b".repeat(512)), result.pages());
        assertLimits(result, s -> 0);
    }

    @Test void resultPagesAreImmutable() {
        assertThrows(UnsupportedOperationException.class, () -> paginate("x").pages().clear());
    }

    @Test void randomizedInputsNeverExceedAnyPageLimit() {
        Random random = new Random(42);
        String[] tokens = {"hello", " ", "\n", "😀", "\u0301", "longword".repeat(30), "\t"};
        for (int trial = 0; trial < 300; trial++) {
            StringBuilder text = new StringBuilder();
            for (int i = 0; i < 100; i++) text.append(tokens[random.nextInt(tokens.length)]);
            assertLimits(paginate(text.toString()), WIDTH);
        }
    }

    private static void assertLimits(TextPaginator.Result result, ToIntFunction<String> width) {
        assertTrue(result.pages().size() <= 100);
        for (String page : result.pages()) {
            assertTrue(page.length() <= 1024, "Page exceeds character limit");
            String[] lines = page.split("\n", -1);
            assertTrue(lines.length <= 14, "Page exceeds line limit");
            for (String line : lines) assertTrue(width.applyAsInt(line) <= 114, "Line exceeds pixel width");
        }
    }
}
