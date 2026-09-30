#!/usr/bin/env bash
cd /my-home/dev/knotes
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=/my-home/Android/Sdk
./gradlew installDebug && \
  "$ANDROID_HOME/platform-tools/adb" shell monkey -p com.knotes -c android.intent.category.LAUNCHER 1


# make these ui changes: a line separator between the left navigation items, lighter active/opened record panel background - 3. display the added note to the record, even when   it's still queued for synching with the server, and add an icon (comment) next to the title of the record to signal that it has a comment. also an attachment icon for the   records having filesList. also move the audio/video player settings to the bothom of the screen and make the bookmakrs list before them and sort the bookmakrs in   chronologically descending order (latest at the top). also show the id of the record before its title. last, when the record full text or description is Arabic, make the content not just right justified but direction: rtl, so dots at the end of the sentences are to the right of the sentences, not to the left.

# Resume this session with: claude --resume 5d31175a-191c-48f3-b16b-0800510d6a16 


