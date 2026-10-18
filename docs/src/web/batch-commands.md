# Batch edit & batch entry

Nibras has a small **command language** for setting fields by typing instead of clicking. The same
tokens work when editing many records at once (**batch edit**), when creating many records from
lines of text (**batch entry**), in **Import files**, and — as filters — in
[global search](search.md#global-search).

## The tokens

| Token | Sets | Example |
|---|---|---|
| `p3` | priority | `p2` |
| `#code` | type (codes of *this* module's types) | `#art` |
| `?code` | status | `?done` |
| `@code` | context | `@home` |
| `dCODE` | department | `dA` |
| `cCODE` | course | `cALG` |
| `=code` | the record's own code field | `=alg` |
| `*` / `*-` | bookmark / remove bookmark | `*` |
| `!` / `!-` | urgent / not urgent | `!` |
| `(DATE` | start date | `(2026-07-17` |
| `)DATE` | end date | `)+3` |
| `'xx` | language | `'en` |

Codes are the short codes set on the [Parameters](administration.md#parameters) page and on
departments and courses. Codes resolve against the module you are working in, so `#art` on a
resource means a *resource* type.

### Writing dates

`DATE` accepts several forms; the time defaults to 00:00 and the year to the current one:

| Form | Meaning |
|---|---|
| `2026-07-17` | a full date |
| `2026-07-17_1600` | a full date at 16:00 |
| `295` | ISO week 29, day 5 (Friday; 1 = Monday … 7 = Sunday) |
| `295_25` | the same, in 2025 |
| `295_1600` | week 29, Friday, 16:00 |
| `-1`, `+3`, `0` | days relative to today |
| `-1_1600` | yesterday at 16:00 |

### Language tags

A leading apostrophe plus one of the language codes from
[Settings](settings.md#tasks-and-other-preferences) (by default `ar`, `en`, `fr`, `fa`, `de`) sets a
record's language. Anything not in the list stays ordinary text, so words like *don't* are never
mistaken for a tag. See [Languages](../concepts/languages.md).

## Batch edit

1. In a module list, tick the checkboxes of the records to change (the checkbox in the header
   selects the whole page).
2. A blue bar appears: **n selected**. Type a command, e.g. `p3 ?done *-`.
3. Press **Enter** or click **Apply**. **Clear** cancels the selection.

A message confirms how many records were updated and which fields changed. Every change is recorded
in the audit log. In batch edit an unknown token is an error, so nothing is changed by a typo.

## Batch entry

**Batch entry** (sidebar) creates one record per line.

1. Choose the **Default module**.
2. Type or paste lines. Tokens set fields; the remaining words become the title. A line may start
   with a module code to override the default:

    ```text
    t p3 ?done @home a task done at home
    n a quick note
    c dA =alg Algebra
    ```

3. Click **Add n records**.

Created records are shown as cards. Lines that failed stay in the box with their error so you can
fix them and run again.

!!! tip
    In batch entry, a `cCODE` or `dCODE` that doesn't match a real course or department is kept as
    part of the title, so a word like "car" isn't swallowed.

You can create tasks, journals, plans, resources, notes, goals, writings and courses this way (those
that are enabled).
