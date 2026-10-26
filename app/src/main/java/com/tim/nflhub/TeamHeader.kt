package com.tim.nflhub

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.lerp
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
/** Height of the League/Teams box row; the Teams dropdown opens right below it. */
val TOP_BOXES_HEIGHT = 76.dp

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

@Composable
fun TopBoxes(team: Team?, showHome: Boolean, teamsOpen: Boolean, onLeague: () -> Unit, onTeams: () -> Unit, onHome: () -> Unit, onSettings: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(TOP_BOXES_HEIGHT).background(Color(0xFF0E1726)).padding(start = 8.dp, top = 8.dp, bottom = 8.dp, end = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        // LEFT: The League
        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(NflNavy, Color(0xFF0B2A5B), NflRed)))
            .clickable(onClick = onLeague)) {
            // stripes + big faint shield behind the words
            Row(Modifier.fillMaxSize()) { repeat(6) { i -> Box(Modifier.weight(1f).fillMaxHeight().background(if (i % 2 == 0) Color.White.copy(alpha = 0.04f) else Color.Transparent)) } }
            NetImage(NFL_SHIELD, Modifier.size(78.dp).align(Alignment.CenterEnd).offset(x = 14.dp).alpha(0.30f))
            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                NetImage(NFL_SHIELD, Modifier.size(32.dp)); Spacer(Modifier.width(8.dp))
                Column {
                    Text("The League", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1,
                        style = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.5f), blurRadius = 4f)))
                    Text("Scores & stats", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        // RIGHT: Teams dropdown
        val (c1, c2) = bannerColors(team)
        Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF263241), lerp(c1, Color(0xFF263241), 0.35f))))
            .border(1.dp, if (teamsOpen) Color.White else Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
            .clickable(onClick = onTeams)) {
            Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Groups, null, tint = Color.White, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text("Teams", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1)
                    Text("All 32 teams", color = Color.White.copy(alpha = 0.85f), fontSize = 10.sp, maxLines = 1)
                }
                Icon(if (teamsOpen) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, "Open team list", tint = Color.White)
            }
        }
        Column(Modifier.width(36.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
            IconButton(onClick = onSettings, Modifier.size(30.dp)) { Icon(Icons.Default.Settings, "Settings", tint = Color.White) }
            if (showHome) IconButton(onClick = onHome, Modifier.size(30.dp)) { Icon(Icons.Default.Home, "Home team", tint = Color.White) }
        }
    }
}

/** Dropdown panel under the Teams box: search field + scrollable list of all teams, filtered live as you type. */
@Composable
fun TeamsDropdown(teamsL: Load<List<Team>>, retry: () -> Unit, currentId: String, onPick: (String) -> Unit, onDismiss: () -> Unit, autoFocus: Boolean = true, initialQuery: String = "") {
    var q by remember { mutableStateOf(initialQuery) }
    val fr = remember { FocusRequester() }
    Column(Modifier.fillMaxSize()) {
        Spacer(Modifier.height(TOP_BOXES_HEIGHT))
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f))
            .clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)) {
            Surface(Modifier.fillMaxWidth().padding(horizontal = 8.dp).heightIn(max = 480.dp)
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

@Composable
fun TeamBanner(team: Team?, header: TeamHeader?, fallbackName: String) {
    val (c1, c2) = bannerColors(team)
    val shadow = TextStyle(shadow = Shadow(Color.Black.copy(alpha = 0.55f), blurRadius = 6f))
    Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(c1, c2)))) {
        if (team != null) NetImage(team.logo, Modifier.size(170.dp).align(Alignment.CenterEnd).offset(x = 50.dp).alpha(0.13f))
        if (team != null) NetImage(team.logo, Modifier.size(120.dp).align(Alignment.CenterStart).offset(x = (-50).dp).alpha(0.08f))
        Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            if (team != null) NetImage(team.logo, Modifier.size(76.dp))
            Text(team?.name ?: fallbackName, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center,
                style = shadow, modifier = Modifier.padding(top = 4.dp, start = 12.dp, end = 12.dp))
            val detail = listOfNotNull(header?.record?.ifBlank { null }, header?.standing?.ifBlank { null }).joinToString("  ·  ")
            Text(detail.ifBlank { " " }, color = Color.White.copy(alpha = 0.85f), fontSize = 13.sp, fontWeight = FontWeight.Medium, style = shadow)
        }
        Box(Modifier.fillMaxWidth().height(3.dp).align(Alignment.BottomCenter).background(hexColor(team?.altColor ?: "", Color.White).let {
            if (it.luminance() < 0.08f) Color.White.copy(alpha = 0.35f) else it }))
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

/** Fixed, equal-width tabs (icon + 11sp label) so all five fit on a 360dp-wide phone without scrolling. */
@Composable
fun TeamTabs(tab: Int, team: Team?, onTab: (Int) -> Unit) {
    val (c1, _) = bannerColors(team)
    IconTabs(tab, TEAM_TABS, lerp(c1, Color.Black, 0.25f), onTab)
}

@Composable
fun IconTabs(tab: Int, tabs: List<Pair<String, ImageVector>>, bg: Color, onTab: (Int) -> Unit) {
    TabRow(selectedTabIndex = tab, containerColor = bg, contentColor = Color.White) {
        tabs.forEachIndexed { i, (label, icon) ->
            Tab(selected = tab == i, onClick = { onTab(i) }, unselectedContentColor = Color.White.copy(alpha = 0.7f)) {
                Column(Modifier.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(icon, null, Modifier.size(20.dp))
                    Text(label, fontSize = 11.sp, maxLines = 1, softWrap = false, fontWeight = if (tab == i) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}
