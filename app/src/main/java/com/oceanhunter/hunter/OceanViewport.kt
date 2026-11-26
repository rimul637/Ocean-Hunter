package com.oceanhunter.hunter

import kotlin.math.min

internal const val SEA_WIDTH = 960f
internal const val SEA_HEIGHT = 540f

/** Uniform world scale and an independent, inset-aware layout for controls. */
internal class OceanViewport {
    var scale = 1f
        private set
    var width = SEA_WIDTH
        private set
    var height = SEA_HEIGHT
        private set
    var hudScale = 1f
        private set
    var hudLeft = 0f
        private set
    var hudTop = 0f
        private set
    var hudWidth = SEA_WIDTH
        private set
    var hudHeight = SEA_HEIGHT
        private set

    val centerX get() = hudWidth / 2
    val joystickY get() = hudHeight - 88f
    val fireX get() = hudWidth - 83f
    val fireY get() = hudHeight - 92f
    val shieldX get() = hudWidth - 190f
    val shieldY get() = hudHeight - 71f
    val pauseX get() = hudWidth - 56f

    fun update(pixelWidth: Int, pixelHeight: Int, left: Int, top: Int, right: Int, bottom: Int) {
        if (pixelWidth <= 0 || pixelHeight <= 0) return
        scale = min(pixelWidth / SEA_WIDTH, pixelHeight / SEA_HEIGHT)
        width = pixelWidth / scale
        height = pixelHeight / scale

        val safeWidth = (pixelWidth - left - right).coerceAtLeast(1) / scale
        val safeHeight = (pixelHeight - top - bottom).coerceAtLeast(1) / scale
        hudLeft = left / scale
        hudTop = top / scale
        hudScale = min(1f, min(safeWidth / SEA_WIDTH, safeHeight / SEA_HEIGHT))
        hudWidth = safeWidth / hudScale
        hudHeight = safeHeight / hudScale
    }

    fun hudX(pixelX: Float) = (pixelX / scale - hudLeft) / hudScale
    fun hudY(pixelY: Float) = (pixelY / scale - hudTop) / hudScale
}
