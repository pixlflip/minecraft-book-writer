package com.bookwriter.gui;

import com.bookwriter.BookWriterMod;
import com.bookwriter.web.LinkFetcher;
import com.bookwriter.web.UrlResolver;
import com.bookwriter.compat.BaseScreen;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.CompletableFuture;

/** Asks for a link, downloads it without freezing the game, and hands the text to the preview. */
public class LinkScreen extends BaseScreen {
    private static final int FIELD_WIDTH = 300;

    private final Screen parent;
    private final BookImportTarget target;

    private EditBox linkField;
    private Button fetchButton;
    private String link = "";
    private Component status = CommonComponents.EMPTY;
    private CompletableFuture<LinkFetcher.Result> request;

    public LinkScreen(Screen parent, BookImportTarget target, String initialLink) {
        super(Component.translatable("bookwriter.link.title"));
        this.parent = parent;
        this.target = target;
        this.link = initialLink;
    }

    @Override
    protected void init() {
        int fieldWidth = Math.min(FIELD_WIDTH, this.width - 20);
        int x = (this.width - fieldWidth) / 2;
        int y = this.height / 2 - 30;

        this.linkField = new EditBox(this.font, x, y, fieldWidth, 20, Component.translatable("bookwriter.link.field"));
        this.linkField.setMaxLength(2048);
        this.linkField.setValue(this.link);
        this.linkField.setHint(Component.translatable("bookwriter.link.placeholder").withStyle(ChatFormatting.DARK_GRAY));
        this.linkField.setResponder(text -> {
            this.link = text;
            this.updateFetchButton();
        });
        this.addRenderableWidget(this.linkField);

        this.fetchButton = this.addRenderableWidget(Button.builder(Component.translatable("bookwriter.link.fetch"), button -> this.fetch())
            .bounds(this.width / 2 - 102, y + 46, 100, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
            .bounds(this.width / 2 + 2, y + 46, 100, 20).build());
        this.updateFetchButton();
        this.applyInitialFocus();
    }

    @Override
    protected GuiEventListener initialFocus() {
        return this.linkField;
    }

    private boolean fetching() {
        return this.request != null;
    }

    private void updateFetchButton() {
        this.fetchButton.active = !this.fetching() && UrlResolver.looksLikeUrl(this.link);
    }

    private void fetch() {
        if (this.fetching() || !UrlResolver.looksLikeUrl(this.link)) {
            return;
        }
        this.status = Component.translatable("bookwriter.link.fetching");
        CompletableFuture<LinkFetcher.Result> future = LinkFetcher.fetch(this.link);
        this.request = future;
        this.updateFetchButton();

        // The download finishes on a background thread; go back to the game thread to touch the UI.
        future.whenComplete((result, error) -> this.minecraft.execute(() -> {
            if (this.request != future) {
                return; // Cancelled, or the screen was closed.
            }
            this.request = null;
            if (error == null) {
                this.minecraft.setScreen(new ImportPreviewScreen(this.parent, this.target, Component.literal(result.title()), result.text(), false));
                return;
            }
            Throwable cause = error.getCause() != null ? error.getCause() : error;
            if (cause instanceof LinkFetcher.FetchException fetchException) {
                this.status = Component.translatable(fetchException.key(), fetchException.args()).withStyle(ChatFormatting.RED);
            } else {
                BookWriterMod.LOGGER.warn("Could not import link", cause);
                this.status = Component.translatable("bookwriter.error.connect").withStyle(ChatFormatting.RED);
            }
            this.updateFetchButton();
        }));
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }

    @Override
    public void removed() {
        if (this.request != null) {
            this.request.cancel(true);
            this.request = null;
        }
    }

    @Override
    protected boolean onKey(int keyCode, boolean ctrl, boolean shift, boolean alt) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && this.linkField.isFocused()) {
            this.fetch();
            return true;
        }
        return false;
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        int y = this.height / 2 - 30;
        context.centeredText(this.font, this.title, this.width / 2, y - 32, 0xFFFFFFFF);
        context.centeredText(this.font, Component.translatable("bookwriter.link.hint"), this.width / 2, y - 14, 0xFFA0A0A0);

        int width = Math.min(FIELD_WIDTH, this.width - 20);
        int lines = this.font.split(this.status, width).size();
        // Centre short messages; wrap long ones under the field.
        if (lines <= 1) {
            context.centeredText(this.font, this.status, this.width / 2, y + 28, 0xFFFFFFFF);
        } else {
            this.drawWrapped(context, this.status, (this.width - width) / 2, y + 72, width, 0xFFFFFFFF);
        }
    }
}
