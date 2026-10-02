package com.bookwriter.screen;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.NoticeScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;

/** Error dialogs return to the same editor on both OK and Escape. */
public final class BookWriterNoticeScreen extends NoticeScreen {
    private final Screen parent;

    public BookWriterNoticeScreen(Screen parent, Text notice) {
        super(() -> MinecraftClient.getInstance().setScreen(parent),
            Text.translatable("bookwriter.notice.title"), notice, ScreenTexts.OK, true);
        this.parent = parent;
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }
}
