package com.example.touchsynth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
        setContent {
            MaterialTheme {
                TouchSynthScreen()
            }
        }
    }
}

@Composable
private fun TouchSynthScreen() {
    val engine = remember { SynthEngine() }
    var waveform by remember { mutableStateOf(SynthEngine.Waveform.SAW) }
    var volume by remember { mutableFloatStateOf(0.25f) }
    var cutoff by remember { mutableFloatStateOf(5000f) }
    var attack by remember { mutableFloatStateOf(0.02f) }
    var decay by remember { mutableFloatStateOf(0.30f) }
    var sustain by remember { mutableFloatStateOf(0.70f) }
    var release by remember { mutableFloatStateOf(0.25f) }
    var activeNote by remember { mutableStateOf<String?>(null) }

    DisposableEffect(engine) {
        engine.start()
        onDispose { engine.stop() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111318))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("TOUCH SYNTH", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            activeNote?.let { "正在演奏：$it" } ?: "按住下方音符开始发声",
            color = Color(0xFFB7BDC9)
        )

        SectionTitle("OSCILLATOR")
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            WaveButton("正弦", waveform == SynthEngine.Waveform.SINE) {
                waveform = SynthEngine.Waveform.SINE
                engine.setWaveform(waveform)
            }
            WaveButton("锯齿", waveform == SynthEngine.Waveform.SAW) {
                waveform = SynthEngine.Waveform.SAW
                engine.setWaveform(waveform)
            }
            WaveButton("方波", waveform == SynthEngine.Waveform.SQUARE) {
                waveform = SynthEngine.Waveform.SQUARE
                engine.setWaveform(waveform)
            }
        }

        ParameterSlider("音量", volume, 0.05f..0.60f, "%.2f".format(volume)) {
            volume = it
            engine.setVolume(it)
        }

        SectionTitle("FILTER")
        ParameterSlider("Cutoff", cutoff, 80f..12000f, "${cutoff.toInt()} Hz") {
            cutoff = it
            engine.setCutoff(it)
        }

        SectionTitle("ENVELOPE")
        ParameterSlider("Attack", attack, 0.005f..2f, "%.2f s".format(attack)) {
            attack = it
            engine.setAttack(it)
        }
        ParameterSlider("Decay", decay, 0.005f..2f, "%.2f s".format(decay)) {
            decay = it
            engine.setDecay(it)
        }
        ParameterSlider("Sustain", sustain, 0f..1f, "%.2f".format(sustain)) {
            sustain = it
            engine.setSustain(it)
        }
        ParameterSlider("Release", release, 0.03f..4f, "%.2f s".format(release)) {
            release = it
            engine.setRelease(it)
        }

        SectionTitle("C 大调音阶")
        KeyboardRow(
            notes = listOf(60 to "C4", 62 to "D4", 64 to "E4", 65 to "F4"),
            engine = engine,
            onActiveNoteChange = { activeNote = it }
        )
        KeyboardRow(
            notes = listOf(67 to "G4", 69 to "A4", 71 to "B4", 72 to "C5"),
            engine = engine,
            onActiveNoteChange = { activeNote = it }
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, color = Color.White, fontWeight = FontWeight.Medium, fontSize = 15.sp)
}

@Composable
private fun WaveButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier.width(100.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) Color(0xFF7C5CFC) else Color(0xFF282C35)
        )
    ) {
        Text(text)
    }
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

@Composable
private fun KeyboardRow(
    notes: List<Pair<Int, String>>,
    engine: SynthEngine,
    onActiveNoteChange: (String?) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        notes.forEach { (midi, label) ->
            NotePad(
                label = label,
                onPress = {
                    onActiveNoteChange(label)
                    engine.noteOn(midi)
                },
                onRelease = {
                    onActiveNoteChange(null)
                    engine.noteOff()
                }
            )
        }
    }
}

@Composable
private fun NotePad(label: String, onPress: () -> Unit, onRelease: () -> Unit) {
    Box(
        modifier = Modifier
            .width(78.dp)
            .height(110.dp)
            .background(Color(0xFFE8EAF0), RoundedCornerShape(16.dp))
            .pointerInput(label) {
                detectTapGestures(
                    onPress = {
                        onPress()
                        try {
                            tryAwaitRelease()
                        } finally {
                            onRelease()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color(0xFF15171C), fontSize = 20.sp, fontWeight = FontWeight.Bold)
    }
}
