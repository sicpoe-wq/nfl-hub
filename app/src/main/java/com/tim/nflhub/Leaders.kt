package com.tim.nflhub

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** One leaderboard entry before athlete details are fetched. */
data class LeaderRef(val athleteRef: String, val value: Double, val display: String, val active: Boolean?)
/** A leaderboard category. ESPN categories carry [refs]; bundled (static) categories carry [rows] + a source label. */
data class LeaderCat(
    val key: String, val label: String, val refs: List<LeaderRef> = emptyList(),
    val rows: List<LeaderRow>? = null, val sourceNote: String = ""
)
data class LeaderRow(
    val rank: String, val id: String, val name: String, val team: String, val headshot: String,
    val value: String, val active: Boolean?
)

object LeadersApi {
    private const val CORE = "https://sports.core.api.espn.com/v2/sports/football/leagues/nfl"
    /** ESPN's per-season leader data is verified complete back to 1990 (1982-89 spot checks had missing/wrong leaders). */
    const val FIRST_SEASON = 1990
    private val ORDER = listOf(
        "passingYards", "passingTouchdowns", "quarterbackRating", "completions", "rushingYards", "rushingTouchdowns",
        "receivingYards", "receptions", "receivingTouchdowns", "totalTouchdowns", "totalPoints",
        "sacks", "interceptions", "totalTackles", "passesDefended"
    )
    private val LABELS = mapOf(
        "passingYards" to "Passing Yds", "passingTouchdowns" to "Passing TD", "quarterbackRating" to "QB Rating",
        "completions" to "Completions", "rushingYards" to "Rushing Yds", "rushingTouchdowns" to "Rushing TD",
        "receivingYards" to "Receiving Yds", "receptions" to "Receptions", "receivingTouchdowns" to "Receiving TD",
        "totalTouchdowns" to "Total TD", "totalPoints" to "Points", "sacks" to "Sacks", "interceptions" to "Interceptions",
        "totalTackles" to "Tackles", "passesDefended" to "Passes Def."
    )
    private val athleteCache = ConcurrentHashMap<String, JSONObject>()
    private val teamCache = ConcurrentHashMap<String, String>()
    private val catCache = ConcurrentHashMap<String, List<LeaderCat>>()
    private var season: Int? = null

    private fun https(u: String) = u.replace("http://", "https://")

    /** Current NFL season year from ESPN's scoreboard (e.g. 2026). */
    suspend fun currentSeason(): Int = withContext(Dispatchers.IO) {
        season ?: Api.get("https://site.api.espn.com/apis/site/v2/sports/football/nfl/scoreboard")
            .getJSONObject("season").getInt("year").also { season = it }
    }

    private fun parse(d: JSONObject): List<LeaderCat> {
        val cats = d.optJSONArray("categories") ?: return emptyList()
        val out = mutableMapOf<String, LeaderCat>()
        for (i in 0 until cats.length()) {
            val c = cats.getJSONObject(i)
            val name = c.optString("name")
            if (name !in ORDER) continue
            val ls = c.optJSONArray("leaders") ?: continue
            val refs = (0 until ls.length()).mapNotNull { j ->
                val l = ls.getJSONObject(j)
                val ref = l.optJSONObject("athlete")?.optString("\$ref").orEmpty()
                if (ref.isBlank()) null
                else LeaderRef(https(ref), l.optDouble("value"), l.optString("displayValue"), if (l.has("active")) l.optBoolean("active") else null)
            }
            if (refs.isNotEmpty()) out[name] = LeaderCat(name, LABELS[name] ?: c.optString("displayName"), refs)
        }
        return ORDER.mapNotNull { out[it] }
    }

    /** Regular-season leaders for [year] (ESPN core API, top 25 per category). */
    suspend fun seasonCats(year: Int): List<LeaderCat> = withContext(Dispatchers.IO) {
        val k = "s$year"
        catCache[k] ?: parse(Api.get("$CORE/seasons/$year/types/2/leaders?limit=25")).also { if (year < (season ?: 0)) catCache[k] = it }
    }

    /** Career leaders: ESPN's live all-time feed plus the bundled, cited receiving categories ESPN doesn't provide. */
    suspend fun careerCats(ctx: Context): List<LeaderCat> = withContext(Dispatchers.IO) {
        val live = catCache["career"] ?: parse(Api.get("$CORE/leaders?limit=25")).also { catCache["career"] = it }
        val bundled = try { bundledCareer(ctx) } catch (e: Exception) { emptyList() }
        val all = (live + bundled.filter { b -> live.none { it.key == b.key } }).associateBy { it.key }
        ORDER.mapNotNull { all[it] }
    }

