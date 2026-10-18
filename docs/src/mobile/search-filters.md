# Finding notes

The Notes page has three tools for finding things: **search**, **sort and group**, and the **filter panel**. They work together: the filters apply on top of the search, and the list shows what passes both.

## Search

Tap the magnifier (**Search**) in the top bar and type in **Search notes…**. The list narrows as you type.

Search looks at:

- the text of each note (highlight markers are ignored, so a phrase still matches across a highlight), and
- the type and category **codes** in the file name, such as `W` or `P`. It matches the short codes, not the full names shown in Settings.

The search is not case-sensitive.

!!! note "Opening search clears the filters"
    When you open search, the filters are cleared so that you search everything. Otherwise a note could be hidden by a filter you'd forgotten about, and the empty result would be misleading. Tap the **×** to close search and clear the text.

## Sort and group

The **Sort notes** button (the lines icon) opens a menu with two parts.

**Order**

| Option | Order |
|---|---|
| **Sort by Date** | Oldest at the top, newest at the bottom, like a chat. The list opens at the bottom. |
| **Sort by Category** | By category code. Notes with no category come last. |
| **Sort by Priority** | By priority number. Notes with no priority come last. |
| **Sort by Type** | By type code. Notes with no type come last. |

**Grouping**

| Option | Result |
|---|---|
| **No grouping** | One plain list. |
| **Group by Type** / **Group by Priority** / **Group by Category** | The list is split into collapsible sections such as **Untyped**, **Priority 3** or **Uncategorized**, each with a count. Tap a section header to fold or unfold it. |

## The filter panel

Above the list is a bar labelled **Filters**. When filters are on it reads **Filters (N)**, where N is how many, and shows *shown/total* on the right, so a short list is never mistaken for missing notes. Tap the bar to open or close the panel.

!!! tip "It starts on today"
    When the app starts, today is already selected on the calendar, so the list shows today's notes. Tap **Clear all** to see everything.

### Calendar

A month calendar, starting on Monday. Each day shows its date and, in brackets, how many notes fall on it, shaded darker for busier days.

- Tap a day to show only that day's notes. Tap more days to add them; tap a picked day again to drop it.
- **Previous month** / **Next month** arrows move the calendar; **Today** jumps back. Moving between months doesn't change what is picked.
- **Created** / **Updated** chooses which date counts:
    - **Created** is the time in the note's file name, which is when it was written. It doesn't change when you edit or retag the note.
    - **Updated** is when the file was last changed.

### Priority, Type, Category

Each shows its values as chips with a count in brackets. Pick one or more to show only those notes. The counts always show what you *would* get by picking that value with the other filters as they are, so an unpicked option doesn't read 0.

### Tags

A cloud of the `#hashtags` written inside your notes. Bigger text means a tag is used more often. Tap a tag to show only notes containing it, and tap it again to drop it. Tags are matched without regard to case, so `#Work` and `#work` are the same tag.

### Non-standard names

For files in the folder whose names don't follow the note scheme, such as a file another device dropped into a synced folder.

| Option | Shows |
|---|---|
| **All** | Everything |
| **Notes only** | Only properly named notes |
| **Unnamed only** | Only the non-standard files, which is a to-do list for renaming them |

### Bad taxonomy

For notes whose name is well-formed but uses a type, priority or category that isn't defined in your [Settings](settings.md) (for example a code you have since removed).

| Option | Shows |
|---|---|
| **All** | Everything |
| **Valid only** | Notes whose tags are all defined |
| **Bad only** | Notes with an undefined type, priority or category |

**Clear all** turns every filter off. It keeps your **Created** / **Updated** choice.

## The AI answers page

The [AI answers](ai-answers.md) page has the same search box and filter panel.
