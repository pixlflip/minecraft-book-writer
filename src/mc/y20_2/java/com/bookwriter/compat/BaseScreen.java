package com.bookwriter.compat;


import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Element;
import org.jetbrains.annotations.Nullable;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

/** Screen base for Book Writer's own screens: hides the input and background API differences between versions. */
public abstract class BaseScreen extends Screen {
    protected BaseScreen(Text title) {
        super(title);
    }

    /** Handles a key press; return true to consume it. */
    protected abstract boolean onKey(int keyCode, boolean ctrl, boolean shift, boolean alt);

    /** Draws the screen's own text on top of its widgets. */
    protected abstract void renderContent(DrawContext context, int mouseX, int mouseY, float delta);

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.onKey(keyCode, hasControlDown(), hasShiftDown(), hasAltDown())) {
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /** The widget to focus when the screen opens, or null for the default. */
    @Nullable
    protected Element initialFocus() {
        return null;
    }

    /** Call at the end of init(): not every version in this range has an initial-focus hook. */
    protected void applyInitialFocus() {
        Element element = this.initialFocus();
        if (element != null) {
            this.setInitialFocus(element);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {

        super.render(context, mouseX, mouseY, delta);
        this.renderContent(context, mouseX, mouseY, delta);
    }

    /** Draws {@code text} wrapped to {@code width} and returns the y below it. */
    protected int drawWrapped(DrawContext context, Text text, int x, int y, int width, int color) {
        for (OrderedText line : this.textRenderer.wrapLines(text, width)) {
            context.drawTextWithShadow(this.textRenderer, line, x, y, color);
            y += this.textRenderer.fontHeight;
        }
        return y;
    }
}
