package com.umutk.pulse.extras.music.cassette

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.res.imageResource
import com.umutk.pulse.R

/**
 * Real material photographs (ambientCG, CC0 / public domain) that the skins lay under their drawing: walnut wood, brushed steel, white plastic and leather.
 * Each one repeats seamlessly, so any panel can be filled with it. See THIRD_PARTY.md.
 */
enum class Tex(val res: Int) { WALNUT(R.drawable.tex_walnut), STEEL(R.drawable.tex_steel), PLASTIC(R.drawable.tex_plastic), LEATHER(R.drawable.tex_leather) }

@Composable
fun rememberTexture(t: Tex): Brush {
    val img = androidx.compose.ui.graphics.ImageBitmap.imageResource(t.res)
    return remember(t) { ShaderBrush(ImageShader(img, TileMode.Repeated, TileMode.Repeated)) }
}
