# Prayer times and athan

**Prayer times** (the mosque icon, first in **On this phone**) shows today's prayer times and the Islamic date, and can call the athan, remind you beforehand, and silence your phone while you pray. Everything is calculated on the phone, so it works offline and nothing about your location leaves the device.

## The page

- **The Islamic (Hijri) date**, then today's date and your place name.
- **What's next**: the next prayer, its time, and a live countdown (**in 1h 12m**, then **in 4m 30s** as it gets close).
- **The times**: Fajr, Shurooq (sunrise), Dhuhr, Asr, Maghrib and Isha, each with its Arabic name. The next one is highlighted, and a speaker icon (**Athan on**) marks the times that will call the athan. If you've nudged a time, the adjustment is shown beside it.

Under the times is a row of five chips. Each opens a settings panel underneath; tap it again to close. **Every change takes effect immediately; there is no Save button.**

| Chip | Panel |
|---|---|
| **Times** | Which times to show, and per-time adjustments |
| **Method** | When Asr begins, the calculation method, your location, far-north rules |
| **Athan** | Master switch, athan sound and volumes, reminders, widget countdown, beeps before Shurooq |
| **Silence** | Silencing the phone at prayer times |
| **Hijri** | Adjusting the Islamic date |

## First: set your location

The times depend on where you are. Open **Method → Where you are**:

- Tap **Use my location** to fill in **Latitude** and **Longitude** from the phone's last known position. Android asks for location permission the first time. Only the coordinates are read, and only when you press the button. Nothing is looked up online.
- Or type the coordinates yourself.
- **Place name (shown at the top)** is just a label for the page header.

## Times

- **Which times to show**: untick any you don't want. Hidden times are also left out of reminders and the widget.
- **Adjust each time**: add or take off minutes (−60 to +60) with the − and + buttons, and **reset** to go back. This is how you match the mosque you actually pray at. No calculation method agrees with every mosque.

## Method

- **School — when Asr begins**: **Standard (Shafiʿi / Jaʿfari)** or **Hanafi**. The two differ by about an hour in summer.
- **Calculation method**: how far below the horizon the sun must be for Fajr and Isha. Each option shows its angles:
    - **Muslim World League** (default)
    - **Islamic Society of North America**
    - **Egyptian General Authority of Survey**
    - **Umm al-Qura, Makkah**
    - **University of Islamic Sciences, Karachi**
    - **Institute of Geophysics, Tehran**
    - **Shia Ithna-ʿAshari (Leva Institute, Qum)**

    Shia Ithna-ʿAshari and Tehran also hold Maghrib back until the sun is 4° below the horizon, rather than at sunset.
- **Far-north nights**: above about 48° latitude there are summer nights when Fajr or Isha never occur. Leave this at **None (leave undefined)** unless you see a blank time. Otherwise choose **Middle of the night**, **One-seventh of the night** or **Angle-based**.

## Athan

### Switches

- **Alarms on**: the master switch. Off, the page still shows the times, but nothing sounds and no alarm is set. The page then warns: **Alarms are switched off — the times are still shown, but nothing will sound.**
- **Prayer times in the app**: off removes the whole page from the rail and schedules nothing. To bring it back, use the **Prayer times are hidden** switch in [Settings](settings.md).

### The athan

- **Call the athan at**: choose the prayers that get the athan (Dhuhr and Maghrib by default). Shurooq is sunrise, not a prayer, so it's never on this list.
- **The sound**: with no file chosen, the athan plays the **phone's default alarm sound**. To hear an actual athan, tap **Choose athan file** and pick any audio file of the recording you want. **Clear** goes back to the default alarm sound. **Hear it** plays it now; **Stop** stops it.
- The athan plays on the **alarm** sound channel, so it is still heard while the phone is silenced for the prayer.
- **Volume for each**: a separate volume per prayer (80% by default). It can make an athan quieter than the phone's alarm volume, never louder; 0 mutes that one. **Hear:** buttons let you test each.

When the athan plays, a notification appears with a **Stop** button.

### Reminders and countdown

- **Remind me before each time**: a quiet notification shortly before every time you've chosen to show (**10 min before** by default, adjustable from 1 to 60 minutes). On by default.
- **Widget countdown**: the [prayer widget](widgets.md#prayer-times) shows its countdown only once the next time is this close (1 hour by default, from 15 minutes up to **always showing**).

### Beeps before Shurooq

Sunrise ends the time for Fajr, so it can have a countdown of its own. Switch on **Beeps before Shurooq** and choose how long before sunrise (10 min by default) and how many beeps (3 by default). The beeps sound on the alarm channel, loud enough to wake someone.

**Beep sound**: the **Built-in tone** (needs no file), one of the phone's **Ringtones**, or **A file**. **Hear N** plays them now.

## Silence

**Silence the phone → Go silent at these times** switches the ringer to silent at the prayer time and puts it back exactly as it was afterwards.

- **For N min**: how long to stay silent (15 by default, 5–60).
- **At these times**: which prayers (Dhuhr and Maghrib by default). This is chosen separately from the athan: wanting quiet while you pray and wanting to hear the call are different choices.

!!! note "Do Not Disturb access"
    Android only lets an app change the ringer if you grant it Do Not Disturb access. If it's missing, the panel says so. Tap **Grant access** and switch knotes on in the list.

## Hijri

The Islamic date is calculated arithmetically, so it needs no network. The real month begins with the sighting of the new moon, which can fall a day either side.

- **Shift by**: move the date a day earlier or later to match what was announced where you live.
- **Day starts at Maghrib**: the Islamic day begins at sunset, so after Maghrib the date shown is already tomorrow's. On by default.

## Related

- [Prayer-relative alarms](alarms.md#prayer): an alarm a set number of minutes before or after any prayer.
- The [Prayer times widget](widgets.md#prayer-times) on your home screen.

!!! tip "Default location"
    Until you set your own, the page uses a default location. Set yours under **Method → Where you are** before relying on the times.
