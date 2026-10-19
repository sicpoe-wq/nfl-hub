package com.tim.nflhub

import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class GTeam(val id: String, val name: String, val abbr: String, val logo: String, val score: String, val record: String, val lines: List<String>, val winner: Boolean)
data class BoxCat(val title: String, val labels: List<String>, val rows: List<Pair<String, List<String>>>, val totals: List<String>)
data class TeamBox(val teamStats: List<Pair<String, String>>, val cats: List<BoxCat>)
data class ScoringPlay(val period: Int, val clock: String, val teamId: String, val logo: String, val type: String, val text: String, val away: String, val home: String)
data class Highlight(val id: String, val title: String, val thumb: String, val duration: Int, val hls: String, val mp4: String)
data class GameSummary(
    val state: String, val statusDetail: String, val season: String, val seasonType: Int, val week: Int,
    val away: GTeam, val home: GTeam, val box: Map<String, TeamBox>, val scoring: List<ScoringPlay>, val videos: List<Highlight>, val venue: String
)

object GameApi {
    private fun JSONObject.s(k: String) = optString(k, "")
    private fun strList(o: JSONObject?, k: String) = o?.optJSONArray(k)?.let { a -> (0 until a.length()).map { a.optString(it, "") } } ?: emptyList()

    // Verified 2026-10-19 with Eagles events 401872929 (wk1) and 401872998 (wk6): header, boxscore.teams/players, scoringPlays, videos.
    suspend fun summary(eventId: String): GameSummary = withContext(Dispatchers.IO) {
        val d = Api.get("https://site.api.espn.com/apis/site/v2/sports/football/nfl/summary?event=$eventId")
        val h = d.optJSONObject("header") ?: throw RuntimeException("No game data")
        val comp = h.optJSONArray("competitions")?.optJSONObject(0) ?: throw RuntimeException("No game data")
        val st = comp.optJSONObject("status")?.optJSONObject("type")
        val comps = comp.optJSONArray("competitors") ?: throw RuntimeException("No teams")
        var away: GTeam? = null; var home: GTeam? = null
        for (i in 0 until comps.length()) {
            val c = comps.getJSONObject(i); val t = c.optJSONObject("team") ?: continue
            val logo = t.optJSONArray("logos")?.optJSONObject(0)?.s("href")?.ifBlank { null } ?: t.s("logo")
            val rec = c.optJSONArray("record")?.let { r -> (0 until r.length()).map { r.getJSONObject(it) }.firstOrNull { it.s("type") == "total" }?.s("displayValue") } ?: ""
            val lines = c.optJSONArray("linescores")?.let { a -> (0 until a.length()).map { a.getJSONObject(it).s("displayValue") } } ?: emptyList()
            val g = GTeam(t.s("id"), t.s("displayName"), t.s("abbreviation"), logo, c.opt("score")?.let { if (it is JSONObject) it.s("displayValue") else it.toString() } ?: "", rec, lines, c.optBoolean("winner", false))
            if (c.s("homeAway") == "home") home = g else away = g
        }
        if (away == null || home == null) throw RuntimeException("Teams missing")

        val box = HashMap<String, TeamBox>()
        val bs = d.optJSONObject("boxscore")
        val teamStats = HashMap<String, List<Pair<String, String>>>()
        bs?.optJSONArray("teams")?.let { a ->
            for (i in 0 until a.length()) {
                val t = a.getJSONObject(i); val id = t.optJSONObject("team")?.s("id") ?: continue
                val ss = t.optJSONArray("statistics") ?: continue
                teamStats[id] = (0 until ss.length()).map { ss.getJSONObject(it) }.map { it.optString("label", it.s("name")) to it.s("displayValue") }
                    .distinct()
            }
        }
        val cats = HashMap<String, List<BoxCat>>()
        bs?.optJSONArray("players")?.let { a ->
            for (i in 0 until a.length()) {
                val p = a.getJSONObject(i); val id = p.optJSONObject("team")?.s("id") ?: continue
                val sc = p.optJSONArray("statistics") ?: continue
                cats[id] = (0 until sc.length()).mapNotNull { k ->
                    val c = sc.getJSONObject(k)
                    val ath = c.optJSONArray("athletes") ?: return@mapNotNull null
                    if (ath.length() == 0) return@mapNotNull null
                    val rows = (0 until ath.length()).map { j ->
                        val x = ath.getJSONObject(j)
                        (x.optJSONObject("athlete")?.s("displayName") ?: "") to strList(x, "stats")
                    }
                    val title = c.s("text").ifBlank { c.s("name") }.let { t -> t.removePrefix(p.optJSONObject("team")?.s("location") ?: "").trim().ifBlank { t } }
                    BoxCat(title, strList(c, "labels"), rows, strList(c, "totals"))
                }
            }
        }
        for (id in listOf(away.id, home.id)) box[id] = TeamBox(teamStats[id] ?: emptyList(), cats[id] ?: emptyList())

        val scoring = d.optJSONArray("scoringPlays")?.let { a ->
            (0 until a.length()).map { a.getJSONObject(it) }.map { p ->
                val t = p.optJSONObject("team")
                ScoringPlay(p.optJSONObject("period")?.optInt("number", 0) ?: 0, p.optJSONObject("clock")?.s("displayValue") ?: "",
                    t?.s("id") ?: "", t?.s("logo") ?: "", p.optJSONObject("type")?.s("abbreviation") ?: "", p.s("text"),
                    p.opt("awayScore")?.toString() ?: "", p.opt("homeScore")?.toString() ?: "")
            }
        } ?: emptyList()

        val videos = d.optJSONArray("videos")?.let { a ->
            (0 until a.length()).map { a.getJSONObject(it) }.mapNotNull { v ->
                val src = v.optJSONObject("links")?.optJSONObject("source")
                val hls = src?.optJSONObject("HLS")?.s("href") ?: ""
                val mp4 = src?.s("href")?.ifBlank { null } ?: src?.optJSONObject("HD")?.s("href") ?: ""
                if (hls.isBlank() && mp4.isBlank()) null
                else Highlight(v.s("id"), v.s("headline"), v.s("thumbnail"), v.optInt("duration", 0), hls, mp4)
            }
        } ?: emptyList()

        val venue = d.optJSONObject("gameInfo")?.optJSONObject("venue")?.let { v ->
            listOf(v.s("fullName"), v.optJSONObject("address")?.let { ad -> listOf(ad.s("city"), ad.s("state")).filter { it.isNotBlank() }.joinToString(", ") } ?: "")
                .filter { it.isNotBlank() }.joinToString(" · ")
        } ?: ""
        val season = h.optJSONObject("season")
        GameSummary(st?.s("state") ?: "", st?.s("shortDetail") ?: "", season?.optInt("year", 0)?.toString() ?: "",
            season?.optInt("type", 2) ?: 2, h.optInt("week", 0), away, home, box, scoring, videos, venue)
    }
}

