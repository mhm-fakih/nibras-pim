# Languages

Nibras is written with bilingual, right-to-left use in mind. There are two separate questions:
**which language the apps speak** (their menus and messages) and **how they lay out the text you
write** (in any language).

## Interface languages

| App | Interface |
|-----|-----------|
| [knotes for Android](../mobile/index.md) | **English, Arabic (عربي) or French (Français)** — your choice |
| [Web app](../web/index.md), [desktop](../desktop/index.md), [terminal](../terminal/index.md), [audio player](../audio-player/index.md) | English |

On the phone, the language picker is the first page of the setup tour and the first row of
Settings. Each language is listed in its own script, so you can find yours whatever the screen is
currently showing. The switch is immediate. Until you choose, the app follows the phone's
language when it is one of the three.

On Android 13 and later the choice is the system's per-app language, so it also appears (and can be
changed) under the system's *App languages* settings, and widgets and notifications follow it too.

### Arabic: right-to-left, with Latin digits

- **The whole interface mirrors.** Navigation, lists and controls are laid out right to left.
- **Digits stay Latin** (`12:30`, not `١٢:٣٠`). Many numbers the app formats end up in data —
  bookmark timestamps, file names, values sent to the server — and a change of digit shape there
  would corrupt files that other tools and devices read. Latin digits are also what many Arabic
  readers use day to day.

### What is and isn't translated

| Translated | Never translated |
|------------|------------------|
| Menus, buttons, settings, dialogs, messages | **Your notes and files** — their text and their names |
| Widget labels and notification texts | Your type and category names (your own vocabulary, or the server's) |
| Date wording such as Hijri month names | Records and their content on the server |
| | Bookmark labels and transcripts |

!!! note
    The prayer-times widget is always Arabic and right-to-left, whatever the interface language —
    it uses the short Arabic prayer names, with Latin digits.

## Arabic text in your notes

Whatever the interface language, **each note lays itself out by its own content**:

- A line or paragraph is right-to-left when its **first strong letter** is Arabic, and
  left-to-right otherwise. So an Arabic note right-aligns automatically, an English note
  left-aligns, and a single quoted Arabic word inside an English sentence doesn't flip the whole
  line.
- Mixed notes work line by line: an English heading over an Arabic paragraph gets both halves right.
- Titles and file names (for example Arabic track and folder names in the phone's player)
  right-align by the same rule.
- Arabic `#hashtags` are recognised like any other.

App by app:

| App | Arabic text |
|-----|-------------|
| Phone | Cards, reader and editor align per line, automatically. |
| Desktop | Each paragraph picks its own direction; the side editor switches to a right-to-left pane for an Arabic note, so empty lines and numbers start on the right side. Tags stay left-to-right (`T 2 A`). A badge shows the active keyboard layout (`EN` / `ع`). |
| Web app | Text fields and displayed text detect direction automatically. |
| Audio player | Arabic transcripts are right-aligned. |
| Terminal | Relies on the terminal to shape and order Arabic (most modern terminals do). If yours doesn't, set `NIBRAS_RESHAPE=1` before starting it. The input line itself stays left-aligned. |

## Tagging a record's language

The web app understands a small convention for the *content* language of records: a word made of
an apostrophe and a language code — `'en`, `'ar`, `'fr` — on its own.

- In **search** it filters: `prayer 'ar` finds Arabic records matching "prayer".
- On **import**, such a tag in a note's first line sets the new record's language and is removed
  from the title.

Only codes listed in the web app's settings (*Language codes*, by default `ar,en,fr,fa,de`) are
recognised, so an ordinary apostrophe in your text is never mistaken for one.
