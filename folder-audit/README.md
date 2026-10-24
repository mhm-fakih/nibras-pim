# folder-audit

Hash-based change auditing for **any folder**, with git history and zip backups.
Set up 2026-08-11 (originally for `~/mdd/knotes-h`, generalised the same day).

## Usage

```bash
folder-audit                      # audit the current directory
folder-audit /home/akil/new/knotes   # audit any folder
folder-audit ~/notes --diff       # show full diffs for changed text files
folder-audit ~/notes --yes        # accept without prompting
folder-audit ~/notes --check      # report only, never writes; exit 1 if changes
folder-audit ~/notes --log        # that folder's audit history and zips
folder-audit --list               # every folder under audit
folder-audit --all --check        # sweep all registered folders
```

`knotes-audit` is a convenience alias pinned to `/home/akil/mdd/knotes-h`; it accepts
all the same flags.

A first run on a folder records a **baseline** and asks nothing further. Every later run
compares against it.

## What a run does

1. **Hashes** every non-hidden file (SHA-256, 8 threads) and records every non-hidden
   folder. Roughly 2.5 s for 900 files / 2.1 GB.
2. **Compares** against that folder's `.audit/manifest.tsv` and reports:
   - new files and folders
   - deleted files and folders
   - changed files — for text, `+N -M` line counts (full diff with `--diff`);
     for media, the size delta
3. **On acceptance only** — nothing below happens if you answer `n`:
   - rewrites `.audit/manifest.tsv`
   - commits to the git repo in the folder
   - writes a datetime-stamped zip to `.backup-zip/`
   - appends a line to `.audit/CHANGES.md`
   - prunes zips beyond `keep_zips`
   - registers the folder in `~/.config/folder-audit/folders.txt`

## Per-folder state

Everything lives inside the folder being audited, so folders are completely independent —
no shared database, and moving or deleting a folder takes its audit state with it.

| Path | What |
|---|---|
| `<folder>/.audit/manifest.tsv` | `type, sha256, size, mtime, path` — files and folders |
| `<folder>/.audit/config.json` | per-folder settings (below) |
| `<folder>/.audit/CHANGES.md` | one line per accepted audit |
| `<folder>/.backup-zip/` | `<folder>_<date>_<time>_<sha>.zip` |
| `<folder>/.git/` | content history |

The only global state is the registry at `~/.config/folder-audit/folders.txt`, which just
lists which folders are under audit so `--list` and `--all` can find them.

## Configuration

Written to `<folder>/.audit/config.json` on first accepted run; edit it to change
behaviour for that folder alone:

```json
{
  "media_ext": [".mp3", ".mp4", ".m4a", ".wav", ".ogg", ".flac", "..."],
  "max_git_mb": 20,
  "keep_zips": 10,
  "git": true,
  "zip": true
}
```

- **`media_ext` / `max_git_mb`** — files matching either rule are hashed but **not**
  committed to git. Their changes are still reported, just not recoverable from history.
  The size rule matters as much as the extension list: in `knotes-h` it caught a 20.5 MB
  extension-less file called `Discovery` that no extension list would have.
- **`git: false`** — audit and zip only, no repo. Use for folders that already contain
  someone else's git repo, or where history is not wanted.
- **`zip: false`** — git only, no zip snapshots.

## Double protection

Two independent copies of every non-media file:

- **git** — full per-commit history: `git -C <folder> show <sha>:<path>`
- **zip** — a self-contained snapshot per accepted audit, readable with any unzip tool,
  no git required. Each zip also contains `.audit/manifest.tsv`, so an extracted snapshot
  can be verified against its own hashes.

## Safety guards

- **Refuses `/`, `~`, `/home`, `/etc`, `/usr`, `/var`, `/tmp`** — audit a specific
  folder, not a system root.
- **Refuses to commit into a foreign git repo.** If the folder already has a repo whose
  last commit was authored by anyone other than folder-audit, it stops and tells you to
  either set `"git": false` or pick another folder. It will never write commits into a
  project's real history.
- **Warns on overlap** with an already-audited folder (nesting works, but you would be
  auditing the same files twice).
- **`--check` never writes anything** — safe in scripts and cron. Exit 1 means changes
  are pending.

## Scope

Only **non-hidden** files and folders are audited. Hidden entries are excluded from git
too — except the folder's own `/.audit/`, so the manifest and changelog are versioned
alongside the content they describe. The exclusion is anchored, so a nested folder under
its own audit keeps its `.audit/` out of the parent repo.

### Syncthing note (applies to `knotes-h`)

`~/mdd/knotes-h` is a Syncthing folder (`sendreceive`) whose `.stignore` is `.*`, so
Syncthing ignores every hidden entry — `.git/`, `.audit/` and `.backup-zip/` are **not**
replicated to other devices. Audit state stays local and the zips are never pushed over
the network.

A consequence: changes arriving *from another device* appear as ordinary new/changed/
deleted entries. The audit tells you what changed, not who changed it. This is not
hypothetical — the 2026-08-11 12:39 audit captured 21 photos and an edited note that
appeared during setup.

## Restoring

```bash
cd <folder>
git log --oneline                       # find the audit
git show <sha> --stat                   # what it changed
git show <sha>:path/to/note.md          # a file's content at that point
git checkout <sha> -- path/to/note.md   # restore into the working tree
```

From a zip:

```bash
unzip -l <folder>/.backup-zip/<name>.zip
unzip -o <folder>/.backup-zip/<name>.zip 'path/to/note.md' -d /tmp/restore
```

Media files are recoverable from neither, by design. The manifest can still confirm
*that* one changed and what its hash was: `git log -p -- .audit/manifest.tsv`.

## Files

| Path | Purpose |
|---|---|
| `folder-audit/audit.py` in this repo | the tool |
| `~/.local/bin/folder-audit` | launcher: a symlink to `audit.py` (`ln -s "$PWD/audit.py" ~/.local/bin/folder-audit`) |
| `~/.local/bin/knotes-audit` | alias pinned to `~/mdd/knotes-h` |
| `~/.config/folder-audit/folders.txt` | registry of audited folders |

## knotes-h baseline

Recorded 2026-08-11: 894 files, 23 folders, 2.1 GB — 700 committed in full (46 MB repo),
194 hash-only. Grew to 915 files at the 12:39 audit.
