package com.pico.swan.beattap.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BeatRulesTest {
    @Test
    fun catalog_hasFiveUniqueTracks() {
        assertEquals(5, TrackCatalog.builtIn.size)
        assertEquals(5, TrackCatalog.builtIn.map { it.id }.toSet().size)
    }

    @Test
    fun difficulty_increasesTargetDensityAndPairs() {
        val track = TrackCatalog.builtIn.first()
        val practice = BeatRules.generate(track, Difficulty.PRACTICE)
        val normal = BeatRules.generate(track, Difficulty.NORMAL)
        val challenge = BeatRules.generate(track, Difficulty.CHALLENGE)
        assertTrue(practice.size < normal.size)
        assertTrue(normal.size < challenge.size)
        assertEquals(0, practice.count { it.isPair })
        assertTrue(challenge.count { it.isPair } > normal.count { it.isPair })
        assertTrue(challenge.all { it.lanes.size <= 2 })
    }

    @Test
    fun judgementAndStarsFollowFrozenThresholds() {
        assertEquals(Judgement.PERFECT, BeatRules.judge(120))
        assertEquals(Judgement.GOOD, BeatRules.judge(220))
        assertEquals(Judgement.MISS, BeatRules.judge(400))
        assertEquals(5, BeatRules.starsFor(96f))
        assertEquals(1, BeatRules.starsFor(40f))
    }
}

