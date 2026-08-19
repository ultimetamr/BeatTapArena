package com.pico.swan.beattap.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pico.spatial.ui.design.Button
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.material.backgroundMaterial
import com.pico.spatial.ui.platform.Material
import com.pico.swan.beattap.domain.BeatEvent
import com.pico.swan.beattap.domain.BeatRules
import com.pico.swan.beattap.domain.Difficulty
import com.pico.swan.beattap.domain.Judgement
import com.pico.swan.beattap.domain.Lane
import com.pico.swan.beattap.domain.ResultSummary
import com.pico.swan.beattap.domain.TrackCatalog
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private enum class ArenaPhase { CATALOG, COUNTDOWN, PLAYING, PAUSED, RESULT }
private enum class InputMode(val label: String) { AUTO("自动"), HAND("手势"), CONTROLLER("手柄") }

private data class ScoreState(
    val perfect: Int = 0,
    val good: Int = 0,
    val miss: Int = 0,
    val combo: Int = 0,
    val maxCombo: Int = 0,
) {
    fun record(judgement: Judgement): ScoreState = when (judgement) {
        Judgement.PERFECT -> copy(
            perfect = perfect + 1,
            combo = combo + 1,
            maxCombo = maxOf(maxCombo, combo + 1),
        )
        Judgement.GOOD -> copy(
            good = good + 1,
            combo = combo + 1,
            maxCombo = maxOf(maxCombo, combo + 1),
        )
        Judgement.MISS -> copy(miss = miss + 1, combo = 0)
    }

    fun summary() = ResultSummary(perfect, good, miss, maxCombo)
}

