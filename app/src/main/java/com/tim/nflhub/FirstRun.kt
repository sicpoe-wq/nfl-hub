package com.tim.nflhub

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Pseudo-team id for "open to The League page at startup". */
const val LEAGUE_ID = "nfl"
const val LEAGUE_LABEL = "NFL (The League)"

/** Shared live-search rule (case-insensitive substring of full name, city, nickname or abbreviation). */
fun teamMatches(t: Team, query: String): Boolean {
    val s = query.trim()
    return s.isEmpty() || listOf(t.name, t.location, t.nickname, t.abbr).any { it.contains(s, ignoreCase = true) }
}

/** The NFL row shows when the search is empty or matches NFL / The League / National Football League. */
fun leagueMatches(query: String): Boolean {
    val s = query.trim()
    // Word-prefix match so e.g. "eag" (Eagles) doesn't hit "lEAGue".
    return s.isEmpty() || listOf("NFL", "The League", "National Football League").any { p ->
        p.startsWith(s, ignoreCase = true) || p.split(" ").any { it.startsWith(s, ignoreCase = true) } }
}

/** Options for the first-run picker in display order: League row (if it matches) then matching teams A–Z. */
fun firstRunOptions(teams: List<Team>, query: String): List<Pair<String, String>> =
    (if (leagueMatches(query)) listOf(LEAGUE_ID to LEAGUE_LABEL) else emptyList()) +
        teams.filter { teamMatches(it, query) }.sortedBy { it.name }.map { it.id to it.name }

/** Full-screen first-launch chooser (public build): pick the startup homepage. No dismiss without a choice. */
@Composable
fun FirstRunPicker(teamsL: Load<List<Team>>, retry: () -> Unit, onPick: (String) -> Unit,
                   initialQuery: String = "", autoFocus: Boolean = false,
                   insets: WindowInsets = WindowInsets.safeDrawing) {
    var q by remember { mutableStateOf(initialQuery) }
    val fr = remember { FocusRequester() }
    Column(Modifier.fillMaxSize().background(Color.White).testTag("firstRunPicker")) {
        Column(Modifier.fillMaxWidth().background(Brush.linearGradient(LeagueGradient))
            .windowInsetsPadding(insets.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
            .padding(16.dp, 14.dp, 16.dp, 14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            NetImage(NFL_SHIELD, Modifier.size(52.dp))
            Spacer(Modifier.height(8.dp))
            Text("Choose your team homepage", color = Color.White, fontFamily = WordmarkFonts.anton, fontSize = 26.sp, textAlign = TextAlign.Center)
            Text("What team do you follow?", color = Color.White.copy(alpha = 0.9f), fontSize = 15.sp, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.height(12.dp))
            TextField(q, { q = it }, Modifier.fillMaxWidth().focusRequester(fr).testTag("firstRunSearch"), singleLine = true,
                placeholder = { Text("Search team, city or abbr", maxLines = 1) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Default.Close, "Clear") } },
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = NflNavy))
        }
        Box(Modifier.fillMaxWidth().height(3.dp).background(NflRed))
        LoadView(teamsL, retry) { teams ->
            val opts = firstRunOptions(teams, q)
            val byId = teams.associateBy { it.id }
            if (opts.isEmpty()) Text("No teams match \"${q.trim()}\"", Modifier.padding(16.dp), color = Color.Gray)
            LazyColumn(Modifier.fillMaxSize(), contentPadding = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom).asPaddingValues()) {
                items(opts, key = { it.first }) { (id, label) ->
                    val isLeague = id == LEAGUE_ID
                    Row(Modifier.fillMaxWidth().clickable { onPick(id) }.testTag("pick_$id")
                        .background(if (isLeague) LeagueTint else Color.Transparent)
                        .windowInsetsPadding(insets.only(WindowInsetsSides.Horizontal)).padding(16.dp, 10.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        NetImage(if (isLeague) NFL_SHIELD else byId[id]?.logo ?: "", Modifier.size(36.dp)); Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(label, fontSize = 16.sp, fontWeight = if (isLeague) FontWeight.Bold else FontWeight.Medium, color = if (isLeague) NflNavy else Color.Unspecified)
                            if (isLeague) Text("League news, scores, standings & leaders", fontSize = 12.sp, color = Color.Gray)
                        }
                        Text(if (isLeague) "NFL" else byId[id]?.abbr ?: "", fontSize = 12.sp, color = Color.Gray)
                    }
                    HorizontalDivider(color = Color(0xFFE6E6E6))
                }
            }
        }
    }
    if (autoFocus) LaunchedEffect(Unit) { try { fr.requestFocus() } catch (_: Exception) {} }
}
