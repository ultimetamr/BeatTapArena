package com.pico.swan.beattap.domain

import kotlin.math.abs

object BeatRules {
    const val PERFECT_WINDOW_MS = 140L
    const val GOOD_WINDOW_MS = 320L

    fun judge(deltaMs: Long, calibrationOffsetMs: Int = 0): Judgement {
        val corrected = abs(deltaMs - calibrationOffsetMs)
        return when {
            corrected <= PERFECT_WINDOW_MS -> Judgement.PERFECT
            corrected <= GOOD_WINDOW_MS -> Judgement.GOOD
            else -> Judgement.MISS
        }
    }

    fun starsFor(accuracy: Float): Int = when {
        accuracy >= 95f -> 5
        accuracy >= 88f -> 4
        accuracy >= 78f -> 3
        accuracy >= 65f -> 2
        else -> 1
    }

    fun generate(track: Track, difficulty: Difficulty): List<BeatEvent> {
        val totalBeats = track.durationSeconds * track.bpm / 60
        return buildList {
            for (beat in 4 until totalBeats) {
                val occupied = when (difficulty) {
                    Difficulty.PRACTICE -> beat % 2 == 0
                    Difficulty.NORMAL -> beat % 4 != 3
                    Difficulty.CHALLENGE -> true
                }
                if (!occupied) continue

                val pairEvery = when (difficulty) {
                    Difficulty.PRACTICE -> Int.MAX_VALUE
                    Difficulty.NORMAL -> 24
                    Difficulty.CHALLENGE -> 8
                }
                val isPair = beat >= 12 && (beat + track.seed) % pairEvery == 0
                val lanes = if (isPair) {
                    setOf(Lane.LEFT, Lane.RIGHT)
                } else {
                    setOf(Lane.values()[(beat + track.seed) % Lane.values().size])
                }
                add(BeatEvent(beat, lanes))
            }
        }
    }

    fun lightLevel(combo: Int): Int = (combo / 8).coerceIn(0, 4)
}

