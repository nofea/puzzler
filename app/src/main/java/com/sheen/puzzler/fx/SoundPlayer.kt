package com.sheen.puzzler.fx

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

enum class Sfx { SWAP, INVALID, SPECIAL, SHUFFLE, WIN, FAIL }

/** Plays short tones synthesized at startup, so the app ships no audio files. */
class SoundPlayer {
    private val sampleRate = 44_100
    private val tracks = HashMap<String, AudioTrack>()
    var muted = false

    /** A note: frequency in Hz and duration in ms. Notes in a sound play one after another. */
    private data class Note(val freq: Double, val ms: Int)

    private fun synth(notes: List<Note>, volume: Double = 0.35): ShortArray {
        val out = ArrayList<Short>()
        for (note in notes) {
            val n = sampleRate * note.ms / 1000
            for (i in 0 until n) {
                val t = i.toDouble() / sampleRate
                val attack = (i / (sampleRate * 0.005)).coerceAtMost(1.0)
                val decay = exp(-4.0 * i / n)
                // Fundamental plus a soft octave for a bell-ish tone.
                val s = sin(2 * PI * note.freq * t) + 0.3 * sin(4 * PI * note.freq * t)
                out += (s * attack * decay * volume * Short.MAX_VALUE / 1.3).toInt().toShort()
            }
        }
        return out.toShortArray()
    }

    private fun build(pcm: ShortArray): AudioTrack {
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
        track.write(pcm, 0, pcm.size)
        return track
    }

    private fun notesFor(sfx: Sfx) = when (sfx) {
        Sfx.SWAP -> listOf(Note(880.0, 40))
        Sfx.INVALID -> listOf(Note(220.0, 70), Note(180.0, 90))
        Sfx.SPECIAL -> listOf(Note(660.0, 60), Note(990.0, 60), Note(1320.0, 120))
        Sfx.SHUFFLE -> listOf(Note(500.0, 50), Note(600.0, 50), Note(500.0, 50), Note(600.0, 50))
        Sfx.WIN -> listOf(Note(523.0, 120), Note(659.0, 120), Note(784.0, 120), Note(1047.0, 300))
        Sfx.FAIL -> listOf(Note(392.0, 180), Note(330.0, 180), Note(262.0, 360))
    }

    private fun play(key: String, notes: () -> List<Note>) {
        if (muted) return
        runCatching {
            val track = tracks.getOrPut(key) { build(synth(notes())) }
            if (track.playState != AudioTrack.PLAYSTATE_STOPPED) track.stop()
            track.reloadStaticData()
            track.play()
        }
    }

    fun play(sfx: Sfx) = play(sfx.name) { notesFor(sfx) }

    /** Match chime; pitch climbs a step with each cascade. */
    fun match(cascade: Int) {
        val step = cascade.coerceIn(1, 8)
        play("match$step") { listOf(Note(523.25 * 2.0.pow((step - 1) * 2 / 12.0), 120)) }
    }

    fun release() {
        tracks.values.forEach { runCatching { it.release() } }
        tracks.clear()
    }
}
