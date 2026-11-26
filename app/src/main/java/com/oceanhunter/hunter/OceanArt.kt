package com.oceanhunter.hunter

import android.graphics.*
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withRotation
import androidx.core.graphics.withSave
import kotlin.math.*
import kotlin.random.Random

internal class OceanArt {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val rect = RectF()
    private val path = Path()
    private val ocean = arrayOf(
        intArrayOf(0xff138eac.toInt(), 0xff061e42.toInt(), 0xff168e8a.toInt(), 0xffef6e9b.toInt()),
        intArrayOf(0xff1678a9.toInt(), 0xff04132e.toInt(), 0xff1c4775.toInt(), 0xff709dac.toInt()),
        intArrayOf(0xff17777f.toInt(), 0xff06222a.toInt(), 0xff396e6d.toInt(), 0xffc1a263.toInt()),
        intArrayOf(0xff4eabca.toInt(), 0xff101a54.toInt(), 0xff4e79b3.toInt(), 0xff9de9fe.toInt()),
        intArrayOf(0xff53465f.toInt(), 0xff240c28.toInt(), 0xff7d3b41.toInt(), 0xfffa713b.toInt()),
        intArrayOf(0xff32528e.toInt(), 0xff090d2a.toInt(), 0xff354078.toInt(), 0xffa878ef.toInt())
    )
    private val backgrounds = arrayOfNulls<BitmapShader>(6)
    private val layers = arrayOfNulls<BitmapShader>(6)
    private val terrainMatrix = Matrix()
    private var water: LinearGradient? = null
    private var surfaceLight: RadialGradient? = null
    private var gradientWorld = -1
    private var gradientWidth = 0f
    private var gradientHeight = 0f
    private val fishColors = intArrayOf(0xff5ea3d1.toInt(), 0xffdf8be7.toInt(), 0xff3da9bd.toInt(), 0xffec655a.toInt(),
        0xff74d38f.toInt(), 0xffc566bd.toInt(), 0xffffba60.toInt(), 0xff63718e.toInt(), 0xff6ccee0.toInt(), 0xff8671c8.toInt())
    private val player by lazy { sprite(null) }
    private val sprites = arrayOfNulls<Bitmap>(Enemy.entries.size)
    private val bosses = arrayOf(Enemy.CRAB, Enemy.SHARK, Enemy.SUB, Enemy.SNAKE, Enemy.MONSTER, Enemy.OCTOPUS)
    private val white = Color.WHITE
    private val outline = 0xff0a1736.toInt()
    private val gold = 0xffffc243.toInt()
    private val coin by lazy { collectibleTexture(11f, 0xffffeb8b.toInt(), 0xffdb7b13.toInt()) }
    private val powers by lazy { Array(Power.entries.size) { collectibleTexture(14f, fishColors[it], outline) } }

    private fun color(value: Int) { paint.shader = null; paint.style = Paint.Style.FILL; paint.color = value }
    private fun oval(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, value: Int) {
        color(value); c.drawOval(x - rx, y - ry, x + rx, y + ry, paint)
    }
    private fun round(c: Canvas, l: Float, t: Float, r: Float, b: Float, radius: Float, value: Int) {
        color(value); c.drawRoundRect(l, t, r, b, radius, radius, paint)
    }
    private fun poly(c: Canvas, value: Int, vararg xy: Float) {
        color(value); path.reset(); path.moveTo(xy[0], xy[1])
        for (i in 2 until xy.size step 2) path.lineTo(xy[i], xy[i + 1])
        path.close(); c.drawPath(path, paint)
    }
    private fun ray(c: Canvas, value: Int, x: Float, bottom: Float, height: Float) {
        color(value); path.reset()
        path.moveTo(x, 0f); path.lineTo(x + 28, 0f); path.lineTo(bottom, height * .77f); path.lineTo(x - 177, height * .61f)
        path.close(); c.drawPath(path, paint)
    }
    private fun line(c: Canvas, x: Float, y: Float, x2: Float, y2: Float, size: Float, value: Int) {
        color(value); paint.strokeWidth = size; paint.strokeCap = Paint.Cap.ROUND
        c.drawLine(x, y, x2, y2, paint)
    }
    private fun glossy(c: Canvas, x: Float, y: Float, rx: Float, ry: Float, light: Int, dark: Int) {
        oval(c, x, y, rx + 2.5f, ry + 2.5f, outline)
        color(white)
        paint.shader = LinearGradient(x - rx * .36f, y - ry, x + rx * .5f, y + ry, light, dark, Shader.TileMode.CLAMP)
        c.drawOval(x - rx, y - ry, x + rx, y + ry, paint)
        oval(c, x - rx * .26f, y - ry * .53f, rx * .47f, ry * .145f, 0x59ffffff)
    }

