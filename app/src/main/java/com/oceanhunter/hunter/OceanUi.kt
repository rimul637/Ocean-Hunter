package com.oceanhunter.hunter

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.IOException

private enum class Page { SPLASH, HOME, LEVELS, SHOP, SETTINGS, GAME }
private enum class Tint(val light: Color, val shade: Color) {
    GREEN(Color(0xff23cf91), Color(0xff08704c)),
    BLUE(Color(0xff32b4ed), Color(0xff175288)),
    ORANGE(Color(0xffffad39), Color(0xffa65519)),
    PURPLE(Color(0xffaf80eb), Color(0xff62398e)),
    RED(Color(0xfff77266), Color(0xff9a323f)),
    LOCKED(Color(0xff526879), Color(0xff27364a))
}
private val Gold = Color(0xffffd776)
private val Ice = Color(0xffbceeff)
private val Navy = Color(0xff071930)
private val Rim = RoundedCornerShape(17.dp)

@Composable
internal fun OceanGame(
    store: ProgressStore, audio: OceanAudio, active: Boolean, exit: () -> Unit, register: (OceanView) -> Unit
) {
    val progress by store.progress.collectAsState(initial = Progress())
    val scope = rememberCoroutineScope()
    val art = remember { OceanArt().apply { preload(0) } }
    var page by remember { mutableStateOf(Page.SPLASH) }
    var chapter by remember { mutableIntStateOf(0) }
    var dive by remember { mutableStateOf<DiveEngine?>(null) }
    var paused by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<DiveResult?>(null) }
    var saved by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var bossMusic by remember { mutableStateOf(false) }

    fun navigate(to: Page) {
        audio.play(Cue.CLICK)
        if (to != Page.GAME) dive = null
        page = to
        message = ""
    }
    fun save(finished: DiveResult) {
        if (saving) return
        saving = true
        scope.launch {
            try {
                store.reward(finished)
                saved = true
                message = ""
            } catch (_: IOException) {
                message = "Progress couldn’t be saved. Retry to keep your rewards."
            } finally {
                saving = false
            }
        }
    }
    fun start(level: Int) {
        audio.play(Cue.CLICK)
        val mission = Campaign.missions.getOrNull(level - 1)
        paused = false
        result = null
        saved = false
        message = ""
        bossMusic = mission?.goal == Goal.BOSS
        dive = DiveEngine(mission, progress, audio::play) { finished ->
            result = finished
            save(finished)
        }.also { art.preload(it.world) }
        page = Page.GAME
    }
    fun back() {
        when (page) {
            Page.SPLASH -> navigate(Page.HOME)
            Page.GAME -> if (result != null) {
                if (saved) navigate(Page.HOME)
            } else paused = !paused
            Page.HOME -> exit()
            else -> navigate(Page.HOME)
        }
    }

    val track = when {
        page != Page.GAME -> Track.MENU
        paused -> null
        bossMusic -> Track.BOSS
        dive?.mission?.goal == Goal.TREASURE || dive?.mission?.goal == Goal.DESTINATION -> Track.EXPLORE
        else -> Track.ACTION
    }
    LaunchedEffect(track, progress.music, progress.sfx, active) { audio.update(track, progress, active) }
    LaunchedEffect(active) { if (!active && page == Page.GAME && result == null) paused = true }
    LaunchedEffect(dive) {
        val current = dive ?: return@LaunchedEffect
        while (!current.ended) {
            bossMusic = current.mission?.goal == Goal.BOSS || current.boss != null
            delay(200)
        }
    }
    LaunchedEffect(page) {
        if (page == Page.SPLASH) { delay(1250); page = Page.HOME }
    }
    BackHandler { back() }

    BoxWithConstraints(Modifier.fillMaxSize().background(Navy).windowInsetsPadding(WindowInsets.displayCutout)) {
        val compact = maxHeight < 435.dp
        val landscape = maxWidth > maxHeight
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { OceanView(it, art).also(register) },
            update = {
                it.engine = if (page == Page.GAME) dive else null
                it.preview = page == Page.SPLASH || page == Page.HOME || page == Page.SHOP
                it.scene = if (page == Page.LEVELS) chapter else 0
                it.live = active && !paused && result == null || (active && page != Page.GAME)
                it.pause = { paused = true }
                it.invalidate()
            }
        )
        val spacing = if (compact) 6.dp else 10.dp
        when (page) {
            Page.SPLASH -> Column(Modifier.align(Alignment.TopCenter).padding(top = if (landscape) 18.dp else 86.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Logo(compact)
                Image(painterResource(R.drawable.ocean_icon), "Ocean Hunter", Modifier.padding(top = 10.dp).size(if (compact) 48.dp else 72.dp))
            }
            Page.HOME -> if (landscape) {
                Row(Modifier.fillMaxSize().padding(horizontal = 38.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Logo(compact)
                        TextLine("◈  ${progress.coins}   •   BEST  ${progress.best}", Gold, 18)
                    }
                    Column(Modifier.widthIn(max = 236.dp).verticalScroll(rememberScrollState()).focusGroup(), verticalArrangement = Arrangement.spacedBy(spacing)) {
                        MainButtons({ chapter = (progress.unlocked - 1) / 10; navigate(Page.LEVELS) }, { start(0) },
                            { navigate(Page.SHOP) }, { navigate(Page.SETTINGS) }, exit)
                    }
                }
            } else {
                Column(Modifier.align(Alignment.Center).fillMaxWidth(.78f).verticalScroll(rememberScrollState()).focusGroup(),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Logo(false)
                    TextLine("◈  ${progress.coins}   •   BEST  ${progress.best}", Gold, 18)
                    Spacer(Modifier.height(28.dp))
                    MainButtons({ chapter = (progress.unlocked - 1) / 10; navigate(Page.LEVELS) }, { start(0) },
                        { navigate(Page.SHOP) }, { navigate(Page.SETTINGS) }, exit)
                }
            }
            Page.LEVELS -> Frame("DIVE MAP", { navigate(Page.HOME) }, compact) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GoldButton("‹", Tint.BLUE, Modifier.width(52.dp), enabled = chapter > 0) { chapter--; audio.play(Cue.CLICK) }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        TextLine(Campaign.worlds[chapter].title, Gold, 24)
                        TextLine("${progress.stars.drop(chapter * 10).take(10).count { it > 0 }} / 10 cleared", Ice, 13)
                    }
                    GoldButton("›", Tint.BLUE, Modifier.width(52.dp), enabled = chapter < 5) { chapter++; audio.play(Cue.CLICK) }
                }
                if (landscape) {
                    repeat(2) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(5) { col ->
                                LevelButton(chapter * 10 + row * 5 + col + 1, progress, Modifier.weight(1f), ::start)
                            }
                        }
                    }
                } else {
                    repeat(5) { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            repeat(2) { col ->
                                LevelButton(chapter * 10 + row * 2 + col + 1, progress, Modifier.weight(1f), ::start)
                            }
                        }
                    }
                }
            }
            Page.SHOP -> Frame("SUBMARINE WORKSHOP", { navigate(Page.HOME) }, compact) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    TextLine("◈  ${progress.coins}", Gold, 20)
                    SubPreview(art, active)
                }
                if (message.isNotEmpty()) TextLine(message, Ice, 14)
                for (upgrade in Upgrade.entries) {
                    val rank = progress.upgrades[upgrade.ordinal]
                    val cost = upgrade.cost(rank)
                    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            TextLine("${upgrade.title}  ${rank}/5", Gold, 15)
                            TextLine("${upgrade.stat}  ${upgrade.value(rank)}${if (rank < 5) " → ${upgrade.value(rank + 1)}" else ""}", Ice, 12)
                            val fill by animateFloatAsState(rank / 5f, tween(360), label = "upgrade rank")
                            Box(Modifier.width(95.dp).height(4.dp).clip(RoundedCornerShape(5.dp)).background(Color(0xff2b5465))) {
                                Box(Modifier.fillMaxWidth(fill).fillMaxHeight().background(Gold))
                            }
                        }
                        GoldButton(if (rank == 5) "MAX" else "◈ $cost", Tint.ORANGE, Modifier.width(if (compact) 103.dp else 88.dp),
                            enabled = rank < 5 && progress.coins >= cost, height = 41) {
                            scope.launch {
                                try {
                                    if (store.buy(upgrade, rank)) { audio.play(Cue.UPGRADE); message = "${upgrade.title} upgraded" }
                                } catch (_: IOException) { message = "Upgrade could not be saved" }
                            }
                        }
                    }
                }
            }
            Page.SETTINGS -> Frame("SETTINGS", { navigate(Page.HOME) }, compact) {
                GoldButton("♫  MUSIC  ${if (progress.music) "ON" else "OFF"}", Tint.BLUE, Modifier.fillMaxWidth()) {
                    audio.play(Cue.CLICK)
                    scope.launch { try { store.toggleMusic() } catch (_: IOException) { message = "Setting could not be saved" } }
                }
                GoldButton("♪  SOUND EFFECTS  ${if (progress.sfx) "ON" else "OFF"}", Tint.PURPLE, Modifier.fillMaxWidth()) {
                    audio.play(Cue.CLICK)
                    scope.launch { try { store.toggleSfx() } catch (_: IOException) { message = "Setting could not be saved" } }
                }
                if (message.isNotEmpty()) TextLine(message, Ice, 14)
            }
            Page.GAME -> Unit
        }

        val finished = result
        if (page == Page.GAME && (paused || finished != null)) {
            Box(Modifier.fillMaxSize().background(Color(0xa405142c)), contentAlignment = Alignment.Center) {
                var opened by remember { mutableStateOf(false) }
                LaunchedEffect(Unit) { opened = true }
                AnimatedVisibility(opened, enter = fadeIn(tween(190)) + scaleIn(tween(230), initialScale = .84f)) {
                    Column(Modifier.widthIn(max = 440.dp).fillMaxWidth(.9f).verticalScroll(rememberScrollState())
                        .clip(Rim).background(Brush.verticalGradient(listOf(Color(0xff204b68), Navy))).border(2.dp, Gold, Rim).padding(15.dp).focusGroup(),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(spacing)) {
                        if (finished == null) {
                            TextLine("DIVE PAUSED", Gold, 30)
                            GoldButton("▶  RESUME", Tint.GREEN, Modifier.fillMaxWidth()) { audio.play(Cue.CLICK); paused = false }
                            GoldButton("↻  RESTART", Tint.ORANGE, Modifier.fillMaxWidth()) { start(dive?.mission?.number ?: 0) }
                            GoldButton("⌂  HOME", Tint.BLUE, Modifier.fillMaxWidth()) { navigate(Page.HOME) }
                        } else {
                            TextLine(if (finished.cleared) "LEVEL COMPLETE" else "GAME OVER", Gold, 30)
                            if (finished.cleared) {
                                val size by animateFloatAsState(if (opened) 1f else .3f, spring(dampingRatio = .55f), label = "earned stars")
                                Box(Modifier.graphicsLayer { scaleX = size; scaleY = size }) {
                                    TextLine("★".repeat(finished.stars) + "☆".repeat(3 - finished.stars), Gold, 35)
                                }
                            }
                            TextLine("Score  ${finished.score}    ◈  ${finished.coins + finished.bonus}", Ice, 17)
                            if (finished.bonus > 0) TextLine("Collected ${finished.coins}  •  Reward ${finished.bonus}", Gold, 13)
                            if (finished.level == 0) TextLine("Best  ${maxOf(progress.best, finished.score)}", Gold, 15)
                            if (saving) TextLine("Saving rewards…", Ice, 13)
                            if (!saved && !saving && message.isNotEmpty()) {
                                TextLine(message, Ice, 14)
                                GoldButton("RETRY SAVE", Tint.ORANGE, Modifier.fillMaxWidth()) { save(finished) }
                            }
                            if (finished.cleared && finished.level < 60) GoldButton("▶  NEXT LEVEL", Tint.GREEN, Modifier.fillMaxWidth(), saved) { start(finished.level + 1) }
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GoldButton(if (finished.cleared) "↻  REPLAY" else "↻  RETRY", Tint.ORANGE, Modifier.weight(1f), saved) { start(finished.level) }
                                GoldButton("⌂  HOME", Tint.BLUE, Modifier.weight(1f), saved) { navigate(Page.HOME) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubPreview(art: OceanArt, active: Boolean) {
    var time by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        val start = withFrameNanos { it }
        while (true) withFrameNanos { time = (it - start) / 1_000_000_000f }
    }
    Canvas(Modifier.width(136.dp).height(53.dp).clip(Rim).background(Color(0xcc0f385e)).border(1.dp, Gold, Rim)) {
        val canvas = drawContext.canvas.nativeCanvas
        art.submarine(canvas, size.width * .5f, size.height * .51f, size.height / 70f, time,
            kotlin.math.sin(time * 2f) * 5f)
    }
}

@Composable
private fun MainButtons(play: () -> Unit, endless: () -> Unit, shop: () -> Unit, settings: () -> Unit, exit: () -> Unit) {
    GoldButton("▶  PLAY", Tint.GREEN, Modifier.fillMaxWidth(), action = play)
    GoldButton("∞  ENDLESS", Tint.BLUE, Modifier.fillMaxWidth(), action = endless)
    GoldButton("⚙  UPGRADES", Tint.ORANGE, Modifier.fillMaxWidth(), action = shop)
    GoldButton("☷  SETTINGS", Tint.PURPLE, Modifier.fillMaxWidth(), action = settings)
    GoldButton("↪  EXIT", Tint.RED, Modifier.fillMaxWidth(), action = exit)
}

@Composable
private fun Frame(title: String, back: () -> Unit, compact: Boolean, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().padding(if (compact) 8.dp else 18.dp), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 770.dp).fillMaxWidth(.95f).clip(Rim)
            .background(Brush.verticalGradient(listOf(Color(0xd6184564), Color(0xf2071930))))
            .border(1.dp, Color(0x99ffc865), Rim).padding(if (compact) 10.dp else 18.dp)
            .verticalScroll(rememberScrollState()).focusGroup(),
            verticalArrangement = Arrangement.spacedBy(if (compact) 6.dp else 10.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                GoldButton("‹ BACK", Tint.BLUE, Modifier.width(85.dp), height = 39, action = back)
                TextLine(title, Gold, if (compact) 23 else 25, modifier = Modifier.weight(1f))
            }
            content()
        }
    }
}

@Composable
private fun LevelButton(level: Int, progress: Progress, modifier: Modifier, select: (Int) -> Unit) {
    val score = progress.stars[level - 1]
    val unlocked = level <= progress.unlocked
    val label = when {
        !unlocked -> "$level  ·  LOCKED"
        score > 0 -> "$level  ${"★".repeat(score)}"
        level % 10 == 0 -> "$level  ·  BOSS"
        else -> "$level  ·  DIVE"
    }
    GoldButton(label, if (level % 10 == 0) Tint.PURPLE else Tint.GREEN, modifier, unlocked, 47) { select(level) }
}

@Composable
private fun Logo(compact: Boolean) {
    Column {
        TextLine("OCEAN", Ice, if (compact) 43 else 54, weight = FontWeight.Black)
        TextLine("HUNTER", Gold, if (compact) 49 else 61, weight = FontWeight.Black)
    }
}

@Composable
private fun TextLine(
    value: String, color: Color, size: Int, modifier: Modifier = Modifier, weight: FontWeight = FontWeight.Bold,
    centered: Boolean = false
) {
    BasicText(value, modifier, style = TextStyle(
        color = color, fontFamily = FontFamily.SansSerif, fontSize = size.sp, lineHeight = (size + 4).sp,
        fontWeight = weight, textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        shadow = Shadow(Navy, Offset(0f, 2f), 3f)
    ))
}

@Composable
private fun GoldButton(label: String, tint: Tint, modifier: Modifier = Modifier, enabled: Boolean = true, height: Int = 49, action: () -> Unit) {
    val selected = if (enabled) tint else Tint.LOCKED
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (pressed) .945f else 1f, spring(dampingRatio = .61f), label = "button pressure")
    val color = if (enabled) Color(0xfffffff1) else Color(0xffc1ccd4)
    Box(modifier.height(height.dp).graphicsLayer { scaleX = scale; scaleY = scale }
        .onPreviewKeyEvent { event ->
            val activate = event.key == Key.Enter || event.key == Key.NumPadEnter || event.key == Key.DirectionCenter
            if (enabled && activate) {
                if (event.type == KeyEventType.KeyUp) action()
                true
            } else false
        }
        .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = action),
        contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().padding(top = 4.dp).clip(Rim).background(Color(0xff3d261b)))
        Box(Modifier.fillMaxSize().padding(bottom = 4.dp).clip(Rim)
            .background(Brush.verticalGradient(listOf(Color(0xfffff5bf), Color(0xffecb746), Color(0xffa86122))))
        ) {
            Box(Modifier.fillMaxSize().padding(3.dp).clip(RoundedCornerShape(14.dp))
                .background(Brush.verticalGradient(listOf(selected.light, selected.shade)))) {
                Box(Modifier.align(Alignment.TopCenter).padding(horizontal = 5.dp, vertical = 3.dp)
                    .fillMaxWidth().fillMaxHeight(.47f).clip(RoundedCornerShape(11.dp))
                    .background(Brush.verticalGradient(listOf(Color(0x66ffffff), Color(0x07ffffff)))))
            }
            if (focused) Box(Modifier.fillMaxSize().border(2.dp, Color(0xfffffff3), Rim))
            Box(Modifier.fillMaxSize().padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                TextLine(label, color, if (height <= 41) 13 else 16, weight = FontWeight.Black, centered = true)
            }
        }
    }
}
