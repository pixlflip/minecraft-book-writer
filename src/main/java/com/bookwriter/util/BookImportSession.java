package com.bookwriter.util;

import java.util.List;

/** One-level import undo belongs to one open book screen, never another book. */
public final class BookImportSession {
    public record Snapshot(List<String> pages, int currentPage) {
        public Snapshot {
            pages = List.copyOf(pages);
        }
    }

    private Snapshot undo;

    public void remember(List<String> pages, int currentPage) {
        undo = new Snapshot(pages, currentPage);
    }

    public boolean canUndo() {
        return undo != null;
    }

    public Snapshot undo() {
        Snapshot snapshot = undo;
        undo = null;
        return snapshot;
    }

    /** Only trailing empty editor placeholders are omitted; existing content is not repaginated. */
    public static int appendIndex(List<String> pages) {
        int end = pages.size();
        while (end > 0 && pages.get(end - 1).isEmpty()) end--;
        return end;
    }
}
