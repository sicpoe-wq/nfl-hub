package com.tim.nflhub

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

/**
 * Free OFL Google Fonts bundled in res/font, used as rough lookalikes for each club's wordmark style.
 * These are NOT the official team typefaces (those are proprietary); they're the closest free style match.
 */
object WordmarkFonts {
    val bebas = FontFamily(Font(R.font.bebas_neue))                 // tall condensed sans
    val graduate = FontFamily(Font(R.font.graduate))                // collegiate / varsity block
    val alfaSlab = FontFamily(Font(R.font.alfa_slab_one))           // heavy slab serif
    val russo = FontFamily(Font(R.font.russo_one))                  // squared, modern
    val racing = FontFamily(Font(R.font.racing_sans_one))           // forward-leaning italic sport
    val archivo = FontFamily(Font(R.font.archivo_black))            // heavy grotesque sans
    val anton = FontFamily(Font(R.font.anton))                      // heavy condensed block
    val playfair = FontFamily(Font(R.font.playfair_display_sc_black)) // flared classic serif caps
}

/** Team abbreviation (ESPN) -> wordmark lookalike font. Keep in sync with team_photos/README / report. */
val TEAM_FONT: Map<String, FontFamily> by lazy {
    with(WordmarkFonts) {
        mapOf(
            "ARI" to racing, "ATL" to russo, "BAL" to playfair, "BUF" to racing,
            "CAR" to russo, "CHI" to alfaSlab, "CIN" to archivo, "CLE" to anton,
            "DAL" to archivo, "DEN" to racing, "DET" to archivo, "GB" to graduate,
            "HOU" to alfaSlab, "IND" to playfair, "JAX" to russo, "KC" to racing,
            "LV" to alfaSlab, "LAC" to racing, "LAR" to bebas, "MIA" to russo,
            "MIN" to playfair, "NE" to russo, "NO" to playfair, "NYG" to graduate,
            "NYJ" to archivo, "PHI" to playfair, "PIT" to anton, "SF" to racing,
            "SEA" to russo, "TB" to bebas, "TEN" to russo, "WSH" to graduate,
        )
    }
}

fun teamFont(abbr: String?): FontFamily = TEAM_FONT[abbr?.uppercase()] ?: WordmarkFonts.anton

/** Bundled banner photo for a team (assets/team_photos/<abbr>.jpg; provenance in team_photos/manifest.json). */
val LocalTeamPhoto = staticCompositionLocalOf<((String) -> ImageBitmap?)?> { null }

/** Small LRU (decoded photos are ~4 MB each) so flipping through all 32 teams doesn't pile up memory. */
private val photoCache = object : LinkedHashMap<String, ImageBitmap?>(8, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap?>?) = size > 4
}

fun loadTeamPhoto(ctx: Context, abbr: String): ImageBitmap? = synchronized(photoCache) {
    photoCache.getOrPut(abbr.lowercase()) {
        try {
            ctx.assets.open("team_photos/${abbr.lowercase()}.jpg").use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
        } catch (e: Exception) { null }
    }
}

@Composable
fun rememberTeamPhoto(abbr: String?): ImageBitmap? {
    if (abbr.isNullOrBlank()) return null
    val override = LocalTeamPhoto.current
    val ctx = LocalContext.current
    return remember(abbr, override) { override?.invoke(abbr) ?: loadTeamPhoto(ctx, abbr) }
}
