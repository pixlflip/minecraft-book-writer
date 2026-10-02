package com.bookwriter;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BookWriterMod implements ClientModInitializer {
    public static final String MOD_ID = "bookwriter";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitializeClient() {
        LOGGER.info("Book Writer initialized - safe clipboard imports enabled");
    }
}
