package com.uliteamr.rustupmanager.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Layers: ImageVector
    get() {
        if (_Layers != null) return _Layers!!
        _Layers = ImageVector.Builder(
            name = "Layers",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                lineTo(2f, 7f)
                lineTo(12f, 12f)
                lineTo(22f, 7f)
                close()
                moveTo(2f, 12f)
                lineTo(12f, 17f)
                lineTo(22f, 12f)
                lineTo(19.5f, 10.73f)
                lineTo(12f, 14.5f)
                lineTo(4.5f, 10.73f)
                close()
                moveTo(2f, 17f)
                lineTo(12f, 22f)
                lineTo(22f, 17f)
                lineTo(19.5f, 15.73f)
                lineTo(12f, 19.5f)
                lineTo(4.5f, 15.73f)
                close()
            }
        }.build()
        return _Layers!!
    }

@Suppress("ObjectPropertyName")
private var _Layers: ImageVector? = null
