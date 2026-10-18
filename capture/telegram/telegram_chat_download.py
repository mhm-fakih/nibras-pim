#!/usr/bin/env python3
"""
Telegram chat exporter — interactive, or unattended for cron.

Interactive flow (run it in a real terminal so the prompts work):
  1. Lists your groups, channels and Saved Messages, most-recently-active
     first (other 1:1 chats are not listed). Each row shows
     a selection number (changes between runs) and a fixed chat id (never
     changes — that's the one to put in a cron job).
  2. You pick one by number.
  3. You choose how many of the latest messages to download (e.g. 100).
  4. They are saved — in original chat order (oldest first) — as a single
     knotes note (type N), with media downloaded to the note's companion
     folder and linked inline.
  5. You are asked whether to DELETE those messages from Telegram or keep them.
     Deletion requires typing 'delete' and removes the messages for everyone
     (only works where you have permission); otherwise nothing is touched.

Output (in the knotes folder: --knotes, else $NIBRAS_KNOTES_DIR, else
~/new/knotes — see ../knotes_out.py):
  <yyyy_mm_dd-hhmmss>~N~p<priority>~<category>.md     the transcript
  <yyyy_mm_dd-hhmmss>~N~p<priority>~<category>.d/     its media, as
      <id>_<timestamp>.<ext>
The timestamp is the export time. Priority and category default to the
folder's .knotes.json (priority_default, default_category), else p2 and D.

Usage:
  ../run.sh telegram                                 # interactive
  ../run.sh telegram --list                          # print chat ids and exit
  ../run.sh telegram --chat -1001234567890 --limit 200
  ../run.sh telegram --chat -1001234567890 --no-media   # text only
  ../run.sh telegram --chat @mychannel --limit 200 --delete
  ../run.sh telegram --chat saved --limit 200        # Saved Messages
  ../run.sh telegram --knotes ~/notes --category Z --priority 3

Credentials come from the environment (run.sh loads ../config.env):
  TG_API_ID, TG_API_HASH   from https://my.telegram.org → API development tools
  TG_PHONE                 the account's phone number, for the first login
The login is kept in session_name.session next to this script (gitignored).

With --chat the script never prompts, so it is safe to run from cron. It keeps
the messages on Telegram unless you also pass --delete (which skips both
confirmations — nothing will ask you first).
"""

import argparse
import asyncio
import glob
import os
import re
import sys
from datetime import datetime, timedelta, timezone

from telethon import TelegramClient, errors, utils

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from knotes_out import add_note_args, check_note_args, knotes_dir, new_note_path  # noqa: E402

# ---- Config -----------------------------------------------------------------
# Never hard-code these: the repo is published. run.sh loads them from ../config.env.
API_ID = os.environ.get("TG_API_ID", "")
API_HASH = os.environ.get("TG_API_HASH", "")
PHONE_NUMBER = os.environ.get("TG_PHONE", "")

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
NOTE_TYPE = "N"

