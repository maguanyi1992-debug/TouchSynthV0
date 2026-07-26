package com.example.touchsynth

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.touchsynth.audio.SynthEngine

class MainActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);setContent{MaterialTheme{App()}}}}
private enum class Page{PLAY,CHORDS,SOUND}
private data class ChordType(val name:String,val intervals:List<Int>)
private data class ChordPad(val root:Int,val type:Int,val octave:Int=3)
private val roots=listOf("C","C#","D","Eb","E","F","F#","G","Ab","A","Bb","B")
private val types=listOf(
 ChordType("maj",listOf(0,4,7)),ChordType("m",listOf(0,3,7)),ChordType("sus2",listOf(0,2,7)),ChordType("sus4",listOf(0,5,7)),
 ChordType("maj7",listOf(0,4,7,11)),ChordType("7",listOf(0,4,7,10)),ChordType("m7",listOf(0,3,7,10)),ChordType("mMaj7",listOf(0,3,7,11)),
 ChordType("m7b5",listOf(0,3,6,10)),ChordType("dim7",listOf(0,3,6,9)),ChordType("6",listOf(0,4,7,9)),ChordType("m6",listOf(0,3,7,9)),
 ChordType("add9",listOf(0,4,7,14)),ChordType("madd9",listOf(0,3,7,14)),ChordType("maj9",listOf(0,4,7,11,14)),ChordType("9",listOf(0,4,7,10,14)),
 ChordType("m9",listOf(0,3,7,10,14)),ChordType("6/9",listOf(0,4,7,9,14)),ChordType("m11",listOf(0,3,7,10,14,17)),ChordType("13",listOf(0,4,7,10,14,21)),
 ChordType("maj7#11",listOf(0,4,7,11,14,18)),ChordType("7b9",listOf(0,4,7,10,13)),ChordType("7#9",listOf(0,4,7,10,15)),ChordType("aug",listOf(0,4,8))
)
private fun label(p:ChordPad)=roots[p.root]+types[p.type].name
private fun notes(p:ChordPad):List<Int>{val base=12*(p.octave+1)+p.root;return types[p.type].intervals.map{base+it}}

@Composable private fun App(){
 val engine=remember{SynthEngine()};var page by remember{mutableStateOf(Page.PLAY)}
 var preset by remember{mutableStateOf(SynthEngine.presets.first())}
 var pads by remember{mutableStateOf(listOf(ChordPad(9,12),ChordPad(4,0),ChordPad(1,6),ChordPad(11,3),ChordPad(6,16),ChordPad(2,4),ChordPad(4,17),ChordPad(9,14)))}
 var selected by remember{mutableIntStateOf(0)};var latch by remember{mutableStateOf(false)};var latched by remember{mutableStateOf(setOf<Int>())}
 var cutoff by remember{mutableFloatStateOf(preset.cutoff)};var detune by remember{mutableFloatStateOf(preset.detuneCents)}
 var mix by remember{mutableFloatStateOf(preset.oscMix)};var resonance by remember{mutableFloatStateOf(preset.resonance)}
 var attack by remember{mutableFloatStateOf(preset.attack)};var release by remember{mutableFloatStateOf(preset.release)};var chorus by remember{mutableFloatStateOf(preset.chorus)}
 DisposableEffect(Unit){engine.applyPreset(preset);engine.start();onDispose{engine.stop()}}
 Column(Modifier.fillMaxSize().background(Color(0xFF101218)).statusBarsPadding()){
  Text("TOUCH SYNTH V0.4",Color.White,22.sp,FontWeight.Bold,modifier=Modifier.padding(14.dp,10.dp,14.dp,4.dp))
  Row(Modifier.fillMaxWidth().padding(horizontal=12.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
   Tab("演奏",page==Page.PLAY){page=Page.PLAY};Tab("和弦",page==Page.CHORDS){page=Page.CHORDS};Tab("音色",page==Page.SOUND){page=Page.SOUND}
  }
  when(page){
   Page.PLAY->Play(engine,pads,latch,latched,{latch=it;if(!it){engine.allNotesOff();latched=emptySet()}},{latched=it},preset,{p->preset=p;engine.applyPreset(p);cutoff=p.cutoff;detune=p.detuneCents;mix=p.oscMix;resonance=p.resonance;attack=p.attack;release=p.release;chorus=p.chorus})
   Page.CHORDS->ChordEditor(pads,selected,{selected=it},{pads=it})
   Page.SOUND->Sound(engine,cutoff,detune,mix,resonance,attack,release,chorus,{cutoff=it;engine.setCutoff(it)},{detune=it;engine.setDetune(it)},{mix=it;engine.setOscMix(it)},{resonance=it;engine.setResonance(it)},{attack=it;engine.setAttack(it)},{release=it;engine.setRelease(it)},{chorus=it;engine.setChorus(it)})
  }
 }
}
@Composable private fun Tab(t:String,s:Boolean,on:()->Unit){Button(onClick=on,modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(if(s)Color(0xFF7657FF)else Color(0xFF292D36))){Text(t)}}

@Composable private fun Play(engine:SynthEngine,pads:List<ChordPad>,latch:Boolean,latched:Set<Int>,onLatch:(Boolean)->Unit,onLatched:(Set<Int>)->Unit,preset:SynthEngine.Preset,onPreset:(SynthEngine.Preset)->Unit){
 Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(10.dp)){
  Row(verticalAlignment=Alignment.CenterVertically){Text("和弦演奏",Color.White,18.sp,FontWeight.Bold,modifier=Modifier.weight(1f));Text("Latch",Color.White);Switch(latch,onLatch)}
  Text(engine.audioInfo()+" · 建议手机扬声器/有线耳机",Color(0xFFADB4C4),12.sp)
  SynthEngine.presets.chunked(3).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)){row.forEach{p->Button(onClick={onPreset(p)},modifier=Modifier.weight(1f),colors=ButtonDefaults.buttonColors(if(p.name==preset.name)Color(0xFF7657FF)else Color(0xFF292D36)),contentPadding=PaddingValues(7.dp)){Text(p.name,fontSize=11.sp)}}}}
  pads.chunked(2).forEachIndexed{ri,row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)){row.forEachIndexed{ci,p->val index=ri*2+ci;ChordButton(label(p),types[p.type].intervals.size,index in latched,Modifier.weight(1f)){down->val ns=notes(p);if(latch&&down){if(index in latched){engine.noteOff(ns);onLatched(latched-index)}else{engine.noteOn(ns);onLatched(latched+index)}}else if(!latch){if(down)engine.noteOn(ns)else engine.noteOff(ns)}}}}}
  Button(onClick={engine.allNotesOff();onLatched(emptySet())},modifier=Modifier.fillMaxWidth(),colors=ButtonDefaults.buttonColors(Color(0xFF9A3E4A))){Text("PANIC · 全部停止")}
 }
}
@Composable private fun ChordButton(name:String,count:Int,on:Boolean,mod:Modifier,event:(Boolean)->Unit){var pressed by remember{mutableStateOf(false)};Box(mod.height(108.dp).background(if(pressed||on)Color(0xFF7657FF)else Color(0xFF242832),RoundedCornerShape(18.dp)).pointerInput(name){detectTapGestures(onPress={pressed=true;event(true);try{tryAwaitRelease()}finally{if(!on)event(false);pressed=false}})},contentAlignment=Alignment.Center){Column(horizontalAlignment=Alignment.CenterHorizontally){Text(name,Color.White,24.sp,FontWeight.Bold);Text("$count notes",Color(0xFFD5D0FF),12.sp)}}}

