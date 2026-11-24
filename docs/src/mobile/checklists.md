# Checklists

**Checklists** (the checklist icon, just under Calendar in the rail) keeps the small things you mean to do every day or every week: charging the phone, a review before bed, a call to the family. You tick them off day by day, and can add a note to any tick. With a Nibras server set up, the lists sync with the web, where you can tick and annotate them too. They are kept apart from your tasks on purpose, so thirty small daily ticks never bury the real work.

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

### Notes

**Long-press** an item's mark (in either view) to open that day: choose the mark outright (**Done**, **Partly done**, **Not done**) and write a **Note**, what was done or why not, then tap **Save**. A day with a note shows a small dot in the corner of its mark, and in Day view the note's first line appears under the item's name.

### Week: looking back

The **Week** view shows every item against all seven days, with each day's completion over each group. You can tick here too; days still to come are dimmed.

## Edit the lists

Tap the pencil (**Edit the lists**). The page switches to editing until you tap it again (**Done editing**):

- **Add item** under a group adds items. The box stays open for the next one, so a long list is typed in one go; tap **Done** when finished.
- Tap an item or a group heading to rename it (**Rename item**, **Rename group**).
- The bin next to an item removes it (**Remove item**).
- **Add group** at the bottom adds a group. Only an empty group can be removed (**Remove group**).

An item or group added while a week is on screen starts that week and carries on to the following ones. One removed while a week is on screen is taken off from that week on: earlier weeks keep it, with their ticks. Adding an item back under the same code brings the same item back, old ticks included. A name is the item's name in every week; renaming in one week renames it everywhere.

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

Each week runs Monday to Sunday and shows every item that is on the list in it. Items carry on from week to week by themselves, unticked. A week where nothing is ticked or noted yet is marked **not started**. **Plan next week** opens next week for editing, so you can adjust the list before the week begins.

## Reminders

Write a time in front of a group's title, for example rename **Start of day** to `07:30 Start of day`. Every day at 07:30 a quiet notification then names the group and says **Time to fill in its items.** Tapping it opens the page.

- The reminder is **silent**: no sound and no vibration.
- It is skipped on days when every item in the group already has a mark.
- Only daily groups have reminders, not the weekly one.

The time is part of the title, so it carries on to the next weeks with the group, and a time changed on the web is picked up by the phone at the next sync.

## Arabic items

An item or group whose text starts with Arabic letters is written right to left and aligned to the right, whatever language the app is in. Everything else reads left to right, so a list can mix both.

## Where the lists are kept

The lists are kept inside the app, not as files. Each group, item and day's tick (with its note) is a record, the same records the Nibras server keeps in its database.

### Syncing with the web

With a Nibras server set up and **Sync with Nibras** on (see [Nibras sync](sync.md)), the phone and the web exchange their changes: a few seconds after you tick or edit, when you open the page, and with every sync. The web's Checklists page can tick, write notes, add and rename items and groups, and take them off the list, and those changes arrive on the phone.

If the same tick (or item, or group) was changed on both sides between two syncs, one side wins. By default it is the phone; change it on the web: the **⋮** menu of the **Checklists** page, **The web's change stands**. Without a server, the lists simply stay on the phone.

### The old week files

Earlier versions kept each week as a Markdown file (`2026-W43.md`) in the **Checklists folder**. The first time this version opens, it reads those files in: items, groups and ticks keep their codes and dates, and an item that had disappeared from the latest week ends where it was last listed. The files are left where they are, untouched, and are no longer written; the Nibras server reads in its own copy the same way, so both land on the same records.

The **Checklists** switch at the top of [Settings](settings.md) → **Checklists** (where the Checklists folder is set too) turns the feature off: the page leaves the rail and no reminders are set.
