package com.knotes

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import com.knotes.ui.PLAYER_DEST

/**
 * The home-screen "Player" icon: opens the app straight on the full-screen player, with the track
 * that is loaded or was last played — no need to pick a media file first. A trampoline only: it
 * hands over to [MainActivity] and is gone.
 */
class PlayerLauncherActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(MainActivity.EXTRA_DEST, PLAYER_DEST),
        )
        finish()
    }
}
