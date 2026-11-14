package com.bookwriter.text;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TextCleanerTest {
    @Test
    void normalizesLineEndings() {
        assertEquals("a\nb\nc\nd", TextCleaner.clean("a\r\nb\rc d", false));
    }

    @Test
    void removesCharactersBooksReject() {
        assertEquals("red  text", TextCleaner.clean("§cred\u0000\t\u007Ftext", false));
    }

    @Test
    void trimsLeadingBlankLinesAndTrailingWhitespace() {
        assertEquals("  indented\nlast", TextCleaner.clean("\n \n  indented\nlast \n\n", false));
    }

    @Test
    void tidyFixesSpacesInvisiblesAndBlankRuns() {
        assertEquals("a b\n\nc", TextCleaner.clean("a b​  \n\n\n\n﻿c", true));
    }

    @Test
    void withoutTidyKeepsTextAsWritten() {
        assertEquals("a b\n\n\n\nc", TextCleaner.clean("a b\n\n\n\nc", false));
    }

    @Test
    void emptyInput() {
        assertEquals("", TextCleaner.clean(null, true));
        assertEquals("", TextCleaner.clean(" \n\t\n", true));
    }
}
