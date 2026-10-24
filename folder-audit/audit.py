#!/usr/bin/env python3
"""
folder-audit — hash-based change auditing for any folder.

Each run hashes every non-hidden file under the target folder, compares against that
folder's stored manifest, and reports new / changed / deleted files and folders. On
acceptance it updates the manifest, commits to a git repo inside the folder, and
writes a datetime-stamped zip backup.

Non-media files are committed to git in full. Media (audio/video) and anything over
max_git_mb are recorded by hash only -- their changes are still reported, just not
stored. Hidden entries are never audited.

State lives inside each audited folder, so folders are fully independent:
    <folder>/.audit/manifest.tsv     hashes of every file and folder
    <folder>/.audit/config.json      per-folder settings
    <folder>/.audit/CHANGES.md       one line per accepted audit
    <folder>/.backup-zip/            datetime-stamped zips
    <folder>/.git/                   content history
"""

import argparse
import datetime as dt
import hashlib
import json
import os
import subprocess
import sys
import zipfile
from concurrent.futures import ThreadPoolExecutor

REGISTRY = os.path.expanduser('~/.config/folder-audit/folders.txt')

DEFAULT_CONFIG = {
    'media_ext': ['.mp3', '.mp4', '.m4a', '.wav', '.ogg', '.oga', '.flac', '.opus',
                  '.mkv', '.webm', '.avi', '.mov', '.wma', '.aac', '.30mhz'],
    'max_git_mb': 20,     # any file larger than this is hash-only, whatever its type
    'keep_zips': 10,      # zip backups retained in .backup-zip
    'git': True,          # commit non-media content to a git repo in the folder
    'zip': True,          # write a zip snapshot per accepted audit
}

# Refuse to audit these outright -- too large or too dangerous to treat as a folder.
FORBIDDEN = {'/', os.path.expanduser('~'), '/home', '/etc', '/usr', '/var', '/tmp'}

C = {'r': '\033[0m', 'b': '\033[1m', 'gr': '\033[32m', 'y': '\033[33m',
     'rd': '\033[31m', 'dim': '\033[2m', 'cy': '\033[36m'}
if not sys.stdout.isatty():
    C = {k: '' for k in C}

# Set by configure()
ROOT = AUDIT_DIR = MANIFEST = CHANGELOG = CONFIG_FILE = BACKUP_DIR = None
CFG = dict(DEFAULT_CONFIG)


def configure(path):
    global ROOT, AUDIT_DIR, MANIFEST, CHANGELOG, CONFIG_FILE, BACKUP_DIR, CFG
    ROOT = os.path.realpath(os.path.expanduser(path))
    AUDIT_DIR = os.path.join(ROOT, '.audit')
    MANIFEST = os.path.join(AUDIT_DIR, 'manifest.tsv')
    CHANGELOG = os.path.join(AUDIT_DIR, 'CHANGES.md')
    CONFIG_FILE = os.path.join(AUDIT_DIR, 'config.json')
    BACKUP_DIR = os.path.join(ROOT, '.backup-zip')
    CFG = dict(DEFAULT_CONFIG)
    if os.path.exists(CONFIG_FILE):
        try:
            with open(CONFIG_FILE, encoding='utf-8') as f:
                CFG.update(json.load(f))
        except (OSError, ValueError) as e:
            sys.exit(f'bad config {CONFIG_FILE}: {e}')
    return ROOT


def save_config():
    os.makedirs(AUDIT_DIR, exist_ok=True)
    if not os.path.exists(CONFIG_FILE):
        with open(CONFIG_FILE, 'w', encoding='utf-8') as f:
            json.dump(CFG, f, indent=2)
            f.write('\n')


# ---------------------------------------------------------------- registry

def registry_read():
    if not os.path.exists(REGISTRY):
        return []
    with open(REGISTRY, encoding='utf-8') as f:
        return [ln.strip() for ln in f if ln.strip() and not ln.startswith('#')]


def registry_add(path):
    folders = registry_read()
    if path not in folders:
        folders.append(path)
        os.makedirs(os.path.dirname(REGISTRY), exist_ok=True)
        with open(REGISTRY, 'w', encoding='utf-8') as f:
            f.write('# folders under audit — one absolute path per line\n')
            f.write('\n'.join(sorted(folders)) + '\n')


