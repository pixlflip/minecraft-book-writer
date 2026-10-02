package com.bookwriter.util;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class BookImportSessionTest {
    @Test void undoIsAnIndependentOneShotSnapshot() {
        var session = new BookImportSession();
        var pages = new ArrayList<>(List.of("old", "text"));
        session.remember(pages, 1);
        pages.clear();
        assertTrue(session.canUndo());
        var snapshot = session.undo();
        assertEquals(List.of("old", "text"), snapshot.pages());
        assertEquals(1, snapshot.currentPage());
        assertFalse(session.canUndo());
        assertNull(session.undo());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.pages().clear());
    }

    @Test void latestImportReplacesPreviousUndoAndSessionsAreIsolated() {
        var first = new BookImportSession();
        var second = new BookImportSession();
        first.remember(List.of("a"), 0);
        first.remember(List.of("b"), 0);
        assertEquals(List.of("b"), first.undo().pages());
        assertFalse(second.canUndo());
    }

    @Test void appendPreservesInternalBlankPagesAndAllNonemptyPages() {
        assertEquals(3, BookImportSession.appendIndex(List.of("first", "", "last", "", "")));
        assertEquals(0, BookImportSession.appendIndex(List.of("", "")));
        assertEquals(2, BookImportSession.appendIndex(List.of("text", " ")));
    }
}
