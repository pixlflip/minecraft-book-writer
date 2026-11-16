package com.bookwriter.smoketest;

import com.bookwriter.compat.Compat;
import com.bookwriter.gui.BookImportTarget;
import com.bookwriter.gui.ImportPreviewScreen;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.BookEditScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;

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
            Minecraft client = waitFor("the game to load", () -> {
                Minecraft c = Minecraft.getInstance();
                return c != null && !Compat.overlayActive() && Compat.currentScreen() != null ? c : null;
            });
            pause();

            // Create a world through the normal Create New World screen. The title screen can
            // still replace it while the game settles after loading, so retry until it sticks.
            boolean[] created = new boolean[1];
            for (int attempt = 0; attempt < 5 && !created[0]; attempt++) {
                onClient(client, () -> openCreateWorld(client));
                waitFor("the create world screen", () -> Compat.currentScreen() instanceof CreateWorldScreen ? Boolean.TRUE : null);
                pause();
                onClient(client, () -> {
                    if (Compat.currentScreen() instanceof CreateWorldScreen) {
                        press(button(Compat.currentScreen(), "selectWorld.create"));
                        created[0] = true;
                    }
                });
            }
            check(created[0], "could not create a world");
            // Some versions ask for confirmation first (experimental features, for example): accept it.
            waitFor("the world to load", () -> {
                if (client.player != null && client.level != null && Compat.currentScreen() == null) {
                    return Boolean.TRUE;
                }
                Screen screen = Compat.currentScreen();
                if (screen != null && screen.children().stream().anyMatch(e -> key(e).equals("gui.yes"))) {
                    client.execute(() -> {
                        if (Compat.currentScreen() == screen) {
                            press(button(screen, "gui.yes"));
                        }
                    });
                }
                return null;
            });
            pause();

            // A book and quill in every hotbar slot, given by the server so it is really there.
            UUID id = client.player.getUUID();
            client.getSingleplayerServer().submit(() -> {
                ServerPlayer player = client.getSingleplayerServer().getPlayerList().getPlayer(id);
                for (int slot = 0; slot < 9; slot++) {
                    player.getInventory().setItem(slot, new ItemStack(Items.WRITABLE_BOOK));
                }
            }).get();
            waitFor("the book to reach the client", () -> client.player.getMainHandItem().is(Items.WRITABLE_BOOK) ? Boolean.TRUE : null);

            StringBuilder text = new StringBuilder();
            for (int i = 0; i < 1500; i++) {
                text.append("Word").append(i).append(i % 40 == 39 ? "\n\n" : " ");
            }
            Screen[] book = new Screen[1];
            onClient(client, () -> {
                client.keyboardHandler.setClipboard(text.toString());
                book[0] = openHeldBook(client);
            });
            pause();

            onClient(client, () -> {
                check(Compat.currentScreen() == book[0], "book screen did not open: " + Compat.currentScreen());
                check(book[0] instanceof BookImportTarget, "mixin was not applied to BookEditScreen");
                long ours = book[0].children().stream().filter(e -> key(e).startsWith("bookwriter.")).count();
                check(ours >= 3, "expected the Book Writer buttons, found " + ours);
                press(button(book[0], "bookwriter.button.paste"));
                check(Compat.currentScreen() instanceof ImportPreviewScreen, "Paste Text did not open the preview: " + Compat.currentScreen());
            });
            pause();

            int[] imported = new int[1];
            onClient(client, () -> {
                press(button(Compat.currentScreen(), "bookwriter.preview.import"));
                check(Compat.currentScreen() == book[0], "Import did not return to the book: " + Compat.currentScreen());
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
                Compat.setScreen(null);
                System.out.println("BOOKWRITER SMOKETEST PASS");
                client.stop();
            });
        } catch (Throwable t) {
            System.out.println("BOOKWRITER SMOKETEST FAIL: " + t);
            t.printStackTrace(System.out);
            Runtime.getRuntime().halt(1);
        }
    }

    /** Opens the book and quill in the main hand. The constructor differs between versions. */
    private static Screen openHeldBook(Minecraft client) {
        ItemStack stack = client.player.getMainHandItem();
        try {
            for (Constructor<?> constructor : BookEditScreen.class.getConstructors()) {
                Class<?>[] types = constructor.getParameterTypes();
                Object[] args = new Object[types.length];
                args[0] = client.player;
                args[1] = stack;
                args[2] = InteractionHand.MAIN_HAND;
                if (types.length == 4) {
                    // 1.20.5+: the book's content component.
                    Object type = Class.forName("net.minecraft.core.component.DataComponents").getField("WRITABLE_BOOK_CONTENT").get(null);
                    args[3] = componentGetter().invoke(stack, type);
                }
                Screen screen = (Screen) constructor.newInstance(args);
                Compat.setScreen(screen);
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
    private static void openCreateWorld(Minecraft client) {
        Screen parent = Compat.currentScreen();
        try {
            for (Method m : CreateWorldScreen.class.getMethods()) {
                Class<?>[] p = m.getParameterTypes();
                if (Modifier.isStatic(m.getModifiers()) && p.length == 2 && p[0] == Minecraft.class
                    && (p[1] == Runnable.class || p[1] == Screen.class)) {
                    Runnable back = () -> Compat.setScreen(parent);
                    m.invoke(null, client, p[1] == Runnable.class ? back : parent);
                    return;
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
        throw new IllegalStateException("cannot open the create world screen");
    }

    private static String key(GuiEventListener element) {
        if (element instanceof Button button && button.getMessage().getContents() instanceof TranslatableContents t) {
            return t.getKey();
        }
        return "";
    }

    private static Button button(Screen screen, String key) {
        for (GuiEventListener element : screen.children()) {
            if (key(element).equals(key)) {
                return (Button) element;
            }
        }
        throw new AssertionError("no " + key + " button on " + screen);
    }

    /** Presses a button the way a click would. onPress takes an input argument from 1.21.9. */
    private static void press(Button button) {
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

    private static void onClient(Minecraft client, Runnable step) throws Exception {
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

    /** What is on screen, for failure messages: the screen and its button labels. */
    private static String describeScreen() {
        try {
            Screen screen = Compat.currentScreen();
            if (screen == null) {
                return " (no screen open)";
            }
            StringBuilder out = new StringBuilder(" (screen: ").append(screen.getClass().getName()).append(", buttons:");
            screen.children().forEach(e -> out.append(' ').append(key(e)));
            return out.append(')').toString();
        } catch (RuntimeException e) {
            return " (screen unknown: " + e + ")";
        }
    }

    private static <T> T waitFor(String what, Supplier<T> supplier) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 300_000;
        while (System.currentTimeMillis() < deadline) {
            T value;
            try {
                value = supplier.get();
            } catch (NullPointerException notReadyYet) {
                value = null;
            }
            if (value != null) {
                return value;
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("timed out waiting for " + what + describeScreen());
    }
}