# ---------------------------------------------------------------- scanning

def sha256(path):
    h = hashlib.sha256()
    try:
        with open(path, 'rb') as f:
            for chunk in iter(lambda: f.read(1 << 20), b''):
                h.update(chunk)
    except OSError as e:
        return f'ERROR:{e.__class__.__name__}'
    return h.hexdigest()


def scan():
    """Return ({relpath: (sha, size, mtime)}, {reldir})."""
    files, dirs = {}, set()
    for base, subdirs, names in os.walk(ROOT):
        subdirs[:] = [d for d in subdirs if not d.startswith('.')]
        rel_base = os.path.relpath(base, ROOT)
        if rel_base != '.':
            dirs.add(rel_base)
        for n in names:
            if n.startswith('.'):
                continue
            p = os.path.join(base, n)
            rel = os.path.relpath(p, ROOT)
            if os.path.islink(p) or not os.path.isfile(p):
                continue
            try:
                st = os.stat(p)
            except OSError:
                continue
            files[rel] = [None, st.st_size, int(st.st_mtime)]

    paths = list(files)
    with ThreadPoolExecutor(8) as ex:
        for rel, digest in zip(paths, ex.map(
                lambda r: sha256(os.path.join(ROOT, r)), paths)):
            files[rel][0] = digest
    return {k: tuple(v) for k, v in files.items()}, dirs


def is_media(rel, size):
    ext = os.path.splitext(rel)[1].lower()
    return ext in set(CFG['media_ext']) or size > CFG['max_git_mb'] * 1024 * 1024


def looks_textual(rel):
    try:
        with open(os.path.join(ROOT, rel), 'rb') as f:
            chunk = f.read(8192)
    except OSError:
        return False
    if b'\0' in chunk:
        return False
    try:
        chunk.decode('utf-8')
    except UnicodeDecodeError:
        return False
    return True


# ---------------------------------------------------------------- manifest

def load_manifest():
    files, dirs = {}, set()
    if not os.path.exists(MANIFEST):
        return files, dirs, False
    with open(MANIFEST, encoding='utf-8') as f:
        for line in f:
            line = line.rstrip('\n')
            if not line or line.startswith('#'):
                continue
            kind, digest, size, mtime, rel = line.split('\t', 4)
            if kind == 'D':
                dirs.add(rel)
            else:
                files[rel] = (digest, int(size), int(mtime))
    return files, dirs, True


def save_manifest(files, dirs):
    os.makedirs(AUDIT_DIR, exist_ok=True)
    with open(MANIFEST, 'w', encoding='utf-8') as f:
        f.write(f'# {os.path.basename(ROOT)} manifest — '
                f'{len(files)} files, {len(dirs)} folders\n')
        f.write('# type\tsha256\tsize\tmtime\tpath\n')
        for rel in sorted(dirs):
            f.write(f'D\t-\t0\t0\t{rel}\n')
        for rel in sorted(files):
            d, s, m = files[rel]
            f.write(f'F\t{d}\t{s}\t{m}\t{rel}\n')


# ---------------------------------------------------------------- git

def git(*args, check=True):
    return subprocess.run(('git', '-C', ROOT) + args, check=check,
                          text=True, capture_output=True)


def git_ready():
    return os.path.isdir(os.path.join(ROOT, '.git'))


def esc(rel):
    """Escape a path for .git/info/exclude (anchored, literal)."""
    out = rel.replace('\\', '\\\\')
    for ch in '*?[]':
        out = out.replace(ch, '\\' + ch)
    return '/' + out


def write_exclude(files):
    """Regenerate .git/info/exclude so media/oversized files stay untracked."""
    path = os.path.join(ROOT, '.git', 'info', 'exclude')
    os.makedirs(os.path.dirname(path), exist_ok=True)
    lines = ['# generated by folder-audit — do not edit',
             "# hidden entries are out of the audit's scope, so git ignores them",
             '# too — except .audit/, which holds the manifest and changelog.',
             '# The negation is anchored, so a nested folder under its own audit',
             '# keeps its .audit/ out of this repo.',
             '.*', '!/.audit/', '!/.audit/**', '']
    lines += [esc(rel) for rel, (_, size, _) in sorted(files.items())
              if is_media(rel, size)]
    with open(path, 'w', encoding='utf-8') as f:
        f.write('\n'.join(lines) + '\n')


