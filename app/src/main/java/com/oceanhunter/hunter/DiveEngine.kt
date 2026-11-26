package com.oceanhunter.hunter

import java.util.UUID
import kotlin.math.*
import kotlin.random.Random

internal const val SEA_WIDTH = 960f
internal const val SEA_HEIGHT = 540f

internal enum class Kind { ENEMY, BOSS, TORPEDO, HOSTILE, COIN, CHEST, POWER, HAZARD, PARTICLE, BEACON }
internal class Entity {
    var kind = Kind.ENEMY
    var x = 0f
    var y = 0f
    var vx = 0f
    var vy = 0f
    var origin = 0f
    var radius = 0f
    var age = 0f
    var life = 0f
    var hp = 0
    var code = 0
    var timer = 0f
    var warning = 0f
    var flash = 0f
    fun set(kind: Kind, x: Float, y: Float, radius: Float, code: Int, life: Float) {
        this.kind = kind
        this.x = x
        this.y = y
        this.radius = radius
        this.code = code
        this.life = life
        origin = y
        vx = 0f
        vy = 0f
        age = 0f
        timer = 0f
        warning = 0f
        flash = 0f
        hp = 0
    }
}

internal enum class Cue { CLICK, FIRE, BLAST, COIN, TREASURE, SHIELD, HURT, UPGRADE, WIN, LOSE }

