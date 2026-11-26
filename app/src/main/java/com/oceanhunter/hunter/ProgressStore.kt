package com.oceanhunter.hunter

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.oceanData by preferencesDataStore("ocean_progress")

data class Progress(
    val coins: Int = 0, val unlocked: Int = 1, val best: Int = 0,
    val stars: List<Int> = List(60) { 0 }, val upgrades: List<Int> = List(6) { 0 },
    val music: Boolean = true, val sfx: Boolean = true
)
data class DiveResult(val id: String, val level: Int, val cleared: Boolean, val stars: Int, val score: Int, val coins: Int, val bonus: Int)

class ProgressStore(context: Context) {
    private val disk = context.applicationContext.oceanData
    private val coins = intPreferencesKey("coins")
    private val unlocked = intPreferencesKey("unlocked")
    private val best = intPreferencesKey("best")
    private val music = booleanPreferencesKey("music")
    private val sfx = booleanPreferencesKey("sfx")
    private val receipts = stringSetPreferencesKey("reward_receipts")
    private val stars = List(60) { intPreferencesKey("stars_${it + 1}") }
    private val upgrades = Upgrade.entries.map { intPreferencesKey("upgrade_${it.name}") }

    val progress = disk.data.catch { if (it is IOException) emit(emptyPreferences()) else throw it }.map(::read)

    private fun read(p: Preferences) = Progress(
        p[coins] ?: 0, p[unlocked] ?: 1, p[best] ?: 0,
        stars.map { p[it] ?: 0 }, upgrades.map { p[it] ?: 0 }, p[music] ?: true, p[sfx] ?: true
    )

    suspend fun buy(upgrade: Upgrade, expectedRank: Int): Boolean {
        val key = upgrades[upgrade.ordinal]
        var purchased = false
        disk.edit { p ->
            val rank = p[key] ?: 0
            val balance = p[coins] ?: 0
            if (rank == expectedRank && rank < 5 && balance >= upgrade.cost(rank)) {
                p[coins] = balance - upgrade.cost(rank)
                p[key] = rank + 1
                purchased = true
            }
        }
        return purchased
    }

    suspend fun toggleMusic() { disk.edit { it[music] = !(it[music] ?: true) } }
    suspend fun toggleSfx() { disk.edit { it[sfx] = !(it[sfx] ?: true) } }

    suspend fun reward(result: DiveResult) {
        disk.edit { p ->
            val paid = p[receipts].orEmpty()
            if (result.id in paid) return@edit
            p[receipts] = paid + result.id
            p[coins] = ((p[coins] ?: 0).toLong() + result.coins + result.bonus).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            if (result.level == 0) p[best] = maxOf(p[best] ?: 0, result.score)
            if (result.cleared && result.level in 1..60) {
                val key = stars[result.level - 1]
                p[key] = maxOf(p[key] ?: 0, result.stars)
                p[unlocked] = maxOf(p[unlocked] ?: 1, (result.level + 1).coerceAtMost(60))
            }
        }
    }
}