def diffstat(rel):
    r = git('diff', '--numstat', '--', rel, check=False)
    if r.returncode or not r.stdout.strip():
        return None
    a, d, *_ = r.stdout.split()
    return (a, d)


# ---------------------------------------------------------------- backup zip

def make_zip(files, tag):
    os.makedirs(BACKUP_DIR, exist_ok=True)
    stamp = dt.datetime.now().strftime('%Y-%m-%d_%H%M%S')
    name = f'{os.path.basename(ROOT)}_{stamp}_{tag}.zip'
    out = os.path.join(BACKUP_DIR, name)
    n = 0
    with zipfile.ZipFile(out, 'w', zipfile.ZIP_DEFLATED, compresslevel=6) as z:
        for rel, (_, size, _) in sorted(files.items()):
            if is_media(rel, size):
                continue
            z.write(os.path.join(ROOT, rel), rel)
            n += 1
        if os.path.exists(MANIFEST):
            z.write(MANIFEST, '.audit/manifest.tsv')
    return out, n, os.path.getsize(out)


def prune_zips():
    if not os.path.isdir(BACKUP_DIR):
        return []
    zips = sorted(f for f in os.listdir(BACKUP_DIR) if f.endswith('.zip'))
    keep = CFG['keep_zips']
    dead = zips[:-keep] if len(zips) > keep else []
    for f in dead:
        os.remove(os.path.join(BACKUP_DIR, f))
    return dead


# ---------------------------------------------------------------- reporting

def human(n):
    for unit in ('B', 'KB', 'MB', 'GB'):
        if abs(n) < 1024 or unit == 'GB':
            return f'{n:.0f} {unit}' if unit == 'B' else f'{n:.1f} {unit}'
        n /= 1024


def report(new, changed, deleted, new_dirs, deleted_dirs, cur, old, show_diff):
    total = len(new) + len(changed) + len(deleted) + len(new_dirs) + len(deleted_dirs)
    if not total:
        print(f"{C['gr']}No changes.{C['r']} {len(cur)} files, "
              f"{C['dim']}manifest up to date{C['r']}")
        return 0

    print(f"{C['b']}Changes since last audit{C['r']}  {C['dim']}{ROOT}{C['r']}\n")

    if new_dirs:
        print(f"  {C['gr']}{C['b']}NEW FOLDERS{C['r']} ({len(new_dirs)})")
        for d in sorted(new_dirs):
            print(f"    {C['gr']}+{C['r']} {d}/")
        print()
    if new:
        print(f"  {C['gr']}{C['b']}NEW FILES{C['r']} ({len(new)})")
        for rel in sorted(new):
            size = cur[rel][1]
            tag = f"{C['dim']}media{C['r']} " if is_media(rel, size) else ''
            print(f"    {C['gr']}+{C['r']} {rel}  {C['dim']}({tag}{human(size)}){C['r']}")
        print()
    if changed:
        print(f"  {C['y']}{C['b']}CHANGED{C['r']} ({len(changed)})")
        for rel in sorted(changed):
            osize, nsize = old[rel][1], cur[rel][1]
            if is_media(rel, nsize):
                detail = f"{C['dim']}media, {human(osize)} → {human(nsize)}{C['r']}"
            else:
                ds = diffstat(rel) if git_ready() else None
                detail = (f"{C['gr']}+{ds[0]}{C['r']} {C['rd']}-{ds[1]}{C['r']} lines"
                          if ds else
                          f"{C['dim']}{human(osize)} → {human(nsize)}{C['r']}")
            print(f"    {C['y']}~{C['r']} {rel}  {detail}")
            if show_diff and git_ready() and looks_textual(rel) \
                    and not is_media(rel, nsize):
                for ln in git('diff', '--no-color', '--', rel,
                              check=False).stdout.splitlines()[4:]:
                    col = C['gr'] if ln.startswith('+') else (
                        C['rd'] if ln.startswith('-') else C['dim'])
                    print(f'        {col}{ln}{C["r"]}')
        print()
    if deleted:
        print(f"  {C['rd']}{C['b']}DELETED FILES{C['r']} ({len(deleted)})")
        for rel in sorted(deleted):
            print(f"    {C['rd']}-{C['r']} {rel}  {C['dim']}({human(old[rel][1])}){C['r']}")
        print()
    if deleted_dirs:
        print(f"  {C['rd']}{C['b']}DELETED FOLDERS{C['r']} ({len(deleted_dirs)})")
        for d in sorted(deleted_dirs):
            print(f"    {C['rd']}-{C['r']} {d}/")
        print()
    return total


