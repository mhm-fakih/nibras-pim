#!/usr/bin/env bash
set -euo pipefail

# Build the signed release APK. Needs keystore.properties beside this file (kept
# out of version control); without it Gradle falls back to an unsigned build.
#
# Same self-locating cd as build-run.sh — see the note there.
cd "$(dirname "${BASH_SOURCE[0]}")"

# These were previously assigned without `export`, so Gradle never received them.
# It built anyway, because local.properties supplies sdk.dir and the Android
# plugin reads that first — but local.properties is gitignored, so on a fresh
# checkout there is no sdk.dir AND the unexported ANDROID_HOME would not have
# stood in for it. Exported now, and only when nothing else has set them.
export JAVA_HOME="${JAVA_HOME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export ANDROID_HOME="${ANDROID_HOME:-/my-home/Android/Sdk}"

./gradlew assembleRelease

# Then, to install and to open the folder:
#   "$ANDROID_HOME/platform-tools/adb" install -r app/build/outputs/apk/release/app-release.apk
#   nemo app/build/outputs/apk/release

# filesList, nbFiles 
# I want new ui change and one major change: merge read/unread section into one "unbookmarked" section, as clicking on a record makes it read and moves it quickly to the read section thereby   dissapearing from the current view. merging unread/read should solve this problem. 2 - I want    to color the expanding record with a differenct color to easily distinguish it from the rest. 3   - I want a persistant status bar showing: nb. of queued notes, nb. of queued highlights, if    online/offline, syncing data indicator. 4 - I want to show and links the fields nbFiles, and    filesList in the exportJsonX actions data, and to be able to open such files as was the case in   the ionic4 old apsp. 5 - I want to know how difficult is it to introduce an audio player to   play the audio/video files contained in the record's folder in its phone file system as   explained in the previous notes, with strong bookmarking feature, and to save the bookmars to   the same folder with the same name with .txt extension.

# audio-player-design.jpg
# build the audio player with bookmarks, with this sample design screenshot (from Simple ABP          v1.8.3 android audio player), with these features chosen in mind: 1 - skip/forward n nb. of         seconds 2 - next file to player in the folder, 3 - sound volume slider (-20db - +50db), one big     bookmarking button with optional text to accompany it, 4- bookmarking format like the audio         chapter lyrics format (time stamp, optional text per line), all per file, 5- slider with the        ability to go to any position in the audio/video file, bookmarked times are showed on the timer,    dark mode. speed option (0.75-1.25). that's it.  

# given a directory with subdirectory containing files all timestamped with format like this: 2026_06_10-121357 (current directory), I want to build a script to auto read such directory, scan the name of all files, and build a chronologically ordered list of them in markdown format (single file), with links to non-text files (with preview to be read in Obsidian) and embedding links (like latex //include command) to .txt or .md files. I want you to suggest a workflow to continously add the links to new files if the past file was edited (comments on files), so my edits are not overwritten by the execution of the script.


