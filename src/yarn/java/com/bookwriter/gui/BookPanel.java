package com.bookwriter.gui;

import com.bookwriter.BookWriterMod;
import com.bookwriter.web.UrlResolver;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * The Book Writer buttons shown on the book and quill screen, and what they do. The per-version
 * mixin decides when to add them and how to write the result back into the book.
 */
public final class BookPanel {
    /** Width of the book texture, which the buttons sit beside. */
    private static final int BOOK_WIDTH = 192;
    private static final int PANEL_WIDTH = 98;

    private BookPanel() {
    }

    /**
     * Builds the buttons: a column right of the book when there is room, otherwise a row under
     * the book's own Sign / Done buttons.
     */
    public static List<ButtonWidget> create(Screen screen, BookImportTarget target, int width, int height, boolean canUndo, Runnable undo) {
        List<ButtonWidget.Builder> builders = new ArrayList<>();
        builders.add(ButtonWidget.builder(Text.translatable("bookwriter.button.paste"), button -> pasteText(screen, target))
            .tooltip(Tooltip.of(Text.translatable("bookwriter.button.paste.tooltip"))));
        builders.add(ButtonWidget.builder(Text.translatable("bookwriter.button.link"), button -> pasteLink(screen, target))
            .tooltip(Tooltip.of(Text.translatable("bookwriter.button.link.tooltip"))));
        builders.add(ButtonWidget.builder(Text.translatable("bookwriter.button.undo"), button -> undo.run())
            .tooltip(Tooltip.of(Text.translatable("bookwriter.button.undo.tooltip"))));
        if (!BookWriterMod.pendingText.isEmpty()) {
            builders.add(ButtonWidget.builder(Text.translatable("bookwriter.button.continue"), button -> continueImport(screen, target))
                .tooltip(Tooltip.of(Text.translatable("bookwriter.button.continue.tooltip"))));
        }

        List<ButtonWidget> buttons = new ArrayList<>();
        int side = (width - BOOK_WIDTH) / 2;
        if (side >= PANEL_WIDTH + 6) {
            int x = (width + BOOK_WIDTH) / 2 + 2;
            int y = 28;
            for (ButtonWidget.Builder builder : builders) {
                buttons.add(builder.dimensions(x, y, PANEL_WIDTH, 20).build());
                y += 24;
            }
        } else {
            int gap = 2;
            int count = builders.size();
            int buttonWidth = Math.min(72, (width - 8 - gap * (count - 1)) / count);
            int x = (width - buttonWidth * count - gap * (count - 1)) / 2;
            int y = Math.min(220, height - 20);
            for (ButtonWidget.Builder builder : builders) {
                buttons.add(builder.dimensions(x, y, buttonWidth, 20).build());
                x += buttonWidth + gap;
            }
        }
        // The Undo button is always third.
        buttons.get(2).active = canUndo;
        return buttons;
    }

    public static void pasteText(Screen screen, BookImportTarget target) {
        MinecraftClient client = MinecraftClient.getInstance();
        String clipboard = client.keyboard.getClipboard();
        if (clipboard == null || clipboard.isBlank()) {
            Toasts.show(Text.translatable("bookwriter.toast.clipboard_empty"), Text.translatable("bookwriter.toast.clipboard_empty.hint"));
            return;
        }
        client.setScreen(new ImportPreviewScreen(screen, target, Text.translatable("bookwriter.source.clipboard"), clipboard, false));
    }

    public static void pasteLink(Screen screen, BookImportTarget target) {
        MinecraftClient client = MinecraftClient.getInstance();
        String clipboard = client.keyboard.getClipboard();
        String link = UrlResolver.looksLikeUrl(clipboard) ? clipboard.trim() : "";
        client.setScreen(new LinkScreen(screen, target, link));
    }

    public static void continueImport(Screen screen, BookImportTarget target) {
        MinecraftClient.getInstance().setScreen(new ImportPreviewScreen(screen, target,
            Text.translatable("bookwriter.source.continued"), BookWriterMod.pendingText, true));
    }

    /** Ctrl+Shift+V (Cmd+Shift+V on macOS) imports the clipboard; plain Ctrl+V still pastes into the page. */
    public static boolean isImportShortcut(int keyCode, boolean ctrl, boolean shift, boolean alt) {
        return keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_V && ctrl && shift && !alt;
    }
}
