# Checklists

**Checklists** (the checklist icon, just under Calendar in the rail) keeps the small things you mean to do every day or every week: charging the phone, a review before bed, a call to the family. You tick them off day by day, and each week is kept as one plain Markdown file. They are kept apart from your tasks on purpose, so thirty small daily ticks never bury the real work.

## The page

- The top bar shows the week (**Week 43**), its dates, and **this week** or **not started** (a week with no file yet). The arrows go to the **Previous week** and **Next week**.
- **Day** / **Week** switch between the two views of the same week (see below).
- **This week** jumps back when you're looking at another week. On the current week, the same place offers **Plan next week**.
- The pencil (**Edit the lists**) and the note icon (**Edit as text**) are for changing the items. See [Edit the lists](#edit-the-lists).

The items are sorted into **groups**. A new checklist starts with **Start of day**, **Mid-day**, **End of day**, **Study** and **Weekly**, all empty. Rename, add or remove groups as you like.

### Day: ticking

The **Day** view is for ticking. Two rows of chips at the top pick the day, Monday to Sunday, each with how much of it is done (**75%**). Today is marked with a dot, and you can pick another day to fill in yesterday.

Tap an item to cycle its mark: done (✓), then partly done (◐), then not done. A partly done item counts as half. Each group's heading shows its percentage for the day.

The **Weekly** group is different: its items have one mark for the whole week rather than one per day.

### Week: looking back

The **Week** view shows every item against all seven days, with each day's completion over each group. You can tick here too; days still to come are dimmed.

## Edit the lists

Tap the pencil (**Edit the lists**). The page switches to editing until you tap it again (**Done editing**):

- **Add item** under a group adds items. The box stays open for the next one, so a long list is typed in one go; tap **Done** when finished.
- Tap an item or a group heading to rename it (**Rename item**, **Rename group**).
- The bin next to an item removes it (**Remove item**).
- **Add group** at the bottom adds a group. Only an empty group can be removed (**Remove group**).

Changes apply to the week on screen. The items carry over to the following weeks.

### Edit as text

For bigger changes (a term's courses pasted in, a group reordered), tap **Edit as text**. The whole week opens as one text:

```
## 1 Start of day
1.1 Check phone charge level
1.2 Review the plan

## 5 Weekly [weekly]
5.1 Call the family
```

One item per line, under a `## Group` heading. Add `[weekly]` after a group's name for once-a-week items. Paste, reorder or delete freely, then tap **Save**.

### Codes

Every group has a number and every item a code (`1.2` is item 2 of group 1). The code is shown small above each item's name, and it is what the ticks belong to: renaming an item keeps its ticks, and the same code is the same item from week to week. Items are listed in code order. In **Edit as text**, a line keeps its ticks as long as its code is unchanged, and giving it another code moves it. A line with no code is a new item in the group above it, numbered next.

## Weeks

Each week runs Monday to Sunday and has its own list. A week you haven't touched yet shows the latest earlier week's items with no marks; its file is only written once you tick or change something, so looking ahead leaves nothing behind. **Plan next week** copies this week's items to next week and opens it for editing, so you can adjust the list before the week begins.

## Reminders

Write a time in front of a group's title, for example rename **Start of day** to `07:30 Start of day`. Every day at 07:30 a quiet notification then names the group and says **Time to fill in its items.** Tapping it opens the page.

- The reminder is **silent**: no sound and no vibration.
- It is skipped on days when every item in the group already has a mark.
- Only daily groups have reminders, not the weekly one.

The time is part of the title, so it carries on to the next weeks with the group, and a time changed on a computer is picked up by the phone.

## Arabic items

An item or group whose text starts with Arabic letters is written right to left and aligned to the right, whatever language the app is in. Everything else reads left to right, so a list can mix both.

## Where the files are

Each week is one Markdown file named after it, for example `2026-W43.md`, in the **Checklists folder**. By default this is a `checklists` folder inside your notes folder, so it syncs with your notes. Change it in [Settings](settings.md) → **Checklists**. A file looks like this:

```
# Week 43 · 2026-10-19 – 2026-10-25

## 1 Start of day
| Item | Mon | Tue | Wed | Thu | Fri | Sat | Sun |
|---|---|---|---|---|---|---|---|
| 1.1 Check phone charge level | ✓ | ◐ |  |  |  |  |  |
```

It reads as a table in any Markdown viewer. If you edit it by hand, `x` also counts as done. When the file changes under the app (a sync from your computer), the page re-reads it when you come back to it.

The **Checklists** switch at the top of the same settings tab turns the feature off: the page leaves the rail and no reminders are set.
