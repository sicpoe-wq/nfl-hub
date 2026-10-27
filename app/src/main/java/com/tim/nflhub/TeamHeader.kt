package com.tim.nflhub

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.lerp
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.TabRowDefaults
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

const val NFL_SHIELD = "https://a.espncdn.com/i/teamlogos/leagues/500/nfl.png"
val NflNavy = Color(0xFF013369)
val NflRed = Color(0xFFD50A0A)

/** Lets JVM screenshot tests supply local logo bitmaps instead of network loads. Null in the app. */
val LocalImageOverride = staticCompositionLocalOf<((String) -> Painter?)?> { null }

@Composable
fun NetImage(url: String, modifier: Modifier, contentScale: ContentScale = ContentScale.Fit) {
    val p = LocalImageOverride.current?.invoke(url)
    if (p != null) Image(p, null, modifier, contentScale = contentScale)
    else if (url.isNotBlank()) AsyncImage(url, null, modifier, contentScale = contentScale)
    else Spacer(modifier)
}

fun hexColor(h: String, fallback: Color): Color = try {
    if (h.length == 6) Color(("FF$h").toLong(16)) else fallback
} catch (e: Exception) { fallback }

/** Gradient stops from ESPN color -> alternateColor, darkening an alternate that's too light for white text. */
fun bannerColors(t: Team?): Pair<Color, Color> {
    val c1 = hexColor(t?.color ?: "", EaglesGreen)
    var c2 = hexColor(t?.altColor ?: "", Color.Black)
    if (c2.luminance() > 0.75f) c2 = lerp(c1, Color.Black, 0.55f)
    else if (c2.luminance() > 0.35f) c2 = lerp(c2, Color.Black, 0.35f)
    return lerp(c1, Color.Black, if (c1.luminance() > 0.45f) 0.3f else 0f) to c2
}

