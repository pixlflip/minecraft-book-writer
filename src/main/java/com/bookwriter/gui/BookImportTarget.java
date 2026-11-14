package com.bookwriter.gui;

import com.bookwriter.text.ImportPlanner;

import java.util.List;

/** Implemented by the book edit screen (through a mixin) so import screens can read and write it. */
public interface BookImportTarget {
    List<String> bookwriter$getPages();

    int bookwriter$getCurrentPage();

    /** Replaces the book contents with the plan's pages and jumps to the first imported page. */
    void bookwriter$applyImport(ImportPlanner.Plan plan);
}