internal class DiveEngine(
    val mission: Mission?, progress: Progress, private val sound: (Cue) -> Unit = {},
    private val finish: (DiveResult) -> Unit = {}
) {
    private val id = UUID.randomUUID().toString()
    private val random = Random(mission?.number?.times(10271) ?: Random.nextInt())
    private val ranks = progress.upgrades
    private val pool = ArrayDeque<Entity>(480).apply { repeat(480) { addLast(Entity()) } }
    val actors = ArrayList<Entity>(480)
    val maxHealth = Upgrade.HEALTH.base + ranks[0] * Upgrade.HEALTH.step
    val effects = FloatArray(Power.entries.size)
    var health = maxHealth
        private set
    var elapsed = 0f
        private set
    var score = 0
        private set
    var coins = 0
        private set
    var kills = 0
        private set
    var treasures = 0
        private set
    var world = mission?.world ?: random.nextInt(6)
        private set
    var x = 174f
        private set
    var y = 267f
        private set
    var tilt = 0f
        private set
    var barrier = 0f
        private set
    var recharge = 0f
        private set
    var invulnerable = 0f
        private set
    var boss: Entity? = null
        private set
    var ended = false
        private set
    var moveX = 0f
    var moveY = 0f
    var fire = false
    private var vx = 0f
    private var vy = 0f
    private var shotClock = 0f
    private var waveClock = 2f
    private var hazardClock = 12f
    private var chestClock = 3.5f
    private var wave = 0
    private var spawnedBoss = 0
    private var beacon = false

    private fun add(kind: Kind, x: Float, y: Float, radius: Float, code: Int = 0, life: Float = 20f): Entity? =
        pool.removeFirstOrNull()?.also { it.set(kind, x, y, radius, code, life); actors.add(it) }

    fun shield() {
        if (ended || recharge > 0) return
        barrier = 2.6f + ranks[4] * .35f
        recharge = (9 - ranks[4]).toFloat()
        sound(Cue.SHIELD)
    }

    fun advance(dt: Float) {
        if (ended) return
        elapsed += dt
        barrier = (barrier - dt).coerceAtLeast(0f)
        recharge = (recharge - dt).coerceAtLeast(0f)
        invulnerable = (invulnerable - dt).coerceAtLeast(0f)
        for (i in effects.indices) effects[i] = (effects[i] - dt).coerceAtLeast(0f)

        val speed = 260f + ranks[1] * 22
        val length = hypot(moveX, moveY).coerceAtLeast(1f)
        val blend = (dt * 7).coerceAtMost(1f)
        vx += (moveX / length * speed - vx) * blend
        vy += (moveY / length * speed - vy) * blend
        x = (x + vx * dt).coerceIn(42f, 915f)
        y = (y + vy * dt).coerceIn(90f, 445f)
        tilt = vy * .065f
        shotClock -= dt
        if (fire && shotClock <= 0f) launch()
        schedule(dt)

        val count = actors.size
        for (index in 0 until count) {
            if (ended) break
            val e = actors[index]
            if (e.life <= 0) continue
            e.age += dt
            e.life -= dt
            e.flash -= dt
            e.x += e.vx * dt
            e.y += e.vy * dt
            when (e.kind) {
                Kind.ENEMY -> updateEnemy(e, dt)
                Kind.BOSS -> updateBoss(e, dt)
                Kind.TORPEDO -> hitEnemy(e)
                Kind.HOSTILE, Kind.HAZARD -> {
                    if ((e.kind == Kind.HOSTILE || e.age > 1.15f) && touches(e, x, y, 16f)) {
                        hurt(e.code)
                        if (e.kind == Kind.HOSTILE) e.life = 0f
                    }
                }
                Kind.CHEST, Kind.COIN, Kind.POWER, Kind.BEACON -> collect(e)
                Kind.PARTICLE -> Unit
            }
        }
        for (index in actors.lastIndex downTo 0) {
            val e = actors[index]
            if (e.life <= 0f || e.x !in -130f..1150f || e.y !in -130f..630f) {
                val last = actors.removeAt(actors.lastIndex)
                if (index < actors.size) actors[index] = last
                pool.addLast(e)
            }
        }
        if (ended) return
        if (health <= 0) {
            close(false)
            return
        }
        when (mission?.goal) {
            Goal.SURVIVE -> if (elapsed >= mission.span) close(true)
            Goal.TREASURE -> if (treasures >= mission.quota) close(true)
            Goal.DESTROY -> if (kills >= mission.quota) close(true)
            Goal.TIMED -> if (kills >= mission.quota) close(true) else if (elapsed >= mission.span) close(false)
            else -> Unit
        }
    }

    private fun launch() {
        val spread = when {
            effects[Power.TRIPLE.ordinal] > 0 -> 3
            effects[Power.DOUBLE.ordinal] > 0 -> 2
            else -> 1
        }
        val boost = if (effects[Power.DAMAGE.ordinal] > 0) 1.65f else 1f
        for (i in 0 until spread) {
            add(Kind.TORPEDO, x + 32, y, 9f, ((14 + ranks[2] * 4) * boost).toInt(), 2f)?.apply {
                vx = 515f
                vy = (i - (spread - 1) / 2f) * 100
            }
        }
        shotClock = (.36f - ranks[3] * .031f) * if (effects[Power.RAPID.ordinal] > 0) .55f else 1f
        sound(Cue.FIRE)
    }

    private fun schedule(dt: Float) {
        if (mission == null && elapsed >= (spawnedBoss + 1) * 72f && boss == null) {
            if (spawnedBoss > 0) world = (world + 1 + random.nextInt(5)) % 6
            spawnBoss(world)
        } else if (mission?.goal == Goal.BOSS && spawnedBoss == 0 && elapsed > 3f) spawnBoss(world)

        if (mission?.goal == Goal.DESTINATION && elapsed >= mission.span && !beacon) {
            beacon = add(Kind.BEACON, 880f, 165f + mission.formation * 48, 32f, life = 99999f) != null
        }
        if (mission?.goal == Goal.TREASURE) {
            chestClock -= dt
            if (chestClock <= 0f) {
                add(Kind.CHEST, 980f, 124f + random.nextInt(277), 23f, life = 19f)?.vx = -94f
                chestClock = 4.5f
            }
        }
        if (boss != null || mission?.goal == Goal.BOSS) return
        waveClock -= dt
        if (waveClock <= 0f) {
            val formation = mission?.formation ?: wave % 5
            val enemies = Campaign.worlds[world].enemies
            val difficulty = mission?.let { (it.number - 1) / 9f } ?: (elapsed / 35f).coerceAtMost(12f)
            val count = if (wave > 1 && (wave + formation) % 3 == 0) 3 else 2
            repeat(count) { index ->
                val lane = when (formation) {
                    0 -> 130 + (wave * 83 + index * 105) % 290
                    1 -> 137 + index * 138
                    2 -> 125 + ((wave + index) % 3) * 132
                    3 -> (y + if (index == 0) -100 else 110).coerceIn(110f, 424f).toInt()
                    else -> 136 + (wave * 61 + index * 63) % 264
                }
                val type = enemies[(wave + index * 2 + formation) % enemies.size]
                add(Kind.ENEMY, 1005f + index * 67f, lane.toFloat(), type.radius, type.ordinal, 30f)?.apply {
                    hp = type.hp + (difficulty * 2.3f).toInt()
                    vx = -type.speed - (difficulty * 5).coerceAtMost(50f)
                    timer = 1f + index * .5f
                }
            }
            wave++
            waveClock = (3.4f - difficulty * .16f).coerceAtLeast(1.55f)
        }
        val hazard = mission?.hazard ?: if (elapsed > 24) 2 else 0
        if (hazard == 0) return
        hazardClock -= dt
        if (hazardClock <= 0f) {
            val lane = 118 + random.nextInt(296)
            add(Kind.HAZARD, minOf(880f, x + 110), lane.toFloat(), 28f + hazard * 10, 15 + hazard * 2, 3.1f)
            hazardClock = 10f - hazard * 2
        }
    }

    private fun spawnBoss(stage: Int) {
        val entry = add(Kind.BOSS, 1060f, 250f, 65f, stage, 99999f) ?: return
        spawnedBoss++
        boss = entry.apply {
            hp = bossMax(stage)
            timer = 2.65f
        }
    }

    fun bossMax(stage: Int) = 370 + stage * 68 + if (mission == null) spawnedBoss.coerceAtMost(20) * 25 else 0

    private fun updateBoss(e: Entity, dt: Float) {
        e.x += ((if (e.code == 1 && e.warning < .3f && e.timer < .82f) 600f else 793f) - e.x) * dt * 1.7f
        e.y += (250f + sin(e.age * (.85f + e.code * .11f)) * (65f + e.code * 6) - e.y) * dt * 2f
        if (e.age < 1.6f) return
        e.timer -= dt
        e.warning = if (e.timer in 0f..0.82f) e.timer else 0f
        if (e.timer <= 0f) {
            val fury = e.hp < bossMax(e.code) / 2
            val aim = atan2(y - e.y, x - e.x)
            when (e.code) {
                0 -> fan(e, PI.toFloat(), if (fury) 7 else 5, .15f, 226f)
                1 -> fan(e, aim, if (fury) 5 else 3, .22f, 315f)
                2 -> {
                    fan(e, aim, if (fury) 5 else 2, .18f, 255f)
                    add(Kind.HAZARD, x.coerceAtMost(865f), y, 46f, 19, 2.7f)
                }
                3 -> for (i in 0..4) {
                    if (i != wave % 5) add(Kind.HOSTILE, 954f, 112f + i * 78, 13f, 17, 6f)?.vx = if (fury) -340f else -245f
                }
                4 -> {
                    fan(e, PI.toFloat(), if (fury) 8 else 5, .16f, 221f)
                    add(Kind.HAZARD, x, y, 48f, 25, 2.6f)
                }
                else -> {
                    fan(e, aim, if (fury) 7 else 4, .13f, 258f)
                    add(Kind.HAZARD, x, y, 56f, 22, 2.65f)
                    if (fury) add(Kind.HAZARD, x - 70, y - 105, 45f, 22, 2.95f)
                }
            }
            wave++
            e.timer = if (fury) 2.75f else 3.65f
        }
        if (touches(e, x, y, 12f)) hurt(23)
    }

    private fun fan(e: Entity, angle: Float, count: Int, step: Float, speed: Float) {
        repeat(count) { i ->
            val heading = angle + (i - (count - 1) / 2f) * step
            add(Kind.HOSTILE, e.x - e.radius * .6f, e.y, 9f, if (e.kind == Kind.BOSS) 17 + e.code else 12, 6f)?.apply {
                vx = cos(heading) * speed
                vy = sin(heading) * speed
            }
        }
    }

    private fun updateEnemy(e: Entity, dt: Float) {
        val type = Enemy.entries[e.code]
        val angle = atan2(y - e.y, x - e.x)
        e.timer -= dt
        when (type) {
            Enemy.SHARK -> { e.vy = (y - e.y).coerceIn(-70f, 70f); if (e.age > 1.1f && e.age < 1.7f) e.x -= dt * 115f }
            Enemy.JELLY -> { e.y = e.origin + sin(e.age * 2f) * 36f; e.warning = if (e.age % 3f > 2.35f) .6f else 0f }
            Enemy.SUB -> if (e.timer <= 0f && e.x in (x + 130)..935f) { fan(e, angle, 1, 0f, 230f); e.timer = 2.65f }
            Enemy.CRAB -> e.y = e.origin + abs(sin(e.age * 3f)) * 22f
            Enemy.SNAKE -> e.y = e.origin + sin(e.age * 4.4f) * 59f
            Enemy.OCTOPUS -> if (e.timer <= 0f && e.x in (x + 150)..900f) {
                add(Kind.HAZARD, e.x - 104, e.y, 40f, type.damage, 2.4f)
                e.timer = 3.4f
            }
            Enemy.PUFFER -> e.radius = type.radius + (1 + sin(e.age * 2.8f)) * 7f
            Enemy.MINE -> e.y = e.origin + sin(e.age * 1.5f) * 14f
            Enemy.DRONE -> e.vy = (y - e.y).coerceIn(-110f, 110f)
            Enemy.MONSTER -> if (e.timer <= 0f && e.x in (x + 140)..920f) { fan(e, angle, 3, .23f, 223f); e.timer = 3f }
        }
        if (touches(e, x, y, if (e.warning > 0f) 38f else 12f)) {
            hurt(type.damage)
            if (type == Enemy.MINE) { e.life = 0f; burst(e.x, e.y, 17, 2); sound(Cue.BLAST) }
        }
    }

    private fun hitEnemy(shot: Entity) {
        for (i in actors.indices) {
            val e = actors[i]
            if (e.life <= 0 || (e.kind != Kind.ENEMY && e.kind != Kind.BOSS)) continue
            if (e.kind == Kind.BOSS && e.age < 1.3f) continue
            if (!touches(e, shot.x, shot.y, shot.radius)) continue
            shot.life = 0f
            e.hp -= shot.code
            e.flash = .13f
            if (e.hp <= 0) {
                e.life = 0f
                kills++
                val big = e.kind == Kind.BOSS
                val reward = if (big) 24 else Enemy.entries[e.code].reward
                score += if (big) 1750 else reward * 55
                burst(e.x, e.y, if (big) 48 else 13, 2)
                add(Kind.COIN, e.x, e.y, 12f, reward, 8f)?.vx = -79f
                if (kills % 5 == 0 && !big) add(Kind.POWER, e.x, e.y - 26, 15f, random.nextInt(Power.entries.size), 9f)?.vx = -72f
                sound(Cue.BLAST)
                if (big) {
                    boss = null
                    coins += reward
                    if (mission != null) close(true)
                    else add(Kind.POWER, e.x - 50, e.y, 16f, Power.HEAL.ordinal, 12f)?.vx = -92f
                }
            }
            return
        }
    }

    private fun collect(e: Entity) {
        val magnet = 34f + ranks[5] * 18 + if (effects[Power.MAGNET.ordinal] > 0) 130 else 0
        if (e.kind == Kind.COIN && touches(e, x, y, magnet)) {
            e.vx = (x - e.x) * 9
            e.vy = (y - e.y) * 9
        }
        if (!touches(e, x, y, 18f)) return
        e.life = 0f
        when (e.kind) {
            Kind.CHEST -> { treasures++; coins += 10; score += 380; sound(Cue.TREASURE) }
            Kind.COIN -> { coins += e.code; score += e.code * 12; sound(Cue.COIN) }
            Kind.POWER -> {
                val power = Power.entries[e.code]
                when (power) {
                    Power.HEAL -> health = (health + maxHealth / 3).coerceAtMost(maxHealth)
                    Power.GOLD -> { coins += 15; score += 450 }
                    else -> effects[e.code] = power.seconds
                }
                sound(Cue.TREASURE)
            }
            Kind.BEACON -> close(true)
            else -> Unit
        }
        burst(e.x, e.y, 9, 1)
    }

    private fun hurt(amount: Int) {
        if (invulnerable > 0 || barrier > 0 || effects[Power.SHIELD.ordinal] > 0 || ended) return
        health = (health - amount).coerceAtLeast(0)
        invulnerable = 1.05f
        burst(x, y, 11, 0)
        sound(Cue.HURT)
    }

    private fun touches(e: Entity, x: Float, y: Float, radius: Float): Boolean {
        val dx = x - e.x
        val dy = y - e.y
        val r = radius + e.radius
        return dx * dx + dy * dy <= r * r
    }

    private fun burst(x: Float, y: Float, count: Int, color: Int) {
        repeat(count) {
            val angle = random.nextFloat() * (2 * PI).toFloat()
            val speed = 27f + random.nextFloat() * 179
            add(Kind.PARTICLE, x, y, 2.4f + random.nextFloat() * 5, color, .35f + random.nextFloat() * .47f)?.apply {
                vx = cos(angle) * speed
                vy = sin(angle) * speed - 27
            }
        }
    }

    private fun close(clear: Boolean) {
        if (ended) return
        ended = true
        val stars = when {
            !clear -> 0
            health >= maxHealth * .71f && score >= (mission?.world ?: 0) * 110 + 450 -> 3
            health >= maxHealth * .35f -> 2
            else -> 1
        }
        val bonus = if (clear) 10 + (mission?.world ?: 0) * 3 + stars * 4 + if (mission?.goal == Goal.BOSS) 30 else 0 else 0
        sound(if (clear) Cue.WIN else Cue.LOSE)
        finish(DiveResult(id, mission?.number ?: 0, clear, stars, score, coins, bonus))
    }
}
