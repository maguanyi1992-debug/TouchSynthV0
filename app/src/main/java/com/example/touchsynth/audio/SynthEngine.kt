package com.example.touchsynth.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.sin

class SynthEngine {
    enum class Waveform { SINE, SAW, SQUARE }

    private val sampleRate = 48_000
    private val minBuffer = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_FLOAT
    )
    private val bufferFrames = maxOf(512, minBuffer / Float.SIZE_BYTES)

    @Volatile private var running = false
    @Volatile private var gate = false
    @Volatile private var frequency = 440.0
    @Volatile private var masterVolume = 0.25f
    @Volatile private var attackSeconds = 0.02f
    @Volatile private var releaseSeconds = 0.25f
    @Volatile private var waveform = Waveform.SAW

    private var audioThread: Thread? = null
    private var audioTrack: AudioTrack? = null

    fun start() {
        if (running) return

        val format = AudioFormat.Builder()
            .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
            .setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        audioTrack = AudioTrack.Builder()
            .setAudioAttributes(attributes)
            .setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferFrames * Float.SIZE_BYTES * 4)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        running = true
        audioTrack?.play()
        audioThread = thread(name = "TouchSynthAudio", priority = Thread.MAX_PRIORITY) {
            renderLoop()
        }
    }

    fun stop() {
        running = false
        audioThread?.join(500)
        audioThread = null
        audioTrack?.runCatching { stop() }
        audioTrack?.release()
        audioTrack = null
    }

    fun noteOn(midiNote: Int) {
        frequency = midiToFrequency(midiNote)
        gate = true
    }

    fun noteOff() {
        gate = false
    }

    fun setWaveform(value: Waveform) { waveform = value }
    fun setVolume(value: Float) { masterVolume = value.coerceIn(0f, 0.8f) }
    fun setAttack(value: Float) { attackSeconds = value.coerceIn(0.005f, 2f) }
    fun setRelease(value: Float) { releaseSeconds = value.coerceIn(0.01f, 4f) }

    private fun renderLoop() {
        val buffer = FloatArray(bufferFrames)
        var phase = 0.0
        var envelope = 0.0f

        while (running) {
            val localFrequency = frequency
            val phaseStep = localFrequency / sampleRate
            val target = if (gate) 1f else 0f
            val seconds = if (gate) attackSeconds else releaseSeconds
            val smoothing = (1f / (seconds * sampleRate)).coerceAtMost(1f)

            for (i in buffer.indices) {
                envelope += (target - envelope) * smoothing
                phase += phaseStep
                if (phase >= 1.0) phase -= 1.0

                val oscillator = when (waveform) {
                    Waveform.SINE -> sin(phase * 2.0 * PI).toFloat()
                    Waveform.SAW -> (phase * 2.0 - 1.0).toFloat()
                    Waveform.SQUARE -> if (phase < 0.5) 1f else -1f
                }
                buffer[i] = oscillator * envelope * masterVolume
            }

            audioTrack?.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
        }
    }

    private fun midiToFrequency(note: Int): Double =
        440.0 * Math.pow(2.0, (note - 69) / 12.0)
}
