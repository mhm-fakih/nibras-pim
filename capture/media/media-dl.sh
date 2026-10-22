#!/usr/bin/env bash
#
# media-dl.sh - download media links with yt-dlp into one knotes Resource note.
#
# A run becomes  <yyyy_mm_dd-hhmmss>~R~p<priority>~<category>.md  in the knotes
# folder: the line you type at the "Note:" prompt (or -m TEXT), then each item's
# title, duration/site/resolution and URL. Its companion folder  <same name>.d/
# holds, for every item:
#   1. the full media file  (best video + best audio, merged to mp4) - not with -a
#   2. an audio-only file   (128 kbps, default mp3)
#   3. a subtitle file      (.srt) in the *audio* language - the human-made track
#      if the uploader provided one, otherwise the auto-generated (ASR) track.
#      YouTube's auto-captions scroll, repeating every line ~3x; those duplicate
#      cues are collapsed automatically (uploader-authored subs are left alone).
#
# Usage:  ./media-dl.sh [options] [URL...]
#
# Several links may share one argument (a pasted block works); a bare 11-char
# YouTube id is taken as that video. With no URL, the
# links are asked for, one or more per line, until an empty line. Then the note's
# text is asked for - one line; empty uses the (first) media title.
#
#   -m TEXT   the note's text, no prompt (empty = the media title)
#   -o DIR    knotes folder          (default: $NIBRAS_KNOTES_DIR, else ~/new/knotes)
#   -P N      note priority          (default: the folder's .knotes.json, else 2)
#   -k X      note category letter   (default: the folder's .knotes.json, else D)
#   -f FMT    audio format           (default: mp3;  also: m4a, opus, flac...)
#   -b RATE   audio bitrate          (default: 128k)
#   -l LANG   force subtitle language instead of auto-detecting it
#   -c BROWSER  read cookies from browser (chrome|firefox|brave|edge...)
#   -p        treat the URL as a playlist (default: single video only)
#   -a        audio only: download just the audio stream, keep the mp3, no video
#   -V        voice quality: mono 64k (speech sounds the same at half the size);
#             -b after -V still sets the bitrate
#   -A        skip the audio-only file
#   -S        skip subtitles
#   -E        also embed the (cleaned) subtitles into the video container
#   -D        do not collapse duplicate auto-caption cues
#   -U        skip the yt-dlp self-update check
#   -h        this help
#
# Env overrides: NIBRAS_KNOTES_DIR, MEDIA_DL_AUDIO_FMT, MEDIA_DL_AUDIO_BR,
#                MEDIA_DL_COOKIES_BROWSER

set -uo pipefail

KNOTES_OUT="$(dirname "$(readlink -f "$0")")/../knotes_out.py"
OUTROOT="${NIBRAS_KNOTES_DIR:-$HOME/new/knotes}"
PRIORITY=""
TEXT=""
HAVE_TEXT=0
CATEGORY=""
AUDIO_FMT="${MEDIA_DL_AUDIO_FMT:-mp3}"
AUDIO_BR="${MEDIA_DL_AUDIO_BR:-128k}"
AUDIO_CH=2
COOKIES_BROWSER="${MEDIA_DL_COOKIES_BROWSER:-}"
FORCE_LANG=""
PLAYLIST=0
WANT_AUDIO=1
AUDIO_ONLY=0
WANT_SUBS=1
EMBED_SUBS=0
DO_DEDUP=1
DO_UPDATE=1

die()  { printf '\033[31merror:\033[0m %s\n' "$*" >&2; exit 1; }
info() { printf '\033[36m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[33mwarn:\033[0m %s\n' "$*" >&2; }
usage() { awk 'NR>1 && /^#/ {sub(/^# ?/,""); print; next} NR>1 {exit}' "$0"; exit 0; }

