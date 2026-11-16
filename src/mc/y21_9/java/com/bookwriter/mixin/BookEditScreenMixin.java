package com.bookwriter.mixin;

import com.bookwriter.BookWriterMod;
import com.bookwriter.gui.BookImportTarget;
import com.bookwriter.gui.BookPanel;
import com.bookwriter.gui.Toasts;
import com.bookwriter.text.ImportPlanner;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.widget.EditBoxWidget;
import net.minecraft.text.Text;
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

/** Adds the Book Writer buttons to the book and quill screen (1.21.6 onwards: the page is an edit box). */
@Mixin(BookEditScreen.class)
public abstract class BookEditScreenMixin extends Screen implements BookImportTarget {
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
        BookPanel.create(this, this, this.width, this.height, this.bookwriter$undoPages != null, this::bookwriter$undo)
            .forEach(this::addDrawableChild);
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void bookwriter$shortcut(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (BookPanel.isImportShortcut(input.key(), input.hasCtrl(), input.hasShift(), input.hasAlt())) {
            BookPanel.pasteText(this, this);
            cir.setReturnValue(true);
        }
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