/** Dropdown panel under the Teams box: search field + scrollable list of all teams, filtered live as you type. */
@Composable
fun TeamsDropdown(teamsL: Load<List<Team>>, retry: () -> Unit, currentId: String, onPick: (String) -> Unit, onDismiss: () -> Unit, autoFocus: Boolean = true, initialQuery: String = "", topOffset: Dp = 0.dp) {
    var q by remember { mutableStateOf(initialQuery) }
    val fr = remember { FocusRequester() }
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(topOffset)) // panel opens directly under the banner (where the Teams button sits)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)) {
            Surface(Modifier.align(Alignment.TopEnd).windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))
                .padding(horizontal = 8.dp).widthIn(max = 520.dp).fillMaxWidth().heightIn(max = 480.dp)
                .clickable(remember { MutableInteractionSource() }, null) {}, shape = RoundedCornerShape(bottomStart = 14.dp, bottomEnd = 14.dp),
                shadowElevation = 8.dp, color = Color.White) {
                Column {
                    OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(10.dp).focusRequester(fr), singleLine = true,
                        placeholder = { Text("Search city, team, abbr", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Default.Search, null) },
                        trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Default.Close, "Clear") } })
                    LoadView(teamsL, retry) { teams ->
                        val s = q.trim()
                        val list = if (s.isEmpty()) teams else teams.filter { t ->
                            listOf(t.name, t.location, t.nickname, t.abbr).any { it.contains(s, ignoreCase = true) } }
                        if (list.isEmpty()) Text("No teams match \"$s\"", Modifier.padding(16.dp), color = Color.Gray)
                        LazyColumn {
                            items(list, key = { it.id }) { t ->
                                Row(Modifier.fillMaxWidth().clickable { onPick(t.id) }
                                    .background(if (t.id == currentId) Color(0xFFE8F0F1) else Color.Transparent).padding(16.dp, 8.dp),
                                    verticalAlignment = Alignment.CenterVertically) {
                                    NetImage(t.logo, Modifier.size(34.dp)); Spacer(Modifier.width(12.dp))
                                    Text(t.name, Modifier.weight(1f), fontSize = 16.sp, fontWeight = if (t.id == currentId) FontWeight.Bold else FontWeight.Normal)
                                    Text(t.abbr, fontSize = 12.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (autoFocus) LaunchedEffect(Unit) { try { fr.requestFocus() } catch (_: Exception) {} }
}

/** Fill = ESPN primary color, outline = ESPN alternate color. If the primary is near-black (it would vanish on the
 *  darkened photo) the two are swapped; if both are dark, the outline falls back to white. */
fun wordmarkColors(t: Team?): Pair<Color, Color> {
    var fill = hexColor(t?.color ?: "", EaglesGreen)
    var stroke = hexColor(t?.altColor ?: "", Color.White)
    if (fill.luminance() < 0.02f && stroke.luminance() > 0.1f) { val x = fill; fill = stroke; stroke = x }
    if (stroke.luminance() < 0.08f) stroke = Color.White
    return fill to stroke
}

/** Big wordmark-style text: thick outline pass + fill pass on top, shrunk to fit one line. */
@Composable
fun OutlinedWordmark(text: String, font: FontFamily, fill: Color, stroke: Color, maxSize: Float, modifier: Modifier = Modifier,
                     alignment: Alignment = Alignment.Center) {
    BoxWithConstraints(modifier, contentAlignment = alignment) {
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val maxW = constraints.maxWidth
        val size = remember(text, font, maxW) {
            var s = maxSize
            while (s > 14f && measurer.measure(text, TextStyle(fontFamily = font, fontSize = s.sp, letterSpacing = 1.sp), maxLines = 1, softWrap = false).size.width > maxW) s -= 1f
            s
        }
        val strokePx = with(density) { (size / 9f).coerceIn(2.5f, 6f).dp.toPx() }
        val base = TextStyle(fontFamily = font, fontSize = size.sp, letterSpacing = 1.sp, textAlign = TextAlign.Center, lineHeight = (size * 1.05f).sp)
        Text(text, maxLines = 1, softWrap = false, color = stroke,
            style = base.copy(drawStyle = Stroke(width = strokePx, join = StrokeJoin.Round),
                shadow = Shadow(Color.Black.copy(alpha = 0.85f), offset = Offset(0f, 4f), blurRadius = 10f)))
        Text(text, maxLines = 1, softWrap = false, color = fill, style = base)
    }
}

/** Banner background: the team's bundled photo (darkened) edge-to-edge behind the status bar, plus the accent strip
 *  along the bottom edge. Fills whatever height the (possibly collapsing) banner currently has. */
@Composable
fun TeamBannerBackground(team: Team?, modifier: Modifier = Modifier) {
    val (c1, c2) = bannerColors(team)
    val photo = rememberTeamPhoto(team?.abbr)
    Box(modifier.fillMaxSize().background(Brush.linearGradient(listOf(c1, c2)))) {
        if (photo != null) Image(photo, null, Modifier.matchParentSize(), contentScale = ContentScale.Crop)
        else if (team != null) NetImage(team.logo, Modifier.size(170.dp).align(Alignment.CenterEnd).offset(x = 50.dp).alpha(0.13f))
        // dark scrim: heavier at the top (status bar icons) and bottom (buttons), lighter through the middle
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(
            0f to Color.Black.copy(alpha = 0.72f), 0.35f to Color.Black.copy(alpha = 0.50f),
            0.65f to Color.Black.copy(alpha = 0.55f), 1f to Color.Black.copy(alpha = 0.85f))))
        Box(Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter).background(hexColor(team?.altColor ?: "", Color.White).let {
            if (it.luminance() < 0.08f) Color.White.copy(alpha = 0.35f) else it }))
    }
}

private fun bannerDetail(header: TeamHeader?) =
    listOfNotNull(header?.record?.ifBlank { null }, header?.standing?.ifBlank { null }).joinToString("  ·  ")

private val bannerShadow = TextStyle(shadow = Shadow(Color.Black, blurRadius = 8f))

/**
 * Team banner (portrait, fully expanded): photo background; home (top-left) and settings (top-right) sit BELOW the
 * status bar; big team-colored wordmark; "The League" and "Teams" centered at the very bottom.
 */
@Composable
fun TeamBanner(
    team: Team?, header: TeamHeader?, fallbackName: String,
    showHome: Boolean, teamsOpen: Boolean,
    onLeague: () -> Unit, onTeams: () -> Unit, onHome: () -> Unit, onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    insets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
) {
    Box(modifier.fillMaxWidth()) {
        TeamBannerBackground(team, Modifier.matchParentSize())
        TeamBannerExpanded(team, header, fallbackName, showHome, teamsOpen, onLeague, onTeams, onHome, onSettings, insets)
    }
}

@Composable
fun TeamBannerExpanded(
    team: Team?, header: TeamHeader?, fallbackName: String, showHome: Boolean, teamsOpen: Boolean,
    onLeague: () -> Unit, onTeams: () -> Unit, onHome: () -> Unit, onSettings: () -> Unit, insets: WindowInsets,
) {
    Column(Modifier.fillMaxWidth().windowInsetsPadding(insets), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showHome) RoundIcon(Icons.Default.Home, "Home team", onHome) else Spacer(Modifier.size(40.dp))
            Spacer(Modifier.weight(1f))
            if (team != null) NetImage(team.logo, Modifier.size(40.dp))
            Spacer(Modifier.weight(1f))
            RoundIcon(Icons.Default.Settings, "Settings", onSettings)
        }
        val (fill, stroke) = wordmarkColors(team)
        val font = teamFont(team?.abbr)
        val loc = team?.location?.takeIf { it.isNotBlank() && team.nickname.isNotBlank() }
        val big = (if (loc != null) team.nickname else team?.name ?: fallbackName).uppercase()
        if (loc != null) Text(loc.uppercase(), color = Color.White, fontFamily = font, fontSize = 17.sp, letterSpacing = 2.sp, maxLines = 1,
            style = bannerShadow, modifier = Modifier.padding(top = 2.dp))
        OutlinedWordmark(big, font, fill, stroke, maxSize = 56f, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp))
        Text(bannerDetail(header).ifBlank { " " }, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, style = bannerShadow)
        Spacer(Modifier.height(12.dp))
        BannerButtons(teamsOpen, onLeague, onTeams)
        Spacer(Modifier.height(10.dp))
    }
}

