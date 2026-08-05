package com.uliteamr.rustupmanager.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Wrench: ImageVector
    get() {
        if (_Wrench != null) return _Wrench!!
        _Wrench = ImageVector.Builder(
            name = "Wrench",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(10f, 4f)
                lineTo(14f, 4f)
                lineTo(14f, 10f)
                lineTo(20f, 10f)
                lineTo(20f, 14f)
                lineTo(14f, 14f)
                lineTo(14f, 20f)
                lineTo(10f, 20f)
                lineTo(10f, 14f)
                lineTo(4f, 14f)
                lineTo(4f, 10f)
                lineTo(10f, 10f)
                close()
            }
        }.build()
        return _Wrench!!
    }

@Suppress("ObjectPropertyName")
private var _Wrench: ImageVector? = null
