# Book Writer

A **client-side Fabric mod for Minecraft Java Edition 1.21.7** that imports clipboard text into writable books using the current Minecraft font for wrapping. No server plugin is required.

## Install

Requires Java 21, Fabric Loader 0.16.14 or later, and Fabric API for Minecraft 1.21.7. Put the built `bookwriter-1.1.0.jar` and Fabric API in your client's `mods` folder. Do not install this client-only mod on a dedicated server. Other Minecraft versions have not been verified.

## Use

1. Copy plain text, then open a **book and quill**.
2. Use the second button row below Sign and Done:
   - **Replace** replaces the whole book with the clipboard text.
   - **Append** starts the import on new pages after existing text. It preserves existing page contents and omits trailing empty editor placeholder pages.
   - **Undo** restores the pages and selected page from immediately before the latest successful import in this open editor.
3. The confirmation shows the imported page count. Cancel or Escape leaves the book unchanged.
4. Click **Done** to save, or sign the book using the normal Minecraft controls.

Undo is one level, belongs only to the current book-editing screen, and is lost when the editor is closed. It also discards manual edits made after the import; a confirmation warns before doing so. This is not a persistent book backup. Closing with Escape follows vanilla behavior; use Done to save.

## Limits and formatting

- Font-aware wrapping: 114 pixels per line and 14 lines per page.
- At most 100 pages, each at most 1,024 UTF-16 code units, including newlines.
- If the **entire import** does not fit, it is refused and no pages are changed. Split large texts across books; nothing is silently truncated.
- Clipboard inputs over 1,000,000 UTF-16 code units are refused to bound processing work.
- Windows/Mac line endings are normalized. Explicit blank lines and leading indentation are retained when they fit. Trailing spaces at automatic word-wrap boundaries may be removed.
- Supplementary Unicode characters are not split across lines/pages. Missing glyphs and custom font behavior depend on the client's resource packs.
- Book Writer does not interpret Markdown or provide rich-text editing. Server filtering/rules still apply to saved books.

## Build and test

Install a Java 21 JDK, then run:

```sh
./gradlew clean test build
```

Outputs are in `build/libs/`. Use `bookwriter-1.1.0.jar`, not the sources JAR or files under `build/devlibs/`.

For an interactive development client:

```sh
./gradlew runClient
```

The Gradle wrapper is checksum-pinned and Fabric Loom uses a stable pinned release. JUnit tests cover pagination boundaries, page/character limits, Unicode, overflow, and undo snapshots. GitHub Actions builds the mod and uploads the distributable JAR and test reports for each push/PR.

### In-game release checklist

- Open an existing book; test Replace and Append, confirm and cancel each, and check the page count.
- Test Undo, cancellation of Undo, and manual edits after an import.
- Verify empty/oversized clipboard and page overflow leave all pages unchanged.
- Test small GUI scales, keyboard navigation, tooltips, and resource-pack fonts.
- Click Done, reopen the book, and check persistence; also test signing, offhand books, and multiplayer server filtering.

## Repository layout

- `src/main/java`: Fabric entrypoint, book-screen mixin, pure pagination and import-session logic.
- `src/main/resources`: mod metadata, mixin configuration, English translations.
- `src/test/java`: unit/regression tests, independent of a running Minecraft client.

Build output, game runs, and Gradle caches are ignored, not source-controlled. Earlier commits still contain legacy caches; this change does not rewrite Git history.

## License

MIT; see [LICENSE](LICENSE).