    fun preload(index: Int) {
        if (backgrounds[index] != null) return
        backgrounds[index] = terrainShader(index, false)
        layers[index] = terrainShader(index, true)
        player
        coin
        powers
        for (type in Campaign.worlds[index].enemies + bosses[index]) {
            if (sprites[type.ordinal] == null) sprites[type.ordinal] = sprite(type)
        }
    }

    private fun terrainShader(world: Int, foreground: Boolean): BitmapShader {
        val bitmap = makeBackground(world, foreground)
        bitmap.prepareToDraw()
        return BitmapShader(bitmap, Shader.TileMode.MIRROR, Shader.TileMode.CLAMP)
    }

    private fun makeBackground(world: Int, foreground: Boolean): Bitmap {
        val bitmap = createBitmap(960, 540)
        val c = Canvas(bitmap)
        val palette = ocean[world]
        val rng = Random(42 + world)
        if (!foreground) {
            for (depth in 0..2) {
                path.reset(); path.moveTo(0f, 540f)
                for (i in 0..20) {
                    val x = i * 48f
                    val y = 411 - depth * 28 + sin(i * (.87f + depth * .22f) + world) * (15 + depth * 9) + rng.nextInt(35)
                    path.lineTo(x, y)
                }
                path.lineTo(960f, 540f); path.close()
                color(if (depth == 2) palette[1] else (palette[2] and 0xffffff) or 0x66000000)
                c.drawPath(path, paint)
            }
            repeat(8) { i ->
                val x = i * 144f + 35
                when (world) {
                    2 -> {
                        poly(c, 0xff193c45.toInt(), x, 401f, x + 23, 454f, x + 126, 464f, x + 145, 402f)
                        line(c, x + 75, 427f, x + 58, 290f, 9f, 0xff284751.toInt())
                        poly(c, 0xff41686c.toInt(), x + 61, 300f, x + 116, 367f, x + 66, 382f)
                    }
                    3 -> {
                        poly(c, 0xaa5289c4.toInt(), x, 469f, x + 78, 281f + i % 3 * 32, x + 110, 442f)
                        poly(c, 0x8877c5ec.toInt(), x + 16, 441f, x + 78, 281f + i % 3 * 32, x + 63, 428f)
                    }
                    4 -> {
                        poly(c, 0xff422234.toInt(), x - 28, 470f, x + 32, 345f, x + 68, 345f, x + 130, 470f)
                        line(c, x + 52, 348f, x + 57, 396f, 6f, 0x83ff643b.toInt())
                        oval(c, x + 49, 346f, 22f, 5f, 0xaaff8a36.toInt())
                    }
                    5 -> {
                        round(c, x, 316f, x + 23, 471f, 4f, palette[2])
                        round(c, x + 91, 316f, x + 115, 471f, 4f, palette[2])
                        round(c, x - 6, 307f, x + 121, 334f, 6f, palette[2])
                        for (j in 0..4) line(c, x + 8, 340f + j * 21, x + 18, 340f + j * 21, 2f, 0x709589c2)
                    }
                    else -> if (world == 1) poly(c, 0xff174666.toInt(), x - 43, 460f, x + 32, 308f + i % 3 * 20, x + 130, 450f)
                }
            }
        } else {
            path.reset(); path.moveTo(0f, 540f)
            for (i in 0..30) path.lineTo(i * 32f, 491f + sin(i * .63f) * 9)
            path.lineTo(960f, 540f); path.close()
            color(palette[1]); c.drawPath(path, paint)
            repeat(65) {
                val x = rng.nextInt(960).toFloat()
                val h = (10 + rng.nextInt(63)).toFloat()
                val y = 510f + rng.nextInt(14)
                val shade = if (it % 3 == 0) palette[3] else palette[2]
                if (world <= 2 || world == 5) {
                    line(c, x, y, x + 8, y - h, 3.5f, shade)
                    repeat(4) { j ->
                        val a = y - h * (.25f + j * .18f)
                        line(c, x + j * 2, a, x + if (j % 2 == 0) -11f else 21f, a - h * .23f, 2.2f, shade)
                    }
                } else poly(c, shade, x - 8, y, x + 4, y - h, x + 11, y - h * .43f, x + 14, y)
                oval(c, x + 3, y + 4, 15f, 4f, 0x5574cbce)
            }
        }
        return bitmap
    }

