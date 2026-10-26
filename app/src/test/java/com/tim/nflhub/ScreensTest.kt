package com.tim.nflhub

import android.graphics.BitmapFactory
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.URL

/** Single-screen snapshots on a 360dp-wide phone (Nexus 5) using LIVE ESPN data and real logos/headshots. */
class ScreensTest {
    @get:Rule val p = Paparazzi(deviceConfig = DeviceConfig.NEXUS_5, theme = "android:Theme.Material.Light.NoActionBar")

    private val imgs = HashMap<String, Painter?>()
    private fun img(url: String): Painter? = imgs.getOrPut(url) {
        try {
            val c = URL(url).openConnection() as java.net.HttpURLConnection
            c.setRequestProperty("User-Agent", "NflHub/1.7")
            if (c.responseCode != 200) null else {
                val b = c.inputStream.readBytes()
                BitmapFactory.decodeByteArray(b, 0, b.size)?.let { BitmapPainter(it.asImageBitmap()) }
            }
        } catch (e: Exception) { null }
    }

    private fun shot(name: String, content: @Composable () -> Unit) = p.snapshot(name) {
        MaterialTheme(colorScheme = lightColorScheme(primary = EaglesGreen, secondary = EaglesSilver)) {
            CompositionLocalProvider(LocalImageOverride provides { u -> imgs[u] }) {
                Surface(Modifier.fillMaxSize(), color = Color.White) { content() }
            }
        }
    }

    private val teams by lazy { runBlocking { Api.teams() } }

    private fun teamTop(id: String, dropdown: String? = null) {
        val team = teams.first { it.id == id }
        val hdr = runBlocking { Api.teamHeader(id) }
        val sch = runBlocking { Api.schedule(id) }
        (teams.map { it.logo } + team.logo + NFL_SHIELD + sch.games.map { it.oppLogo }).distinct().forEach { img(it) }
        val zone = java.time.ZoneId.of("America/New_York")
        shot(team.abbr.lowercase() + (if (dropdown != null) "_dropdown_" + dropdown.ifBlank { "all" } else "_top")) {
            Box(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    TopBoxes(team, id != EAGLES_ID, dropdown != null, {}, {}, {}, {})
                    TeamBanner(team, hdr, team.name)
                    TeamTabs(1, team) {}
                    sch.games.filter { it.seasonType == 2 }.take(6).forEach { GameRow(it, zone); HorizontalDivider() }
                }
                if (dropdown != null) TeamsDropdown(Load.Ok(teams), {}, id, {}, {}, autoFocus = false, initialQuery = dropdown)
            }
        }
    }

    @Test fun eaglesTop() = teamTop(EAGLES_ID)
    @Test fun steelersTop() = teamTop("23")
    @Test fun cardinalsTop() = teamTop("22")
    @Test fun dropdownAll() = teamTop(EAGLES_ID, "")
    @Test fun dropdownSearch() = teamTop(EAGLES_ID, "new")

    private fun leaders(name: String, mode: Int, year: Int, cats: List<LeaderCat>, key: String) {
        val cat = cats.first { it.key == key }
        val rows = runBlocking { LeadersApi.resolve(cat) }
        rows.forEach { img(it.headshot) }
        shot(name) {
            Column(Modifier.fillMaxSize()) {
                IconTabs(3, NFL_TABS, EaglesGreen) {}
                ModeChips(mode) {}
                if (mode == 1) OutlinedButton(onClick = {}, Modifier.padding(horizontal = 12.dp)) { Text("Season: $year ▾") }
                CategoryChips(cats, key) {}
                val title = when (mode) { 0 -> "$year regular season"; 1 -> "$year regular season"; else -> "All-time career" }
                LeaderTitle("$title · ${cat.label}", leaderNote(mode, year, cat))
                LeaderRows(rows) {}
            }
        }
    }

    @Test fun leadersSeason() { val y = runBlocking { LeadersApi.currentSeason() }; leaders("leaders_season", 0, y, runBlocking { LeadersApi.seasonCats(y) }, "rushingYards") }
    @Test fun leadersPast() = leaders("leaders_past_1995", 1, 1995, runBlocking { LeadersApi.seasonCats(1995) }, "receivingYards")
    @Test fun leadersCareerRec() {
        val bundled = LeadersApi.parseBundled(File("src/main/assets/career_static.json").readText())
        leaders("leaders_career_receiving", 2, 0, bundled, "receivingYards")
    }
    @Test fun leadersCareerRush() {
        val d = Api.get("https://sports.core.api.espn.com/v2/sports/football/leagues/nfl/leaders?limit=25")
        val cats = runBlocking { LeadersApi.careerCatsFromJson(d, File("src/main/assets/career_static.json").readText()) }
        leaders("leaders_career_rushing", 2, 0, cats, "rushingYards")
    }
}
