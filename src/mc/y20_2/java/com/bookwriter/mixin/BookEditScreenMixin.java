package com.bookwriter.mixin;

import com.bookwriter.BookWriterMod;
import com.bookwriter.gui.BookImportTarget;
import com.bookwriter.gui.BookPanel;
import com.bookwriter.gui.Toasts;
import com.bookwriter.text.ImportPlanner;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
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

/**
 * Adds the Book Writer buttons to the book and quill screen (before 1.21.6: the screen draws its
 * own page text and signs the book in place).
 */
@Mixin(BookEditScreen.class)
public abstract class BookEditScreenMixin extends Screen implements BookImportTarget {
    @Shadow
    private int currentPage;

    @Shadow
    @Final
    private List<String> pages;

    /** Set when the book was edited; Done only saves a dirty book. */
    @Shadow
    private boolean dirty;

    /** True while the screen shows the title / sign view. */
    @Shadow
    private boolean signing;

    @Unique
    private final List<ButtonWidget> bookwriter$buttons = new ArrayList<>();

    /** The book as it was before the last import, or null if there is nothing to undo. */
    @Unique
    private List<String> bookwriter$undoPages;
    @Unique
    private int bookwriter$undoPage;

    protected BookEditScreenMixin(Text title) {
        super(title);
    }

    @Shadow
    private void changePage() {
        throw new AssertionError();
    }

    @Shadow
    private void updateButtons() {
        throw new AssertionError();
    }

    @Inject(method = "init", at = @At("RETURN"))
    private void bookwriter$addButtons(CallbackInfo ci) {
        this.bookwriter$buttons.clear();
        this.bookwriter$buttons.addAll(BookPanel.create(this, this, this.width, this.height, this.bookwriter$undoPages != null, this::bookwriter$undo));
        this.bookwriter$buttons.forEach(this::addDrawableChild);
        this.bookwriter$updateVisibility();
    }

    /** Hide the buttons while signing, like the book's own page buttons. */
    @Inject(method = "updateButtons", at = @At("RETURN"))
    private void bookwriter$updateButtons(CallbackInfo ci) {
        this.bookwriter$updateVisibility();
    }

    @Unique
    private void bookwriter$updateVisibility() {
        for (ButtonWidget button : this.bookwriter$buttons) {
            button.visible = !this.signing;
        }
    }

    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void bookwriter$shortcut(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        if (!this.signing && BookPanel.isImportShortcut(keyCode, Screen.hasControlDown(), Screen.hasShiftDown(), Screen.hasAltDown())) {
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
        this.pages.clear();
        this.pages.addAll(newPages);
        if (this.pages.isEmpty()) {
            this.pages.add("");
        }
        this.currentPage = Math.max(0, Math.min(page, this.pages.size() - 1));
        this.dirty = true;
        this.changePage();
        this.updateButtons();
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
