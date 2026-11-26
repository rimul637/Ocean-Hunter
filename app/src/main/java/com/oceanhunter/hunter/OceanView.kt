package com.oceanhunter.hunter

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import androidx.core.graphics.Insets
import androidx.core.graphics.withSave
import androidx.core.graphics.withTranslation
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import kotlin.math.*

@SuppressLint("ViewConstructor")
internal class OceanView(context: Context, private val art: OceanArt) : View(context) {
    var engine: DiveEngine? = null
        set(value) {
            if (field === value) return
            field = value
            value?.resize(viewport.width, viewport.height)
            clearInput()
            previous = 0L
            accumulator = 0f
            displayedHull = 1f
            requestFocus()
            invalidate()
        }
    var live = true
        set(value) {
            if (field == value) return
            field = value
            previous = 0L
            if (!value) clearInput()
            invalidate()
        }
    var pause: () -> Unit = {}
    var preview = false
    var scene = 0

    private var previous = 0L
    private var timeline = 0f
    private var accumulator = 0f
    private val viewport = OceanViewport()
    private var controlsInsets = Insets.NONE
    private var moveId = -1
    private var fireId = -1
    private var shieldId = -1
    private var stickX = 0f
    private var stickY = 0f
    private val keys = BooleanArray(300)
    private val draw = Paint(Paint.ANTI_ALIAS_FLAG)
    private val brass = 0xffffd574.toInt()
    private val ink = 0xff071b33.toInt()
    private val emerald = 0xff68f0ac.toInt()
    private val sky = 0xff95f6ff.toInt()
    private val ruby = 0xffff846d.toInt()
    private val white = Color.WHITE
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        setShadowLayer(2f, 0f, 2f, ink)
    }
    private val bossNames = Campaign.worlds.map { it.boss.uppercase() }
    private val brassGradient = LinearGradient(0f, -35f, 0f, 35f, intArrayOf(0xfffff6b6.toInt(), brass, 0xff9b541e.toInt()), null, Shader.TileMode.CLAMP)
    private val shades = listOf(
        intArrayOf(0xff51bbda.toInt(), 0xff164783.toInt()),
        intArrayOf(0xffffa56a.toInt(), 0xff9e292c.toInt()),
        intArrayOf(0xff4ae3e6.toInt(), 0xff195e8c.toInt()),
        intArrayOf(0xffffdb7b.toInt(), 0xffa36a2c.toInt())
    ).map { LinearGradient(0f, -32f, 0f, 32f, it, null, Shader.TileMode.CLAMP) }
    private var hudElapsed = -1
    private var hudKills = -1
    private var hudGems = -1
    private var hudScore = -1
    private var hudCoins = -1
    private var missionTitle = ""
    private var missionProgress = ""
    private var hullText = ""
    private var hull = -1
    private var displayedHull = 1f
    private var scoreText = ""
    private var coinsText = ""
    private val effectSeconds = IntArray(Power.entries.size) { -1 }
    private val effectLabels = Array(Power.entries.size) { "" }

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
            val safe = insets.getInsets(WindowInsetsCompat.Type.displayCutout() or WindowInsetsCompat.Type.mandatorySystemGestures())
            if (safe != controlsInsets) {
                controlsInsets = safe
                resizeViewport(width, height)
            }
            insets
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        ViewCompat.requestApplyInsets(this)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) = resizeViewport(w, h)

    private fun resizeViewport(w: Int, h: Int) {
        val safe = controlsInsets
        viewport.update(w, h, safe.left, safe.top, safe.right, safe.bottom)
        engine?.resize(viewport.width, viewport.height)
        clearInput()
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val now = System.nanoTime()
        val dt = if (previous == 0L || !live) 0f else ((now - previous) / 1e9f).coerceIn(0f, .065f)
        previous = now
        timeline += dt
        val dive = engine
        if (dive != null && live && !dive.ended) {
            accumulator += dt
            while (accumulator >= STEP && !dive.ended) {
                dive.advance(STEP)
                accumulator -= STEP
            }
        }
        canvas.withSave {
            scale(viewport.scale, viewport.scale)
            if (dive == null) {
                art.environment(this, scene, timeline, viewport.width, viewport.height)
                if (preview) art.submarine(this, viewport.width * .498f,
                    viewport.height * .505f + sin(timeline * 2.2f) * 14, 2.45f, timeline, sin(timeline * 1.5f) * 6)
            } else {
                val health = dive.health.toFloat() / dive.maxHealth
                displayedHull += (health - displayedHull) * (if (dive.ended) 1f else min(1f, dt * 12))
                val blend = if (dive.ended) 1f else (accumulator / STEP).coerceIn(0f, 1f)
                val time = (dive.elapsed - STEP + accumulator).coerceAtLeast(0f)
                art.render(this, dive, time, blend)
                withTranslation(viewport.hudLeft, viewport.hudTop) {
                    scale(viewport.hudScale, viewport.hudScale)
                    renderHud(this, dive)
                }
            }
        }
        if (live && dive?.ended != true) postInvalidateOnAnimation()
    }

    private fun label(c: Canvas, text: String, x: Float, y: Float, textSize: Float, color: Int = white, centered: Boolean = false) {
        textPaint.color = color
        textPaint.textAlign = if (centered) Paint.Align.CENTER else Paint.Align.LEFT
        textPaint.textSize = textSize
        c.drawText(text, x, y, textPaint)
    }

    private fun slab(c: Canvas, x: Float, y: Float, w: Float, h: Float, tint: Int, radius: Float = 12f) {
        draw.shader = null; draw.color = tint
        c.drawRoundRect(x, y, x + w, y + h, radius, radius, draw)
    }

    private fun renderHud(c: Canvas, dive: DiveEngine) {
        val m = dive.mission
        val seconds = dive.elapsed.toInt()
        if (hudElapsed != seconds || hudKills != dive.kills || hudGems != dive.treasures || missionTitle.isEmpty()) {
            hudElapsed = seconds; hudKills = dive.kills; hudGems = dive.treasures
            missionTitle = if (m == null) "ENDLESS SURVIVAL" else "${m.number}  •  ${m.name.uppercase()}"
            missionProgress = when (m?.goal) {
                Goal.DESTROY -> "${dive.kills} / ${m.quota} enemies"
                Goal.TIMED -> "${dive.kills} / ${m.quota} enemies  •  ${(m.span - seconds).coerceAtLeast(0)}s left"
                Goal.TREASURE -> "${dive.treasures} / ${m.quota} treasure chests"
                Goal.SURVIVE -> "Survive  •  ${(m.span - seconds).coerceAtLeast(0)}s left"
                Goal.DESTINATION -> if (seconds < m.span) "Dive beacon in ${m.span - seconds}s" else "Reach the dive beacon →"
                Goal.BOSS -> Campaign.worlds[m.world].boss
                null -> "${seconds / 60}m ${seconds % 60}s  •  ${Campaign.worlds[dive.world].title}"
            }
        }
        if (hull != dive.health) { hull = dive.health; hullText = "HULL  ${dive.health}/${dive.maxHealth}" }
        if (hudScore != dive.score) { hudScore = dive.score; scoreText = "SCORE  ${dive.score}" }
        if (hudCoins != dive.coins) { hudCoins = dive.coins; coinsText = "◈  ${dive.coins}" }
        slab(c, 22f, 17f, 171f, 50f, 0xb5082240.toInt())
        label(c, hullText, 34f, 41f, 17f, emerald)
        slab(c, 34f, 49f, 146f, 7f, ink, 4f)
        slab(c, 34f, 49f, 146f * displayedHull, 7f, if (dive.health * 3 < dive.maxHealth) ruby else emerald, 4f)
        val center = viewport.centerX
        slab(c, center - 215f, 13f, 430f, 62f, 0x96102b4d.toInt())
        label(c, missionTitle, center, 34f, 20f, brass, true)
        label(c, missionProgress, center, 52f, 15f, white, true)
        label(c, scoreText, center, 68f, 13f, sky, true)
        label(c, coinsText, viewport.hudWidth - 165f, 47f, 21f, brass)
        control(c, viewport.pauseX, PAUSE_Y, 25f, 3, "Ⅱ", false)
        val boss = dive.boss
        if (boss != null) {
            slab(c, center - 186f, 86f, 372f, 28f, 0xbe1f183d.toInt())
            slab(c, center - 170f, 105f, 340f * boss.hp.coerceAtLeast(0) / dive.bossMax(boss.code), 4f, ruby, 2f)
            label(c, bossNames[boss.code], center, 101f, 14f, ruby, true)
        }
        var powerX = 220f
        for (i in dive.effects.indices) {
            val remaining = ceil(dive.effects[i]).toInt()
            if (remaining <= 0) continue
            if (remaining != effectSeconds[i]) {
                effectSeconds[i] = remaining
                effectLabels[i] = "${Power.entries[i].title} ${remaining}s"
            }
            label(c, effectLabels[i], powerX, viewport.hudHeight - 18f, 13f, sky)
            powerX += 78f
        }
        control(c, JOYSTICK_X, viewport.joystickY, 48f, 0, "", moveId != -1)
        control(c, JOYSTICK_X + stickX * 28, viewport.joystickY + stickY * 28, 19f, 0, "✥", false)
        control(c, viewport.fireX, viewport.fireY, 43f, 1, "FIRE", dive.fire)
        control(c, viewport.shieldX, viewport.shieldY, 36f, 2, if (dive.recharge > 0f) countdown[ceil(dive.recharge).toInt().coerceIn(1, 9)] else "SHIELD", shieldId != -1)
    }

    private val countdown = (0..9).map { "${it}s" }

    private fun control(c: Canvas, x: Float, y: Float, r: Float, style: Int, text: String, pressed: Boolean) {
        c.withTranslation(x, y) {
            if (pressed) scale(.91f, .91f)
            draw.shader = null
            draw.color = 0xcc0a1024.toInt()
            drawCircle(0f, 5f, r + 3, draw)
            draw.shader = brassGradient
            drawCircle(0f, 0f, r, draw)
            draw.shader = shades[style]
            drawCircle(0f, 0f, r - 4, draw)
            draw.shader = null
            draw.color = 0x56ffffff
            drawOval(-r * .57f, -r * .76f, r * .56f, -r * .43f, draw)
            if (text.isNotEmpty()) label(this, text, 0f, r * .2f, r * .43f + 2, white, true)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val dive = engine ?: return false
        if (!live || dive.ended) return false
        val index = event.actionIndex
        val id = event.getPointerId(index)
        val x = viewport.hudX(event.getX(index))
        val y = viewport.hudY(event.getY(index))
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                when {
                    hypot(x - viewport.pauseX, y - PAUSE_Y) < 32 -> pause()
                    hypot(x - viewport.fireX, y - viewport.fireY) < 53 && fireId == -1 -> {
                        fireId = id
                        dive.requestFire()
                    }
                    hypot(x - viewport.shieldX, y - viewport.shieldY) < 44 && shieldId == -1 -> { shieldId = id; dive.shield() }
                    x in 19f..190f && y in (viewport.joystickY - 92f)..(viewport.hudHeight - 10f) && moveId == -1 -> { moveId = id; moveStick(x, y) }
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val selected = event.findPointerIndex(moveId)
                if (selected >= 0) moveStick(viewport.hudX(event.getX(selected)), viewport.hudY(event.getY(selected)))
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (moveId == id) { moveId = -1; stickX = 0f; stickY = 0f }
                if (fireId == id) fireId = -1
                if (shieldId == id) shieldId = -1
                if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
            }
            MotionEvent.ACTION_CANCEL -> clearInput()
        }
        updateInput()
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun moveStick(x: Float, y: Float) {
        val dx = x - JOYSTICK_X
        val dy = y - viewport.joystickY
        val distance = hypot(dx, dy).coerceAtLeast(30f)
        stickX = dx / distance
        stickY = dy / distance
    }

    fun handleKey(event: KeyEvent): Boolean {
        val dive = engine ?: return false
        if (!live || dive.ended) return false
        val code = event.keyCode
        if (code !in keys.indices) return false
        when (code) {
            KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_D, KeyEvent.KEYCODE_DPAD_RIGHT,
            KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_DPAD_DOWN,
            KeyEvent.KEYCODE_SPACE, KeyEvent.KEYCODE_E -> {
                keys[code] = event.action == KeyEvent.ACTION_DOWN
                if (keys[code] && event.repeatCount == 0) when (code) {
                    KeyEvent.KEYCODE_E -> dive.shield()
                    KeyEvent.KEYCODE_SPACE -> dive.requestFire()
                }
                updateInput()
                return true
            }
        }
        return false
    }

    private fun axis(positive: Boolean, negative: Boolean) = (if (positive) 1f else 0f) - (if (negative) 1f else 0f)
    private fun updateInput() {
        engine?.apply {
            moveX = if (moveId != -1) stickX else axis(keys[KeyEvent.KEYCODE_D] || keys[KeyEvent.KEYCODE_DPAD_RIGHT], keys[KeyEvent.KEYCODE_A] || keys[KeyEvent.KEYCODE_DPAD_LEFT])
            moveY = if (moveId != -1) stickY else axis(keys[KeyEvent.KEYCODE_S] || keys[KeyEvent.KEYCODE_DPAD_DOWN], keys[KeyEvent.KEYCODE_W] || keys[KeyEvent.KEYCODE_DPAD_UP])
            fire = fireId != -1 || keys[KeyEvent.KEYCODE_SPACE]
        }
    }
    private fun clearInput() {
        engine?.cancelFireRequest()
        keys.fill(false); moveId = -1; fireId = -1; shieldId = -1; stickX = 0f; stickY = 0f
        updateInput()
        hudElapsed = -1; hudKills = -1; hudGems = -1; hudScore = -1; hudCoins = -1; hull = -1
    }

    companion object {
        private const val STEP = 1f / 120f
        private const val JOYSTICK_X = 95f
        private const val PAUSE_Y = 37f
    }
}
