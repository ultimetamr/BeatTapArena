package com.pico.swan.beattap.content

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.swan.beattap.ui.BeatTapArenaScreen

@Composable
fun BeatTapArenaStage() {
    SpatialView(
        initial = { content, attachments ->
            attachments.entity(id = "arena")?.apply {
                components[TransformComponent::class.java]?.setPosition(Vector3(0f, 1.42f, -1.28f))
                content.addEntity(this)
            }
        },
        attachments = {
            AttachmentPanel(id = "arena") {
                Box(
                    modifier = Modifier.size(1180.dp, 720.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BeatTapArenaScreen()
                }
            }
        },
    )
}

