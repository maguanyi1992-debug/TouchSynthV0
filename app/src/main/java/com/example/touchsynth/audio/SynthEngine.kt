package com.example.touchsynth.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class SynthEngine {
    enum class Waveform { SINE, SAW, SQUARE }

    private enum class EnvelopeStage { IDLE, ATTACK, DECAY, SUSTAIN, RELEASE }

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
    @Volatile private var decaySeconds = 0.30f
    @Volatile private var sustainLevel = 0.70f
    @Volatile private var releaseSeconds = 0.25f
    @Volatile private var cutoffHz = 5_000f
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
    fun setAttack(value: Float) { attackSeconds = value.coerceIn(0.005f, 3f) }
    fun setDecay(value: Float) { decaySeconds = value.coerceIn(0.005f, 3f) }
    fun setSustain(value: Float) { sustainLevel = value.coerceIn(0f, 1f) }
    fun setRelease(value: Float) { releaseSeconds = value.coerceIn(0.01f, 5f) }
    fun setCutoff(value: Float) { cutoffHz = value.coerceIn(80f, 12_000f) }

    private fun renderLoop() {
        val buffer = FloatArray(bufferFrames)
        var phase = 0.0
        var envelope = 0f
        var stage = EnvelopeStage.IDLE
        var previousGate = false
        var filteredSample = 0f

        while (running) {
            val phaseStep = frequency / sampleRate
            val localAttack = attackSeconds
            val localDecay = decaySeconds
            val localSustain = sustainLevel
            val localRelease = releaseSeconds
            val filterCoefficient = (
                1.0 - exp(-2.0 * PI * cutoffHz / sampleRate)
            ).toFloat().coerceIn(0.001f, 1f)

            val currentGate = gate
            if (currentGate && !previousGate) stage = EnvelopeStage.ATTACK
            if (!currentGate && previousGate) stage = EnvelopeStage.RELEASE
            previousGate = currentGate

            for (i in buffer.indices) {
                envelope = when (stage) {
                    EnvelopeStage.IDLE -> 0f
                    EnvelopeStage.ATTACK -> {
                        val next = envelope + 1f / (localAttack * sampleRate)
                        if (next >= 1f) {
                            stage = EnvelopeStage.DECAY
                            1f
                        } else next
                    }
                    EnvelopeStage.DECAY -> {
                        val next = envelope - (1f - localSustain) / (localDecay * sampleRate)
                        if (next <= localSustain) {
                            stage = EnvelopeStage.SUSTAIN
                            localSustain
                        } else next
                    }
                    EnvelopeStage.SUSTAIN -> {
                        if (!gate) stage = EnvelopeStage.RELEASE
                        localSustain
                    }
                    EnvelopeStage.RELEASE -> {
                        val next = envelope - 1f / (localRelease * sampleRate)
                        if (next <= 0f) {
                            stage = EnvelopeStage.IDLE
                            0f
                        } else next
                    }
                }

                phase += phaseStep
                if (phase >= 1.0) phase -= 1.0

                val oscillator = when (waveform) {
                    Waveform.SINE -> sin(phase * 2.0 * PI).toFloat()
                    Waveform.SAW -> (phase * 2.0 - 1.0).toFloat()
                    Waveform.SQUARE -> if (phase < 0.5) 1f else -1f
                }

                val rawSample = oscillator * envelope * masterVolume
                filteredSample += filterCoefficient * (rawSample - filteredSample)
                buffer[i] = filteredSample
            }

            audioTrack?.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
        }
    }

    private fun midiToFrequency(note: Int): Double =
        440.0 * Math.pow(2.0, (note - 69) / 12.0)
}
