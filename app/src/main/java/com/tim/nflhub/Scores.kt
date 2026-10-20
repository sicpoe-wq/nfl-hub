package com.tim.nflhub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class WeekOpt(val seasonType: Int, val week: Int, val label: String, val short: String)
data class SbTeam(val id: String, val name: String, val abbr: String, val logo: String, val score: String, val record: String, val winner: Boolean)
data class SbGame(val id: String, val date: String, val state: String, val detail: String, val timeValid: Boolean, val tv: String, val away: SbTeam, val home: SbTeam)
data class Scoreboard(val year: Int, val curType: Int, val curWeek: Int, val weeks: List<WeekOpt>, val games: List<SbGame>, val byes: List<String>)

object ScoresApi {
    private fun JSONObject.s(k: String) = optString(k, "")
    // Verified 2026-10-20: /scoreboard (no params) returns season{year,type}, week{number,teamsOnBye}, leagues[0].calendar
    // (Preseason HOF+3 wks, Weeks 1-18, Wild Card..Super Bowl); ?seasontype=&week=&dates=YEAR returns that week's events.
    suspend fun load(type: Int?, week: Int?, year: Int?): Scoreboard = withContext(Dispatchers.IO) {
        val base = "https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard"
        val d = Api.get(if (type == null) base else "$base?seasontype=$type&week=$week&dates=$year")
        val season = d.optJSONObject("season")
        val weeks = ArrayList<WeekOpt>()
        d.optJSONArray("leagues")?.optJSONObject(0)?.optJSONArray("calendar")?.let { cal ->
            for (i in 0 until cal.length()) {
                val s = cal.getJSONObject(i); val t = s.optString("value").toIntOrNull() ?: continue
                if (t !in 1..3) continue
                val es = s.optJSONArray("entries") ?: continue
                for (j in 0 until es.length()) {
                    val e = es.getJSONObject(j); val w = e.optString("value").toIntOrNull() ?: continue
                    val lbl = e.s("label")
                    val short = when (t) { 1 -> if (lbl.contains("Hall", true)) "HOF" else "Pre ${w - 1}"; 2 -> "Wk $w"; else -> lbl.replace(" Round", "").replace("Conference Championship", "Conf. Champ") }
                    weeks += WeekOpt(t, w, if (t == 1) lbl else lbl, short)
                }
            }
        }
        val games = ArrayList<SbGame>()
        d.optJSONArray("events")?.let { ev ->
            for (i in 0 until ev.length()) {
                val e = ev.getJSONObject(i); val c = e.optJSONArray("competitions")?.optJSONObject(0) ?: continue
                val st = (c.optJSONObject("status") ?: e.optJSONObject("status"))?.optJSONObject("type")
                var away: SbTeam? = null; var home: SbTeam? = null
                val cs = c.optJSONArray("competitors") ?: continue
                for (k in 0 until cs.length()) {
                    val x = cs.getJSONObject(k); val t = x.optJSONObject("team") ?: continue
                    val rec = x.optJSONArray("records")?.let { r -> (0 until r.length()).map { r.getJSONObject(it) }.firstOrNull { it.s("type") == "total" }?.s("summary") } ?: ""
                    val tm = SbTeam(t.s("id"), t.s("displayName"), t.s("abbreviation"), t.s("logo"), x.s("score"), rec, x.optBoolean("winner", false))
                    if (x.s("homeAway") == "home") home = tm else away = tm
                }
                if (away == null || home == null) continue
                val tv = c.optJSONArray("broadcasts")?.let { b -> (0 until b.length()).flatMap { bi -> b.getJSONObject(bi).optJSONArray("names")?.let { n -> (0 until n.length()).map { n.optString(it) } } ?: emptyList() } }?.distinct()?.joinToString(", ") ?: ""
                games += SbGame(e.s("id"), e.s("date"), st?.s("state") ?: "", st?.s("shortDetail") ?: "", c.optBoolean("timeValid", true), tv, away, home)
            }
        }
        val byes = d.optJSONObject("week")?.optJSONArray("teamsOnBye")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).s("abbreviation") } } ?: emptyList()
        Scoreboard(season?.optInt("year", 0) ?: 0, season?.optInt("type", 2) ?: 2, d.optJSONObject("week")?.optInt("number", 1) ?: 1, weeks, games.sortedBy { it.date }, byes)
    }
}

