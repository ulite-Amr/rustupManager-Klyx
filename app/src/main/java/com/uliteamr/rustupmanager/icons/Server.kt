package com.uliteamr.rustupmanager.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Server: ImageVector
    get() {
        if (_Server != null) return _Server!!
        _Server = ImageVector.Builder(
            name = "Server",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(4f, 3f)
                lineTo(20f, 3f)
                lineTo(20f, 9f)
                lineTo(4f, 9f)
                close()
                moveTo(4f, 11f)
                lineTo(20f, 11f)
                lineTo(20f, 17f)
                lineTo(4f, 17f)
                close()
                moveTo(4f, 19f)
                lineTo(20f, 19f)
                lineTo(20f, 21f)
                lineTo(4f, 21f)
                close()
            }
            path(fill = SolidColor(Color.White)) {
                moveTo(6f, 5.5f)
                lineTo(9f, 5.5f)
                lineTo(9f, 6.5f)
                lineTo(6f, 6.5f)
                close()
                moveTo(6f, 13.5f)
                lineTo(9f, 13.5f)
                lineTo(9f, 14.5f)
                lineTo(6f, 14.5f)
                close()
            }
        }.build()
        return _Server!!
    }

@Suppress("ObjectPropertyName")
private var _Server: ImageVector? = null