    /** Same merge as [careerCats] but from already-fetched JSON + asset text (used by tests). */
    fun careerCatsFromJson(live: JSONObject, bundledText: String): List<LeaderCat> {
        val l = parse(live); val b = parseBundled(bundledText)
        val all = (l + b.filter { x -> l.none { it.key == x.key } }).associateBy { it.key }
        return ORDER.mapNotNull { all[it] }
    }

    private fun bundledCareer(ctx: Context): List<LeaderCat> =
        parseBundled(ctx.assets.open("career_static.json").bufferedReader().readText())

    fun parseBundled(text: String): List<LeaderCat> {
        val j = JSONObject(text)
        val cats = j.getJSONArray("categories")
        return (0 until cats.length()).map { i ->
            val c = cats.getJSONObject(i)
            val ls = c.getJSONArray("leaders")
            val asOf = try { java.time.LocalDate.parse(c.getString("asOf")).format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy")) } catch (e: Exception) { c.getString("asOf") }
            LeaderCat(c.getString("key"), LABELS[c.getString("key")] ?: c.getString("label"), rows = (0 until ls.length()).map { k ->
                val l = ls.getJSONObject(k); val id = l.optString("espnId")
                LeaderRow(l.optString("rank"), id, l.optString("name"), l.optString("team"),
                    if (id.isNotBlank()) "https://a.espncdn.com/i/headshots/nfl/players/full/$id.png" else "",
                    l.optString("value"), l.optBoolean("active"))
            }, sourceNote = "Bundled snapshot as of $asOf · source: ${c.getString("source")} (rev ${c.optString("revid")}). " +
                "Active players' totals may lag live stats.")
        }
    }

    private fun json(u: String): JSONObject = athleteCache[u] ?: Api.get(u).also { athleteCache[u] = it }

    private fun teamAbbr(ref: String): String {
        if (ref.isBlank()) return ""
        val u = https(ref)
        return teamCache[u] ?: (try { Api.get(u).optString("abbreviation") } catch (e: Exception) { "" }).also { if (it.isNotBlank()) teamCache[u] = it }
    }

    fun fmt(v: Double, display: String): String =
        if (v.isNaN()) display else if (v % 1.0 == 0.0) "%,d".format(v.toLong()) else "%,.1f".format(v)

    /** Resolve names/headshots/teams for one category (parallel, max 8 requests at once). For seasons the team is the season's team; for career it's the last team. */
    suspend fun resolve(cat: LeaderCat): List<LeaderRow> = cat.rows ?: withContext(Dispatchers.IO) {
        val sem = Semaphore(8)
        val rows = coroutineScope {
            cat.refs.map { r ->
                async {
                    sem.withPermit {
                        val a = try { json(r.athleteRef) } catch (e: Exception) { null }
                        val id = r.athleteRef.substringAfter("/athletes/").substringBefore("?").substringBefore("/")
                        LeaderRow("", id, a?.optString("displayName").orEmpty().ifBlank { "Player $id" },
                            teamAbbr(a?.optJSONObject("team")?.optString("\$ref").orEmpty()),
                            a?.optJSONObject("headshot")?.optString("href").orEmpty(), fmt(r.value, r.display), r.active)
                    }
                }
            }.awaitAll()
        }
        // Ties share a rank (1, 2, 2, 4 ...).
        var rank = 0
        rows.mapIndexed { i, row ->
            if (i == 0 || cat.refs[i].value != cat.refs[i - 1].value) rank = i + 1
            row.copy(rank = rank.toString())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeadersTab(onPlayer: (Player) -> Unit) {
    val ctx = LocalContext.current
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 this season, 1 past, 2 career
    val (curL, retryCur) = rememberLoad("cur-season") { LeadersApi.currentSeason() }
    LoadView(curL, retryCur) { cur ->
        var pastYear by rememberSaveable { mutableIntStateOf(cur - 1) }
        var catKey by rememberSaveable { mutableStateOf("passingYards") }
        val loadKey = when (mode) { 0 -> "s$cur"; 1 -> "s$pastYear"; else -> "career" }
        val (catsL, retryCats) = rememberLoad(loadKey) {
            when (mode) { 0 -> LeadersApi.seasonCats(cur); 1 -> LeadersApi.seasonCats(pastYear); else -> LeadersApi.careerCats(ctx) }
        }
        Column(Modifier.fillMaxSize()) {
            ModeChips(mode) { mode = it }
            if (mode == 1) {
                var open by remember { mutableStateOf(false) }
                Box(Modifier.padding(horizontal = 12.dp)) {
                    OutlinedButton(onClick = { open = true }) { Text("Season: $pastYear ▾") }
                    DropdownMenu(open, { open = false }, Modifier.heightIn(max = 360.dp)) {
                        for (y in (cur - 1) downTo LeadersApi.FIRST_SEASON) DropdownMenuItem(text = { Text("$y") }, onClick = { pastYear = y; open = false })
                    }
                }
            }
            LoadView(catsL, retryCats) { cats ->
                if (cats.isEmpty()) {
                    Box(Modifier.fillMaxSize(), Alignment.Center) { Text("ESPN has no leaders for this season yet.") }; return@LoadView
                }
                val cat = cats.firstOrNull { it.key == catKey } ?: cats.first()
                CategoryChips(cats, cat.key) { catKey = it }
                val title = when (mode) { 0 -> "$cur regular season"; 1 -> "$pastYear regular season"; else -> "All-time career" }
                val note = leaderNote(mode, pastYear, cat)
                LeaderList(cat, "$title · ${cat.label}", note, onPlayer)
            }
        }
    }
}

fun leaderNote(mode: Int, pastYear: Int, cat: LeaderCat): String =
    cat.sourceNote.ifBlank {
        "Source: ESPN, live · top ${cat.refs.size}" + when (mode) {
            2 -> " · team = last team played for"
            1 -> if (pastYear < 2001) " · tackles: 2001+ only" else ""
            else -> ""
        }
    } + if (cat.rows != null) " Team = last team played for." else ""

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeChips(mode: Int, onMode: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        listOf("This Season", "Past Seasons", "Career").forEachIndexed { i, t ->
            FilterChip(selected = mode == i, onClick = { onMode(i) }, label = { Text(t, fontSize = 12.sp, maxLines = 1, softWrap = false) }, modifier = Modifier.weight(1f))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryChips(cats: List<LeaderCat>, selected: String, onCat: (String) -> Unit) {
    val idx = cats.indexOfFirst { it.key == selected }.coerceAtLeast(0)
    val st = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = (idx - 1).coerceAtLeast(0))
    LazyRow(state = st, contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items(cats) { c -> FilterChip(selected = c.key == selected, onClick = { onCat(c.key) }, label = { Text(c.label, fontSize = 12.sp) }) }
    }
}

@Composable
fun LeaderTitle(title: String, note: String) {
    Text(title, Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = NflNavy)
    Text(note, Modifier.padding(horizontal = 16.dp, vertical = 2.dp), fontSize = 11.sp, color = Color.Gray)
}

@Composable
private fun LeaderList(cat: LeaderCat, title: String, note: String, onPlayer: (Player) -> Unit) {
    val (rowsL, retry) = rememberLoad(cat) { LeadersApi.resolve(cat) }
    Column(Modifier.fillMaxSize()) {
        LeaderTitle(title, note)
        LoadView(rowsL, retry) { rows -> LeaderRows(rows, onPlayer) }
    }
}

@Composable
fun LeaderRows(rows: List<LeaderRow>, onPlayer: (Player) -> Unit) {
            LazyColumn(Modifier.fillMaxSize()) {
                items(rows) { r ->
                    val tappable = r.id.isNotBlank()
                    Row(Modifier.fillMaxWidth().then(if (tappable) Modifier.clickable {
                        onPlayer(Player(r.id, r.name, "", "", "", "", "", "", "", r.headshot, "", ""))
                    } else Modifier).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(r.rank, Modifier.width(30.dp), fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 15.sp)
                        Headshot(r.headshot, 44, r.name); Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(r.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(r.team.ifBlank { null }, if (r.active == true) "Active" else null).joinToString(" · "),
                                fontSize = 12.sp, color = Color.Gray, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Text(r.value, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = NflNavy)
                    }
                    HorizontalDivider(color = Color(0xFFEEEEEE))
                }
            }
}
