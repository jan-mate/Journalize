# Journalize guide

Journalize is build around entries, an entry is a single record containing the content you write, a timestamp, and location (if you allow it).
Currently you're reading an entry, and it works just like any other entry, which means you can edit it, feel free to test out the app by editing this entry.

## Tags
Above the keyboard theres two rows of buttons, the first one is for tags, by default it contains "To-Do, Emotion, Book, Qoute, Philosophy", these can be changed in settings. Tags help you organize entries, and can later be used for searching. Pressing the buttons toggle the tags.

## Second button row
This row contains 5 buttons:

### New
This makes a new empty entry

### Last
This opens the last modified entry that's not currently open

### Image
This lets you add images, however you need to render the entry to see them.

### Render/Edit
Pressing this button renders the text using Markdown.
Pressing it again lets you edit the entry.

### Menu
This lets you open a list of all entries. From here you can open settings by pressing on the menu in top right corner and pressing `Settings`.
You can also select each entry individually or select all entries that are shown by pressing `Select All Shown` in the menu.

#### Searching
The search by will open automatically at start, in here you can search for entries that contain specific content.
You can use special keywords `AND`/`∧` and `OR`/`∨`to refine your search queries. And takes precedence.
You can further refine search by pressing on the tag icon in top right corner and then pressing on tags you want to allow.

#### Settings
To update values remember to press `Done`/`Return`.
Before exporting you need to choose a directory.
Importing will delete your current entries.

## Automatic Pseudo-MarkDown rendering
When typing in an entry, text will be modified to look more like MarkDown, here are some examples:

- *Italics* or _Italics_
- **Bold**
- `Monotone/code`
- ~~strikethrough~~ (not rendered)

### Titles can be made by starting lines with 1 to 6 #'s

Lists can be made by starting a line with one of 3 methods
1. -
2. * (equivelent to -)
3. A number followed a dot. Like this list.

When typing in a list, pressing `Return` will automatically create a new list item on the next line. If you press `Return` again while the list item is empty, the list element will be removed, and the cursor will move to a new line without continuing the list.