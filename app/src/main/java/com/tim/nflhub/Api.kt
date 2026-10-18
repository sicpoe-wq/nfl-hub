package com.tim.nflhub

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class Team(val id: String, val name: String, val abbr: String, val logo: String, val color: String)
data class Player(
    val id: String, val name: String, val jersey: String, val pos: String, val group: String,
    val height: String, val weight: String, val age: String, val college: String,
    val headshot: String, val status: String, val exp: String
)
data class Injury(
    val playerId: String, val name: String, val pos: String, val status: String,
    val type: String, val comment: String, val date: String, val headshot: String
)
data class NewsItem(val headline: String, val description: String, val image: String, val published: String, val link: String)
data class StatCategory(val name: String, val rows: List<Pair<String, String>>)
data class StatTable(val labels: List<String>, val rows: List<Pair<String, List<String>>>)
data class PlayerStats(val tables: List<Pair<String, StatTable>>)

object Api {
    private const val SITE = "https://site.api.espn.com/apis/site/v2/sports/football/nfl"
    private const val COMMON = "https://site.web.api.espn.com/apis/common/v3/sports/football/nfl"

    private fun get(url: String): JSONObject {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 20000
        c.setRequestProperty("User-Agent", "NflHub/1.1")
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
                logos?.optJSONObject(0)?.str("href") ?: "", t.str("color"))
        }.sortedBy { it.name }
    }

    suspend fun news(teamId: String): List<NewsItem> = withContext(Dispatchers.IO) {
        val arr = get("$SITE/news?team=$teamId&limit=25").optJSONArray("articles") ?: return@withContext emptyList()
        (0 until arr.length()).mapNotNull {
            val x = arr.getJSONObject(it)
            val link = x.optJSONObject("links")?.optJSONObject("web")?.str("href") ?: ""
            if (x.str("headline").isBlank() || link.isBlank()) return@mapNotNull null
            val imgs = x.optJSONArray("images")
            var img = ""
            if (imgs != null) for (i in 0 until imgs.length()) { val u = imgs.getJSONObject(i).str("url"); if (u.isNotBlank()) { img = u; break } }
            NewsItem(x.str("headline"), x.str("description"), img, x.str("published"), link)
        }.sortedByDescending { it.published }
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
                    a.optJSONArray("injuries")?.optJSONObject(0)?.str("status") ?: "",
                    a.optJSONObject("experience")?.let { e -> if (e.has("years")) e.get("years").toString() else "" } ?: ""
                )
            }
        }
        out
    }

    private var injCache: Pair<Long, JSONArray>? = null

    /** Team-level /teams/{id}/injuries returns an empty object, so we use the league-wide /injuries feed and filter by team. */
    suspend fun injuries(teamId: String, force: Boolean = false): List<Injury> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val cached = injCache
        val all = if (!force && cached != null && now - cached.first < 120_000) cached.second
        else get("$SITE/injuries").getJSONArray("injuries").also { injCache = now to it }
        var team: JSONObject? = null
        for (i in 0 until all.length()) if (all.getJSONObject(i).str("id") == teamId) team = all.getJSONObject(i)
        val arr = team?.optJSONArray("injuries") ?: return@withContext emptyList()
        (0 until arr.length()).map {
            val x = arr.getJSONObject(it); val a = x.optJSONObject("athlete")
            Injury(
                a?.optJSONObject("links")?.str("x") ?: (a?.optJSONArray("links")?.optJSONObject(0)?.str("href")
                    ?.substringAfter("/id/", "")?.substringBefore("/") ?: ""),
                a?.str("displayName") ?: "", a?.optJSONObject("position")?.str("abbreviation") ?: "",
                x.str("status"), x.optJSONObject("details")?.let { d ->
                    listOf(d.str("type"), d.str("location")).filter { s -> s.isNotBlank() && s != "Not Specified" }.distinct().joinToString(" · ")
                } ?: "",
                x.str("shortComment"), x.str("date").take(10),
                a?.optJSONObject("headshot")?.str("href") ?: ""
            )
        }
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
