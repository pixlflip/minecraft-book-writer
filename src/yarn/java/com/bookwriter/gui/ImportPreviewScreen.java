package com.bookwriter.gui;

import com.bookwriter.BookWriterMod;
import com.bookwriter.text.BookPaginator;
import com.bookwriter.text.ImportMode;
import com.bookwriter.text.ImportPlanner;
import com.bookwriter.text.TextCleaner;
import com.bookwriter.compat.BaseScreen;
import com.bookwriter.compat.Compat;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows what an import will do to the book (how many pages, what gets replaced, what does not
 * fit) with a page-by-page preview, before anything is changed.
 */
public class ImportPreviewScreen extends BaseScreen {
    /** Text area of a book page, the same in every supported version. */
    public static final int TEXT_WIDTH = 114;
    public static final int TEXT_HEIGHT = 128;
    private static final int PAGE_WIDTH = TEXT_WIDTH + 8;
    private static final int PAGE_HEIGHT = TEXT_HEIGHT + 8;
    private static final int COLUMN_GAP = 10;
    private static final int COLUMN_WIDTH = 150;
    private static final int TOP = 34;

    private static final int PAPER = 0xFFFDF6E3;
    private static final int PAPER_EDGE = 0xFF8B6B3D;
    private static final int INK = 0xFF000000;
    private static final int WHITE = 0xFFFFFFFF;
    private static final int GRAY = 0xFFA0A0A0;

    private final Screen parent;
    private final BookImportTarget target;
    private final Text source;
    private final String rawText;
    /** True when importing {@link BookWriterMod#pendingText}, which this import then uses up. */
    private final boolean continuation;

    private String cleanText = "";
    private ImportPlanner.Plan plan;
    private int previewPage;
    private List<Text> summary = List.of();

    private ButtonWidget importButton;
    private ButtonWidget previousButton;
    private ButtonWidget nextButton;

    public ImportPreviewScreen(Screen parent, BookImportTarget target, Text source, String rawText, boolean continuation) {
        super(Text.translatable("bookwriter.preview.title"));
        this.parent = parent;
        this.target = target;
        this.source = source;
        this.rawText = rawText;
        this.continuation = continuation;
    }

    public static BookPaginator.LineWrapper lineWrapper(TextRenderer textRenderer) {
        return (text, consumer) -> textRenderer.getTextHandler()
            .wrapLines(text, TEXT_WIDTH, Style.EMPTY, false, (style, start, end) -> consumer.accept(start, end));
    }

    private int left() {
        return (this.width - (PAGE_WIDTH + COLUMN_GAP + COLUMN_WIDTH)) / 2;
    }

    @Override
    protected void init() {
        int column = left() + PAGE_WIDTH + COLUMN_GAP;

        this.addDrawableChild(Compat.<ImportMode>cycling(mode -> Text.translatable("bookwriter.mode." + mode.name().toLowerCase()), BookWriterMod.importMode)
            .values(ImportMode.values())
            .tooltip(mode -> Tooltip.of(Text.translatable("bookwriter.mode." + mode.name().toLowerCase() + ".tooltip")))
            .build(column, TOP, COLUMN_WIDTH, 20, Text.translatable("bookwriter.mode"), (button, mode) -> {
                BookWriterMod.importMode = mode;
                this.recalculate();
            }));

        this.addDrawableChild(CyclingButtonWidget.onOffBuilder(BookWriterMod.tidyText)
            .tooltip(value -> Tooltip.of(Text.translatable("bookwriter.tidy.tooltip")))
            .build(column, TOP + 24, COLUMN_WIDTH, 20, Text.translatable("bookwriter.tidy"), (button, value) -> {
                BookWriterMod.tidyText = value;
                this.recalculate();
            }));

        int navY = TOP + PAGE_HEIGHT + 4;
        this.previousButton = this.addDrawableChild(ButtonWidget.builder(Text.literal("<"), button -> this.flip(-1))
            .dimensions(left(), navY, 20, 20).build());
        this.nextButton = this.addDrawableChild(ButtonWidget.builder(Text.literal(">"), button -> this.flip(1))
            .dimensions(left() + PAGE_WIDTH - 20, navY, 20, 20).build());

        int buttonY = Math.min(this.height - 24, navY + 28);
        this.importButton = this.addDrawableChild(ButtonWidget.builder(Text.translatable("bookwriter.preview.import"), button -> this.confirm())
            .dimensions(column, buttonY, 73, 20).build());
        this.addDrawableChild(ButtonWidget.builder(ScreenTexts.CANCEL, button -> this.close())
            .dimensions(column + 77, buttonY, 73, 20).build());

        this.recalculate();
    }

