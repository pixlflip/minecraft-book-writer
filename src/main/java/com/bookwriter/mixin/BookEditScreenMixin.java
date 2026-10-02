package com.bookwriter.mixin;

import com.bookwriter.BookWriterMod;
import com.bookwriter.screen.BookWriterNoticeScreen;
import com.bookwriter.util.BookImportSession;
import com.bookwriter.util.TextPaginator;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

@Mixin(BookEditScreen.class)
public abstract class BookEditScreenMixin extends Screen {
    @Shadow private int currentPage;
    @Shadow @Final private List<String> pages;
    @Unique private final BookImportSession bookwriter$session = new BookImportSession();
    @Unique private ButtonWidget bookwriter$undoButton;

    protected BookEditScreenMixin(Text title) {
        super(title);
    }

    @Shadow private void updatePage() { throw new AssertionError(); }
    @Shadow private void updatePreviousPageButtonVisibility() { throw new AssertionError(); }

    @Inject(method = "init", at = @At("RETURN"))
    private void bookwriter$addImportButtons(CallbackInfo ci) {
        // A second centered row avoids the old off-screen button on narrow GUI scales.
        int x = this.width / 2 - 100;
        int y = 220;
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("bookwriter.replace"),
                button -> bookwriter$prepareImport(false))
            .dimensions(x, y, 64, 20)
            .tooltip(Tooltip.of(Text.translatable("bookwriter.replace.tooltip"))).build());
        this.addDrawableChild(ButtonWidget.builder(Text.translatable("bookwriter.append"),
                button -> bookwriter$prepareImport(true))
            .dimensions(x + 68, y, 64, 20)
            .tooltip(Tooltip.of(Text.translatable("bookwriter.append.tooltip"))).build());
        bookwriter$undoButton = this.addDrawableChild(ButtonWidget.builder(Text.translatable("bookwriter.undo"),
                button -> bookwriter$confirmUndo())
            .dimensions(x + 136, y, 64, 20)
            .tooltip(Tooltip.of(Text.translatable("bookwriter.undo.tooltip"))).build());
        bookwriter$undoButton.active = bookwriter$session.canUndo();
    }

    @Unique
    private void bookwriter$prepareImport(boolean append) {
        String clipboard = this.client.keyboard.getClipboard();
        if (clipboard == null || clipboard.isBlank()) {
            bookwriter$showNotice("bookwriter.empty");
            return;
        }
        if (clipboard.length() > TextPaginator.MAX_INPUT_CHARS) {
            bookwriter$showNotice("bookwriter.too_large", TextPaginator.MAX_INPUT_CHARS);
            return;
        }
        int appendIndex = append ? BookImportSession.appendIndex(this.pages) : 0;
        int capacity = TextPaginator.MAX_PAGES - appendIndex;
        TextPaginator.Result result;
        try {
            result = TextPaginator.paginate(clipboard, this.textRenderer::getWidth, capacity);
        } catch (IllegalArgumentException exception) {
            BookWriterMod.LOGGER.warn("Cannot paginate clipboard text: {}", exception.getMessage());
            bookwriter$showNotice("bookwriter.cannot_paginate");
            return;
        }
        if (result.overflow()) {
            bookwriter$showNotice("bookwriter.overflow", capacity);
            return;
        }
        List<String> imported = new ArrayList<>(this.pages.subList(0, appendIndex));
        imported.addAll(result.pages());
        Text message = append
            ? Text.translatable("bookwriter.confirm.append", result.pages().size(), imported.size())
            : Text.translatable("bookwriter.confirm.replace", this.pages.size(), result.pages().size());
        this.client.setScreen(new ConfirmScreen(confirmed -> {
            this.client.setScreen(this);
            if (confirmed) {
                bookwriter$session.remember(this.pages, this.currentPage);
                bookwriter$applyPages(imported, appendIndex);
                bookwriter$notify("bookwriter.imported", result.pages().size(), imported.size());
            }
        }, Text.translatable("bookwriter.confirm.title"), message,
            Text.translatable("bookwriter.import"), ScreenTexts.CANCEL));
    }

    @Unique
    private void bookwriter$confirmUndo() {
        if (!bookwriter$session.canUndo()) return;
        this.client.setScreen(new ConfirmScreen(confirmed -> {
            this.client.setScreen(this);
            if (confirmed) {
                BookImportSession.Snapshot snapshot = bookwriter$session.undo();
                if (snapshot != null) {
                    bookwriter$applyPages(snapshot.pages(), snapshot.currentPage());
                    bookwriter$notify("bookwriter.restored");
                }
            }
        }, Text.translatable("bookwriter.undo"), Text.translatable("bookwriter.confirm.undo"),
            Text.translatable("bookwriter.undo"), ScreenTexts.CANCEL));
    }

    @Unique
    private void bookwriter$applyPages(List<String> replacement, int selectedPage) {
        this.pages.clear();
        this.pages.addAll(replacement);
        this.currentPage = Math.clamp(selectedPage, 0, this.pages.size() - 1);
        this.updatePage();
        this.updatePreviousPageButtonVisibility();
        bookwriter$undoButton.active = bookwriter$session.canUndo();
    }

    @Unique
    private void bookwriter$showNotice(String key, Object... args) {
        this.client.setScreen(new BookWriterNoticeScreen(this, Text.translatable(key, args)));
    }

    @Unique
    private void bookwriter$notify(String key, Object... args) {
        if (this.client.player != null) {
            this.client.player.sendMessage(Text.translatable(key, args), false);
        }
    }
}