/** Sideways phone: one short row — logo, wordmark + record/division, then The League / Teams and the gear inline. */
@Composable
fun TeamBannerLandscape(
    team: Team?, header: TeamHeader?, fallbackName: String, showHome: Boolean, teamsOpen: Boolean,
    onLeague: () -> Unit, onTeams: () -> Unit, onHome: () -> Unit, onSettings: () -> Unit, insets: WindowInsets,
) {
    val (fill, stroke) = wordmarkColors(team)
    val font = teamFont(team?.abbr)
    val loc = team?.location?.takeIf { it.isNotBlank() && team.nickname.isNotBlank() }
    val big = (if (loc != null) team.nickname else team?.name ?: fallbackName).uppercase()
    Row(Modifier.fillMaxWidth().windowInsetsPadding(insets).padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (showHome) RoundIcon(Icons.Default.Home, "Home team", onHome)
        if (team != null) NetImage(team.logo, Modifier.size(58.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            if (loc != null) Text(loc.uppercase(), color = Color.White, fontFamily = font, fontSize = 13.sp, letterSpacing = 2.sp, maxLines = 1, style = bannerShadow)
            OutlinedWordmark(big, font, fill, stroke, maxSize = 38f, modifier = Modifier.fillMaxWidth(), alignment = Alignment.CenterStart)
            Text(bannerDetail(header).ifBlank { " " }, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, style = bannerShadow, maxLines = 1)
        }
        Spacer(Modifier.width(8.dp))
        BannerButtons(teamsOpen, onLeague, onTeams, width = 132.dp, height = 38.dp)
        Spacer(Modifier.width(4.dp))
        RoundIcon(Icons.Default.Settings, "Settings", onSettings)
    }
}

/** What the banner shrinks to on scroll: a single 50dp bar with logo + name, The League / Teams and the gear. */
@Composable
fun TeamCompactBar(
    team: Team?, fallbackName: String, showHome: Boolean, teamsOpen: Boolean,
    onLeague: () -> Unit, onTeams: () -> Unit, onHome: () -> Unit, onSettings: () -> Unit, insets: WindowInsets,
) {
    val (fill, stroke) = wordmarkColors(team)
    val font = teamFont(team?.abbr)
    BoxWithConstraints(Modifier.fillMaxWidth().windowInsetsPadding(insets)) {
        val wide = maxWidth >= 560.dp
        val name = (if (wide) team?.name else team?.nickname?.ifBlank { null } ?: team?.name)?.uppercase() ?: fallbackName.uppercase()
        Row(Modifier.fillMaxWidth().height(50.dp).padding(start = 4.dp, end = 4.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
            if (showHome) RoundIcon(Icons.Default.Home, "Home team", onHome) else Spacer(Modifier.width(6.dp))
            if (team != null) NetImage(team.logo, Modifier.size(34.dp))
            Spacer(Modifier.width(8.dp))
            OutlinedWordmark(name, font, fill, stroke, maxSize = 26f, modifier = Modifier.weight(1f), alignment = Alignment.CenterStart)
            Spacer(Modifier.width(6.dp))
            if (wide) BannerButtons(teamsOpen, onLeague, onTeams, width = 124.dp, height = 34.dp)
            else CompactBannerButtons(teamsOpen, onLeague, onTeams)
            RoundIcon(Icons.Default.Settings, "Settings", onSettings)
        }
    }
}

/** Team banner that collapses to [TeamCompactBar] as the content scrolls; on a sideways phone the expanded state is
 *  already the short single-row [TeamBannerLandscape]. */
@Composable
fun CollapsingTeamBanner(
    state: CollapseState, short: Boolean,
    team: Team?, header: TeamHeader?, fallbackName: String, showHome: Boolean, teamsOpen: Boolean,
    onLeague: () -> Unit, onTeams: () -> Unit, onHome: () -> Unit, onSettings: () -> Unit,
    modifier: Modifier = Modifier,
    insets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
) {
    CollapsingHeader(state, modifier,
        background = { TeamBannerBackground(team) },
        expanded = {
            if (short) TeamBannerLandscape(team, header, fallbackName, showHome, teamsOpen, onLeague, onTeams, onHome, onSettings, insets)
            else TeamBannerExpanded(team, header, fallbackName, showHome, teamsOpen, onLeague, onTeams, onHome, onSettings, insets)
        },
        compact = { TeamCompactBar(team, fallbackName, showHome, teamsOpen, onLeague, onTeams, onHome, onSettings, insets) })
}

@Composable
private fun RoundIcon(icon: ImageVector, desc: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, Modifier.size(40.dp)) {
        Box(Modifier.size(34.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.35f)), Alignment.Center) {
            Icon(icon, desc, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

val LeagueGradient = listOf(NflNavy, Color(0xFF0B2A5B), NflRed)

@Composable
fun LeagueBannerBackground(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Brush.linearGradient(LeagueGradient))) {
        NetImage(NFL_SHIELD, Modifier.size(190.dp).align(Alignment.CenterEnd).offset(x = 50.dp).alpha(0.12f))
        Box(Modifier.matchParentSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.45f), Color.Transparent, Color.Black.copy(alpha = 0.5f)))))
    }
}

