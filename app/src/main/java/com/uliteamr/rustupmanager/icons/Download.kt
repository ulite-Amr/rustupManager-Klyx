package com.uliteamr.rustupmanager.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Download: ImageVector
    get() {
        if (_Download != null) return _Download!!
        _Download = ImageVector.Builder(
            name = "Download",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(5f, 20f)
                horizontalLineToRelative(14f)
                verticalLineToRelative(-2f)
                horizontalLineTo(5f)
                verticalLineTo(20f)
                close()
                moveTo(19f, 9f)
                horizontalLineToRelative(-4f)
                verticalLineTo(3f)
                horizontalLineTo(9f)
                verticalLineToRelative(6f)
                horizontalLineTo(5f)
                lineToRelative(7f, 7f)
                lineToRelative(7f, -7f)
                close()
            }
        }.build()
        return _Download!!
    }

@Suppress("ObjectPropertyName")
private var _Download: ImageVector? = null
