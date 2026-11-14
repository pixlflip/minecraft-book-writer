package com.bookwriter.mixin;

import com.bookwriter.BookWriterMod;
import com.bookwriter.gui.BookImportTarget;
import com.bookwriter.gui.ImportPreviewScreen;
import com.bookwriter.gui.LinkScreen;
import com.bookwriter.gui.Toasts;
import com.bookwriter.text.ImportPlanner;
import com.bookwriter.web.UrlResolver;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/** Adds the Book Writer buttons to the book and quill screen. */
@Mixin(BookEditScreen.class)
public abstract class BookEditScreenMixin extends Screen implements BookImportTarget {
    @Unique
    private static final int BOOK_WIDTH = 192;
    @Unique
    private static final int PANEL_WIDTH = 98;

    @Shadow
    private int currentPage;

    @Shadow
    @Final
    private List<String> pages;

    @Shadow
    private EditBoxWidget editBox;

    /** The book as it was before the last import, or null if there is nothing to undo. */
    @Unique
    private List<String> bookwriter$undoPages;
    @Unique
    private int bookwriter$undoPage;

    protected BookEditScreenMixin(Text title) {
        super(title);
    }

    @Shadow
    private void updatePage() {
        throw new AssertionError();
    }

    @Shadow
    private void updatePreviousPageButtonVisibility() {
        throw new AssertionError();
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void bookwriter$addButtons(CallbackInfo ci) {
        List<ButtonWidget> buttons = new ArrayList<>();
        buttons.add(ButtonWidget.builder(Text.translatable("bookwriter.button.paste"), button -> this.bookwriter$pasteText())
            .tooltip(Tooltip.of(Text.translatable("bookwriter.button.paste.tooltip"))).build());
        buttons.add(ButtonWidget.builder(Text.translatable("bookwriter.button.link"), button -> this.bookwriter$pasteLink())
            .tooltip(Tooltip.of(Text.translatable("bookwriter.button.link.tooltip"))).build());

        ButtonWidget undo = ButtonWidget.builder(Text.translatable("bookwriter.button.undo"), button -> this.bookwriter$undo())
            .tooltip(Tooltip.of(Text.translatable("bookwriter.button.undo.tooltip"))).build();
        undo.active = this.bookwriter$undoPages != null;
        buttons.add(undo);

        if (!BookWriterMod.pendingText.isEmpty()) {
            buttons.add(ButtonWidget.builder(Text.translatable("bookwriter.button.continue"), button -> this.bookwriter$continue())
                .tooltip(Tooltip.of(Text.translatable("bookwriter.button.continue.tooltip"))).build());
        }

        int side = (this.width - BOOK_WIDTH) / 2;
        if (side >= PANEL_WIDTH + 6) {
            // Room beside the book: a column to its right.
            int x = (this.width + BOOK_WIDTH) / 2 + 2;
            int y = 28;
            for (ButtonWidget button : buttons) {
                button.setDimensionsAndPosition(PANEL_WIDTH, 20, x, y);
                y += 24;
            }
        } else {
            // Narrow window: a row under Sign / Done.
            int gap = 2;
            int buttonWidth = Math.min(72, (this.width - 8 - gap * (buttons.size() - 1)) / buttons.size());
            int x = (this.width - buttonWidth * buttons.size() - gap * (buttons.size() - 1)) / 2;
            int y = Math.min(220, this.height - 20);
            for (ButtonWidget button : buttons) {
                button.setDimensionsAndPosition(buttonWidth, 20, x, y);
                x += buttonWidth + gap;
            }
        }
        buttons.forEach(this::addDrawableChild);
    }

    /** Ctrl+Shift+V (Cmd+Shift+V on macOS) imports the clipboard; plain Ctrl+V still pastes into the page. */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void bookwriter$shortcut(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (keyCode == GLFW.GLFW_KEY_V && Screen.hasControlDown() && Screen.hasShiftDown() && !Screen.hasAltDown()) {
            this.bookwriter$pasteText();
            cir.setReturnValue(true);
        }
    }

    @Unique
    private void bookwriter$pasteText() {
        String clipboard = this.client.keyboard.getClipboard();
        if (clipboard == null || clipboard.isBlank()) {
            Toasts.show(Text.translatable("bookwriter.toast.clipboard_empty"), Text.translatable("bookwriter.toast.clipboard_empty.hint"));
            return;
        }
        this.client.setScreen(new ImportPreviewScreen(this, this, Text.translatable("bookwriter.source.clipboard"), clipboard, false));
    }

    @Unique
    private void bookwriter$pasteLink() {
        String clipboard = this.client.keyboard.getClipboard();
        String link = UrlResolver.looksLikeUrl(clipboard) ? clipboard.trim() : "";
        this.client.setScreen(new LinkScreen(this, this, link));
    }

    @Unique
    private void bookwriter$continue() {
        this.client.setScreen(new ImportPreviewScreen(this, this, Text.translatable("bookwriter.source.continued"), BookWriterMod.pendingText, true));
    }

    @Unique
    private void bookwriter$undo() {
        if (this.bookwriter$undoPages == null) {
            return;
        }
        this.bookwriter$setPages(this.bookwriter$undoPages, this.bookwriter$undoPage);
        this.bookwriter$undoPages = null;
        // Rebuild the buttons so Undo greys out again.
        this.clearAndInit();
        Toasts.show(Text.translatable("bookwriter.toast.undone"), null);
    }

    @Unique
    private void bookwriter$setPages(List<String> newPages, int page) {
        // The signing screen shares this list, so change it in place.
        this.pages.clear();
        this.pages.addAll(newPages);
        if (this.pages.isEmpty()) {
            this.pages.add("");
        }
        this.currentPage = Math.max(0, Math.min(page, this.pages.size() - 1));
        if (this.editBox != null) {
            this.updatePage();
            this.updatePreviousPageButtonVisibility();
        }
    }

    @Override
    public List<String> bookwriter$getPages() {
        return List.copyOf(this.pages);
    }

    @Override
    public int bookwriter$getCurrentPage() {
        return this.currentPage;
    }

    @Override
    public void bookwriter$applyImport(ImportPlanner.Plan plan) {
        this.bookwriter$undoPages = new ArrayList<>(this.pages);
        this.bookwriter$undoPage = this.currentPage;
        this.bookwriter$setPages(plan.pages(), plan.firstNewPage());
        BookWriterMod.LOGGER.debug("Imported {} pages at page {}", plan.importedPages(), plan.firstNewPage() + 1);
    }
}