    fun environment(c: Canvas, world: Int, time: Float, width: Float, height: Float) {
        preload(world)
        if (world != gradientWorld || width != gradientWidth || height != gradientHeight) {
            val palette = ocean[world]
            water = LinearGradient(0f, 0f, 0f, height, palette[0], palette[1], Shader.TileMode.CLAMP)
            surfaceLight = RadialGradient(width * .57f, 0f, max(height, width * .61f),
                0x8560f9ff.toInt(), 0x0060f9ff, Shader.TileMode.CLAMP)
            gradientWorld = world
            gradientWidth = width
            gradientHeight = height
        }
        color(white)
        paint.shader = water
        c.drawRect(0f, 0f, width, height, paint)
        paint.shader = surfaceLight
        c.drawRect(0f, 0f, width, height, paint)
        terrain(c, checkNotNull(backgrounds[world]), width, height, time * 4f)

        val light = sin(time * .16f) * 39f
        for (i in 0..5) {
            val x = width * .5f + (i - 3) * 46f + light
            ray(c, if (i % 2 == 0) 0x164af3ff else 0x0d7af6ff, x, x - 104 + i * 16, height)
        }
        val bubbleCount = (34f * sqrt(width / SEA_WIDTH * height / SEA_HEIGHT)).toInt().coerceIn(34, 64)
        color(0x488deeff)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = .9f
        for (i in 0 until bubbleCount) {
            val x = (i * 67f - time * (6 + i % 3)).mod(width)
            val y = (i * 36f - time * (9 + i % 5)).mod(height)
            c.drawCircle(x + sin(y * .05f) * 7, y, 2f + i % 5, paint)
        }
        val fishCount = (12f * width / SEA_WIDTH).toInt().coerceIn(12, 22)
        for (i in 0 until fishCount) {
            val x = (width + 90f + i * 78f - time * (14 + i % 3)).mod(width + 140f) - 45f
            val y = height * (.175f + i % 6 * .072f) + sin(time * 1.3f + i) * 8f
            val shade = if (i % 4 == 0) 0x9deab55b.toInt() else 0x804fe2ce.toInt()
            oval(c, x, y, 9f + i % 4, 4f, shade)
            line(c, x + 7, y, x + 15, y - 4, 2.5f, shade)
            line(c, x + 7, y, x + 15, y + 4, 2.5f, shade)
            oval(c, x - 4, y - 1, 1.3f, 1.3f, outline)
        }
        terrain(c, checkNotNull(layers[world]), width, height, time * 18f)
    }