@Composable private fun ChordEditor(pads:List<ChordPad>,selected:Int,onSelect:(Int)->Unit,onPads:(List<ChordPad>)->Unit){val p=pads[selected];Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){
 Text("选择要编辑的 Pad",Color.White,FontWeight.Bold);Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)){pads.forEachIndexed{i,x->Button(onClick={onSelect(i)},modifier=Modifier.weight(1f),contentPadding=PaddingValues(4.dp),colors=ButtonDefaults.buttonColors(if(i==selected)Color(0xFF7657FF)else Color(0xFF292D36))){Text("${i+1}",fontSize=12.sp)}}}
 Text("Pad ${selected+1}：${label(p)}",Color.White,22.sp,FontWeight.Bold)
 Text("根音",Color.White);ChoiceGrid(roots,p.root){r->onPads(pads.toMutableList().also{it[selected]=p.copy(root=r)})}
 Text("和弦类型（含七、九、十一、十三和弦）",Color.White);ChoiceGrid(types.map{it.name},p.type,4){t->onPads(pads.toMutableList().also{it[selected]=p.copy(type=t)})}
 Text("基础八度",Color.White);Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){(2..5).forEach{o->Button(onClick={onPads(pads.toMutableList().also{it[selected]=p.copy(octave=o)})},colors=ButtonDefaults.buttonColors(if(o==p.octave)Color(0xFF7657FF)else Color(0xFF292D36))){Text("$o")}}}
 Text("实际 MIDI 音符：${notes(p).joinToString()}",Color(0xFFADB4C4),12.sp)
 }}
@Composable private fun ChoiceGrid(items:List<String>,selected:Int,columns:Int=6,on:(Int)->Unit){items.chunked(columns).forEach{row->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(5.dp)){row.forEach{s->val i=items.indexOf(s);Button(onClick={on(i)},modifier=Modifier.weight(1f),contentPadding=PaddingValues(3.dp),colors=ButtonDefaults.buttonColors(if(i==selected)Color(0xFF7657FF)else Color(0xFF292D36))){Text(s,fontSize=10.sp)}}repeat(columns-row.size){Spacer(Modifier.weight(1f))}}}}

@Composable private fun Sound(engine:SynthEngine,cutoff:Float,detune:Float,mix:Float,res:Float,attack:Float,release:Float,chorus:Float,onCut:(Float)->Unit,onDet:(Float)->Unit,onMix:(Float)->Unit,onRes:(Float)->Unit,onAtt:(Float)->Unit,onRel:(Float)->Unit,onCho:(Float)->Unit){Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
 Text("双振荡器 + PolyBLEP + 共振滤波 + Chorus",Color.White,FontWeight.Bold)
 Param("OSC 2 Mix",mix,0f..1f,"%.2f".format(mix),onMix);Param("Detune",detune,-30f..30f,"%.0f cents".format(detune),onDet)
 Param("Cutoff",cutoff,80f..12000f,"${cutoff.toInt()} Hz",onCut);Param("Resonance",res,0f..0.85f,"%.2f".format(res),onRes)
 Param("Attack",attack,.003f..1.5f,"%.3f s".format(attack),onAtt);Param("Release",release,.02f..4f,"%.2f s".format(release),onRel);Param("Chorus",chorus,0f..0.55f,"%.2f".format(chorus),onCho)
 Button(onClick={onAtt(.005f);onRel(.18f);onCut(6000f)},modifier=Modifier.fillMaxWidth()){Text("低延迟演奏设置")}
 }}
@Composable private fun Param(name:String,v:Float,range:ClosedFloatingPointRange<Float>,value:String,on:(Float)->Unit){Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(name,Color.White);Text(value,Color(0xFFB8B0FF))};Slider(v,on, valueRange=range)}