# ---------------------------------------------------------------- safety

def preflight(first_time):
    """Guard against auditing something that shouldn't be audited."""
    if not os.path.isdir(ROOT):
        sys.exit(f'not a folder: {ROOT}')
    if (ROOT.rstrip('/') or '/') in FORBIDDEN:
        sys.exit(f'refusing to audit {ROOT} — pick a specific folder')
    if not first_time:
        return
    # An existing git repo we did not create is not ours to commit into.
    if git_ready() and CFG['git']:
        r = git('log', '-1', '--format=%an', check=False)
        author = r.stdout.strip()
        if author and author not in ('folder-audit', 'knotes-audit'):
            sys.exit(
                f"{ROOT} already contains a git repo (last commit by {author!r}).\n"
                f"folder-audit will not commit into someone else's repo.\n"
                f'Either audit without git — mkdir -p "{AUDIT_DIR}" && '
                f'echo \'{{"git": false}}\' > "{CONFIG_FILE}" — or pick another folder.')
    for other in registry_read():
        if other != ROOT and (ROOT.startswith(other + '/')
                              or other.startswith(ROOT + '/')):
            print(f"{C['y']}warning:{C['r']} overlaps an audited folder: {other}")


# ---------------------------------------------------------------- main

def run(path, args):
    configure(path)
    first_time = not os.path.exists(MANIFEST)
    preflight(first_time)

    if args.log:
        print(f"{C['b']}{ROOT}{C['r']}")
        if git_ready():
            print(git('log', '--oneline', '--decorate', '-20').stdout, end='')
        if os.path.isdir(BACKUP_DIR):
            zips = sorted(os.listdir(BACKUP_DIR))
            print(f"\n{len(zips)} zip backups (keeping last {CFG['keep_zips']})")
            for z in zips[-5:]:
                print('  ', z)
        return 0

    need_git_init = CFG['git'] and not git_ready()
    if need_git_init and not args.check:
        print(f"{C['cy']}Initialising git repo in {ROOT}{C['r']}")
        subprocess.run(('git', 'init', '-q', '-b', 'main', ROOT), check=True)
        git('config', 'user.name', 'folder-audit')
        git('config', 'user.email', 'folder-audit@localhost')

    cur, cur_dirs = scan()
    old, old_dirs, had_manifest = load_manifest()

    new = {r for r in cur if r not in old}
    deleted = {r for r in old if r not in cur}
    changed = {r for r in cur if r in old and cur[r][0] != old[r][0]}
    new_dirs = cur_dirs - old_dirs
    deleted_dirs = old_dirs - cur_dirs

    if not had_manifest:
        tracked = sum(1 for r, (_, s, _) in cur.items() if not is_media(r, s))
        size = sum(s for _, s, _ in cur.values())
        print(f"{C['cy']}{C['b']}Baseline run{C['r']} — {ROOT}\n")
        print(f"  {len(cur)} files, {len(cur_dirs)} folders, {human(size)}")
        print(f"  {tracked} committed to git in full, "
              f"{len(cur) - tracked} recorded by hash only (media/oversized)\n")
        n = 1
    else:
        n = report(new, changed, deleted, new_dirs, deleted_dirs,
                   cur, old, args.diff)

    if args.check:
        return 1 if n else 0
    if not n:
        return 0

    if not args.yes:
        try:
            ans = input(f"{C['b']}Accept these changes? [y/N]{C['r']} ").strip().lower()
        except (EOFError, KeyboardInterrupt):
            print('\naborted')
            return 0
        if ans not in ('y', 'yes'):
            print('aborted — manifest, git and zips left untouched')
            return 0

    save_config()
    save_manifest(cur, cur_dirs)

    if not had_manifest:
        summary = f'baseline: {len(cur)} files, {len(cur_dirs)} folders'
    else:
        bits = []
        if new: bits.append(f'+{len(new)} new')
        if changed: bits.append(f'~{len(changed)} changed')
        if deleted: bits.append(f'-{len(deleted)} deleted')
        if new_dirs: bits.append(f'+{len(new_dirs)} folders')
        if deleted_dirs: bits.append(f'-{len(deleted_dirs)} folders')
        summary = ', '.join(bits)

    stamp = dt.datetime.now().strftime('%Y-%m-%d %H:%M')
    sha = 'nogit'
    if CFG['git']:
        write_exclude(cur)
        git('add', '-A')
        r = git('commit', '-q', '-m', f'audit {stamp}: {summary}', check=False)
        if r.returncode:
            print(f"{C['dim']}(git: nothing to commit — media-only change){C['r']}")
        sha = git('rev-parse', '--short', 'HEAD', check=False).stdout.strip() or 'nogit'

    zipline = ''
    if CFG['zip']:
        path_, count, size = make_zip(cur, sha)
        pruned = prune_zips()
        zipline = f'{os.path.basename(path_)} ({human(size)})'

    with open(CHANGELOG, 'a', encoding='utf-8') as f:
        f.write(f'- **{stamp}** `{sha}` — {summary}'
                + (f' — zip `{zipline}`' if zipline else '') + '\n')
    if CFG['git']:
        git('add', '-A')
        git('commit', '-q', '--amend', '--no-edit', check=False)

    registry_add(ROOT)

    print(f"\n{C['gr']}Accepted.{C['r']}")
    print(f"  manifest  {len(cur)} files, {len(cur_dirs)} folders")
    if CFG['git']:
        print(f"  commit    {sha}  {summary}")
    if CFG['zip']:
        print(f"  zip       {os.path.basename(path_)}  ({count} files, {human(size)})")
        if pruned:
            print(f"  pruned    {len(pruned)} old zip(s)")
    return 0