# The mp4 muxer silently ignores 2-letter language codes, so map ISO 639-1 -> 639-2.
iso3() {
  case "${1%%-*}" in
    ???) printf '%s' "${1%%-*}" ;;
    ar) echo ara ;; en) echo eng ;; fr) echo fra ;; de) echo deu ;; es) echo spa ;;
    it) echo ita ;; pt) echo por ;; ru) echo rus ;; tr) echo tur ;; fa) echo fas ;;
    ur) echo urd ;; hi) echo hin ;; bn) echo ben ;; id) echo ind ;; ms) echo msa ;;
    ja) echo jpn ;; ko) echo kor ;; zh) echo zho ;; nl) echo nld ;; pl) echo pol ;;
    sv) echo swe ;; no) echo nor ;; da) echo dan ;; fi) echo fin ;; el) echo ell ;;
    he) echo heb ;; cs) echo ces ;; sk) echo slk ;; hu) echo hun ;; ro) echo ron ;;
    bg) echo bul ;; uk) echo ukr ;; sr) echo srp ;; hr) echo hrv ;; vi) echo vie ;;
    th) echo tha ;; sw) echo swa ;; ta) echo tam ;; te) echo tel ;; ml) echo mal ;;
    mr) echo mar ;; pa) echo pan ;; gu) echo guj ;; ku) echo kur ;; ps) echo pus ;;
    am) echo amh ;; so) echo som ;; ha) echo hau ;; yo) echo yor ;; az) echo aze ;;
    kk) echo kaz ;; uz) echo uzb ;; ca) echo cat ;; et) echo est ;; lv) echo lav ;;
    lt) echo lit ;; sl) echo slv ;; is) echo isl ;; ga) echo gle ;; cy) echo cym ;;
    sq) echo sqi ;; mk) echo mkd ;; hy) echo hye ;; ka) echo kat ;; ne) echo nep ;;
    si) echo sin ;; my) echo mya ;; km) echo khm ;; lo) echo lao ;; mn) echo mon ;;
    tl) echo tgl ;; af) echo afr ;; be) echo bel ;; bs) echo bos ;; la) echo lat ;;
    *) echo und ;;
  esac
}

while getopts ":m:o:P:k:f:b:l:c:paVASEDUh" opt; do
  case "$opt" in
    m) TEXT="$OPTARG"; HAVE_TEXT=1 ;;
    o) OUTROOT="$OPTARG" ;;
    P) PRIORITY="$OPTARG" ;;
    k) CATEGORY="$OPTARG" ;;
    f) AUDIO_FMT="$OPTARG" ;;
    b) AUDIO_BR="$OPTARG" ;;
    l) FORCE_LANG="$OPTARG" ;;
    c) COOKIES_BROWSER="$OPTARG" ;;
    p) PLAYLIST=1 ;;
    a) AUDIO_ONLY=1 ;;
    V) AUDIO_BR=64k; AUDIO_CH=1 ;;
    A) WANT_AUDIO=0 ;;
    S) WANT_SUBS=0 ;;
    E) EMBED_SUBS=1 ;;
    D) DO_DEDUP=0 ;;
    U) DO_UPDATE=0 ;;
    h) usage ;;
    :) die "option -$OPTARG needs an argument" ;;
    \?) die "unknown option -$OPTARG (try -h)" ;;
  esac
done
shift $((OPTIND - 1))
[ "$AUDIO_ONLY" = 1 ] && [ "$WANT_AUDIO" = 0 ] && die "-a and -A together leave nothing to keep"

# ------------------------------------------------------- links and note text
# Asked before any download, so the run can then be left alone.
URLS=()
for arg in "$@"; do
  read -r -d '' -a words <<<"$arg" || true      # split pasted blocks of links
  URLS+=("${words[@]}")
