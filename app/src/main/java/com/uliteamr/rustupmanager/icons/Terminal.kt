package com.uliteamr.rustupmanager.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Terminal: ImageVector
    get() {
        if (_Terminal != null) return _Terminal!!
        _Terminal = ImageVector.Builder(
            name = "Terminal",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(20f, 4f)
                horizontalLineTo(4f)
                curveTo(2.89f, 4f, 2f, 4.9f, 2f, 6f)
                verticalLineToRelative(12f)
                curveToRelative(1.1f, 0f, 0.89f, 2f, 2f, 2f)
                horizontalLineToRelative(16f)
                curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
                verticalLineTo(6f)
                curveTo(22f, 4.9f, 21.11f, 4f, 20f, 4f)
                close()
                moveTo(20f, 18f)
                horizontalLineTo(4f)
                verticalLineTo(8f)
                horizontalLineToRelative(16f)
                verticalLineToRelative(10f)
                close()
                moveTo(18f, 17f)
                horizontalLineToRelative(-6f)
                verticalLineToRelative(-2f)
                horizontalLineToRelative(6f)
                verticalLineToRelative(2f)
                close()
                moveTo(7.5f, 17f)
                lineToRelative(-1.41f, -1.41f)
                lineTo(8.67f, 13f)
                lineToRelative(-2.59f, -2.59f)
                lineTo(7.5f, 9f)
                lineToRelative(4f, 4f)
                lineToRelative(-4f, 4f)
                close()
            }
        }.build()
        return _Terminal!!
    }

@Suppress("ObjectPropertyName")
private var _Terminal: ImageVector? = null