@Composable
fun BeatTapArenaScreen() {
    val context = LocalContext.current
    val demoStage = remember(context) { context.findActivity()?.intent?.getStringExtra("screen") == "stage" }
    var selectedTrackIndex by remember { mutableStateOf(0) }
    var difficulty by remember { mutableStateOf(Difficulty.NORMAL) }
    var inputMode by remember { mutableStateOf(if (demoStage) InputMode.CONTROLLER else InputMode.AUTO) }
    var calibrationMs by remember { mutableStateOf(20) }
    var reduceMotion by remember { mutableStateOf(false) }
    var phase by remember { mutableStateOf(if (demoStage) ArenaPhase.PLAYING else ArenaPhase.CATALOG) }
    var countdown by remember { mutableStateOf(3) }
    var runId by remember { mutableStateOf(0) }
    var currentBeat by remember { mutableStateOf(4) }
    var activeEvent by remember { mutableStateOf<BeatEvent?>(null) }
    var eventStartedAt by remember { mutableStateOf(0L) }
    var hitLanes by remember { mutableStateOf(emptySet<Lane>()) }
    var targetProgress by remember { mutableStateOf(0f) }
    var score by remember { mutableStateOf(ScoreState(combo = if (demoStage) 24 else 0, maxCombo = if (demoStage) 24 else 0)) }
    var lastJudgement by remember { mutableStateOf(if (demoStage) "Perfect · 手柄" else "等待节拍") }

    val track = TrackCatalog.builtIn[selectedTrackIndex]
    val beatMap = remember(track.id, difficulty) { BeatRules.generate(track, difficulty) }
    val eventsByBeat = remember(beatMap) { beatMap.associateBy { it.beatIndex } }
    val totalBeats = track.durationSeconds * track.bpm / 60
    val beatIntervalMs = 60_000L / track.bpm
    val tone = remember { runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, 68) }.getOrNull() }

    DisposableEffect(tone) {
        onDispose { tone?.release() }
    }

    fun record(judgement: Judgement) {
        score = score.record(judgement)
        lastJudgement = when (judgement) {
            Judgement.PERFECT -> "Perfect · ${inputMode.label}"
            Judgement.GOOD -> "Good · ${inputMode.label}"
            Judgement.MISS -> "Miss · 继续听拍"
        }
    }

    fun hitLane(lane: Lane) {
        val event = activeEvent ?: return
        if (phase != ArenaPhase.PLAYING || lane !in event.lanes || lane in hitLanes) return
        val elapsed = SystemClock.elapsedRealtime() - eventStartedAt
        val judgement = BeatRules.judge(elapsed, 210 + calibrationMs)
        hitLanes = hitLanes + lane
        record(judgement)
    }

    fun startRun() {
        score = ScoreState()
        currentBeat = 4
        activeEvent = null
        hitLanes = emptySet()
        targetProgress = 0f
        countdown = 3
        runId += 1
        phase = ArenaPhase.COUNTDOWN
    }

    LaunchedEffect(phase, runId, track.id, difficulty) {
        when (phase) {
            ArenaPhase.COUNTDOWN -> {
                for (value in 3 downTo 1) {
                    countdown = value
                    tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 70)
                    delay(700L)
                }
                phase = ArenaPhase.PLAYING
            }
            ArenaPhase.PLAYING -> {
                while (currentBeat < totalBeats) {
                    val event = eventsByBeat[currentBeat]
                    activeEvent = event
                    hitLanes = emptySet()
                    targetProgress = if (reduceMotion) 1f else 0f
                    eventStartedAt = SystemClock.elapsedRealtime()
                    if (event != null) {
                        tone?.startTone(
                            if (event.isPair) ToneGenerator.TONE_PROP_ACK else ToneGenerator.TONE_PROP_BEEP,
                            75,
                        )
                    }

                    val steps = if (reduceMotion) 1 else 6
                    repeat(steps) { step ->
                        delay(BeatRules.GOOD_WINDOW_MS / steps)
                        targetProgress = (step + 1f) / steps
                    }

                    event?.lanes?.minus(hitLanes)?.forEach { record(Judgement.MISS) }
                    activeEvent = null
                    targetProgress = 0f
                    delay((beatIntervalMs - BeatRules.GOOD_WINDOW_MS).coerceAtLeast(30L))
                    currentBeat += 1
                }
                phase = ArenaPhase.RESULT
            }
            else -> Unit
        }
    }

    ArenaSurface(phase = phase) {
        when (phase) {
            ArenaPhase.CATALOG -> CatalogScreen(
                selectedTrackIndex = selectedTrackIndex,
                onTrackSelected = { selectedTrackIndex = it },
                difficulty = difficulty,
                onDifficultySelected = { difficulty = it },
                inputMode = inputMode,
                onInputSelected = { inputMode = it },
                calibrationMs = calibrationMs,
                onCalibrationChanged = { calibrationMs = it.coerceIn(-120, 120) },
                reduceMotion = reduceMotion,
                onToggleReduceMotion = { reduceMotion = !reduceMotion },
                onStart = ::startRun,
            )
            ArenaPhase.COUNTDOWN -> CountdownScreen(track.title, countdown, inputMode)
            ArenaPhase.PLAYING -> GameScreen(
                title = track.title,
                difficulty = difficulty,
                inputMode = inputMode,
                onControllerTakeover = { inputMode = InputMode.CONTROLLER },
                score = score,
                lastJudgement = lastJudgement,
                activeEvent = activeEvent,
                hitLanes = hitLanes,
                targetProgress = targetProgress,
                beat = currentBeat,
                totalBeats = totalBeats,
                onHit = ::hitLane,
                onPause = { phase = ArenaPhase.PAUSED },
            )
            ArenaPhase.PAUSED -> PauseScreen(
                inputMode = inputMode,
                onControllerTakeover = { inputMode = InputMode.CONTROLLER },
                onResume = {
                    countdown = 3
                    phase = ArenaPhase.COUNTDOWN
                },
                onExit = { phase = ArenaPhase.CATALOG },
            )
            ArenaPhase.RESULT -> ResultScreen(
                title = track.title,
                summary = score.summary(),
                onRetry = ::startRun,
                onExit = { phase = ArenaPhase.CATALOG },
            )
        }
    }
}

