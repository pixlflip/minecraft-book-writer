package com.bookwriter.web;

import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pulls the readable text out of a web page: finds the element that holds the article, drops
 * navigation, ads and other page furniture, and renders what is left as plain text with blank
 * lines between paragraphs.
 */
public final class ArticleExtractor {
    private static final String JUNK_TAGS = "script, style, noscript, template, svg, canvas, iframe, object, embed, form, button, input, "
        + "select, textarea, nav, footer, aside, dialog, [hidden], [aria-hidden=true]";
    private static final Pattern JUNK_NAMES = Pattern.compile(
        "(^|[\\s_-])(nav|navbar|navigation|menu|sidebar|footer|comments?|share|sharing|social|related|recommended|promo|advert|"
            + "advertisement|ads?|banner|cookie|consent|newsletter|subscribe|popup|modal|breadcrumbs?|toolbar|masthead)([\\s_-]|$)",
        Pattern.CASE_INSENSITIVE);
    private static final Set<String> BLOCK_TAGS = Set.of(
        "p", "div", "section", "article", "main", "header", "blockquote", "pre", "ul", "ol", "li", "table", "tr", "h1", "h2", "h3", "h4",
        "h5", "h6", "figure", "figcaption", "dl", "dt", "dd", "hr", "address");
    private static final Set<String> PARAGRAPH_TAGS = Set.of("p", "blockquote", "pre", "ul", "ol", "table", "h1", "h2", "h3", "h4", "h5", "h6", "figure", "dl", "hr");

    private ArticleExtractor() {
    }

    /** @param title the page or article title, possibly empty */
    public record Article(String title, String text) {
    }

    public static Article extract(Document document) {
        String title = document.title().strip();
        Element body = document.body();
        if (body == null) {
            return new Article(title, "");
        }

        body.select(JUNK_TAGS).remove();
        int bodyLength = body.text().length();
        for (Element element : body.select("[class], [id]")) {
            // Never throw away a container that plainly holds the content.
            if (element.parent() == null || element == body || element.normalName().equals("article") || element.normalName().equals("main")) {
                continue;
            }
            // ...nor one that holds most of the page, whatever it is called ("content-with-sidebar").
            if ((JUNK_NAMES.matcher(element.className()).find() || JUNK_NAMES.matcher(element.id()).find())
                && element.select("article, main").isEmpty() && element.text().length() * 2 < bodyLength) {
                element.remove();
            }
        }

        Element content = findContent(body);
        String text = render(content);

        // Lead with the headline unless the content already starts with it.
        Element heading = content.selectFirst("h1");
        if (heading == null) {
            heading = body.selectFirst("h1");
            String headline = heading != null ? heading.text().strip() : title;
            if (!headline.isEmpty() && !text.startsWith(headline)) {
                text = headline + "\n\n" + text;
            }
        }
        if (heading != null && !heading.text().isBlank()) {
            title = heading.text().strip();
        }
        return new Article(title, text.strip());
    }

    /** Picks the element whose own paragraphs hold the most text. */
    private static Element findContent(Element body) {
        Element best = body;
        int bestScore = 0;
        for (Element candidate : body.select("article, main, [role=main], div, section, td")) {
            int score = 0;
            for (Element child : candidate.children()) {
                String name = child.normalName();
                if (name.equals("p") || name.equals("blockquote") || name.equals("pre") || name.equals("ul") || name.equals("ol")) {
                    int length = child.text().length();
                    // Short fragments are usually captions, bylines and link lists.
                    score += length >= 40 ? length : length / 4;
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        // Articles are often split across sibling sections; if a semantic container wraps the
        // winner and has a reasonable amount more text, prefer the container.
        Element semantic = body.selectFirst("article, main, [role=main]");
        if (semantic != null && semantic != best && semantic.text().length() >= Math.max(200, best.text().length())
            && (best == body || isAncestor(semantic, best))) {
            return semantic;
        }
        if (bestScore < 200 && semantic != null && semantic.text().length() > 200) {
            return semantic;
        }
        return best;
    }

    private static boolean isAncestor(Element ancestor, Element element) {
        for (Element parent = element.parent(); parent != null; parent = parent.parent()) {
            if (parent == ancestor) {
                return true;
            }
        }
        return false;
    }

    private static String render(Element root) {
        StringBuilder out = new StringBuilder();
        append(root, out, false);
        return out.toString().replaceAll("[ \\u00A0]+\n", "\n").replaceAll("\n{3,}", "\n\n").strip();
    }

    private static void append(Node node, StringBuilder out, boolean preformatted) {
        if (node instanceof TextNode textNode) {
            if (preformatted) {
                out.append(textNode.getWholeText());
                return;
            }
            String text = textNode.getWholeText().replaceAll("\\s+", " ");
            if (text.equals(" ") && (out.isEmpty() || out.charAt(out.length() - 1) == '\n' || out.charAt(out.length() - 1) == ' ')) {
                return;
            }
            if (text.startsWith(" ") && (out.isEmpty() || Character.isWhitespace(out.charAt(out.length() - 1)))) {
                text = text.substring(1);
            }
            out.append(text);
            return;
        }
        if (!(node instanceof Element element)) {
            return;
        }

        String name = element.normalName();
        if (name.equals("br")) {
            out.append('\n');
            return;
        }
        if (name.equals("hr")) {
            breakBlock(out, true);
            out.append("* * *");
            breakBlock(out, true);
            return;
        }

        boolean block = BLOCK_TAGS.contains(name);
        boolean paragraph = PARAGRAPH_TAGS.contains(name) && !(name.matches("ul|ol") && element.parent() != null && element.parent().normalName().equals("li"));
        if (block) {
            breakBlock(out, paragraph);
        }
        if (name.equals("li")) {
            out.append("- ");
        } else if (name.equals("td") || name.equals("th")) {
            if (element.elementSiblingIndex() > 0) {
                out.append(" | ");
            }
        }
        for (Node child : element.childNodes()) {
            append(child, out, preformatted || name.equals("pre"));
        }
        if (block) {
            breakBlock(out, paragraph);
        }
    }

    /** Ends the current line, and for paragraph-level blocks leaves one blank line. */
    private static void breakBlock(StringBuilder out, boolean paragraph) {
        while (!out.isEmpty() && out.charAt(out.length() - 1) == ' ') {
            out.setLength(out.length() - 1);
        }
        if (out.isEmpty()) {
            return;
        }
        int newlines = 0;
        for (int i = out.length() - 1; i >= 0 && out.charAt(i) == '\n'; i--) {
            newlines++;
        }
        for (int wanted = paragraph ? 2 : 1; newlines < wanted; newlines++) {
            out.append('\n');
        }
    }
}
