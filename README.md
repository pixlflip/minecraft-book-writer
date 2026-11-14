# Book Writer

A client-side Fabric mod for Minecraft 1.21.7 that imports long text into a book and quill.
Paste from the clipboard or from a web link, and the text is spread over as many pages as it
needs.

## Using it

Open a book and quill. Book Writer adds buttons beside the book (or in a row under
Sign / Done when the window is narrow):

| Button | What it does |
| --- | --- |
| **Paste Text** | Imports the clipboard. Shortcut: Ctrl+Shift+V (Cmd+Shift+V on macOS). Plain Ctrl+V still pastes into the current page as usual. |
| **Paste Link** | Asks for a link, downloads the page and imports its text. If the clipboard holds a link, it is filled in for you. |
| **Undo Import** | Puts the book back the way it was before the last import. |
| **Continue…** | Appears when the last import did not fit in one book. Open another book and press it to carry on where the text stopped. |

Both paste buttons open a preview before anything changes. It shows how many pages the text
needs, lets you flip through them, and warns if existing pages would be replaced or if the
text is too long for one book (100 pages). There you choose:

- **Where** the pages go: at the end, after the page you were on, or replacing the book.
- **Tidy Text**: removes invisible characters, trailing spaces and runs of blank lines.

Pages break exactly where the game would break them, because the mod uses Minecraft's own
line wrapping. No line breaks are added inside paragraphs, so imported pages stay editable.

## Links

Paste Link works with ordinary web pages (it keeps the article and drops menus, ads and
footers) and has plain-text shortcuts for Pastebin, GitHub Gists, GitHub files and public
Google Docs. Direct links to `.txt` files work too.

It cannot read pages that need a login, sit behind a paywall or build their text with
JavaScript, and it does not read PDFs. In those cases you get a message saying why.

The download is made from your computer, straight to the site in the link. Nothing is sent
anywhere else.

## Building

Requires Java 21.

```bash
./gradlew build
```

The mod jar is written to `build/libs/`. It needs Fabric Loader and Fabric API.

## License

MIT
