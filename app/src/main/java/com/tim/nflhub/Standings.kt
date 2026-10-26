package com.tim.nflhub

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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class StandingRow(
    val teamId: String, val name: String, val abbr: String, val logo: String,
    val w: Int, val l: Int, val t: Int, val pct: String, val divRec: String,
    val pf: String, val pa: String, val diff: String, val streak: String,
    val seed: Int?, val clincher: String, val conf: String, val div: String
) {
    val record get() = if (t > 0) "$w-$l-$t" else "$w-$l"
    val winPct get() = if (w + l + t == 0) 0.0 else (w + t * 0.5) / (w + l + t)
}
data class Division(val conf: String, val name: String, val rows: List<StandingRow>)
data class Seed(val seed: Int, val row: StandingRow, val divLeader: Boolean)
data class ConfPicture(val conf: String, val seeds: List<Seed>, val hunt: List<StandingRow>)
data class StandingsData(val season: String, val divisions: List<Division>, val pictures: List<ConfPicture>, val espnSeeds: Boolean)

object StandingsApi {
    // Verified 2026-10-19: returns NFL -> AFC/NFC -> 4 divisions each, entries with playoffSeed, divisionRecord, streak, etc.
    private const val URL = "https://site.web.api.espn.com/apis/v2/sports/football/nfl/standings?level=3"

    @Volatile private var cached: Pair<Long, StandingsData>? = null

    /** Standings no older than [maxAgeMs] (the team banners share one fetch instead of one per team). */
    suspend fun loadCached(maxAgeMs: Long = 120_000): StandingsData {
        cached?.let { (t, d) -> if (System.currentTimeMillis() - t < maxAgeMs) return d }
        return load()
    }

    /** ("4th in NFC West", record) for a team, using the same tiebroken division order as the Standings screen. */
    suspend fun divisionPlace(teamId: String): Pair<String, String>? {
        val d = loadCached()
        val div = d.divisions.firstOrNull { dv -> dv.rows.any { it.teamId == teamId } } ?: return null
        val i = div.rows.indexOfFirst { it.teamId == teamId }
        return "${ordinal(i + 1)} in ${div.name}" to div.rows[i].record
    }

    suspend fun load(): StandingsData = withContext(Dispatchers.IO) {
        val root = Api.get(URL)
        val season = root.optJSONObject("season")?.optString("displayName", "") ?: ""
        val divisions = mutableListOf<Division>()
        val confs = root.optJSONArray("children") ?: throw RuntimeException("No standings data")
        for (i in 0 until confs.length()) {
            val c = confs.getJSONObject(i)
            val confAbbr = c.optString("abbreviation", c.optString("name"))
            val divs = c.optJSONArray("children") ?: continue
            for (j in 0 until divs.length()) {
                val d = divs.getJSONObject(j)
                val entries = d.optJSONObject("standings")?.optJSONArray("entries") ?: continue
                val rows = (0 until entries.length()).map { parseRow(entries.getJSONObject(it), confAbbr, d.optString("name")) }
                divisions += Division(confAbbr, d.optString("name"), rows.sortedWith(
                    compareBy<StandingRow> { it.seed ?: 99 }.thenByDescending { it.winPct }))
            }
        }
        if (divisions.isEmpty()) throw RuntimeException("No standings data")
        val espnSeeds = divisions.all { d -> d.rows.all { it.seed != null } }
        val pictures = divisions.groupBy { it.conf }.map { (conf, ds) -> picture(conf, ds, espnSeeds) }
        StandingsData(season, divisions, pictures, espnSeeds).also { cached = System.currentTimeMillis() to it }
    }

    private fun parseRow(e: JSONObject, conf: String, div: String): StandingRow {
        val t = e.getJSONObject("team")
        val stats = e.optJSONArray("stats")
        val byName = HashMap<String, JSONObject>(); val byType = HashMap<String, JSONObject>()
        if (stats != null) for (k in 0 until stats.length()) {
            val s = stats.getJSONObject(k)
            s.optString("name").takeIf { it.isNotBlank() }?.let { byName.putIfAbsent(it, s) }
            s.optString("type").takeIf { it.isNotBlank() }?.let { byType.putIfAbsent(it, s) }
        }
        fun dv(n: String) = byName[n]?.optString("displayValue", "") ?: ""
        fun num(n: String) = byName[n]?.optDouble("value", Double.NaN)?.takeIf { !it.isNaN() }?.toInt() ?: dv(n).toIntOrNull() ?: 0
        val divRec = dv("divisionRecord").ifBlank { byType["vsdiv"]?.optString("summary", "") ?: "" }
        val seed = byName["playoffSeed"]?.optDouble("value", Double.NaN)?.takeIf { !it.isNaN() && it > 0 }?.toInt()
        val logos = t.optJSONArray("logos")
        return StandingRow(
            t.optString("id"), t.optString("displayName"), t.optString("abbreviation"),
            logos?.optJSONObject(0)?.optString("href", "") ?: "",
            num("wins"), num("losses"), num("ties"), dv("winPercent"), divRec,
            dv("pointsFor"), dv("pointsAgainst"), dv("differential").ifBlank { dv("pointDifferential") }, dv("streak"),
            seed, dv("clincher"), conf, div
        )
    }

