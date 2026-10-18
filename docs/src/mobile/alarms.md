# Alarms, reminders and the chime

knotes can sound at a moment in four ways:

- **Alarms** that you set yourself: a clock time, a countdown, or an offset from a prayer.
- **Plan reminders** before events on your Nibras planner.
- A **15-minute chime** that marks the passing of time.
- The **athan**, covered in [Prayer times and athan](prayer.md).

All of them keep working with the app closed and are re-armed after the phone restarts.

## Alarms

Open **Alarms** (the alarm-clock icon) from the rail. The header shows **Alarms** and how many are switched on (**N on**).

### Add an alarm

Tap the **+** button (**Add an alarm**). The **New alarm** form opens:

1. **Label**: optional, shown in the list and the notification.
2. **Kind**: choose **Time**, **Countdown** or **Prayer**. The fields below change to match. Switching kind keeps what you typed under the others.
3. Set the timing (see below).
4. **Sound**: **Choose…** any audio file on the phone, or **Default** for the **Default alarm sound**.
5. **Repeat: Nx**: how many times the sound plays (1–20).
6. **Volume: N%**: how loud, for this alarm only.
7. Save.

#### Time

An **Hour** and **Minute**, and the **Days** it repeats on. **Days — none means once**: with no days picked, it rings once at the next occurrence of that time, as a phone alarm does.

#### Countdown

Pick a **Duration** (5, 10, 15, 20 or 30 minutes, 1h, 1h 30m or 2h), or type any number in **Or minutes**.

A countdown doesn't start by being saved. Start it with the play button on its row. It shows **running** while counting down and **not started** otherwise. If the phone was off when it should have rung, it doesn't go off late when the phone comes back.

#### Prayer

Pick one of the prayers and an **Offset — negative is before**, for example −30 for half an hour before Fajr, or **at** for the time itself. You can limit it to certain **Days** too. The alarm moves with the prayer every day and uses the settings from the [Prayer times](prayer.md) page, so it always agrees with what that page shows.

### The list

Each row says what the alarm is and **when it will actually ring next**, for example **rings tomorrow 05:10**. If an alarm is switched on but has nowhere to fire (a countdown that hasn't been started, for instance), it says **not scheduled**, so it can't look armed when it isn't.

Each row has:

- a switch to turn it on or off,
- a play/stop button (countdowns only),
- a delete button, which asks **Delete this alarm?** first,
- **Edit**.

When an alarm rings, a notification shows its label, and the sound plays the number of times you set at that alarm's volume, on the alarm channel.

!!! warning "Alarms firing late?"
    If the page shows **Exact alarms are not permitted for this app, so these may fire late.**, open Android's settings for knotes and allow **Alarms & reminders**.

The [Alarms countdown widget](widgets.md#alarms-countdown) shows how long until today's remaining alarms.

## Plan reminders

If you're connected to a [Nibras server](sync.md), knotes reminds you before upcoming **Plans** and **Journal** events that have a start time. Configure them in [Settings](settings.md) → **Plan reminders**:

| Setting | Default | Meaning |
|---|---|---|
| **Lead times (minutes)** | `60, 5` | Minutes before the start. Several are allowed, and each gives its own alert. `60, 5` means one hour before and five minutes before. |
| **Reminder sound** | Default | **Choose sound** to pick one. |
| **Require dismissal** | Off | On: a full-screen alarm whose sound loops until you tap **Dismiss**. Off: the sound plays the repeat count below, then stops. |
| **Repeat sound (times)** | 4 | How many times the sound plays when **Require dismissal** is off. |

The reminder notification shows the plan and when it starts (**in 5 min · 14:30**). It stays until you act on it:

- **Dismiss** clears this reminder. Later reminders for the same event still come.
- **Stop reminders** cancels the remaining reminders for this occurrence, for when you're already there. Next week's reminders for a repeating plan are not affected.

If tasks on the server are past their due date, knotes also posts a single **N tasks overdue** notification.

## The 15-minute chime

A soft chime at **:00, :15, :30 and :45**, so you notice time passing without looking at anything. Set it up in [Settings](settings.md) → **Status & chime**:

- **15-minute chime**: on or off (on by default; also offered in the setup tour as **Quarter-hour chime**).
- **Chime start hour** and **Chime end hour**: it only sounds inside this window (8 to 22 by default).
- **Choose chime file**: any audio file. If none is chosen, a short chime built into the app is used.

The chime plays at your phone's **notification** volume, so it's as loud as your other notifications and respects that slider.

## The status notification

**Persistent notification** (in **Status & chime**, on by default) keeps a quiet, ongoing notification showing today's note count (**Today: N · Total: M**) and, when syncing with Nibras, whether you're online and when you last synced.

It also keeps the app running in the background, which helps reminders and the chime fire on time. If you switch it off and alarms start arriving late, switch it back on.
