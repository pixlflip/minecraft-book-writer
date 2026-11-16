# Book Writer

Import long text into a Minecraft book and quill. Paste from your clipboard or straight from a
web link, check the result in a preview, and Book Writer spreads it over as many pages as it
needs.

A client-side [Fabric](https://fabricmc.net/) mod for **Minecraft 1.20 through 26.3**. Works in
singleplayer and on servers; the server does not need it.

![A book and quill with the Paste Text, Paste Link and Undo Import buttons beside it](docs/images/book.png)

## Features

- **Paste Text**: import the clipboard across as many pages as it takes.
- **Paste Link**: import the readable text of a web page, Pastebin, Gist or public Google Doc.
- **Preview first**: see the page count and flip through every page before anything changes.
- **Choose where it goes**: at the end, after the page you are on, or replacing the book.
- **Undo Import**: one click puts the book back the way it was.
- **Longer than one book?** The text that does not fit is kept, so you can continue in the next book.
- **Pages that match the game**: text is wrapped with Minecraft's own line wrapping, and no
  line breaks are forced into your paragraphs, so imported pages stay easy to edit.

## Installing

1. Install [Fabric Loader](https://fabricmc.net/use/) for your Minecraft version.
2. Download the Book Writer jar for **your exact Minecraft version** from the
   [latest release](https://github.com/pixlflip/minecraft-book-writer/releases/latest). The file
   name ends in your version, for example `bookwriter-1.1.0+mc1.21.11.jar` for 1.21.11.
3. Put it in your `mods` folder.

Fabric API is not needed. Each jar only loads on the Minecraft version in its name, so if the
game says Book Writer is incompatible, check that you picked the matching file.

### Supported versions

| Minecraft | Jar |
| --- | --- |
| 26.1, 26.1.1, 26.1.2, 26.2, 26.3 | one jar per version |
| 1.21 – 1.21.11 | one jar per version |
| 1.20 – 1.20.6 | one jar per version |

Older versions are not supported: the book screen and the rendering API they use are too
different. On versions before 1.21.6 a page holds at most 1,023 characters (the game's own
limit there), one fewer than on newer versions.

## Using it

Open a book and quill. The Book Writer buttons sit beside the book, or in a row under
Sign / Done when the window is narrow.

| Button | What it does |
| --- | --- |
| **Paste Text** | Imports the clipboard. Shortcut: Ctrl+Shift+V (Cmd+Shift+V on macOS). Plain Ctrl+V still pastes into the current page as usual. |
| **Paste Link** | Asks for a link, downloads the page and imports its text. If the clipboard holds a link, it is filled in for you. |
| **Undo Import** | Puts the book back the way it was before the last import. |
| **Continue…** | Appears when the last import did not fit in one book. Open another book and press it to carry on where the text stopped. |

### The preview

Both paste buttons open a preview before the book is touched. It shows how many pages the
text needs and which pages it will fill, and lets you flip through them exactly as the book
will show them.

![The import preview showing the first imported page, the page count and the Import button](docs/images/link-preview.png)

Two settings live here:

- **Where**: *At The End* adds pages after the last page with text. *After This Page* adds
  them after the page you were on (or onto it, if it is blank). *Replace Book* starts over
  from page 1, and warns you how many pages it will discard.
- **Tidy Text**: removes invisible characters, trailing spaces and runs of blank lines that
  tend to come along when copying from websites and documents.

Press **Import** (or Enter) and the pages are written. A message confirms how many, and
**Undo Import** is there if it is not what you wanted.

![The book after an import, with a message saying four pages were imported](docs/images/imported.png)

### Text that is too long for one book

A book holds 100 pages. When the text needs more, the preview tells you how many pages will
not fit. Import anyway, then open another book and quill and press **Continue…** to carry on
from exactly where the first book stopped.

![The preview warning that 41 more pages do not fit and will be kept for another book](docs/images/overflow.png)

### Importing from a link

![The Import From Link screen with a web address typed in](docs/images/link.png)

Paste Link works with:

- **Web pages**: the article text is kept, and menus, sidebars, ads and footers are dropped.
- **Pastebin, GitHub Gists and GitHub files**: fetched as plain text.
- **Public Google Docs**: fetched as plain text. The document must be viewable by anyone
  with the link.
- **Direct links to text files**.

It cannot read pages that need a login, sit behind a paywall or build their text with
JavaScript, and it does not read PDFs. In those cases you get a message saying why.

**Privacy:** the page is downloaded by your own computer, directly from the site in the
link. Book Writer does not send your links or text anywhere else.

## Building

Requires Java 25 to run the build. Every Minecraft version is built from the same source:

```bash
./gradlew build -Pmc=1.21.11     # one Minecraft version
./build-all.sh                   # every supported version
```

The mod jar is written to `build/libs/` (`build-all.sh` collects them all in `dist/`).

How the code is laid out:

- `src/main`: everything that does not touch Minecraft (cleaning, pagination, import planning,
  link fetching) and the resources. Shared by every version.
- `src/yarn`: the screens, for 1.20 – 1.21.11 (Yarn names).
- `src/mc/<group>`: the book screen mixin and small compatibility shims for each group of
  versions whose APIs match: `y20` (1.20 – 1.20.1), `y20_2` (1.20.2 – 1.21.5), `y21_6`
  (1.21.6 – 1.21.8), `y21_9` (1.21.9 – 1.21.11) and `m26` (26.x, which ships with Mojang's own
  names and has its own copy of the screens).
- `versions/<mc>.properties`: which mappings, Java version and source group each Minecraft
  version uses. Adding a version is usually just adding a file here.

`./gradlew runSmokeTest -Pmc=<version>` starts the real game with a development-only test mod
that opens a book and quill, imports a long text through Book Writer and checks the result.
It needs a display; CI runs it under Xvfb for every version.

## License

[MIT](LICENSE)
