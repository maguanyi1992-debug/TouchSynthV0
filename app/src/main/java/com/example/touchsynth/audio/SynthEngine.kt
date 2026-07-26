package com.example.touchsynth.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Process
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sin

class SynthEngine {
    enum class Waveform { SINE, TRIANGLE, SAW, SQUARE }

    data class Preset(
        val name: String,
        val osc1: Waveform,
        val osc2: Waveform,
        val oscMix: Float,
        val detuneCents: Float,
        val volume: Float,
        val cutoff: Float,
        val resonance: Float,
        val attack: Float,
        val decay: Float,
        val sustain: Float,
        val release: Float,
        val chorus: Float
    )

    private enum class Stage { ATTACK, DECAY, SUSTAIN, RELEASE }
    private data class Voice(
        val note: Int,
        val frequency: Double,
        var phase1: Double = 0.0,
        var phase2: Double = 0.0,
        var envelope: Float = 0f,
        var stage: Stage = Stage.ATTACK
    )
    private sealed interface Command {
        data class On(val note: Int) : Command
        data class Off(val note: Int) : Command
        data object AllOff : Command
    }

    companion object {
        val presets = listOf(
            Preset("Instant Dream", Waveform.SAW, Waveform.TRIANGLE, .42f, 7f, .24f, 5200f, .16f, .005f, .25f, .72f, .45f, .20f),
            Preset("Soft Cloud", Waveform.SAW, Waveform.SAW, .48f, 11f, .21f, 2600f, .22f, .18f, .75f, .78f, 2.2f, .34f),
            Preset("Glass Lead", Waveform.TRIANGLE, Waveform.SQUARE, .28f, 5f, .22f, 7600f, .12f, .005f, .16f, .82f, .28f, .12f),
            Preset("Deep Bass", Waveform.SQUARE, Waveform.SINE, .35f, -4f, .28f, 1100f, .30f, .005f, .18f, .68f, .22f, .04f),
            Preset("Frozen Pluck", Waveform.SAW, Waveform.TRIANGLE, .40f, 8f, .25f, 4300f, .28f, .005f, .22f, .04f, .24f, .16f),
            Preset("Dark Drone", Waveform.SAW, Waveform.SINE, .52f, 13f, .20f, 850f, .36f, .55f, 1.2f, .88f, 3.5f, .38f)
        )
    }

