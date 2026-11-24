package com.knotes.alarm

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.knotes.R
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

/**
 * The alarm as a window: the time, the alarm's label and one big Stop, over the lock screen and
 * over whatever app is open — instead of only a notification. [AlarmSoundService] opens it when a
 * sound starts (directly when Android allows that, through the notification's full-screen intent
 * otherwise) and it closes itself when the sound has finished or been stopped anywhere else.
 */
class AlarmActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        setContent {
            val label by AlarmSoundService.ringing.collectAsState()
            // The sound ended (played its repeats, or Stop in the notification): nothing to show.
            LaunchedEffect(label) { if (label == null) finish() }
            AlarmWindow(label.orEmpty()) {
                AlarmSoundService.stop(this)
                finish()
            }
        }
    }

    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            )
        }
        // Stopping the alarm doesn't need the PIN: this window shows over the lock screen and
        // the phone stays locked behind it.
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

private val ARABIC = Regex("[\u0600-\u06FF\u0750-\u077F\uFB50-\uFDFF\uFE70-\uFEFF]")

@androidx.compose.runtime.Composable
private fun AlarmWindow(label: String, onStop: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) { now = System.currentTimeMillis(); delay(1000) }
    }
    val timeFmt = remember { DateFormat.getTimeInstance(DateFormat.SHORT) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0B1A2E))
            .systemBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Alarm, null, tint = Color(0xFF8AB4F8), modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(16.dp))
            Text(timeFmt.format(Date(now)), color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light)
            Spacer(Modifier.height(16.dp))
            Text(
                label.ifBlank { stringResource(R.string.prayer_alarm_default_title) },
                color = Color.White,
                style = MaterialTheme.typography.headlineSmall.copy(textDirection = TextDirection.Content),
                // Arabic reads right-aligned, as everywhere in the app; anything else is centred.
                textAlign = if (ARABIC.containsMatchIn(label)) TextAlign.Right else TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Button(
            onClick = onStop,
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD93025), contentColor = Color.White),
            modifier = Modifier.size(160.dp),
        ) {
            Text(stringResource(R.string.prayer_alarm_stop), fontSize = 24.sp, fontWeight = FontWeight.Bold)
        }
    }
}
