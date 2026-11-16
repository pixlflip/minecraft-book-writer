package com.bookwriter.smoketest;

import com.bookwriter.compat.Compat;
import com.bookwriter.gui.BookImportTarget;
import com.bookwriter.gui.ImportPreviewScreen;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.screen.world.CreateWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.TranslatableTextContent;
import net.minecraft.util.Hand;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Test-only client mod, never packaged in a release. In the real game it creates a world, gives
 * the player a book and quill, opens it, imports a long text through Book Writer's own buttons,
 * presses Done, reopens the book from the inventory to check the pages were saved, and quits.
 */
public class SmokeTest implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        Thread thread = new Thread(SmokeTest::run, "bookwriter-smoketest");
        thread.setDaemon(true);
        thread.start();
    }

    private static void run() {
        try {
            MinecraftClient client = waitFor("the game to load", () -> {
                MinecraftClient c = MinecraftClient.getInstance();
                return c != null && c.getOverlay() == null && c.currentScreen != null ? c : null;
            });
            pause();

            // Create a world through the normal Create New World screen.
            onClient(client, () -> openCreateWorld(client));
            // It loads the default data packs first, then appears.
            waitFor("the create world screen", () -> client.currentScreen instanceof CreateWorldScreen ? Boolean.TRUE : null);
            pause();
            onClient(client, () -> press(button(client.currentScreen, "selectWorld.create")));
            waitFor("the world to load", () -> client.player != null && client.world != null && client.currentScreen == null ? Boolean.TRUE : null);
            pause();

            // A book and quill in every hotbar slot, given by the server so it is really there.
            UUID id = client.player.getUuid();
            client.getServer().submit(() -> {
                ServerPlayerEntity player = client.getServer().getPlayerManager().getPlayer(id);
                for (int slot = 0; slot < 9; slot++) {
                    player.getInventory().setStack(slot, new ItemStack(Items.WRITABLE_BOOK));
                }
            }).get();
            waitFor("the book to reach the client", () -> client.player.getMainHandStack().isOf(Items.WRITABLE_BOOK) ? Boolean.TRUE : null);

            StringBuilder text = new StringBuilder();
            for (int i = 0; i < 1500; i++) {
                text.append("Word").append(i).append(i % 40 == 39 ? "\n\n" : " ");
            }
            Screen[] book = new Screen[1];
            onClient(client, () -> {
                client.keyboard.setClipboard(text.toString());
                book[0] = openHeldBook(client);
            });
            pause();

            onClient(client, () -> {
                check(client.currentScreen == book[0], "book screen did not open: " + client.currentScreen);
                check(book[0] instanceof BookImportTarget, "mixin was not applied to BookEditScreen");
                long ours = book[0].children().stream().filter(e -> key(e).startsWith("bookwriter.")).count();
                check(ours >= 3, "expected the Book Writer buttons, found " + ours);
                press(button(book[0], "bookwriter.button.paste"));
                check(client.currentScreen instanceof ImportPreviewScreen, "Paste Text did not open the preview: " + client.currentScreen);
            });
            pause();

            int[] imported = new int[1];
            onClient(client, () -> {
                press(button(client.currentScreen, "bookwriter.preview.import"));
                check(client.currentScreen == book[0], "Import did not return to the book: " + client.currentScreen);
                List<String> pages = ((BookImportTarget) book[0]).bookwriter$getPages();
                imported[0] = pages.size();
                check(imported[0] > 5, "expected many imported pages, found " + imported[0]);
                for (String page : pages) {
                    check(page.length() <= Compat.PAGE_LENGTH, "page over the character limit: " + page.length());
                }
                check(String.join(" ", pages).contains("Word1499"), "the end of the text is missing");
                System.out.println("BOOKWRITER SMOKETEST imported " + imported[0] + " pages");
            });
            pause();

            // Done saves the book to the server; reopen it from the inventory.
            onClient(client, () -> press(button(book[0], "gui.done")));
            pause();
            onClient(client, () -> {
                Screen reopened = openHeldBook(client);
                int saved = ((BookImportTarget) reopened).bookwriter$getPages().size();
                check(saved == imported[0], "the saved book has " + saved + " pages, expected " + imported[0]);
                System.out.println("BOOKWRITER SMOKETEST saved " + saved + " pages");
            });
            pause();

            onClient(client, () -> {
                client.setScreen(null);
                System.out.println("BOOKWRITER SMOKETEST PASS");
                client.scheduleStop();
            });
        } catch (Throwable t) {
            System.out.println("BOOKWRITER SMOKETEST FAIL: " + t);
            t.printStackTrace(System.out);
            Runtime.getRuntime().halt(1);
        }
    }

    /** Opens the book and quill in the main hand. The constructor differs between versions. */
    private static Screen openHeldBook(MinecraftClient client) {
        ItemStack stack = client.player.getMainHandStack();
        try {
            for (Constructor<?> constructor : BookEditScreen.class.getConstructors()) {
                Class<?>[] types = constructor.getParameterTypes();
                Object[] args = new Object[types.length];
                args[0] = client.player;
                args[1] = stack;
                args[2] = Hand.MAIN_HAND;
                if (types.length == 4) {
                    // 1.20.5+: the book's content component.
                    Object type = Class.forName("net.minecraft.component.DataComponentTypes").getField("WRITABLE_BOOK_CONTENT").get(null);
                    args[3] = componentGetter().invoke(stack, type);
                }
                Screen screen = (Screen) constructor.newInstance(args);
                client.setScreen(screen);
                return screen;
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        throw new IllegalStateException("no BookEditScreen constructor");
    }

    private static Method componentGetter() {
        for (Method m : ItemStack.class.getMethods()) {
            if (m.getName().equals("get") && m.getParameterCount() == 1) {
                return m;
            }
        }
        throw new IllegalStateException("no ItemStack.get");
    }

    /** CreateWorldScreen.create(client, parent) or .show(client, Runnable), depending on version. */
    private static void openCreateWorld(MinecraftClient client) {
        Screen parent = client.currentScreen;
        try {
            for (Method m : CreateWorldScreen.class.getMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (Modifier.isStatic(m.getModifiers()) && p.length == 2 && p[0] == MinecraftClient.class
                    && (p[1] == Runnable.class || p[1] == Screen.class)) {
                    Runnable back = () -> client.setScreen(parent);
                    m.invoke(null, client, p[1] == Runnable.class ? back : parent);
                    return;
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        throw new IllegalStateException("cannot open the create world screen");
    }

    private static String key(Element element) {
        if (element instanceof ButtonWidget button && button.getMessage().getContent() instanceof TranslatableTextContent t) {
            return t.getKey();
        }
        return "";
    }

    private static ButtonWidget button(Screen screen, String key) {
        for (Element element : screen.children()) {
            if (key(element).equals(key)) {
                return (ButtonWidget) element;
            }
        }
        throw new AssertionError("no " + key + " button on " + screen);
    }

    /** Presses a button the way a click would. onPress takes an input argument from 1.21.9. */
    private static void press(ButtonWidget button) {
        check(button.active && button.visible, "button is not usable: " + key(button));
        try {
            for (Method m : button.getClass().getMethods()) {
                if (m.getName().equals("onPress") && m.getParameterCount() <= 1) {
                    m.invoke(button, m.getParameterCount() == 0 ? new Object[0] : new Object[] {null});
                    return;
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        throw new IllegalStateException("cannot press " + key(button));
    }

    private static void onClient(MinecraftClient client, Runnable step) throws Exception {
        client.submit(step).get();
    }

    private static void pause() throws InterruptedException {
        // Let the game render a few seconds of frames on the current screen.
        Thread.sleep(3000);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static <T> T waitFor(String what, Supplier<T> supplier) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 600_000;
        while (System.currentTimeMillis() < deadline) {
            T value = supplier.get();
            if (value != null) {
                return value;
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("timed out waiting for " + what);
    }
}
