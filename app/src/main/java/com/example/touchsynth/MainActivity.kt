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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.shape.RoundedCornerShape
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
        setContent { TouchSynthApp() }
    }
}

@Composable
private fun TouchSynthApp() {
    val engine = remember { SynthEngine() }
    var waveform by remember { mutableStateOf(SynthEngine.Waveform.SAW) }
    var volume by remember { mutableFloatStateOf(0.25f) }
    var attack by remember { mutableFloatStateOf(0.02f) }
    var release by remember { mutableFloatStateOf(0.25f) }
    var activeNote by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        engine.start()
        onDispose { engine.stop() }
    }

    MaterialTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF111318))
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("TOUCH SYNTH", color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold)
            Text(
                activeNote?.let { "正在演奏：$it" } ?: "按住下方音符开始发声",
                color = Color(0xFFB7BDC9)
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SynthEngine.Waveform.entries.forEach { option ->
                    Button(
                        onClick = {
                            waveform = option
                            engine.setWaveform(option)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (waveform == option) Color(0xFF7C5CFC) else Color(0xFF282C35)
                        )
                    ) {
                        Text(
                            when (option) {
                                SynthEngine.Waveform.SINE -> "正弦"
                                SynthEngine.Waveform.SAW -> "锯齿"
                                SynthEngine.Waveform.SQUARE -> "方波"
                            }
                        )
                    }
                }
            }

            ParameterSlider("音量", volume, 0.05f..0.60f) {
                volume = it
                engine.setVolume(it)
            }
            ParameterSlider("Attack", attack, 0.005f..1.5f) {
                attack = it
                engine.setAttack(it)
            }
            ParameterSlider("Release", release, 0.03f..3f) {
                release = it
                engine.setRelease(it)
            }

            Spacer(Modifier.height(4.dp))
            Text("C 大调音阶", color = Color.White, fontWeight = FontWeight.Medium)

            val notes = listOf(
                60 to "C4", 62 to "D4", 64 to "E4", 65 to "F4",
                67 to "G4", 69 to "A4", 71 to "B4", 72 to "C5"
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                notes.chunked(4).forEach { rowNotes ->
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowNotes.forEach { (midi, label) ->
                            NotePad(
                                label = label,
                                modifier = Modifier.weight(1f).fillMaxSize(),
                                onPress = {
                                    activeNote = label
                                    engine.noteOn(midi)
                                },
                                onRelease = {
                                    activeNote = null
                                    engine.noteOff()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ParameterSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Color.White)
            Text("%.2f".format(value), color = Color(0xFFB7BDC9))
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = range)
    }
}

@Composable
private fun NotePad(
    label: String,
    modifier: Modifier,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    Box(
        modifier = modifier
            .background(Color(0xFFE8EAF0), RoundedCornerShape(18.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        onPress()
                        tryAwaitRelease()
                        onRelease()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color(0xFF15171C), fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}
