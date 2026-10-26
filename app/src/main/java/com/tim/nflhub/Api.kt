package com.tim.nflhub

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Team(val id: String, val name: String, val abbr: String, val logo: String, val color: String, val nickname: String = "",
                val altColor: String = "", val location: String = "")
/** Banner detail line from ESPN /teams/{id}: overall record (e.g. "2-4") and standing (e.g. "2nd in NFC East"). */
data class TeamHeader(val record: String, val standing: String)
data class Player(
    val id: String, val name: String, val jersey: String, val pos: String, val group: String,
    val height: String, val weight: String, val age: String, val college: String,
    val headshot: String, val status: String, val exp: String
)
data class Injury(
    val playerId: String, val name: String, val pos: String, val status: String,
    val type: String, val comment: String, val date: String, val headshot: String,
    val jersey: String = ""
)
/** Full injury info for one player; blank string = ESPN did not provide it. */
data class InjuryDetail(
    val status: String, val bodyPart: String, val location: String, val detail: String, val side: String,
    val returnDate: String, val fantasyStatus: String, val shortComment: String, val longComment: String, val date: String
)
data class NewsItem(val headline: String, val description: String, val image: String, val published: String, val link: String)
data class Game(
    val id: String, val seasonType: Int, val weekNum: Int, val weekText: String, val date: String, val timeValid: Boolean,
    val home: Boolean, val oppName: String, val oppAbbr: String, val oppLogo: String,
    val tv: String, val venue: String, val venueCity: String, val state: String, val statusDetail: String,
    val myScore: String, val oppScore: String, val result: String
)
data class Schedule(val games: List<Game>, val byeWeek: Int, val seasonYear: String)
data class StatCategory(val name: String, val rows: List<Pair<String, String>>)
data class StatTable(val labels: List<String>, val rows: List<Pair<String, List<String>>>)
data class PlayerStats(val tables: List<Pair<String, StatTable>>)

object Api {
    private const val SITE = "https://site.api.espn.com/apis/site/v2/sports/football/nfl"
    private const val COMMON = "https://site.web.api.espn.com/apis/common/v3/sports/football/nfl"

