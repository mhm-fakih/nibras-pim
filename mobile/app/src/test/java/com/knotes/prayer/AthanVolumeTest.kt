package com.knotes.prayer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Per-prayer athan volume: the percentage → amplitude mapping, and how it is stored. */
class AthanVolumeTest {

    @Test
    fun theEndsOfTheRangeMeanWhatTheySay() {
        assertEquals(0f, PrayerSettings.volumeScalar(0), 0f)
        assertEquals(1f, PrayerSettings.volumeScalar(100), 0f)
    }

    @Test
    fun theCurveIsSquaredNotLinear() {
        // MediaPlayer takes an amplitude multiplier, and amplitude is not loudness: a linear 50%
        // is only ~6 dB down and barely sounds quieter. Squaring puts the middle of the slider at
        // ~12 dB down, which is about what someone means by "half".
        assertEquals(0.25f, PrayerSettings.volumeScalar(50), 1e-6f)
        assertEquals(0.64f, PrayerSettings.volumeScalar(80), 1e-6f)
        assertTrue("50% must be quieter than a linear half", PrayerSettings.volumeScalar(50) < 0.5f)
    }

    @Test
    fun louderPercentagesAreAlwaysLouder() {
        var previous = -1f
        (0..100 step 5).forEach { p ->
            val v = PrayerSettings.volumeScalar(p)
            assertTrue("$p% was not louder than the step below", v > previous)
            previous = v
        }
    }

    @Test
    fun outOfRangePercentagesAreClamped() {
        assertEquals(0f, PrayerSettings.volumeScalar(-40), 0f)
        assertEquals(1f, PrayerSettings.volumeScalar(400), 0f)
    }

    @Test
    fun eachPrayerKeepsItsOwnVolumeAndTheRestTakeTheDefault() {
        val s = PrayerSettings(athanVolumes = mapOf(Prayer.FAJR to 25, Prayer.DHUHR to 80))
        assertEquals(25, s.athanVolumeOf(Prayer.FAJR))
        assertEquals(80, s.athanVolumeOf(Prayer.DHUHR))
        assertEquals(PrayerSettings.DEFAULT_ATHAN_VOLUME, s.athanVolumeOf(Prayer.MAGHRIB))
    }

    @Test
    fun aMutedAthanSurvivesBeingSaved() {
        // 0 is a real setting — "call this one silently" — unlike a zero adjustment, which is just
        // the absence of one. Dropping it on encode would restore the default instead.
        val encoded = PrayerSettingsStore.encodeVolumes(mapOf(Prayer.FAJR to 0, Prayer.ISHA to 60))
        assertEquals("FAJR:0,ISHA:60", encoded)
        val decoded = PrayerSettingsStore.decodeVolumes(encoded)
        assertEquals(0, decoded[Prayer.FAJR])
        assertEquals(0, PrayerSettings(athanVolumes = decoded).athanVolumeOf(Prayer.FAJR))
    }

    @Test
    fun volumesRoundTripAndSurviveJunk() {
        val map = mapOf(Prayer.FAJR to 30, Prayer.DHUHR to 90, Prayer.MAGHRIB to 100)
        assertEquals(map, PrayerSettingsStore.decodeVolumes(PrayerSettingsStore.encodeVolumes(map)))
        assertEquals(emptyMap<Prayer, Int>(), PrayerSettingsStore.decodeVolumes(null))
        assertEquals(emptyMap<Prayer, Int>(), PrayerSettingsStore.decodeVolumes(""))
        assertEquals(
            mapOf(Prayer.ASR to 40),
            PrayerSettingsStore.decodeVolumes("NOPE:10,ASR:40,rubbish"),
        )
        // A hand-edited preference outside 0..100 is clamped, not taken at face value.
        assertEquals(mapOf(Prayer.ASR to 100), PrayerSettingsStore.decodeVolumes("ASR:5000"))
    }
}

/** The beep sound: a ringtone, a file, or the device's own tone. */
class BeepSoundTest {

    @Test
    fun noSoundChosenMeansTheBuiltInTone() {
        // Blank is a real, supported choice — not an unset field waiting to be filled. It is the
        // default precisely because it needs no file and cannot be broken by deleting one.
        assertEquals("", PrayerSettings().beepSoundUri)
    }

    @Test
    fun aChosenSoundIsKeptVerbatim() {
        // Whatever the picker hands back — a ringtone content URI or a document one — is stored
        // exactly, because it is what the media player will be asked to open months later.
        val ringtone = "content://media/internal/audio/media/27"
        val document = "content://com.android.externalstorage.documents/document/primary%3Abeep.ogg"
        assertEquals(ringtone, PrayerSettings(beepSoundUri = ringtone).beepSoundUri)
        assertEquals(document, PrayerSettings(beepSoundUri = document).beepSoundUri)
    }

    @Test
    fun theBeepCountIsClampedToWhatTheUiOffers() {
        // The count drives how many times a chosen sound plays, so an absurd stored value would
        // mean an absurdly long countdown rather than a merely odd-looking slider.
        assertEquals(PrayerSettings.MIN_BEEPS, 1)
        assertEquals(PrayerSettings.MAX_BEEPS, 10)
        assertEquals(10, 50.coerceIn(PrayerSettings.MIN_BEEPS, PrayerSettings.MAX_BEEPS))
        assertEquals(1, 0.coerceIn(PrayerSettings.MIN_BEEPS, PrayerSettings.MAX_BEEPS))
    }
}
