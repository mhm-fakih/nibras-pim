#!/usr/bin/env bash
#
# dedup-symlink.sh — replace duplicate files with relative symlinks, keeping the
# real copy in a preferred set of directories (priority tiers).
#
# WHAT IT DOES
#   For every group of byte-identical files it finds, ONE copy is kept as the
#   real file and the others are replaced by RELATIVE SYMLINKS pointing at it.
#   No file is ever deleted — a duplicate is only ever turned into a symlink to
#   an identical copy that still exists.
#
#   The kept ("master") copy is chosen by PRIORITY: if a group has a copy in the
#   first priority dir it is kept there; otherwise the second; otherwise the
#   third; otherwise (a group that lives only outside the priority dirs) one copy
#   is kept and the rest symlinked. Anything reached via SCAN_ROOTS that is not
#   in a priority dir always ranks BELOW every priority dir.
#
# HOW THE PRIORITY IS ENFORCED
#   jdupes -O (--param-order) makes command-line argument order the tiebreaker
#   for which file in a set is the master. So we pass the priority dirs first
#   (highest first), then the SCAN_ROOTS last, in order. jdupes de-duplicates
#   overlapping paths by device+inode, so listing a dir both explicitly and again
#   via its parent root is safe — each file is considered exactly once.
#
# SAFETY
#   * APPLY mode never uses -Q, so matches are confirmed byte-for-byte before any
#     file is touched. (A hash-only false match would silently replace a unique
#     file with a symlink to different content — i.e. data loss. Never in apply.)
#   * PREVIEW mode changes nothing, so it uses -Q for speed by default.
#   * Certain extensions are excluded so symlinks can't break sites/programs that
#     don't follow them (e.g. .jar/.js/.css/.groovy). Extend NOEXT as needed.
#
# CROSS-FILESYSTEM NOTE
#   SCAN_ROOTS may live on different drives. Symlinks (unlike hard links) can
#   cross filesystems, and jdupes will emit a relative link that walks up and
#   over (e.g. ../../../otherdrive/file). Such a link BREAKS while the drive
#   holding the master is unmounted. If you do not want that, run this script
#   once per drive instead of listing both roots here.
#
# USAGE
#   ./dedup-symlink.sh preview                 # list duplicate sets, change nothing
#   ./dedup-symlink.sh apply                   # create symlinks (asks to confirm)
#   ./dedup-symlink.sh apply --yes             # apply without the confirm prompt
#   ./dedup-symlink.sh apply --size 1000KB     # override the min-size threshold
#   ./dedup-symlink.sh preview --size 1M --yes # flags may be combined, any order
#
#   Flags (after the mode):
#     --size VALUE   minimum file size to consider (jdupes suffix: K/M/G, add B
#                    for decimal). Overrides the MIN_SIZE default below.
#                    Examples: 1000KB (=1MB decimal), 10000KB (=10MB), 1M, 500K.
#     --yes          skip the apply confirmation prompt.
#
# PORTABILITY
#   Put this machine's paths in dedup.conf next to the script (gitignored; it
#   overrides the CONFIG block below), or edit the block. Requires: bash, jdupes (>= 1.20).
#
# ---------------------------------------------------------------------------
set -euo pipefail

# ============================= CONFIG ======================================
# Roots that get fully scanned, in descending priority. Everything under these
# is considered, but ranks BELOW the priority dirs. Absolute paths. Roots may
# sit on different filesystems — see CROSS-FILESYSTEM NOTE above.
SCAN_ROOTS=(
  "/media/$USER/data-drive"
  "/media/$USER/backup-drive/home/$USER"
)

# Priority tiers, HIGHEST priority first. Absolute paths (or relative to $PWD).
# A group's master is kept in the earliest-listed tier that has a copy. Missing
# dirs are skipped with a warning.
PRIORITY_DIRS=(
  "/media/$USER/data-drive/main"
  "/media/$USER/data-drive/secondary"
)

# Also scan the whole of each SCAN_ROOTS entry (lower priority than all tiers
# above)? If "no", only the PRIORITY_DIRS are scanned.
SCAN_WHOLE_BASE="yes"

# Default minimum file size to consider (jdupes size suffix: K/M/G, add B for
# decimal). "500KB" = 500 kB (decimal). Use "500KiB"/"500K" for binary.
# Override per-run without editing this file via:  --size VALUE
MIN_SIZE="500KB"

# Comma-separated extensions to EXCLUDE (never symlinked). Protects things that
# break when symlinked or are read by programs that don't follow links.
NOEXT="jar,war,class,log,dat,groovy,properties,js,css,xml,java,py,ts,tsx,jsx,json,yml,yaml,html,htm,sh,sql,gradle,md,txt,conf,ini"

# Path substrings to exclude entirely (matched case-sensitively). Good for
# unreadable/system dirs. Wrap dir names in slashes, e.g. "/lost+found/".
EXCLUDE_PATHS=(
  "/lost+found/"
)

# Use quick (hash-only) matching in PREVIEW for speed. Preview never modifies
# files so this is safe. APPLY always ignores this and confirms byte-for-byte.
QUICK_PREVIEW="yes"
# ===========================================================================