    private fun terrain(c: Canvas, shader: BitmapShader, width: Float, height: Float, scroll: Float) {
        val top = height - SEA_HEIGHT
        terrainMatrix.setTranslate(-scroll.mod(SEA_WIDTH * 2), top)
        shader.setLocalMatrix(terrainMatrix)
        color(white)
        paint.shader = shader
        c.drawRect(0f, top, width, height, paint)
    }

    private fun collectibleTexture(radius: Float, light: Int, dark: Int): Bitmap {
        val center = radius + 3f
        val bitmap = createBitmap((center * 2).toInt(), (center * 2).toInt())
        glossy(Canvas(bitmap), center, center, radius, radius, light, dark)
        bitmap.prepareToDraw()
        return bitmap
    }

    private fun sprite(type: Enemy?): Bitmap {
        val b = createBitmap(148, 112)
        val c = Canvas(b)
        c.translate(74f, 54f)
        if (type == null || type == Enemy.SUB || type == Enemy.DRONE) {
            val light = if (type == null) 0xffffde58.toInt() else fishColors[type.ordinal]
            poly(c, outline, -32f, -12f, -56f, -30f, -62f, -21f, -49f, 10f, -57f, 28f, -43f, 30f, -29f, 13f)
            poly(c, 0xfffa6334.toInt(), -32f, -10f, -56f, -26f, -55f, -17f, -45f, 11f, -51f, 25f, -44f, 25f, -28f, 11f)
            round(c, -18f, -30f, 12f, -13f, 5f, outline)
            round(c, -14f, -28f, 9f, -15f, 4f, light)
            round(c, 31f, -4f, 62f, 10f, 6f, outline)
            round(c, 41f, -1f, 60f, 7f, 4f, light)
            glossy(c, 0f, 0f, 49f, 24f, light, if (type == null) 0xff9b4512.toInt() else outline)
            glossy(c, 15f, -3f, 23f, 17f, 0xff76f2f6.toInt(), 0xff114483.toInt())
            oval(c, -34f, 8f, 11f, 11f, outline)
            oval(c, -34f, 8f, 7f, 7f, 0xffffe477.toInt())
            round(c, -12f, 16f, 31f, 21f, 2f, 0xff63391c.toInt())
            b.prepareToDraw()
            return b
        }
        val tint = fishColors[type.ordinal]
        when (type) {
            Enemy.CRAB -> {
                for (i in 0..3) for (side in floatArrayOf(-1f, 1f)) {
                    val l = 10f + i * 6
                    line(c, side * l, 9f, side * (l + 13), 28f - i * 2, 8f, outline)
                    line(c, side * l, 9f, side * (l + 13), 28f - i * 2, 5f, tint)
                }
                glossy(c, -42f, -6f, 16f, 14f, tint, 0xff742439.toInt())
                glossy(c, 42f, -6f, 16f, 14f, tint, 0xff742439.toInt())
                glossy(c, 0f, 5f, 34f, 23f, tint, 0xff862139.toInt())
                eye(c, -13f, -7f); eye(c, 8f, -7f)
            }
            Enemy.JELLY, Enemy.OCTOPUS -> {
                for (i in 0..6) {
                    val x = i * 10f - 29
                    path.reset(); path.moveTo(x, 2f)
                    path.cubicTo(x - 17, 19f, x + 27, 28f, x + (i % 3 - 1) * 17, 45f - i % 2 * 12)
                    color(tint); paint.style = Paint.Style.STROKE; paint.strokeWidth = if (type == Enemy.JELLY) 3f else 8f
                    c.drawPath(path, paint)
                }
                glossy(c, 0f, -8f, 40f, 25f, tint, 0xff594295.toInt())
                eye(c, -14f, -4f); eye(c, 10f, -4f)
            }
            Enemy.MINE, Enemy.PUFFER -> {
                for (i in 0..13) {
                    c.withRotation(i * (360f / 14)) {
                        poly(this, tint, -9f, -22f, 0f, -44f, 9f, -22f)
                    }
                }
                glossy(c, 0f, 0f, 30f, 28f, tint, outline)
                if (type == Enemy.MINE) { oval(c, -4f, -4f, 13f, 12f, outline); oval(c, -4f, -4f, 6f, 6f, 0xffff6544.toInt()) }
                else { eye(c, -17f, -5f); eye(c, 3f, -5f); oval(c, -11f, 11f, 7f, 5f, outline) }
            }
            Enemy.SNAKE -> {
                for (i in 7 downTo 0) glossy(c, i * 13f - 47, sin(i * .78f) * 17, 19f - i, 12f - i * .55f, tint, outline)
                eye(c, -52f, -3f)
            }
            else -> {
                poly(c, outline, -14f, -12f, 4f, -38f, 17f, -16f, 27f, -9f)
                poly(c, tint, -11f, -10f, 4f, -34f, 15f, -14f, 24f, -9f)
                poly(c, tint, 32f, -4f, 62f, -25f, 58f, 1f, 65f, 23f, 32f, 8f)
                glossy(c, -4f, 0f, 47f, 23f, tint, outline)
                oval(c, -18f, 11f, 28f, 8f, 0xffcfeced.toInt())
                poly(c, outline, -46f, 8f, -18f, 15f, -37f, 19f)
                for (i in 0..3) poly(c, white, -40f + i * 5, 10f + i, -36f + i * 5, 11f + i, -38f + i * 5, 15f + i)
                eye(c, -34f, -6f)
            }
        }
        b.prepareToDraw()
        return b
    }

