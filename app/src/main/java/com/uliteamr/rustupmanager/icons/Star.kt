package com.uliteamr.rustupmanager.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Star: ImageVector
    get() {
        if (_Star != null) return _Star!!
        _Star = ImageVector.Builder(
            name = "Star",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                lineTo(14.59f, 8.36f)
                lineTo(21.51f, 8.82f)
                lineTo(16.21f, 13.31f)
                lineTo(17.9f, 20.02f)
                lineTo(12f, 16.31f)
                lineTo(6.1f, 20.02f)
                lineTo(7.79f, 13.31f)
                lineTo(2.49f, 8.82f)
                lineTo(9.41f, 8.36f)
                close()
            }
        }.build()
        return _Star!!
    }

@Suppress("ObjectPropertyName")
private var _Star: ImageVector? = null