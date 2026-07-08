package com.habitergy.link.ui.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private const val MATERIAL_SHAPE_VIEWBOX = 380f

/** Same path as MUI `MaterialShapeImage` shape `6-sided-cookie` (RegisterStep1 hero). */
private const val SIX_SIDED_COOKIE_PATH =
    "M134.186 54.5654C165.276 24.4782 214.724 24.4782 245.814 54.5654C255.328 63.7718 266.984 70.4811 279.738 74.0919C321.419 85.8924 346.142 128.586 335.552 170.473C332.312 183.291 332.312 196.709 335.552 209.527C346.142 251.414 321.419 294.108 279.738 305.908C266.984 309.519 255.328 316.228 245.814 325.435C214.724 355.522 165.276 355.522 134.186 325.435C124.672 316.228 113.016 309.519 100.262 305.908C58.5815 294.108 33.8578 251.414 44.4476 209.527C47.6879 196.709 47.6879 183.291 44.4476 170.473C33.8578 128.586 58.5815 85.8924 100.262 74.0919C113.016 70.4811 124.672 63.7718 134.186 54.5654Z"

@Composable
fun MaterialShapeImage(
    @DrawableRes imageRes: Int,
    modifier: Modifier = Modifier,
    size: Dp = 192.dp,
    contentDescription: String? = null,
) {
    val shapePath = remember {
        PathParser().parsePathString(SIX_SIDED_COOKIE_PATH).toPath()
    }

    Image(
        painter = painterResource(imageRes),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier
            .size(size)
            .drawWithContent {
                val scale = this.size.width / MATERIAL_SHAPE_VIEWBOX
                val scaledPath = Path().apply {
                    addPath(shapePath, Matrix().apply { scale(scale, scale) })
                }
                clipPath(scaledPath) {
                    drawContent()
                }
            },
    )
}