@kotlin.OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GameScreen(eventId: String, weekText: String, onBack: () -> Unit) {
    val (l, retry) = rememberLoad("game-$eventId") { GameApi.summary(eventId) }
    val title = (l as? Load.Ok)?.v?.let { "${it.away.abbr} @ ${it.home.abbr}" } ?: "Game"
    Scaffold(topBar = {
        TopAppBar(
            title = { Text(title, fontWeight = FontWeight.Bold) },
            navigationIcon = { TextButton(onClick = onBack) { Text("‹ Back", color = Color.White) } },
            actions = { TextButton(onClick = retry) { Text("Refresh", color = Color.White) } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = EaglesGreen, titleContentColor = Color.White)
        )
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            LoadView(l, retry) { g -> GameBody(g, weekText) }
        }
    }
}

@Composable
private fun GameBody(g: GameSummary, weekText: String) {
    var tab by remember { mutableIntStateOf(0) }
    val teams = listOf(g.away, g.home)
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        ScoreHeader(g, weekText)
        TabRow(selectedTabIndex = tab, containerColor = Color.White, contentColor = EaglesGreen) {
            teams.forEachIndexed { i, t ->
                Tab(selected = tab == i, onClick = { tab = i }, text = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(t.logo, null, Modifier.size(24.dp)); Spacer(Modifier.width(6.dp)); Text(t.abbr, fontWeight = FontWeight.Bold)
                    }
                })
            }
        }
        val t = teams[tab]
        val b = g.box[t.id]
        if (b == null || (b.teamStats.isEmpty() && b.cats.isEmpty())) {
            Text(if (g.state == "pre") "Box score is available once the game starts." else "ESPN has no box score for this game yet.", Modifier.padding(16.dp), color = Color.Gray)
        } else {
            if (b.teamStats.isNotEmpty()) {
                SectionTitle("${t.name} team stats")
                b.teamStats.forEach { (k, v) ->
                    Row(Modifier.fillMaxWidth().padding(16.dp, 4.dp)) { Text(k, Modifier.weight(1f), fontSize = 14.sp); Text(v, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
                    HorizontalDivider(color = Color(0xFFF0F0F0))
                }
            }
            b.cats.forEach { c -> BoxTable(c) }
        }
        if (g.scoring.isNotEmpty()) {
            SectionTitle("Scoring summary")
            g.scoring.forEach { p ->
                Row(Modifier.fillMaxWidth().padding(16.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (p.logo.isNotBlank()) AsyncImage(p.logo, null, Modifier.size(24.dp)) else Spacer(Modifier.size(24.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.text, fontSize = 13.sp)
                        Text(listOf(qName(p.period), p.clock, p.type).filter { it.isNotBlank() }.joinToString(" · "), fontSize = 11.sp, color = Color.Gray)
                    }
                    Text("${p.away}-${p.home}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
                HorizontalDivider(color = Color(0xFFF0F0F0))
            }
        }
        Highlights(g, weekText)
        Spacer(Modifier.height(32.dp))
    }
}

private fun qName(p: Int) = when (p) { 0 -> ""; in 1..4 -> "Q$p"; 5 -> "OT"; else -> "OT${p - 4}" }

@Composable
private fun SectionTitle(t: String) =
    Text(t, Modifier.fillMaxWidth().background(Color(0xFFEEEEEE)).padding(16.dp, 8.dp), fontWeight = FontWeight.Bold, color = EaglesGreen)

@Composable
private fun ScoreHeader(g: GameSummary, weekText: String) {
    Column(Modifier.fillMaxWidth().background(Color(0xFFE8F0F1)).padding(12.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TeamScore(g.away, Modifier.weight(1f))
            Column(Modifier.width(96.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val live = g.state == "in"
                Text(if (live) "LIVE" else g.statusDetail.ifBlank { "Final" }, fontWeight = FontWeight.Bold, fontSize = 13.sp,
                    color = if (live) Color(0xFFC62828) else Color.DarkGray, textAlign = TextAlign.Center)
                if (live && g.statusDetail.isNotBlank()) Text(g.statusDetail, fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center)
                if (weekText.isNotBlank()) Text(weekText, fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center)
            }
            TeamScore(g.home, Modifier.weight(1f))
        }
        val n = maxOf(g.away.lines.size, g.home.lines.size)
        if (n > 0) {
            Spacer(Modifier.height(8.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(Color.White).padding(8.dp)) {
                Row { Text("", Modifier.width(56.dp)); (1..n).forEach { Text(qName(it), Modifier.weight(1f), fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center) }
                    Text("T", Modifier.weight(1f), fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold) }
                listOf(g.away, g.home).forEach { t ->
                    Row {
                        Text(t.abbr, Modifier.width(56.dp), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        (0 until n).forEach { i -> Text(t.lines.getOrElse(i) { "" }, Modifier.weight(1f), fontSize = 13.sp, textAlign = TextAlign.Center) }
                        Text(t.score, Modifier.weight(1f), fontSize = 13.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        if (g.venue.isNotBlank()) Text(g.venue, Modifier.padding(top = 6.dp).fillMaxWidth(), fontSize = 11.sp, color = Color.Gray, textAlign = TextAlign.Center)
    }
}

@Composable
private fun TeamScore(t: GTeam, m: Modifier) {
    Column(m, horizontalAlignment = Alignment.CenterHorizontally) {
        AsyncImage(t.logo, null, Modifier.size(52.dp))
        Text(t.abbr, fontWeight = FontWeight.Bold)
        Text(t.score.ifBlank { "–" }, fontSize = 30.sp, fontWeight = if (t.winner) FontWeight.ExtraBold else FontWeight.Normal)
        if (t.record.isNotBlank()) Text(t.record, fontSize = 11.sp, color = Color.Gray)
    }
}

@Composable
private fun BoxTable(c: BoxCat) {
    Text(c.title, Modifier.padding(16.dp, 12.dp, 16.dp, 4.dp), fontWeight = FontWeight.Bold, fontSize = 16.sp, color = EaglesGreen)
    Row(Modifier.padding(horizontal = 16.dp)) {
        Column {
            Cell("Player", 140, true)
            c.rows.forEach { (n, _) -> Cell(n, 140, false) }
            if (c.totals.isNotEmpty()) Cell("Team", 140, true)
        }
        Column(Modifier.horizontalScroll(rememberScrollState())) {
            Row { c.labels.forEach { Cell(it, 58, true) } }
            c.rows.forEach { (_, v) -> Row { v.forEach { Cell(it, 58, false) } } }
            if (c.totals.isNotEmpty()) Row(Modifier.background(Color(0xFFE8F0F1))) { c.totals.forEach { Cell(it, 58, true) } }
        }
    }
}

private fun youtubeQuery(g: GameSummary, weekText: String): String {
    val wk = when (g.seasonType) {
        1 -> "preseason week ${g.week}"
        3 -> weekText.ifBlank { "playoffs" }
        else -> "week ${g.week}"
    }
    return "${g.away.name} vs ${g.home.name} highlights ${g.season} $wk NFL"
}

@Composable
private fun Highlights(g: GameSummary, weekText: String) {
    val ctx = LocalContext.current
    var sel by remember { mutableStateOf<Highlight?>(g.videos.firstOrNull()) }
    var playing by remember { mutableStateOf(false) }
    SectionTitle("Highlights")
    val openYt = {
        val q = youtubeQuery(g, weekText)
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
    }
    if (g.videos.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("ESPN has no highlight clips for this game (ESPN clips usually expire a couple of days after the game).", fontSize = 13.sp, color = Color.Gray, textAlign = TextAlign.Center)
            Spacer(Modifier.height(10.dp))
            Button(onClick = openYt, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCC0000))) { Text("▶  Watch highlights on YouTube") }
        }
        return
    }
    val s = sel ?: return
    Column(Modifier.fillMaxWidth().padding(16.dp, 10.dp)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(Color.Black)) {
            if (playing) key(s.id) { VideoPlayer(s) }
            else {
                AsyncImage(s.thumb, null, Modifier.fillMaxSize().clickable { playing = true }, contentScale = ContentScale.Crop)
                Box(Modifier.align(Alignment.Center).size(64.dp).clip(RoundedCornerShape(32.dp)).background(Color(0xAA000000)).clickable { playing = true }, Alignment.Center) {
                    Text("▶", color = Color.White, fontSize = 30.sp)
                }
            }
        }
        Text(s.title, Modifier.padding(top = 6.dp), fontWeight = FontWeight.Bold, fontSize = 15.sp)
        if (s.duration > 0) Text(dur(s.duration) + " · ESPN", fontSize = 11.sp, color = Color.Gray)
    }
    if (g.videos.size > 1) g.videos.forEach { v ->
        val on = v.id == s.id
        Row(Modifier.fillMaxWidth().clickable { sel = v; playing = true }.background(if (on) Color(0xFFE8F0F1) else Color.Transparent).padding(16.dp, 6.dp),
            verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(v.thumb, null, Modifier.width(112.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(6.dp))
                .then(if (on) Modifier.border(2.dp, EaglesGreen, RoundedCornerShape(6.dp)) else Modifier), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(v.title, fontSize = 13.sp, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal, maxLines = 3)
                if (v.duration > 0) Text(dur(v.duration), fontSize = 11.sp, color = Color.Gray)
            }
        }
    }
    TextButton(onClick = openYt, modifier = Modifier.padding(horizontal = 8.dp)) { Text("More highlights on YouTube ›", color = Color(0xFFCC0000)) }
}

private fun dur(sec: Int) = "%d:%02d".format(sec / 60, sec % 60)

/** Plays HLS (adaptive) first; if that fails, falls back to the progressive MP4. */
@OptIn(UnstableApi::class)
@Composable
private fun VideoPlayer(v: Highlight) {
    val ctx = LocalContext.current
    var err by remember(v.id) { mutableStateOf<String?>(null) }
    val player = remember(v.id) {
        ExoPlayer.Builder(ctx).build().apply {
            fun item(url: String) = MediaItem.Builder().setUri(url).apply { if (url.contains(".m3u8")) setMimeType(MimeTypes.APPLICATION_M3U8) else setMimeType(MimeTypes.VIDEO_MP4) }.build()
            val first = v.hls.ifBlank { v.mp4 }
            var triedMp4 = first == v.mp4
            addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    if (!triedMp4 && v.mp4.isNotBlank()) { triedMp4 = true; setMediaItem(item(v.mp4)); prepare(); play() }
                    else err = "Couldn't play this clip (${error.errorCodeName}). It may have expired."
                }
            })
            setMediaItem(item(first)); prepare(); playWhenReady = true
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true } }, modifier = Modifier.fillMaxSize())
        err?.let { Text(it, Modifier.align(Alignment.Center).background(Color(0xCC000000)).padding(12.dp), color = Color.White, fontSize = 13.sp, textAlign = TextAlign.Center) }
    }
}
