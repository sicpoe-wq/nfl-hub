package com.tim.nflhub

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import android.content.Intent
import android.net.Uri
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

val EaglesGreen = Color(0xFF004C54)
val EaglesSilver = Color(0xFFA5ACAF)
const val EAGLES_ID = "21"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = EaglesGreen, secondary = EaglesSilver)) {
                App()
            }
        }
    }
}

sealed class Load<out T> {
    object Loading : Load<Nothing>()
    data class Err(val msg: String) : Load<Nothing>()
    data class Ok<T>(val v: T) : Load<T>()
}

@Composable
fun <T> rememberLoad(key: Any?, block: suspend () -> T): Pair<Load<T>, () -> Unit> {
    var state by remember(key) { mutableStateOf<Load<T>>(Load.Loading) }
    var tick by remember(key) { mutableIntStateOf(0) }
    LaunchedEffect(key, tick) {
        state = Load.Loading
        state = try { Load.Ok(block()) } catch (e: Exception) { Load.Err(e.message ?: e.javaClass.simpleName) }
    }
    return state to { tick++ }
}

@Composable
fun <T> LoadView(l: Load<T>, retry: () -> Unit, content: @Composable (T) -> Unit) {
    when (l) {
        is Load.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        is Load.Err -> Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
            Text("Couldn't load data: ${l.msg}")
            Spacer(Modifier.height(12.dp))
            Button(onClick = retry) { Text("Retry") }
        }
        is Load.Ok -> content(l.v)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    val ctx = LocalContext.current
    var defaultTeamId by remember { mutableStateOf(Prefs.defaultTeam(ctx)) }
    var teamId by remember { mutableStateOf(defaultTeamId) }
    var showSettings by remember { mutableStateOf(false) }
    var rosterOrder by remember { mutableStateOf(Prefs.rosterOrder(ctx)) }
    var showPicker by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Player?>(null) }
    var nflTab by remember { mutableStateOf<Int?>(null) }
    var openGame by remember { mutableStateOf<Pair<String, String>?>(null) }
    val (teamsL, retryTeams) = rememberLoad("teams") { Api.teams() }
    val team = (teamsL as? Load.Ok)?.v?.firstOrNull { it.id == teamId }
    val title = team?.name ?: if (teamId == EAGLES_ID) "Philadelphia Eagles" else "NFL Hub"
    if (showSettings) {
        androidx.activity.compose.BackHandler { showSettings = false }
        SettingsScreen(teamsL, retryTeams, { showSettings = false }, { rosterOrder = it }, { defaultTeamId = it })
        return
    }
    openGame?.let { (id, wk) ->
        androidx.activity.compose.BackHandler { openGame = null }
        GameScreen(id, wk, onBack = { openGame = null }, onTeam = { tid -> teamId = tid; tab = 0; openGame = null; nflTab = null })
        return
    }
    nflTab?.let { nt ->
        androidx.activity.compose.BackHandler { nflTab = null }
        NflScreen(nt, { nflTab = it }, onBack = { nflTab = null }, onTeam = { teamId = it; tab = 0; nflTab = null }, onGame = { id, wk -> openGame = id to wk })
        return
    }

    Scaffold(topBar = {
        Column {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (team != null) AsyncImage(team.logo, null, Modifier.size(32.dp))
                        Spacer(Modifier.width(8.dp)); Text(title, fontWeight = FontWeight.Bold)
                    }
                },
                actions = {
                    if (teamId != defaultTeamId) TextButton(onClick = { teamId = defaultTeamId }) { Text("🏠 Home", color = Color.White) }
                    TextButton(onClick = { showPicker = true }) { Text("Teams ▾", color = Color.White) }
                    IconButton(onClick = { showSettings = true }) { Icon(Icons.Default.Settings, "Settings", tint = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EaglesGreen, titleContentColor = Color.White)
            )
            ScrollableTabRow(selectedTabIndex = tab, containerColor = EaglesGreen, contentColor = Color.White, edgePadding = 0.dp) {
                listOf("News", "Schedule", "Roster", "Stats", "Injuries").forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            key(teamId) {
                when (tab) {
                    0 -> NewsTab(teamId, team?.nickname ?: "")
                    1 -> ScheduleTab(teamId) { g -> openGame = g.id to scheduleWeekLabel(g) }
                    2 -> RosterTab(teamId, rosterOrder) { selected = it }
                    3 -> StatsTab(teamId)
                    else -> InjuriesTab(teamId) { i -> selected = Player(i.playerId, i.name, i.jersey, i.pos, "", "", "", "", "", i.headshot, i.status, "") }
                }
            }
        }
    }

    if (showPicker) {
        ModalBottomSheet(onDismissRequest = { showPicker = false }) {
            LoadView(teamsL, retryTeams) { teams ->
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().clickable { showPicker = false; nflTab = 0 }.background(Color(0xFFE8F0F1)).padding(16.dp, 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage("https://a.espncdn.com/i/teamlogos/leagues/500/nfl.png", null, Modifier.size(36.dp)); Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("NFL", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = EaglesGreen)
                                Text("League news, weekly scores & standings", fontSize = 12.sp, color = Color.Gray)
                            }
                            Text("›", fontSize = 24.sp, color = EaglesGreen)
                        }
                        HorizontalDivider()
                        Row(
                            Modifier.fillMaxWidth().clickable { showPicker = false; nflTab = 2 }.background(Color(0xFFE8F0F1)).padding(16.dp, 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(36.dp), Alignment.Center) { Text("🏆", fontSize = 24.sp) }; Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Standings", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = EaglesGreen)
                                Text("Division standings & playoff picture", fontSize = 12.sp, color = Color.Gray)
                            }
                            Text("›", fontSize = 24.sp, color = EaglesGreen)
                        }
                        HorizontalDivider()
                    }
                    items(teams) { t ->
                        Row(
                            Modifier.fillMaxWidth().clickable { teamId = t.id; showPicker = false }.padding(16.dp, 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(t.logo, null, Modifier.size(36.dp)); Spacer(Modifier.width(14.dp))
                            Text(t.name, fontSize = 17.sp, fontWeight = if (t.id == teamId) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }

    selected?.let { p ->
        ModalBottomSheet(onDismissRequest = { selected = null }) { PlayerSheet(p) }
    }
}

@Composable
fun statusColor(s: String): Color = when {
    s.equals("Out", true) || s.contains("Reserve", true) -> Color(0xFFC62828)
    s.equals("Doubtful", true) -> Color(0xFFE65100)
    s.equals("Questionable", true) -> Color(0xFFF9A825)
    s.isBlank() -> Color.Transparent
    else -> Color(0xFF757575)
}

@Composable
fun Headshot(url: String, size: Int = 48) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(Color(0xFFE0E0E0))) {
        if (url.isNotBlank()) AsyncImage(url, null, Modifier.fillMaxSize())
    }
}

@Composable
fun StatusChip(s: String) {
    if (s.isBlank() || s.equals("Active", true)) return
    Text(s, color = Color.White, fontSize = 11.sp, modifier = Modifier.clip(CircleShape).background(statusColor(s)).padding(8.dp, 2.dp))
}

@Composable
fun RosterTab(teamId: String, posOrder: List<String>, onPlayer: (Player) -> Unit) {
    val (l, retry) = rememberLoad(teamId) { Api.rosterWithInjuries(teamId) }
    LoadView(l, retry) { players ->
        val order = listOf("offense", "defense", "specialTeam", "practiceSquad", "injured", "suspended")
        val names = mapOf("offense" to "Offense", "defense" to "Defense", "specialTeam" to "Special Teams",
            "injured" to "Injured / Out", "practiceSquad" to "Practice Squad", "suspended" to "Suspended")
        val rank = posOrder.withIndex().associate { it.value to it.index }
        fun rankOf(p: Player) = Positions.keyForAbbr(p.pos)?.let { rank[it] } ?: Int.MAX_VALUE
        fun sevOf(s: String) = when { s.equals("Out", true) -> 0; s.contains("Reserve", true) -> 1; s.equals("Doubtful", true) -> 2
            s.equals("Questionable", true) -> 3; else -> 4 }
        val grouped = players.groupBy { it.group }.mapValues { (_, l) -> l.sortedWith(compareBy<Player> { if (it.group == "injured") sevOf(it.status) else 0 }.thenBy { rankOf(it) }.thenBy { it.pos }.thenBy { it.jersey.toIntOrNull() ?: 999 }) }
        LazyColumn(Modifier.fillMaxSize()) {
            order.forEach { g ->
                val list = grouped[g] ?: return@forEach
                item { Text("${names[g] ?: g} (${list.size})", Modifier.fillMaxWidth().background(Color(0xFFEEEEEE)).padding(16.dp, 8.dp), fontWeight = FontWeight.Bold, color = EaglesGreen) }
                items(list) { p ->
                    Row(Modifier.fillMaxWidth().clickable { onPlayer(p) }.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Headshot(p.headshot)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.SemiBold)
                            Text(listOf(p.pos, p.height, p.weight, if (p.age.isNotBlank()) "Age ${p.age}" else "", p.college).filter { it.isNotBlank() }.joinToString(" · "),
                                fontSize = 12.sp, color = Color.Gray)
                        }
                        StatusChip(p.status)
                        if (p.jersey.isNotBlank()) Text("#${p.jersey}", Modifier.padding(start = 10.dp), fontWeight = FontWeight.Bold, color = EaglesGreen)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun StatsTab(teamId: String) {
    val (l, retry) = rememberLoad(teamId) { Api.teamStats(teamId) }
    LoadView(l, retry) { cats ->
        if (cats.isEmpty()) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No team stats available") }; return@LoadView }
        LazyColumn(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            item { Text("Team season stats · tap a player on the Roster tab for player stats", Modifier.padding(vertical = 10.dp), fontSize = 12.sp, color = Color.Gray) }
            cats.forEach { c ->
                item { Text(c.name, Modifier.padding(top = 14.dp, bottom = 4.dp), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = EaglesGreen) }
                items(c.rows) { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                        Text(k, Modifier.weight(1f), fontSize = 14.sp); Text(v, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                    HorizontalDivider(color = Color(0xFFEEEEEE))
                }
            }
        }
    }
}

@Composable
fun InjuriesTab(teamId: String, onPlayer: (Injury) -> Unit) {
    val (l, retry) = rememberLoad(teamId) { Api.injuries(teamId, force = true) }
    LoadView(l, retry) { inj ->
        if (inj.isEmpty()) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No injuries reported") }; return@LoadView }
        LazyColumn(Modifier.fillMaxSize()) {
            items(inj) { i ->
                Column(Modifier.fillMaxWidth().clickable { onPlayer(i) }.padding(16.dp, 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Headshot(i.headshot, 44); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(i.name, fontWeight = FontWeight.SemiBold)
                            Text(listOf(i.pos, i.type, i.date).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 12.sp, color = Color.Gray)
                        }
                        StatusChip(i.status)
                    }
                    if (i.comment.isNotBlank()) Text(i.comment, Modifier.padding(top = 6.dp), fontSize = 13.sp)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun PlayerSheet(p: Player) {
    val (l, retry) = rememberLoad(p.id) { Api.playerStats(p.id) }
    var inj by remember(p.id) { mutableStateOf<InjuryDetail?>(null) }
    var injLoaded by remember(p.id) { mutableStateOf(false) }
    LaunchedEffect(p.id) { inj = Api.injuryDetail(p.id); injLoaded = true }
    val injured = p.status.isNotBlank() && !p.status.equals("Active", true) && !p.status.equals("Practice Squad", true)
    Column(Modifier.fillMaxWidth().heightIn(max = 620.dp).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Headshot(p.headshot, 56); Spacer(Modifier.width(12.dp))
            Column {
                Text(p.name, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(listOf(p.pos, if (p.jersey.isNotBlank()) "#${p.jersey}" else "", p.height, p.weight).filter { it.isNotBlank() }.joinToString(" · "), color = Color.Gray, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f, fill = false)) {
            LoadView(l, retry) { ps ->
                LazyColumn {
                    if (injured || (inj?.status?.let { !it.equals("Active", true) } == true)) item { InjuryCard(p, inj, injLoaded) }
                    item { Text("Player stats", Modifier.padding(top = 12.dp), fontWeight = FontWeight.Bold, fontSize = 17.sp) }
                    if (ps.tables.isEmpty()) item { Text("No stats available for this player.", Modifier.padding(vertical = 12.dp)) }
                    items(ps.tables) { (name, t) ->
                        Text(name, Modifier.padding(top = 12.dp, bottom = 4.dp), fontWeight = FontWeight.Bold, color = EaglesGreen)
                        Column(Modifier.horizontalScroll(rememberScrollState())) {
                            Row { Cell("Season", 110, true); t.labels.forEach { Cell(it, 52, true) } }
                            t.rows.forEach { (s, v) ->
                                Row(Modifier.background(if (s == "Career") Color(0xFFE8F0F1) else Color.Transparent)) {
                                    Cell(s, 110, s == "Career"); v.forEach { Cell(it, 52, s == "Career") }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Cell(t: String, w: Int, bold: Boolean) =
    Text(t, Modifier.width(w.dp).padding(vertical = 3.dp), fontSize = 12.sp, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal, maxLines = 1)

fun niceDate(iso: String): String = try {
    val t = parseEspnInstant(iso) ?: throw IllegalArgumentException()
    java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a").withZone(java.time.ZoneId.systemDefault()).format(t)
} catch (e: Exception) { iso.take(10) }

@Composable
fun NewsTab(teamId: String, nickname: String) {
    val (l, retry) = rememberLoad(teamId to nickname) { Api.news(teamId, nickname) }
    LoadView(l, retry) { items ->
        if (items.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(24.dp), Arrangement.Center, Alignment.CenterHorizontally) {
                Text("No team-specific news right now", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("Check NFL News in the Teams menu for league stories.", fontSize = 13.sp, color = Color.Gray, modifier = Modifier.padding(top = 6.dp))
                Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = retry) { Text("Refresh") }
            }
            return@LoadView
        }
        NewsList(items)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NflScreen(startTab: Int, onTabChange: (Int) -> Unit, onBack: () -> Unit, onTeam: (String) -> Unit, onGame: (String, String) -> Unit) {
    val (l, retry) = rememberLoad("league-news") { Api.leagueNews() }
    val tab = startTab
    Scaffold(topBar = {
        Column {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage("https://a.espncdn.com/i/teamlogos/leagues/500/nfl.png", null, Modifier.size(32.dp))
                        Spacer(Modifier.width(8.dp)); Text("NFL", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ Back", color = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EaglesGreen, titleContentColor = Color.White)
            )
            TabRow(selectedTabIndex = tab, containerColor = EaglesGreen, contentColor = Color.White) {
                listOf("News", "Scores", "Standings").forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { onTabChange(i) }, text = { Text(t) }) }
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> LoadView(l, retry) { items ->
                    if (items.isEmpty()) Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No news available") } else NewsList(items)
                }
                1 -> ScoresTab(onGame)
                else -> StandingsBody(onTeam)
            }
        }
    }
}

@Composable
fun NewsList(items: List<NewsItem>) {
    val ctx = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items(items) { n ->
            Card(Modifier.fillMaxWidth().clickable {
                try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(n.link)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
            }, colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(2.dp)) {
                Column {
                    if (n.image.isNotBlank()) AsyncImage(n.image, null, Modifier.fillMaxWidth().aspectRatio(16f / 9f), contentScale = ContentScale.Crop)
                    Column(Modifier.padding(12.dp)) {
                        Text(n.headline, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        if (n.description.isNotBlank()) Text(n.description, Modifier.padding(top = 4.dp), fontSize = 13.sp, color = Color.DarkGray, maxLines = 3)
                        Text(niceDate(n.published), Modifier.padding(top = 6.dp), fontSize = 12.sp, color = Color.Gray)
                    }
                }
            }
        }
    }
}

/** ESPN dates look like 2026-09-13T20:25Z (no seconds); java.time.Instant.parse needs seconds on older Android. */
fun parseEspnInstant(iso: String): java.time.Instant? = try {
    java.time.OffsetDateTime.parse(iso, java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant()
} catch (e: Exception) {
    try { java.time.Instant.parse(iso) } catch (e2: Exception) {
        try { java.time.Instant.parse(iso.removeSuffix("Z") + ":00Z") } catch (e3: Exception) { null }
    }
}

@Composable
fun ScheduleTab(teamId: String, onGame: (Game) -> Unit = {}) {
    val (l, retry) = rememberLoad(teamId) { Api.schedule(teamId) }
    LoadView(l, retry) { sch ->
        if (sch.games.isEmpty()) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No schedule available") }; return@LoadView }
        val zone = java.time.ZoneId.systemDefault()
        // Build rows: games grouped by season type, with a bye row inserted in the regular season.
        val sections = listOf(1 to "Preseason", 2 to "Regular Season", 3 to "Postseason")
        LazyColumn(Modifier.fillMaxSize()) {
            item {
                Text("${sch.seasonYear} schedule · times in your time zone (${java.time.ZonedDateTime.now(zone).format(java.time.format.DateTimeFormatter.ofPattern("zzz"))})",
                    Modifier.padding(16.dp, 10.dp), fontSize = 12.sp, color = Color.Gray)
            }
            sections.forEach { (type, label) ->
                val games = sch.games.filter { it.seasonType == type }
                val showBye = type == 2 && sch.byeWeek > 0
                if (games.isEmpty() && !showBye) return@forEach
                item { Text(label, Modifier.fillMaxWidth().background(Color(0xFFEEEEEE)).padding(16.dp, 8.dp), fontWeight = FontWeight.Bold, color = EaglesGreen) }
                val rows: List<Any> = if (showBye && games.none { it.weekNum == sch.byeWeek }) {
                    val before = games.filter { it.weekNum < sch.byeWeek }; val after = games.filter { it.weekNum > sch.byeWeek }
                    before + listOf("BYE") + after
                } else games
                items(rows) { r ->
                    if (r is Game) GameRow(r, zone, onGame) else ByeRow(sch.byeWeek)
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
fun ByeRow(week: Int) {
    Row(Modifier.fillMaxWidth().background(Color(0xFFFAFAFA)).padding(16.dp, 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Week $week", Modifier.width(64.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
        Text("BYE WEEK", fontWeight = FontWeight.Bold, color = Color.Gray, letterSpacing = 2.sp)
    }
}

@Composable
fun GameRow(g: Game, zone: java.time.ZoneId, onGame: (Game) -> Unit = {}) {
    val tappable = g.state == "post" || g.state == "in"
    val inst = parseEspnInstant(g.date)
    val zdt = inst?.atZone(zone)
    val dateStr = zdt?.format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d")) ?: g.date.take(10)
    val timeStr = when {
        g.state == "post" -> ""
        !g.timeValid || zdt == null -> "Time TBD"
        else -> zdt.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a zzz"))
    }
    val wk = when {
        g.seasonType == 1 -> "Pre ${g.weekNum}"
        g.seasonType == 3 -> g.weekText.ifBlank { "Playoffs" }
        else -> "Week ${g.weekNum}"
    }
    Row(Modifier.fillMaxWidth().then(if (tappable) Modifier.clickable { onGame(g) } else Modifier).padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(wk, Modifier.width(64.dp), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EaglesGreen, maxLines = 2)
        Text(if (g.home) "vs" else "@", Modifier.width(24.dp), fontSize = 13.sp, color = Color.Gray, fontWeight = FontWeight.SemiBold)
        if (g.oppLogo.isNotBlank()) AsyncImage(g.oppLogo, null, Modifier.size(32.dp)) else Spacer(Modifier.size(32.dp))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(g.oppName, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
            Text(listOf(dateStr, timeStr).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 12.sp, color = Color.DarkGray)
            val extra = listOf(g.tv.takeIf { it.isNotBlank() }?.let { "📺 $it" } ?: "",
                listOf(g.venue, g.venueCity).filter { it.isNotBlank() }.joinToString(", ")).filter { it.isNotBlank() }.joinToString(" · ")
            if (extra.isNotBlank()) Text(extra, fontSize = 11.sp, color = Color.Gray, maxLines = 2)
        }
        when {
            g.result.isNotBlank() -> Column(horizontalAlignment = Alignment.End) {
                val c = when (g.result) { "W" -> Color(0xFF2E7D32); "L" -> Color(0xFFC62828); else -> Color(0xFF757575) }
                Text(g.result, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp,
                    modifier = Modifier.clip(CircleShape).background(c).padding(8.dp, 2.dp))
                Text("${g.myScore}-${g.oppScore}", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(top = 2.dp))
            }
            g.state == "in" -> Column(horizontalAlignment = Alignment.End) {
                Text("LIVE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                    modifier = Modifier.clip(CircleShape).background(Color(0xFFC62828)).padding(8.dp, 2.dp))
                if (g.myScore.isNotBlank()) Text("${g.myScore}-${g.oppScore}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (g.statusDetail.isNotBlank()) Text(g.statusDetail, fontSize = 10.sp, color = Color.Gray)
            }
            g.state == "post" -> Text(g.statusDetail.ifBlank { "Final" }, fontSize = 12.sp, color = Color.Gray)
        }
        if (tappable) Text("›", Modifier.padding(start = 8.dp), fontSize = 20.sp, color = Color.Gray)
    }
}

@Composable
fun InjuryCard(p: Player, d: InjuryDetail?, loaded: Boolean) {
    fun v(s: String?) = if (s.isNullOrBlank()) "Not reported" else s
    val status = d?.status?.takeIf { it.isNotBlank() } ?: p.status
    val body = d?.let { listOf(it.bodyPart, it.detail.takeIf { x -> x != it.bodyPart }, it.side.takeIf { x -> x.isNotBlank() }?.let { x -> "($x)" })
        .filter { x -> !x.isNullOrBlank() }.joinToString(" ") } ?: ""
    val ret = d?.returnDate?.takeIf { it.isNotBlank() }?.let {
        try { java.time.LocalDate.parse(it).format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy")) + " (estimated)" } catch (e: Exception) { it }
    } ?: ""
    val how = d?.longComment?.takeIf { it.isNotBlank() } ?: d?.shortComment ?: ""
    Column(Modifier.fillMaxWidth().padding(top = 4.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFFFFF3F3)).padding(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Injury report", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFFC62828), modifier = Modifier.weight(1f))
            StatusChip(status)
        }
        if (!loaded) Text("Loading details…", fontSize = 12.sp, color = Color.Gray)
        InjRow("Injury", v(body))
        InjRow("Status", v(status))
        InjRow("Expected return", v(ret))
        if (d != null && d.fantasyStatus.isNotBlank()) InjRow("Designation", d.fantasyStatus)
        InjRow("Last update", v(d?.date))
        InjRow("How it happened / notes", v(how))
        Text("Source: ESPN. Return dates are estimates and \"Not reported\" means ESPN has no data.", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
fun InjRow(label: String, value: String) {
    Column(Modifier.padding(top = 6.dp)) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

fun scheduleWeekLabel(g: Game): String = when (g.seasonType) {
    1 -> "Preseason Week ${g.weekNum}"
    3 -> g.weekText.ifBlank { "Playoffs" }
    else -> "Week ${g.weekNum}"
}
