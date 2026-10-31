# Home-screen widgets

Nibras Mobile comes with eleven home-screen widgets. To add one, long-press an empty spot on your home screen, choose **Widgets**, find **Nibras Mobile**, and drag the widget you want into place. Most can be resized.

| Widget (name in the picker) | Shows | Needs a Nibras server? |
|---|---|---|
| [Recent notes](#recent-notes) | Your recent notes, to read | No |
| [Today's tasks](#todays-tasks) | Today's top-priority tasks, with an add bar | No |
| [Today's journal](#todays-journal) | Today's journal entries, with an add bar | No |
| [Top priority](#top-priority) | Every note at your top priority | No |
| [Tasks board](#tasks-board) | Every task at your two top priorities, by department | No |
| [Nibras Mobile KPIs](#nibras-mobile-kpis) | Counts at a glance | Partly |
| [Upcoming plans](#upcoming-plans) | Your next planned events | Yes |
| [Bookmarked records](#bookmarked-records) | Records you starred | Yes |
| [Prayer times](#prayer-times) | Islamic date, countdown, today's times | No |
| [Alarms countdown](#alarms-countdown) | Time until today's alarms | No |
| [Phone status](#phone-status) | Volume levels | No |

!!! tip "Tap the title to refresh"
    A widget only updates when something redraws it. On most widgets, **tapping the title** redraws it now. On the widgets that show server data (**KPIs**, **Upcoming plans**, **Bookmarked records**), it also pulls fresh data from the server first. Tapping elsewhere on a widget usually opens the app.

## Recent notes

A read-only feed of what you've written lately, oldest at the top and newest at the bottom, like a chat you scroll through. The title reads **Today's notes** or **Last N days**.

Long notes are cut off after a set number of words with **Read more**. **Tap a note** to unfold it in place, and tap again to fold it. That's the only control; there are no filters or buttons.

Set it up in [Settings](settings.md) → **Calendar & widgets**, under **Recent notes widget**:

| Setting | Range | Default |
|---|---|---|
| How far back (**today only** / **N days back**) | 1–30 days | 3 days |
| **N words, then Read more** | 10–200 words | 40 |
| **Text size N sp** | 10–28 sp | 15 sp |

The widget redraws when you tap **Save** in Settings, so a new size shows straight away.

## Today's tasks

The tasks written **today** at your **Top priority** (titled, for example, **Today's p4 tasks**). It empties itself overnight, so it's a list of today's work, not an archive.

Tap **+  Add a task…** at the top to type a new one straight from the home screen. See [Quick capture](capture.md#add-a-task-or-journal-entry-from-the-home-screen).

## Today's journal

Today's journal entries, at every priority. Like the tasks widget it starts fresh each day. Tap **+  Write today's journal…** to add an entry.

## Top priority

Every note in your folder at your **Top priority** (priority 4 unless you change **Top priority (widgets)** in Settings → **General**), of any type and any date. If there are none it says **No priority-4 notes**. Tapping the title redraws it. These are notes on your phone, so no sync is needed.

## Tasks board

A widget meant to fill a home-screen page: every task (a note of type `T`) at your top two priorities, whenever it was written. With the default settings that is p4 and p3, and the title reads **Tasks p3–p4** with the count beside it. Tasks are grouped by priority, then by department (category); tasks with none go under **No department**.

Unlike **Today's tasks**, it has no add bar and doesn't empty overnight: it's the standing picture of what matters. Each task is set in its own text direction, so Arabic tasks read right to left. Tap the title to redraw it, or a task to open the app on **Tasks**.

Set it up in [Settings](settings.md) → **Calendar & widgets**, under **Tasks board widget**:

| Setting | Range | Default |
|---|---|---|
| **Text size N sp** | 10–24 sp | 13 sp |
| **N columns** | 1–4 | 2 |

The two priorities follow **Top priority (widgets)** in [Settings](settings.md) → **General**.

## Nibras Mobile KPIs

A small widget of counts, with the label shortened next to each number so it fits a narrow cell:

| Reads | Meaning |
|---|---|
| `5 nt. td. / 1204` | Notes written today / notes in total |
| `862 f.` | Files in your notes folder, including attachments |
| `12 f. td.` | How many of those files are from today |
| `7.7 gb f.` | Free storage on the phone, in GB |
| `9 p4` | Notes at your top priority |
| `3 pl.` | Upcoming plans (from the server) |

Some numbers change colour as a warning: too many top-priority notes, or too few plans ahead.

## Upcoming plans

Your next events from the Nibras planner, labelled by day and time (**Tom 09:00** means tomorrow at 09:00). Scroll to see more. Tap the title to pull fresh plans from the server. Tap a row to open the app.

## Bookmarked records

The Plans, Goals and Tasks you've starred in the app's [server pages](server-pages.md#bookmark-the-star) (records bookmarked on this phone), in a scrollable list. Tap the title to sync and redraw.

## Prayer times

A wide strip with the Islamic date, a countdown to the next time, and one column per prayer you've chosen to show, with the next one highlighted. It's in Arabic and right-to-left, written large enough to read across a room.

The countdown appears only once the next prayer is close. How close is the **Widget countdown** setting on the [Prayer times](prayer.md#reminders-and-countdown) page (1 hour by default). Once a time arrives, the strip counts up from it instead (**منذ 4:12**) for as long as the **Elapsed timer** on the Prayer times page says (30 minutes by default). Tap the date to redraw; tap elsewhere to open the Prayer times page.

## Alarms countdown

A card titled **Alarms · N today** listing each of today's remaining [alarms](alarms.md): its time, how long until it rings, and its label (**07:00 · in 2h 15m — Wake up**). When nothing is left it says **Nothing left today**. It updates itself every minute while it's on your home screen. Tap it to open the Alarms page.

## Phone status

Titled **Volumes**: your **Ring**, **Notification**, **Alarm** and **Music** volume levels as bars. They update when the volume or ringer mode changes. Tap the title to refresh. Tap elsewhere to open the phone's volume settings.