# This machine's real values: dedup.conf next to this script (gitignored) may set
# any of the variables above, e.g. SCAN_ROOTS=(...) and PRIORITY_DIRS=(...).
LOCAL_CONF="$(dirname "$(readlink -f "$0")")/dedup.conf"
# shellcheck source=/dev/null
[ -f "$LOCAL_CONF" ] && . "$LOCAL_CONF"

log()  { printf '%s\n' "$*" >&2; }
die()  { printf 'ERROR: %s\n' "$*" >&2; exit 1; }

command -v jdupes >/dev/null 2>&1 || die "jdupes not found in PATH. Install it first."

MODE="${1:-preview}"
[ $# -gt 0 ] && shift
ASSUME_YES="no"
while [ $# -gt 0 ]; do
  case "$1" in
    --yes)     ASSUME_YES="yes" ;;
    --size)    shift; [ $# -gt 0 ] || die "--size needs a value"; MIN_SIZE="$1" ;;
    --size=*)  MIN_SIZE="${1#*=}" ;;
    *)         die "unknown argument: $1 (use: preview|apply [--size VALUE] [--yes])" ;;
  esac
  shift
done
[ -n "$MIN_SIZE" ] || die "MIN_SIZE is empty"

# --- build the common jdupes options -------------------------------------
COMMON=( -r -A -O )
for p in "${EXCLUDE_PATHS[@]}"; do COMMON+=( -X "nostr:$p" ); done
COMMON+=( -X "size+:$MIN_SIZE" )
[ -n "$NOEXT" ] && COMMON+=( -X "noext:$NOEXT" )

# --- build the ordered target list (priority tiers first, then roots) -----
TARGETS=()
for d in "${PRIORITY_DIRS[@]}"; do
  if [ -d "$d" ]; then TARGETS+=( "$d" ); else log "warn: priority dir not found, skipping: $d"; fi
done
[ "${#TARGETS[@]}" -gt 0 ] || die "none of the PRIORITY_DIRS exist"
if [ "$SCAN_WHOLE_BASE" = "yes" ]; then
  for r in "${SCAN_ROOTS[@]}"; do
    [ -d "$r" ] || die "scan root not found (drive not mounted?): $r"
    TARGETS+=( "$r" )
  done
fi

summarize() {  # reads a jdupes list on stdin; per-tier master counts + reclaimable bytes
  awk -v tiers="$(printf '%s\n' "${PRIORITY_DIRS[@]}")" '
    function human(b,  u,i){ split("B KB MB GB TB",u," "); i=1
      while(b>=1000 && i<5){ b/=1000; i++ } return sprintf("%.1f %s", b, u[i]) }
    BEGIN{ n=split(tiers,T,"\n"); newset=1 }
    /^$/ { newset=1; next }
    {
      if(newset==1){
        sets++; newset=0; master=$0
        cmd="stat -c %s \"" master "\" 2>/dev/null"; cmd|getline sz; close(cmd)
        size[sets]=sz+0
        tag="(other)"
        for(i=1;i<=n;i++){ t=T[i]; if(t!="" && (master==t || index(master,t"/")==1)){ tag=t; break } }
        cnt[tag]++
      } else {
        dups++; saved += size[sets]
      }
    }
    END{
      printf "duplicate sets      : %d\n", sets
      printf "files to symlink    : %d\n", dups+0
      printf "space reclaimable   : ~%s\n", human(saved+0)
      for(i=1;i<=n;i++){ t=T[i]; if(t!="") printf "  master kept in %-46s : %d\n", t, cnt[t]+0 }
      printf "  master kept in %-46s : %d\n", "(other/base)", cnt["(other)"]+0
    }'
}

case "$MODE" in
  preview)
    OPTS=( "${COMMON[@]}" )
    [ "$QUICK_PREVIEW" = "yes" ] && OPTS+=( -Q )
    log "== PREVIEW (no changes) =="
    log "min size: $MIN_SIZE"
    log "targets (priority order):"; for t in "${TARGETS[@]}"; do log "  $t"; done
    out="$(jdupes "${OPTS[@]}" "${TARGETS[@]}" || true)"
    printf '%s\n' "$out"
    log "----------------------------------------"
    printf '%s\n' "$out" | summarize >&2
    log "In each set above, the FIRST file is the master that stays real;"
    log "the rest would become relative symlinks to it. Run 'apply' to do it."
    ;;
  apply)
    log "== APPLY: duplicates -> relative symlinks (byte-for-byte confirmed) =="
    log "min size: $MIN_SIZE"
    log "targets (priority order):"; for t in "${TARGETS[@]}"; do log "  $t"; done
    if [ "$ASSUME_YES" != "yes" ]; then
      printf 'Proceed and modify files under the above roots? [y/N] ' >&2
      read -r ans; case "$ans" in y|Y|yes|YES) ;; *) die "aborted by user." ;; esac
    fi
    # -l = make relative symlinks, no prompt. NO -Q here (safety).
    jdupes "${COMMON[@]}" -l "${TARGETS[@]}"
    log "Done. Duplicates are now relative symlinks to the kept master copies."
    ;;
  *)
    die "unknown mode '$MODE'. Use: preview | apply [--yes]"
    ;;
esac
