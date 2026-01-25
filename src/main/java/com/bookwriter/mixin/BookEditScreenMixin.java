package com.bookwriter.mixin;

import com.bookwriter.BookWriterMod;
import com.bookwriter.util.TextPaginator;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(BookEditScreen.class)
public abstract class BookEditScreenMixin extends Screen {
    @Shadow
    private int currentPage;

    @Shadow
    private List<String> pages;

    // Required constructor for extending Screen
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
    private void addPasteButton(CallbackInfo ci) {
        // Position the Paste button to the left of the Sign button
        // Sign button is at: this.width / 2 - 100, 196, 98, 20
        // Done button is at: this.width / 2 + 2, 196, 98, 20
        int buttonWidth = 60;
        int buttonHeight = 20;
        int buttonX = this.width / 2 - 100 - buttonWidth - 4; // Left of the Sign button
        int buttonY = 196; // Same Y position as the Done/Sign buttons

        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Paste"),
            button -> pasteFromClipboard()
        ).dimensions(buttonX, buttonY, buttonWidth, buttonHeight).build());

        BookWriterMod.LOGGER.debug("Paste button added to book edit screen");
    }

    private void pasteFromClipboard() {
        MinecraftClient client = MinecraftClient.getInstance();
        String clipboardContent = client.keyboard.getClipboard();

        if (clipboardContent == null || clipboardContent.isEmpty()) {
            BookWriterMod.LOGGER.debug("Clipboard is empty, nothing to paste");
            return;
        }

        // Paginate the clipboard content
        List<String> newPages = TextPaginator.paginate(clipboardContent);

        // Replace all pages with the new content
        this.pages.clear();
        this.pages.addAll(newPages);

        // Go to the first page
        this.currentPage = 0;

        // Update the screen to reflect changes
        this.updatePage();
        this.updatePreviousPageButtonVisibility();

        BookWriterMod.LOGGER.info("Pasted {} pages of content from clipboard", newPages.size());
    }
}
