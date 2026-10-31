# Alarms, reminders and the chime

Nibras Mobile can sound at a moment in five ways:

- The **quick timer** at the top of the Alarms page: one dial, one button.
- **Alarms** that you set yourself: a clock time, a countdown, or an offset from a prayer.
- **Plan reminders** before events on your Nibras planner.
- A **chime** that marks the passing of time, every 15 minutes or less often.
- The **athan**, covered in [Prayer times and athan](prayer.md).

All of them keep working with the app closed, and are re-armed after the phone restarts and after the app is updated.

## Quick timer

Open **Alarms** (the alarm-clock icon) from the rail. The top of the page is the **Quick timer**: a 60-minute dial with a round button beside it, for the "ten minutes, starting now" kind of timer.

1. Drag around the dial, clockwise from 12 like a kitchen timer, to set **1 to 60 minutes**. The middle shows the minutes. The dial opens where you left it last time (10 minutes the first time).
2. Tap the play button (**Start the quick timer**), or tap the middle of the dial.
3. While it runs, the coloured ring shrinks back towards 12, the middle counts down, and **Ends at** under it gives the clock time it will ring.
4. To cancel, tap the same button, now a red stop button (**Stop the quick timer**), or the middle of the dial again.

The quick timer rings like any other alarm, with a notification and a **Stop** button. Its sound, volume and how many times it plays are set once, in [Settings](settings.md#alerts) → **Alerts** → **Quick timer**:

| Setting | Default | Meaning |
|---|---|---|
| **Choose sound file** | **Default alarm sound** | Any audio file on the phone. **Default alarm sound** goes back to the phone's own. |
| **Volume N%** | 80% | How loud, as a share of the phone's full alarm volume. |
| **Plays N×** | 2× | How many times the sound plays (1–10). |

## Alarms

Under the quick timer, the header shows **Alarms** and how many are switched on (**N on**).

### Add an alarm

Tap the **+** button (**Add an alarm**). The **New alarm** form opens:

1. **Label**: optional, shown in the list and the notification.
2. **Sound**: **Choose…** any audio file on the phone, or **Default** for the **Default alarm sound**.
3. **Kind**: choose **Time**, **Countdown** or **Prayer**. The fields below change to match. Switching kind keeps what you typed under the others.
4. Set the timing (see below).
5. **Repeat: Nx**: how many times the sound plays (1–20).
6. **Volume: N%**: how loud, for this alarm only. At the far left the slider reads **Volume: lowest −5 dB**: the phone's lowest alarm step, made a little quieter still, for an alarm that should only just be heard.
7. **Save**.

#### Time

An **Hour** and **Minute**, and the **Days** it repeats on. **Days — none means once**: with no days picked, it rings once at the next occurrence of that time, as a phone alarm does.

#### Countdown

Pick a **Duration** (5, 10, 15, 20 or 30 minutes, 1h, 1h 30m or 2h), or type any number in **Or minutes**.

A countdown doesn't start by being saved. Start it with the play button on its card. It shows **running** while counting down and **not started** otherwise. If the phone was off when it should have rung, it doesn't go off late when the phone comes back.

#### Prayer

Pick one of the prayers and an **Offset — negative is before**, for example −30 for half an hour before Fajr, or **at** for the time itself. You can limit it to certain **Days** too. The alarm moves with the prayer every day and uses the settings from the [Prayer times](prayer.md) page, so it always agrees with what that page shows.

### The list

Each alarm is a card that reads top to bottom, one fact per line:

- the time (or the countdown's length, or the prayer and offset) on the left, and your label on the right,
- **when it will actually ring next**, for example **rings tomorrow 05:10**, or **off**. If an alarm is switched on but has nowhere to fire (a countdown that hasn't been started, for instance), it says **not scheduled**, so it can't look armed when it isn't,
- its days (**once**, or **On** and the days), or for a countdown **running** / **not started**,
- the sound file it plays,
- its repeat count and volume, for example **Repeat: 2x · Volume: 60%**.

The bottom row holds the actions:

- **Edit**,
- **Duplicate** (the copy icon): opens a copy of the alarm in the form, so you can change its time or sound before saving it. **Cancel** leaves no copy behind. A copy of a running countdown starts stopped,
- a play/stop button (countdowns only),
- a delete button, which asks **Delete this alarm?** first,
- a switch to turn it on or off.

When an alarm rings, a notification shows its label with a **Stop** button, and the sound plays the number of times you set. While it plays, the phone's alarm volume is set to that alarm's level, then put back as it was, so 50% is equally loud whatever the alarm slider was left at. Alarms due at the same moment play one after the other. The sound plays from a foreground service, so Android can't cut it short once the alarm has fired.

!!! warning "Alarms firing late?"
    If the page shows **Exact alarms are not permitted for this app, so these may fire late.**, open Android's settings for Nibras Mobile and allow **Alarms & reminders**.

The [Alarms countdown widget](widgets.md#alarms-countdown) shows how long until today's remaining alarms.

## Plan reminders

If you're connected to a [Nibras server](sync.md), Nibras Mobile reminds you before upcoming **Plans** and **Journal** events that have a start time. Configure them in [Settings](settings.md#alerts) → **Alerts** → **Plan reminders**:

| Setting | Default | Meaning |
|---|---|---|
| **Lead times (minutes)** | `60, 5` | Minutes before the start. Several are allowed, and each gives its own alert. `60, 5` means one hour before and five minutes before. |
| **Reminder sound** | Default | **Choose sound** to pick one. |
| **Require dismissal** | Off | On: a full-screen alarm whose sound loops until you tap **Dismiss**. Off: the sound plays the repeat count below, then stops. |
| **Remind even on silent or vibrate** | Off | Plays the reminder sound at alarm volume, so silent and vibrate modes don't mute it. |
| **Repeat sound (times)** | 4 | How many times the sound plays when **Require dismissal** is off. |

The reminder notification shows the plan and when it starts (**in 5 min · 14:30**). It stays until you act on it:

- **Dismiss** clears this reminder. Later reminders for the same event still come.
- **Stop reminders** cancels the remaining reminders for this occurrence, for when you're already there. Next week's reminders for a repeating plan are not affected.

If tasks on the server are past their due date, Nibras Mobile also posts a single **N tasks overdue** notification.

## The chime

A soft chime that marks time passing, so you notice it without looking at anything. Set it up in [Settings](settings.md#alerts) → **Alerts** → **Status & chime**:

- **Chime**: on or off (on by default; also offered in the setup tour as **Quarter-hour chime**).
- **Chime every**: **15 min**, **30 min**, **1 h** or **2 h**. The interval is counted from the start hour: with a start hour of 8 and **2 h**, it sounds at 08:00, 10:00, 12:00 and so on.
- **Choose chime file**: any audio file. If none is chosen, the phone's default notification sound is used.
- **Chime start hour** and **Chime end hour**: it only sounds inside this window (8 to 22 by default, both included).

The chime plays at your phone's **notification** volume, so it's as loud as your other notifications and respects that slider.

## The status notification

**Persistent notification** (Settings → **Alerts** → **Status & chime**, on by default) keeps a quiet, ongoing notification showing today's note count (**Today: N · Total: M**) and, when syncing with Nibras, whether you're online and when you last synced.

It also keeps the app running in the background, which helps reminders and the chime fire on time. If you switch it off and alarms start arriving late, switch it back on.
