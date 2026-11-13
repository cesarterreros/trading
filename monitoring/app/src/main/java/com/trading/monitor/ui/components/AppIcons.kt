package com.trading.monitor.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Hand-built 24 dp icons that Material Icons Core does not ship; every icon is a single vector
 * whose colour comes from the tint of the `Icon` composable that draws it.
 */
object AppIcons {

    /** Border brush for the path builders below; the real colour is supplied by `Icon(tint = ...)`. */
    private val Black = SolidColor(Color.Black)

    /** A box with an arrow leaving its top-right corner. */
    val ExternalLink: ImageVector by lazy {
        ImageVector.Builder(
            name = "ExternalLink",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(
            stroke = Black,
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // The box, with a gap where the arrow exits.
            moveTo(18f, 13f)
            verticalLineTo(19f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, 2f)
            horizontalLineTo(5f)
            arcToRelative(2f, 2f, 0f, false, true, -2f, -2f)
            verticalLineTo(8f)
            arcToRelative(2f, 2f, 0f, false, true, 2f, -2f)
            horizontalLineTo(11f)
            // The arrow: an open corner plus the shaft.
            moveTo(15f, 3f)
            horizontalLineTo(21f)
            verticalLineTo(9f)
            moveTo(10f, 14f)
            lineTo(21f, 3f)
        }.build()
    }

    /** A filled badge with a check mark knocked out of it. */
    val Verified: ImageVector by lazy {
        ImageVector.Builder(
            name = "Verified",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(
            fill = Black,
            pathFillType = PathFillType.EvenOdd,
        ) {
            // Badge disc.
            moveTo(3f, 12f)
            arcToRelative(9f, 9f, 0f, false, true, 18f, 0f)
            arcToRelative(9f, 9f, 0f, false, true, -18f, 0f)
            close()
            // Check mark, cut out of the disc by the even-odd fill rule.
            moveTo(6.83f, 13.17f)
            lineTo(10.70f, 17.04f)
            lineTo(17.27f, 10.47f)
            lineTo(15.93f, 9.13f)
            lineTo(10.70f, 14.36f)
            lineTo(8.17f, 11.83f)
            close()
        }.build()
    }

    /** A diagonal arrow pointing up and to the right. */
    val ArrowUpRight: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowUpRight",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(
            stroke = Black,
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(7f, 17f)
            lineTo(17f, 7f)
            moveTo(9f, 7f)
            horizontalLineTo(17f)
            verticalLineTo(15f)
        }.build()
    }

    /** A diagonal arrow pointing down and to the right. */
    val ArrowDownRight: ImageVector by lazy {
        ImageVector.Builder(
            name = "ArrowDownRight",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(
            stroke = Black,
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(7f, 7f)
            lineTo(17f, 17f)
            moveTo(9f, 17f)
            horizontalLineTo(17f)
            verticalLineTo(9f)
        }.build()
    }

    /** A short horizontal line, used for "no change". */
    val Dash: ImageVector by lazy {
        ImageVector.Builder(
            name = "Dash",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).path(
            stroke = Black,
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(8f, 12f)
            lineTo(16f, 12f)
        }.build()
    }
}