@Composable
private fun ArenaSurface(phase: ArenaPhase, content: @Composable () -> Unit) {
    val base = Modifier
        .fillMaxSize()
        .clip(RoundedCornerShape(32.dp))
    val surface = if (phase == ArenaPhase.CATALOG) {
        base.backgroundMaterial(true, Material.Regular)
    } else {
        base.background(PicoTheme.colorScheme.fillPrimary)
    }
    Box(modifier = surface.padding(30.dp), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun CatalogScreen(
    selectedTrackIndex: Int,
    onTrackSelected: (Int) -> Unit,
    difficulty: Difficulty,
    onDifficultySelected: (Difficulty) -> Unit,
    inputMode: InputMode,
    onInputSelected: (InputMode) -> Unit,
    calibrationMs: Int,
    onCalibrationChanged: (Int) -> Unit,
    reduceMotion: Boolean,
    onToggleReduceMotion: () -> Unit,
    onStart: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
        Column(
            modifier = Modifier.width(460.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("节拍打靶", style = PicoTheme.typography.displaySmall)
            Text("选一首歌，用捏合或手柄点亮舞台。", style = PicoTheme.typography.bodyLarge)
            Spacer(Modifier.height(4.dp))
            TrackCatalog.builtIn.forEachIndexed { index, track ->
                Button(onClick = { onTrackSelected(index) }) {
                    Text(
                        text = if (selectedTrackIndex == index) {
                            "✓ ${track.title} · ${track.bpm} BPM · ${track.durationSeconds}秒"
                        } else {
                            "${track.title} · ${track.bpm} BPM · ${track.durationSeconds}秒"
                        },
                    )
                }
            }
            Text("5 首本地合成节拍 · 无联网曲库", style = PicoTheme.typography.labelMedium)
        }

        Column(
            modifier = Modifier.width(610.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("本局设置", style = PicoTheme.typography.titleLarge)
            Text("难度", style = PicoTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Difficulty.values().forEach { item ->
                    Button(onClick = { onDifficultySelected(item) }) {
                        Text(if (difficulty == item) "✓ ${item.label}" else item.label)
                    }
                }
            }
            Text("练习目标更疏；挑战增加密度与同拍双手。目标速度保持舒适。", style = PicoTheme.typography.bodyMedium)
            Text("输入", style = PicoTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                InputMode.values().forEach { item ->
                    Button(onClick = { onInputSelected(item) }) {
                        Text(if (inputMode == item) "✓ ${item.label}" else item.label)
                    }
                }
            }
            Text("凝视目标后捏合；手柄确认键可完成全部流程。", style = PicoTheme.typography.bodyMedium)
            Text("音画校准：${if (calibrationMs >= 0) "+" else ""}${calibrationMs} ms", style = PicoTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { onCalibrationChanged(calibrationMs - 10) }) { Text("−10 ms") }
                Button(onClick = { onCalibrationChanged(0) }) { Text("归零") }
                Button(onClick = { onCalibrationChanged(calibrationMs + 10) }) { Text("+10 ms") }
                Button(onClick = onToggleReduceMotion) { Text(if (reduceMotion) "✓ 减少动态" else "减少动态") }
            }
            Spacer(Modifier.height(6.dp))
            Button(onClick = onStart) { Text("进入舞台 · 3 秒倒计时") }
        }
    }
}

@Composable
private fun CountdownScreen(title: String, countdown: Int, inputMode: InputMode) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(title, style = PicoTheme.typography.titleLarge)
        Text(countdown.toString(), style = PicoTheme.typography.displayLarge)
        Text("目标到达命中环时捏合或按确认键", style = PicoTheme.typography.titleMedium)
        Text("当前输入：${inputMode.label} · 左/右按提示，中间任意手", style = PicoTheme.typography.bodyLarge)
    }
}

