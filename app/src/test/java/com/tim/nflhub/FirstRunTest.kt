package com.tim.nflhub

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Pure logic for the public build's first-run homepage picker (no network). */
class FirstRunLogicTest {
    private val teams = listOf(
        Team("21", "Philadelphia Eagles", "PHI", "", "", "Eagles", "", "Philadelphia"),
        Team("23", "Pittsburgh Steelers", "PIT", "", "", "Steelers", "", "Pittsburgh"),
        Team("22", "Arizona Cardinals", "ARI", "", "", "Cardinals", "", "Arizona"),
        Team("19", "New York Giants", "NYG", "", "", "Giants", "", "New York"),
        Team("20", "New York Jets", "NYJ", "", "", "Jets", "", "New York"),
    )

    @Test fun emptyQueryPutsLeagueFirstThenTeamsAlphabetical() {
        val o = firstRunOptions(teams, "")
        assertEquals(LEAGUE_ID, o.first().first)
        assertEquals(listOf("Arizona Cardinals", "New York Giants", "New York Jets", "Philadelphia Eagles", "Pittsburgh Steelers"), o.drop(1).map { it.second })
    }
    @Test fun searchNarrowsCaseInsensitive() {
        assertEquals(listOf("21"), firstRunOptions(teams, "PH").map { it.first })
        assertEquals(listOf("21"), firstRunOptions(teams, "eag").map { it.first })
        assertEquals(listOf("19", "20"), firstRunOptions(teams, "new york").map { it.first })
        assertEquals(listOf("20"), firstRunOptions(teams, "nyj").map { it.first })
        assertTrue(firstRunOptions(teams, "zzz").isEmpty())
    }
    @Test fun leagueRowMatchesNflOrLeague() {
        assertEquals(LEAGUE_ID, firstRunOptions(teams, "nfl").first().first)
        assertEquals(LEAGUE_ID, firstRunOptions(teams, "league").first().first)
        assertFalse(firstRunOptions(teams, "ph").any { it.first == LEAGUE_ID })
    }
}

/** v1.13 single-screen phone snapshots (LIVE ESPN team list + logos). */
class FirstRunShotsTest : SnapBase(DeviceConfig.NEXUS_5) {
    private fun loadImgs() { (teams.map { it.logo } + NFL_SHIELD).distinct().forEach { img(it) } }

    @Test fun pickerAll() { loadImgs(); shot("v113_firstrun_picker") { LeagueTheme { Box(Modifier.fillMaxSize()) {
        FirstRunPicker(Load.Ok(teams), {}, {}, insets = fakeInsets); FakeStatusBar() } } } }

    @Test fun pickerSearch() { loadImgs(); shot("v113_firstrun_search_ph") { LeagueTheme { Box(Modifier.fillMaxSize()) {
        FirstRunPicker(Load.Ok(teams), {}, {}, initialQuery = "ph", insets = fakeInsets); FakeStatusBar() } } } }

    @Test fun settingsLeagueOption() { loadImgs(); shot("v113_settings_nfl_option") {
        SettingsScreen(Load.Ok(teams), {}, {}, {}, {}, initialCurrent = LEAGUE_ID, initialQuery = "nf") } }
}
