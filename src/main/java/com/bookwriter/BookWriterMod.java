package com.bookwriter;

import com.bookwriter.text.ImportMode;
import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BookWriterMod implements ClientModInitializer {
    public static final String MOD_ID = "bookwriter";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    // Choices and leftovers that should survive closing the book, for this game session.

    /** Import mode last used on the preview screen. */
    public static ImportMode importMode = ImportMode.APPEND;
    /** Whether "Tidy text" was last on. */
    public static boolean tidyText = true;
    /** Text that did not fit in the last book, waiting to be continued in another one. */
    public static String pendingText = "";

    @Override
    public void onInitializeClient() {
        LOGGER.info("Book Writer initialized");
    }
}
