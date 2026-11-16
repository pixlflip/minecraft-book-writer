package com.bookwriter.compat;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jetbrains.annotations.Nullable;

/** Screen base for Book Writer's own screens: hides the input and rendering API differences between versions. */
public abstract class BaseScreen extends Screen {
    protected BaseScreen(Component title) {
        super(title);
    }

    /** Handles a key press; return true to consume it. */
    protected abstract boolean onKey(int keyCode, boolean ctrl, boolean shift, boolean alt);

    /** Draws the screen's own text on top of its widgets. */
    protected abstract void renderContent(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta);

    /** The widget to focus when the screen opens, or null for the default. */
    @Nullable
    protected GuiEventListener initialFocus() {
        return null;
    }

    /** Kept for older builds, which have no initial-focus hook; here the hook below does it. */
    protected void applyInitialFocus() {
    }

    @Override
    protected void setInitialFocus() {
        GuiEventListener element = this.initialFocus();
        if (element != null) {
            this.setInitialFocus(element);
        } else {
            super.setInitialFocus();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (this.onKey(event.key(), event.hasControlDownWithQuirk(), event.hasShiftDown(), event.hasAltDown())) {
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        this.renderContent(context, mouseX, mouseY, delta);
    }

    /** Draws {@code text} wrapped to {@code width} and returns the y below it. */
    protected int drawWrapped(GuiGraphicsExtractor context, Component text, int x, int y, int width, int color) {
        for (FormattedCharSequence line : this.font.split(text, width)) {
            context.text(this.font, line, x, y, color);
            y += this.font.lineHeight;
        }
        return y;
    }
}
