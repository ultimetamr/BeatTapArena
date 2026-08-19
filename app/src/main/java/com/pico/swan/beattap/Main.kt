package com.pico.swan.beattap

import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.pico.swan.beattap.content.BeatTapArenaStage

fun mainApp(scope: SpatialAppScope) = with(scope) {
    DefaultStage {
        PicoTheme { BeatTapArenaStage() }
    }
}