    private val sampleRate = 48_000
    private val minBufferBytes = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_FLOAT)
    private val bufferFrames = maxOf(128, minBufferBytes / (Float.SIZE_BYTES * 2)).coerceAtMost(512)
    private val commands = ConcurrentLinkedQueue<Command>()
    private val voices = mutableListOf<Voice>()

    @Volatile private var running = false
    @Volatile private var osc1 = Waveform.SAW
    @Volatile private var osc2 = Waveform.TRIANGLE
    @Volatile private var oscMix = .42f
    @Volatile private var detuneCents = 7f
    @Volatile private var volume = .24f
    @Volatile private var cutoff = 5200f
    @Volatile private var resonance = .16f
    @Volatile private var attack = .005f
    @Volatile private var decay = .25f
    @Volatile private var sustain = .72f
    @Volatile private var release = .45f
    @Volatile private var chorusMix = .20f

    private var track: AudioTrack? = null
    private var audioThread: Thread? = null

    fun start() {
        if (running) return
        val format = AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_FLOAT).setSampleRate(sampleRate)
            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build()
        val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
        track = AudioTrack.Builder().setAudioAttributes(attrs).setAudioFormat(format)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(bufferFrames * 2 * Float.SIZE_BYTES)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY).build()
        running = true
        track?.play()
        audioThread = thread(name = "TouchSynthAudio") {
            Process.setThreadPriority(Process.THREAD_PRIORITY_URGENT_AUDIO)
            renderLoop()
        }
    }

    fun stop() {
        running = false
        audioThread?.join(400)
        track?.runCatching { stop() }
        track?.release(); track = null; audioThread = null
    }

    fun noteOn(note: Int) { commands.offer(Command.On(note)) }
    fun noteOff(note: Int) { commands.offer(Command.Off(note)) }
    fun allNotesOff() { commands.offer(Command.AllOff) }
    fun noteOn(notes: List<Int>) = notes.forEach(::noteOn)
    fun noteOff(notes: List<Int>) = notes.forEach(::noteOff)

    fun applyPreset(p: Preset) {
        osc1=p.osc1; osc2=p.osc2; oscMix=p.oscMix; detuneCents=p.detuneCents; volume=p.volume
        cutoff=p.cutoff; resonance=p.resonance; attack=p.attack; decay=p.decay; sustain=p.sustain
        release=p.release; chorusMix=p.chorus
    }
    fun setOscMix(v: Float) { oscMix=v.coerceIn(0f,1f) }
    fun setDetune(v: Float) { detuneCents=v.coerceIn(-30f,30f) }
    fun setCutoff(v: Float) { cutoff=v.coerceIn(80f,12000f) }
    fun setResonance(v: Float) { resonance=v.coerceIn(0f,.85f) }
    fun setAttack(v: Float) { attack=v.coerceIn(.003f,3f) }
    fun setRelease(v: Float) { release=v.coerceIn(.02f,5f) }
    fun setChorus(v: Float) { chorusMix=v.coerceIn(0f,.55f) }
    fun audioInfo() = "48 kHz · ${bufferFrames}帧缓冲"

    private fun applyCommands() {
        while (true) when (val c = commands.poll() ?: break) {
            is Command.On -> {
                voices.removeAll { it.note == c.note }
                if (voices.size >= 24) voices.removeAt(0)
                voices.add(Voice(c.note, midiToFrequency(c.note)))
            }
            is Command.Off -> voices.firstOrNull { it.note == c.note && it.stage != Stage.RELEASE }?.stage = Stage.RELEASE
            Command.AllOff -> voices.forEach { it.stage = Stage.RELEASE }
        }
    }

    private fun renderLoop() {
        val out = FloatArray(bufferFrames * 2)
        val delay = FloatArray(2048)
        var delayIndex = 0
        var low = 0f
        var band = 0f
        var lfoPhase = 0.0
        while (running) {
            applyCommands()
            val a=attack; val d=decay; val s=sustain; val r=release
            val mix=oscMix; val detune=detuneCents; val vol=volume
            val f=(2.0 * sin(PI * cutoff.coerceAtMost(11000f) / sampleRate)).toFloat().coerceIn(.001f,.95f)
            val damp=(1f-resonance).coerceIn(.12f,1f)
            val localOsc1=osc1; val localOsc2=osc2; val localChorus=chorusMix
            for (frame in 0 until bufferFrames) {
                var mono=0f
                val iterator=voices.iterator()
                while(iterator.hasNext()) {
                    val v=iterator.next()
                    v.envelope=advance(v,a,d,s,r)
                    if(v.stage==Stage.RELEASE && v.envelope<=0f) { iterator.remove(); continue }
                    val dt1=v.frequency/sampleRate
                    val freq2=v.frequency*Math.pow(2.0,detune/1200.0)
                    val dt2=freq2/sampleRate
                    val x1=wave(localOsc1,v.phase1,dt1)
                    val x2=wave(localOsc2,v.phase2,dt2)
                    v.phase1=(v.phase1+dt1)%1.0; v.phase2=(v.phase2+dt2)%1.0
                    mono += (x1*(1f-mix)+x2*mix)*v.envelope
                }
                if(voices.isNotEmpty()) mono /= kotlin.math.sqrt(voices.size.toFloat()).coerceAtLeast(1f)
                band += f*(mono-low-band*damp)
                low += f*band
                val dry=low*vol
                lfoPhase += 0.24/sampleRate
                if(lfoPhase>=1.0) lfoPhase-=1.0
                val delaySamples=(620 + 115*sin(lfoPhase*2*PI)).toInt()
                val read=(delayIndex-delaySamples+delay.size)%delay.size
                val wet=delay[read]
                delay[delayIndex]=dry
                delayIndex=(delayIndex+1)%delay.size
                val left=dry*(1f-localChorus)+wet*localChorus
                val right=dry*(1f-localChorus)+delay[(read+37)%delay.size]*localChorus
                out[frame*2]=left.coerceIn(-1f,1f)
                out[frame*2+1]=right.coerceIn(-1f,1f)
            }
            track?.write(out,0,out.size,AudioTrack.WRITE_BLOCKING)
        }
    }

    private fun advance(v: Voice,a:Float,d:Float,s:Float,r:Float):Float = when(v.stage) {
        Stage.ATTACK -> (v.envelope+1f/(a*sampleRate)).let { if(it>=1f){v.stage=Stage.DECAY;1f}else it }
        Stage.DECAY -> (v.envelope-(1f-s)/(d*sampleRate)).let { if(it<=s){v.stage=Stage.SUSTAIN;s}else it }
        Stage.SUSTAIN -> s
        Stage.RELEASE -> (v.envelope-1f/(r*sampleRate)).coerceAtLeast(0f)
    }

    private fun wave(type:Waveform,phase:Double,dt:Double):Float = when(type) {
        Waveform.SINE -> sin(phase*2*PI).toFloat()
        Waveform.TRIANGLE -> (4.0*kotlin.math.abs(phase-floor(phase+.5))-1.0).toFloat()
        Waveform.SAW -> ((2.0*phase-1.0)-polyBlep(phase,dt)).toFloat()
        Waveform.SQUARE -> {
            var y=if(phase<.5)1.0 else -1.0
            y+=polyBlep(phase,dt); y-=polyBlep((phase+.5)%1.0,dt); y.toFloat()
        }
    }
    private fun polyBlep(t:Double,dt:Double):Double = when {
        t<dt -> { val x=t/dt; x+x-x*x-1.0 }
        t>1.0-dt -> { val x=(t-1.0)/dt; x*x+x+x+1.0 }
        else -> 0.0
    }
    private fun midiToFrequency(note:Int)=440.0*Math.pow(2.0,(note-69)/12.0)
}
