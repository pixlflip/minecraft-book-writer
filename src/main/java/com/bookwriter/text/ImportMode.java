package com.bookwriter.text;

/** Where imported pages go relative to what is already in the book. */
public enum ImportMode {
    /** Discard the current contents. */
    REPLACE,
    /** Add after the page being viewed (or onto it, if it is blank). */
    INSERT,
    /** Add after the last page that has text. */
    APPEND
}
