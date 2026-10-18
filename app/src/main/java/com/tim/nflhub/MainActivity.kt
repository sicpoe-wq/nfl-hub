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
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
    var teamId by remember { mutableStateOf(EAGLES_ID) }
    var showPicker by remember { mutableStateOf(false) }
    var tab by remember { mutableIntStateOf(0) }
    var selected by remember { mutableStateOf<Player?>(null) }
    val (teamsL, retryTeams) = rememberLoad("teams") { Api.teams() }
    val team = (teamsL as? Load.Ok)?.v?.firstOrNull { it.id == teamId }
    val title = team?.name ?: "Philadelphia Eagles"

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
                    if (teamId != EAGLES_ID) TextButton(onClick = { teamId = EAGLES_ID }) { Text("🦅 Home", color = Color.White) }
                    TextButton(onClick = { showPicker = true }) { Text("Teams ▾", color = Color.White) }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EaglesGreen, titleContentColor = Color.White)
            )
            TabRow(selectedTabIndex = tab, containerColor = EaglesGreen, contentColor = Color.White) {
                listOf("Roster", "Stats", "Injuries").forEachIndexed { i, t ->
                    Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) })
                }
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            key(teamId) {
                when (tab) {
                    0 -> RosterTab(teamId) { selected = it }
                    1 -> StatsTab(teamId)
                    else -> InjuriesTab(teamId) { id, name -> selected = Player(id, name, "", "", "", "", "", "", "", "", "", "") }
                }
            }
        }
    }

    if (showPicker) {
        ModalBottomSheet(onDismissRequest = { showPicker = false }) {
            LoadView(teamsL, retryTeams) { teams ->
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 560.dp)) {
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
fun RosterTab(teamId: String, onPlayer: (Player) -> Unit) {
    val (l, retry) = rememberLoad(teamId) { Api.roster(teamId) }
    LoadView(l, retry) { players ->
        val order = listOf("offense", "defense", "specialTeam", "injuredReserveOrOut", "practiceSquad", "suspended")
        val names = mapOf("offense" to "Offense", "defense" to "Defense", "specialTeam" to "Special Teams",
            "injuredReserveOrOut" to "Injured Reserve / Out", "practiceSquad" to "Practice Squad", "suspended" to "Suspended")
        val grouped = players.groupBy { it.group }
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
fun InjuriesTab(teamId: String, onPlayer: (String, String) -> Unit) {
    val (l, retry) = rememberLoad(teamId) { Api.injuries(teamId, force = true) }
    LoadView(l, retry) { inj ->
        if (inj.isEmpty()) { Box(Modifier.fillMaxSize(), Alignment.Center) { Text("No injuries reported") }; return@LoadView }
        LazyColumn(Modifier.fillMaxSize()) {
            items(inj) { i ->
                Column(Modifier.fillMaxWidth().clickable(enabled = i.playerId.isNotBlank()) { onPlayer(i.playerId, i.name) }.padding(16.dp, 10.dp)) {
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
                if (ps.tables.isEmpty()) { Text("No stats available for this player.", Modifier.padding(24.dp)); return@LoadView }
                LazyColumn {
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