    /** Uses ESPN's playoffSeed when every team has one; otherwise a simplified computation. */
    private fun picture(conf: String, divs: List<Division>, espn: Boolean): ConfPicture {
        val all = divs.flatMap { it.rows }
        return if (espn) {
            val sorted = all.sortedBy { it.seed }
            val leaders = divs.mapNotNull { d -> d.rows.minByOrNull { it.seed ?: 99 }?.teamId }.toSet()
            ConfPicture(conf, sorted.take(7).map { Seed(it.seed!!, it, it.teamId in leaders) }, sorted.drop(7).take(5))
        } else {
            val cmp = compareByDescending<StandingRow> { it.winPct }.thenByDescending { it.w }
            val leaders = divs.mapNotNull { d -> d.rows.sortedWith(cmp).firstOrNull() }.sortedWith(cmp)
            val rest = (all - leaders.toSet()).sortedWith(cmp)
            val seeds = leaders.mapIndexed { i, r -> Seed(i + 1, r, true) } + rest.take(3).mapIndexed { i, r -> Seed(i + 5, r, false) }
            ConfPicture(conf, seeds, rest.drop(3).take(5))
        }
    }
}

private val clinchLegend = mapOf("z" to "clinched #1 seed / bye", "y" to "clinched division", "x" to "clinched playoff berth",
    "*" to "clinched #1 seed", "e" to "eliminated")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StandingsScreen(onBack: () -> Unit, onTeam: (String) -> Unit) {
    val (l, retry) = rememberLoad("standings") { StandingsApi.load() }
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(topBar = {
        Column {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage("https://a.espncdn.com/i/teamlogos/leagues/500/nfl.png", null, Modifier.size(32.dp))
                        Spacer(Modifier.width(8.dp)); Text("Standings", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = { TextButton(onClick = onBack) { Text("‹ Back", color = Color.White) } },
                actions = { TextButton(onClick = retry) { Text("Refresh", color = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = NflNavy, titleContentColor = Color.White)
            )
            TabRow(selectedTabIndex = tab, containerColor = NflNavy, contentColor = Color.White) {
                listOf("Divisions", "Playoff Picture").forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            LoadView(l, retry) { data -> if (tab == 0) DivisionsView(data, onTeam) else PlayoffView(data, onTeam) }
        }
    }
}

private val COLS = listOf("W-L-T" to 56, "PCT" to 46, "DIV" to 50, "PF" to 40, "PA" to 40, "STRK" to 44)

@Composable
internal fun DivisionsView(data: StandingsData, onTeam: (String) -> Unit) {
    val hs = rememberScrollState()
    LazyColumn(Modifier.fillMaxSize()) {
        item { Text("${data.season} season · tap a team to open it · swipe the table sideways for more columns", Modifier.padding(16.dp, 10.dp), fontSize = 12.sp, color = Color.Gray) }
        listOf("AFC", "NFC").forEach { conf ->
            val divs = data.divisions.filter { it.conf == conf }
            if (divs.isEmpty()) return@forEach
            item {
                Row(Modifier.fillMaxWidth().background(NflNavy).padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NetImage("https://a.espncdn.com/i/teamlogos/nfl/500/${conf.lowercase()}.png", Modifier.size(24.dp)); Spacer(Modifier.width(8.dp))
                    Text(if (conf == "AFC") "American Football Conference" else "National Football Conference", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            divs.forEach { d ->
                item {
                    Row(Modifier.fillMaxWidth().background(Color(0xFFEEEEEE)).padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(d.name, Modifier.width(150.dp).padding(start = 16.dp), fontWeight = FontWeight.Bold, color = NflNavy)
                        Row(Modifier.horizontalScroll(hs)) { COLS.forEach { (c, w) -> Text(c, Modifier.width(w.dp), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray, textAlign = TextAlign.Center) } }
                    }
                }
                items(d.rows) { r ->
                    Row(Modifier.fillMaxWidth().clickable { onTeam(r.teamId) }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Row(Modifier.width(150.dp).padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            NetImage(r.logo, Modifier.size(26.dp)); Spacer(Modifier.width(6.dp))
                            Text(r.abbr, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            if (r.clincher.isNotBlank()) Text(" ${r.clincher}", fontSize = 12.sp, color = if (r.clincher == "e") Color(0xFFC62828) else NflNavy, fontWeight = FontWeight.Bold)
                        }
                        Row(Modifier.horizontalScroll(hs)) {
                            val vals = listOf(r.record + if (r.t == 0) "-0" else "", r.pct, r.divRec, r.pf, r.pa, r.streak)
                            COLS.zip(vals).forEach { (c, v) ->
                                Text(v.ifBlank { "–" }, Modifier.width(c.second.dp), fontSize = 13.sp, textAlign = TextAlign.Center,
                                    fontWeight = if (c.first == "W-L-T") FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (c.first == "STRK") (if (v.startsWith("W")) NflNavy else if (v.startsWith("L")) Color(0xFFC62828) else Color.Unspecified) else Color.Unspecified)
                            }
                        }
                    }
                    HorizontalDivider(color = Color(0xFFEEEEEE))
                }
            }
        }
        if (data.divisions.any { d -> d.rows.any { it.clincher.isNotBlank() } }) item {
            Text(clinchLegend.entries.joinToString(" · ") { "${it.key} = ${it.value}" }, Modifier.padding(16.dp), fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun PlayoffView(data: StandingsData, onTeam: (String) -> Unit) {
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Text(if (data.espnSeeds) "${data.season} · If the season ended today. Seeds from ESPN (1-4 division leaders, 5-7 wild cards)."
                 else "Based on current records; tiebreakers simplified. Division leaders by record are seeded 1-4, then the best remaining records 5-7.",
                Modifier.padding(16.dp, 10.dp), fontSize = 12.sp, color = Color.Gray)
        }
        data.pictures.sortedBy { it.conf }.forEach { p ->
            item {
                Row(Modifier.fillMaxWidth().background(NflNavy).padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    NetImage("https://a.espncdn.com/i/teamlogos/nfl/500/${p.conf.lowercase()}.png", Modifier.size(24.dp)); Spacer(Modifier.width(8.dp))
                    Text("${p.conf} Playoff Picture", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            items(p.seeds) { s ->
                SeedRow(s.seed.toString(), s.row, if (s.divLeader) "${s.row.div} leader" else "Wild card", if (s.seed == 1) "First-round bye" else "", onTeam)
                if (s.seed == 4) Text("Wild cards", Modifier.fillMaxWidth().background(Color(0xFFF4F4F4)).padding(16.dp, 4.dp), fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            }
            if (p.hunt.isNotEmpty()) {
                item { Text("In the hunt", Modifier.fillMaxWidth().background(Color(0xFFEEEEEE)).padding(16.dp, 6.dp), fontWeight = FontWeight.Bold, color = Color.DarkGray) }
                items(p.hunt) { r -> SeedRow(r.seed?.toString() ?: "–", r, r.div, "", onTeam, dim = true) }
            }
        }
        item { Spacer(Modifier.height(24.dp)) }
    }
}

@Composable
private fun SeedRow(seed: String, r: StandingRow, sub: String, tag: String, onTeam: (String) -> Unit, dim: Boolean = false) {
    Row(Modifier.fillMaxWidth().clickable { onTeam(r.teamId) }.padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(if (dim) Color(0xFFBDBDBD) else NflNavy), Alignment.Center) {
            Text(seed, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.width(10.dp))
        NetImage(r.logo, Modifier.size(32.dp)); Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(r.name, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, maxLines = 1)
                if (r.clincher.isNotBlank()) Text(" ${r.clincher}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = if (r.clincher == "e") Color(0xFFC62828) else NflNavy)
            }
            Text(listOf(sub, tag, clinchLegend[r.clincher] ?: "").filter { it.isNotBlank() }.joinToString(" · "), fontSize = 11.sp, color = Color.Gray)
        }
        Text(r.record, fontWeight = FontWeight.Bold, fontSize = 15.sp)
    }
    HorizontalDivider(color = Color(0xFFEEEEEE))
}

@Composable
fun StandingsBody(onTeam: (String) -> Unit) {
    val (l, retry) = rememberLoad("standings") { StandingsApi.load() }
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(8.dp, 4.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("Divisions", "Playoff Picture").forEachIndexed { i, t ->
                FilterChip(selected = tab == i, onClick = { tab = i }, label = { Text(t) }, modifier = Modifier.padding(end = 8.dp))
            }
            Spacer(Modifier.weight(1f)); TextButton(onClick = retry) { Text("Refresh") }
        }
        Box(Modifier.fillMaxSize()) { LoadView(l, retry) { data -> if (tab == 0) DivisionsView(data, onTeam) else PlayoffView(data, onTeam) } }
    }
}