/** League screen banner: same layout as the team banner (inset-aware icons on top, big name, League/Teams at the bottom),
 *  on the NFL navy→red gradient (no photo, so nothing is mislabeled). */
@Composable
fun LeagueBanner(onHome: (() -> Unit)?, onTeams: () -> Unit, onSettings: (() -> Unit)?,
                 insets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)) {
    Box(Modifier.fillMaxWidth()) {
        LeagueBannerBackground(Modifier.matchParentSize())
        LeagueBannerExpanded(onHome, onTeams, onSettings, insets)
    }
}

@Composable
fun LeagueBannerExpanded(onHome: (() -> Unit)?, onTeams: () -> Unit, onSettings: (() -> Unit)?, insets: WindowInsets) {
    Column(Modifier.fillMaxWidth().windowInsetsPadding(insets), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth().height(44.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onHome != null) RoundIcon(Icons.Default.Home, "Back to my team", onHome) else Spacer(Modifier.size(40.dp))
            Spacer(Modifier.weight(1f))
            NetImage(NFL_SHIELD, Modifier.size(40.dp))
            Spacer(Modifier.weight(1f))
            if (onSettings != null) RoundIcon(Icons.Default.Settings, "Settings", onSettings) else Spacer(Modifier.size(40.dp))
        }
        Text("NATIONAL FOOTBALL LEAGUE", color = Color.White, fontFamily = WordmarkFonts.anton, fontSize = 15.sp, letterSpacing = 2.sp, maxLines = 1)
        OutlinedWordmark("THE LEAGUE", WordmarkFonts.anton, Color.White, NflRed, maxSize = 52f, modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp))
        Spacer(Modifier.height(10.dp))
        BannerButtons(false, onLeague = {}, onTeams = onTeams, leagueSelected = true)
        Spacer(Modifier.height(10.dp))
    }
}

