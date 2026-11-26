package com.oceanhunter.hunter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OceanViewportTest {
    @Test
    fun comparisonScreensUseTheEntireSurfaceWithUniformScale() {
        for ((width, height) in listOf(1536 to 691, 1920 to 1080, 1600 to 1200, 1080 to 2400)) {
            val viewport = OceanViewport()
            viewport.update(width, height, 0, 0, 0, 0)
            assertEquals(width.toFloat(), viewport.width * viewport.scale, .001f)
            assertEquals(height.toFloat(), viewport.height * viewport.scale, .001f)
            assertTrue(viewport.width >= SEA_WIDTH - .001f)
            assertTrue(viewport.height >= SEA_HEIGHT - .001f)
        }
    }

    @Test
    fun wideScreenControlsAnchorToSafeEdgesAndTouchUsesTheSameTransform() {
        val viewport = OceanViewport()
        viewport.update(1536, 691, 59, 21, 15, 24)
        val pixelScale = viewport.scale * viewport.hudScale
        val firePixelX = viewport.hudLeft * viewport.scale + viewport.fireX * pixelScale
        val firePixelY = viewport.hudTop * viewport.scale + viewport.fireY * pixelScale
        assertEquals(1536 - 15 - 83 * pixelScale, firePixelX, .001f)
        assertEquals(691 - 24 - 92 * pixelScale, firePixelY, .001f)
        assertEquals(viewport.fireX, viewport.hudX(firePixelX), .001f)
        assertEquals(viewport.fireY, viewport.hudY(firePixelY), .001f)
        assertTrue(firePixelX + 53f * pixelScale < 1536 - 15)
        assertTrue((viewport.hudLeft * viewport.scale + (95f - 48f) * pixelScale) > 59f)
    }

    @Test
    fun aDiveCanUseTheExpandedWidthAndResizeWithoutResetting() {
        val engine = DiveEngine(Campaign.missions.first(), Progress())
        engine.resize(1320f, SEA_HEIGHT)
        engine.moveX = 1f
        repeat(600) { engine.advance(1f / 120f) }
        assertTrue(engine.x > SEA_WIDTH)
        assertTrue(engine.actors.any { it.x > SEA_WIDTH })
        val elapsed = engine.elapsed
        val actors = engine.actors.size
        engine.resize(SEA_WIDTH, 1120f)
        assertEquals(elapsed, engine.elapsed, 0f)
        assertEquals(actors, engine.actors.size)
        assertTrue(engine.x <= SEA_WIDTH - 45)
        assertEquals(engine.x, engine.previousX, 0f)
        assertEquals(engine.y, engine.previousY, 0f)
    }

    @Test
    fun aFireTapBetweenFramesStillLaunchesOneTorpedo() {
        val engine = DiveEngine(Campaign.missions.first(), Progress())
        engine.requestFire()
        engine.advance(1f / 120f)
        assertEquals(1, engine.actors.count { it.kind == Kind.TORPEDO })
        repeat(60) { engine.advance(1f / 120f) }
        assertEquals(1, engine.actors.count { it.kind == Kind.TORPEDO })
    }
}
