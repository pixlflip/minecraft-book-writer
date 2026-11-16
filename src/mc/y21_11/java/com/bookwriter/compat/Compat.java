package com.bookwriter.compat;

import net.minecraft.client.gui.widget.CyclingButtonWidget;
import net.minecraft.text.Text;

import java.util.function.Function;

/** Small API differences between Minecraft versions. */
public final class Compat {
    /** Longest page the book editor accepts. */
    public static final int PAGE_LENGTH = 1024;

    private Compat() {
    }

    public static <T> CyclingButtonWidget.Builder<T> cycling(Function<T, Text> names, T initial) {
        return CyclingButtonWidget.builder(names, initial);
    }
}