@Composable
fun LeagueBannerLandscape(onHome: (() -> Unit)?, onTeams: () -> Unit, onSettings: (() -> Unit)?, insets: WindowInsets) {
    Row(Modifier.fillMaxWidth().windowInsetsPadding(insets).padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically) {
        if (onHome != null) RoundIcon(Icons.Default.Home, "Back to my team", onHome) else Spacer(Modifier.size(40.dp))
        NetImage(NFL_SHIELD, Modifier.size(52.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text("NATIONAL FOOTBALL LEAGUE", color = Color.White, fontFamily = WordmarkFonts.anton, fontSize = 12.sp, letterSpacing = 2.sp, maxLines = 1)
            OutlinedWordmark("THE LEAGUE", WordmarkFonts.anton, Color.White, NflRed, maxSize = 38f, modifier = Modifier.fillMaxWidth(), alignment = Alignment.CenterStart)
        }
        Spacer(Modifier.width(8.dp))
        BannerButtons(false, onLeague = {}, onTeams = onTeams, leagueSelected = true, width = 132.dp, height = 38.dp)
        Spacer(Modifier.width(4.dp))
        if (onSettings != null) RoundIcon(Icons.Default.Settings, "Settings", onSettings) else Spacer(Modifier.size(40.dp))
    }
}

@Composable
fun LeagueCompactBar(onHome: (() -> Unit)?, onTeams: () -> Unit, onSettings: (() -> Unit)?, insets: WindowInsets) {
    BoxWithConstraints(Modifier.fillMaxWidth().windowInsetsPadding(insets)) {
        val wide = maxWidth >= 560.dp
        Row(Modifier.fillMaxWidth().height(50.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onHome != null) RoundIcon(Icons.Default.Home, "Back to my team", onHome) else Spacer(Modifier.size(40.dp))
            NetImage(NFL_SHIELD, Modifier.size(32.dp))
            Spacer(Modifier.width(8.dp))
            OutlinedWordmark("THE LEAGUE", WordmarkFonts.anton, Color.White, NflRed, maxSize = 26f, modifier = Modifier.weight(1f), alignment = Alignment.CenterStart)
            Spacer(Modifier.width(6.dp))
            if (wide) BannerButtons(false, onLeague = {}, onTeams = onTeams, leagueSelected = true, width = 124.dp, height = 34.dp)
            else CompactBannerButtons(false, onLeague = {}, onTeams = onTeams, leagueSelected = true)
            if (onSettings != null) RoundIcon(Icons.Default.Settings, "Settings", onSettings) else Spacer(Modifier.size(40.dp))
        }
    }
}

@Composable
fun CollapsingLeagueBanner(state: CollapseState, short: Boolean, onHome: (() -> Unit)?, onTeams: () -> Unit, onSettings: (() -> Unit)?,
                           insets: WindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)) {
    CollapsingHeader(state,
        background = { LeagueBannerBackground() },
        expanded = { if (short) LeagueBannerLandscape(onHome, onTeams, onSettings, insets) else LeagueBannerExpanded(onHome, onTeams, onSettings, insets) },
        compact = { LeagueCompactBar(onHome, onTeams, onSettings, insets) })
}