    internal fun get(url: String): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 20000
        c.setRequestProperty("User-Agent", "NflHub/1.7")
        try {
            if (c.responseCode != 200) throw RuntimeException("HTTP ${c.responseCode}")
            return JSONObject(c.inputStream.bufferedReader().readText())
        } finally { c.disconnect() }
    }

    private fun JSONObject.str(k: String) = optString(k, "")

    suspend fun teams(): List<Team> = withContext(Dispatchers.IO) {
        val arr = get("$SITE/teams").getJSONArray("sports").getJSONObject(0)
            .getJSONArray("leagues").getJSONObject(0).getJSONArray("teams")
        (0 until arr.length()).map {
            val t = arr.getJSONObject(it).getJSONObject("team")
            val logos = t.optJSONArray("logos")
            Team(t.str("id"), t.str("displayName"), t.str("abbreviation"),
                logos?.optJSONObject(0)?.str("href") ?: "", t.str("color"), t.optString("name", t.str("shortDisplayName")),
                t.str("alternateColor"), t.str("location"))
        }.sortedBy { it.name }
    }

    suspend fun teamHeader(teamId: String): TeamHeader = withContext(Dispatchers.IO) {
        val t = get("$SITE/teams/$teamId").getJSONObject("team")
        TeamHeader(t.optJSONObject("record")?.optJSONArray("items")?.optJSONObject(0)?.str("summary") ?: "", t.str("standingSummary"))
    }

    private fun parseNews(arr: JSONArray?, keep: (JSONObject) -> Boolean = { true }): List<NewsItem> {
        arr ?: return emptyList()
        return (0 until arr.length()).mapNotNull {
            val x = arr.getJSONObject(it)
            if (!keep(x)) return@mapNotNull null
            val link = x.optJSONObject("links")?.optJSONObject("web")?.str("href") ?: ""
            if (x.str("headline").isBlank() || link.isBlank()) return@mapNotNull null
            val imgs = x.optJSONArray("images")
            var img = ""
            if (imgs != null) for (i in 0 until imgs.length()) { val u = imgs.getJSONObject(i).str("url"); if (u.isNotBlank()) { img = u; break } }
            NewsItem(x.str("headline"), x.str("description"), img, x.str("published"), link)
        }.distinctBy { it.link }.sortedByDescending { it.published }
    }

    private fun teamTagIds(x: JSONObject): List<String> {
        val cats = x.optJSONArray("categories") ?: return emptyList()
        return (0 until cats.length()).mapNotNull {
            val c = cats.getJSONObject(it)
            if (c.str("type") != "team") null
            else c.optString("teamId", "").ifBlank { c.optJSONObject("team")?.optString("id", "") ?: "" }.ifBlank { null }
        }.distinct()
    }

    /**
     * ESPN's ?team= feed only returns articles tagged with the team, but many are league-wide roundups
     * (power rankings, "Week 6 takeaways") that tag 8-32 teams. Keep an article only if it's tagged with this team AND
     * it's tagged with at most 2 teams (team stories, game previews/recaps) or the headline names the team.
     */
    suspend fun news(teamId: String, nickname: String = ""): List<NewsItem> = withContext(Dispatchers.IO) {
        val arr = get("$SITE/news?team=$teamId&limit=50").optJSONArray("articles")
        val nick = nickname.trim()
        val rx = if (nick.isNotBlank()) Regex("\\b" + Regex.escape(nick) + "\\b", RegexOption.IGNORE_CASE) else null
        parseNews(arr) { x ->
            val ids = teamTagIds(x)
            teamId in ids && (ids.size <= 2 || (rx != null && rx.containsMatchIn(x.str("headline"))))
        }
    }

    suspend fun leagueNews(): List<NewsItem> = withContext(Dispatchers.IO) {
        parseNews(get("$SITE/news?limit=50").optJSONArray("articles"))
    }

    private fun parseGames(teamId: String, d: JSONObject): List<Game> {
        val ev = d.optJSONArray("events") ?: return emptyList()
        return (0 until ev.length()).mapNotNull { i ->
            val e = ev.getJSONObject(i)
            val c = e.optJSONArray("competitions")?.optJSONObject(0) ?: return@mapNotNull null
            val comps = c.optJSONArray("competitors") ?: return@mapNotNull null
            var me: JSONObject? = null; var opp: JSONObject? = null
            for (k in 0 until comps.length()) { val t = comps.getJSONObject(k); if (t.optJSONObject("team")?.str("id") == teamId) me = t else opp = t }
            if (me == null || opp == null) return@mapNotNull null
            val ot = opp.optJSONObject("team")
            fun score(t: JSONObject): String = when (val sc = t.opt("score")) {
                is JSONObject -> sc.optString("displayValue", "")
                null -> ""
                else -> sc.toString()
            }
            val st = c.optJSONObject("status")?.optJSONObject("type")
            val state = st?.str("state") ?: ""
            val ms = score(me); val os = score(opp)
            val result = if (state == "post" && ms.isNotBlank() && os.isNotBlank()) {
                val a = ms.toDoubleOrNull() ?: 0.0; val b = os.toDoubleOrNull() ?: 0.0
                when { a > b -> "W"; a < b -> "L"; else -> "T" }
            } else ""
            val b = c.optJSONArray("broadcasts")
            val tv = if (b == null) "" else (0 until b.length()).mapNotNull { j ->
                b.getJSONObject(j).optJSONObject("media")?.str("shortName")?.ifBlank { null } }.distinct().joinToString(", ")
            val v = c.optJSONObject("venue")
            val addr = v?.optJSONObject("address")
            val city = listOf(addr?.str("city") ?: "", addr?.str("state") ?: "").filter { it.isNotBlank() }.joinToString(", ")
            val wk = e.optJSONObject("week")
            val stype = e.optJSONObject("seasonType")?.optInt("type", 0) ?: 0
            Game(
                e.str("id"), stype, wk?.optInt("number", 0) ?: 0, wk?.str("text") ?: "",
                e.str("date"), e.optBoolean("timeValid", c.optBoolean("timeValid", true)) && c.optBoolean("timeValid", true),
                me.str("homeAway") == "home", ot?.optString("displayName", "") ?: "", ot?.str("abbreviation") ?: "",
                ot?.optJSONArray("logos")?.optJSONObject(0)?.str("href") ?: "",
                tv, v?.str("fullName") ?: "", city, state, st?.str("shortDetail") ?: "", ms, os, result
            )
        }
    }

    /** Preseason (1), regular season (2) and postseason (3). Default call returns current season type + byeWeek. */
    suspend fun schedule(teamId: String): Schedule = withContext(Dispatchers.IO) {
        val base = "$SITE/teams/$teamId/schedule"
        val main = get(base)
        val year = main.optJSONObject("requestedSeason")?.optString("year", "") ?: ""
        val bye = main.optInt("byeWeek", 0)
        val q = if (year.isNotBlank()) "&season=$year" else ""
        val all = mutableListOf<Game>()
        for (t in 1..3) {
            val d = try { get("$base?seasontype=$t$q") } catch (e: Exception) { null } ?: continue
            if (d.optJSONObject("requestedSeason")?.optInt("type", t) != t) continue
            all += parseGames(teamId, d)
        }
        if (all.none { it.seasonType == 2 }) all += parseGames(teamId, main)
        Schedule(all.distinctBy { it.id }.sortedBy { it.date }, bye, year)
    }

    suspend fun roster(teamId: String): List<Player> = withContext(Dispatchers.IO) {
        val groups = get("$SITE/teams/$teamId/roster").getJSONArray("athletes")
        val out = mutableListOf<Player>()
        for (g in 0 until groups.length()) {
            val grp = groups.getJSONObject(g)
            val items = grp.optJSONArray("items") ?: continue
            for (i in 0 until items.length()) {
                val a = items.getJSONObject(i)
                out += Player(
                    a.str("id"), a.optString("displayName", a.str("fullName")), a.str("jersey"),
                    a.optJSONObject("position")?.str("abbreviation") ?: "",
                    grp.str("position"),
                    a.str("displayHeight"), a.str("displayWeight"),
                    if (a.has("age")) a.get("age").toString() else "",
                    a.optJSONObject("college")?.str("name") ?: "",
                    a.optJSONObject("headshot")?.str("href") ?: "",
                    a.optJSONArray("injuries")?.optJSONObject(0)?.str("status")?.takeIf { it.isNotBlank() }
                        ?: a.optJSONObject("status")?.str("name")?.takeIf { it.equals("Day-To-Day", true) } ?: "",
                    a.optJSONObject("experience")?.let { e -> if (e.has("years")) e.get("years").toString() else "" } ?: ""
                )
            }
        }
        out
    }

    private var injCache: Pair<Long, JSONArray>? = null

    private fun isInjured(st: String) = st.isNotBlank() && !st.equals("Active", true) && !st.equals("Practice Squad", true)

    private fun feedIdOf(a: JSONObject?): String {
        a ?: return ""
        a.str("id").let { if (it.isNotBlank() && !it.startsWith("-")) return it }
        val links = a.optJSONArray("links") ?: return ""
        for (i in 0 until links.length()) {
            val h = links.getJSONObject(i).str("href")
            if (h.contains("/id/")) return h.substringAfter("/id/").substringBefore("/")
        }
        return ""
    }

    private fun feedInjuries(teamId: String, force: Boolean): List<Injury> {
        val now = System.currentTimeMillis()
        val cached = injCache
        val all = try {
            if (!force && cached != null && now - cached.first < 120_000) cached.second
            else get("$SITE/injuries").getJSONArray("injuries").also { injCache = now to it }
        } catch (e: Exception) { return emptyList() }
        var team: JSONObject? = null
        for (i in 0 until all.length()) if (all.getJSONObject(i).str("id") == teamId) team = all.getJSONObject(i)
        val arr = team?.optJSONArray("injuries") ?: return emptyList()
        return (0 until arr.length()).map {
            val x = arr.getJSONObject(it); val a = x.optJSONObject("athlete")
            Injury(
                feedIdOf(a),
                a?.str("displayName") ?: "", a?.optJSONObject("position")?.str("abbreviation") ?: "",
                x.str("status"), x.optJSONObject("details")?.let { d ->
                    listOf(d.str("type"), d.str("location")).filter { s -> s.isNotBlank() && s != "Not Specified" }.distinct().joinToString(" · ")
                } ?: "",
                x.str("shortComment"), x.str("date").take(10),
                a?.optJSONObject("headshot")?.str("href") ?: ""
            )
        }
    }

    /**
     * ESPN's league-wide /injuries feed is incomplete (it omitted 7 of 20 flagged Eagles on the roster, e.g. IR players)
     * and also lists "Active" players with old notes. So we merge: roster-flagged players + feed entries with an injured status,
     * deduped by athlete id (fallback: name). Feed data wins where present (it has comments/body part).
     */
    suspend fun injuries(teamId: String, force: Boolean = false): List<Injury> = withContext(Dispatchers.IO) {
        val feed = feedInjuries(teamId, force).filter { isInjured(it.status) }
        val roster = try { roster(teamId) } catch (e: Exception) { emptyList() }
        val byKey = LinkedHashMap<String, Injury>()
        fun key(id: String, name: String) = if (id.isNotBlank()) id else "n:$name"
        val rosterById = roster.associateBy { it.id }
        val rosterByName = roster.associateBy { it.name }
        for (f in feed) {
            val r = rosterById[f.playerId] ?: rosterByName[f.name]
            val id = f.playerId.ifBlank { r?.id ?: "" }
            byKey[key(id, f.name)] = f.copy(playerId = id, jersey = r?.jersey ?: "",
                pos = f.pos.ifBlank { r?.pos ?: "" }, headshot = f.headshot.ifBlank { r?.headshot ?: "" })
        }
        for (p in roster) if (isInjured(p.status) && !byKey.containsKey(key(p.id, p.name)))
            byKey[key(p.id, p.name)] = Injury(p.id, p.name, p.pos, p.status, "", "", "", p.headshot, p.jersey)
        val rank = { s: String -> when { s.equals("Out", true) -> 0; s.contains("Reserve", true) -> 1; s.equals("Doubtful", true) -> 2
            s.equals("Questionable", true) -> 3; else -> 4 } }
        byKey.values.sortedWith(compareBy<Injury> { rank(it.status) }.thenBy { it.name })
    }

    /** Roster with the SAME merged injury list as the Injuries tab applied: injured players get group "injured" and the merged status. */
    suspend fun rosterWithInjuries(teamId: String): List<Player> = withContext(Dispatchers.IO) {
        val roster = roster(teamId)
        val inj = try { injuries(teamId, force = true) } catch (e: Exception) { emptyList() }
        val byId = inj.filter { it.playerId.isNotBlank() }.associateBy { it.playerId }
        val byName = inj.associateBy { it.name }
        roster.map { p ->
            val i = byId[p.id] ?: byName[p.name]
            when {
                i != null -> p.copy(group = "injured", status = i.status)
                p.group == "injuredReserveOrOut" -> p.copy(group = "injured", status = p.status.ifBlank { "Injured Reserve" })
                else -> p
            }
        }
    }

    /** Per-athlete injury detail from ESPN's athlete endpoint (has returnDate, body part, long comment). Null if none. */
    suspend fun injuryDetail(playerId: String): InjuryDetail? = withContext(Dispatchers.IO) {
        if (playerId.isBlank()) return@withContext null
        try {
            val inj = get("$COMMON/athletes/$playerId").optJSONObject("athlete")?.optJSONArray("injuries")?.optJSONObject(0)
                ?: return@withContext null
            val d = inj.optJSONObject("details")
            fun clean(s: String?) = if (s.isNullOrBlank() || s == "Not Specified") "" else s
            InjuryDetail(
                inj.str("status"), clean(d?.str("type")), clean(d?.str("location")), clean(d?.str("detail")), clean(d?.str("side")),
                d?.str("returnDate")?.take(10) ?: "", d?.optJSONObject("fantasyStatus")?.str("description") ?: "",
                inj.str("shortComment"), inj.str("longComment"), inj.str("date").take(10)
            )
        } catch (e: Exception) { null }
    }

    suspend fun teamStats(teamId: String): List<StatCategory> = withContext(Dispatchers.IO) {
        val cats = get("$SITE/teams/$teamId/statistics").getJSONObject("results")
            .getJSONObject("stats").getJSONArray("categories")
        (0 until cats.length()).map {
            val c = cats.getJSONObject(it); val st = c.getJSONArray("stats")
            StatCategory(c.optString("displayName", c.str("name")), (0 until st.length()).map { j ->
                val s = st.getJSONObject(j); s.optString("displayName") to s.optString("displayValue")
            })
        }
    }

    suspend fun playerStats(playerId: String): PlayerStats = withContext(Dispatchers.IO) {
        val cats = get("$COMMON/athletes/$playerId/stats").optJSONArray("categories")
            ?: return@withContext PlayerStats(emptyList())
        val tables = (0 until cats.length()).mapNotNull {
            val c = cats.getJSONObject(it)
            val labels = c.getJSONArray("labels").let { l -> (0 until l.length()).map { i -> l.getString(i).trim() } }
            val st = c.optJSONArray("statistics") ?: JSONArray()
            val rows = mutableListOf<Pair<String, List<String>>>()
            for (j in 0 until st.length()) {
                val r = st.getJSONObject(j)
                val vals = r.getJSONArray("stats").let { l -> (0 until l.length()).map { i -> l.getString(i) } }
                rows += "${r.getJSONObject("season").str("year")} ${r.str("teamSlug").split("-").lastOrNull()?.replaceFirstChar { ch -> ch.uppercase() } ?: ""}" to vals
            }
            c.optJSONArray("totals")?.let { t -> rows += "Career" to (0 until t.length()).map { i -> t.getString(i) } }
            if (rows.isEmpty()) null else c.optString("displayName", c.str("name")) to StatTable(labels, rows)
        }
        PlayerStats(tables)
    }
}