@Composable
fun ScoresTab(onGame: (String, String) -> Unit) {
    // First load: ESPN's current week (no params). Afterwards: the chosen week.
    var sel by remember { mutableStateOf<WeekOpt?>(null) }
    var meta by remember { mutableStateOf<Scoreboard?>(null) }
    val (l, retry) = rememberLoad("sb-${sel?.seasonType}-${sel?.week}") {
        ScoresApi.load(sel?.seasonType, sel?.week, meta?.year).also { if (meta == null) meta = it }
    }
    val m = meta
    val cur = sel ?: m?.let { mm -> mm.weeks.firstOrNull { it.seasonType == mm.curType && it.week == mm.curWeek } }
    Column(Modifier.fillMaxSize()) {
        if (m != null && m.weeks.isNotEmpty()) {
            val st = rememberLazyListState()
            val idx = m.weeks.indexOf(cur).coerceAtLeast(0)
            LaunchedEffect(m) { st.scrollToItem((idx - 1).coerceAtLeast(0)) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                LazyRow(Modifier.weight(1f), state = st, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    itemsIndexed(m.weeks) { _, w ->
                        FilterChip(selected = w == cur, onClick = { sel = w }, label = { Text(w.short) }, modifier = Modifier.padding(end = 6.dp))
                    }
                }
                TextButton(onClick = retry) { Text("Refresh") }
            }
        }
        Box(Modifier.fillMaxSize()) {
            LoadView(l, retry) { sb ->
                val zone = java.time.ZoneId.systemDefault()
                val label = cur?.label ?: "Week ${sb.curWeek}"
                LazyColumn(Modifier.fillMaxSize()) {
                    item {
                        Text("${sb.year.takeIf { it > 0 } ?: ""} $label · ${sb.games.size} games · times in your time zone", Modifier.padding(16.dp, 8.dp), fontSize = 12.sp, color = Color.Gray)
                    }
                    if (sb.games.isEmpty()) item { Text("No games scheduled for this week yet.", Modifier.padding(16.dp), color = Color.Gray) }
                    items(sb.games) { g -> ScoreRow(g, zone) { onGame(g.id, label) }; HorizontalDivider() }
                    if (sb.byes.isNotEmpty() && sel == null) item { Text("Bye: " + sb.byes.joinToString(", "), Modifier.padding(16.dp, 10.dp), fontSize = 12.sp, color = Color.Gray) }
                }
            }
        }
    }
}

@Composable
private fun ScoreRow(g: SbGame, zone: java.time.ZoneId, onClick: () -> Unit) {
    val tappable = g.state == "post" || g.state == "in"
    val showScore = g.state != "pre"
    Row(Modifier.fillMaxWidth().then(if (tappable) Modifier.clickable(onClick = onClick) else Modifier).padding(16.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            listOf(g.away, g.home).forEach { t ->
                val bold = g.state == "post" && t.winner
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (t.logo.isNotBlank()) AsyncImage(t.logo, null, Modifier.size(28.dp)) else Spacer(Modifier.size(28.dp))
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(t.name, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Normal, fontSize = 15.sp, maxLines = 1)
                        if (t.record.isNotBlank()) Text(t.record, fontSize = 11.sp, color = Color.Gray)
                    }
                    if (showScore) Text(t.score, fontSize = 18.sp, fontWeight = if (bold) FontWeight.ExtraBold else FontWeight.Normal,
                        color = if (g.state == "post" && !t.winner) Color.Gray else Color.Black)
                }
            }
        }
        Column(Modifier.width(104.dp).padding(start = 12.dp), horizontalAlignment = Alignment.End) {
            when (g.state) {
                "in" -> {
                    Text("LIVE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp, modifier = Modifier.background(Color(0xFFC62828)).padding(6.dp, 1.dp))
                    Text(g.detail, fontSize = 12.sp, color = Color(0xFFC62828))
                }
                "post" -> Text(g.detail.ifBlank { "Final" }, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Color.DarkGray)
                else -> {
                    val z = parseEspnInstant(g.date)?.atZone(zone)
                    Text(z?.format(java.time.format.DateTimeFormatter.ofPattern("EEE, MMM d")) ?: g.date.take(10), fontSize = 12.sp)
                    Text(if (!g.timeValid || z == null) "Time TBD" else z.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a zzz")), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    if (g.detail.contains("Postponed", true) || g.detail.contains("Canceled", true)) Text(g.detail, fontSize = 11.sp, color = Color(0xFFC62828))
                }
            }
            if (g.tv.isNotBlank()) Text("📺 ${g.tv}", fontSize = 11.sp, color = Color.Gray, maxLines = 2)
        }
        if (tappable) Text("›", Modifier.padding(start = 6.dp), fontSize = 20.sp, color = Color.Gray)
    }
}
