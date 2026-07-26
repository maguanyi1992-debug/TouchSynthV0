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
import androidx.compose.foundation.layout.width
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

    var waveform by remember {
        mutableStateOf(SynthEngine.Waveform.SAW)
    }

    var volume by remember {
        mutableFloatStateOf(0.25f)
    }

    var attack by remember {
        mutableFloatStateOf(0.02f)
    }

    var release by remember {
        mutableFloatStateOf(0.25f)
    }

    var activeNote by remember {
        mutableStateOf<String?>(null)
    }

    DisposableEffect(engine) {
        engine.start()

        onDispose {
            engine.stop()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111318))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = "TOUCH SYNTH",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = activeNote?.let {
                "正在演奏：$it"
            } ?: "按住下方音符开始发声",
            color = Color(0xFFB7BDC9)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            WaveButton(
                text = "正弦",
                selected = waveform == SynthEngine.Waveform.SINE
            ) {
                waveform = SynthEngine.Waveform.SINE
                engine.setWaveform(waveform)
            }

            WaveButton(
                text = "锯齿",
                selected = waveform == SynthEngine.Waveform.SAW
            ) {
                waveform = SynthEngine.Waveform.SAW
                engine.setWaveform(waveform)
            }

            WaveButton(
                text = "方波",
                selected = waveform == SynthEngine.Waveform.SQUARE
            ) {
                waveform = SynthEngine.Waveform.SQUARE
                engine.setWaveform(waveform)
            }
        }

        ParameterSlider(
            label = "音量",
            value = volume,
            range = 0.05f..0.60f
        ) {
            volume = it
            engine.setVolume(it)
        }

        ParameterSlider(
            label = "Attack",
            value = attack,
            range = 0.005f..1.5f
        ) {
            attack = it
            engine.setAttack(it)
        }

        ParameterSlider(
            label = "Release",
            value = release,
            range = 0.03f..3f
        ) {
            release = it
            engine.setRelease(it)
        }

        Spacer(
            modifier = Modifier.height(4.dp)
        )

        Text(
            text = "C 大调音阶",
            color = Color.White,
            fontWeight = FontWeight.Medium
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NotePad("C4") {
                activeNote = "C4"
                engine.noteOn(60)
            } onRelease@{
                activeNote = null
                engine.noteOff()
            }

            NotePad("D4") {
                activeNote = "D4"
                engine.noteOn(62)
            } onRelease@{
                activeNote = null
                engine.noteOff()
            }

            NotePad("E4") {
                activeNote = "E4"
                engine.noteOn(64)
            } onRelease@{
                activeNote = null
                engine.noteOff()
            }

            NotePad("F4") {
                activeNote = "F4"
                engine.noteOn(65)
            } onRelease@{
                activeNote = null
                engine.noteOff()
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            NotePad("G4") {
                activeNote = "G4"
                engine.noteOn(67)
            } onRelease@{
                activeNote = null
                engine.noteOff()
            }

            NotePad("A4") {
                activeNote = "A4"
                engine.noteOn(69)
            } onRelease@{
                activeNote = null
                engine.noteOff()
            }

            NotePad("B4") {
                activeNote = "B4"
                engine.noteOn(71)
            } onRelease@{
                activeNote = null
                engine.noteOff()
            }

            NotePad("C5") {
                activeNote = "C5"
                engine.noteOn(72)
            } onRelease@{
                activeNote = null
                engine.noteOff()
            }
        }
    }
}

@Composable
private fun WaveButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier.width(100.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) {
                Color(0xFF7C5CFC)
            } else {
                Color(0xFF282C35)
            }
        )
    ) {
        Text(text = text)
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = Color.White
            )

            Text(
                text = String.format("%.2f", value),
                color = Color(0xFFB7BDC9)
            )
        }

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range
        )
    }
}

@Composable
private fun NotePad(
    label: String,
    onPress: () -> Unit,
    onRelease: () -> Unit
) {
    Box(
        modifier = Modifier
            .width(78.dp)
            .height(120.dp)
            .background(
                color = Color(0xFFE8EAF0),
                shape = RoundedCornerShape(16.dp)
            )
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
        Text(
            text = label,
            color = Color(0xFF15171C),
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