def main():
    ap = argparse.ArgumentParser(
        prog='folder-audit',
        description='Hash-based change auditing for any folder.',
        epilog='State is stored inside each audited folder, so folders are independent.')
    ap.add_argument('folder', nargs='?', default='.',
                    help='folder to audit (default: current directory)')
    ap.add_argument('-y', '--yes', action='store_true', help='accept without prompting')
    ap.add_argument('-c', '--check', action='store_true',
                    help='report only, never modify; exit 1 if changes found')
    ap.add_argument('-d', '--diff', action='store_true',
                    help='show full diffs for changed text files')
    ap.add_argument('-l', '--log', action='store_true',
                    help='show audit history and zip inventory')
    ap.add_argument('-a', '--all', action='store_true',
                    help='run over every folder in the registry')
    ap.add_argument('--list', action='store_true',
                    help='list folders under audit')
    args = ap.parse_args()

    if args.list:
        folders = registry_read()
        if not folders:
            print('no folders under audit yet')
            return 0
        for f in folders:
            configure(f)
            n = '?'
            if os.path.exists(MANIFEST):
                with open(MANIFEST, encoding='utf-8') as fh:
                    n = sum(1 for ln in fh if ln.startswith('F\t'))
            mark = '' if os.path.isdir(f) else f"  {C['rd']}(missing){C['r']}"
            print(f'  {f}  {C["dim"]}{n} files{C["r"]}{mark}')
        return 0

    if args.all:
        rc = 0
        for f in registry_read():
            if not os.path.isdir(f):
                print(f"{C['rd']}skipping missing:{C['r']} {f}")
                continue
            print(f"{C['b']}=== {f}{C['r']}")
            rc = run(f, args) or rc
            print()
        return rc

    return run(args.folder, args)


if __name__ == '__main__':
    sys.exit(main() or 0)
