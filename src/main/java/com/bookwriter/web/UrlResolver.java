package com.bookwriter.web;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns what the user typed or pasted into the URL that actually serves the text. */
public final class UrlResolver {
    private static final Pattern BARE_HOST = Pattern.compile("^[\\w-]+(\\.[\\w-]+)+(:\\d+)?(/\\S*)?$");
    private static final Pattern PASTEBIN = Pattern.compile("^/([A-Za-z0-9]+)$");
    private static final Pattern GOOGLE_DOC = Pattern.compile("^/document/d/([\\w-]+)(/.*)?$");
    private static final Pattern GIST = Pattern.compile("^/([\\w-]+)/([0-9a-f]+)/?$");
    private static final Pattern GITHUB_BLOB = Pattern.compile("^/([^/]+)/([^/]+)/blob/(.+)$");

    private UrlResolver() {
    }

    /** {@return whether {@code text} is a single http(s) link (or a bare domain like example.com/page)} */
    public static boolean looksLikeUrl(String text) {
        if (text == null) {
            return false;
        }
        String trimmed = text.trim();
        if (trimmed.isEmpty() || trimmed.length() > 2048 || trimmed.chars().anyMatch(Character::isWhitespace)) {
            return false;
        }
        String lower = trimmed.toLowerCase(Locale.ROOT);
        return lower.startsWith("http://") || lower.startsWith("https://") || BARE_HOST.matcher(trimmed).matches();
    }

    /**
     * Parses a link and, for sites that have one, swaps it for the plain text version of the same
     * content (raw Pastebin and Gist files, Google Docs text export, raw GitHub files).
     *
     * @throws IllegalArgumentException if this is not a usable http(s) link
     */
    public static URI resolve(String input) {
        String trimmed = input == null ? "" : input.trim();
        if (!looksLikeUrl(trimmed)) {
            throw new IllegalArgumentException("Not a web link");
        }
        if (!trimmed.toLowerCase(Locale.ROOT).startsWith("http")) {
            trimmed = "https://" + trimmed;
        }

        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Not a web link");
        }
        if (uri.getHost() == null) {
            throw new IllegalArgumentException("Not a web link");
        }

        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (host.startsWith("www.")) {
            host = host.substring(4);
        }
        String path = uri.getPath() == null ? "" : uri.getPath();

        Matcher matcher;
        if (host.equals("pastebin.com") && (matcher = PASTEBIN.matcher(path)).matches()) {
            return URI.create("https://pastebin.com/raw/" + matcher.group(1));
        }
        if (host.equals("docs.google.com") && (matcher = GOOGLE_DOC.matcher(path)).matches()) {
            return URI.create("https://docs.google.com/document/d/" + matcher.group(1) + "/export?format=txt");
        }
        if (host.equals("gist.github.com") && (matcher = GIST.matcher(path)).matches()) {
            return URI.create("https://gist.githubusercontent.com/" + matcher.group(1) + "/" + matcher.group(2) + "/raw");
        }
        if (host.equals("github.com") && (matcher = GITHUB_BLOB.matcher(uri.getRawPath())).matches()) {
            return URI.create("https://raw.githubusercontent.com/" + matcher.group(1) + "/" + matcher.group(2) + "/" + matcher.group(3));
        }
        return uri;
    }
}