    private fun eye(c: Canvas, x: Float, y: Float) {
        oval(c, x, y, 7f, 7f, white)
        oval(c, x - 2, y, 3.6f, 4.2f, outline)
        oval(c, x - 2.8f, y - 1.6f, 1.3f, 1.3f, white)
    }

    private fun drawBitmap(c: Canvas, image: Bitmap, x: Float, y: Float, scale: Float, flip: Boolean = false, flash: Boolean = false) {
        val w = image.width * scale / 2
        val h = image.height * scale / 2
        rect.set(x - w, y - h, x + w, y + h)
        c.withSave {
            if (flip) scale(-1f, 1f, x, y)
            color(white)
            if (flash) paint.colorFilter = flashFilter
            drawBitmap(image, null, rect, paint)
            paint.colorFilter = null
        }
    }

    private val flashFilter = PorterDuffColorFilter(0xa5fff6bd.toInt(), PorterDuff.Mode.SRC_ATOP)

    fun submarine(c: Canvas, x: Float, y: Float, scale: Float, time: Float, tilt: Float = 0f, flash: Boolean = false) {
        c.withRotation(tilt, x, y) {
            val jet = sin(time * 58f) * 8f
            oval(this, x - (53 + jet) * scale, y + 1f, (17 + jet) * scale, 5f * scale, 0x8880effe.toInt())
            oval(this, x - 54f * scale, y, 4f * scale, (3f + abs(jet)) * scale, 0xffeafaef.toInt())
            drawBitmap(this, player, x, y, scale, flash = flash)
        }
    }

