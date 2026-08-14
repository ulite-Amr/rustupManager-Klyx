package com.uliteamr.rustupmanager.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Info: ImageVector
    get() {
        if (_Info != null) return _Info!!
        _Info = ImageVector.Builder(
            name = "Info",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(12f, 2f)
                curveTo(6.48f, 2f, 2f, 6.48f, 2f, 12f)
                reflectiveCurveToRelative(4.48f, 10f, 10f, 10f)
                reflectiveCurveToRelative(10f, -4.48f, 10f, -10f)
                reflectiveCurveToRelative(-4.48f, -10f, -10f, -10f)
                close()
                moveTo(13f, 17f)
                horizontalLineToRelative(-2f)
                verticalLineToRelative(-6f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(6f)
                close()
                moveTo(13f, 9f)
                horizontalLineToRelative(-2f)
                verticalLineTo(7f)
                horizontalLineToRelative(2f)
                verticalLineToRelative(2f)
                close()
            }
        }.build()
        return _Info!!
    }

@Suppress("ObjectPropertyName")
private var _Info: ImageVector? = null
