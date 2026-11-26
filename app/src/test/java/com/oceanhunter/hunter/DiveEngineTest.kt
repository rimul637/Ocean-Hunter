package com.oceanhunter.hunter

import org.junit.Assert.*
import org.junit.Test

class DiveEngineTest {
    private val step = 1f / 120f

    @Test
    fun campaignHasSixtyDistinctMissionsAndSixBosses() {
        assertEquals((1..60).toList(), Campaign.missions.map { it.number })
        assertEquals(60, Campaign.missions.map { it.name }.toSet().size)
        assertEquals(Goal.entries.toSet(), Campaign.missions.map { it.goal }.toSet())
        assertEquals((10..60 step 10).toList(), Campaign.missions.filter { it.goal == Goal.BOSS }.map { it.number })
        assertEquals(Enemy.entries.toSet(), Campaign.worlds.flatMap { it.enemies }.toSet())
    }

    @Test
    fun torpedoImpactDestroysEnemyAndAwardsScore() {
        val engine = DiveEngine(Campaign.missions.first(), Progress())
        engine.actors.add(Entity().apply { set(Kind.ENEMY, 420f, engine.y, 23f, Enemy.JELLY.ordinal, 10f); hp = 12 })
        engine.fire = true
        repeat(60) { engine.advance(step) }
        assertEquals(1, engine.kills)
        assertTrue(engine.score > 0)
        assertTrue(engine.actors.any { it.kind == Kind.COIN })
    }

    @Test
    fun shieldExpiresAndBlocksDamageWhileActive() {
        val engine = DiveEngine(Campaign.missions.first(), Progress())
        engine.shield()
        fun hazard() = Entity().apply { set(Kind.HAZARD, engine.x, engine.y, 22f, 26, 5f); age = 2f }
        engine.actors.add(hazard())
        engine.advance(step)
        assertEquals(100, engine.health)
        repeat(1200) { engine.advance(step) }
        assertEquals(0f, engine.recharge, .01f)
        assertTrue(engine.health < 100)
    }

    @Test
    fun missionVictoryAndTimedFailureAreReportedExactlyOnce() {
        val outcomes = mutableListOf<DiveResult>()
        val fast = Mission(1, "Survive", Goal.SURVIVE, 0, 1, 0, 0)
        val win = DiveEngine(fast, Progress(), finish = outcomes::add)
        repeat(300) { win.advance(step) }
        assertEquals(1, outcomes.size)
        assertTrue(outcomes.single().cleared)
        val timer = Mission(1, "Timer", Goal.TIMED, 99, 1, 0, 0)
        val loss = DiveEngine(timer, Progress(), finish = outcomes::add)
        repeat(300) { loss.advance(step) }
        assertEquals(2, outcomes.size)
        assertFalse(outcomes.last().cleared)
    }

    @Test
    fun bossClearAndPeriodicEndlessBossUseSameCombat() {
        for (mission in Campaign.missions.filter { it.goal == Goal.BOSS }) {
            val outcomes = mutableListOf<DiveResult>()
            val bossRoom = DiveEngine(mission, Progress(), finish = outcomes::add)
            repeat(540) { bossRoom.advance(step) }
            val boss = checkNotNull(bossRoom.boss)
            assertEquals(mission.world, boss.code)
            boss.hp = 1
            bossRoom.actors.add(Entity().apply { set(Kind.TORPEDO, boss.x, boss.y, 8f, 15, 2f) })
            bossRoom.advance(step)
            assertTrue(outcomes.single().cleared)
            assertTrue(outcomes.single().bonus > 30)
        }
        val endless = DiveEngine(null, Progress(upgrades = List(6) { 5 }))
        repeat(8820) {
            endless.actors.filter { entity -> entity.kind == Kind.ENEMY || entity.kind == Kind.HAZARD }.forEach { e -> e.life = 0f }
            endless.advance(step)
        }
        assertNotNull(endless.boss)
        assertFalse(endless.ended)
    }

    @Test
    fun temporaryPowerUpsResetAndUpgradeCostsIncrease() {
        val engine = DiveEngine(null, Progress())
        engine.actors.add(Entity().apply { set(Kind.POWER, engine.x, engine.y, 12f, Power.TRIPLE.ordinal, 1f) })
        engine.advance(step)
        assertEquals(9f, engine.effects[Power.TRIPLE.ordinal], .001f)
        repeat(1100) { engine.advance(step) }
        assertEquals(0f, engine.effects[Power.TRIPLE.ordinal], .001f)
        assertTrue(DiveEngine(null, Progress()).effects.all { it == 0f })
        for (upgrade in Upgrade.entries) assertTrue((0..4).zipWithNext().all { upgrade.cost(it.first) < upgrade.cost(it.second) })
    }
}