done
if [ ${#URLS[@]} -eq 0 ]; then
  [ -t 0 ] || usage
  echo "Media links - one or more per line, an empty line ends:"
  while IFS= read -r -p '  link: ' line && [ -n "${line//[[:space:]]/}" ]; do
    read -r -a words <<<"$line"
    URLS+=("${words[@]}")
  done
  [ ${#URLS[@]} -ge 1 ] || die "no links given"
fi
if [ "$HAVE_TEXT" = 0 ] && [ -t 0 ]; then
  read -r -e -p "Note (one line, empty = the media title): " TEXT || TEXT=""
fi
for i in "${!URLS[@]}"; do                       # a bare YouTube id -> its URL
  [[ "${URLS[$i]}" =~ ^[A-Za-z0-9_-]{11}$ ]] && URLS[$i]="https://www.youtube.com/watch?v=${URLS[$i]}"
done
TEXT="$(printf '%s' "$TEXT" | tr '\t\r\n' '   ' | sed 's/^ *//; s/ *$//')"

command -v yt-dlp  >/dev/null || die "yt-dlp not found on PATH"
command -v ffmpeg  >/dev/null || die "ffmpeg not found on PATH"
command -v python3 >/dev/null || die "python3 not found on PATH"

# ---------------------------------------------------------------- keep current
if [ "$DO_UPDATE" = 1 ]; then
  info "checking for a newer yt-dlp ..."
  if ! yt-dlp -U 2>&1 | sed 's/^/    /'; then
    warn "self-update failed (pip/distro install?) - continuing with $(yt-dlp --version)"
  fi
fi
info "yt-dlp $(yt-dlp --version)"

# --------------------------------------------------------------- shared flags
COMMON=(--ignore-config --no-warnings --retries 10 --fragment-retries 10
        --retry-sleep 'fragment:exp=1:20' --concurrent-fragments 4)
[ -n "$COOKIES_BROWSER" ] && COMMON+=(--cookies-from-browser "$COOKIES_BROWSER")
# A playlist carries on past private/removed entries instead of failing whole.
[ "$PLAYLIST" = 1 ] && COMMON+=(--yes-playlist --ignore-errors) || COMMON+=(--no-playlist)

mkdir -p "$OUTROOT" || die "cannot create $OUTROOT"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

# --------------------------------------------------------- metadata inspector
# Reads newline-delimited JSON on stdin, emits one row per entry, fields separated by
# the ASCII unit separator (\x1f) - a TAB would let `read` merge empty fields:
#   id <TAB> webpage_url <TAB> audio_lang <TAB> sub_mode <TAB> sub_key <TAB> about <TAB> title
# about is the note's description line, e.g. "Video, 19:00 (Youtube, 720p)".
# sub_mode is "native" (uploader-provided), "auto" (ASR/google-generated) or "none".
read -r -d '' INSPECT <<'PY'
import json, sys

def audio_lang(info):
    lang = (info.get("language") or "").strip()
    if not lang:
        fmts = info.get("formats") or []
        for f in fmts:                                   # prefer audio-only tracks
            if f.get("vcodec") in (None, "none") and f.get("acodec") not in (None, "none") and f.get("language"):
                lang = f["language"]; break
    if not lang:
        for f in info.get("formats") or []:
            if f.get("language"):
                lang = f["language"]; break
    return lang.strip()

def pick(d, base, prefer_orig=False):
    if not d or not base:
        return None
    keys = list(d)
    low  = {k.lower(): k for k in keys}
    if prefer_orig:                                      # YouTube marks the real ASR
        for k in keys:                                   # language as "<lang>-orig"
            if k.lower() in (base + "-orig", base + "-org"):
                return k
    if base in low:
        return low[base]
    for k in keys:                                       # e.g. "pt" -> "pt-BR"
        kl = k.lower()
        if kl.split("-")[0] == base and not kl.endswith("-orig"):
            return k
    return None

def about(info):
    kind = "Video" if info.get("vcodec") not in (None, "none") or info.get("height") else "Audio"
    extra = ", ".join(x for x in (info.get("extractor_key") or "",
                                  f'{info["height"]}p' if info.get("height") else "") if x)
    secs = int(info.get("duration") or 0)          # duration_string says "19" for 0:19
    h, m, sec = secs // 3600, secs // 60 % 60, secs % 60
    dur = (f"{h}:{m:02d}:{sec:02d}" if h else f"{m}:{sec:02d}") if secs else ""
    return f"{kind}{', ' + dur if dur else ''}{f' ({extra})' if extra else ''}".replace("\t", " ")

for line in sys.stdin:
    line = line.strip()
    if not line:
        continue
    info = json.loads(line)
    lang = audio_lang(info)
    base = lang.split("-")[0].lower() if lang else ""
    subs, autos = info.get("subtitles") or {}, info.get("automatic_captions") or {}
    subs = {k: v for k, v in subs.items() if not k.startswith("live_chat")}

    key = pick(subs, base)
    mode = "native" if key else ""
    if not key:
        key = pick(autos, base, prefer_orig=True)
        mode = "auto" if key else ""
    if not key and not base:                             # language unknown: last resort
        key = pick(subs, "en") or pick(autos, "en", True) or (next(iter(subs), None))
        mode = "native" if key in subs else ("auto" if key else "")
    if not key:
        mode = "none"

    print("\x1f".join([
        info.get("id") or "",
        info.get("webpage_url") or info.get("original_url") or "",
        lang or "?",
        mode,
        key or "",
        about(info),
        (info.get("title") or "").replace("\t", " "),
    ]))
PY

# --------------------------------------------------------- subtitle cleaner
read -r -d '' DEDUP_PY <<'PYDEDUP'
# Collapse YouTube's rolling auto-caption cues into one cue per spoken line.
#
# YouTube ASR captions scroll: a line is emitted as the new bottom line, again in
# a ~10ms bridging cue, then again scrolled up as the top line of the next cue.
# Each line therefore appears ~3x. We keep only its first appearance, and end it
# when the next new line starts (never later than the line's own last appearance,
# so silences don't leave text stranded on screen).
import re, sys

CUE = re.compile(
    r'(?m)^[^\S\n]*(\d+)[^\S\n]*\r?\n'
    r'(\d{1,2}:\d{2}:\d{2}[,.]\d{1,3})[^\S\n]*-->[^\S\n]*(\d{1,2}:\d{2}:\d{2}[,.]\d{1,3})[^\n]*\r?\n')

def secs(t):
    h, m, rest = t.split(":")
    s, ms = re.split(r'[,.]', rest)
    return int(h) * 3600 + int(m) * 60 + int(s) + int(ms.ljust(3, "0")) / 1000

def stamp(v):
    v = max(v, 0.0)
    ms = int(round(v * 1000))
    h, ms = divmod(ms, 3600000)
    m, ms = divmod(ms, 60000)
    s, ms = divmod(ms, 1000)
    return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"

def clean(line):
    line = re.sub(r'<[^>]+>', '', line)          # <c>, <b>, karaoke timing tags
    line = line.replace('​', '').replace('&nbsp;', ' ')
    return re.sub(r'\s+', ' ', line).strip()

def parse(text):
    text = text.lstrip('﻿')
    marks = list(CUE.finditer(text))
    cues = []
    for i, m in enumerate(marks):
        body = text[m.end(): marks[i + 1].start() if i + 1 < len(marks) else len(text)]
        lines = [c for c in (clean(l) for l in body.splitlines()) if c]
        cues.append((secs(m.group(2)), secs(m.group(3)), lines))
    return cues

def is_rolling(cues):
    """Detect YouTube's scrolling ASR style; uploader-authored subs are left alone."""
    if len(cues) < 10:
        return False
    short = sum(1 for s, e, _ in cues if e - s <= 0.05)
    repeat = sum(1 for i in range(1, len(cues))
                 if cues[i][2] and cues[i - 1][2] and cues[i][2][0] == cues[i - 1][2][-1])
    return short / len(cues) > 0.15 or repeat / len(cues) > 0.30

def dedup(cues, window=2):
    out = []                                  # [start, last_seen_end, text]
    recent = []                               # texts still visible on screen
    for start, end, lines in cues:
        for line in lines:
            if line in recent:                # a scrolled-up copy: just note it is
                for c in reversed(out):       # still on screen, do not re-emit
                    if c[2] == line:
                        c[1] = max(c[1], end)
                        break
                continue
            out.append([start, end, line])
            recent.append(line)
            del recent[:-window]
    for i, c in enumerate(out[:-1]):          # end at the next line's start, but
        nxt = out[i + 1][0]                   # never past this line's own last sighting
        c[1] = max(min(c[1], nxt) if nxt > c[0] else c[1], c[0] + 0.05)
    return out

def render(cues):
    return "".join(f"{i}\n{stamp(s)} --> {stamp(e)}\n{t}\n\n"
                   for i, (s, e, t) in enumerate(cues, 1))

def main(path, dest=None, force=False):
    raw = open(path, encoding="utf-8-sig", errors="replace").read()
    cues = parse(raw)
    if not cues:
        print(f"dedup: no cues parsed in {path}, left untouched", file=sys.stderr)
        return 1
    if not force and not is_rolling(cues):
        print(f"dedup: {path} is not rolling auto-caption style, left untouched")
        return 0
    out = dedup(cues)
    open(dest or path, "w", encoding="utf-8").write(render(out))
    print(f"dedup: {len(cues)} cues -> {len(out)}")
    return 0

if __name__ == "__main__":
    a = [x for x in sys.argv[1:] if x != "-f"]
    sys.exit(main(a[0], a[1] if len(a) > 1 else None, "-f" in sys.argv))
PYDEDUP

# ----------------------------------------------------------------------- main
# One run is one note: every link's media lands in the same companion folder,
# and the note lists them all under the line typed at the prompt (or -m TEXT).
NOTE_ARGS=(new --dir "$OUTROOT" --type R)
[ -n "$PRIORITY" ] && NOTE_ARGS+=(--priority "$PRIORITY")
[ -n "$CATEGORY" ] && NOTE_ARGS+=(--category "$CATEGORY")
NOTE="$(python3 "$KNOTES_OUT" "${NOTE_ARGS[@]}")" || die "cannot create a note in $OUTROOT"
DEST="${NOTE%.md}.d"
mkdir -p "$DEST"
BODY="$WORK/body.md"; : > "$BODY"
FIRST_TITLE=""
ITEMS=0                                          # entries written to the note
SAVED=0                                          # of which downloaded

failed() {                                       # keep the link even if it failed
  printf '%s\n_download failed_\n\n' "$1" >> "$BODY"; ITEMS=$((ITEMS + 1)); rc=1
}

rc=0
for URL in "${URLS[@]}"; do
  info "inspecting $URL"
  if ! yt-dlp "${COMMON[@]}" --dump-json "$URL" > "$WORK/meta.ndjson" 2>"$WORK/meta.err"; then
    if [ ! -s "$WORK/meta.ndjson" ]; then
      warn "could not read metadata for $URL"; sed 's/^/    /' "$WORK/meta.err" >&2; failed "$URL"; continue
    fi
    warn "some entries of $URL are unavailable - skipped:"; grep ERROR "$WORK/meta.err" | sed 's/^/    /' >&2; rc=1
  fi
  python3 -c "$INSPECT" < "$WORK/meta.ndjson" > "$WORK/rows.tsv" || { warn "metadata parse failed"; failed "$URL"; continue; }

  # Rows come in on fd 3: yt-dlp and ffmpeg inside the loop read stdin, and
  # would otherwise swallow the rows still waiting (a playlist lost 28 of 42).
  while IFS=$'\x1f' read -r VID VURL ALANG SMODE SKEY ABOUT TITLE <&3; do
    [ -n "$VID" ] || continue
    [ -n "$VURL" ] || VURL="$URL"

    case "$SMODE" in
      native) subnote="original subtitles [$SKEY]" ;;
      auto)   subnote="auto-generated subtitles [$SKEY]" ;;
      *)      subnote="no subtitles available" ;;
    esac
    info "$TITLE"
    printf '    audio language: %s | %s\n' "$ALANG" "$subnote"

    # ---- 1. full media (or, with -a, just the audio stream) ----------------
    DL=("${COMMON[@]}" --no-playlist
        --embed-metadata --write-thumbnail --convert-thumbnails jpg
        -o "$DEST/%(title).120B [%(id)s].%(ext)s"
        --print-to-file after_move:filepath "$WORK/path.txt")
    if [ "$AUDIO_ONLY" = 1 ]; then   # the source is converted below, then removed;
      DL+=(-f 'ba/b')                # webm audio cannot take an embedded thumbnail
      ABOUT="$(printf '%s' "$ABOUT" | sed -E 's/^Video/Audio/; s/, [0-9]+p\)/)/')"
    else
      DL+=(-f 'bv*+ba/b' --merge-output-format mp4 --embed-thumbnail)
    fi
    if [ "$WANT_SUBS" = 1 ] && [ -n "$SKEY" ]; then
      [ "$SMODE" = native ] && DL+=(--write-subs) || DL+=(--write-auto-subs)
      DL+=(--sub-langs "$SKEY" --convert-subs srt)
    elif [ "$WANT_SUBS" = 1 ] && [ -n "$FORCE_LANG" ]; then
      DL+=(--write-subs --write-auto-subs --sub-langs "$FORCE_LANG" --convert-subs srt)
    fi

    : > "$WORK/path.txt"
    [ "$AUDIO_ONLY" = 1 ] && info "downloading audio ..." || info "downloading full media ..."
    if ! yt-dlp "${DL[@]}" "$VURL" </dev/null; then
      warn "download failed: $TITLE"; failed "$VURL"; continue
    fi
    VIDEO="$(tail -n1 "$WORK/path.txt")"
    [ -f "$VIDEO" ] || VIDEO="$(find "$DEST" -maxdepth 1 -type f -name "*$VID*" \
        \( -name '*.mp4' -o -name '*.mkv' -o -name '*.webm' -o -name '*.m4a' -o -name '*.opus' \) -print -quit)"
    # The folder is shared by every link, so this entry's subtitles and cover
    # are found by the video's own name, not by "the first .srt/.jpg there".
    STEM="${VIDEO%.*}"

    # ---- 2. subtitles: collapse rolling auto-caption duplicates ------------
    SRT=""
    if [ "$WANT_SUBS" = 1 ] && [ -n "$VIDEO" ]; then
      for f in "$STEM".*.srt; do [ -f "$f" ] && { SRT="$f"; break; }; done
      if [ -n "$SRT" ] && [ "$DO_DEDUP" = 1 ]; then
        python3 -c "$DEDUP_PY" "$SRT" | sed 's/^/    /' \
          || warn "subtitle dedup skipped (subtitles left as downloaded)"
      fi
    fi

    # ---- 3. optionally embed the cleaned subtitles into the container ------
    if [ "$EMBED_SUBS" = 1 ] && [ "$AUDIO_ONLY" = 0 ] && [ -n "$SRT" ] && [ -f "$VIDEO" ]; then
      info "embedding subtitles ..."
      TMPV="$DEST/.embed-$$.mp4"
      LANGTAG="$ALANG"                            # fall back to the subtitle key
      { [ -z "$LANGTAG" ] || [ "$LANGTAG" = "?" ]; } && LANGTAG="$SKEY"
      TAG=(-metadata:s:s:0 language="$(iso3 "$LANGTAG")")
      if ffmpeg -nostdin -y -loglevel error -i "$VIDEO" -i "$SRT" -map 0 -map 1 \
           -c copy -c:s mov_text "${TAG[@]+"${TAG[@]}"}" "$TMPV" 2>/dev/null \
         || ffmpeg -nostdin -y -loglevel error -i "$VIDEO" -i "$SRT" -map 0:v:0 -map 0:a -map 1 \
           -c copy -c:s mov_text "${TAG[@]+"${TAG[@]}"}" "$TMPV"; then
        mv -f "$TMPV" "$VIDEO"
      else
        rm -f "$TMPV"; warn "could not embed subtitles"
      fi
    fi

    # ---- 4. audio-only, 128 kbps ------------------------------------------
    if [ "$WANT_AUDIO" = 1 ] && [ -f "$VIDEO" ]; then
      AUDIO="$STEM.${AUDIO_BR}.$AUDIO_FMT"
      case "$AUDIO_FMT" in
        mp3)  acodec=(-c:a libmp3lame -b:a "$AUDIO_BR" -ar 44100 -ac "$AUDIO_CH") ;;
        m4a)  acodec=(-c:a aac        -b:a "$AUDIO_BR" -ar 44100 -ac "$AUDIO_CH") ;;
        opus) acodec=(-c:a libopus    -b:a "$AUDIO_BR") ;;
        flac) acodec=(-c:a flac) ;;
        *)    acodec=(-b:a "$AUDIO_BR") ;;
      esac
      COVER=""
      for f in "$STEM.jpg" "$STEM.png"; do [ -f "$f" ] && { COVER="$f"; break; }; done
      info "extracting ${AUDIO_BR} audio -> ${AUDIO##*/}"
      AUDIO_OK=0
      if [ -n "$COVER" ] && [ "$AUDIO_FMT" = mp3 ] \
         && ffmpeg -nostdin -y -loglevel error -i "$VIDEO" -i "$COVER" \
               -map 0:a:0 -map 1:v:0 "${acodec[@]}" -c:v mjpeg -disposition:v:0 attached_pic \
               -map_metadata 0 -id3v2_version 3 "$AUDIO" 2>/dev/null; then
        AUDIO_OK=1
      elif ffmpeg -nostdin -y -loglevel error -i "$VIDEO" -vn "${acodec[@]}" -map_metadata 0 "$AUDIO"; then
        AUDIO_OK=1
      else
        warn "audio extraction failed"; rc=1
      fi
      # -a: the downloaded stream was only the source - unless the conversion
      # failed, in which case it is the only copy of the audio and stays.
      if [ "$AUDIO_ONLY" = 1 ] && [ "$AUDIO_OK" = 1 ] && [ -s "$AUDIO" ] && [ "$AUDIO" != "$VIDEO" ]; then
        rm -f -- "$VIDEO"
      fi
    fi

    # ---- 5. this entry in the note: title, what it is, where it came from ---
    { printf '%s\n' "${TITLE:-$VID}"
      printf '%s\n' "$ABOUT"
      printf '%s\n' "$VURL"
      printf '_audio language: %s · %s_\n\n' "$ALANG" "$subnote"
    } >> "$BODY"
    [ -n "$FIRST_TITLE" ] || FIRST_TITLE="${TITLE:-$VID}"
    ITEMS=$((ITEMS + 1)); SAVED=$((SAVED + 1))
  done 3< "$WORK/rows.tsv"
done

# ------------------------------------------------------------------- the note
if [ "$SAVED" = 0 ]; then                        # nothing downloaded: drop the
  rm -rf "$DEST"; rm -f "$NOTE"                  # reserved, empty note
  [ -n "$TEXT" ] && warn "note not written - its text was: $TEXT"
  exit 1
fi
{ if [ -n "$TEXT" ]; then
    printf '%s\n\n' "$TEXT"; cat "$BODY"
  elif [ "$ITEMS" = 1 ]; then                    # a lone entry's title is the title
    head -n1 "$BODY"; printf '\n'; tail -n +2 "$BODY"
  else
    printf '%s\n\n' "$FIRST_TITLE"; cat "$BODY"
  fi
} | sed -e :a -e '/^\n*$/{$d;N;ba' -e '}' > "$NOTE"   # no trailing blank lines

printf '\033[32mdone:\033[0m %s  (%d of %d saved)\n' "$NOTE" "$SAVED" "$ITEMS"
find "$DEST" -maxdepth 1 -type f -printf '      %f (%s bytes)\n' | sort

exit $rc