    fun render(c: Canvas, engine: DiveEngine, time: Float, blend: Float) {
        environment(c, engine.world, time, engine.width, engine.height)
        for (index in engine.actors.indices) {
            val e = engine.actors[index]
            val x = e.previousX + (e.x - e.previousX) * blend
            val y = e.previousY + (e.y - e.previousY) * blend
            val margin = if (e.kind == Kind.BOSS) 160f else 100f
            if (x < -margin || x > engine.width + margin || y < -margin || y > engine.height + margin) continue
            when (e.kind) {
                Kind.BOSS, Kind.ENEMY -> {
                    val type = if (e.kind == Kind.BOSS) bosses[e.code] else Enemy.entries[e.code]
                    val image = sprites[type.ordinal] ?: sprite(type).also { sprites[type.ordinal] = it }
                    val scale = e.radius / 37f * if (e.kind == Kind.BOSS) 1.15f else .93f
                    val tilt = sin(e.age * 3.4f) * (if (e.kind == Kind.BOSS) 5 else 8)
                    c.withRotation(tilt, x, y) {
                        drawBitmap(this, image, x, y, scale, type == Enemy.SUB || type == Enemy.DRONE, e.flash > 0f)
                    }
                    if (e.warning > 0f) ring(c, x, y, e.radius + 9f + sin(e.age * 26) * 4, 0xeeffba76.toInt())
                }
                Kind.TORPEDO -> {
                    line(c, x - 37, y, x - 1, y, 5f, 0x70ffb445)
                    round(c, x - 13, y - 4, x + 9, y + 4, 4f, 0xffff8150.toInt())
                    line(c, x - 6, y - 1, x + 7, y - 1, 2.8f, 0xffffe68d.toInt())
                }
                Kind.HOSTILE -> { oval(c, x, y, 11f, 7f, 0x99f84176.toInt()); oval(c, x, y, 5f, 4f, 0xffffc1b4.toInt()) }
                Kind.COIN -> {
                    val half = 8f + abs(cos(e.age * 9)) * 6f
                    rect.set(x - half, y - 14f, x + half, y + 14f)
                    color(white)
                    c.drawBitmap(coin, null, rect, paint)
                }
                Kind.CHEST -> {
                    val bob = y + sin(e.age * 5) * 3
                    round(c, x - 21, bob - 12, x + 21, bob + 14, 7f, gold)
                    round(c, x - 17, bob - 8, x + 17, bob + 10, 5f, 0xff814628.toInt())
                    line(c, x - 19, bob, x + 19, bob, 3f, gold)
                    round(c, x - 4, bob - 4, x + 4, bob + 7, 2f, gold)
                }
                Kind.POWER -> {
                    drawBitmap(c, powers[e.code], x, y, 1f)
                    line(c, x - 6, y, x + 6, y, 2.5f, white)
                    line(c, x, y - 6, x, y + 6, 2.5f, white)
                }
                Kind.BEACON -> {
                    ring(c, x, y, 26f + sin(e.age * 5) * 5f, 0xff7bffe1.toInt())
                    oval(c, x, y, 11f, 11f, 0xff9effe2.toInt())
                }
                Kind.HAZARD -> {
                    val shade = if (e.age < 1.15f) 0x36ffae63 else 0x85ff7453.toInt()
                    oval(c, x, y, e.radius, e.radius, shade)
                    ring(c, x, y, e.radius, 0xcaffbc79.toInt())
                }
                Kind.PARTICLE -> {
                    val alpha = (e.life * 300).toInt().coerceIn(0, 220)
                    val rgb = if (e.code == 0) 0xff897b else if (e.code == 1) 0xffdf75 else 0xffa444
                    oval(c, x, y, e.radius, e.radius, (alpha shl 24) or rgb)
                }
            }
        }
        val playerX = engine.previousX + (engine.x - engine.previousX) * blend
        val playerY = engine.previousY + (engine.y - engine.previousY) * blend
        submarine(c, playerX, playerY, .84f, time, engine.tilt, engine.invulnerable > .78f)
        if (engine.barrier > 0 || engine.effects[Power.SHIELD.ordinal] > 0) {
            oval(c, playerX, playerY, 46f, 37f, 0x3995faff)
            ring(c, playerX, playerY, 43f + sin(time * 13) * 3, 0xaa79feff.toInt())
        }
    }

    fun ring(c: Canvas, x: Float, y: Float, radius: Float, tint: Int, width: Float = 2.2f) {
        color(tint); paint.style = Paint.Style.STROKE; paint.strokeWidth = width
        c.drawCircle(x, y, radius, paint)
    }
}
