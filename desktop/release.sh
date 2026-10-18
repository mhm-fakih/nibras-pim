#!/usr/bin/env bash
# Install a tagged, tested version of the desktop app into the installed ("prod") copy.
#
#   git tag desktop-v1.4.0          # in this repo, on the commit you tested
#   desktop/release.sh desktop-v1.4.0
#
# Only the tag's COMMITTED files are installed, so work in progress never leaks into the installed
# app; installing an older tag is a rollback. The installed copy's own runtime files survive every
# release: config.json (live settings + password), build-number.txt, tag-bank.md, __pycache__ and
# anything else its .gitignore names. Afterwards the release is committed in the installed copy's
# own git repo, so it keeps a record of what was installed when.
set -euo pipefail

TAG=${1:?usage: release.sh <tag>   (e.g. desktop-v1.4.0)}
PROD=${KNOTES_PROD_DIR:-$HOME/sys2/desktop}
REPO=$(git -C "$(dirname "${BASH_SOURCE[0]}")" rev-parse --show-toplevel)

die() { echo "release.sh: $*" >&2; exit 1; }

git -C "$REPO" rev-parse -q --verify "refs/tags/$TAG^{commit}" >/dev/null \
    || die "no tag '$TAG' in $REPO"
[ -d "$PROD" ] || die "installed copy not found: $PROD (set KNOTES_PROD_DIR)"
# Edits made directly in the installed copy would be overwritten — refuse instead of losing them.
if [ -d "$PROD/.git" ] && [ -n "$(git -C "$PROD" status --porcelain)" ]; then
    git -C "$PROD" status --short >&2
    die "$PROD has uncommitted changes; move them into $REPO/desktop first"
fi

STAGE=$(mktemp -d)
trap 'rm -rf "$STAGE"' EXIT
git -C "$REPO" archive "$TAG" desktop | tar -x -C "$STAGE"
# Dev-only files, and the repo's placeholder config, never reach the installed copy.
rm -f "$STAGE/desktop/config.json" "$STAGE/desktop/release.sh" "$STAGE/desktop/run-dev.sh"

# --delete drops files a newer version removed. The filters protect the installed copy's own files:
# its git repo, the release marker, the tag bank, and everything its .gitignore lists.
rsync -a --delete \
    --exclude=/.git --exclude=/RELEASE --exclude=/tag-bank.md \
    --filter=':- .gitignore' \
    "$STAGE/desktop/" "$PROD/"

COMMIT=$(git -C "$REPO" rev-parse --short "$TAG^{commit}")
echo "$TAG ($COMMIT)" > "$PROD/RELEASE"

if [ -d "$PROD/.git" ]; then
    git -C "$PROD" add -A
    if git -C "$PROD" diff --cached --quiet; then
        echo "Installed $TAG — no changes from the current install."
    else
        git -C "$PROD" commit -q -m "Release $TAG (spring-boot-react-v1 $COMMIT)"
        echo "Installed $TAG into $PROD and committed it there:"
        git -C "$PROD" log --oneline -1
    fi
else
    echo "Installed $TAG into $PROD."
fi
echo "Restart the app to run it."
