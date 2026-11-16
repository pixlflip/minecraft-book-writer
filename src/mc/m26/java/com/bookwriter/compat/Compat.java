package com.bookwriter.compat;

import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.network.chat.Component;

import java.util.function.Function;

/** Small API differences between Minecraft versions. */
public final class Compat {
    /** Longest page the book editor accepts. */
    public static final int PAGE_LENGTH = 1024;

    private Compat() {
    }

    public static <T> CycleButton.Builder<T> cycling(Function<T, Component> names, T initial) {
        return CycleButton.builder(names, initial);
    }
}
