package com.tim.nflhub

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(teams: Load<List<Team>>, retryTeams: () -> Unit, onBack: () -> Unit, onOrderChanged: (List<String>) -> Unit, onDefaultChanged: (String) -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Scaffold(contentWindowInsets = WindowInsets.safeDrawing, topBar = {
        Column(Modifier.background(EaglesGreen)) {
            TopAppBar(
                windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = EaglesGreen, titleContentColor = Color.White)
            )
            TabRow(selectedTabIndex = tab, containerColor = EaglesGreen, contentColor = Color.White,
                modifier = Modifier.windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal))) {
                listOf("Default Team", "Roster Order").forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
        }
    }) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (tab) {
                0 -> LoadView(teams, retryTeams) { DefaultTeamSettings(it, onDefaultChanged) }
                else -> RosterOrderSettings(onOrderChanged)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefaultTeamSettings(teams: List<Team>, onChanged: (String) -> Unit) {
    val ctx = LocalContext.current
    var current by remember { mutableStateOf(Prefs.defaultTeam(ctx)) }
    var expanded by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val cur = teams.firstOrNull { it.id == current }
    fun choose(t: Team) { current = t.id; Prefs.setDefaultTeam(ctx, t.id); onChanged(t.id); query = ""; expanded = false }

    val q = query.trim().lowercase()
    val suggestions = if (q.isEmpty()) emptyList() else teams.filter {
        it.name.lowercase().contains(q) || it.abbr.lowercase().startsWith(q) || it.name.lowercase().split(" ").any { w -> w.startsWith(q) }
    }.sortedBy { if (it.abbr.lowercase() == q || it.name.lowercase().startsWith(q)) 0 else 1 }

    LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("The app opens to this team at launch.", color = Color.Gray, fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Text("Choose from list", fontWeight = FontWeight.Bold, color = EaglesGreen)
            Spacer(Modifier.height(6.dp))
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = cur?.name ?: "Philadelphia Eagles", onValueChange = {}, readOnly = true,
                    label = { Text("Default team") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                    teams.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t.name, fontWeight = if (t.id == current) FontWeight.Bold else FontWeight.Normal) },
                            leadingIcon = { AsyncImage(t.logo, null, Modifier.size(28.dp)) },
                            onClick = { choose(t) }
                        )
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
            Text("Or search by name", fontWeight = FontWeight.Bold, color = EaglesGreen)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text("Type a city, team name or abbreviation") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Default.Close, "Clear") } },
                modifier = Modifier.fillMaxWidth()
            )
            if (q.isNotEmpty() && suggestions.isEmpty()) Text("No matching teams", Modifier.padding(12.dp), color = Color.Gray)
        }
        items(suggestions) { t ->
            Row(Modifier.fillMaxWidth().clickable { choose(t) }.padding(8.dp, 10.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(t.logo, null, Modifier.size(32.dp)); Spacer(Modifier.width(12.dp))
                Text(t.name, Modifier.weight(1f)); Text(t.abbr, color = Color.Gray)
            }
            HorizontalDivider()
        }
    }
}

@Composable
fun RosterOrderSettings(onChanged: (List<String>) -> Unit) {
    val ctx = LocalContext.current
    var order by remember { mutableStateOf(Prefs.rosterOrder(ctx)) }
    fun save(o: List<String>) { order = o; Prefs.setRosterOrder(ctx, o); onChanged(o) }
    fun move(key: String, dir: Int) {
        val unit = Positions.byKey(key)!!.unit
        val inUnit = order.filter { Positions.byKey(it)?.unit == unit }
        val i = inUnit.indexOf(key); val j = i + dir
        if (j !in inUnit.indices) return
        val swapped = inUnit.toMutableList().also { val t = it[i]; it[i] = it[j]; it[j] = t }
        // Rebuild the full list keeping each unit's slots, only permuting within the unit.
        var n = 0
        save(order.map { if (Positions.byKey(it)?.unit == unit) swapped[n++] else it })
    }
    LazyColumn(Modifier.fillMaxSize()) {
        item {
            Column(Modifier.padding(16.dp)) {
                Text("Choose the order position groups are listed on the Roster tab. Use the arrows to move a group up or down within Offense, Defense or Special Teams.", color = Color.Gray, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { Prefs.resetRosterOrder(ctx); order = Prefs.rosterOrder(ctx); onChanged(order) }) {
                    Icon(Icons.Default.Restore, null); Spacer(Modifier.width(6.dp)); Text("Reset to default")
                }
            }
        }
        Positions.units.forEach { unit ->
            val keys = order.filter { Positions.byKey(it)?.unit == unit }
            item { Text(unit, Modifier.fillMaxWidth().background(Color(0xFFEEEEEE)).padding(16.dp, 8.dp), fontWeight = FontWeight.Bold, color = EaglesGreen) }
            items(keys.size) { idx ->
                val k = keys[idx]
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${idx + 1}.", Modifier.width(28.dp), color = Color.Gray)
                    Text(Positions.byKey(k)!!.label, Modifier.weight(1f))
                    IconButton(onClick = { move(k, -1) }, enabled = idx > 0) { Icon(Icons.Default.KeyboardArrowUp, "Move up") }
                    IconButton(onClick = { move(k, 1) }, enabled = idx < keys.size - 1) { Icon(Icons.Default.KeyboardArrowDown, "Move down") }
                }
                HorizontalDivider()
            }
        }
    }
}
