package com.tim.nflhub

import android.graphics.BitmapFactory
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.ScreenOrientation
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.net.URL

/** v1.12 single-screen snapshots with LIVE ESPN data: sideways (Nexus 5 rotated, 640x360dp) team + League pages, and
 *  the portrait game page. */
abstract class SnapBase(device: DeviceConfig) {
    @get:Rule val p = Paparazzi(deviceConfig = device, theme = "android:Theme.Material.Light.NoActionBar")
    private val imgs = HashMap<String, Painter?>()
    fun img(url: String): Painter? = imgs.getOrPut(url) {
        try {
            val c = URL(url).openConnection() as java.net.HttpURLConnection
            c.setRequestProperty("User-Agent", "NflHub/1.12")
            if (c.responseCode != 200) null else {
                val b = c.inputStream.readBytes()
                BitmapFactory.decodeByteArray(b, 0, b.size)?.let { BitmapPainter(it.asImageBitmap()) }
            }
        } catch (e: Exception) { null }
    }
    private fun photo(abbr: String) = File("src/main/assets/team_photos/${abbr.lowercase()}.jpg").takeIf { it.exists() }
        ?.let { BitmapFactory.decodeFile(it.path)?.asImageBitmap() }
    fun shot(name: String, content: @Composable () -> Unit) = p.snapshot(name) {
        MaterialTheme(colorScheme = lightColorScheme(primary = EaglesGreen, secondary = EaglesSilver)) {
            CompositionLocalProvider(LocalImageOverride provides { u -> imgs[u] }, LocalTeamPhoto provides { a -> photo(a) }) {
                Surface(Modifier.fillMaxSize(), color = Color.White) { content() }
            }
        }
    }
    val fakeInsets = WindowInsets(top = 24.dp)
    @Composable fun FakeStatusBar() {
        Row(Modifier.fillMaxWidth().height(24.dp).padding(horizontal = 10.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text("9:41", color = Color.White, fontSize = 12.sp); Spacer(Modifier.weight(1f)); Text("▲ ▮ 87%", color = Color.White, fontSize = 11.sp)
        }
    }
    val teams by lazy { runBlocking { Api.teams() } }

    fun teamScreen(name: String, id: String, short: Boolean, collapsed: Float) {
        val team = teams.first { it.id == id }
        val hdr = runBlocking { Api.teamHeader(id) }
        val sch = runBlocking { Api.schedule(id) }
        (listOf(team.logo, NFL_SHIELD) + sch.games.map { it.oppLogo }).distinct().forEach { img(it) }
        println("SHOT $name: ${team.abbr} ${hdr.record} · ${hdr.standing}")
        val zone = java.time.ZoneId.of("America/New_York")
        shot(name) {
            val st = remember { CollapseState(collapsed) }
            Box(Modifier.fillMaxSize()) {
                CollapsingScreen(st,
                    header = { CollapsingTeamBanner(st, short, team, hdr, team.name, showHome = id != EAGLES_ID, teamsOpen = false,
                        onLeague = {}, onTeams = {}, onHome = {}, onSettings = {}, insets = fakeInsets) },
                    tabs = { TeamTabs(1, team, inline = short) {} }) {
                    Column { sch.games.filter { it.seasonType == 2 }.take(8).forEach { GameRow(it, zone); HorizontalDivider() } }
                }
                FakeStatusBar()
            }
        }
    }
}

class LandscapeTest : SnapBase(DeviceConfig.NEXUS_5.copy(screenWidth = 1920, screenHeight = 1080, orientation = ScreenOrientation.LANDSCAPE)) {
    @Test fun teamLandscape() = teamScreen("v112_team_landscape", EAGLES_ID, short = true, collapsed = 0f)
    @Test fun teamLandscapeCollapsed() = teamScreen("v112_team_landscape_collapsed", "23", short = true, collapsed = 1f)

    @Test fun leagueLandscape() {
        img(NFL_SHIELD)
        val data = runBlocking { StandingsApi.load() }
        (data.divisions.flatMap { d -> d.rows.map { it.logo } } + listOf("afc", "nfc").map { "https://a.espncdn.com/i/teamlogos/nfl/500/$it.png" }).forEach { img(it) }
        shot("v112_league_landscape") { LeagueTheme {
            val st = remember { CollapseState() }
            Box(Modifier.fillMaxSize()) {
                CollapsingScreen(st,
                    header = { CollapsingLeagueBanner(st, true, onHome = {}, onTeams = {}, onSettings = {}, insets = fakeInsets) },
                    tabs = { LeagueTabs(2, inline = true) {} }) {
                    DivisionsView(data) {}
                }
                FakeStatusBar()
            }
        } }
    }
}

class PortraitV112Test : SnapBase(DeviceConfig.NEXUS_5) {
    @Test fun teamPortrait() = teamScreen("v112_team_portrait", EAGLES_ID, short = false, collapsed = 0f)
    @Test fun teamPortraitCollapsed() = teamScreen("v112_team_portrait_collapsed", EAGLES_ID, short = false, collapsed = 1f)

    @Test fun gamePortrait() {
        val sch = runBlocking { Api.schedule(EAGLES_ID) }
        val g = sch.games.last { it.state == "post" }
        val sum = runBlocking { GameApi.summary(g.id) }
        (listOf(sum.away.logo, sum.home.logo) + sum.scoring.map { it.logo }).distinct().forEach { img(it) }
        println("SHOT game ${g.id} ${sum.away.abbr} ${sum.away.score} @ ${sum.home.abbr} ${sum.home.score}")
        shot("v112_game_portrait") {
            GameScreenContent(Load.Ok(sum), {}, scheduleWeekLabel(g), onBack = {})
        }
    }
}
