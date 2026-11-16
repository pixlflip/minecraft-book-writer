package com.bookwriter.gui;

import com.bookwriter.BookWriterMod;
import com.bookwriter.text.BookPaginator;
import com.bookwriter.text.ImportMode;
import com.bookwriter.text.ImportPlanner;
import com.bookwriter.text.TextCleaner;
import com.bookwriter.compat.BaseScreen;
import com.bookwriter.compat.Compat;


import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import com.mojang.blaze3d.platform.InputConstants;

import java.util.ArrayList;
import java.util.List;

/**
 * Shows what an import will do to the book (how many pages, what gets replaced, what does not
 * fit) with a page-by-page preview, before anything is changed.
 */
public class ImportPreviewScreen extends BaseScreen {
    /** Component area of a book page, the same in every supported version. */
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
    private final Component source;
    private final String rawText;
    /** True when importing {@link BookWriterMod#pendingText}, which this import then uses up. */
    private final boolean continuation;

    private String cleanText = "";
    private ImportPlanner.Plan plan;
    private int previewPage;
    private List<Component> summary = List.of();

    private Button importButton;
    private Button previousButton;
    private Button nextButton;

    public ImportPreviewScreen(Screen parent, BookImportTarget target, Component source, String rawText, boolean continuation) {
        super(Component.translatable("bookwriter.preview.title"));
        this.parent = parent;
        this.target = target;
        this.source = source;
        this.rawText = rawText;
        this.continuation = continuation;
    }

    public static BookPaginator.LineWrapper lineWrapper(Font textRenderer) {
        return (text, consumer) -> textRenderer.getSplitter()
            .splitLines(text, TEXT_WIDTH, Style.EMPTY, false, (style, start, end) -> consumer.accept(start, end));
    }

    private int left() {
        return (this.width - (PAGE_WIDTH + COLUMN_GAP + COLUMN_WIDTH)) / 2;
    }

