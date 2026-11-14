package com.bookwriter.text;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportPlannerTest {
    /** One page per line of {@code text}. */
    static List<BookPaginator.Page> pages(String text) {
        List<BookPaginator.Page> pages = new ArrayList<>();
        int start = 0;
        for (String line : text.split("\n")) {
            pages.add(new BookPaginator.Page(line, start));
            start += line.length() + 1;
        }
        return pages;
    }

    static ImportPlanner.Plan plan(List<String> existing, int currentPage, String text, ImportMode mode) {
        return ImportPlanner.plan(existing, currentPage, text, pages(text), mode);
    }

    @Test
    void replaceDiscardsTheBook() {
        ImportPlanner.Plan plan = plan(List.of("old1", "", "old2"), 2, "n1\nn2", ImportMode.REPLACE);
        assertEquals(List.of("n1", "n2"), plan.pages());
        assertEquals(0, plan.firstNewPage());
        assertEquals(2, plan.importedPages());
        assertEquals(2, plan.replacedPages());
        assertFalse(plan.hasRemainder());
    }

    @Test
    void appendGoesAfterTheLastPageWithText() {
        ImportPlanner.Plan plan = plan(List.of("old1", "old2", "", " "), 0, "n1\nn2", ImportMode.APPEND);
        assertEquals(List.of("old1", "old2", "n1", "n2"), plan.pages());
        assertEquals(2, plan.firstNewPage());
        assertEquals(0, plan.replacedPages());
    }

    @Test
    void appendIntoAnEmptyBookStartsAtPageOne() {
        ImportPlanner.Plan plan = plan(List.of(""), 0, "n1", ImportMode.APPEND);
        assertEquals(List.of("n1"), plan.pages());
        assertEquals(0, plan.firstNewPage());
    }

    @Test
    void insertGoesAfterTheCurrentPage() {
        ImportPlanner.Plan plan = plan(List.of("a", "b", "c"), 1, "n1\nn2", ImportMode.INSERT);
        assertEquals(List.of("a", "b", "n1", "n2", "c"), plan.pages());
        assertEquals(2, plan.firstNewPage());
    }

    @Test
    void insertReusesABlankCurrentPage() {
        ImportPlanner.Plan plan = plan(List.of("a", "", "c"), 1, "n1\nn2", ImportMode.INSERT);
        assertEquals(List.of("a", "n1", "n2", "c"), plan.pages());
        assertEquals(1, plan.firstNewPage());
    }

    @Test
    void whatDoesNotFitBecomesTheRemainder() {
        List<String> existing = new ArrayList<>(Collections.nCopies(98, "old"));
        ImportPlanner.Plan plan = plan(existing, 0, "n1\nn2\nn3\nn4", ImportMode.APPEND);
        assertEquals(100, plan.pages().size());
        assertEquals(2, plan.importedPages());
        assertEquals("n2", plan.pages().get(99));
        assertTrue(plan.hasRemainder());
        assertEquals(2, plan.remainderPages());
        assertEquals("n3\nn4", plan.remainder());
    }

    @Test
    void aFullBookImportsNothing() {
        List<String> existing = new ArrayList<>(Collections.nCopies(100, "old"));
        ImportPlanner.Plan plan = plan(existing, 5, "n1", ImportMode.INSERT);
        assertEquals(existing, plan.pages());
        assertEquals(0, plan.importedPages());
        assertEquals("n1", plan.remainder());
    }

    @Test
    void anEmptyImportLeavesAtLeastOnePage() {
        ImportPlanner.Plan plan = ImportPlanner.plan(List.of("old"), 0, "", List.of(), ImportMode.REPLACE);
        assertEquals(List.of(""), plan.pages());
        assertEquals(0, plan.importedPages());
    }
}
