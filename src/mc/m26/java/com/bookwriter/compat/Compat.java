package com.bookwriter.compat;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.toasts.ToastManager;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

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

    public static void setScreen(@Nullable Screen screen) {
        Minecraft.getInstance().setScreen(screen);
    }

    @Nullable
    public static Screen currentScreen() {
        return Minecraft.getInstance().screen;
    }

    public static ToastManager toastManager() {
        return Minecraft.getInstance().getToastManager();
    }

    /** True while a loading overlay covers the game. */
    public static boolean overlayActive() {
        return Minecraft.getInstance().getOverlay() != null;
    }
}