    @Override
    protected void init() {
        int column = left() + PAGE_WIDTH + COLUMN_GAP;

        this.addRenderableWidget(Compat.<ImportMode>cycling(mode -> Component.translatable("bookwriter.mode." + mode.name().toLowerCase()), BookWriterMod.importMode)
            .withValues(ImportMode.values())
            .withTooltip(mode -> Tooltip.create(Component.translatable("bookwriter.mode." + mode.name().toLowerCase() + ".tooltip")))
            .create(column, TOP, COLUMN_WIDTH, 20, Component.translatable("bookwriter.mode"), (button, mode) -> {
                BookWriterMod.importMode = mode;
                this.recalculate();
            }));

        this.addRenderableWidget(CycleButton.onOffBuilder(BookWriterMod.tidyText)
            .withTooltip(value -> Tooltip.create(Component.translatable("bookwriter.tidy.tooltip")))
            .create(column, TOP + 24, COLUMN_WIDTH, 20, Component.translatable("bookwriter.tidy"), (button, value) -> {
                BookWriterMod.tidyText = value;
                this.recalculate();
            }));

        int navY = TOP + PAGE_HEIGHT + 4;
        this.previousButton = this.addRenderableWidget(Button.builder(Component.literal("<"), button -> this.flip(-1))
            .bounds(left(), navY, 20, 20).build());
        this.nextButton = this.addRenderableWidget(Button.builder(Component.literal(">"), button -> this.flip(1))
            .bounds(left() + PAGE_WIDTH - 20, navY, 20, 20).build());

        int buttonY = Math.min(this.height - 24, navY + 28);
        this.importButton = this.addRenderableWidget(Button.builder(Component.translatable("bookwriter.preview.import"), button -> this.confirm())
            .bounds(column, buttonY, 73, 20).build());
        this.addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, button -> this.onClose())
            .bounds(column + 77, buttonY, 73, 20).build());

        this.recalculate();
    }

    private void recalculate() {
        this.cleanText = TextCleaner.clean(this.rawText, BookWriterMod.tidyText);
        List<BookPaginator.Page> pages = BookPaginator.paginate(this.cleanText, lineWrapper(this.font), Compat.PAGE_LENGTH);
        this.plan = ImportPlanner.plan(this.target.bookwriter$getPages(), this.target.bookwriter$getCurrentPage(), this.cleanText, pages, BookWriterMod.importMode);
        this.previewPage = Math.min(this.previewPage, Math.max(0, this.plan.importedPages() - 1));
        this.importButton.active = this.plan.importedPages() > 0;

        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable(pages.size() == 1 ? "bookwriter.preview.size.one" : "bookwriter.preview.size", String.format("%,d", this.cleanText.length()), pages.size()));
        if (pages.isEmpty()) {
            lines.add(Component.translatable("bookwriter.preview.nothing").withStyle(ChatFormatting.RED));
        } else if (this.plan.importedPages() == 0) {
            lines.add(Component.translatable("bookwriter.preview.full").withStyle(ChatFormatting.RED));
        } else {
            int first = this.plan.firstNewPage() + 1;
            lines.add(this.plan.importedPages() == 1
                ? Component.translatable("bookwriter.preview.range.one", first, this.plan.pages().size())
                : Component.translatable("bookwriter.preview.range", first, first + this.plan.importedPages() - 1, this.plan.pages().size()));
        }
        if (this.plan.replacedPages() > 0 && this.plan.importedPages() > 0) {
            lines.add(Toasts.counted("bookwriter.preview.replaces", this.plan.replacedPages()).withStyle(ChatFormatting.GOLD));
        }
        if (this.plan.hasRemainder() && this.plan.importedPages() > 0) {
            lines.add(Toasts.counted("bookwriter.preview.overflow", this.plan.remainderPages()).withStyle(ChatFormatting.YELLOW));
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
            Toasts.show(Toasts.counted("bookwriter.toast.imported", this.plan.importedPages()), Component.translatable("bookwriter.toast.undo_hint"));
        }
        Compat.setScreen(this.parent);
    }

    @Override
    public void onClose() {
        Compat.setScreen(this.parent);
    }

    @Override
    protected boolean onKey(int keyCode, boolean ctrl, boolean shift, boolean alt) {
        switch (keyCode) {
            case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                // Enter on a focused button presses that button; otherwise it confirms.
                if (!(this.getFocused() instanceof Button) && this.importButton.active) {
                    this.confirm();
                    return true;
                }
                return false;
            }
            case InputConstants.KEY_PAGEUP -> {
                this.flip(-1);
                return true;
            }
            case InputConstants.KEY_PAGEDOWN -> {
                this.flip(1);
                return true;
            }
            default -> {
                return false;
            }
        }
    }

    @Override
    protected void renderContent(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        int left = left();
        int column = left + PAGE_WIDTH + COLUMN_GAP;

        context.centeredText(this.font, this.title, this.width / 2, 8, WHITE);
        String from = this.font.plainSubstrByWidth(Component.translatable("bookwriter.preview.source", this.source).getString(), PAGE_WIDTH + COLUMN_GAP + COLUMN_WIDTH);
        context.centeredText(this.font, from, this.width / 2, 20, GRAY);

        // The page being previewed, drawn the way the book will wrap it.
        context.fill(left - 1, TOP - 1, left + PAGE_WIDTH + 1, TOP + PAGE_HEIGHT + 1, PAPER_EDGE);
        context.fill(left, TOP, left + PAGE_WIDTH, TOP + PAGE_HEIGHT, PAPER);
        if (this.plan.importedPages() > 0) {
            String page = this.plan.pages().get(this.plan.firstNewPage() + this.previewPage);
            int y = TOP + 4;
            for (FormattedCharSequence line : this.font.split(Component.literal(page), TEXT_WIDTH)) {
                context.text(this.font, line, left + 4, y, INK, false);
                y += this.font.lineHeight;
            }
            Component indicator = Component.translatable("book.pageIndicator", this.plan.firstNewPage() + this.previewPage + 1, this.plan.pages().size());
            context.centeredText(this.font, indicator, left + PAGE_WIDTH / 2, TOP + PAGE_HEIGHT + 10, WHITE);
        }

        int y = TOP + 52;
        for (Component line : this.summary) {
            y = this.drawWrapped(context, line, column, y, COLUMN_WIDTH, WHITE) + 4;
        }
    }
}
