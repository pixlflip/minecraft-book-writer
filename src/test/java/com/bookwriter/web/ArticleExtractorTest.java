package com.bookwriter.web;

import org.jsoup.Jsoup;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArticleExtractorTest {
    private static final String PARAGRAPH_1 = "The first paragraph of the story is long enough to count as real content for the extractor.";
    private static final String PARAGRAPH_2 = "The second paragraph carries on, with a <a href='/x'>link</a> and <em>emphasis</em> in the middle of it.";

    private static final String PAGE = """
        <html><head><title>The Story | Example Site</title><style>p { color: red }</style></head>
        <body class="has-sidebar">
          <nav><a href="/">Home</a> <a href="/about">About</a></nav>
          <div class="cookie-banner">We use cookies. Accept all cookies to continue reading this site.</div>
          <div id="wrapper">
            <article>
              <h1>The Story</h1>
              <p>%s</p>
              <p>%s</p>
              <h2>A Heading</h2>
              <ul><li>First item</li><li>Second item</li></ul>
              <p>Line one<br>line two of the closing paragraph, which also needs to be reasonably long.</p>
              <div class="share-buttons">Share this on every social network you can think of right now</div>
            </article>
            <div class="sidebar"><p>Related: ten other stories you will not believe, number seven is a shock.</p></div>
          </div>
          <footer>Copyright Example Site</footer>
          <script>alert("nope")</script>
        </body></html>
        """.formatted(PARAGRAPH_1, PARAGRAPH_2);

    @Test
    void keepsTheArticleAndDropsTheRest() {
        ArticleExtractor.Article article = ArticleExtractor.extract(Jsoup.parse(PAGE));

        assertEquals("The Story", article.title());
        assertEquals("""
            The Story

            %s

            The second paragraph carries on, with a link and emphasis in the middle of it.

            A Heading

            - First item
            - Second item

            Line one
            line two of the closing paragraph, which also needs to be reasonably long.""".formatted(PARAGRAPH_1), article.text());
    }

    @Test
    void findsContentWithoutSemanticMarkup() {
        String html = "<html><head><title>Plain</title></head><body><div class='top'><a href='/'>Home</a></div>"
            + "<div class='post-body'><p>" + PARAGRAPH_1 + "</p><p>" + PARAGRAPH_1 + "</p></div>"
            + "<div class='links'><p>Short link list</p></div></body></html>";
        ArticleExtractor.Article article = ArticleExtractor.extract(Jsoup.parse(html));

        assertEquals("Plain\n\n" + PARAGRAPH_1 + "\n\n" + PARAGRAPH_1, article.text());
    }

    @Test
    void preservesPreformattedText() {
        String html = "<body><main><p>" + PARAGRAPH_1 + "</p><pre>line 1\n  indented\nline 3</pre></main></body>";
        assertTrue(ArticleExtractor.extract(Jsoup.parse(html)).text().endsWith("line 1\n  indented\nline 3"));
    }

    @Test
    void pageWithNoTextIsEmpty() {
        assertEquals("", ArticleExtractor.extract(Jsoup.parse("<html><body><script>render()</script><div id='root'></div></body></html>")).text());
    }

    @Test
    void parsesPlainTextResponses() throws Exception {
        LinkFetcher.Result result = LinkFetcher.parse("https://example.com/a.txt", "text/plain; charset=utf-8", "﻿café\nline".getBytes(StandardCharsets.UTF_8));
        assertEquals("café\nline", result.text());
        assertEquals("https://example.com/a.txt", result.title());
    }

    @Test
    void parsesHtmlResponsesUsingTheDeclaredCharset() throws Exception {
        byte[] body = ("<html><head><title>T</title></head><body><article><p>café " + PARAGRAPH_1 + "</p></article></body></html>").getBytes(StandardCharsets.ISO_8859_1);
        LinkFetcher.Result result = LinkFetcher.parse("https://example.com/", "text/html; charset=ISO-8859-1", body);
        assertTrue(result.text().contains("café The first"));
        assertEquals("T", result.title());
    }

    @Test
    void rejectsBinaryAndEmptyResponses() {
        LinkFetcher.FetchException binary = assertThrows(LinkFetcher.FetchException.class, () -> LinkFetcher.parse("https://example.com/a.png", "image/png", new byte[] {1, 2, 3}));
        assertEquals("bookwriter.error.not_text", binary.key());

        LinkFetcher.FetchException empty = assertThrows(LinkFetcher.FetchException.class,
            () -> LinkFetcher.parse("https://example.com/", "text/html", "<html><body><div id='app'></div></body></html>".getBytes(StandardCharsets.UTF_8)));
        assertEquals("bookwriter.error.empty", empty.key());
        assertFalse(empty.key().isEmpty());
    }
}
