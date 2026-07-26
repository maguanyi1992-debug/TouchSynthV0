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

    data class Preset(
        val name: String,
        val waveform: Waveform,
        val volume: Float,
        val cutoff: Float,
        val attack: Float,
        val decay: Float,
        val sustain: Float,
        val release: Float
    )

    private enum class EnvelopeStage { ATTACK, DECAY, SUSTAIN, RELEASE }

    private data class Voice(
        val note: Int,
        val frequency: Double,
        var phase: Double = 0.0,
        var envelope: Float = 0f,
        var stage: EnvelopeStage = EnvelopeStage.ATTACK,
        var released: Boolean = false
    )

    companion object {
        val presets = listOf(
            Preset("Soft Pad", Waveform.SAW, 0.22f, 2400f, 0.70f, 0.80f, 0.72f, 2.80f),
            Preset("Bright Lead", Waveform.SAW, 0.24f, 9000f, 0.01f, 0.18f, 0.82f, 0.35f),
            Preset("Deep Bass", Waveform.SQUARE, 0.26f, 900f, 0.01f, 0.20f, 0.62f, 0.30f),
            Preset("Short Pluck", Waveform.SAW, 0.25f, 5200f, 0.005f, 0.22f, 0.02f, 0.18f),
            Preset("Retro Square", Waveform.SQUARE, 0.20f, 6500f, 0.01f, 0.12f, 0.88f, 0.20f),
            Preset("Dark Drone", Waveform.SINE, 0.24f, 700f, 1.20f, 1.50f, 0.82f, 4.00f)
        )
    }

    private val sampleRate = 48_000
    private val minBuffer = AudioTrack.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_OUT_MONO,
        AudioFormat.ENCODING_PCM_FLOAT
    )
    private val bufferFrames = maxOf(512, minBuffer / Float.SIZE_BYTES)
    private val voices = mutableListOf<Voice>()
    private val voiceLock = Any()

    @Volatile private var running = false
    @Volatile private var masterVolume = 0.22f
    @Volatile private var attackSeconds = 0.70f
    @Volatile private var decaySeconds = 0.80f
    @Volatile private var sustainLevel = 0.72f
    @Volatile private var releaseSeconds = 2.80f
    @Volatile private var cutoffHz = 2400f
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
        synchronized(voiceLock) {
            voices.removeAll { it.note == midiNote }
            if (voices.size >= 8) voices.removeAt(0)
            voices.add(Voice(note = midiNote, frequency = midiToFrequency(midiNote)))
        }
    }

    fun noteOff(midiNote: Int) {
        synchronized(voiceLock) {
            voices.firstOrNull { it.note == midiNote && !it.released }?.let {
                it.released = true
                it.stage = EnvelopeStage.RELEASE
            }
        }
    }

    fun allNotesOff() {
        synchronized(voiceLock) {
            voices.forEach {
                it.released = true
                it.stage = EnvelopeStage.RELEASE
            }
        }
    }

    fun applyPreset(preset: Preset) {
        waveform = preset.waveform
        masterVolume = preset.volume
        cutoffHz = preset.cutoff
        attackSeconds = preset.attack
        decaySeconds = preset.decay
        sustainLevel = preset.sustain
        releaseSeconds = preset.release
    }

    fun setWaveform(value: Waveform) { waveform = value }
    fun setVolume(value: Float) { masterVolume = value.coerceIn(0f, 0.6f) }
    fun setAttack(value: Float) { attackSeconds = value.coerceIn(0.005f, 3f) }
    fun setDecay(value: Float) { decaySeconds = value.coerceIn(0.005f, 3f) }
    fun setSustain(value: Float) { sustainLevel = value.coerceIn(0f, 1f) }
    fun setRelease(value: Float) { releaseSeconds = value.coerceIn(0.01f, 5f) }
    fun setCutoff(value: Float) { cutoffHz = value.coerceIn(80f, 12_000f) }

    private fun renderLoop() {
        val buffer = FloatArray(bufferFrames)
        var filteredSample = 0f
        while (running) {
            val localAttack = attackSeconds
            val localDecay = decaySeconds
            val localSustain = sustainLevel
            val localRelease = releaseSeconds
            val localVolume = masterVolume
            val localCutoff = cutoffHz
            val localWaveform = waveform
            val filterCoefficient = (1.0 - exp(-2.0 * PI * localCutoff / sampleRate))
                .toFloat().coerceIn(0.001f, 1f)

            for (i in buffer.indices) {
                var mixed = 0f
                var count = 0
                synchronized(voiceLock) {
                    val iterator = voices.iterator()
                    while (iterator.hasNext()) {
                        val voice = iterator.next()
                        voice.envelope = advanceEnvelope(
                            voice = voice,
                            attack = localAttack,
                            decay = localDecay,
                            sustain = localSustain,
                            release = localRelease
                        )
                        if (voice.stage == EnvelopeStage.RELEASE && voice.envelope <= 0f) {
                            iterator.remove()
                            continue
                        }
                        voice.phase += voice.frequency / sampleRate
                        if (voice.phase >= 1.0) voice.phase -= 1.0
                        val oscillator = when (localWaveform) {
                            Waveform.SINE -> sin(voice.phase * 2.0 * PI).toFloat()
                            Waveform.SAW -> (voice.phase * 2.0 - 1.0).toFloat()
                            Waveform.SQUARE -> if (voice.phase < 0.5) 1f else -1f
                        }
                        mixed += oscillator * voice.envelope
                        count++
                    }
                }
                if (count > 0) mixed /= count.coerceAtLeast(1)
                val raw = mixed * localVolume
                filteredSample += filterCoefficient * (raw - filteredSample)
                buffer[i] = filteredSample
            }
            audioTrack?.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
        }
    }

    private fun advanceEnvelope(
        voice: Voice,
        attack: Float,
        decay: Float,
        sustain: Float,
        release: Float
    ): Float {
        return when (voice.stage) {
            EnvelopeStage.ATTACK -> {
                val next = voice.envelope + 1f / (attack * sampleRate)
                if (next >= 1f) {
                    voice.stage = EnvelopeStage.DECAY
                    1f
                } else next
            }
            EnvelopeStage.DECAY -> {
                val next = voice.envelope - (1f - sustain) / (decay * sampleRate)
                if (next <= sustain) {
                    voice.stage = EnvelopeStage.SUSTAIN
                    sustain
                } else next
            }
            EnvelopeStage.SUSTAIN -> sustain
            EnvelopeStage.RELEASE -> {
                val next = voice.envelope - 1f / (release * sampleRate)
                next.coerceAtLeast(0f)
            }
        }
    }

    private fun midiToFrequency(note: Int): Double =
        440.0 * Math.pow(2.0, (note - 69) / 12.0)
}