IMAGE_EXTS = {".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".tiff"}
DEFAULT_LIMIT = 100
LARGE_BATCH = 50          # above this many, deletion needs a second (typed-count) confirmation
SAVED_ALIASES = {"me", "self", "saved"}   # --chat spellings for Saved Messages

# Raised when a message's file_reference has gone stale; a re-fetch usually cures it.
STALE_REFERENCE_ERRORS = (
    errors.FileReferenceExpiredError,
    errors.FileReferenceInvalidError,
    errors.FileReferenceEmptyError,
)

# Telethon refuses empty credentials outright; leave the client unset so the
# module still imports, and let __main__ explain what to fill in.
client = (TelegramClient(os.path.join(SCRIPT_DIR, 'session_name'), int(API_ID), API_HASH)
          if API_ID.isdigit() and API_HASH else None)


# ---- Helpers ----------------------------------------------------------------
async def prompt(message: str) -> str:
    """
    Ask the user a question without freezing the Telegram connection.

    A bare input() blocks the thread running the asyncio event loop, which is
    also the loop running Telethon's keepalive (it pings every 60s) and its
    auto-reconnect. Sit at a prompt for a few minutes and Telegram drops the
    idle connection, with nothing able to notice or reconnect — so the next
    request after the prompt fails. Reading stdin in a worker thread instead
    lets the loop keep running while we wait, however long that takes.

    Returns None — distinct from an empty line — if stdin closes (EOFError,
    e.g. an SSH session that dropped during a long wait) or on Ctrl-C, so
    callers can pick a safe default rather than dying with a traceback.
    """
    try:
        return (await asyncio.to_thread(input, message)).strip()
    except (EOFError, KeyboardInterrupt):
        print()          # move off the half-written prompt line
        return None


def chat_id(dialog) -> int:
    """
    The chat's permanent id, in the form Telegram/Telethon accept back as input
    (-100… for channels and supergroups, -… for legacy groups). Unlike the
    selection number in the listing, this never changes — pass it to --chat.
    """
    return utils.get_peer_id(dialog.entity)


_me_id = None


async def my_id() -> int:
    """The account's own user id, fetched once and cached."""
    global _me_id
    if _me_id is None:
        _me_id = (await client.get_me()).id
    return _me_id


def is_saved_messages(dialog) -> bool:
    """
    True for Saved Messages — which is not a group or a channel but the 1:1
    dialog with your own account. Only meaningful after my_id() has run.
    """
    return bool(dialog.is_user) and dialog.entity.id == _me_id


def dialog_title(dialog) -> str:
    """
    The chat's display title. Telethon reports Saved Messages under your own
    display name, so label it the way Telegram clients do instead.
    """
    if is_saved_messages(dialog):
        return "Saved Messages"
    return dialog.name or "chat"


async def load_dialogs():
    """
    All groups & channels on the account plus Saved Messages,
    most-recently-active first.
    """
    print("Loading your groups and channels…\n")
    await my_id()                             # cached before is_saved_messages() below
    dialogs = []
    async for d in client.iter_dialogs():
        # Skip 1:1 user chats, except the one with yourself (Saved Messages).
        if d.is_group or d.is_channel or is_saved_messages(d):
            dialogs.append(d)
    # Telegram already returns recent-first, but sort explicitly to be sure.
    dialogs.sort(key=lambda d: d.date or datetime.min.replace(tzinfo=timezone.utc), reverse=True)

    if not dialogs:
        raise SystemExit("No groups or channels found on this account.")
    return dialogs


def print_dialogs(dialogs):
    """Print the numbered listing, including each chat's fixed id and @username."""
    print(f"  {'#':>3}  {'Chat ID':>15}  {'Username':<20} {'Type':<8} {'Last activity':<17} Title")
    print(f"  {'-'*3}  {'-'*15}  {'-'*20} {'-'*8} {'-'*17} {'-'*30}")
    for i, d in enumerate(dialogs, 1):
        if is_saved_messages(d):
            kind = "saved"
        else:
            kind = "channel" if (d.is_channel and not d.is_group) else "group"
        when = d.date.strftime('%Y-%m-%d %H:%M') if d.date else "—"
        uname = getattr(d.entity, 'username', None)
        uname = f"@{uname}" if uname else "—"
        print(f"  {i:>3}  {chat_id(d):>15}  {uname:<20} {kind:<8} {when:<17} {dialog_title(d)}")
    print("\n  The # column is only good for this run; the Chat ID column is permanent —")
    print("  use it (or the @username) with --chat in cron jobs.")


async def pick_dialog():
    """List groups & channels and let the user choose one interactively."""
    dialogs = await load_dialogs()
    print_dialogs(dialogs)

    while True:
        raw = await prompt("\nSelect a chat by number: ")
        if raw is None:
            raise SystemExit("No selection — nothing was downloaded.")
        if raw.isdigit() and 1 <= int(raw) <= len(dialogs):
            return dialogs[int(raw) - 1]
        print("  Please enter a valid number from the list.")


async def find_dialog(spec: str):
    """
    Resolve a --chat argument to a dialog: a fixed chat id (as shown in the
    listing, with or without the -100 prefix), an @username, or one of
    SAVED_ALIASES for Saved Messages — which has no username of its own, so
    without an alias you would have to look up your own numeric user id.

    Matching walks the dialog list rather than calling get_entity() so a bare
    numeric id always resolves — Telegram only accepts ids for peers the
    session already knows about.
    """
    spec = spec.strip()
    dialogs = await load_dialogs()

    if spec.lstrip('@').lower() in SAVED_ALIASES:
        for d in dialogs:
            if is_saved_messages(d):
                return d
    elif re.fullmatch(r'-?\d+', spec):
        wanted = int(spec)
        for d in dialogs:
            cid = chat_id(d)
            # Accept the unprefixed id too (e.g. 1234567890 for -1001234567890).
            if wanted in (cid, d.entity.id):
                return d
    else:
        wanted = spec.lstrip('@').lower()
        for d in dialogs:
            uname = getattr(d.entity, 'username', None)
            if uname and uname.lower() == wanted:
                return d

    print_dialogs(dialogs)
    raise SystemExit(f"\nNo chat matches --chat {spec!r} (see the list above).")


async def ask_limit() -> int:
    """Ask how many of the latest messages to download."""
    while True:
        raw = await prompt(f"How many of the latest messages to download? [{DEFAULT_LIMIT}]: ")
        if raw is None:                       # stdin closed — take the default
            return DEFAULT_LIMIT
        if not raw:
            return DEFAULT_LIMIT
        if raw.isdigit() and int(raw) > 0:
            return int(raw)
        print("  Please enter a positive number (or press Enter for the default).")


async def sender_name(message, cache: dict) -> str:
    """Resolve a human-readable sender name, caching entities to avoid floods."""
    if not getattr(message, 'from_id', None):
        return ""
    key = str(message.from_id)
    if key in cache:
        return cache[key]
    name = ""
    try:
        ent = await client.get_entity(message.from_id)
        first = getattr(ent, 'first_name', None)
        last = getattr(ent, 'last_name', None)
        title = getattr(ent, 'title', None)          # channels/groups
        if first or last:
            name = " ".join(p for p in (first, last) if p)
        elif title:
            name = title
    except Exception:
        pass
    # WhatsApp-imported messages resolve to a generic "Imported Message"
    # pseudo-user; that's noise, so drop it (header will omit the label).
    if name == "Imported Message":
        name = ""
    cache[key] = name
    return name


async def forward_note(message, cache: dict) -> str:
    """
    A "Forwarded from …" line for reposted messages, or "" for original ones.

    Telegram keeps the origin in the message's forward header: usually a channel
    plus the id of the post there, sometimes a user, and sometimes only a bare
    name when the original sender hides their account. When the origin channel
    has a @username the post links straight to it; private channels get the
    /c/<id>/<post> form, which resolves only for members.
    """
    fwd = getattr(message, 'forward', None)
    if not fwd:
        return ""

    key = f"fwd:{getattr(fwd, 'chat_id', None)}:{getattr(fwd, 'sender_id', None)}"
    if key in cache:
        name, username = cache[key]
    else:
        name, username = "", None
        for get in (fwd.get_chat, fwd.get_sender):
            try:
                ent = await get()
            except Exception:
                ent = None
            if ent is None:
                continue
            name = getattr(ent, 'title', None) or " ".join(
                p for p in (getattr(ent, 'first_name', None),
                            getattr(ent, 'last_name', None)) if p
            )
            username = getattr(ent, 'username', None)
            if name or username:
                break
        cache[key] = (name, username)

    post = getattr(fwd, 'channel_post', None)
    # from_name is all Telegram gives when the original account is hidden.
    label = name or (f"@{username}" if username else "") or getattr(fwd, 'from_name', "") or "unknown"

    if username:
        link = f"https://t.me/{username}" + (f"/{post}" if post else "")
    elif post and getattr(fwd, 'chat_id', None):
        link = f"https://t.me/c/{utils.resolve_id(fwd.chat_id)[0]}/{post}"
    else:
        link = ""

    if link:
        suffix = f" (@{username})" if username and name else ""
        return f"↪️ _Forwarded from_ [{label}]({link}){suffix}"
    return f"↪️ _Forwarded from_ {label}"


def find_existing_media(message, media_dir: str) -> tuple[str | None, bool]:
    """
    Look for media already in media_dir for this message.

    Returns (path, complete). Files are named <id>_<timestamp>.<ext>, so a glob
    on the id+timestamp prefix finds one whatever extension Telethon picked.
    Earlier runs may have left "<id>_<timestamp> (1).jpg" duplicates behind;
    those match too, and the shortest name (the original) wins so no further
    copies pile up.

    complete is False when the file is empty or its size doesn't match what
    Telegram reports — an interrupted download, worth fetching again. The path
    is still returned so the retry overwrites it in place instead of adding
    another " (1)" copy.
    """
    stamp = message.date.strftime('%Y%m%d_%H%M%S')
    pattern = glob.escape(os.path.join(media_dir, f"{message.id}_{stamp}")) + "*"
    candidates = [p for p in glob.glob(pattern) if os.path.isfile(p)]
    if not candidates:
        return None, False

    path = min(candidates, key=lambda p: (len(p), p))
    expected = getattr(getattr(message, 'file', None), 'size', None)
    actual = os.path.getsize(path)
    complete = actual > 0 and (expected is None or actual == expected)
    return path, complete


async def download_media(message, media_dir: str, entity=None) -> tuple[str | None, bool]:
    """
    Ensure a message's media is on disk, in media_dir.

    Returns (path, reused) where reused is True when the file was already there
    and nothing was transferred. path is None if the download failed.

    Every message carries a file_reference — a short-lived token Telegram
    issues when the message is fetched, not a permanent address. We fetch the
    whole batch up front, so on a long media run the references from that first
    call can go stale before the loop reaches the later messages, and the
    download fails even though the file is perfectly fine on Telegram's side.
    The cure is the same one official clients use: re-fetch that one message
    for a fresh reference and try again.
    """
    if not message.media:
        return None, False

    existing, complete = find_existing_media(message, media_dir)
    if complete:
        return existing, True

    # Reuse the exact path of a partial download (Telethon overwrites a path
    # that already exists); otherwise let it append the extension itself.
    stamp = message.date.strftime('%Y%m%d_%H%M%S')
    target = existing or os.path.join(media_dir, f"{message.id}_{stamp}")
    try:
        return await message.download_media(file=target), False
    except STALE_REFERENCE_ERRORS:
        pass
    except Exception as e:
        print(f"  ! media download failed for msg {message.id}: {e}")
        return None, False

    # Stale reference: ask Telegram for the message again and retry once.
    print(f"  ~ refreshing expired file reference for msg {message.id}…")
    try:
        fresh = await client.get_messages(entity or message.peer_id, ids=message.id)
    except Exception as e:
        print(f"  ! could not re-fetch msg {message.id}: {e}")
        return None, False

    if not fresh or not fresh.media:
        print(f"  ! msg {message.id} no longer has media (deleted or expired at the source)")
        return None, False

    try:
        return await fresh.download_media(file=target), False
    except STALE_REFERENCE_ERRORS:
        # A fresh reference failing the same way means the media really is gone
        # for good — self-destructing media is the usual reason.
        print(f"  ! msg {message.id}: media cannot be re-downloaded "
              f"(self-destructing or removed at the source)")
    except Exception as e:
        print(f"  ! media download failed for msg {message.id} after refresh: {e}")
    return None, False


def media_label(message) -> str:
    """
    Describe a message's media from the metadata Telegram already sent, without
    downloading anything — used for the placeholder left by --no-media.
    """
    f = getattr(message, 'file', None)
    what = getattr(f, 'name', None) or getattr(f, 'mime_type', None) or "media"
    size = getattr(f, 'size', None)
    if size:
        units = ("B", "KB", "MB", "GB")
        i = 0
        while size >= 1024 and i < len(units) - 1:
            size /= 1024.0
            i += 1
        what += f" · {size:.0f} {units[i]}" if i == 0 else f" · {size:.1f} {units[i]}"
    return what


def media_markdown(path: str, note_dir: str) -> str:
    """Inline-embed images; link everything else. Links are relative to the note."""
    rel = os.path.relpath(path, note_dir)
    name = os.path.basename(path)
    link = f"<{rel}>" if " " in rel else rel        # angle-brackets keep spaces from breaking the link
    if os.path.splitext(path)[1].lower() in IMAGE_EXTS:
        return f"![{name}]({link})"
    return f"📎 [{name}]({link})"


async def export_messages(dialog, limit: int, skip_media: bool = False,
                          folder: str | None = None,
                          priority: int | None = None, category: str | None = None):
    """
    Download the latest `limit` messages, written oldest-first, as a new
    knotes note in folder (default: knotes_out.knotes_dir()); media goes to the note's companion .d folder.

    With skip_media the transcript still records that a message had media (kind
    and size, from metadata Telegram already sent) but nothing is transferred.
    priority and category default to the folder's .knotes.json.
    Returns (md_path, ids, messages).
    """
    entity = dialog.entity
    title = dialog_title(dialog)

    # Fetch newest-first, then reverse so the transcript reads in original order.
    print(f"\nFetching the latest {limit} messages from “{title}”…")
    messages = await client.get_messages(entity, limit=limit)
    messages = list(reversed(messages))           # oldest -> newest
    print(f"Got {len(messages)} messages.")

    knotes = knotes_dir(folder)
    knotes.mkdir(parents=True, exist_ok=True)
    started = datetime.now()
    out_path = str(new_note_path(knotes, NOTE_TYPE, priority, category, started))
    media_dir = out_path[:-3] + ".d"
    # The .d folder is created on the first download, so text-only notes don't get an empty one.

    cache: dict = {}
    exported_ids = []
    media_count = 0
    reused_count = 0
    skipped_count = 0

    with open(out_path, 'w', encoding='utf-8') as md:
        # knotes notes start with a plain title line; no '#' headings, since
        # the manuscript embeds notes under its own ##/### structure.
        md.write(f"Telegram {title} export\n\n")
        md.write(f"_Latest {len(messages)} messages · exported "
                 f"{started.strftime('%Y-%m-%d %H:%M:%S')}_\n\n---\n\n")

        for n, message in enumerate(messages, 1):
            if n % 50 == 0:
                print(f"  …{n}/{len(messages)}")

            who = await sender_name(message, cache)
            when = message.date.strftime('%Y-%m-%d %H:%M:%S')
            header = f"**`[{message.id}]` {when}"
            if who:
                header += f" — {who}"
            md.write(header + "**\n\n")

            note = await forward_note(message, cache)
            if note:
                md.write(note + "\n\n")

            text = message.text or ""             # keeps Telegram markdown formatting
            if text.strip():
                md.write(text.rstrip() + "\n\n")

            if message.media and skip_media:
                skipped_count += 1
                md.write(f"_[media not downloaded: {media_label(message)}]_\n\n")
            elif message.media:
                os.makedirs(media_dir, exist_ok=True)
                path, reused = await download_media(message, media_dir, entity)
                if path:
                    media_count += 1
                    md.write(media_markdown(path, str(knotes)) + "\n\n")
                    if reused:
                        reused_count += 1
                        print(f"  = media (already on disk): {os.path.basename(path)}")
                    else:
                        print(f"  + media: {os.path.basename(path)}")
                else:
                    md.write("_[media download failed]_\n\n")

            md.write("---\n\n")
            exported_ids.append(message.id)

    summary = f"\nDone. {len(exported_ids)} messages, {media_count} media files"
    if reused_count:
        summary += f" ({reused_count} already on disk, not re-downloaded)"
    if skipped_count:
        summary += f", {skipped_count} media skipped (--no-media)"
    print(summary + ".")
    print(f"Note: {out_path}")
    if os.path.isdir(media_dir):
        print(f"Media dir: {media_dir}")
    return out_path, exported_ids, messages


async def maybe_delete(dialog, ids, messages):
    """Ask whether to delete the exported messages from Telegram; delete only on confirmation."""
    oldest = messages[0].date.strftime('%Y-%m-%d %H:%M')
    newest = messages[-1].date.strftime('%Y-%m-%d %H:%M')
    print(f"\n{len(ids)} messages were saved to disk.")
    print(f"Date range: {oldest}  →  {newest}")
    print("Delete them from Telegram now? This removes them for everyone and CANNOT be undone.")
    answer = await prompt("Type 'delete' to delete, anything else to keep: ")
    if answer is None or answer.lower() != "delete":
        print("Kept. Nothing was deleted from Telegram.")
        return

    # Second confirmation for large batches: retype the exact count.
    if len(ids) > LARGE_BATCH:
        print(f"\nThat's a large batch ({len(ids)} messages, {oldest} → {newest}).")
        confirm = await prompt(f"To confirm, type the number {len(ids)} exactly: ")
        if confirm != str(len(ids)):
            print("Count did not match. Kept. Nothing was deleted from Telegram.")
            return

    await delete_messages(dialog, ids)


async def delete_messages(dialog, ids):
    """Delete the given message ids for everyone, in Telegram's 100-per-call batches."""
    # The confirmation prompt may have sat for a long time before we got here.
    # prompt() keeps the keepalive running, but a suspended laptop stops the
    # whole process regardless, so make sure we still have a link before asking
    # Telegram to delete anything.
    if not client.is_connected():
        print("Reconnecting to Telegram…")
        await client.connect()

    deleted = 0
    # Telegram accepts up to ~100 message ids per delete call.
    for i in range(0, len(ids), 100):
        chunk = ids[i:i + 100]
        try:
            await client.delete_messages(dialog.entity, chunk, revoke=True)
            deleted += len(chunk)
        except Exception as e:
            print(f"  ! failed to delete a batch: {e}")
    print(f"Deleted {deleted}/{len(ids)} messages from Telegram.")
    if deleted < len(ids):
        print("  (Some weren't deleted — likely permission limits on this chat.)")


def parse_args():
    parser = argparse.ArgumentParser(
        description="Download the latest messages from a Telegram group or channel.",
        epilog="Without --chat the script is interactive. With it, it never prompts, "
               "so it can run from cron.",
    )
    parser.add_argument(
        '--list', action='store_true',
        help="print your groups, channels and Saved Messages with their fixed "
             "chat ids, then exit",
    )
    parser.add_argument(
        '--chat', metavar='ID_OR_@USERNAME',
        help="chat to download, given as the fixed Chat ID from --list (e.g. -1001234567890), "
             "an @username, or 'saved' (also 'me'/'self') for Saved Messages; "
             "enables unattended mode (no prompts)",
    )
    parser.add_argument(
        '--limit', type=int, default=None, metavar='N',
        help=f"how many of the latest messages to download (default {DEFAULT_LIMIT})",
    )
    parser.add_argument(
        '--no-media', action='store_true',
        help="don't download photos/videos/files; the transcript notes what each "
             "message had (kind and size) instead. Much faster.",
    )
    add_note_args(parser)
    parser.add_argument(
        '--delete', action='store_true',
        help="with --chat: delete the downloaded messages from Telegram afterwards, "
             "for everyone, without asking. Cannot be undone.",
    )
    args = parser.parse_args()

    if args.limit is not None and args.limit <= 0:
        parser.error("--limit must be a positive number")
    check_note_args(parser, args)
    if args.delete and not args.chat:
        parser.error("--delete only applies to unattended runs; it needs --chat")
    return args


async def main(args):
    if args.list:
        print_dialogs(await load_dialogs())
        return

    if args.chat:                                  # unattended: no prompts at all
        dialog = await find_dialog(args.chat)
        limit = args.limit or DEFAULT_LIMIT
        print(f"Chat: {dialog_title(dialog)}  (id {chat_id(dialog)})")
        _, ids, messages = await export_messages(dialog, limit, args.no_media,
                                                 args.knotes, args.priority, args.category)
        if ids and args.delete:
            await delete_messages(dialog, ids)
        return

    dialog = await pick_dialog()
    limit = args.limit or await ask_limit()
    _, ids, messages = await export_messages(dialog, limit, False,
                                             args.knotes, args.priority, args.category)
    if ids:
        await maybe_delete(dialog, ids, messages)


if __name__ == "__main__":
    cli_args = parse_args()
    if not (API_ID.isdigit() and API_HASH):
        sys.exit("TG_API_ID / TG_API_HASH are not set. Copy config.example.env to "
                 "config.env in the capture folder, fill them in, and run via run.sh.")
    if cli_args.chat or cli_args.list:
        # Unattended: a missing/expired session would otherwise block forever on
        # the login-code prompt, so fail fast and tell the user to log in once.
        client.loop.run_until_complete(client.connect())
        if not client.loop.run_until_complete(client.is_user_authorized()):
            client.disconnect()
            sys.exit("Not logged in. Run the script once interactively to sign in, "
                     "then re-run this command.")
    else:
        client.start(phone=PHONE_NUMBER)
    try:
        client.loop.run_until_complete(main(cli_args))
    finally:
        client.disconnect()