    private void recalculate() {
        this.cleanText = TextCleaner.clean(this.rawText, BookWriterMod.tidyText);
        List<BookPaginator.Page> pages = BookPaginator.paginate(this.cleanText, lineWrapper(this.textRenderer), Compat.PAGE_LENGTH);
        this.plan = ImportPlanner.plan(this.target.bookwriter$getPages(), this.target.bookwriter$getCurrentPage(), this.cleanText, pages, BookWriterMod.importMode);
        this.previewPage = Math.min(this.previewPage, Math.max(0, this.plan.importedPages() - 1));
        this.importButton.active = this.plan.importedPages() > 0;

        List<Text> lines = new ArrayList<>();
        lines.add(Text.translatable(pages.size() == 1 ? "bookwriter.preview.size.one" : "bookwriter.preview.size", String.format("%,d", this.cleanText.length()), pages.size()));
        if (pages.isEmpty()) {
            lines.add(Text.translatable("bookwriter.preview.nothing").formatted(Formatting.RED));
        } else if (this.plan.importedPages() == 0) {
            lines.add(Text.translatable("bookwriter.preview.full").formatted(Formatting.RED));
        } else {
            int first = this.plan.firstNewPage() + 1;
            lines.add(this.plan.importedPages() == 1
                ? Text.translatable("bookwriter.preview.range.one", first, this.plan.pages().size())
                : Text.translatable("bookwriter.preview.range", first, first + this.plan.importedPages() - 1, this.plan.pages().size()));
        }
        if (this.plan.replacedPages() > 0 && this.plan.importedPages() > 0) {
            lines.add(Toasts.counted("bookwriter.preview.replaces", this.plan.replacedPages()).formatted(Formatting.GOLD));
        }
        if (this.plan.hasRemainder() && this.plan.importedPages() > 0) {
            lines.add(Toasts.counted("bookwriter.preview.overflow", this.plan.remainderPages()).formatted(Formatting.YELLOW));
        }
        this.summary = lines;
        this.updateNavigation();
    }

    private void flip(int by) {
        this.previewPage = Math.max(0, Math.min(this.previewPage + by, this.plan.importedPages() - 1));
        this.updateNavigation();
    }

    private void updateNavigation() {
        this.previousButton.active = this.previewPage > 0;
        this.nextButton.active = this.previewPage < this.plan.importedPages() - 1;
    }

    private void confirm() {
        if (this.plan.importedPages() == 0) {
            return;
        }
        this.target.bookwriter$applyImport(this.plan);
        if (this.plan.hasRemainder()) {
            BookWriterMod.pendingText = this.plan.remainder();
            Toasts.show(Toasts.counted("bookwriter.toast.imported", this.plan.importedPages()),
                Toasts.counted("bookwriter.toast.overflow", this.plan.remainderPages()));
        } else {
            if (this.continuation) {
                BookWriterMod.pendingText = "";
            }
            Toasts.show(Toasts.counted("bookwriter.toast.imported", this.plan.importedPages()), Text.translatable("bookwriter.toast.undo_hint"));
        }
        this.client.setScreen(this.parent);
    }

    @Override
    public void close() {
        this.client.setScreen(this.parent);
    }

    @Override
    protected boolean onKey(int keyCode, boolean ctrl, boolean shift, boolean alt) {
        switch (keyCode) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> {
                // Enter on a focused button presses that button; otherwise it confirms.
                if (!(this.getFocused() instanceof ButtonWidget) && this.importButton.active) {
                    this.confirm();
                    return true;
                }
                return false;
            }
            case GLFW.GLFW_KEY_PAGE_UP -> {
                this.flip(-1);
                return true;
            }
            case GLFW.GLFW_KEY_PAGE_DOWN -> {
                this.flip(1);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    protected void renderContent(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        int left = left();
        int column = left + PAGE_WIDTH + COLUMN_GAP;

        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 8, WHITE);
        String from = this.textRenderer.trimToWidth(Text.translatable("bookwriter.preview.source", this.source).getString(), PAGE_WIDTH + COLUMN_GAP + COLUMN_WIDTH);
        context.drawCenteredTextWithShadow(this.textRenderer, from, this.width / 2, 20, GRAY);

        // The page being previewed, drawn the way the book will wrap it.
        context.fill(left - 1, TOP - 1, left + PAGE_WIDTH + 1, TOP + PAGE_HEIGHT + 1, PAPER_EDGE);
        context.fill(left, TOP, left + PAGE_WIDTH, TOP + PAGE_HEIGHT, PAPER);
        if (this.plan.importedPages() > 0) {
            String page = this.plan.pages().get(this.plan.firstNewPage() + this.previewPage);
            int y = TOP + 4;
            for (OrderedText line : this.textRenderer.wrapLines(Text.literal(page), TEXT_WIDTH)) {
                context.drawText(this.textRenderer, line, left + 4, y, INK, false);
                y += this.textRenderer.fontHeight;
            }
            Text indicator = Text.translatable("book.pageIndicator", this.plan.firstNewPage() + this.previewPage + 1, this.plan.pages().size());
            context.drawCenteredTextWithShadow(this.textRenderer, indicator, left + PAGE_WIDTH / 2, TOP + PAGE_HEIGHT + 10, WHITE);
        }

        int y = TOP + 52;
        for (Text line : this.summary) {
            y = this.drawWrapped(context, line, column, y, COLUMN_WIDTH, WHITE) + 4;
        }
    }
}
