package com.tim.nflhub

import android.content.Context
import android.content.SharedPreferences

/** Position groups, by unit. Each group maps several ESPN position abbreviations. */
data class PosGroup(val key: String, val label: String, val unit: String, val abbrs: Set<String>)

object Positions {
    val all = listOf(
        PosGroup("QB", "Quarterbacks (QB)", "Offense", setOf("QB")),
        PosGroup("RB", "Running Backs (RB/FB)", "Offense", setOf("RB", "FB", "HB")),
        PosGroup("WR", "Wide Receivers (WR)", "Offense", setOf("WR")),
        PosGroup("TE", "Tight Ends (TE)", "Offense", setOf("TE")),
        PosGroup("OL", "Offensive Line (C/G/OT)", "Offense", setOf("C", "G", "OT", "T", "OL", "OG")),
        PosGroup("DL", "Defensive Line (DE/DT)", "Defense", setOf("DE", "DT", "DL", "NT")),
        PosGroup("LB", "Linebackers (LB)", "Defense", setOf("LB", "ILB", "OLB", "MLB")),
        PosGroup("DB", "Defensive Backs (CB/S)", "Defense", setOf("CB", "S", "FS", "SS", "DB")),
        PosGroup("K", "Kickers (K)", "Special Teams", setOf("PK", "K")),
        PosGroup("P", "Punters (P)", "Special Teams", setOf("P")),
        PosGroup("LS", "Long Snappers (LS)", "Special Teams", setOf("LS")),
    )
    val units = listOf("Offense", "Defense", "Special Teams")
    val defaultOrder = all.map { it.key }
    fun byKey(k: String) = all.firstOrNull { it.key == k }
    fun keyForAbbr(abbr: String) = all.firstOrNull { abbr.uppercase() in it.abbrs }?.key
}

object Prefs {
    private fun sp(c: Context): SharedPreferences = c.getSharedPreferences("nflhub", Context.MODE_PRIVATE)

    /** Saved startup page: a team id or LEAGUE_ID. Unsaved: Eagles (personal build) / The League (public build). */
    fun defaultTeam(c: Context) = sp(c).getString("default_team", null) ?: BuildConfig.DEFAULT_TEAM
    fun hasDefaultTeam(c: Context) = sp(c).contains("default_team")
    fun setDefaultTeam(c: Context, id: String) = sp(c).edit().putString("default_team", id).apply()

    /** Always returns a complete, valid ordering (unknown/missing keys repaired). */
    fun rosterOrder(c: Context): List<String> {
        val saved = sp(c).getString("roster_order", null)?.split(",")?.filter { Positions.byKey(it) != null }?.distinct() ?: emptyList()
        return saved + Positions.defaultOrder.filter { it !in saved }
    }
    fun setRosterOrder(c: Context, order: List<String>) = sp(c).edit().putString("roster_order", order.joinToString(",")).apply()
    fun resetRosterOrder(c: Context) = sp(c).edit().remove("roster_order").apply()
}
