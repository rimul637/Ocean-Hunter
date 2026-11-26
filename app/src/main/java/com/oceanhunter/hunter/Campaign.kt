package com.oceanhunter.hunter

enum class Enemy(val hp: Int, val radius: Float, val speed: Float, val damage: Int, val reward: Int) {
    SHARK(27, 27f, 118f, 15, 2), JELLY(19, 23f, 49f, 13, 2),
    SUB(40, 27f, 59f, 17, 3), CRAB(54, 27f, 51f, 18, 3),
    SNAKE(23, 23f, 126f, 13, 3), OCTOPUS(48, 31f, 62f, 17, 4),
    PUFFER(34, 24f, 67f, 13, 3), MINE(21, 23f, 38f, 24, 2),
    DRONE(32, 22f, 96f, 14, 3), MONSTER(69, 33f, 76f, 23, 5)
}
enum class Goal { DESTROY, TREASURE, SURVIVE, DESTINATION, TIMED, BOSS }
enum class Upgrade(val title: String, val stat: String, val base: Int, val step: Int, val unit: String) {
    HEALTH("HEALTH", "Hull integrity", 100, 18, " HP"),
    SPEED("SPEED", "Cruise speed", 260, 22, ""),
    TORPEDO("TORPEDO", "Torpedo damage", 14, 4, ""),
    RAPID("RAPID FIRE", "Shot interval", 360, -31, " ms"),
    SHIELD("SHIELD", "Recharge time", 9, -1, " s"),
    MAGNET("MAGNET", "Collection range", 34, 18, "")
    ;
    fun cost(rank: Int) = 24 + ordinal * 3 + rank * 23 + rank * rank * 11
    fun value(rank: Int) = "${base + step * rank}$unit"
}
enum class Power(val title: String, val seconds: Float) {
    HEAL("REPAIR", 0f), DOUBLE("DOUBLE", 12f), TRIPLE("TRIPLE", 9f), RAPID("RAPID", 11f),
    SHIELD("BARRIER", 6f), MAGNET("MAGNET", 13f), DAMAGE("BOOST", 10f), GOLD("GOLD", 0f)
}

data class World(val title: String, val boss: String, val enemies: List<Enemy>)
data class Mission(val number: Int, val name: String, val goal: Goal, val quota: Int, val span: Int, val formation: Int, val hazard: Int) {
    val world get() = (number - 1) / 10
    val objective get() = when (goal) {
        Goal.DESTROY -> "Destroy $quota enemies"
        Goal.TREASURE -> "Recover $quota treasure chests"
        Goal.SURVIVE -> "Survive for $span seconds"
        Goal.DESTINATION -> "Reach the dive beacon"
        Goal.TIMED -> "Destroy $quota enemies in ${span}s"
        Goal.BOSS -> "Defeat ${Campaign.worlds[world].boss}"
    }
}

object Campaign {
    val worlds = listOf(
        World("Coral Kingdom", "Giant Crab", listOf(Enemy.PUFFER, Enemy.JELLY, Enemy.CRAB, Enemy.SHARK)),
        World("Shark Territory", "Mega Shark", listOf(Enemy.SHARK, Enemy.SNAKE, Enemy.JELLY, Enemy.PUFFER)),
        World("Sunken Ship", "Ghost Submarine", listOf(Enemy.MINE, Enemy.SUB, Enemy.DRONE, Enemy.CRAB)),
        World("Frozen Ocean", "Ice Serpent", listOf(Enemy.SNAKE, Enemy.JELLY, Enemy.DRONE, Enemy.SHARK)),
        World("Volcanic Depths", "Lava Monster", listOf(Enemy.MONSTER, Enemy.CRAB, Enemy.MINE, Enemy.SUB)),
        World("Monster Kingdom", "Giant Kraken", listOf(Enemy.OCTOPUS, Enemy.MONSTER, Enemy.SNAKE, Enemy.DRONE))
    )
    // Name, mission objective, quota, duration, formation and hazard track.
    private val rows = """
        First Descent|D|8|0|0|0
        Lantern Reef|T|3|0|1|0
        Anemone Alley|S|30|0|2|1
        Turquoise Crossing|R|36|0|3|0
        Pearl Patrol|D|12|0|4|1
        Needle Coral|C|11|40|2|2
        Garden of Shells|T|5|0|3|1
        Cracked Atoll|S|42|0|1|2
        Emerald Bulwark|D|17|0|4|2
        Crown of Coral|B|0|0|0|0
        Fin Shadows|S|36|0|1|0
        Blue Chasm|R|40|0|2|1
        Baited Waters|D|13|0|3|0
        Hunter's Wake|C|15|46|0|1
        Sharkbone Bluff|T|4|0|4|2
        Night Current|S|51|0|3|2
        Narrow Teeth|D|19|0|2|1
        Red Fin Run|C|19|48|4|2
        Abyssal Pass|R|52|0|0|2
        Apex Below|B|0|0|1|0
        Broken Mast|T|4|0|2|0
        Bell in the Depths|D|14|0|1|1
        Cannon Gallery|R|42|0|4|1
        Cargo Hold|T|6|0|0|2
        Signal Lost|S|47|0|3|2
        Rust Fleet|D|19|0|2|1
        Powder Trail|C|17|44|4|2
        Captain's Cache|T|7|0|1|2
        Blackwater Channel|R|55|0|3|2
        The Unquiet Wreck|B|0|0|2|0
        Glacial Gate|R|39|0|3|1
        Frost Bloom|D|15|0|0|0
        Icebound Pearls|T|5|0|4|1
        Shiver Current|S|43|0|2|2
        Crystal Maze|R|50|0|1|2
        Whitewater Raid|C|18|46|3|1
        Polar Hollow|T|6|0|0|2
        Blue Ice Run|S|54|0|4|2
        Frozen Pursuit|D|23|0|2|2
        Heart of Winter|B|0|0|3|0
        Cinder Shelf|S|39|0|4|1
        Obsidian Vault|T|5|0|2|2
        Ashwake Patrol|D|17|0|1|1
        Vent Crossing|R|46|0|3|2
        Ring of Fire|C|19|49|0|2
        Magma Channels|S|53|0|2|2
        Ember Reliquary|T|7|0|1|2
        Pyre Guard|D|24|0|4|2
        Furnace Run|R|58|0|3|2
        Living Furnace|B|0|0|4|0
        Black Lantern|D|19|0|3|1
        Sunless Vault|T|6|0|2|2
        Whispering Stone|R|48|0|0|2
        Watchers Below|S|51|0|4|1
        Eldritch Patrol|C|21|50|1|2
        Ruin of Tides|T|8|0|3|2
        Deepest Crossing|R|60|0|2|2
        Last Defense|D|28|0|4|2
        The Throne Gate|S|64|0|0|2
        King of the Abyss|B|0|0|0|0
    """.trimIndent().lines()
    val missions = rows.mapIndexed { index, row ->
        val (name, code, quota, span, formation) = row.split('|')
        val goal = when (code) {
            "D" -> Goal.DESTROY
            "T" -> Goal.TREASURE
            "S" -> Goal.SURVIVE
            "R" -> Goal.DESTINATION
            "C" -> Goal.TIMED
            else -> Goal.BOSS
        }
        Mission(index + 1, name, goal, quota.toInt(), if (span == "0") quota.toInt() else span.toInt(), formation.toInt(), row.last().digitToInt())
    }
}
