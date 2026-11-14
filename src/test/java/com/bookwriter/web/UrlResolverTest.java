package com.bookwriter.web;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UrlResolverTest {
    @Test
    void recognizesLinks() {
        assertTrue(UrlResolver.looksLikeUrl("https://example.com/a?b=c"));
        assertTrue(UrlResolver.looksLikeUrl("  HTTP://example.com  "));
        assertTrue(UrlResolver.looksLikeUrl("example.com/page"));
        assertFalse(UrlResolver.looksLikeUrl("just some words"));
        assertFalse(UrlResolver.looksLikeUrl("see https://example.com for more"));
        assertFalse(UrlResolver.looksLikeUrl("file:///etc/passwd"));
        assertFalse(UrlResolver.looksLikeUrl("hello"));
        assertFalse(UrlResolver.looksLikeUrl(""));
        assertFalse(UrlResolver.looksLikeUrl(null));
    }

    @Test
    void addsMissingScheme() {
        assertEquals("https://example.com/page", UrlResolver.resolve("example.com/page").toString());
    }

    @Test
    void leavesOrdinaryPagesAlone() {
        assertEquals("https://example.com/a/b?c=d", UrlResolver.resolve("https://example.com/a/b?c=d").toString());
        assertEquals("https://pastebin.com/raw/AbC123", UrlResolver.resolve("https://pastebin.com/raw/AbC123").toString());
    }

    @Test
    void rewritesToPlainTextVersions() {
        assertEquals("https://pastebin.com/raw/AbC123", UrlResolver.resolve("https://pastebin.com/AbC123").toString());
        assertEquals("https://docs.google.com/document/d/1a-B_c/export?format=txt",
            UrlResolver.resolve("https://docs.google.com/document/d/1a-B_c/edit?usp=sharing").toString());
        assertEquals("https://gist.githubusercontent.com/someone/0123abcd/raw", UrlResolver.resolve("https://gist.github.com/someone/0123abcd").toString());
        assertEquals("https://raw.githubusercontent.com/owner/repo/main/docs/story.txt",
            UrlResolver.resolve("https://github.com/owner/repo/blob/main/docs/story.txt").toString());
    }

    @Test
    void rejectsNonLinks() {
        assertThrows(IllegalArgumentException.class, () -> UrlResolver.resolve("not a link"));
        assertThrows(IllegalArgumentException.class, () -> UrlResolver.resolve("ftp://example.com/file"));
        assertThrows(IllegalArgumentException.class, () -> UrlResolver.resolve(null));
    }
}
