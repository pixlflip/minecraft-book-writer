package com.bookwriter.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.toast.SystemToast;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;

public final class Toasts {
    private Toasts() {
    }

    /** Translates {@code key}, or {@code key + ".one"} when {@code count} is 1, with the count as its argument. */
    public static MutableText counted(String key, int count) {
        return Text.translatable(count == 1 ? key + ".one" : key, count);
    }

    public static void show(Text title, @Nullable Text description) {
        MinecraftClient client = MinecraftClient.getInstance();
        // show() updates a toast that is still on screen instead of stacking a second one.
        SystemToast.show(client.getToastManager(), SystemToast.Type.PERIODIC_NOTIFICATION, title, description);
    }
}
