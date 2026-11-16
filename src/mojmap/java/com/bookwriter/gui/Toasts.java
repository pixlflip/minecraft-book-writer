package com.bookwriter.gui;

import com.bookwriter.compat.Compat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

public final class Toasts {
    private Toasts() {
    }

    /** Translates {@code key}, or {@code key + ".one"} when {@code count} is 1, with the count as its argument. */
    public static MutableComponent counted(String key, int count) {
        return Component.translatable(count == 1 ? key + ".one" : key, count);
    }

    public static void show(Component title, @Nullable Component description) {
        Minecraft client = Minecraft.getInstance();
        // show() updates a toast that is still on screen instead of stacking a second one.
        SystemToast.addOrUpdate(Compat.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION, title, description);
    }
}