val BANNER_BUTTON_W = 150.dp
val BANNER_BUTTON_H = 40.dp

/** "The League" + "Teams": same size, side by side, centered. */
@Composable
fun BannerButtons(teamsOpen: Boolean, onLeague: () -> Unit, onTeams: () -> Unit, leagueSelected: Boolean = false,
                  width: Dp = BANNER_BUTTON_W, height: Dp = BANNER_BUTTON_H) {
    val shape = RoundedCornerShape(height / 2)
    val fs = if (height < 38.dp) 13.sp else 14.sp
    Row(horizontalArrangement = Arrangement.spacedBy(if (width < BANNER_BUTTON_W) 8.dp else 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width, height).clip(shape)
            .background(Brush.linearGradient(listOf(NflNavy, Color(0xFF0B2A5B), NflRed)))
            .border(if (leagueSelected) 2.dp else 1.dp, if (leagueSelected) Color.White else Color.White.copy(alpha = 0.35f), shape).clickable(onClick = onLeague), Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NetImage(NFL_SHIELD, Modifier.size(22.dp)); Spacer(Modifier.width(6.dp))
                Text("The League", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = fs, maxLines = 1)
            }
        }
        Box(Modifier.size(width, height).clip(shape)
            .background(Color.Black.copy(alpha = if (teamsOpen) 0.75f else 0.55f))
            .border(1.dp, if (teamsOpen) Color.White else Color.White.copy(alpha = 0.35f), shape).clickable(onClick = onTeams), Alignment.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Groups, null, tint = Color.White, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(6.dp))
                Text("Teams", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = fs, maxLines = 1)
                Icon(if (teamsOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, "Open team list", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Narrow compact bar: The League / Teams as two equal round icon buttons (shield, team list). */
@Composable
fun CompactBannerButtons(teamsOpen: Boolean, onLeague: () -> Unit, onTeams: () -> Unit, leagueSelected: Boolean = false) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(Brush.linearGradient(listOf(NflNavy, Color(0xFF0B2A5B), NflRed)))
            .border(if (leagueSelected) 2.dp else 1.dp, if (leagueSelected) Color.White else Color.White.copy(alpha = 0.35f), CircleShape)
            .clickable(onClickLabel = "The League", onClick = onLeague), Alignment.Center) {
            NetImage(NFL_SHIELD, Modifier.size(22.dp))
        }
        Box(Modifier.size(36.dp).clip(CircleShape).background(Color.Black.copy(alpha = if (teamsOpen) 0.75f else 0.55f))
            .border(1.dp, if (teamsOpen) Color.White else Color.White.copy(alpha = 0.35f), CircleShape)
            .clickable(onClickLabel = "Teams", onClick = onTeams), Alignment.Center) {
            Icon(Icons.Default.Groups, "Teams", tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

val TEAM_TABS: List<Pair<String, ImageVector>> = listOf(
    "News" to Icons.Default.Newspaper, "Schedule" to Icons.Default.CalendarMonth, "Roster" to Icons.Default.Groups,
    "Stats" to Icons.Default.BarChart, "Injuries" to Icons.Default.LocalHospital
)

val NFL_TABS: List<Pair<String, ImageVector>> = listOf(
    "News" to Icons.Default.Newspaper, "Scores" to Icons.Default.Scoreboard,
    "Standings" to Icons.Default.FormatListNumbered, "Leaders" to Icons.Default.Leaderboard
)

/** Fixed, equal-width tabs (icon + 11sp label) so all five fit on a 360dp-wide phone without scrolling.
 *  [inline] (sideways phone) puts the icon beside the label for a shorter bar. */
@Composable
fun TeamTabs(tab: Int, team: Team?, inline: Boolean = false, onTab: (Int) -> Unit) {
    val (c1, _) = bannerColors(team)
    IconTabs(tab, TEAM_TABS, lerp(c1, Color.Black, 0.25f), onTab, inline = inline)
}

/** League tab bar: the League banner's navy-to-red gradient, darkened the same 25% the team tab bars use. */
val LeagueTabBrush: Brush = Brush.horizontalGradient(
    listOf(NflNavy, Color(0xFF0B2A5B), NflRed).map { lerp(it, Color.Black, 0.25f) })

/** Border for the League tab bar, mirroring the team tab bar's framing (a 3dp accent strip along its top edge where the
 *  banner meets the tabs, plus a thin divider along its bottom edge). Near-black navy reads as black but stays in the
 *  NFL palette and separates cleanly from both the navy and the red ends of the gradient. */
val LeagueBorder = Color(0xFF050B18)

@Composable
fun LeagueTabs(tab: Int, inline: Boolean = false, onTab: (Int) -> Unit) = Column(Modifier.fillMaxWidth()) {
    Box(Modifier.fillMaxWidth().height(3.dp).background(LeagueBorder))
    IconTabs(tab, NFL_TABS, Color.Transparent, onTab, LeagueTabBrush, divider = LeagueBorder, dividerHeight = 2.dp, inline = inline)
}

/** League-only theme: swaps the app's Eagles-green Material accents (chips, buttons, spinners, sheet headings) for NFL navy. */
val LeagueSelectedChip = Color(0xFFDCE4F2)
/** Light navy tint for League-themed panels (score header, team card, totals rows). */
val LeagueTint = Color(0xFFE6ECF5)

@Composable
fun LeagueTheme(content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme.copy(
        primary = NflNavy, onPrimary = Color.White,
        primaryContainer = LeagueSelectedChip, onPrimaryContainer = NflNavy,
        secondaryContainer = LeagueSelectedChip, onSecondaryContainer = NflNavy,
        tertiary = NflRed, surfaceTint = NflNavy,
    )
    MaterialTheme(colorScheme = cs) {
        CompositionLocalProvider(LocalAccent provides NflNavy, LocalAccentTint provides LeagueTint, content = content)
    }
}

@Composable
fun IconTabs(tab: Int, tabs: List<Pair<String, ImageVector>>, bg: Color, onTab: (Int) -> Unit, brush: Brush? = null,
             divider: Color? = null, dividerHeight: androidx.compose.ui.unit.Dp = 1.dp, inline: Boolean = false) {
    // Background spans the full width (under a landscape cutout / side nav bar); the tabs themselves stay inside the safe area.
    Column(Modifier.fillMaxWidth().then(if (brush != null) Modifier.background(brush) else Modifier.background(bg))) {
        TabRow(selectedTabIndex = tab, containerColor = Color.Transparent, contentColor = Color.White,
            modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)),
            divider = {},
            indicator = { pos ->
                if (tab < pos.size) {
                    if (brush != null) TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[tab]), height = 3.dp, color = Color.White)
                    else TabRowDefaults.SecondaryIndicator(Modifier.tabIndicatorOffset(pos[tab]))
                }
            }) {
            tabs.forEachIndexed { i, (label, icon) ->
                Tab(selected = tab == i, onClick = { onTab(i) }, unselectedContentColor = Color.White.copy(alpha = 0.7f)) {
                    val weight = if (tab == i) FontWeight.Bold else FontWeight.Normal
                    if (inline) Row(Modifier.padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text(label, fontSize = 13.sp, maxLines = 1, softWrap = false, fontWeight = weight)
                    } else Column(Modifier.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(icon, null, Modifier.size(20.dp))
                        Text(label, fontSize = 11.sp, maxLines = 1, softWrap = false, fontWeight = weight)
                    }
                }
            }
        }
        if (divider != null) HorizontalDivider(thickness = dividerHeight, color = divider) else HorizontalDivider()
    }
}
