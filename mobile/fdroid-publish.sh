#!/usr/bin/env bash
# Publish a GitHub release's APK to the project's F-Droid repository (GitHub Pages).
#
#   mobile/fdroid-publish.sh release-2026-10-19
#
# Takes the `knotes-*-release.apk` asset of that GitHub release — the very file users can also
# download there, never a fresh build — refreshes the store texts from mobile/fastlane, re-signs the
# repository index and pushes it to https://mhm-fakih.github.io/nibras-fdroid/repo.
#
# The repository's signing key and config live in $FDROID_DIR (default ~/dev/nibras-fdroid), which
# is deliberately NOT a git repository: only its pages/ checkout is pushed. Back that key up — lose
# it and every user has to remove and re-add the repository.
set -euo pipefail

TAG=${1:?usage: fdroid-publish.sh <github-release-tag>}
FDROID_DIR=${FDROID_DIR:-$HOME/dev/nibras-fdroid}
GH_REPO=${GH_REPO:-mhm-fakih/nibras-pim}
HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
export ANDROID_HOME=${ANDROID_HOME:-$HOME/Android/Sdk}

die() { echo "fdroid-publish.sh: $*" >&2; exit 1; }
[ -f "$FDROID_DIR/config.yml" ] && [ -f "$FDROID_DIR/keystore.p12" ] \
    || die "no repository key/config in $FDROID_DIR"
[ -x "$FDROID_DIR/.venv/bin/fdroid" ] \
    || die "fdroidserver missing: python3 -m venv $FDROID_DIR/.venv && $FDROID_DIR/.venv/bin/pip install fdroidserver"
[ -d "$FDROID_DIR/pages/.git" ] || die "no pages/ checkout in $FDROID_DIR"

cd "$FDROID_DIR"
echo "==> Fetching the release APK of $TAG…"
gh release download "$TAG" -R "$GH_REPO" -p 'knotes-*-release.apk' -D repo --clobber
# The store texts travel with the code: refresh them from the tagged-or-current fastlane folder.
rm -rf metadata/com.knotes && mkdir -p metadata/com.knotes
cp -r "$HERE/fastlane/metadata/android/"* metadata/com.knotes/

echo "==> Signing the repository index…"
.venv/bin/fdroid update

echo "==> Publishing…"
rsync -a --delete --exclude=.git repo/ pages/repo/
cd pages
git add -A
if git diff --cached --quiet; then
    echo "Nothing changed."
else
    git -c user.name="Mohamad F. Fakih" -c user.email="mhmfakih@proton.me" \
        commit -q -m "Publish $TAG"
    git push -q origin main
    echo "Pushed: $(git log --oneline -1)  →  https://mhm-fakih.github.io/nibras-fdroid/repo"
fi
