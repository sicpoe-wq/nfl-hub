package com.tim.nflhub

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

/** Hits live ESPN endpoints. Run manually: ./gradlew testDebugUnitTest */
class LiveApiTest {
    @Test fun standings() = runBlocking {
        val s = StandingsApi.load()
        println("season=${s.season} divisions=${s.divisions.size} espnSeeds=${s.espnSeeds}")
        s.divisions.forEach { d -> println("${d.conf} ${d.name}: " + d.rows.joinToString { "${it.abbr} ${it.record} ${it.pct} div=${it.divRec} PF=${it.pf} PA=${it.pa} ${it.streak} seed=${it.seed} cl='${it.clincher}'" }) }
        s.pictures.forEach { p -> println("${p.conf} seeds: " + p.seeds.joinToString { "${it.seed}.${it.row.abbr}${if (it.divLeader) "*" else ""}" } + " | hunt: " + p.hunt.joinToString { it.abbr }) }
        assertTrue(s.divisions.size == 8 && s.divisions.all { it.rows.size == 4 })
    }
    @Test fun game() = runBlocking {
        for (id in listOf("401872998", "401872929")) {
            val g = GameApi.summary(id)
            println("$id ${g.away.abbr} ${g.away.score} @ ${g.home.abbr} ${g.home.score} ${g.statusDetail} ${g.season} t${g.seasonType} w${g.week} lines=${g.away.lines}/${g.home.lines} venue=${g.venue}")
            g.box.forEach { (t, b) -> println("  team $t: ${b.teamStats.size} stats, cats=" + b.cats.joinToString { "${it.title}(${it.rows.size})" }) }
            println("  first cat: " + g.box[g.away.id]?.cats?.firstOrNull())
            println("  scoring=${g.scoring.size} first=${g.scoring.firstOrNull()}")
            println("  videos=${g.videos.size}"); g.videos.forEach { println("    ${it.title} ${it.duration}s hls=${it.hls} mp4=${it.mp4}") }
            assertTrue(g.box.values.all { it.cats.isNotEmpty() })
        }
    }
    @Test fun bannerDivisionPlace() = runBlocking {
        val teams = Api.teams()
        val re = Regex("""^\d+(st|nd|rd|th) in (AFC|NFC) (East|North|South|West)$""")
        teams.forEach { t -> val h = Api.teamHeader(t.id); println("BANNER ${t.abbr}: ${h.record}  ·  ${h.standing}"); assertTrue(t.abbr, re.matches(h.standing)) }
        assertTrue(teams.size == 32)
    }
}
