# Accounts, permissions & administration

This page is for the people who look after a Nibras installation's **users** and **vocabulary**:
administrators and, for the Parameters page, parameter editors. If a page described here isn't in
your sidebar, you don't hold the capability it needs.

## Roles and capabilities

Every user has one **role**. What a role may do is a set of **capabilities**:

| Capability | Lets you |
|---|---|
| **Write content** | Create, edit, delete and import records; use Batch entry, Import files, Import notes, Image renamer, Static website, Maintenance and Trash. |
| **Write parameters** | Edit statuses, types and contexts on the **Parameters** page. |
| **Manage users** | Use the **Users** and **Roles & permissions** pages. |
| **Admin tools** | Use the **Admin** page. |
| **Settings** | Use the **Settings** page. |
| **Audit log** | See the **Audit log** page and each record's **History**. |

Anyone signed in can read, search and browse every module and the reading pages (Today, Calendar,
Kanban, Dashboards, Insights, Sprints, Reports, Bookmarks, Highlights, Flash cards, Local notes).

The four roles start out like this:

| Role | Default capabilities |
|---|---|
| **Admin** | Everything — always, and can't be reduced |
| **Data entry** | Write content |
| **Parameters data entry** | Write parameters |
| **Read only** | None — reading only |

### Changing what a role can do

Open **Roles & permissions** (sidebar). The grid shows roles down the side and capabilities across
the top. Tick or untick a box — the change is saved and takes effect immediately. The **Admin** row
is locked.

## Users

Open **Users** to see everyone's **Username**, **Name**, **Role** and **Status** (**Active** /
**Disabled**).

- **Add user** — enter a **Username**, optional **Display name**, a **Role**, a **Password**, and
  leave **Active** on. Click **Save**.
- **Edit** (pencil) — change any of these. Leave **New password (leave blank to keep)** empty to keep
  the current password.
- **Disable** someone by switching **Active** off — they can no longer sign in, but their history
  stays intact.
- **Delete** (bin) removes the account after confirmation.

!!! tip
    Give each person their own account. The audit log records *who* changed what, which is only
    useful if accounts aren't shared.

## Parameters

**Parameters** is where the shared vocabulary lives: the **statuses**, **types** and **contexts**
that records are classified by, per module. (Departments and courses are full modules of their own;
see [Modules & records](records.md).)

For each list:

- Type a name in **New value…** and a short **code**, then click **Add value**. Codes are what you
  type in [commands](batch-commands.md) — `?done`, `#art`, `@home`.
- **Double-click** a value (or use **Rename**) to rename it.
- Click the colour swatch (**Recolour**) to choose its colour. Colours are used for chips and on the
  [Calendar](calendar.md).
- Click the ★ (**Set as default for blank records**) to make a value the default: new records left
  blank get it.
- **Remove this value** deletes it after confirmation; existing records keep showing it until they
  are changed.
- **Fill empty colours** gives every uncoloured value a colour in one go.

## Admin page

**Admin** (heading **Administration**) gives an overview of the data:

- A card per module with its **record count** and, for each classified field, how many of the
  available values are actually **used**.
- **Rebuild search index** — rebuilds the index behind [global search](search.md#global-search). Run
  it once on a new installation, or if search results look incomplete. Progress and a result line
  are shown.
- **Synced to clients** — exactly what the phone, terminal and desktop apps should be offering:
  departments of priority 2 and above, and the types of each enabled module in their sync order.
  Compare it with a device to spot a stale copy. See [Sync](../concepts/sync.md).

## Audit log

**Audit log** lists every change to every record: inserts, each field that was updated (old → new)
and deletions, with when and by whom. Narrow it by **Module**, **Operation** (**Insert**, **Update**,
**Delete**), **Field**, and **From** / **To** dates. Use **Previous** / **Next** to page through.

The same history for a single record appears in its detail pane under **History**.

## Trash

**Trash** lists records that were deleted — or absorbed by a merge (**Merged → #id**) — across all
modules. Click the restore icon to put one back where it was. See
[Deleting and restoring](records.md#deleting-and-restoring).

## Maintenance

**Maintenance** shows where storage is going and offers two ways to reclaim it. Both work the same
way: a ranked table, a checkbox per row, **Select all** / **Clear**, and a button that states what it
will reclaim before you click it.

=== "Repository 1 folders"

    **Heaviest repository 1 folders** lists the records with the most files in the working
    repository. Tick some and click **Move n to repo 2 — reclaims …** to move their files into the
    archive repository. Covers and checkout files stay put. **Rescan** refreshes the list.

=== "Resource full text"

    **Longest resource full text** lists resources whose stored text is largest. Tick some and click
    **Move n to file — … out of the database**. Each text is written to a file in the resource's
    repository-1 folder, checked, and only then cleared from the record. The text no longer turns up
    in record search, but it is still on disk. An existing file of the same name is kept aside, never
    overwritten.
