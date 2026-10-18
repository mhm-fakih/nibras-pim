# Server pages

The **From the server** band of the [rail](navigation.md) holds the pages that show records from your Nibras server. Connect first, as described in [Nibras sync](sync.md).

Every page here shows the copy saved on your phone first and refreshes from the server when it can, so they all work offline. To reload all of them from the server at once, tap **Refresh server pages** (the circular arrow at the bottom of the band).

## Record pages: T, P, J, G, N, W, R

One page per record type: **Tasks**, **Plans**, **Journal**, **Goals**, **Notes**, **Writings** and **Resources**. The number next to each letter in the rail is how many records of that type are on the phone.

### The list

- The top shows the connection state (**Online**, **Offline** or **Connecting**), **Syncing…** while it works, and a **Refresh** button.
- **Search…** narrows the list as you type.
- Records you've starred come first, under **★ Bookmarked**. The rest follow.
- On some pages the rest are split into collapsible groups: Resources by resource type, Tasks by context, Plans and Journal by sub-type. **Expand all** / **Collapse all** opens or closes them together.
- On Resources, Notes and Writings, a thin bar under each row shows roughly how long it is to read. A full line is about ten pages. It turns orange past 15 pages and red past 20.
- Small icons show when a record **Has note** or **Has files**.
- The up and down arrows (**Move to top** / **Move to bottom**) jump through a long list or a long article.

If you're offline and nothing has been saved for a type yet, the page says **Offline — no cached records for this type yet**.

### Open a record

Tap a row to expand it. Only one record is open at a time. An open record shows:

- its full description,
- **Files (N)**: its attachments. Tap one to open it from your **Files folder** (set in Settings). Audio and video play in the app's [player](player.md),
- **Notes (N)**: notes added to it,
- **Add a note to this record…** with a **Send note** button. The note is added to the record on the server (**Note saved**, or saved for later when offline),
- **Mark unread**, which puts the record back to unread. Records you open are marked as read on this phone only.

### Mark a record complete

Tick the checkbox at the start of a row. You'll see **Marked complete**, or **Saved — will mark complete when online**.

### Highlight a resource

On a **Resources** record that has its full text, the text is shown under **Full text — tap a clause to highlight**. Tap a clause to highlight it (it turns bold), and the highlight is sent to the server. Tap it again to remove it. The text size is the **Font size** setting in [Settings](settings.md).

### Bookmark: the star

The star on each row works in one of two ways, chosen in [Settings](settings.md) → **Star button**:

| Mode | What the star does |
|---|---|
| **Bookmark on this device** (default) | Adds the record to a private shortlist on the phone. Nothing is sent to the server. Starred records go to the top of the list and appear on the [Bookmarked records widget](widgets.md#bookmarked-records). |
| **Un-bookmark in Nibras** | Every record on the phone is bookmarked on the server, which is why it's here, so the star is always filled. Tapping it takes the record off your server bookmarks, straight away when online and on the next sync when not. The row stays, marked **Un-bookmarked — leaves this list on the next sync. Tap the star to undo.**, until then. |

## Search server

**Search server** (the globe with a magnifier) searches the **server itself**, including records that have never been synced to the phone.

1. Type in **Search the Nibras server** and tap **Search**.
2. Narrow the results by type with the chips (**All**, or a type code).
3. Tap the flag on a result (**Flag to sync to this phone**) to bookmark it on the server. It then arrives on the phone with the next sync (**Flagged — will sync to this phone**).

Search needs a connection. If it fails, the message says why: wrong sign-in (401), an account without permission (403), a server too old to have search (404), or a server that can't be reached.

## Flash cards

Passages taken from your longer records, shown **one at a time**, full screen. Swipe through them like pictures. A passage too long for the screen is shrunk to fit, so you never need to scroll.

Three tabs, each with a count:

| Tab | Holds |
|---|---|
| **Review** | Passages waiting for a decision. Forty are shown at a time, and the page refills as you go. |
| **Repeat** | Passages you want to see again. |
| **Kept** | Passages you've kept. |

Under each passage, choose **Keep**, **Repeat** or **Reviewed**. **Unset** undoes a decision. The counter shows where you are and how many are left. Use − and + for **Smaller text** / **Larger text**, and the copy button (**Copy this passage**) to copy it.

Decisions work offline too. The card moves at once, and the counter shows how old your copy is and how many decisions are waiting (**saved copy, 2h ago · 3 decisions to sync**).

## Highlights

The key points recorded on your records (the server's highlights field on notes, tasks, journal, plans, goals, writings and resources), **one record at a time**. Swipe to move between them. Each card is named after the record it came from. This page is read-only: it just shows what the records currently say. **Copy these highlights** copies the card; − and + change the text size.

## Contacts

The people you've bookmarked on the server, each followed by the tasks still open with them, with due dates (**due …**). The header shows **N bookmarked · M open**. A contact with nothing open still appears, so you can see that nothing is outstanding. If no one is bookmarked, the page says **No contact is bookmarked. Bookmark the people you are working with.**
