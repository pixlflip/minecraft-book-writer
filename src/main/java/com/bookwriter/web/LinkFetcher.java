package com.bookwriter.web;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Downloads a link in the background and returns the readable text behind it. */
public final class LinkFetcher {
    /** Pages bigger than this are cut off; a full book is roughly 25,000 characters. */
    static final int MAX_BYTES = 4 * 1024 * 1024;
    private static final Pattern CHARSET = Pattern.compile("charset=\"?([\\w.:-]+)", Pattern.CASE_INSENSITIVE);

    private static HttpClient client;

    private LinkFetcher() {
    }

    /** @param title a name for the content: the article headline, or the link itself */
    public record Result(String title, String text) {
    }

    /** A failure with a translation key ({@code bookwriter.error.*}) the UI can show. */
    public static final class FetchException extends Exception {
        private final String key;
        private final Object[] args;

        FetchException(String key, Object... args) {
            super(key);
            this.key = key;
            this.args = args;
        }

        public String key() {
            return key;
        }

        public Object[] args() {
            return args;
        }
    }

    private static synchronized HttpClient client() {
        if (client == null) {
            client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build();
        }
        return client;
    }

    /**
     * Never blocks; the future completes on a background thread, exceptionally with a
     * {@link FetchException} on any failure.
     */
    public static CompletableFuture<Result> fetch(String link) {
        URI uri;
        try {
            uri = UrlResolver.resolve(link);
        } catch (IllegalArgumentException e) {
            return CompletableFuture.failedFuture(new FetchException("bookwriter.error.bad_link"));
        }

        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(20))
            // Many sites refuse clients that do not look like a browser.
            .header("User-Agent", "Mozilla/5.0 (compatible; BookWriter)")
            .header("Accept", "text/html,application/xhtml+xml,text/plain;q=0.9,*/*;q=0.5")
            .header("Accept-Language", "en,*;q=0.5")
            .GET()
            .build();

        return client().sendAsync(request, HttpResponse.BodyHandlers.ofInputStream()).handle((response, error) -> {
            try {
                if (error != null) {
                    throw translate(error);
                }
                return read(uri, response);
            } catch (FetchException e) {
                throw new CompletionException(e);
            }
        });
    }

    private static FetchException translate(Throwable error) {
        Throwable cause = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        if (cause instanceof FetchException fetchException) {
            return fetchException;
        }
        if (cause instanceof HttpTimeoutException) {
            return new FetchException("bookwriter.error.timeout");
        }
        return new FetchException("bookwriter.error.connect");
    }

    private static Result read(URI uri, HttpResponse<InputStream> response) throws FetchException {
        byte[] body;
        try (InputStream stream = response.body()) {
            if (response.statusCode() >= 400) {
                throw new FetchException(switch (response.statusCode()) {
                    case 401, 403 -> "bookwriter.error.forbidden";
                    case 404, 410 -> "bookwriter.error.not_found";
                    default -> "bookwriter.error.http";
                }, response.statusCode());
            }
            body = stream.readNBytes(MAX_BYTES);
        } catch (IOException e) {
            throw new FetchException("bookwriter.error.connect");
        }

        String contentType = response.headers().firstValue("Content-Type").orElse("text/html").toLowerCase(Locale.ROOT);
        return parse(uri.toString(), contentType, body);
    }

    /** Visible for testing. */
    static Result parse(String url, String contentType, byte[] body) throws FetchException {
        Charset charset = null;
        Matcher matcher = CHARSET.matcher(contentType);
        if (matcher.find()) {
            try {
                charset = Charset.forName(matcher.group(1));
            } catch (RuntimeException ignored) {
                // Unknown charset name: fall through to detection / UTF-8.
            }
        }

        String title;
        String text;
        if (contentType.contains("html") || contentType.contains("xml")) {
            Document document;
            try {
                // With no charset jsoup sniffs the BOM and <meta charset>.
                document = Jsoup.parse(new ByteArrayInputStream(body), charset == null ? null : charset.name(), url);
            } catch (IOException e) {
                throw new FetchException("bookwriter.error.connect");
            }
            ArticleExtractor.Article article = ArticleExtractor.extract(document);
            title = article.title();
            text = article.text();
        } else if (contentType.startsWith("text/") || contentType.contains("json")) {
            title = "";
            text = new String(body, charset == null ? StandardCharsets.UTF_8 : charset);
            if (text.startsWith("\uFEFF")) {
                text = text.substring(1);
            }
        } else {
            throw new FetchException("bookwriter.error.not_text");
        }

        if (text.isBlank()) {
            throw new FetchException("bookwriter.error.empty");
        }
        return new Result(title.isBlank() ? url : title, text);
    }
}
