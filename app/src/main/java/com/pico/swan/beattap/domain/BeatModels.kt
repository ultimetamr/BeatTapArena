package com.pico.swan.beattap.domain

enum class Difficulty(val label: String) {
    PRACTICE("练习"),
    NORMAL("普通"),
    CHALLENGE("挑战"),
}

enum class Lane(val label: String, val handHint: String) {
    LEFT("左", "左手"),
    CENTER("中", "任意手"),
    RIGHT("右", "右手"),
}

enum class Judgement(val label: String) {
    PERFECT("Perfect"),
    GOOD("Good"),
    MISS("Miss"),
}

data class Track(
    val id: String,
    val title: String,
    val bpm: Int,
    val durationSeconds: Int,
    val seed: Int,
)

data class BeatEvent(
    val beatIndex: Int,
    val lanes: Set<Lane>,
) {
    val isPair: Boolean get() = lanes.size > 1
}

data class ResultSummary(
    val perfect: Int,
    val good: Int,
    val miss: Int,
    val maxCombo: Int,
) {
    val total: Int get() = perfect + good + miss
    val accuracy: Float
        get() = if (total == 0) 0f else (perfect + good) * 100f / total
    val stars: Int get() = BeatRules.starsFor(accuracy)
}

object TrackCatalog {
    val builtIn: List<Track> = listOf(
        Track("neon_start", "霓虹起步", 100, 45, 1),
        Track("glimmer_step", "微光跳步", 112, 48, 2),
        Track("skyline_echo", "天际回声", 120, 50, 3),
        Track("amber_pulse", "琥珀脉冲", 128, 52, 4),
        Track("starport_dash", "星港短跑", 136, 54, 5),
    )
}

