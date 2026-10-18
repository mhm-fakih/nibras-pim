# FAQ & troubleshooting

## Signing in

??? question "It says *Invalid username or password*."
    Check spelling and capitals. If you've forgotten your password, ask an administrator to set a
    new one on the [Users](administration.md#users) page.

??? question "It says *Can't reach the server — the backend may be down*."
    The web app can't talk to the server at all — your password wasn't even checked. Try again in a
    minute; if it persists, tell your administrator.

??? question "I was signed out by myself."
    Sessions expire after a number of hours (12 by default). Sign in again; you'll return to the page
    you were on.

??? question "A *Change your password* box won't go away."
    Your administrator requires a new password before you continue. Fill in all three fields and
    click **Change password**.

## Missing things

??? question "I can't see the **New** button, or **Edit** / **Delete**."
    Your role can read but not write. Ask an administrator for the **Write content** capability —
    see [Roles and capabilities](administration.md#roles-and-capabilities).

??? question "A page says *You don't have permission to view this page*."
    That page needs a capability your role doesn't have.

??? question "A module or tool page is missing from the sidebar."
    It may be switched off in [Settings → Modules](settings.md#modules) or
    [Settings → Pages](settings.md#pages-and-features), or hidden by your permissions. If the
    sidebar is short of space, tool pages are shown as icons — hover to see their names.

??? question "I deleted a record by mistake."
    Open **Trash** and click the restore icon next to it. Merged records are there too.

## Search

??? question "Global search finds nothing at all."
    If the Search page warns that **the search index is empty**, an administrator needs to click
    **Rebuild search index** once on the [Admin](administration.md#admin-page) page.

??? question "Searching for a word starting with *d* or *c* behaves oddly."
    In global search, `dX` and `cX` are read as department and course filters when a department or
    course with that code exists. Add another word, or search inside the module's list instead.

??? question "How do I see everything I did on a particular day?"
    Use the **On day** filter in a module's list, or switch the Journal module off and click the day
    in the mini calendar to see that day across all modules. With Journal on, clicking a day opens
    the day's journal instead.

## Calendar and journals

??? question "Clicking a date in the mini calendar created a journal."
    That is by design: each day has one summary journal, created the first time you click the day.
    To just filter by date, use **On day** above the list.

??? question "I can't add or resize calendar entries."
    Read-only users can view the calendar but not change it.

## Files and folders

??? question "The **Repo1/Repo2/Repo3** buttons do nothing."
    They open a file manager *on the computer running the server*. If the server is elsewhere,
    nothing visible happens on your screen. Use **Upload** and the file links in the record's
    **Files** section instead.

??? question "The import pages ask for a folder path. Which folder?"
    A folder on the server's disk, not on your own computer. Your administrator will know where
    captured notes and drop folders end up.

## Notifications

??? question "I turned on notifications but never get any."
    Check that the browser allows notifications for this site, and that a tab with the web app is
    open — the app can only notify you while it's running. The setting is per browser, so turn it on
    in each browser you use.

## Sharing and privacy

??? question "Can I show a dashboard on a TV without signing in there?"
    Yes — that's what a dashboard's **View** link is for. But it means anyone who has the link and
    can reach the server can see it. The same is true of [RSS feeds](rss.md). See
    [Dashboards](dashboards.md#showing-it-on-a-screen).

## Display

??? question "Arabic text is aligned the wrong way."
    Direction is decided from the text itself, by its first letters. If a line starts with Latin
    letters or digits it is treated as left-to-right. See [Languages](../concepts/languages.md).

??? question "My layout (column widths, page size…) reset."
    Those preferences live in your browser. A different browser, a private window or clearing site
    data starts afresh.

??? question "Is there a newer version?"
    Click **check for updates** in the status bar, or **Check for updates** on the **Help & shortcuts**
    page.
