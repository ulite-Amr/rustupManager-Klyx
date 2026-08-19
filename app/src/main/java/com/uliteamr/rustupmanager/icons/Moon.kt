package com.uliteamr.rustupmanager.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

val Moon: ImageVector
    get() {
        if (_Moon != null) return _Moon!!
        _Moon = ImageVector.Builder(
            name = "Moon",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(fill = SolidColor(Color.Black)) {
                moveTo(21f, 12.79f)
                curveTo(20.84f, 14.51f, 20.13f, 16.15f, 18.95f, 17.44f)
                curveTo(17.78f, 18.73f, 16.21f, 19.6f, 14.49f, 19.93f)
                curveTo(12.77f, 20.25f, 10.99f, 20.01f, 9.42f, 19.24f)
                curveTo(7.86f, 18.47f, 6.6f, 17.21f, 5.83f, 15.65f)
                curveTo(5.06f, 14.08f, 4.82f, 12.3f, 5.14f, 10.58f)
                curveTo(5.47f, 8.86f, 6.34f, 7.29f, 7.63f, 6.12f)
                curveTo(8.92f, 4.94f, 10.56f, 4.23f, 12.28f, 4.07f)
                curveTo(11.5f, 5.12f, 11.08f, 6.4f, 11.08f, 7.71f)
                curveTo(11.08f, 11.15f, 13.87f, 13.94f, 17.31f, 13.94f)
                curveTo(18.62f, 13.94f, 19.9f, 13.52f, 20.95f, 12.74f)
                curveTo(20.97f, 12.76f, 20.98f, 12.77f, 21f, 12.79f)
                close()
            }
        }.build()
        return _Moon!!
    }

@Suppress("ObjectPropertyName")
private var _Moon: ImageVector? = null