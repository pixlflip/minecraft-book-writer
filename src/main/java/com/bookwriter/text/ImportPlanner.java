package com.bookwriter.text;

import java.util.ArrayList;
import java.util.List;

/** Works out what a book will look like after an import, without touching the book. */
public final class ImportPlanner {
    private ImportPlanner() {
    }

    /**
     * @param pages          the complete book after the import
     * @param firstNewPage   index of the first imported page in {@code pages}
     * @param importedPages  how many pages were added
     * @param replacedPages  how many existing pages with text are discarded
     * @param remainder      text that did not fit, starting at a page boundary; empty if all fit
     * @param remainderPages how many pages {@code remainder} needs
     */
    public record Plan(List<String> pages, int firstNewPage, int importedPages, int replacedPages, String remainder, int remainderPages) {
        public boolean hasRemainder() {
            return remainderPages > 0;
        }
    }

    /**
     * @param existing    current book pages
     * @param currentPage index of the page being viewed
     * @param text        the text {@code newPages} was paginated from
     * @param newPages    result of {@link BookPaginator#paginate}
     */
    public static Plan plan(List<String> existing, int currentPage, String text, List<BookPaginator.Page> newPages, ImportMode mode) {
        List<String> base = new ArrayList<>(existing);
        int replaced = 0;
        int at;

        switch (mode) {
            case REPLACE -> {
                replaced = (int) base.stream().filter(page -> !page.isBlank()).count();
                base.clear();
                at = 0;
            }
            case APPEND -> {
                while (!base.isEmpty() && base.get(base.size() - 1).isBlank()) {
                    base.remove(base.size() - 1);
                }
                at = base.size();
            }
            default -> {
                int page = Math.max(0, Math.min(currentPage, base.size() - 1));
                if (base.isEmpty()) {
                    at = 0;
                } else if (base.get(page).isBlank()) {
                    base.remove(page);
                    at = page;
                } else {
                    at = page + 1;
                }
            }
        }

        int capacity = Math.max(0, BookPaginator.MAX_PAGES - base.size());
        int take = Math.min(capacity, newPages.size());
        List<String> result = new ArrayList<>(base.subList(0, at));
        for (int i = 0; i < take; i++) {
            result.add(newPages.get(i).text());
        }
        result.addAll(base.subList(at, base.size()));
        if (result.isEmpty()) {
            result.add("");
        }

        String remainder = take < newPages.size() ? text.substring(newPages.get(take).start()) : "";
        return new Plan(result, at, take, replaced, remainder, newPages.size() - take);
    }
}