@Composable
private fun GameScreen(
    title: String,
    difficulty: Difficulty,
    inputMode: InputMode,
    onControllerTakeover: () -> Unit,
    score: ScoreState,
    lastJudgement: String,
    activeEvent: BeatEvent?,
    hitLanes: Set<Lane>,
    targetProgress: Float,
    beat: Int,
    totalBeats: Int,
    onHit: (Lane) -> Unit,
    onPause: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Column {
                Text("$title · ${difficulty.label}", style = PicoTheme.typography.titleLarge)
                Text("第 ${beat.coerceAtMost(totalBeats)} / $totalBeats 拍", style = PicoTheme.typography.bodyMedium)
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${score.combo} 连击", style = PicoTheme.typography.displaySmall)
                Text(lastJudgement, style = PicoTheme.typography.labelLarge)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onControllerTakeover) { Text(if (inputMode == InputMode.CONTROLLER) "✓ 手柄接管" else "手柄接管") }
                Button(onClick = onPause) { Text("暂停") }
            }
        }

        StageLights(level = BeatRules.lightLevel(score.combo))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LanePad(Lane.LEFT, activeEvent, hitLanes, targetProgress) { onHit(Lane.LEFT) }
            LanePad(Lane.CENTER, activeEvent, hitLanes, targetProgress) { onHit(Lane.CENTER) }
            LanePad(Lane.RIGHT, activeEvent, hitLanes, targetProgress) { onHit(Lane.RIGHT) }
        }
        Text(
            text = if (activeEvent?.isPair == true) "同拍双手：左右目标没有先后" else "三个通道始终在正前方，无需快速转头",
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
            style = PicoTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun StageLights(level: Int) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Text("舞台灯光  ", style = PicoTheme.typography.labelLarge)
        repeat(4) { index ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 5.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(if (index < level) PicoTheme.colorScheme.labelPrimary else PicoTheme.colorScheme.fillPrimary),
            )
        }
    }
}

@Composable
private fun LanePad(
    lane: Lane,
    activeEvent: BeatEvent?,
    hitLanes: Set<Lane>,
    progress: Float,
    onClick: () -> Unit,
) {
    val active = lane in (activeEvent?.lanes ?: emptySet()) && lane !in hitLanes
    val ringColor = when {
        active -> PicoTheme.colorScheme.labelPrimary
        lane in hitLanes -> PicoTheme.colorScheme.labelPrimary
        else -> PicoTheme.colorScheme.fillPrimary
    }
    Button(onClick = onClick) {
        Column(
            modifier = Modifier.width(290.dp).height(300.dp).padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("${lane.label}通道 · ${lane.handHint}", style = PicoTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .size(150.dp)
                    .border(8.dp, ringColor, CircleShape)
                    .padding(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (active) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Spacer(Modifier.height(((1f - progress) * 52f).dp))
                        Text(if (activeEvent?.isPair == true) "◆" else "●", style = PicoTheme.typography.displayMedium)
                    }
                } else if (lane in hitLanes) {
                    Text("✓", style = PicoTheme.typography.displayMedium)
                } else {
                    Text("◎", style = PicoTheme.typography.displayMedium)
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(if (active) "现在命中" else "等待目标", style = PicoTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun PauseScreen(
    inputMode: InputMode,
    onControllerTakeover: () -> Unit,
    onResume: () -> Unit,
    onExit: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("已保护暂停", style = PicoTheme.typography.displaySmall)
        Text("目标和判定已冻结；漏击不会继续累计。", style = PicoTheme.typography.titleMedium)
        Text("当前输入：${inputMode.label}", style = PicoTheme.typography.bodyLarge)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onControllerTakeover) { Text("手柄接管") }
            Button(onClick = onResume) { Text("继续 · 3 秒倒计时") }
            Button(onClick = onExit) { Text("返回选曲") }
        }
    }
}

@Composable
private fun ResultScreen(title: String, summary: ResultSummary, onRetry: () -> Unit, onExit: () -> Unit) {
    val filledStars = "★".repeat(summary.stars) + "☆".repeat(5 - summary.stars)
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("本曲完成 · $title", style = PicoTheme.typography.displaySmall)
        Text(filledStars, style = PicoTheme.typography.displayMedium)
        Text("命中率 ${summary.accuracy.roundToInt()}%", style = PicoTheme.typography.titleLarge)
        Text("最大连击 ${summary.maxCombo}", style = PicoTheme.typography.titleLarge)
        Text(
            "Perfect ${summary.perfect}  ·  Good ${summary.good}  ·  Miss ${summary.miss}",
            style = PicoTheme.typography.bodyLarge,
        )
        Text("漏击只中断连击，继续听节拍就好。", style = PicoTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Button(onClick = onRetry) { Text("再来一次") }
            Button(onClick = onExit) { Text("返回选曲") }
        }
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

