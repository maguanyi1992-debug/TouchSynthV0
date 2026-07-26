package com.example.touchsynth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.touchsynth.audio.SynthEngine

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { TouchSynthApp() } }
    }
}

private enum class Page { PLAY, SOUND }

@Composable
private fun TouchSynthApp() {
    val engine = remember { SynthEngine() }
    var page by remember { mutableStateOf(Page.PLAY) }
    var octave by remember { mutableIntStateOf(4) }
    var selectedPreset by remember { mutableStateOf(SynthEngine.presets.first()) }
    var waveform by remember { mutableStateOf(selectedPreset.waveform) }
    var volume by remember { mutableFloatStateOf(selectedPreset.volume) }
    var cutoff by remember { mutableFloatStateOf(selectedPreset.cutoff) }
    var attack by remember { mutableFloatStateOf(selectedPreset.attack) }
    var decay by remember { mutableFloatStateOf(selectedPreset.decay) }
    var sustain by remember { mutableFloatStateOf(selectedPreset.sustain) }
    var release by remember { mutableFloatStateOf(selectedPreset.release) }

    DisposableEffect(engine) {
        engine.applyPreset(selectedPreset)
        engine.start()
        onDispose { engine.stop() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111318))
            .statusBarsPadding()
    ) {
        Header(page = page, onPageChange = { page = it })
        if (page == Page.PLAY) {
            PlayPage(
                engine = engine,
                octave = octave,
                cutoff = cutoff,
                selectedPreset = selectedPreset,
                onOctaveChange = { octave = it.coerceIn(1, 7) },
                onCutoffChange = {
                    cutoff = it
                    engine.setCutoff(it)
                },
                onPresetSelected = { preset ->
                    selectedPreset = preset
                    waveform = preset.waveform
                    volume = preset.volume
                    cutoff = preset.cutoff
                    attack = preset.attack
                    decay = preset.decay
                    sustain = preset.sustain
                    release = preset.release
                    engine.applyPreset(preset)
                }
            )
        } else {
            SoundPage(
                waveform = waveform,
                volume = volume,
                cutoff = cutoff,
                attack = attack,
                decay = decay,
                sustain = sustain,
                release = release,
                onWaveformChange = {
                    waveform = it
                    engine.setWaveform(it)
                },
                onVolumeChange = {
                    volume = it
                    engine.setVolume(it)
                },
                onCutoffChange = {
                    cutoff = it
                    engine.setCutoff(it)
                },
                onAttackChange = {
                    attack = it
                    engine.setAttack(it)
                },
                onDecayChange = {
                    decay = it
                    engine.setDecay(it)
                },
                onSustainChange = {
                    sustain = it
                    engine.setSustain(it)
                },
                onReleaseChange = {
                    release = it
                    engine.setRelease(it)
                }
            )
        }
    }
}

@Composable
private fun Header(page: Page, onPageChange: (Page) -> Unit) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text("TOUCH SYNTH", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            TabButton("演奏", page == Page.PLAY) { onPageChange(Page.PLAY) }
            TabButton("音色", page == Page.SOUND) { onPageChange(Page.SOUND) }
        }
    }
}

@Composable
private fun TabButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.width(120.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Color(0xFF7C5CFC) else Color(0xFF282C35)
        )
    ) { Text(text) }
}

@Composable
private fun PlayPage(
    engine: SynthEngine,
    octave: Int,
    cutoff: Float,
    selectedPreset: SynthEngine.Preset,
    onOctaveChange: (Int) -> Unit,
    onCutoffChange: (Float) -> Unit,
    onPresetSelected: (SynthEngine.Preset) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("预设", color = Color.White, fontWeight = FontWeight.Medium)
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SynthEngine.presets.forEach { preset ->
                Button(
                    onClick = { onPresetSelected(preset) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (preset.name == selectedPreset.name) Color(0xFF7C5CFC) else Color(0xFF282C35)
                    )
                ) { Text(preset.name) }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = { onOctaveChange(octave - 1) }) { Text("八度 -") }
            Text("C$octave", color = Color.White, fontSize = 20.sp, modifier = Modifier.padding(top = 12.dp))
            Button(onClick = { onOctaveChange(octave + 1) }) { Text("八度 +") }
        }

        Text("BRIGHT  ${cutoff.toInt()} Hz", color = Color.White)
        Slider(value = cutoff, onValueChange = onCutoffChange, valueRange = 80f..12000f)

        Text("可同时按多个琴键弹和弦", color = Color(0xFFB7BDC9))
        PianoKeyboard(engine = engine, octave = octave)
    }
}

