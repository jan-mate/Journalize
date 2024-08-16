# Journalize

![Journalize Logo](journalize_black_logo.png#gh-light-mode-only)
![Journalize Logo](journalize_white_logo.png#gh-dark-mode-only)

A fast to use android journaling app.

## Features

- Renders in markdown (images included)
- Automatic pseudo-markdown rendering
- Automatically generates next start char in lists
- Import, export and share
- Allows geo-stamping of entries
- Custom tags for the entries
- Browse entries
- Search entries based on content and tags
- Quickly open previous entry
- Automatically open new entry after reopening app after custom duration
- Black/White mode. Both greyscale.


## Usage

Journalize is build around entries, an entry is a single record containing the content you write, a timestamp, and location (if you allow it).

### Tags
Above the keyboard theres two rows of buttons, the first one is for tags, by default it contains "Do, Philosophy, Quote, Book, Emotion", these can be changed in settings. Tags help you organize entries, and can later be used for searching. Pressing the buttons toggle the tags.

### Second button row
This row contains 5 buttons:

#### New
This makes a new empty entry

#### Last
This opens the last modified entry that's not currently open

#### Image
This lets you add images, however you need to render the entry to see them.

#### Render/Edit
Pressing this button renders the text using Markdown.
Pressing it again lets you edit the entry.

#### Menu
This lets you open a list of all entries. From here you can open settings by pressing on the menu in top right corner and pressing `Settings`.
You can also select each entry individually or select all shown entries by pressing `Select All Shown` in the overflow menu.

##### Searching
You can use special keywords `AND`/`∧` and `OR`/`∨`to refine your search queries. `AND`/`∧` takes precedence.
You can further refine search by pressing on the tag icon in top right corner and then pressing on tags you want to allow.

##### Settings
To update values remember to press `Done`/`Return`.
Before exporting you need to choose a directory.
Importing will delete your current entries.

### Automatic Pseudo-MarkDown rendering
When typing in an entry, text will be modified to look more like MarkDown, here are some examples:

- \*Italics* or \_Italics_
- \*\*Bold** or \_\_Bold__
- \`Monotone/code`

#### Titles can be made by starting lines with 1 to 6 #'s

Lists can be made by starting a line with one of 3 methods
1. \-
2. \*
3. \+
4. A number followed by a dot. Like this list.

When typing in a list, pressing `Return` will automatically create a new list item on the next line. If you press `Return` again while the list item is empty, the list element will be removed, and the cursor will move to a new line without continuing the list.


## Alternatives

This app is made for journalling, while it does work for diaries, I'd recommend using other apps like
- [Diary](https://github.com/billthefarmer/diary)
- [Easy Diary](https://github.com/hanjoongcho/aaf-easydiary)
