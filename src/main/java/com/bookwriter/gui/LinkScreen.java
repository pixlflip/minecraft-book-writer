package com.bookwriter.gui;

import com.bookwriter.BookWriterMod;
import com.bookwriter.web.LinkFetcher;
import com.bookwriter.web.UrlResolver;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.CompletableFuture;

/** Asks for a link, downloads it without freezing the game, and hands the text to the preview. */
public class LinkScreen extends Screen {
    private static final int FIELD_WIDTH = 300;

    private final Screen parent;
    private final BookImportTarget target;

    private TextFieldWidget linkField;
    private ButtonWidget fetchButton;
    private String link = "";
    private Text status = ScreenTexts.EMPTY;
    private CompletableFuture<LinkFetcher.Result> request;

    public LinkScreen(Screen parent, BookImportTarget target, String initialLink) {
        super(Text.translatable("bookwriter.link.title"));
        this.parent = parent;
        this.target = target;
        this.link = initialLink;
    }

    @Override
    protected void init() {
        int fieldWidth = Math.min(FIELD_WIDTH, this.width - 20);
        int x = (this.width - fieldWidth) / 2;
        int y = this.height / 2 - 30;

        this.linkField = new TextFieldWidget(this.textRenderer, x, y, fieldWidth, 20, Text.translatable("bookwriter.link.field"));
        this.linkField.setMaxLength(2048);
        this.linkField.setText(this.link);
        this.linkField.setPlaceholder(Text.translatable("bookwriter.link.placeholder").formatted(Formatting.DARK_GRAY));
        this.linkField.setChangedListener(text -> {
            this.link = text;
            this.updateFetchButton();
        });
        this.addDrawableChild(this.linkField);

        this.fetchButton = this.addDrawableChild(ButtonWidget.builder(Text.translatable("bookwriter.link.fetch"), button -> this.fetch())
            .dimensions(this.width / 2 - 102, y + 46, 100, 20).build());
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, button -> this.close())
            .dimensions(this.width / 2 + 2, y + 46, 100, 20).build());
        this.updateFetchButton();
    }

    @Override
    protected void setInitialFocus() {
        this.setInitialFocus(this.linkField);
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
        this.status = Text.translatable("bookwriter.link.fetching");
        CompletableFuture<LinkFetcher.Result> future = LinkFetcher.fetch(this.link);
        this.request = future;
        this.updateFetchButton();

        // The download finishes on a background thread; go back to the game thread to touch the UI.
        future.whenComplete((result, error) -> this.client.execute(() -> {
            if (this.request != future) {
                return; // Cancelled, or the screen was closed.
            }
            this.request = null;
            if (error == null) {
                this.client.setScreen(new ImportPreviewScreen(this.parent, this.target, Text.literal(result.title()), result.text(), false));
                return;
            }
            Throwable cause = error.getCause() != null ? error.getCause() : error;
            if (cause instanceof LinkFetcher.FetchException fetchException) {
                this.status = Text.translatable(fetchException.key(), fetchException.args()).formatted(Formatting.RED);
            } else {
                BookWriterMod.LOGGER.warn("Could not import link", cause);
                this.status = Text.translatable("bookwriter.error.connect").formatted(Formatting.RED);
            }
            this.updateFetchButton();
        }));
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }

    @Override
    public void removed() {
        if (this.request != null) {
            this.request.cancel(true);
            this.request = null;
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && this.linkField.isFocused()) {
            this.fetch();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        super.render(context, mouseX, mouseY, deltaTicks);
        int y = this.height / 2 - 30;
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, y - 32, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.translatable("bookwriter.link.hint"), this.width / 2, y - 14, 0xFFA0A0A0);

        int width = Math.min(FIELD_WIDTH, this.width - 20);
        int lines = this.textRenderer.wrapLines(this.status, width).size();
        // Centre short messages; wrap long ones under the field.
        if (lines <= 1) {
            context.drawCenteredTextWithShadow(this.textRenderer, this.status, this.width / 2, y + 28, 0xFFFFFFFF);
        } else {
            context.drawWrappedText(this.textRenderer, this.status, (this.width - width) / 2, y + 72, width, 0xFFFFFFFF, true);
        }
    }
}