@Composable
private fun PianoKeyboard(engine: SynthEngine, octave: Int) {
    val whiteWidth = 58.dp
    val whiteHeight = 230.dp
    val blackWidth = 36.dp
    val blackHeight = 140.dp
    val baseMidi = 12 * (octave + 1)
    val whiteOffsets = listOf(0, 2, 4, 5, 7, 9, 11, 12, 14, 16)
    val blackNotes = listOf(
        1 to 40, 3 to 98, 6 to 214, 8 to 272, 10 to 330,
        13 to 446, 15 to 504
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .horizontalScroll(rememberScrollState())
    ) {
        Row {
            whiteOffsets.forEach { offset ->
                PianoKey(
                    label = noteName(baseMidi + offset),
                    midi = baseMidi + offset,
                    engine = engine,
                    width = whiteWidth,
                    height = whiteHeight,
                    keyColor = Color(0xFFF2F3F7),
                    textColor = Color(0xFF15171C)
                )
            }
        }
        blackNotes.forEach { (offset, x) ->
            PianoKey(
                label = noteName(baseMidi + offset),
                midi = baseMidi + offset,
                engine = engine,
                width = blackWidth,
                height = blackHeight,
                keyColor = Color(0xFF17191F),
                textColor = Color.White,
                modifier = Modifier.offset(x = x.dp, y = 0.dp)
            )
        }
    }
}

@Composable
private fun PianoKey(
    label: String,
    midi: Int,
    engine: SynthEngine,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    keyColor: Color,
    textColor: Color,
    modifier: Modifier = Modifier
) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier = modifier
            .width(width)
            .height(height)
            .padding(1.dp)
            .background(
                color = if (pressed) Color(0xFF7C5CFC) else keyColor,
                shape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
            )
            .pointerInput(midi) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        engine.noteOn(midi)
                        try {
                            tryAwaitRelease()
                        } finally {
                            engine.noteOff(midi)
                            pressed = false
                        }
                    }
                )
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Text(label, color = textColor, modifier = Modifier.padding(bottom = 12.dp), fontSize = 12.sp)
    }
}

@Composable
private fun SoundPage(
    waveform: SynthEngine.Waveform,
    volume: Float,
    cutoff: Float,
    attack: Float,
    decay: Float,
    sustain: Float,
    release: Float,
    onWaveformChange: (SynthEngine.Waveform) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onCutoffChange: (Float) -> Unit,
    onAttackChange: (Float) -> Unit,
    onDecayChange: (Float) -> Unit,
    onSustainChange: (Float) -> Unit,
    onReleaseChange: (Float) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("OSCILLATOR", color = Color.White, fontWeight = FontWeight.Medium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            WaveButton("正弦", waveform == SynthEngine.Waveform.SINE) { onWaveformChange(SynthEngine.Waveform.SINE) }
            WaveButton("锯齿", waveform == SynthEngine.Waveform.SAW) { onWaveformChange(SynthEngine.Waveform.SAW) }
            WaveButton("方波", waveform == SynthEngine.Waveform.SQUARE) { onWaveformChange(SynthEngine.Waveform.SQUARE) }
        }
        ParameterSlider("音量", volume, 0.05f..0.60f, "%.2f".format(volume), onVolumeChange)
        ParameterSlider("Cutoff", cutoff, 80f..12000f, "${cutoff.toInt()} Hz", onCutoffChange)
        Text("ENVELOPE", color = Color.White, fontWeight = FontWeight.Medium)
        ParameterSlider("Attack", attack, 0.005f..3f, "%.2f s".format(attack), onAttackChange)
        ParameterSlider("Decay", decay, 0.005f..3f, "%.2f s".format(decay), onDecayChange)
        ParameterSlider("Sustain", sustain, 0f..1f, "%.2f".format(sustain), onSustainChange)
        ParameterSlider("Release", release, 0.03f..5f, "%.2f s".format(release), onReleaseChange)
    }
}

@Composable
private fun WaveButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.width(100.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Color(0xFF7C5CFC) else Color(0xFF282C35)
        )
    ) { Text(text) }
}

@Composable
private fun ParameterSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    displayValue: String,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White)
            Text(displayValue, color = Color(0xFFB7BDC9))
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

private fun noteName(midi: Int): String {
    val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
    return names[midi % 12] + (midi / 12 - 1)
}
